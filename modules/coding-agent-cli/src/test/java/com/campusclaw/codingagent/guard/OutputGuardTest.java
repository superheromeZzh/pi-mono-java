/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("checkstyle:no_system_out_err")
class OutputGuardTest {

    private final PrintStream savedOut = System.out;
    private final PrintStream savedErr = System.err;

    @AfterEach
    void restore() {
        System.setOut(savedOut);
        System.setErr(savedErr);
    }

    @Nested
    class Lifecycle {

        @Test
        void initiallyInactive() {
            OutputGuard g = new OutputGuard(savedOut);
            assertThat(g.isActive()).isFalse();
            assertThat(g.getProtocolStream()).isSameAs(savedOut);
        }

        @Test
        void activateRedirectsStdoutToStderr() {
            ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
            System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
            OutputGuard g = new OutputGuard(savedOut);
            g.activate();
            assertThat(g.isActive()).isTrue();
            System.out.println("stray output");
            assertThat(errBuf.toString(StandardCharsets.UTF_8)).contains("stray output");
        }

        @Test
        void activateIsIdempotent() {
            OutputGuard g = new OutputGuard(savedOut);
            g.activate();
            g.activate();
            assertThat(g.isActive()).isTrue();
        }

        @Test
        void deactivateRestoresStdout() {
            OutputGuard g = new OutputGuard(savedOut);
            g.activate();
            g.deactivate();
            assertThat(g.isActive()).isFalse();
            assertThat(System.out).isSameAs(savedOut);
        }

        @Test
        void deactivateOnInactiveIsNoOp() {
            OutputGuard g = new OutputGuard(savedOut);
            g.deactivate();
            assertThat(g.isActive()).isFalse();
        }

        @Test
        void autoCloseCallsDeactivate() {
            OutputGuard g = new OutputGuard(savedOut);
            g.activate();
            g.close();
            assertThat(g.isActive()).isFalse();
        }
    }
}
