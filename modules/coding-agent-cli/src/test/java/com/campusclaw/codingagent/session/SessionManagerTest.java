/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.StopReason;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.ai.types.Usage;
import com.campusclaw.ai.types.UserMessage;
import com.campusclaw.codingagent.config.AppPaths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link SessionManager}'s public lifecycle methods, focusing on the
 * {@code createSession(cwd, sessionId)} overload added so that external
 * identifiers (e.g. WS conversation_id) can drive the JSONL filename.
 */
class SessionManagerTest {

    private Path createdDir;

    @AfterEach
    void cleanup() throws Exception {
        if (createdDir != null && Files.isDirectory(createdDir)) {
            try (var stream = Files.walk(createdDir)) {
                stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    void createSessionWithExternalIdUsesProvidedId() {
        SessionManager sm = new SessionManager();
        String externalId =
                "ws-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String cwd = uniqueCwd();

        sm.createSession(cwd, externalId);

        assertEquals(
                externalId,
                sm.getSessionId(),
                "createSession(cwd, id) must honour the external id rather than generating one");
        Path file = sm.getSessionFile();
        assertNotNull(file, "session file must be set after createSession");
        assertEquals(
                externalId + ".jsonl",
                file.getFileName().toString(),
                "JSONL filename must equal the external id so reconnects can map back to it");
        createdDir = file.getParent();
        sm.close();
    }

    @Test
    void loadsLegacyJsonlWithoutRoleDiscriminator() throws Exception {
        // Synthesize a JSONL file in the exact shape the old buggy SessionManager
        // produced — message objects without a `"role"` field. Walking such a
        // file used to silently drop every entry on resume; with the backfill
        // helper, we should reconstruct the conversation by inferring roles
        // from surviving fields.
        String cwd = uniqueCwd();
        String externalId =
                "legacy-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        SessionManager seeder = new SessionManager();
        seeder.createSession(cwd, externalId);
        Path file = seeder.getSessionFile();
        createdDir = file.getParent();
        seeder.close();

        // Append three legacy lines: user (no role, no metadata), assistant
        // (no role, but has provider/api), and a tool result (no role, but
        // has toolCallId+toolName).
        var legacy = ""
                + "{\"type\":\"message\",\"id\":\"u1\",\"timestamp\":\"2026-04-01T00:00:00Z\","
                + "\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"hi from past\"}],\"timestamp\":1}}\n"
                + "{\"type\":\"message\",\"id\":\"a1\",\"parentId\":\"u1\",\"timestamp\":\"2026-04-01T00:00:01Z\","
                + "\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"hello back\"}],"
                + "\"api\":\"messages\",\"provider\":\"anthropic\",\"model\":\"sonnet\","
                + "\"stopReason\":\"stop\",\"timestamp\":2}}\n"
                + "{\"type\":\"message\",\"id\":\"t1\",\"parentId\":\"a1\",\"timestamp\":\"2026-04-01T00:00:02Z\","
                + "\"message\":{\"toolCallId\":\"call-1\",\"toolName\":\"bash\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],\"isError\":false,\"timestamp\":3}}\n";
        Files.writeString(file, legacy, java.nio.file.StandardOpenOption.APPEND);

        SessionManager loader = new SessionManager();
        List<Message> restored = loader.loadSession(file);

        assertEquals(3, restored.size(), "all three legacy messages must be restored, not silently dropped");
        assertTrue(
                restored.get(0) instanceof com.campusclaw.ai.types.UserMessage,
                "no role + bare content/timestamp → UserMessage");
        assertTrue(
                restored.get(1) instanceof AssistantMessage,
                "no role but provider/model/stopReason → AssistantMessage");
        assertTrue(
                restored.get(2) instanceof com.campusclaw.ai.types.ToolResultMessage,
                "no role but toolCallId+toolName → ToolResultMessage");
        loader.close();
    }

    @Test
    void appendsAndLoadsRoundTripWithExternalId() {
        String externalId =
                "ws-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String cwd = uniqueCwd();

        SessionManager writer = new SessionManager();
        writer.createSession(cwd, externalId);
        Path file = writer.getSessionFile();
        createdDir = file.getParent();

        UserMessage user = new UserMessage("hello", System.currentTimeMillis());
        AssistantMessage assistant = new AssistantMessage(
                List.<ContentBlock>of(new TextContent("hi back", null)),
                "messages",
                "anthropic",
                "sonnet",
                null,
                Usage.empty(),
                StopReason.STOP,
                null,
                System.currentTimeMillis());
        writer.appendMessage(user);
        writer.appendMessage(assistant);
        writer.close();

        // Reload via a fresh SessionManager and verify the messages survive a restart.
        SessionManager reader = new SessionManager();
        List<Message> restored = reader.loadSession(file);
        assertEquals(2, restored.size(), "both user + assistant messages should be restored");
        assertTrue(restored.get(0) instanceof UserMessage, "first message must be UserMessage");
        assertTrue(restored.get(1) instanceof AssistantMessage, "second message must be AssistantMessage");
        assertEquals(
                externalId,
                reader.getSessionId(),
                "loadSession should restore the externally-supplied sessionId from the header");
        reader.close();
    }

    @Test
    void createSessionWithoutIdGeneratesEightCharId() {
        SessionManager sm = new SessionManager();
        sm.createSession(uniqueCwd());
        try {
            assertNotNull(sm.getSessionId(), "auto-generated id must be set");
            assertEquals(8, sm.getSessionId().length(), "auto-generated id must be 8 chars");
            assertNotNull(sm.getSessionFile(), "session file must be assigned");
            createdDir = sm.getSessionFile().getParent();
        } finally {
            sm.close();
        }
    }

    @Test
    void appendModelChangeWritesEntry() throws Exception {
        SessionManager writer = new SessionManager();
        String externalId =
                "mc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        writer.createSession(uniqueCwd(), externalId);
        Path file = writer.getSessionFile();
        createdDir = file.getParent();

        writer.appendModelChange("anthropic", "claude-sonnet");
        writer.appendThinkingLevelChange("high");
        writer.appendSessionName("my-experiment");
        writer.close();

        String body = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("\"type\":\"model_change\""), "model_change entry should be present");
        assertTrue(body.contains("\"provider\":\"anthropic\""));
        assertTrue(body.contains("\"modelId\":\"claude-sonnet\""));
        assertTrue(body.contains("\"type\":\"thinking_level_change\""));
        assertTrue(body.contains("\"thinkingLevel\":\"high\""));
        assertTrue(body.contains("\"type\":\"session_info\""));
        assertTrue(body.contains("\"name\":\"my-experiment\""));
    }

    @Test
    void appendMethodsAreNoOpsWhenNoSessionFile() {
        SessionManager sm = new SessionManager();

        // No createSession call yet — sessionFile is null. All these should silently no-op.
        sm.appendMessage(new UserMessage("ignored", 1L));
        sm.appendModelChange("p", "m");
        sm.appendThinkingLevelChange("low");
        sm.appendSessionName("name");
        assertNotNull(sm, "no exception expected from append-* on idle SessionManager");
    }

    @Test
    void loadSessionReturnsEmptyForMissingFile() {
        SessionManager sm = new SessionManager();
        List<Message> messages = sm.loadSession(Path.of("/tmp/definitely-missing-" + UUID.randomUUID() + ".jsonl"));
        assertTrue(messages.isEmpty(), "loadSession on a missing file returns empty list, never throws");
    }

    @Test
    void loadSessionReturnsEmptyForFileWithoutSessionHeader() throws Exception {
        Path tmp = Files.createTempFile("orphan-session-", ".jsonl");
        try {
            Files.writeString(
                    tmp,
                    "{\"type\":\"message\",\"id\":\"x\",\"message\":{\"role\":\"user\",\"content\":[],\"timestamp\":1}}\n",
                    java.nio.charset.StandardCharsets.UTF_8);
            SessionManager sm = new SessionManager();
            assertTrue(
                    sm.loadSession(tmp).isEmpty(),
                    "a JSONL file whose first line isn't a session header is rejected wholesale");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void loadSessionSkipsMalformedLinesWithoutDroppingValidEntries() throws Exception {
        String externalId =
                "mal-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        SessionManager seeder = new SessionManager();
        String cwd = uniqueCwd();
        seeder.createSession(cwd, externalId);
        Path file = seeder.getSessionFile();
        createdDir = file.getParent();
        seeder.close();

        Files.writeString(
                file,
                "\n"
                        + "{ not valid json }\n"
                        + "{\"type\":\"message\",\"id\":\"u1\",\"message\":{\"role\":\"user\","
                        + "\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],\"timestamp\":1}}\n",
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);

        SessionManager loader = new SessionManager();
        List<Message> restored = loader.loadSession(file);
        assertEquals(1, restored.size(), "blank line + malformed line are skipped, valid entry survives");
        assertTrue(restored.get(0) instanceof UserMessage);
        loader.close();
    }

    @Test
    void resumeLatestSessionReturnsEmptyForUnknownCwd() {
        SessionManager sm = new SessionManager();

        // Random cwd that has never had a session — sessionDir doesn't exist
        String cwd = "/tmp/unknown-" + UUID.randomUUID().toString().substring(0, 8);
        assertTrue(sm.resumeLatestSession(cwd).isEmpty());
    }

    @Test
    void resumeLatestSessionPicksMostRecentFileForCwd() throws Exception {
        String cwd = uniqueCwd();

        SessionManager older = new SessionManager();
        older.createSession(cwd, "older-" + UUID.randomUUID().toString().substring(0, 8));
        older.appendMessage(new UserMessage("old message", 1L));
        Path olderFile = older.getSessionFile();
        createdDir = olderFile.getParent();
        older.close();

        // Force older file to have an older mtime than the second one we're about to write.
        olderFile.toFile().setLastModified(System.currentTimeMillis() - 60_000L);

        SessionManager newer = new SessionManager();
        newer.createSession(cwd, "newer-" + UUID.randomUUID().toString().substring(0, 8));
        newer.appendMessage(new UserMessage("new message", 2L));
        Path newerFile = newer.getSessionFile();
        newer.close();
        newerFile.toFile().setLastModified(System.currentTimeMillis());

        SessionManager resumer = new SessionManager();
        List<Message> restored = resumer.resumeLatestSession(cwd);
        assertEquals(1, restored.size());
        UserMessage userMessage = (UserMessage) restored.get(0);
        TextContent tc = (TextContent) userMessage.content().get(0);
        assertEquals("new message", tc.text(), "the most recent session file should be the one resumed");
        resumer.close();
    }

    @Test
    void closeIsSafeToCallTwice() {
        SessionManager sm = new SessionManager();
        sm.createSession(uniqueCwd());
        createdDir = sm.getSessionFile().getParent();
        sm.appendMessage(new UserMessage("once", 1L));
        sm.close();
        sm.close(); // second call should be a no-op rather than throwing
        assertNotNull(sm.getSessionFile(), "sessionFile reference must survive close()");
    }

    @Test
    void inferRoleDistinguishesUserAssistantAndToolResult() {
        assertEquals(
                "toolResult",
                SessionManager.inferRole(java.util.Map.of("toolCallId", "1", "toolName", "bash")),
                "toolCallId+toolName combo identifies a ToolResultMessage");
        assertEquals(
                "assistant",
                SessionManager.inferRole(java.util.Map.of("provider", "anthropic")),
                "any of the assistant-only metadata keys identifies an AssistantMessage");
        assertEquals(
                "assistant",
                SessionManager.inferRole(java.util.Map.of("stopReason", "stop")),
                "stopReason is also an assistant signature");
        assertEquals(
                "user",
                SessionManager.inferRole(java.util.Map.of("content", java.util.List.of())),
                "bare content with no metadata defaults to UserMessage");
    }

    @Test
    void backfillRoleIfMissingIsIdempotentWhenRolePresent() {
        java.util.Map<String, Object> existing = new java.util.HashMap<>();
        existing.put("role", "assistant");
        existing.put("provider", "anthropic");
        SessionManager.backfillRoleIfMissing(existing);
        assertEquals("assistant", existing.get("role"), "explicit role must be preserved verbatim");
    }

    /**
     * Returns a per-test cwd string that encodes into a unique subfolder under
     * {@link AppPaths#SESSIONS_DIR}, so concurrent test runs don't collide and
     * {@link #cleanup()} can scrub the whole subdirectory after each test.
     *
     * @return the result
     */
    private static String uniqueCwd() {
        return "/tmp/campusclaw-sm-test-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
