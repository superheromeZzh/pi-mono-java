package com.huawei.hicampus.mate.matecampusclaw.codingagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths;

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
        String externalId = "ws-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String cwd = uniqueCwd();

        sm.createSession(cwd, externalId);

        assertEquals(externalId, sm.getSessionId(),
                "createSession(cwd, id) must honour the external id rather than generating one");
        Path file = sm.getSessionFile();
        assertNotNull(file, "session file must be set after createSession");
        assertEquals(externalId + ".jsonl", file.getFileName().toString(),
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
        String externalId = "legacy-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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
                +   "\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"hi from past\"}],\"timestamp\":1}}\n"
                + "{\"type\":\"message\",\"id\":\"a1\",\"parentId\":\"u1\",\"timestamp\":\"2026-04-01T00:00:01Z\","
                +   "\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"hello back\"}],"
                +     "\"api\":\"messages\",\"provider\":\"anthropic\",\"model\":\"sonnet\","
                +     "\"stopReason\":\"stop\",\"timestamp\":2}}\n"
                + "{\"type\":\"message\",\"id\":\"t1\",\"parentId\":\"a1\",\"timestamp\":\"2026-04-01T00:00:02Z\","
                +   "\"message\":{\"toolCallId\":\"call-1\",\"toolName\":\"bash\","
                +     "\"content\":[{\"type\":\"text\",\"text\":\"ok\"}],\"isError\":false,\"timestamp\":3}}\n";
        Files.writeString(file, legacy, java.nio.file.StandardOpenOption.APPEND);

        SessionManager loader = new SessionManager();
        List<Message> restored = loader.loadSession(file);

        assertEquals(3, restored.size(),
                "all three legacy messages must be restored, not silently dropped");
        assertTrue(restored.get(0) instanceof com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage,
                "no role + bare content/timestamp → UserMessage");
        assertTrue(restored.get(1) instanceof AssistantMessage,
                "no role but provider/model/stopReason → AssistantMessage");
        assertTrue(restored.get(2) instanceof com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage,
                "no role but toolCallId+toolName → ToolResultMessage");
        loader.close();
    }

    @Test
    void appendsAndLoadsRoundTripWithExternalId() {
        String externalId = "ws-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String cwd = uniqueCwd();

        SessionManager writer = new SessionManager();
        writer.createSession(cwd, externalId);
        Path file = writer.getSessionFile();
        createdDir = file.getParent();

        UserMessage user = new UserMessage("hello", System.currentTimeMillis());
        AssistantMessage assistant = new AssistantMessage(
                List.<ContentBlock>of(new TextContent("hi back", null)),
                "messages", "anthropic", "sonnet",
                null, Usage.empty(), StopReason.STOP, null, System.currentTimeMillis());
        writer.appendMessage(user);
        writer.appendMessage(assistant);
        writer.close();

        // Reload via a fresh SessionManager and verify the messages survive a restart.
        SessionManager reader = new SessionManager();
        List<Message> restored = reader.loadSession(file);
        assertEquals(2, restored.size(), "both user + assistant messages should be restored");
        assertTrue(restored.get(0) instanceof UserMessage, "first message must be UserMessage");
        assertTrue(restored.get(1) instanceof AssistantMessage, "second message must be AssistantMessage");
        assertEquals(externalId, reader.getSessionId(),
                "loadSession should restore the externally-supplied sessionId from the header");
        reader.close();
    }

    /**
     * Returns a per-test cwd string that encodes into a unique subfolder under
     * {@link AppPaths#SESSIONS_DIR}, so concurrent test runs don't collide and
     * {@link #cleanup()} can scrub the whole subdirectory after each test.
     */
    private static String uniqueCwd() {
        return "/tmp/campusclaw-sm-test-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
