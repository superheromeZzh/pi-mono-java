package com.huawei.hicampus.mate.matecampusclaw.codingagent.skill;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillExpanderTest {

    @TempDir
    Path tempDir;

    SkillExpander expander;
    SkillRegistry registry;

    @BeforeEach
    void setUp() {
        expander = new SkillExpander();
        registry = new SkillRegistry();
    }

    private Skill createSkillWithFile(String name, String frontmatter, String body) throws IOException {
        Path skillDir = tempDir.resolve(name);
        Files.createDirectories(skillDir);
        Path skillFile = skillDir.resolve("SKILL.md");
        String content = frontmatter != null
                ? "---\n" + frontmatter + "\n---\n" + body
                : body;
        Files.writeString(skillFile, content);
        return new Skill(name, "Description for " + name, skillFile, skillDir, "project", false);
    }

    // -------------------------------------------------------------------
    // Command matching
    // -------------------------------------------------------------------

    @Nested
    class CommandMatching {

        @Test
        void expandsSkillCommand() throws IOException {
            Skill skill = createSkillWithFile("commit",
                    "name: commit\ndescription: Git commits", "Commit instructions here.");
            registry.register(skill);

            String result = expander.expand("/skill:commit", registry);

            assertTrue(result.startsWith("<skill name=\"commit\""));
            assertTrue(result.contains("Commit instructions here."));
            assertTrue(result.contains("</skill>"));
        }

        @Test
        void expandsSkillCommandWithArgs() throws IOException {
            Skill skill = createSkillWithFile("commit",
                    "name: commit\ndescription: Git commits", "Commit instructions.");
            registry.register(skill);

            String result = expander.expand("/skill:commit fix the login bug", registry);

            assertTrue(result.contains("</skill>"));
            assertTrue(result.endsWith("fix the login bug"));
        }

        @Test
        void returnsOriginalWhenNoMatch() {
            String input = "Hello, how are you?";
            assertEquals(input, expander.expand(input, registry));
        }

        @Test
        void returnsOriginalForUnknownSkill() {
            String input = "/skill:nonexistent";
            assertEquals(input, expander.expand(input, registry));
        }

        @Test
        void returnsOriginalForNullInput() {
            assertEquals("", expander.expand(null, registry));
        }

        @Test
        void returnsOriginalForEmptyInput() {
            assertEquals("", expander.expand("", registry));
        }

        @Test
        void doesNotMatchPartialCommand() {
            assertEquals("skill:commit", expander.expand("skill:commit", registry));
        }

        @Test
        void doesNotMatchMidLineCommand() throws IOException {
            Skill skill = createSkillWithFile("commit",
                    "name: commit\ndescription: Git commits", "Body.");
            registry.register(skill);

            String input = "Please run /skill:commit";
            assertEquals(input, expander.expand(input, registry));
        }
    }

    // -------------------------------------------------------------------
    // Expansion format
    // -------------------------------------------------------------------

    @Nested
    class ExpansionFormat {

        @Test
        void includesLocationAttribute() throws IOException {
            Skill skill = createSkillWithFile("review",
                    "name: review\ndescription: Code review", "Review steps.");
            registry.register(skill);

            String result = expander.expand("/skill:review", registry);

            assertTrue(result.contains("location=\"" + skill.filePath() + "\""));
        }

        @Test
        void includesRelativeReferenceLine() throws IOException {
            Skill skill = createSkillWithFile("deploy",
                    "name: deploy\ndescription: Deploy app", "Deploy steps.");
            registry.register(skill);

            String result = expander.expand("/skill:deploy", registry);

            assertTrue(result.contains("References are relative to " + skill.baseDir()));
        }

        @Test
        void stripsFrontmatterFromBody() throws IOException {
            Skill skill = createSkillWithFile("test-skill",
                    "name: test-skill\ndescription: Testing", "Only the body.");
            registry.register(skill);

            String result = expander.expand("/skill:test-skill", registry);

            assertFalse(result.contains("name: test-skill"));
            assertFalse(result.contains("description: Testing"));
            assertTrue(result.contains("Only the body."));
        }

        @Test
        void handlesFileWithNoFrontmatter() throws IOException {
            Skill skill = createSkillWithFile("plain", null, "Plain body content.");
            // Override with valid registration
            Skill registered = new Skill("plain", "Plain skill",
                    skill.filePath(), skill.baseDir(), "project", false);
            registry.register(registered);

            String result = expander.expand("/skill:plain", registry);

            assertTrue(result.contains("Plain body content."));
        }
    }
}
