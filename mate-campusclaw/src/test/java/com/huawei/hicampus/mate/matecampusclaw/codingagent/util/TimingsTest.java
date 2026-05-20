/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TimingsTest {

    private static void busyWait(long nanos) {
        long target = System.nanoTime() + nanos;
        while (System.nanoTime() < target) {
            // busy
        }
    }

    @Nested
    class StartEnd {

        @Test
        void startThenEndRecordsSpan() {
            Timings t = new Timings();
            t.start("op");
            busyWait(50_000);
            Timings.TimingSpan span = t.end("op");
            assertThat(span).isNotNull();
            assertThat(span.name()).isEqualTo("op");
            assertThat(span.durationNanos()).isPositive();
            assertThat(span.durationMs()).isGreaterThanOrEqualTo(0);
            assertThat(span.durationSecs()).isGreaterThanOrEqualTo(0);
            assertThat(span.parentName()).isNull();
            assertThat(span.metadata()).isNull();
        }

        @Test
        void endWithoutStartReturnsNull() {
            Timings t = new Timings();
            assertThat(t.end("missing")).isNull();
        }

        @Test
        void nestedSpanRecordsParent() {
            Timings t = new Timings();
            t.start("parent");
            t.start("child", "parent");
            Timings.TimingSpan childSpan = t.end("child");
            t.end("parent");
            assertThat(childSpan.parentName()).isEqualTo("parent");
        }

        @Test
        void endWithMetadataAttachesMap() {
            Timings t = new Timings();
            t.start("op");
            Timings.TimingSpan span = t.end("op", Map.of("k", "v"));
            assertThat(span.metadata()).containsEntry("k", "v");
        }

        @Test
        void disabledTimingsSkipStartAndEnd() {
            Timings t = new Timings();
            t.setEnabled(false);
            assertThat(t.isEnabled()).isFalse();
            t.start("op");
            t.start("op2", "parent");
            assertThat(t.end("op")).isNull();
            assertThat(t.end("op", Map.of("k", "v"))).isNull();
            assertThat(t.getSpans()).isEmpty();
            t.setEnabled(true);
            assertThat(t.isEnabled()).isTrue();
        }
    }

    @Nested
    class MeasureBlocks {

        @Test
        void measureSupplierReturnsValue() {
            Timings t = new Timings();
            String result = t.measure("op", () -> "hello");
            assertThat(result).isEqualTo("hello");
            assertThat(t.getSpans("op")).hasSize(1);
        }

        @Test
        void measureRunnableExecutes() {
            Timings t = new Timings();
            AtomicInteger counter = new AtomicInteger();
            t.measure("op", counter::incrementAndGet);
            assertThat(counter.get()).isEqualTo(1);
            assertThat(t.getSpans("op")).hasSize(1);
        }

        @Test
        void measureEndsEvenOnException() {
            Timings t = new Timings();
            org.junit.jupiter.api.Assertions.assertThrows(
                    RuntimeException.class,
                    () -> t.measure("op", () -> {
                        throw new IllegalStateException("boom");
                    }));
            assertThat(t.getSpans("op")).hasSize(1);
        }
    }

    @Nested
    class Statistics {

        @Test
        void emptyStatsAbsent() {
            Timings t = new Timings();
            assertThat(t.getStats("missing")).isEmpty();
        }

        @Test
        void singleSpanProducesStats() {
            Timings t = new Timings();
            t.measure("op", () -> busyWait(1_000));
            Optional<Timings.TimingStats> stats = t.getStats("op");
            assertThat(stats).isPresent();
            Timings.TimingStats s = stats.get();
            assertThat(s.count()).isEqualTo(1);
            assertThat(s.totalMs()).isEqualTo(s.avgMs());
            assertThat(s.format()).contains("op:");
        }

        @Test
        void multipleSpansProduceAggregateAndFormatString() {
            Timings t = new Timings();
            for (int i = 0; i < 5; i++) {
                t.measure("op", () -> busyWait(1_000));
            }
            Timings.TimingStats s = t.getStats("op").orElseThrow();
            assertThat(s.count()).isEqualTo(5);
            assertThat(s.minMs()).isLessThanOrEqualTo(s.maxMs());
            assertThat(s.avgMs()).isBetween(s.minMs(), s.maxMs());
            assertThat(s.format()).contains("op:").contains("avg").contains("n=5");
        }

        @Test
        void getAllStatsCoversAllNames() {
            Timings t = new Timings();
            t.measure("a", () -> {});
            t.measure("b", () -> {});
            assertThat(t.getAllStats()).extracting(Timings.TimingStats::name).containsExactlyInAnyOrder("a", "b");
        }

        @Test
        void formatReportIncludesAllAndTotal() {
            Timings t = new Timings();
            t.measure("a", () -> {});
            t.measure("b", () -> {});
            String report = t.formatReport();
            assertThat(report)
                    .contains("Timing Report")
                    .contains("a:")
                    .contains("b:")
                    .contains("Total:");
        }
    }

    @Nested
    class SpanAccess {

        @Test
        void getSpansReturnsImmutableCopy() {
            Timings t = new Timings();
            t.measure("op", () -> {});
            assertThat(t.getSpans()).hasSize(1);
            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class, () -> t.getSpans().add(null));
        }

        @Test
        void clearResetsAllState() {
            Timings t = new Timings();
            t.start("active");
            t.measure("ended", () -> {});
            t.clear();
            assertThat(t.getSpans()).isEmpty();

            // After clear, ending the previously-active span should also yield null
            assertThat(t.end("active")).isNull();
        }
    }
}
