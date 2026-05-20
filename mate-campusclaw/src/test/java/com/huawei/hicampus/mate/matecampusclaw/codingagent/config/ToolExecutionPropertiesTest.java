/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.execution.ExecutionMode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ToolExecutionPropertiesTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty("TOOL_EXECUTION_DEFAULT_MODE");
    }

    @Test
    void defaultsAreSensible() {
        ToolExecutionProperties p = new ToolExecutionProperties();
        assertThat(p.getDefaultMode()).isEqualTo(ExecutionMode.LOCAL);
        assertThat(p.isLocalExecutionEnabled()).isTrue();
        assertThat(p.isSandboxExecutionEnabled()).isFalse();
        assertThat(p.getDockerHost()).contains("tcp://");
        assertThat(p.getSandboxWorkspacePath()).isEqualTo("/workspace");
        assertThat(p.getSandboxRequiredPatterns()).isNotEmpty();
        assertThat(p.getProtectedPathPatterns()).isNotEmpty();
        assertThat(p.getLocalSafeCommands()).contains("ls").contains("git");
    }

    @Test
    void initOverridesFromSystemProperty() {
        System.setProperty("TOOL_EXECUTION_DEFAULT_MODE", "sandbox");
        ToolExecutionProperties p = new ToolExecutionProperties();
        p.init();
        assertThat(p.getDefaultMode()).isEqualTo(ExecutionMode.SANDBOX);
    }

    @Test
    void initIgnoresInvalidSystemProperty() {
        System.setProperty("TOOL_EXECUTION_DEFAULT_MODE", "gibberish");
        ToolExecutionProperties p = new ToolExecutionProperties();
        p.init();

        // Stays at default
        assertThat(p.getDefaultMode()).isEqualTo(ExecutionMode.LOCAL);
    }

    @Test
    void initIgnoresEmptySystemProperty() {
        System.setProperty("TOOL_EXECUTION_DEFAULT_MODE", "");
        ToolExecutionProperties p = new ToolExecutionProperties();
        p.init();
        assertThat(p.getDefaultMode()).isEqualTo(ExecutionMode.LOCAL);
    }

    @Test
    void settersWork() {
        ToolExecutionProperties p = new ToolExecutionProperties();
        p.setSandboxExecutionEnabled(true);
        p.setLocalTimeoutSeconds(45);
        assertThat(p.isSandboxExecutionEnabled()).isTrue();
        assertThat(p.getLocalTimeoutSeconds()).isEqualTo(45);
    }
}
