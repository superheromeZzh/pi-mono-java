package com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.skill.Skill;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SystemPromptBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    SystemPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SystemPromptBuilder();
    }

    private SystemPromptConfig minimalConfig() {
        return new SystemPromptConfig(
                List.of(), List.of(),
                Path.of("/test/project"),
                null, Map.of()
        );
    }

    private Skill testSkill(String name, String description, String filePath) {
        return new Skill(name, description, Path.of(filePath), Path.of(filePath).getParent(), "project", false);
    }

    // -------------------------------------------------------------------
    // Base prompt
    // -------------------------------------------------------------------

    @Nested
    class BasePrompt {

        @Test
        void includesBaseRoleDefinition() {
            String result = builder.build(minimalConfig());
            assertTrue(result.contains("expert coding assistant"));
            assertTrue(result.contains("coding agent"));
        }

        @Test
        void alwaysIncludesEnvironmentSection() {
            String result = builder.build(minimalConfig());
            assertTrue(result.contains("# Environment"));
            assertTrue(result.contains("/test/project"));
        }
    }

    // -------------------------------------------------------------------
    // Tool descriptions
    // -------------------------------------------------------------------

    @Nested
    class ToolDescriptions {

        @Test
        void includesToolSection() {
            var tool = new StubTool("bash", "Execute bash commands");
            var config = new SystemPromptConfig(
                    List.of(tool), List.of(),
                    Path.of("/cwd"), null, Map.of()
            );

            String result = builder.build(config);

            assertTrue(result.contains("Available tools:"));
            assertTrue(result.contains("- bash: Execute bash commands"));
        }

        @Test
        void includesMultipleTools() {
            var tool1 = new StubTool("read", "Read file contents");
            var tool2 = new StubTool("write", "Write file contents");
            var config = new SystemPromptConfig(
                    List.of(tool1, tool2), List.of(),
                    Path.of("/cwd"), null, Map.of()
            );

            String result = builder.build(config);

            assertTrue(result.contains("- read: Read file contents"));
            assertTrue(result.contains("- write: Write file contents"));
        }

        @Test
        void noToolSectionWhenEmpty() {
            String result = builder.build(minimalConfig());
            assertFalse(result.contains("Available tools:"));
        }
    }

    // -------------------------------------------------------------------
    // Skills
    // -------------------------------------------------------------------

    @Nested
    class Skills {

        @Test
        void includesSkillsInXmlFormat() {
            var skill = testSkill("commit", "Create git commits", "/path/to/skill.md");
            var config = new SystemPromptConfig(
                    List.of(), List.of(skill),
                    Path.of("/cwd"), null, Map.of()
            );

            String result = builder.build(config);

            assertTrue(result.contains("# Skills"));
            assertTrue(result.contains("<available_skills>"));
            assertTrue(result.contains("<name>commit</name>"));
            assertTrue(result.contains("Create git commits"));
            assertTrue(result.contains("</available_skills>"));
        }

        @Test
        void includesMultipleSkills() {
            var skill1 = testSkill("commit", "Git commits", "/a/skill.md");
            var skill2 = testSkill("review", "Code review", "/b/skill.md");
            var config = new SystemPromptConfig(
                    List.of(), List.of(skill1, skill2),
                    Path.of("/cwd"), null, Map.of()
            );

            String result = builder.build(config);

            assertTrue(result.contains("<name>commit</name>"));
            assertTrue(result.contains("<name>review</name>"));
        }

        @Test
        void noSkillsSectionWhenEmpty() {
            String result = builder.build(minimalConfig());
            assertFalse(result.contains("# Skills"));
            assertFalse(result.contains("<available_skills>"));
        }

        @Test
        void escapesXmlSpecialChars() {
            // name with special chars (would be invalid in practice but tests escaping)
            var skill = testSkill("test", "desc & \"quoted\"", "/path/skill.md");
            var config = new SystemPromptConfig(
                    List.of(), List.of(skill),
                    Path.of("/cwd"), null, Map.of()
            );

            String result = builder.build(config);

            assertTrue(result.contains("desc &amp; &quot;quoted&quot;"));
        }
    }

    // -------------------------------------------------------------------
    // Environment info
    // -------------------------------------------------------------------

    @Nested
    class EnvironmentInfo {

        @Test
        void includesWorkingDirectory() {
            String result = builder.build(minimalConfig());
            assertTrue(result.contains("Working directory: /test/project"));
        }

        @Test
        void includesOsFromEnv() {
            var config = new SystemPromptConfig(
                    List.of(), List.of(),
                    Path.of("/cwd"), null,
                    Map.of("OS_NAME", "Linux")
            );

            String result = builder.build(config);
            assertTrue(result.contains("Operating system: Linux"));
        }

        @Test
        void includesGitBranch() {
            var config = new SystemPromptConfig(
                    List.of(), List.of(),
                    Path.of("/cwd"), null,
                    Map.of("GIT_BRANCH", "feature/test")
            );

            String result = builder.build(config);
            assertTrue(result.contains("Git branch: feature/test"));
        }

        @Test
        void omitsGitBranchWhenAbsent() {
            String result = builder.build(minimalConfig());
            assertFalse(result.contains("Git branch"));
        }

        @Test
        void includesJavaVersion() {
            var config = new SystemPromptConfig(
                    List.of(), List.of(),
                    Path.of("/cwd"), null,
                    Map.of("JAVA_VERSION", "21.0.1")
            );

            String result = builder.build(config);
            assertTrue(result.contains("Java version: 21.0.1"));
        }
    }

    // -------------------------------------------------------------------
    // Custom prompt
    // -------------------------------------------------------------------

    @Nested
    class CustomPrompt {

        @Test
        void appendsCustomPrompt() {
            var config = new SystemPromptConfig(
                    List.of(), List.of(),
                    Path.of("/cwd"),
                    "Always use TypeScript strict mode.",
                    Map.of()
            );

            String result = builder.build(config);

            assertTrue(result.contains("# User Instructions"));
            assertTrue(result.contains("Always use TypeScript strict mode."));
        }

        @Test
        void noCustomSectionWhenNull() {
            String result = builder.build(minimalConfig());
            assertFalse(result.contains("# User Instructions"));
        }

        @Test
        void noCustomSectionWhenBlank() {
            var config = new SystemPromptConfig(
                    List.of(), List.of(),
                    Path.of("/cwd"), "   ", Map.of()
            );

            String result = builder.build(minimalConfig());
            assertFalse(result.contains("# User Instructions"));
        }
    }

    // -------------------------------------------------------------------
    // Full integration
    // -------------------------------------------------------------------

    @Nested
    class FullIntegration {

        @Test
        void assemblesAllSections() {
            var tool = new StubTool("bash", "Run commands");
            var skill = testSkill("commit", "Git commits", "/skills/commit.md");
            var config = new SystemPromptConfig(
                    List.of(tool),
                    List.of(skill),
                    Path.of("/my/project"),
                    "Be concise.",
                    Map.of("GIT_BRANCH", "main", "OS_NAME", "macOS")
            );

            String result = builder.build(config);

            // Verify section ordering: base -> tools -> skills -> env -> custom
            int baseIdx = result.indexOf("coding agent");
            int toolsIdx = result.indexOf("Available tools:");
            int skillsIdx = result.indexOf("# Skills");
            int envIdx = result.indexOf("# Environment");
            int customIdx = result.indexOf("# User Instructions");

            assertTrue(baseIdx >= 0, "base prompt not found");
            assertTrue(toolsIdx >= 0, "tools section not found");
            assertTrue(baseIdx < toolsIdx);
            assertTrue(toolsIdx < skillsIdx);
            assertTrue(skillsIdx < envIdx);
            assertTrue(envIdx < customIdx);
        }
    }

    // -------------------------------------------------------------------
    // SystemPromptConfig record
    // -------------------------------------------------------------------

    @Nested
    class ConfigRecord {

        @Test
        void nullToolsDefaultsToEmptyList() {
            var config = new SystemPromptConfig(null, null, Path.of("/"), null, null);
            assertNotNull(config.tools());
            assertTrue(config.tools().isEmpty());
        }

        @Test
        void nullSkillsDefaultsToEmptyList() {
            var config = new SystemPromptConfig(null, null, Path.of("/"), null, null);
            assertNotNull(config.skills());
            assertTrue(config.skills().isEmpty());
        }

        @Test
        void nullEnvDefaultsToEmptyMap() {
            var config = new SystemPromptConfig(null, null, Path.of("/"), null, null);
            assertNotNull(config.env());
            assertTrue(config.env().isEmpty());
        }

        @Test
        void listsAreImmutableCopies() {
            var tools = new java.util.ArrayList<AgentTool>();
            tools.add(new StubTool("t", "d"));
            var config = new SystemPromptConfig(tools, List.of(), Path.of("/"), null, Map.of());

            tools.clear();
            assertEquals(1, config.tools().size());
            assertThrows(UnsupportedOperationException.class, () -> config.tools().add(new StubTool("x", "y")));
        }
    }

    // -------------------------------------------------------------------
    // Stub tool for testing
    // -------------------------------------------------------------------

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
