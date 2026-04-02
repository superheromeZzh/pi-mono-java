package com.huawei.hicampus.mate.matecampusclaw.codingagent.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;

/**
 * Recursively scans directories to discover and load {@link Skill}s from SKILL.md files.
 * <p>
 * Discovery rules:
 * <ul>
 *   <li>If a directory contains SKILL.md, it is treated as a skill root (no further recursion)</li>
 *   <li>Otherwise, recurse into subdirectories to find SKILL.md files</li>
 * </ul>
 */
public class SkillLoader {

    static final String SKILL_FILENAME = "SKILL.md";
    private static final Pattern NAME_REGEX = Pattern.compile(Skill.NAME_PATTERN);
    private static final String FRONTMATTER_DELIMITER = "---";

    /**
     * Loads all skills from the given directory by recursively scanning for SKILL.md files.
     *
     * @param dir    the root directory to scan
     * @param source the source label ("user" or "project")
     * @return list of discovered skills (invalid files are silently skipped)
     */
    public List<Skill> loadFromDirectory(Path dir, String source) {
        List<Skill> skills = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return skills;
        }
        scanDirectory(dir, source, skills);
        return skills;
    }

    /**
     * Loads a single skill from a SKILL.md file.
     *
     * @param filePath path to the SKILL.md file
     * @param source   the source label ("user" or "project")
     * @return the loaded skill
     * @throws SkillLoadException if the file cannot be read or parsed, or if validation fails
     */
    public Skill loadFromFile(Path filePath, String source) {
        return parseSkillFile(filePath, source);
    }

    private void scanDirectory(Path dir, String source, List<Skill> skills) {
        Path skillFile = dir.resolve(SKILL_FILENAME);
        if (Files.isRegularFile(skillFile)) {
            // This directory is a skill root — load it and do not recurse further.
            try {
                skills.add(parseSkillFile(skillFile, source));
            } catch (SkillLoadException e) {
                // Skip invalid skill files during directory scanning
            }
            return;
        }

        // No SKILL.md here — recurse into subdirectories
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && !entry.getFileName().toString().startsWith(".")) {
                    scanDirectory(entry, source, skills);
                }
            }
        } catch (IOException e) {
            // Skip unreadable directories
        }
    }

    Skill parseSkillFile(Path filePath, String source) {
        String content;
        try {
            content = Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SkillLoadException("Failed to read skill file: " + filePath, e);
        }

        Map<String, Object> frontmatter = parseFrontmatter(content);

        Path baseDir = filePath.getParent();
        String parentDirName = baseDir != null ? baseDir.getFileName().toString() : "";

        // Resolve name: frontmatter > parent directory name
        String name = frontmatter.containsKey("name")
                ? String.valueOf(frontmatter.get("name"))
                : parentDirName;

        validateName(name, filePath);

        // Resolve description (required)
        String description = frontmatter.containsKey("description")
                ? String.valueOf(frontmatter.get("description"))
                : null;

        if (description == null || description.isBlank()) {
            throw new SkillLoadException(
                    "Skill description is required: " + filePath);
        }
        if (description.length() > Skill.MAX_DESCRIPTION_LENGTH) {
            throw new SkillLoadException(
                    "Skill description exceeds " + Skill.MAX_DESCRIPTION_LENGTH + " characters: " + filePath);
        }

        // Resolve disableModelInvocation flag
        boolean disableModelInvocation = Boolean.TRUE.equals(
                frontmatter.get("disable-model-invocation"));

        return new Skill(name, description, filePath, baseDir, source, disableModelInvocation);
    }

    static void validateName(String name, Path filePath) {
        if (name == null || name.isEmpty()) {
            throw new SkillLoadException("Skill name is required: " + filePath);
        }
        if (name.length() > Skill.MAX_NAME_LENGTH) {
            throw new SkillLoadException(
                    "Skill name exceeds " + Skill.MAX_NAME_LENGTH + " characters: " + filePath);
        }
        if (!NAME_REGEX.matcher(name).matches()) {
            throw new SkillLoadException(
                    "Skill name contains invalid characters (must be lowercase a-z, 0-9, hyphens): " + filePath);
        }
    }

    /**
     * Parses YAML frontmatter from content delimited by {@code ---} lines.
     * Returns an empty map if no frontmatter is present.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> parseFrontmatter(String content) {
        if (content == null || !content.startsWith(FRONTMATTER_DELIMITER)) {
            return Map.of();
        }

        int firstDelimEnd = content.indexOf('\n');
        if (firstDelimEnd < 0) {
            return Map.of();
        }

        int secondDelimStart = content.indexOf("\n" + FRONTMATTER_DELIMITER, firstDelimEnd + 1);
        if (secondDelimStart < 0) {
            return Map.of();
        }

        String yamlBlock = content.substring(firstDelimEnd + 1, secondDelimStart);
        if (yamlBlock.isBlank()) {
            return Map.of();
        }

        try {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(yamlBlock);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Strips the YAML frontmatter from content, returning only the body.
     */
    static String stripFrontmatter(String content) {
        if (content == null || !content.startsWith(FRONTMATTER_DELIMITER)) {
            return content != null ? content : "";
        }

        int firstDelimEnd = content.indexOf('\n');
        if (firstDelimEnd < 0) {
            return content;
        }

        int secondDelimStart = content.indexOf("\n" + FRONTMATTER_DELIMITER, firstDelimEnd + 1);
        if (secondDelimStart < 0) {
            return content;
        }

        // Find the end of the closing delimiter line
        int bodyStart = content.indexOf('\n', secondDelimStart + 1);
        if (bodyStart < 0) {
            return "";
        }

        return content.substring(bodyStart + 1);
    }
}
