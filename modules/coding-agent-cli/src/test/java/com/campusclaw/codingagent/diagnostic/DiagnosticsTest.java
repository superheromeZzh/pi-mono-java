/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticsTest {

    @Nested
    class RegistrationAndExecution {

        @Test
        void noChecksProducesEmpty() {
            Diagnostics diag = new Diagnostics();
            assertThat(diag.runAll()).isEmpty();
        }

        @Test
        void registeredCheckRuns() {
            Diagnostics diag = new Diagnostics();
            Diagnostics.DiagnosticResult sample =
                    new Diagnostics.DiagnosticResult("c", Diagnostics.Severity.INFO, "ok", null);
            diag.registerCheck(() -> List.of(sample));
            List<Diagnostics.DiagnosticResult> results = diag.runAll();
            assertThat(results).containsExactly(sample);
        }

        @Test
        void exceptionInCheckProducesErrorResult() {
            Diagnostics diag = new Diagnostics();
            diag.registerCheck(() -> {
                throw new IllegalStateException("boom");
            });
            List<Diagnostics.DiagnosticResult> results = diag.runAll();
            assertThat(results).hasSize(1);
            Diagnostics.DiagnosticResult r = results.get(0);
            assertThat(r.severity()).isEqualTo(Diagnostics.Severity.ERROR);
            assertThat(r.message()).contains("boom");
        }

        @Test
        void multipleChecksAggregate() {
            Diagnostics diag = new Diagnostics();
            diag.registerCheck(
                    () -> List.of(new Diagnostics.DiagnosticResult("a", Diagnostics.Severity.INFO, "a", null)));
            diag.registerCheck(
                    () -> List.of(new Diagnostics.DiagnosticResult("b", Diagnostics.Severity.WARNING, "b", "hint")));
            List<Diagnostics.DiagnosticResult> results = diag.runAll();
            assertThat(results).hasSize(2);
        }
    }

    @Nested
    class Builtins {

        @Test
        void builtinsRunWithoutThrowing(@TempDir Path tmp) {
            Diagnostics diag = new Diagnostics();
            diag.registerBuiltins(tmp);
            List<Diagnostics.DiagnosticResult> results = diag.runAll();
            assertThat(results).isNotEmpty();

            // Should include java-version + api-key/api-keys + disk-space
            assertThat(results).anyMatch(r -> r.checkName().contains("java-version"));
            assertThat(results).anyMatch(r -> r.checkName().contains("disk-space"));
        }
    }

    @Nested
    class FormatReport {

        @Test
        void includesAllIconsAndSuggestions() {
            String report = Diagnostics.formatReport(List.of(
                    new Diagnostics.DiagnosticResult("c1", Diagnostics.Severity.INFO, "im", null),
                    new Diagnostics.DiagnosticResult("c2", Diagnostics.Severity.WARNING, "wm", "wsug"),
                    new Diagnostics.DiagnosticResult("c3", Diagnostics.Severity.ERROR, "em", "esug")));
            assertThat(report)
                    .contains("Diagnostic Report")
                    .contains("[c1]")
                    .contains("im")
                    .contains("[c2]")
                    .contains("wm")
                    .contains("wsug")
                    .contains("[c3]")
                    .contains("em")
                    .contains("esug");
        }

        @Test
        void emptyResultsProducesHeaderOnly() {
            String report = Diagnostics.formatReport(List.of());
            assertThat(report).contains("Diagnostic Report");
        }
    }
}
