package com.campusclaw.codingagent.prompt;

import java.nio.file.Path;

/**
 * A reusable prompt template loaded from a markdown file.
 *
 * @param name        template name (derived from filename, without .md extension)
 * @param description short description (from frontmatter or first line)
 * @param content     template body (after frontmatter)
 * @param filePath    absolute path to the template file
 * @param source      where the template was found ("user" or "project")
 */
public record PromptTemplateEntry(
        String name,
        String description,
        String content,
        Path filePath,
        String source
) {}
