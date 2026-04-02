package com.huawei.hicampus.mate.matecampusclaw.codingagent.session;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionPersistenceTest {

    @TempDir Path tempDir;

    ObjectMapper mapper;
    SessionPersistence persistence;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        persistence = new SessionPersistence(mapper);
    }

    // -- Factory helpers --

    private UserMessage userMsg(String text) {
        return new UserMessage(text, 1000L);
    }

    private AssistantMessage assistantMsg(String text) {
        return new AssistantMessage(
                List.of(new TextContent(text, null)),
                "messages", "anthropic", "claude-sonnet-4",
                null, Usage.empty(), StopReason.STOP, null, 2000L
        );
    }

    private ToolResultMessage toolResultMsg(String toolCallId, String toolName, String text, boolean isError) {
        return new ToolResultMessage(
                toolCallId, toolName,
                List.of(new TextContent(text, null)),
                null, isError, 3000L
        );
    }

    // -------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------

    @Nested
    class Save {

        @Test
        void savesMessagesToJsonlFile() throws IOException {
            Path file = tempDir.resolve("session.jsonl");
            List<Message> messages = List.of(
                    userMsg("Hello"),
                    assistantMsg("Hi there")
            );

            persistence.save("sess-1", messages, file);

            assertTrue(Files.exists(file));
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            assertEquals(2, lines.size());
        }

        @Test
        void eachLineIsValidJson() throws IOException {
            Path file = tempDir.resolve("session.jsonl");
            persistence.save("sess-1", List.of(userMsg("test")), file);

            String line = Files.readAllLines(file, StandardCharsets.UTF_8).get(0);
            // Should parse without error
            assertDoesNotThrow(() -> mapper.readTree(line));
        }

        @Test
        void writesRoleDiscriminator() throws IOException {
            Path file = tempDir.resolve("session.jsonl");
            persistence.save("sess-1", List.of(userMsg("hello")), file);

            String line = Files.readAllLines(file, StandardCharsets.UTF_8).get(0);
            assertTrue(line.contains("\"role\":\"user\""));
        }

        @Test
        void savesEmptyListAsEmptyFile() throws IOException {
            Path file = tempDir.resolve("empty.jsonl");
            persistence.save("sess-1", List.of(), file);

            assertTrue(Files.exists(file));
            assertEquals(0, Files.readAllLines(file, StandardCharsets.UTF_8).size());
        }

        @Test
        void createsParentDirectories() {
            Path file = tempDir.resolve("deep/nested/dir/session.jsonl");

            assertDoesNotThrow(
                    () -> persistence.save("sess-1", List.of(userMsg("test")), file));

            assertTrue(Files.exists(file));
        }

        @Test
        void throwsOnNullSessionId() {
            assertThrows(NullPointerException.class,
                    () -> persistence.save(null, List.of(), tempDir.resolve("f")));
        }

        @Test
        void throwsOnNullMessages() {
            assertThrows(NullPointerException.class,
                    () -> persistence.save("s", null, tempDir.resolve("f")));
        }

        @Test
        void throwsOnNullPath() {
            assertThrows(NullPointerException.class,
                    () -> persistence.save("s", List.of(), null));
        }
    }

    // -------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------

    @Nested
    class Load {

        @Test
        void loadsUserMessage() throws IOException {
            Path file = tempDir.resolve("session.jsonl");
            persistence.save("sess-1", List.of(userMsg("Hello")), file);

            List<Message> loaded = persistence.load(file);

            assertEquals(1, loaded.size());
            assertInstanceOf(UserMessage.class, loaded.get(0));
            UserMessage msg = (UserMessage) loaded.get(0);
            assertEquals(1000L, msg.timestamp());

            TextContent content = (TextContent) msg.content().get(0);
            assertEquals("Hello", content.text());
        }

        @Test
        void loadsAssistantMessage() throws IOException {
            Path file = tempDir.resolve("session.jsonl");
            persistence.save("sess-1", List.of(assistantMsg("Response")), file);

            List<Message> loaded = persistence.load(file);

            assertEquals(1, loaded.size());
            assertInstanceOf(AssistantMessage.class, loaded.get(0));
            AssistantMessage msg = (AssistantMessage) loaded.get(0);
            assertEquals("anthropic", msg.provider());
            assertEquals("claude-sonnet-4", msg.model());
            assertEquals(StopReason.STOP, msg.stopReason());
            assertEquals(2000L, msg.timestamp());
        }

        @Test
        void loadsToolResultMessage() throws IOException {
            Path file = tempDir.resolve("session.jsonl");
            persistence.save("sess-1",
                    List.of(toolResultMsg("tc-1", "bash", "output", false)), file);

            List<Message> loaded = persistence.load(file);

            assertEquals(1, loaded.size());
            assertInstanceOf(ToolResultMessage.class, loaded.get(0));
            ToolResultMessage msg = (ToolResultMessage) loaded.get(0);
            assertEquals("tc-1", msg.toolCallId());
            assertEquals("bash", msg.toolName());
            assertFalse(msg.isError());
        }

        @Test
        void loadsToolResultWithErrorFlag() throws IOException {
            Path file = tempDir.resolve("session.jsonl");
            persistence.save("sess-1",
                    List.of(toolResultMsg("tc-2", "bash", "error output", true)), file);

            List<Message> loaded = persistence.load(file);

            ToolResultMessage msg = (ToolResultMessage) loaded.get(0);
            assertTrue(msg.isError());
        }
    }

    // -------------------------------------------------------------------
    // Polymorphic round-trip
    // -------------------------------------------------------------------

    @Nested
    class PolymorphicRoundTrip {

        @Test
        void roundTripsMixedMessageTypes() throws IOException {
            Path file = tempDir.resolve("mixed.jsonl");

            List<Message> original = List.of(
                    userMsg("Build the app"),
                    assistantMsg("Sure, I'll use the bash tool."),
                    toolResultMsg("tc-1", "bash", "Build succeeded", false),
                    assistantMsg("Build completed successfully.")
            );

            persistence.save("sess-round", original, file);
            List<Message> loaded = persistence.load(file);

            assertEquals(4, loaded.size());
            assertInstanceOf(UserMessage.class, loaded.get(0));
            assertInstanceOf(AssistantMessage.class, loaded.get(1));
            assertInstanceOf(ToolResultMessage.class, loaded.get(2));
            assertInstanceOf(AssistantMessage.class, loaded.get(3));
        }

        @Test
        void preservesContentBlocks() throws IOException {
            Path file = tempDir.resolve("content.jsonl");

            AssistantMessage original = new AssistantMessage(
                    List.of(
                            new TextContent("Here is the result", null),
                            new ToolCall("tc-1", "read", Map.of("path", "/foo.txt"), null)
                    ),
                    "messages", "anthropic", "claude-sonnet-4",
                    "resp-123", Usage.empty(), StopReason.TOOL_USE, null, 5000L
            );

            persistence.save("sess-cb", List.of(original), file);
            List<Message> loaded = persistence.load(file);

            AssistantMessage msg = (AssistantMessage) loaded.get(0);
            assertEquals(2, msg.content().size());
            assertInstanceOf(TextContent.class, msg.content().get(0));
            assertInstanceOf(ToolCall.class, msg.content().get(1));

            ToolCall tc = (ToolCall) msg.content().get(1);
            assertEquals("tc-1", tc.id());
            assertEquals("read", tc.name());
            assertEquals("/foo.txt", tc.arguments().get("path"));
        }

        @Test
        void preservesTimestamps() throws IOException {
            Path file = tempDir.resolve("ts.jsonl");
            UserMessage original = new UserMessage("hello", 42L);

            persistence.save("sess-ts", List.of(original), file);
            List<Message> loaded = persistence.load(file);

            assertEquals(42L, ((UserMessage) loaded.get(0)).timestamp());
        }

        @Test
        void preservesUsageAndCost() throws IOException {
            Path file = tempDir.resolve("usage.jsonl");
            Usage usage = new Usage(100, 200, 50, 25, 375, new Cost(0.01, 0.02, 0.005, 0.003, 0.038));
            AssistantMessage original = new AssistantMessage(
                    List.of(new TextContent("test")),
                    "messages", "anthropic", "m", null, usage,
                    StopReason.STOP, null, 1L
            );

            persistence.save("sess-u", List.of(original), file);
            AssistantMessage loaded = (AssistantMessage) persistence.load(file).get(0);

            assertEquals(100, loaded.usage().input());
            assertEquals(200, loaded.usage().output());
            assertEquals(0.038, loaded.usage().cost().total(), 0.0001);
        }
    }

    // -------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------

    @Nested
    class ErrorHandling {

        @Test
        void throwsOnNonexistentFile() {
            Path file = tempDir.resolve("nonexistent.jsonl");
            assertThrows(SessionPersistenceException.class,
                    () -> persistence.load(file));
        }

        @Test
        void throwsOnMalformedJson() throws IOException {
            Path file = tempDir.resolve("bad.jsonl");
            Files.writeString(file, "not valid json\n");

            assertThrows(SessionPersistenceException.class,
                    () -> persistence.load(file));
        }

        @Test
        void throwsOnNullInputPath() {
            assertThrows(NullPointerException.class,
                    () -> persistence.load(null));
        }

        @Test
        void skipsBlankLines() throws IOException {
            Path file = tempDir.resolve("blanks.jsonl");
            persistence.save("sess-1", List.of(userMsg("a"), userMsg("b")), file);

            // Insert blank lines
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Files.writeString(file, "\n" + content + "\n\n");

            List<Message> loaded = persistence.load(file);
            assertEquals(2, loaded.size());
        }

        @Test
        void includesLineNumberInParseError() throws IOException {
            Path file = tempDir.resolve("bad-line.jsonl");
            String validLine = mapper.writeValueAsString(userMsg("ok"));
            Files.writeString(file, validLine + "\n{bad json}\n");

            var ex = assertThrows(SessionPersistenceException.class,
                    () -> persistence.load(file));
            assertTrue(ex.getMessage().contains("line 2"));
        }
    }
}
