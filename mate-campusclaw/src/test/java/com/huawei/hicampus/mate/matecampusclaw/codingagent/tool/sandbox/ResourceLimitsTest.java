/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResourceLimitsTest {

    @Test
    void defaultsReasonable() {
        ResourceLimits r = ResourceLimits.defaults();
        assertThat(r.getMemoryMb()).isEqualTo(512);
        assertThat(r.getCpuQuota()).isEqualTo(1.0);
        assertThat(r.getTimeoutSeconds()).isEqualTo(60);
        assertThat(r.isNetworkEnabled()).isFalse();
        assertThat(r.isPrivileged()).isFalse();
        assertThat(r.isAutoRemove()).isTrue();
    }

    @Test
    void highPerformance() {
        ResourceLimits r = ResourceLimits.highPerformance();
        assertThat(r.getMemoryMb()).isEqualTo(2048);
        assertThat(r.getCpuQuota()).isEqualTo(2.0);
        assertThat(r.isNetworkEnabled()).isTrue();
    }

    @Test
    void readonly() {
        ResourceLimits r = ResourceLimits.readonly();
        assertThat(r.getMemoryMb()).isEqualTo(256);
        assertThat(r.getTimeoutSeconds()).isEqualTo(30);
        assertThat(r.isNetworkEnabled()).isFalse();
    }

    @Test
    void builderAllFields() {
        ResourceLimits r = ResourceLimits.builder()
                .memoryMb(1024)
                .cpuQuota(0.5)
                .timeoutSeconds(120)
                .networkEnabled(true)
                .image("custom:latest")
                .privileged(true)
                .autoRemove(false)
                .build();
        assertThat(r.getMemoryMb()).isEqualTo(1024);
        assertThat(r.getCpuQuota()).isEqualTo(0.5);
        assertThat(r.getTimeoutSeconds()).isEqualTo(120);
        assertThat(r.isNetworkEnabled()).isTrue();
        assertThat(r.getImage()).isEqualTo("custom:latest");
        assertThat(r.isPrivileged()).isTrue();
        assertThat(r.isAutoRemove()).isFalse();
    }

    @Test
    void setterRoundtrip() {
        ResourceLimits r = ResourceLimits.builder().build();
        r.setMemoryMb(99);
        assertThat(r.getMemoryMb()).isEqualTo(99);
    }
}
