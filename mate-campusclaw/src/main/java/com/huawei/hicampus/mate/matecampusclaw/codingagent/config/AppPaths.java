package com.huawei.hicampus.mate.matecampusclaw.codingagent.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.LoggerFactory;

/**
 * Central definition of all configuration directory paths.
 *
 * <p>User-level: {@code ~/.campusclaw/agent/}
 * <p>Project-level: {@code {cwd}/.campusclaw/}
 */
public final class AppPaths {

    /** Top-level config directory name (under home or project root). */
    public static final String CONFIG_DIR_NAME = ".campusclaw";

    /** User-level agent directory: {@code ~/.campusclaw/agent/}. */
    public static final Path USER_AGENT_DIR = Path.of(
            System.getProperty("user.home"), CONFIG_DIR_NAME, "agent");

    /** User-level settings file: {@code ~/.campusclaw/agent/settings.json}. */
    public static final Path GLOBAL_SETTINGS = USER_AGENT_DIR.resolve("settings.json");

    /** User-level auth file: {@code ~/.campusclaw/agent/auth.json}. */
    public static final Path AUTH_FILE = USER_AGENT_DIR.resolve("auth.json");

    /** User-level keybindings file: {@code ~/.campusclaw/agent/keybindings.json}. */
    public static final Path KEYBINDINGS_FILE = USER_AGENT_DIR.resolve("keybindings.json");

    /** User-level sessions directory: {@code ~/.campusclaw/agent/sessions/}. */
    public static final Path SESSIONS_DIR = USER_AGENT_DIR.resolve("sessions");

    /** User-level skills directory: {@code ~/.campusclaw/agent/skills/}. */
    public static final Path USER_SKILLS_DIR = USER_AGENT_DIR.resolve("skills");

    /** Project-level config directory name relative to cwd. */
    public static final String PROJECT_CONFIG_SUBDIR = CONFIG_DIR_NAME;

    /** Project-level settings file relative to cwd. */
    public static final String PROJECT_SETTINGS = CONFIG_DIR_NAME + "/settings.json";

    /** Project-level skills directory relative to cwd. */
    public static final String PROJECT_SKILLS_SUBDIR = CONFIG_DIR_NAME + "/skills";

    /** Project-level prompts directory name. */
    public static final String PROMPTS_SUBDIR = "prompts";

    /**
     * Ensures the user-level directory structure exists.
     * Call once at startup to create:
     * <pre>
     * ~/.campusclaw/agent/
     *   ├── skills/
     *   ├── prompts/
     *   ├── sessions/
     * </pre>
     */
    public static void ensureUserDirs() {
        Path[] dirs = {
            USER_AGENT_DIR,
            USER_SKILLS_DIR,
            USER_AGENT_DIR.resolve(PROMPTS_SUBDIR),
            SESSIONS_DIR
        };
        for (Path dir : dirs) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                LoggerFactory.getLogger(AppPaths.class)
                    .warn("Failed to create directory: {}", dir, e);
            }
        }
    }

    private AppPaths() {}
}
