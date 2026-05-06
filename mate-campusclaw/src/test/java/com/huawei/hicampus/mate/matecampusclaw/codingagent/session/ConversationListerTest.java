package com.huawei.hicampus.mate.matecampusclaw.codingagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ConversationLister}'s scan/extract logic against real JSONL
 * files written by {@link SessionManager}, so we cover both the empty-dir
 * path and the typical user→assistant→… history shape produced in
 * production.
 */
class ConversationListerTest {

    private final ConversationLister lister = new ConversationLister();
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
    void emptyOrMissingDirYieldsEmptyList() {
        // Use a fresh cwd that no test has touched — the encoded subdir won't
        // exist on disk.
        String cwd = "/tmp/clister-empty-" + UUID.randomUUID();
        List<ConversationLister.Entry> entries = lister.list(cwd);
        assertTrue(entries.isEmpty(),
                "lister must return an empty list when the sessions dir is missing");
    }

    @Test
    void singleSessionYieldsTitleAndMessageCount() {
        String cwd = uniqueCwd();
        String id = "test-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        SessionManager sm = new SessionManager();
        sm.createSession(cwd, id);
        createdDir = sm.getSessionFile().getParent();

        sm.appendMessage(new UserMessage("用 Java 写一个快速排序", System.currentTimeMillis()));
        sm.appendMessage(new AssistantMessage(
                List.<ContentBlock>of(new TextContent("ok", null)),
                "messages", "anthropic", "sonnet",
                null, Usage.empty(), StopReason.STOP, null, System.currentTimeMillis()));
        sm.close();

        List<ConversationLister.Entry> entries = lister.list(cwd);
        assertEquals(1, entries.size(), "exactly one conversation should be listed");
        ConversationLister.Entry entry = entries.get(0);
        assertEquals(id, entry.id(), "id must match the JSONL filename");
        assertEquals("用 Java 写一个快速排序", entry.title(),
                "title should come from the first user TextContent");
        assertEquals(2, entry.messageCount(), "messageCount counts type:message entries only");
    }

    @Test
    void titleFallsBackToIdWhenNoUserMessage() {
        String cwd = uniqueCwd();
        String id = "noTitle-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        SessionManager sm = new SessionManager();
        sm.createSession(cwd, id);
        createdDir = sm.getSessionFile().getParent();
        sm.close();

        ConversationLister.Entry entry = lister.list(cwd).get(0);
        assertEquals(id, entry.title(),
                "with no user message, title should fall back to the id");
        assertEquals(0, entry.messageCount());
    }

    @Test
    void resultsSortedByUpdatedAtDescending() throws Exception {
        String cwd = uniqueCwd();

        // Older session: write, close, then bump mtime backwards.
        SessionManager older = new SessionManager();
        older.createSession(cwd, "older-id");
        createdDir = older.getSessionFile().getParent();
        older.appendMessage(new UserMessage("old prompt", 1L));
        older.close();
        Files.setLastModifiedTime(older.getSessionFile(),
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 60_000));

        SessionManager newer = new SessionManager();
        newer.createSession(cwd, "newer-id");
        newer.appendMessage(new UserMessage("new prompt", 2L));
        newer.close();

        List<ConversationLister.Entry> entries = lister.list(cwd);
        assertEquals(2, entries.size());
        assertEquals("newer-id", entries.get(0).id(), "newer file must come first");
        assertEquals("older-id", entries.get(1).id());
    }

    @Test
    void nonJsonlFilesIgnored() throws Exception {
        String cwd = uniqueCwd();

        SessionManager sm = new SessionManager();
        sm.createSession(cwd, "real-id");
        createdDir = sm.getSessionFile().getParent();
        sm.close();

        // Drop a stray non-jsonl file in the same directory.
        Files.writeString(createdDir.resolve("notes.txt"), "ignore me");

        List<ConversationLister.Entry> entries = lister.list(cwd);
        assertEquals(1, entries.size(), "non-.jsonl entries must be ignored");
        assertEquals("real-id", entries.get(0).id());
        assertFalse(entries.stream().anyMatch(e -> e.id().contains("notes")));
    }

    @Test
    void legacyJsonlWithoutRoleStillYieldsTitle() throws Exception {
        // Legacy files written before the role-discriminator fix have message
        // objects with no `"role"`. Title extraction used to fail and fall
        // back to the hex id; with the role-inference fallback it should now
        // extract the user prompt correctly.
        String cwd = uniqueCwd();
        SessionManager seeder = new SessionManager();
        seeder.createSession(cwd, "legacy");
        Path file = seeder.getSessionFile();
        createdDir = file.getParent();
        seeder.close();

        Files.writeString(file,
                "{\"type\":\"message\",\"id\":\"u1\",\"timestamp\":\"2026-04-01T00:00:00Z\","
                        + "\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"用 Java 写一个快速排序\"}],\"timestamp\":1}}\n",
                java.nio.file.StandardOpenOption.APPEND);

        ConversationLister.Entry entry = lister.list(cwd).get(0);
        assertEquals("用 Java 写一个快速排序", entry.title(),
                "title should be inferred from a legacy user message that lacks role");
    }

    @Test
    void wireFormatHasExpectedKeys() {
        String cwd = uniqueCwd();
        SessionManager sm = new SessionManager();
        sm.createSession(cwd, "wire-id");
        createdDir = sm.getSessionFile().getParent();
        sm.appendMessage(new UserMessage("hi", 1L));
        sm.close();

        var wire = ConversationLister.toWireFormat(lister.list(cwd));
        assertEquals(1, wire.size());
        var entry = wire.get(0);
        assertTrue(entry.containsKey("id"));
        assertTrue(entry.containsKey("title"));
        assertTrue(entry.containsKey("messageCount"));
        assertTrue(entry.containsKey("createdAt"));
        assertTrue(entry.containsKey("updatedAt"));
    }

    private static String uniqueCwd() {
        return "/tmp/clister-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
