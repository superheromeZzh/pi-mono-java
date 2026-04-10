package com.huawei.hicampus.mate.matecampusclaw.codingagent.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers and loads context files (AGENTS.md / CLAUDE.md) from the project
 * directory hierarchy and global user configuration.
 *
 * <p>Search order:
 * <ol>
 *   <li>Global: {@code ~/.campusclaw/agent/AGENTS.md} or {@code ~/.campusclaw/agent/CLAUDE.md}</li>
 *   <li>Ancestor directories: walks from cwd up to filesystem root</li>
 * </ol>
 *
 * <p>In each directory, AGENTS.md is preferred over CLAUDE.md.
 */
public class ContextFileLoader {

    private static final Logger log = LoggerFactory.getLogger(ContextFileLoader.class);
    private static final String[] CANDIDATES = {"AGENTS.md", "CLAUDE.md"};

    /**
     * Loads all context files from the global agent directory and the cwd ancestor chain.
     *
     * @param cwd      the current working directory
     * @param agentDir the global agent config directory (e.g. ~/.campusclaw/agent)
     * @return ordered list of context files (global first, then ancestors root-to-cwd)
     */
    public List<ContextFile> loadProjectContextFiles(Path cwd, Path agentDir) {
        List<ContextFile> contextFiles = new ArrayList<>();
        Set<Path> seen = new HashSet<>();

        // 1. Global context
        ContextFile global = loadFromDir(agentDir);
        if (global != null) {
            contextFiles.add(global);
            seen.add(global.path().toAbsolutePath().normalize());
        }

        // 2. Walk from cwd up to root, collecting in reverse (root-first) order
        List<ContextFile> ancestors = new ArrayList<>();
        Path current = cwd.toAbsolutePath().normalize();
        Path root = current.getRoot();

        while (current != null) {
            ContextFile cf = loadFromDir(current);
            if (cf != null) {
                Path normalized = cf.path().toAbsolutePath().normalize();
                if (!seen.contains(normalized)) {
                    ancestors.addFirst(cf);
                    seen.add(normalized);
                }
            }
            if (current.equals(root)) break;
            Path parent = current.getParent();
            if (parent == null || parent.equals(current)) break;
            current = parent;
        }

        contextFiles.addAll(ancestors);
        return contextFiles;
    }

    /**
     * Discovers a SYSTEM.md file from project-level or global-level config.
     *
     * @param cwd      the current working directory
     * @param agentDir the global agent config directory
     * @return the content of SYSTEM.md, or null if not found
     */
    public String loadSystemPrompt(Path cwd, Path agentDir) {
        // Project-level first
        Path projectPath = cwd.resolve(com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths.CONFIG_DIR_NAME).resolve("SYSTEM.md");
        String content = readIfExists(projectPath);
        if (content != null) return content;

        // Global
        Path globalPath = agentDir.resolve("SYSTEM.md");
        return readIfExists(globalPath);
    }

    /**
     * Discovers an APPEND_SYSTEM.md file from project-level or global-level config.
     *
     * @param cwd      the current working directory
     * @param agentDir the global agent config directory
     * @return the content of APPEND_SYSTEM.md, or null if not found
     */
    public String loadAppendSystemPrompt(Path cwd, Path agentDir) {
        Path projectPath = cwd.resolve(com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths.CONFIG_DIR_NAME).resolve("APPEND_SYSTEM.md");
        String content = readIfExists(projectPath);
        if (content != null) return content;

        Path globalPath = agentDir.resolve("APPEND_SYSTEM.md");
        return readIfExists(globalPath);
    }

    private ContextFile loadFromDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return null;
        for (String candidate : CANDIDATES) {
            Path filePath = dir.resolve(candidate);
            if (Files.isRegularFile(filePath)) {
                try {
                    String content = Files.readString(filePath);
                    return new ContextFile(filePath, content);
                } catch (IOException e) {
                    log.warn("Could not read {}: {}", filePath, e.getMessage());
                }
            }
        }
        return null;
    }

    private String readIfExists(Path path) {
        if (path != null && Files.isRegularFile(path)) {
            try {
                return Files.readString(path);
            } catch (IOException e) {
                log.warn("Could not read {}: {}", path, e.getMessage());
            }
        }
        return null;
    }

    /**
     * A context file with its path and content.
     */
    public record ContextFile(Path path, String content) {}
}
