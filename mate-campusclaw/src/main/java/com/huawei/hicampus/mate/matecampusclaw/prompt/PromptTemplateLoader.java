package com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads prompt templates from markdown files in global and project directories.
 *
 * <p>Template files are {@code .md} files with optional YAML frontmatter.
 * The template name is derived from the filename (minus {@code .md}).
 * Description comes from frontmatter {@code description:} field, or the first
 * non-empty line of the body.
 *
 * <p>Search locations (in order):
 * <ol>
 *   <li>Global: {@code ~/.campusclaw/agent/prompts/}</li>
 *   <li>Project: {@code {cwd}/.campusclaw/prompts/}</li>
 * </ol>
 */
public class PromptTemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateLoader.class);
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
            "\\A---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile(
            "^description:\\s*(.+)$", Pattern.MULTILINE);

    /**
     * Loads prompt templates from global and project directories.
     *
     * @param cwd      the current working directory
     * @param agentDir the global agent config directory (e.g. ~/.campusclaw/agent)
     * @return list of prompt templates (project templates override global ones with the same name)
     */
    public List<PromptTemplateEntry> load(Path cwd, Path agentDir) {
        Map<String, PromptTemplateEntry> byName = new LinkedHashMap<>();

        // Global prompts first
        Path globalDir = agentDir.resolve("prompts");
        loadFromDir(globalDir, "user", byName);

        // Project prompts override
        Path projectDir = cwd.resolve(com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths.CONFIG_DIR_NAME).resolve("prompts");
        loadFromDir(projectDir, "project", byName);

        return List.copyOf(byName.values());
    }

    private void loadFromDir(Path dir, String source, Map<String, PromptTemplateEntry> byName) {
        if (!Files.isDirectory(dir)) return;

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".md"))
                    .forEach(p -> {
                        PromptTemplateEntry entry = loadFromFile(p, source);
                        if (entry != null) {
                            byName.put(entry.name(), entry);
                        }
                    });
        } catch (IOException e) {
            log.debug("Failed to list directory: {}", dir, e);
        }
    }

    PromptTemplateEntry loadFromFile(Path filePath, String source) {
        try {
            String raw = Files.readString(filePath);
            String name = filePath.getFileName().toString().replaceFirst("\\.md$", "");

            String body = raw;
            String description = "";

            // Parse YAML frontmatter
            Matcher fm = FRONTMATTER_PATTERN.matcher(raw);
            if (fm.find()) {
                String frontmatter = fm.group(1);
                body = raw.substring(fm.end());

                Matcher descMatcher = DESCRIPTION_PATTERN.matcher(frontmatter);
                if (descMatcher.find()) {
                    description = descMatcher.group(1).trim();
                }
            }

            // Fallback: use first non-empty line as description
            if (description.isEmpty()) {
                for (String line : body.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        description = trimmed.length() > 60
                                ? trimmed.substring(0, 60) + "..."
                                : trimmed;
                        break;
                    }
                }
            }

            return new PromptTemplateEntry(name, description, body, filePath, source);
        } catch (IOException e) {
            log.debug("Failed to read template: {}", filePath, e);
            return null;
        }
    }
}
