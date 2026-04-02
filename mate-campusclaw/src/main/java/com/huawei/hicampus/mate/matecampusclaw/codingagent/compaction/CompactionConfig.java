package com.huawei.hicampus.mate.matecampusclaw.codingagent.compaction;

public record CompactionConfig(
    boolean enabled,
    int reserveTokens,
    int keepRecentTokens
) {
    public static CompactionConfig defaults() {
        return new CompactionConfig(true, 16384, 20000);
    }
}
