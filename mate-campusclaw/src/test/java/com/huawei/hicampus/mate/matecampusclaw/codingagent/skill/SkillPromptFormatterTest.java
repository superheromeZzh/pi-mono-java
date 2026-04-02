package com.huawei.hicampus.mate.matecampusclaw.codingagent.skill;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SkillPromptFormatterTest {

    private Skill skill(String name, String description, String filePath) {
        return new Skill(name, description,
                Path.of(filePath), Path.of(filePath).getParent(),
                "project", false);
    }

    // -------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------

    @Nested
    class Formatting {

        @Test
        void formatsAvailableSkillsXml() {
            List<Skill> skills = List.of(
                    skill("commit", "Create git commits", "/skills/commit/SKILL.md")
            );

            String result = SkillPromptFormatter.format(skills);

            assertTrue(result.contains("<available_skills>"));
            assertTrue(result.contains("<name>commit</name>"));
            assertTrue(result.contains("<description>Create git commits</description>"));
            assertTrue(result.contains("<location>/skills/commit/SKILL.md</location>"));
            assertTrue(result.contains("</available_skills>"));
        }

        @Test
        void formatsMultipleSkills() {
            List<Skill> skills = List.of(
                    skill("commit", "Git commits", "/skills/commit/SKILL.md"),
                    skill("review", "Code review", "/skills/review/SKILL.md")
            );

            String result = SkillPromptFormatter.format(skills);

            assertTrue(result.contains("<name>commit</name>"));
            assertTrue(result.contains("<name>review</name>"));
        }

        @Test
        void returnsEmptyStringForEmptyList() {
            assertEquals("", SkillPromptFormatter.format(List.of()));
        }

        @Test
        void returnsEmptyStringForNull() {
            assertEquals("", SkillPromptFormatter.format(null));
        }
    }

    // -------------------------------------------------------------------
    // XML escaping
    // -------------------------------------------------------------------

    @Nested
    class XmlEscaping {

        @Test
        void escapesAmpersand() {
            assertEquals("foo &amp; bar", SkillPromptFormatter.escapeXml("foo & bar"));
        }

        @Test
        void escapesAngleBrackets() {
            assertEquals("&lt;tag&gt;", SkillPromptFormatter.escapeXml("<tag>"));
        }

        @Test
        void escapesQuotes() {
            assertEquals("say &quot;hello&quot;", SkillPromptFormatter.escapeXml("say \"hello\""));
        }

        @Test
        void escapesApostrophes() {
            assertEquals("it&apos;s", SkillPromptFormatter.escapeXml("it's"));
        }

        @Test
        void handlesNullInput() {
            assertEquals("", SkillPromptFormatter.escapeXml(null));
        }

        @Test
        void escapesSpecialCharsInFormattedSkill() {
            List<Skill> skills = List.of(
                    skill("test", "desc & <special> \"chars\"", "/path/SKILL.md")
            );

            String result = SkillPromptFormatter.format(skills);

            assertTrue(result.contains("desc &amp; &lt;special&gt; &quot;chars&quot;"));
        }
    }
}
