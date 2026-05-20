/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.ops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolOpsConfigTest {

    @Test
    void beansProvideLocalImplementations() {
        ToolOpsConfig cfg = new ToolOpsConfig();
        assertThat(cfg.readOperations()).isInstanceOf(LocalReadOperations.class);
        assertThat(cfg.writeOperations()).isInstanceOf(LocalWriteOperations.class);
        assertThat(cfg.editOperations()).isInstanceOf(LocalEditOperations.class);
        assertThat(cfg.lsOperations()).isInstanceOf(LocalLsOperations.class);
        assertThat(cfg.fileMutationQueue()).isNotNull();
    }
}
