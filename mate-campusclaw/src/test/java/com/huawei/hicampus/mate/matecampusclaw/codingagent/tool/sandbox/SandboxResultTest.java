/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SandboxResultTest {

    @Test
    void successFactoryFlagged() {
        SandboxResult r = SandboxResult.success("done");
        assertThat(r.getStdout()).isEqualTo("done");
        assertThat(r.getExitCode()).isEqualTo(0);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.isTimeout()).isFalse();
    }

    @Test
    void errorFactory() {
        SandboxResult r = SandboxResult.error("oops", "stderr line");
        assertThat(r.getErrorMessage()).isEqualTo("oops");
        assertThat(r.getStderr()).isEqualTo("stderr line");
        assertThat(r.getExitCode()).isEqualTo(-1);
        assertThat(r.isSuccess()).isFalse();
    }

    @Test
    void timeoutFactory() {
        SandboxResult r = SandboxResult.timeout(30);
        assertThat(r.isTimeout()).isTrue();
        assertThat(r.getErrorMessage()).contains("30 seconds");
        assertThat(r.isSuccess()).isFalse();
    }

    @Test
    void builderRoundtrip() {
        SandboxResult r = SandboxResult.builder()
                .stdout("out")
                .stderr("err")
                .exitCode(0)
                .timeout(false)
                .errorMessage(null)
                .executionTimeMs(123)
                .build();
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getExecutionTimeMs()).isEqualTo(123);
    }
}
