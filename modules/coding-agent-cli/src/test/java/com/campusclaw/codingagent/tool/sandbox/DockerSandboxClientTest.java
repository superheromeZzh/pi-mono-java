/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;

import com.campusclaw.codingagent.config.ToolExecutionProperties;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DockerSandboxClient}. Without a real Docker daemon
 * we exercise the disabled / unavailable paths — those are the relevant
 * failure modes when the host doesn't have Docker installed and the
 * fall-back semantics matter for security correctness.
 */
class DockerSandboxClientTest {

    private static DockerSandboxClient client(boolean sandboxEnabled) {
        ToolExecutionProperties props = new ToolExecutionProperties();
        props.setSandboxExecutionEnabled(sandboxEnabled);
        return new DockerSandboxClient(props, new SandboxSecurityPolicy());
    }

    @Nested
    class SandboxDisabled {

        @Test
        void initializeSkipsWhenDisabled() {
            DockerSandboxClient c = client(false);
            assertThat(c.isAvailable()).isFalse();
            assertThat(c.getWorkerContainerId()).isNull();
        }

        @Test
        void executeReturnsErrorWhenUnavailable() {
            DockerSandboxClient c = client(false);
            SandboxResult result = c.execute(List.of("echo", "hi"), ResourceLimits.defaults());
            assertThat(result.getErrorMessage()).contains("not available");
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void shutdownIsSafeWhenInactive() {
            DockerSandboxClient c = client(false);

            // No worker container ever spawned — shutdown should be a no-op rather than throwing.
            assertThatNoException().isThrownBy(c::shutdown);
        }
    }
}
