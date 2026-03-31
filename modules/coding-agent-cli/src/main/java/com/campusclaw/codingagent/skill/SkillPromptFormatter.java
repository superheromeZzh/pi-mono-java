package com.campusclaw.codingagent.skill;

import java.util.List;

/**
 * Formats a list of visible skills into an XML block suitable for inclusion in the system prompt.
 */
public class SkillPromptFormatter {

    /**
     * Formats the given visible skills as an XML {@code <available_skills>} block.
     * Returns an empty string if the list is empty.
     *
     * @param visibleSkills skills to include (should already be filtered for visibility)
     * @return XML-formatted skill listing, or empty string
     */
    public static String format(List<Skill> visibleSkills) {
        if (visibleSkills == null || visibleSkills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("The following skills provide specialized instructions for specific tasks.\n");
        sb.append("Use the read tool to load a skill's file when the task matches its description.\n");
        sb.append("When a skill file references a relative path, resolve it against the skill directory ");
        sb.append("(parent of SKILL.md / dirname of the path) and use that absolute path in tool commands.\n\n");
        sb.append("<available_skills>\n");

        for (Skill skill : visibleSkills) {
            sb.append("  <skill>\n");
            sb.append("    <name>").append(escapeXml(skill.name())).append("</name>\n");
            sb.append("    <description>").append(escapeXml(skill.description())).append("</description>\n");
            sb.append("    <location>").append(escapeXml(skill.filePath().toString())).append("</location>\n");
            sb.append("  </skill>\n");
        }

        sb.append("</available_skills>");
        return sb.toString();
    }

    static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
