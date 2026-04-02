package com.huawei.hicampus.mate.matecampusclaw.codingagent;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService;
import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.provider.ApiProvider;
import com.huawei.hicampus.mate.matecampusclaw.ai.provider.ApiProviderRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt.SystemPromptBuilder;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.AgentSession;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.SessionConfig;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.SessionPersistence;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.skill.SkillExpander;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.skill.SkillLoader;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash.BashExecutor;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash.BashTool;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.edit.EditTool;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.EditOperations;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.LocalReadOperations;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.LocalWriteOperations;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.read.ReadTool;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.write.WriteTool;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.FileMutationQueue;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * CLI end-to-end integration tests (IT-003).
 *
 * <p>Manually wires the full CLI pipeline components (tools, session, skill system,
 * persistence) with a scripted mock {@link ApiProvider}, exercising:
 * <ul>
 *   <li>One-shot mode end-to-end</li>
 *   <li>Tool execution (bash, read, write, edit)</li>
 *   <li>Skill loading and expansion</li>
 *   <li>Session persistence (save → load → verify)</li>
 * </ul>
 */
@Timeout(30)
class CliIntegrationTest {

    @TempDir Path rawTempDir;
    private Path tempDir;

    private ScriptedMockProvider mockProvider;
    private CampusClawAiService piAiService;
    private ModelRegistry modelRegistry;
    private SystemPromptBuilder promptBuilder;
    private ObjectMapper objectMapper;
    private FileMutationQueue mutationQueue;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = rawTempDir.toRealPath();
        mockProvider = new ScriptedMockProvider();
        var providerRegistry = new ApiProviderRegistry(List.of(mockProvider));
        modelRegistry = new ModelRegistry();
        modelRegistry.register(new Model(
                "claude-sonnet-4-20250514", "Claude Sonnet 4",
                Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
                "https://api.anthropic.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(3.0, 15.0, 0.3, 3.75),
                200_000, 16_000, null, null,
                null
        ));
        piAiService = new CampusClawAiService(providerRegistry, modelRegistry);
        promptBuilder = new SystemPromptBuilder();
        objectMapper = new ObjectMapper();
        mutationQueue = new FileMutationQueue();
    }

    private List<AgentTool> createTools() {
        var readOps = new LocalReadOperations();
        var writeOps = new LocalWriteOperations();
        var editOps = new LocalEditOps();
        return List.of(
                new BashTool(new BashExecutor(), tempDir),
                new ReadTool(readOps, tempDir),
                new WriteTool(writeOps, mutationQueue, tempDir),
                new EditTool(editOps, mutationQueue, tempDir)
        );
    }

    /** Combines read+write for EditTool. */
    private static class LocalEditOps implements EditOperations {
        private final LocalReadOperations read = new LocalReadOperations();
        private final LocalWriteOperations write = new LocalWriteOperations();

        @Override public byte[] readFile(Path path) throws java.io.IOException { return read.readFile(path); }
        @Override public boolean exists(Path path) { return read.exists(path); }
        @Override public String detectMimeType(Path path) throws java.io.IOException { return read.detectMimeType(path); }
        @Override public void writeFile(Path path, String content) throws java.io.IOException { write.writeFile(path, content); }
        @Override public void mkdir(Path path) throws java.io.IOException { write.mkdir(path); }
    }

    private AgentSession createSession() {
        return createSession(null);
    }

    private AgentSession createSession(String customPrompt) {
        var session = new AgentSession(
                piAiService, modelRegistry, promptBuilder,
                new SkillLoader(), new SkillExpander(), createTools()
        );
        session.initialize(new SessionConfig(
                "claude-sonnet-4-20250514", tempDir, customPrompt, "one-shot"
        ));
        return session;
    }

    // -------------------------------------------------------------------
    // One-shot mode end-to-end
    // -------------------------------------------------------------------

    @Nested
    class OneShotModeEndToEnd {

        @Test
        void simpleTextResponseEndToEnd() {
            mockProvider.setScript(List.of(textReply("Hello from the LLM!")));

            var session = createSession();
            session.prompt("Say hello").join();

            var history = session.getHistory();
            assertFalse(history.isEmpty());
            assertInstanceOf(AssistantMessage.class, history.getLast());
            assertEquals("Hello from the LLM!", contentText(history.getLast()));
            assertNull(session.getAgent().getState().getError());
        }

        @Test
        void messageHistoryIsPopulatedAfterExecution() {
            mockProvider.setScript(List.of(textReply("Response text")));

            var session = createSession();
            session.prompt("Test prompt").join();

            var history = session.getHistory();
            assertTrue(history.size() >= 2);
            assertInstanceOf(UserMessage.class, history.getFirst());
            assertInstanceOf(AssistantMessage.class, history.getLast());
        }

        @Test
        void eventSubscriptionWorksEndToEnd() {
            mockProvider.setScript(List.of(textReply("Events test")));

            var session = createSession();
            var events = new CopyOnWriteArrayList<AgentEvent>();
            session.getAgent().subscribe(events::add);

            session.prompt("Check events").join();

            assertTrue(events.stream().anyMatch(AgentStartEvent.class::isInstance));
            assertTrue(events.stream().anyMatch(AgentEndEvent.class::isInstance));
        }

        @Test
        void customSystemPromptIsIncluded() {
            mockProvider.setScript(List.of(textReply("OK")));

            var session = createSession("Always respond in French.");
            session.prompt("Hi").join();

            var history = session.getHistory();
            assertFalse(history.isEmpty());
        }
    }

    // -------------------------------------------------------------------
    // Tool execution end-to-end
    // -------------------------------------------------------------------

    @Nested
    class ToolExecutionEndToEnd {

        @Test
        void bashToolEndToEnd() {
            mockProvider.setScript(List.of(
                    toolCallReply("bash", Map.of("command", "echo hello-world")),
                    textReply("The command output was hello-world")
            ));

            var session = createSession();
            session.prompt("Run echo").join();

            var history = session.getHistory();
            assertTrue(history.size() >= 4);

            var toolResult = findToolResult(history, "bash");
            assertNotNull(toolResult);
            assertFalse(toolResult.isError());
            assertTrue(contentText(toolResult).contains("hello-world"));
        }

        @Test
        void readToolEndToEnd() throws Exception {
            Path testFile = tempDir.resolve("test-read.txt");
            Files.writeString(testFile, "line1\nline2\nline3\n");

            mockProvider.setScript(List.of(
                    toolCallReply("read", Map.of("path", testFile.toString())),
                    textReply("The file has 3 lines")
            ));

            var session = createSession();
            session.prompt("Read the file").join();

            var toolResult = findToolResult(session.getHistory(), "read");
            assertNotNull(toolResult);
            assertFalse(toolResult.isError(), "Tool returned error: " + contentText(toolResult));
            assertTrue(contentText(toolResult).contains("line1"));
            assertTrue(contentText(toolResult).contains("line2"));
        }

        @Test
        void writeToolEndToEnd() throws Exception {
            Path targetFile = tempDir.resolve("test-write.txt");

            mockProvider.setScript(List.of(
                    toolCallReply("write", Map.of(
                            "path", targetFile.toString(),
                            "content", "written by tool"
                    )),
                    textReply("File written successfully")
            ));

            var session = createSession();
            session.prompt("Write a file").join();

            assertTrue(Files.exists(targetFile));
            assertEquals("written by tool", Files.readString(targetFile));

            var toolResult = findToolResult(session.getHistory(), "write");
            assertNotNull(toolResult);
            assertFalse(toolResult.isError());
        }

        @Test
        void editToolEndToEnd() throws Exception {
            Path editFile = tempDir.resolve("test-edit.txt");
            Files.writeString(editFile, "hello world\ngoodbye world\n");

            mockProvider.setScript(List.of(
                    toolCallReply("edit", Map.of(
                            "path", editFile.toString(),
                            "oldText", "hello world",
                            "newText", "hello universe"
                    )),
                    textReply("Edit applied")
            ));

            var session = createSession();
            session.prompt("Edit the file").join();

            String content = Files.readString(editFile);
            assertTrue(content.contains("hello universe"));
            assertFalse(content.contains("hello world"));

            var toolResult = findToolResult(session.getHistory(), "edit");
            assertNotNull(toolResult);
            assertFalse(toolResult.isError());
        }

        @Test
        void multiToolCallCycle() throws Exception {
            Path file = tempDir.resolve("multi-tool.txt");

            mockProvider.setScript(List.of(
                    toolCallReply("write", Map.of(
                            "path", file.toString(),
                            "content", "initial content"
                    )),
                    toolCallReply("read", Map.of("path", file.toString())),
                    textReply("The file contains: initial content")
            ));

            var session = createSession();
            session.prompt("Write then read a file").join();

            var history = session.getHistory();
            assertTrue(history.size() >= 6);
            assertTrue(Files.exists(file));
            assertEquals("initial content", Files.readString(file));
        }
    }

    // -------------------------------------------------------------------
    // Skill loading and expansion
    // -------------------------------------------------------------------

    @Nested
    class SkillLoadingAndExpansion {

        @Test
        void loadsSkillsFromProjectDirectory() throws Exception {
            Path skillDir = tempDir.resolve(".campusclaw/skills/test-skill");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: test-skill
                    description: A test skill for integration testing
                    ---
                    This is the skill body content.
                    """);

            var session = createSession();

            var skills = session.getSkillRegistry().getAll();
            assertTrue(skills.stream().anyMatch(s -> s.name().equals("test-skill")));
        }

        @Test
        void expandsSkillCommandInPrompt() throws Exception {
            Path skillDir = tempDir.resolve(".campusclaw/skills/greet");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: greet
                    description: Greeting skill
                    ---
                    Always greet the user warmly.
                    """);

            mockProvider.setScript(List.of(textReply("Warm greeting!")));

            var session = createSession();
            session.prompt("/skill:greet Hello").join();

            var history = session.getHistory();
            assertFalse(history.isEmpty());
            var userText = contentText(history.getFirst());
            assertTrue(userText.contains("Always greet the user warmly."));
            assertTrue(userText.contains("Hello"));
        }

        @Test
        void disabledSkillNotVisibleInRegistry() throws Exception {
            Path skillDir = tempDir.resolve(".campusclaw/skills/hidden-skill");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: hidden-skill
                    description: A hidden skill
                    disable-model-invocation: true
                    ---
                    Hidden content.
                    """);

            var session = createSession();

            var visible = session.getSkillRegistry().getVisibleSkills();
            assertTrue(visible.stream().noneMatch(s -> s.name().equals("hidden-skill")));

            var all = session.getSkillRegistry().getAll();
            assertTrue(all.stream().anyMatch(s -> s.name().equals("hidden-skill")));
        }
    }

    // -------------------------------------------------------------------
    // Session persistence (save → load → verify)
    // -------------------------------------------------------------------

    @Nested
    class SessionPersistenceEndToEnd {

        @Test
        void savesAndLoadsSessionHistory() {
            mockProvider.setScript(List.of(textReply("Persisted response")));

            var session = createSession();
            session.prompt("Persist me").join();

            var history = session.getHistory();

            Path sessionFile = tempDir.resolve("session.jsonl");
            var persistence = new SessionPersistence(objectMapper);
            persistence.save("test-session", history, sessionFile);

            assertTrue(Files.exists(sessionFile));

            var loaded = persistence.load(sessionFile);

            assertEquals(history.size(), loaded.size());
            assertInstanceOf(UserMessage.class, loaded.getFirst());
            assertTrue(contentText(loaded.getFirst()).contains("Persist me"));
            assertInstanceOf(AssistantMessage.class, loaded.getLast());
            assertTrue(contentText(loaded.getLast()).contains("Persisted response"));
        }

        @Test
        void persistsToolCallHistory() throws Exception {
            Path file = tempDir.resolve("persist-tool.txt");
            Files.writeString(file, "some content");

            mockProvider.setScript(List.of(
                    toolCallReply("read", Map.of("file_path", file.toString())),
                    textReply("Read complete")
            ));

            var session = createSession();
            session.prompt("Read file").join();

            var history = session.getHistory();
            Path sessionFile = tempDir.resolve("tool-session.jsonl");
            var persistence = new SessionPersistence(objectMapper);
            persistence.save("tool-session", history, sessionFile);

            var loaded = persistence.load(sessionFile);

            assertEquals(history.size(), loaded.size());
            assertTrue(loaded.stream().anyMatch(ToolResultMessage.class::isInstance));
            assertTrue(loaded.stream()
                    .filter(AssistantMessage.class::isInstance)
                    .map(AssistantMessage.class::cast)
                    .anyMatch(am -> am.content().stream().anyMatch(ToolCall.class::isInstance)));
        }

        @Test
        void roundTripPreservesMessageTypes() {
            mockProvider.setScript(List.of(textReply("Round trip test")));

            var session = createSession();
            session.prompt("Hello").join();

            var history = session.getHistory();
            Path sessionFile = tempDir.resolve("roundtrip.jsonl");
            var persistence = new SessionPersistence(objectMapper);

            persistence.save("rt", history, sessionFile);
            var loaded = persistence.load(sessionFile);

            for (int i = 0; i < history.size(); i++) {
                assertEquals(history.get(i).getClass(), loaded.get(i).getClass(),
                        "Message type mismatch at index " + i);
            }
        }
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private static Reply textReply(String text) {
        return new Reply(text, null, null);
    }

    private static Reply toolCallReply(String toolName, Map<String, Object> args) {
        return new Reply(null, toolName, args);
    }

    private ToolResultMessage findToolResult(List<Message> history, String toolName) {
        return history.stream()
                .filter(ToolResultMessage.class::isInstance)
                .map(ToolResultMessage.class::cast)
                .filter(tr -> tr.toolName().equals(toolName))
                .findFirst().orElse(null);
    }

    private String contentText(Message message) {
        List<ContentBlock> content;
        if (message instanceof UserMessage um) content = um.content();
        else if (message instanceof AssistantMessage am) content = am.content();
        else if (message instanceof ToolResultMessage tr) content = tr.content();
        else return "";

        return content.stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .reduce("", String::concat);
    }

    // ===================================================================
    // Scripted mock provider
    // ===================================================================

    private record Reply(String text, String toolName, Map<String, Object> toolArgs) {
        boolean isToolCall() { return toolName != null; }
    }

    private static class ScriptedMockProvider implements ApiProvider {

        private volatile List<Reply> script = List.of();
        private final AtomicInteger callIndex = new AtomicInteger(0);

        void setScript(List<Reply> script) {
            this.script = List.copyOf(script);
            callIndex.set(0);
        }

        @Override
        public Api getApi() {
            return Api.ANTHROPIC_MESSAGES;
        }

        @Override
        public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
            throw new UnsupportedOperationException("Uses streamSimple");
        }

        @Override
        public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
            int idx = callIndex.getAndIncrement();
            if (idx >= script.size()) {
                throw new IllegalStateException(
                        "ScriptedMockProvider exhausted: called " + (idx + 1) +
                                " times but only " + script.size() + " replies scripted");
            }
            var reply = script.get(idx);

            if (reply.isToolCall()) {
                return toolCallStream(model, reply.toolName(), reply.toolArgs(), idx);
            }
            return textStream(model, reply.text());
        }

        private AssistantMessageEventStream toolCallStream(
                Model model, String toolName, Map<String, Object> args, int idx) {
            var stream = new AssistantMessageEventStream();
            var toolCall = new ToolCall("tc-" + idx, toolName, args);
            var msg = new AssistantMessage(
                    List.of(toolCall),
                    model.api().value(), model.provider().value(), model.id(),
                    null, Usage.empty(), StopReason.TOOL_USE, null, System.currentTimeMillis()
            );
            stream.push(new AssistantMessageEvent.StartEvent(msg));
            stream.push(new AssistantMessageEvent.ToolCallEndEvent(0, toolCall, msg));
            stream.pushDone(StopReason.TOOL_USE, msg);
            return stream;
        }

        private AssistantMessageEventStream textStream(Model model, String text) {
            var stream = new AssistantMessageEventStream();
            var msg = new AssistantMessage(
                    List.of(new TextContent(text, null)),
                    model.api().value(), model.provider().value(), model.id(),
                    null, Usage.empty(), StopReason.STOP, null, System.currentTimeMillis()
            );
            stream.push(new AssistantMessageEvent.StartEvent(msg));
            stream.push(new AssistantMessageEvent.TextDeltaEvent(0, text, msg));
            stream.pushDone(StopReason.STOP, msg);
            return stream;
        }
    }
}
