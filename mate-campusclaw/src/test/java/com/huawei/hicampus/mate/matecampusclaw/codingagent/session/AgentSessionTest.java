package com.huawei.hicampus.mate.matecampusclaw.codingagent.session;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.huawei.hicampus.mate.matecampusclaw.agent.Agent;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService;
import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt.SystemPromptBuilder;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.skill.SkillExpander;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.skill.SkillLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AgentSessionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock CampusClawAiService piAiService;
    @Mock SystemPromptBuilder promptBuilder;

    @TempDir Path tempDir;

    ModelRegistry modelRegistry;
    SkillLoader skillLoader;
    SkillExpander skillExpander;
    List<AgentTool> tools;
    AgentTool stubTool;

    AgentSession session;

    @BeforeEach
    void setUp() {
        modelRegistry = new ModelRegistry();
        // Register test models (init() is package-private)
        modelRegistry.register(new Model(
                "claude-sonnet-4-20250514", "Claude Sonnet 4",
                Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
                "https://api.anthropic.com", true,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(3.0, 15.0, 0.3, 3.75),
                200000, 16000, null, null,
                null
        ));
        modelRegistry.register(new Model(
                "gpt-4o", "GPT-4o",
                Api.OPENAI_RESPONSES, Provider.OPENAI,
                "https://api.openai.com", false,
                List.of(InputModality.TEXT, InputModality.IMAGE),
                new ModelCost(2.5, 10.0, 1.25, 2.5),
                128000, 16384, null, null,
                null
        ));

        skillLoader = new SkillLoader();
        skillExpander = new SkillExpander();
        stubTool = new StubTool("bash", "Execute commands");
        tools = List.of(stubTool);

        session = createSession();
    }

    private AgentSession createSession() {
        return new TestableAgentSession(
                piAiService, modelRegistry, promptBuilder,
                skillLoader, skillExpander, tools
        );
    }

    private SessionConfig config() {
        return new SessionConfig("claude-sonnet-4-20250514", tempDir, null, "interactive");
    }

    private SessionConfig configWithModel(String model) {
        return new SessionConfig(model, tempDir, null, "interactive");
    }

    // -------------------------------------------------------------------
    // SessionConfig
    // -------------------------------------------------------------------

    @Nested
    class SessionConfigTests {

        @Test
        void recordFieldsAccessible() {
            var config = new SessionConfig("model-id", Path.of("/cwd"), "custom", "one-shot");
            assertEquals("model-id", config.model());
            assertEquals(Path.of("/cwd"), config.cwd());
            assertEquals("custom", config.customPrompt());
            assertEquals("one-shot", config.mode());
        }

        @Test
        void allowsNullFields() {
            var config = new SessionConfig(null, null, null, null);
            assertNull(config.model());
            assertNull(config.cwd());
        }
    }

    // -------------------------------------------------------------------
    // initialize
    // -------------------------------------------------------------------

    @Nested
    class Initialize {

        @Test
        void initializesSuccessfully() {
            when(promptBuilder.build(any())).thenReturn("system prompt");

            session.initialize(config());

            assertTrue(session.isInitialized());
            assertNotNull(session.getAgent());
        }

        @Test
        void resolvesModelFromRegistry() {
            when(promptBuilder.build(any())).thenReturn("prompt");

            session.initialize(config());

            // The agent should have been configured — verify via the model set on agent
            assertTrue(session.isInitialized());
        }

        @Test
        void usesDefaultModelWhenNull() {
            when(promptBuilder.build(any())).thenReturn("prompt");

            var config = new SessionConfig(null, tempDir, null, "interactive");
            session.initialize(config);

            assertTrue(session.isInitialized());
        }

        @Test
        void throwsForUnknownModel() {
            assertThrows(IllegalArgumentException.class,
                    () -> session.initialize(configWithModel("nonexistent-model")));
        }

        @Test
        void throwsOnDoubleInitialization() {
            when(promptBuilder.build(any())).thenReturn("prompt");

            session.initialize(config());

            assertThrows(IllegalStateException.class,
                    () -> session.initialize(config()));
        }

        @Test
        void throwsOnNullConfig() {
            assertThrows(NullPointerException.class,
                    () -> session.initialize(null));
        }

        @Test
        void buildsSystemPromptWithTools() {
            when(promptBuilder.build(any())).thenReturn("built prompt");

            session.initialize(config());

            var captor = ArgumentCaptor.forClass(
                    com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt.SystemPromptConfig.class);
            verify(promptBuilder).build(captor.capture());

            var promptConfig = captor.getValue();
            assertEquals(1, promptConfig.tools().size());
            assertEquals("bash", promptConfig.tools().get(0).name());
            assertEquals(tempDir, promptConfig.cwd());
        }

        @Test
        void passesCustomPromptToBuilder() {
            when(promptBuilder.build(any())).thenReturn("prompt");

            var config = new SessionConfig("claude-sonnet-4-20250514", tempDir, "Be concise.", "interactive");
            session.initialize(config);

            var captor = ArgumentCaptor.forClass(
                    com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt.SystemPromptConfig.class);
            verify(promptBuilder).build(captor.capture());

            assertEquals("Be concise.", captor.getValue().customPrompt());
        }

        @Test
        void registersTools() {
            when(promptBuilder.build(any())).thenReturn("prompt");

            session.initialize(config());

            // Agent should have received the tools
            Agent agent = session.getAgent();
            assertNotNull(agent);
        }
    }

    // -------------------------------------------------------------------
    // Skill loading
    // -------------------------------------------------------------------

    @Nested
    class SkillLoading {

        @Test
        void loadsProjectSkills() throws IOException {
            // Create a project-level skill
            Path skillDir = tempDir.resolve(".campusclaw/skills/test-skill");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: test-skill
                    description: A test skill
                    ---
                    Body content.
                    """);

            when(promptBuilder.build(any())).thenReturn("prompt");

            session.initialize(config());

            var registry = session.getSkillRegistry();
            assertTrue(registry.getByName("test-skill").isPresent());
        }

        @Test
        void includesVisibleSkillsInPromptConfig() throws IOException {
            Path skillDir = tempDir.resolve(".campusclaw/skills/visible-skill");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: visible-skill
                    description: A visible skill
                    ---
                    Body.
                    """);

            when(promptBuilder.build(any())).thenReturn("prompt");

            session.initialize(config());

            var captor = ArgumentCaptor.forClass(
                    com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt.SystemPromptConfig.class);
            verify(promptBuilder).build(captor.capture());

            var skills = captor.getValue().skills();
            assertEquals(1, skills.size());
            assertEquals("visible-skill", skills.get(0).name());
        }

        @Test
        void excludesHiddenSkillsFromPromptConfig() throws IOException {
            Path skillDir = tempDir.resolve(".campusclaw/skills/hidden-skill");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: hidden-skill
                    description: A hidden skill
                    disable-model-invocation: true
                    ---
                    Body.
                    """);

            when(promptBuilder.build(any())).thenReturn("prompt");

            session.initialize(config());

            var captor = ArgumentCaptor.forClass(
                    com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt.SystemPromptConfig.class);
            verify(promptBuilder).build(captor.capture());

            // visibleSkills should be empty since the only skill is hidden
            assertTrue(captor.getValue().skills().isEmpty());

            // But the registry should still contain it
            assertTrue(session.getSkillRegistry().getByName("hidden-skill").isPresent());
        }
    }

    // -------------------------------------------------------------------
    // prompt
    // -------------------------------------------------------------------

    @Nested
    class Prompt {

        @Test
        void throwsWhenNotInitialized() {
            assertThrows(IllegalStateException.class,
                    () -> session.prompt("hello"));
        }

        @Test
        void throwsOnNullInput() {
            when(promptBuilder.build(any())).thenReturn("prompt");
            session.initialize(config());

            assertThrows(NullPointerException.class,
                    () -> session.prompt(null));
        }

        @Test
        void delegatesToAgent() {
            when(promptBuilder.build(any())).thenReturn("prompt");
            session.initialize(config());

            // Prompting with regular text should pass through
            // Agent.prompt will fail because there's no real LLM, but the call
            // should at least be made. We verify via the mock agent.
            var testSession = (TestableAgentSession) session;
            Agent mockAgent = testSession.getMockAgent();

            var future = CompletableFuture.completedFuture((Void) null);
            when(mockAgent.prompt(anyString())).thenReturn(future);

            CompletableFuture<Void> result = session.prompt("hello world");
            assertSame(future, result);
            verify(mockAgent).prompt("hello world");
        }

        @Test
        void expandsSkillCommands() throws IOException {
            // Set up a skill
            Path skillDir = tempDir.resolve(".campusclaw/skills/my-skill");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), """
                    ---
                    name: my-skill
                    description: A test skill
                    ---
                    Skill instructions here.
                    """);

            when(promptBuilder.build(any())).thenReturn("prompt");
            session.initialize(config());

            var testSession = (TestableAgentSession) session;
            Agent mockAgent = testSession.getMockAgent();
            when(mockAgent.prompt(anyString())).thenReturn(CompletableFuture.completedFuture(null));

            session.prompt("/skill:my-skill some args");

            var captor = ArgumentCaptor.forClass(String.class);
            verify(mockAgent).prompt(captor.capture());

            String expanded = captor.getValue();
            assertTrue(expanded.contains("<skill name=\"my-skill\""));
            assertTrue(expanded.contains("Skill instructions here."));
            assertTrue(expanded.contains("some args"));
        }

        @Test
        void passesNonSkillInputUnchanged() {
            when(promptBuilder.build(any())).thenReturn("prompt");
            session.initialize(config());

            var testSession = (TestableAgentSession) session;
            Agent mockAgent = testSession.getMockAgent();
            when(mockAgent.prompt(anyString())).thenReturn(CompletableFuture.completedFuture(null));

            session.prompt("regular input");

            verify(mockAgent).prompt("regular input");
        }
    }

    // -------------------------------------------------------------------
    // abort
    // -------------------------------------------------------------------

    @Nested
    class Abort {

        @Test
        void throwsWhenNotInitialized() {
            assertThrows(IllegalStateException.class,
                    () -> session.abort());
        }

        @Test
        void delegatesToAgent() {
            when(promptBuilder.build(any())).thenReturn("prompt");
            session.initialize(config());

            var testSession = (TestableAgentSession) session;
            Agent mockAgent = testSession.getMockAgent();

            session.abort();

            verify(mockAgent).abort();
        }
    }

    // -------------------------------------------------------------------
    // getHistory
    // -------------------------------------------------------------------

    @Nested
    class GetHistory {

        @Test
        void throwsWhenNotInitialized() {
            assertThrows(IllegalStateException.class,
                    () -> session.getHistory());
        }

        @Test
        void returnsAgentHistory() {
            when(promptBuilder.build(any())).thenReturn("prompt");
            session.initialize(config());

            List<Message> history = session.getHistory();
            assertNotNull(history);
            assertTrue(history.isEmpty());
        }
    }

    // -------------------------------------------------------------------
    // getAgent
    // -------------------------------------------------------------------

    @Nested
    class GetAgent {

        @Test
        void throwsWhenNotInitialized() {
            assertThrows(IllegalStateException.class,
                    () -> session.getAgent());
        }

        @Test
        void returnsAgentAfterInit() {
            when(promptBuilder.build(any())).thenReturn("prompt");
            session.initialize(config());

            assertNotNull(session.getAgent());
        }
    }

    // -------------------------------------------------------------------
    // Model resolution
    // -------------------------------------------------------------------

    @Nested
    class ModelResolution {

        @Test
        void resolvesAnthropicModel() {
            Model model = session.resolveModel("claude-sonnet-4-20250514");
            assertEquals("claude-sonnet-4-20250514", model.id());
            assertEquals(Provider.ANTHROPIC, model.provider());
        }

        @Test
        void resolvesOpenAiModel() {
            Model model = session.resolveModel("gpt-4o");
            assertEquals("gpt-4o", model.id());
            assertEquals(Provider.OPENAI, model.provider());
        }

        @Test
        void throwsForUnknown() {
            assertThrows(IllegalArgumentException.class,
                    () -> session.resolveModel("nonexistent"));
        }
    }

    // -------------------------------------------------------------------
    // Environment
    // -------------------------------------------------------------------

    @Nested
    class Environment {

        @Test
        void buildEnvironmentMapIncludesOsName() {
            Map<String, String> env = AgentSession.buildEnvironmentMap();
            assertTrue(env.containsKey("OS_NAME"));
            assertFalse(env.get("OS_NAME").isEmpty());
        }

        @Test
        void buildEnvironmentMapIncludesJavaVersion() {
            Map<String, String> env = AgentSession.buildEnvironmentMap();
            assertTrue(env.containsKey("JAVA_VERSION"));
            assertFalse(env.get("JAVA_VERSION").isEmpty());
        }
    }

    // -------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------

    /**
     * Testable subclass that captures the created Agent as a mock.
     */
    private static class TestableAgentSession extends AgentSession {
        private Agent mockAgent;

        TestableAgentSession(
                CampusClawAiService piAiService,
                ModelRegistry modelRegistry,
                SystemPromptBuilder promptBuilder,
                SkillLoader skillLoader,
                SkillExpander skillExpander,
                List<AgentTool> tools
        ) {
            super(piAiService, modelRegistry, promptBuilder, skillLoader, skillExpander, tools);
        }

        @Override
        Agent createAgent(CampusClawAiService aiService) {
            mockAgent = mock(Agent.class);
            when(mockAgent.getState()).thenReturn(new com.huawei.hicampus.mate.matecampusclaw.agent.state.AgentState());
            return mockAgent;
        }

        Agent getMockAgent() {
            return mockAgent;
        }
    }

    private static class StubTool implements AgentTool {
        private final String name;
        private final String description;

        StubTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override public String name() { return name; }
        @Override public String label() { return name; }
        @Override public String description() { return description; }

        @Override
        public JsonNode parameters() {
            return MAPPER.createObjectNode().put("type", "object");
        }

        @Override
        public AgentToolResult execute(String toolCallId, Map<String, Object> params,
                                       CancellationToken signal, AgentToolUpdateCallback onUpdate) {
            return null;
        }
    }
}
