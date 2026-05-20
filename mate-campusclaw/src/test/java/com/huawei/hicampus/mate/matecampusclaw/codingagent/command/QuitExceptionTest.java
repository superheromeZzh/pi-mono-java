/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuitExceptionTest {

    @Test
    void defaultConstructor() {
        QuitException ex = new QuitException();
        assertThat(ex.getMessage()).isNull();
    }

    @Test
    void messageConstructor() {
        QuitException ex = new QuitException("bye");
        assertThat(ex.getMessage()).isEqualTo("bye");
    }

    @Test
    void isRuntimeException() {
        assertThat(new QuitException()).isInstanceOf(RuntimeException.class);
    }
}
