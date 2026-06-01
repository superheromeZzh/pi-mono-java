/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.utils;

import java.nio.file.Path;

/**
 * Resolves the CampusClaw user-level configuration root, shared across all modules.
 *
 * <p>Resolution order (highest precedence first):
 * <ol>
 *   <li>system property {@code -Dcampusclaw.home}</li>
 *   <li>environment variable {@code CAMPUSCLAW_HOME}</li>
 *   <li>default {@code ~/file/.campusclaw}</li>
 * </ol>
 *
 * <p>This lives in the {@code ai} module — the lowest module in the dependency graph — so
 * {@code agent-core}, {@code cron}, and {@code coding-agent-cli} can all reuse a single
 * definition instead of independently hardcoding the path.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/30]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public final class CampusClawHome {

    private static final String HOME_PROPERTY = "campusclaw.home";
    private static final String HOME_ENV = "CAMPUSCLAW_HOME";
    private static final String DEFAULT_PARENT = "file";
    private static final String CONFIG_DIR_NAME = ".campusclaw";
    private static final String AGENT_SUBDIR = "agent";

    private CampusClawHome() {}

    /**
     * Returns the user-level configuration root (default {@code ~/file/.campusclaw}).
     *
     * @return the resolved configuration root directory
     */
    public static Path baseDir() {
        return resolveBaseDir(
                System.getProperty(HOME_PROPERTY), System.getenv(HOME_ENV), System.getProperty("user.home"));
    }

    /**
     * Pure resolution seam for {@link #baseDir()}: picks the config root from the three inputs
     * without touching process state, so the precedence rules are unit-testable in isolation.
     *
     * @param homeProperty value of the {@code campusclaw.home} system property (may be {@code null} or blank)
     * @param homeEnv value of the {@code CAMPUSCLAW_HOME} environment variable (may be {@code null} or blank)
     * @param userHome value of the {@code user.home} system property, used for the default location
     * @return the resolved configuration root directory
     */
    static Path resolveBaseDir(String homeProperty, String homeEnv, String userHome) {
        if (homeProperty != null && !homeProperty.isBlank()) {
            return Path.of(homeProperty);
        }
        if (homeEnv != null && !homeEnv.isBlank()) {
            return Path.of(homeEnv);
        }
        return Path.of(userHome, DEFAULT_PARENT, CONFIG_DIR_NAME);
    }

    /**
     * Returns the user-level agent directory ({@code <baseDir>/agent}).
     *
     * @return the resolved agent configuration directory
     */
    public static Path agentDir() {
        return baseDir().resolve(AGENT_SUBDIR);
    }
}
