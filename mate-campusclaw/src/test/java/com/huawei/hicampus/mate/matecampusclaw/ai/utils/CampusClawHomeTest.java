/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link CampusClawHome} root resolution. Precedence is exercised through the pure
 * {@link CampusClawHome#resolveBaseDir} seam (no process state); the public {@code baseDir} /
 * {@code agentDir} paths are driven via the {@code campusclaw.home} system property, saved and
 * restored around each test so the forked JVM stays clean for other suites.
 */
class CampusClawHomeTest {

    private static final String HOME_PROPERTY = "campusclaw.home";

    private String savedHome;

    @BeforeEach
    void saveHomeProperty() {
        savedHome = System.getProperty(HOME_PROPERTY);
    }

    @AfterEach
    void restoreHomeProperty() {
        if (savedHome == null) {
            System.clearProperty(HOME_PROPERTY);
        } else {
            System.setProperty(HOME_PROPERTY, savedHome);
        }
    }

    @Test
    void systemPropertyWinsOverEverything() {
        assertEquals(Path.of("/opt/custom"), CampusClawHome.resolveBaseDir("/opt/custom", "/srv/env", "/home/u"));
    }

    @Test
    void blankSystemPropertyFallsThroughToEnv() {
        assertEquals(Path.of("/srv/env"), CampusClawHome.resolveBaseDir("   ", "/srv/env", "/home/u"));
    }

    @Test
    void envUsedWhenPropertyNull() {
        assertEquals(Path.of("/srv/env"), CampusClawHome.resolveBaseDir(null, "/srv/env", "/home/u"));
    }

    @Test
    void blankEnvFallsThroughToDefault() {
        assertEquals(Path.of("/home/u", ".campusclaw"), CampusClawHome.resolveBaseDir(null, "  ", "/home/u"));
    }

    @Test
    void defaultWhenPropertyAndEnvNull() {
        assertEquals(Path.of("/home/u", ".campusclaw"), CampusClawHome.resolveBaseDir(null, null, "/home/u"));
    }

    @Test
    void defaultWhenPropertyAndEnvBothBlank() {
        assertEquals(Path.of("/home/u", ".campusclaw"), CampusClawHome.resolveBaseDir("", "", "/home/u"));
    }

    @Test
    void publicBaseDirHonorsSystemProperty() {
        System.setProperty(HOME_PROPERTY, "/tmp/cc-home");
        assertEquals(Path.of("/tmp/cc-home"), CampusClawHome.baseDir());
    }

    @Test
    void publicAgentDirAppendsAgentSubdir() {
        System.setProperty(HOME_PROPERTY, "/tmp/cc-home");
        assertEquals(Path.of("/tmp/cc-home", "agent"), CampusClawHome.agentDir());
    }

    @Test
    void publicBaseDirDefaultsUnderUserHome() {
        // Mirrors ConfigValueResolverTest: assert the default branch only when the real
        // CAMPUSCLAW_HOME env var is absent on the runner (it normally is).
        assumeTrue(System.getenv("CAMPUSCLAW_HOME") == null, "CAMPUSCLAW_HOME is set on this runner");
        System.clearProperty(HOME_PROPERTY);
        assertEquals(Path.of(System.getProperty("user.home"), ".campusclaw"), CampusClawHome.baseDir());
    }
}
