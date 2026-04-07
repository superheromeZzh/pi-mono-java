package com.campusclaw.codingagent.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Expands {@code /skill:name-here [args]} commands in user input
 * by reading the referenced SKILL.md file and wrapping it in XML format.
 * <p>
 * Supports sandbox mode: when {@link SandboxSkillParser} is available,
 * skill body content is loaded inside a Docker container for security.
 */
public class SkillExpander {

    private static final Logger log = LoggerFactory.getLogger(SkillExpander.class);

    /**
     * Matches {@code /skill:name} at the start, optionally followed by whitespace and args.
     * Group 1 = skill name, Group 2 = optional args (may be null).
     */
    private static final Pattern SKILL_COMMAND = Pattern.compile(
            "^/skill:([a-z0-9-]+)(?:\\s+(.*))?$", Pattern.DOTALL);

    private final SandboxSkillParser sandboxParser;
    private final boolean sandboxEnabled;

    /**
     * Creates a SkillExpander with direct file reading (no sandbox).
     */
    public SkillExpander() {
        this(null, false);
    }

    /**
     * Creates a SkillExpander with optional sandbox parsing.
     *
     * @param sandboxParser the sandbox parser (can be null)
     * @param sandboxEnabled whether to use sandbox when available
     */
    public SkillExpander(SandboxSkillParser sandboxParser, boolean sandboxEnabled) {
        this.sandboxParser = sandboxParser;
        this.sandboxEnabled = sandboxEnabled && sandboxParser != null && sandboxParser.isAvailable();
        if (this.sandboxEnabled) {
            log.info("SkillExpander initialized with sandbox body loading enabled");
        }
    }

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
        String body = loadSkillBody(skill);
        if (body == null) {
            return userInput;
        }

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

    /**
     * Loads the skill body content, using sandbox if enabled and available.
     * Falls back to direct file reading if sandbox is not available or fails.
     *
     * @param skill the skill to load body for
     * @return body content, or null if loading fails
     */
    private String loadSkillBody(Skill skill) {
        // Try sandbox first if enabled
        if (sandboxEnabled && sandboxParser != null) {
            try {
                log.debug("Loading skill body in sandbox: {}", skill.filePath());
                return sandboxParser.loadBodyInSandbox(skill.filePath());
            } catch (SkillLoadException e) {
                log.warn("Sandbox body loading failed for {}, falling back to direct reading: {}",
                        skill.filePath(), e.getMessage());
                // Fall back to direct reading
            }
        }

        // Direct file reading fallback
        try {
            String fileContent = Files.readString(skill.filePath(), StandardCharsets.UTF_8);
            return SkillLoader.stripFrontmatter(fileContent);
        } catch (IOException e) {
            log.warn("Failed to read skill file: {}", skill.filePath(), e);
            return null;
        }
    }
}
