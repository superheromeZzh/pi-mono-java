/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.util;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;

/**
 * Performance timing tracker for measuring execution durations.
 * Supports named spans, nested timings, and statistical aggregation.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class Timings {
    private static final Logger log = LoggerFactory.getLogger(Timings.class);

    @SuppressWarnings("checkstyle:top_class_comment")
    public record TimingSpan(
            String name,
            long startNanos,
            long endNanos,
            @Nullable String parentName,
            @Nullable Map<String, String> metadata) {
        public long durationNanos() {
            return endNanos - startNanos;
        }

        public double durationMs() {
            return durationNanos() / 1_000_000.0;
        }

        public double durationSecs() {
            return durationNanos() / 1_000_000_000.0;
        }
    }

    @SuppressWarnings("checkstyle:top_class_comment")
    public record TimingStats(
            String name,
            int count,
            double minMs,
            double maxMs,
            double avgMs,
            double totalMs,
            double p50Ms,
            double p95Ms,
            double p99Ms) {
        public String format() {
            if (count == 1) {
                return String.format(Locale.ROOT, "%s: %.1fms", name, totalMs);
            }
            return String.format(
                    "%s: %.1fms avg (min=%.1f, max=%.1f, p95=%.1f, n=%d)", name, avgMs, minMs, maxMs, p95Ms, count);
        }
    }

    private final Deque<TimingSpan> spans = new ConcurrentLinkedDeque<>();
    private final Map<String, Long> activeSpans = new ConcurrentHashMap<>();
    private final Map<String, String> activeParents = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;

    /**
     * Start a named timing span.
     *
     * @param name the name
     */
    public void start(String name) {
        if (!enabled) {
            return;
        }
        activeSpans.put(name, System.nanoTime());
    }

    /**
     * Start a nested timing span with a parent.
     *
     * @param name the name
     * @param parentName the parentName
     */
    public void start(String name, String parentName) {
        if (!enabled) {
            return;
        }
        activeSpans.put(name, System.nanoTime());
        activeParents.put(name, parentName);
    }

    /**
     * End a timing span and record it.
     *
     * @param name the name
     * @return the result
     */
    public @Nullable TimingSpan end(String name) {
        return end(name, null);
    }

    /**
     * End a timing span with optional metadata.
     *
     * @param name the name
     * @param metadata the metadata
     * @return the result
     */
    public @Nullable TimingSpan end(String name, @Nullable Map<String, String> metadata) {
        if (!enabled) {
            return null;
        }
        Long startNanos = activeSpans.remove(name);
        if (startNanos == null) {
            log.debug("No active timing span: {}", name);
            return null;
        }
        String parent = activeParents.remove(name);
        TimingSpan span = new TimingSpan(name, startNanos, System.nanoTime(), parent, metadata);
        spans.addLast(span);
        return span;
    }

    /**
     * Measure a block of code and return the result.
     *
     * @param <T> the value type returned by {@code block}
     * @param name the span name
     * @param block the work to time
     * @return the value returned by {@code block}
     */
    public <T> T measure(String name, java.util.function.Supplier<T> block) {
        start(name);
        try {
            return block.get();
        } finally {
            end(name);
        }
    }

    /**
     * Measure a void block of code.
     *
     * @param name the name
     * @param block the block
     */
    public void measure(String name, Runnable block) {
        start(name);
        try {
            block.run();
        } finally {
            end(name);
        }
    }

    /**
     * Get statistics for a named span across all recordings.
     *
     * @param name the name
     * @return the result
     */
    public Optional<TimingStats> getStats(String name) {
        List<Double> durations = spans.stream()
                .filter(s -> s.name().equals(name))
                .map(TimingSpan::durationMs)
                .sorted()
                .toList();

        if (durations.isEmpty()) {
            return Optional.empty();
        }

        int n = durations.size();
        double sum = durations.stream().mapToDouble(d -> d).sum();
        return Optional.of(new TimingStats(
                name,
                n,
                durations.get(0),
                durations.get(n - 1),
                sum / n,
                sum,
                percentile(durations, 0.50),
                percentile(durations, 0.95),
                percentile(durations, 0.99)));
    }

    /**
     * Get all recorded spans.
     *
     * @return the result
     */
    public List<TimingSpan> getSpans() {
        return List.copyOf(spans);
    }

    /**
     * Get spans for a specific name.
     *
     * @param name the name
     * @return the result
     */
    public List<TimingSpan> getSpans(String name) {
        return spans.stream().filter(s -> s.name().equals(name)).toList();
    }

    /**
     * Get stats for all named spans.
     *
     * @return the result
     */
    public List<TimingStats> getAllStats() {
        Set<String> names = new LinkedHashSet<>();
        spans.forEach(s -> names.add(s.name()));
        List<TimingStats> stats = new ArrayList<>();
        for (String name : names) {
            getStats(name).ifPresent(stats::add);
        }
        return stats;
    }

    /**
     * Format a summary report of all timings.
     *
     * @return the result
     */
    public String formatReport() {
        var sb = new StringBuilder();
        sb.append("=== Timing Report ===\n");
        for (TimingStats stats : getAllStats()) {
            sb.append("  ").append(stats.format()).append('\n');
        }
        double totalMs = spans.stream().mapToDouble(TimingSpan::durationMs).sum();
        sb.append(String.format(Locale.ROOT, "  Total: %.1fms\n", totalMs));
        return sb.toString();
    }

    /**
     * Clear all recorded spans.
     */
    public void clear() {
        spans.clear();
        activeSpans.clear();
        activeParents.clear();
    }

    /**
     * Enable or disable timing recording.
     *
     * @param enabled the enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
