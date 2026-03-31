package com.campusclaw.codingagent.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands {@code /skill:name-here [args]} commands in user input
 * by reading the referenced SKILL.md file and wrapping it in XML format.
 */
public class SkillExpander {

    /**
     * Matches {@code /skill:name} at the start, optionally followed by whitespace and args.
     * Group 1 = skill name, Group 2 = optional args (may be null).
     */
    private static final Pattern SKILL_COMMAND = Pattern.compile(
            "^/skill:([a-z0-9-]+)(?:\\s+(.*))?$", Pattern.DOTALL);

    /**
     * If the user input matches {@code /skill:name [args]}, expands it by:
     * <ol>
     *   <li>Looking up the skill by name in the registry</li>
     *   <li>Reading the SKILL.md file and stripping YAML frontmatter</li>
     *   <li>Wrapping the body in an XML {@code <skill>} element</li>
     *   <li>Appending any trailing args</li>
     * </ol>
     * Returns the original input unchanged if it does not match the pattern
     * or the skill is not found.
     *
     * @param userInput the raw user input string
     * @param registry  the skill registry to look up skills
     * @return expanded skill content or original input
     */
    public String expand(String userInput, SkillRegistry registry) {
        if (userInput == null || userInput.isEmpty()) {
            return userInput != null ? userInput : "";
        }

        Matcher matcher = SKILL_COMMAND.matcher(userInput.trim());
        if (!matcher.matches()) {
            return userInput;
        }

        String skillName = matcher.group(1);
        String args = matcher.group(2);

        Optional<Skill> skillOpt = registry.getByName(skillName);
        if (skillOpt.isEmpty()) {
            return userInput;
        }

        Skill skill = skillOpt.get();
        String fileContent;
        try {
            fileContent = Files.readString(skill.filePath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return userInput;
        }

        String body = SkillLoader.stripFrontmatter(fileContent);

        StringBuilder sb = new StringBuilder();
        sb.append("<skill name=\"").append(skillName)
                .append("\" location=\"").append(skill.filePath()).append("\">\n");
        sb.append("References are relative to ").append(skill.baseDir()).append(".\n");
        sb.append(body);
        if (!body.endsWith("\n")) {
            sb.append('\n');
        }
        sb.append("</skill>");

        if (args != null && !args.isBlank()) {
            sb.append('\n').append(args);
        }

        return sb.toString();
    }
}
