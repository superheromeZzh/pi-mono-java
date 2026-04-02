package com.huawei.hicampus.mate.matecampusclaw.codingagent.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified resource discovery and loading.
 *
 * <p>Discovers resources from multiple layers in priority order:
 * <ol>
 *   <li>Project-level: {@code {cwd}/.campusclaw/}</li>
 *   <li>User-level: {@code ~/.campusclaw/agent/}</li>
 *   <li>Built-in: classpath resources</li>
 * </ol>
 *
 * <p>Higher-priority resources shadow lower-priority ones with the same name.
 */
public class ResourceLoader {

    private static final Logger log = LoggerFactory.getLogger(ResourceLoader.class);

    private final Path cwd;
    private final Path userDir;

    /**
     * Creates a new ResourceLoader.
     *
     * @param cwd     the current working directory (project root)
     * @param userDir the user-level resource directory (e.g. ~/.campusclaw/agent)
     */
    public ResourceLoader(Path cwd, Path userDir) {
        this.cwd = cwd;
        this.userDir = userDir;
    }

    /**
     * Discovers all resources matching the given relative path pattern.
     *
     * @param subPath relative path within each resource layer (e.g. "skills", "prompts")
     * @return list of discovered resources, project-level first
     */
    public List<Resource> discover(String subPath) {
        Map<String, Resource> byName = new LinkedHashMap<>();

        // Project-level
        Path projectDir = cwd.resolve(com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths.CONFIG_DIR_NAME).resolve(subPath);
        loadFromDir(projectDir, "project", byName);

        // User-level
        Path userSubDir = userDir.resolve(subPath);
        loadFromDir(userSubDir, "user", byName);

        return List.copyOf(byName.values());
    }

    /**
     * Loads a specific resource by name from the first layer where it exists.
     *
     * @param subPath      the resource category (e.g. "skills")
     * @param resourceName the resource file name
     * @return the resource contents if found
     */
    public Optional<String> load(String subPath, String resourceName) {
        // Project-level first
        Path projectFile = cwd.resolve(com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths.CONFIG_DIR_NAME).resolve(subPath).resolve(resourceName);
        if (Files.isRegularFile(projectFile)) {
            return readFile(projectFile);
        }

        // User-level
        Path userFile = userDir.resolve(subPath).resolve(resourceName);
        if (Files.isRegularFile(userFile)) {
            return readFile(userFile);
        }

        // Classpath
        try (var is = getClass().getClassLoader().getResourceAsStream(subPath + "/" + resourceName)) {
            if (is != null) {
                return Optional.of(new String(is.readAllBytes()));
            }
        } catch (IOException e) {
            log.debug("Failed to read classpath resource: {}/{}", subPath, resourceName, e);
        }

        return Optional.empty();
    }

    private void loadFromDir(Path dir, String source, Map<String, Resource> byName) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(Files::isRegularFile)
                .forEach(p -> {
                    String name = p.getFileName().toString();
                    if (!byName.containsKey(name)) {
                        byName.put(name, new Resource(name, p, source));
                    }
                });
        } catch (IOException e) {
            log.debug("Failed to list directory: {}", dir, e);
        }
    }

    private Optional<String> readFile(Path path) {
        try {
            return Optional.of(Files.readString(path));
        } catch (IOException e) {
            log.warn("Failed to read resource file: {}", path, e);
            return Optional.empty();
        }
    }

    /**
     * A discovered resource with its metadata.
     */
    public record Resource(String name, Path path, String source) {}
}
