package com.huawei.hicampus.mate.matecampusclaw.codingagent.skill;

import java.nio.file.Path;

/**
 * A self-contained capability package that provides specialized workflows and instructions.
 * Loaded from SKILL.md files with optional YAML frontmatter.
 *
 * @param name                    skill identifier (lowercase a-z, 0-9, hyphens; max 64 chars)
 * @param description             human-readable description (max 1024 chars)
 * @param filePath                path to the SKILL.md file
 * @param baseDir                 directory containing the SKILL.md file
 * @param source                  origin: "user" or "project"
 * @param disableModelInvocation  if true, skill is not shown in system prompt
 */
public record Skill(
        String name,
        String description,
        Path filePath,
        Path baseDir,
        String source,
        boolean disableModelInvocation
) {
    /** Maximum allowed length for skill names. */
    public static final int MAX_NAME_LENGTH = 64;

    /** Maximum allowed length for skill descriptions. */
    public static final int MAX_DESCRIPTION_LENGTH = 1024;

    /** Pattern that valid skill names must match. */
    public static final String NAME_PATTERN = "^[a-z0-9-]+$";
}
