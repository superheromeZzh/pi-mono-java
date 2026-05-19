/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.approval;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Coarse risk classification for tool calls coming from a sub-agent. Mirrors the read-only /
 * write / exec / network buckets used by OpenClaw's ACP approval classifier.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Component
public class ApprovalClassifier {

    /**
     * Risk bucket for a tool call.
     */
    public enum Risk {
        READ_ONLY,
        FILE_WRITE,
        EXEC,
        NETWORK,
        UNKNOWN
    }

    private static final Set<String> READ_ONLY_NAMES =
            Set.of("read", "ls", "glob", "grep", "view", "search", "list", "describe");
    private static final Set<String> FILE_WRITE_NAMES =
            Set.of("write", "edit", "editdiff", "create", "mkdir", "delete", "remove", "rename", "move");
    private static final Set<String> EXEC_NAMES = Set.of("bash", "shell", "exec", "run", "spawn", "kill");
    private static final Set<String> NETWORK_NAMES =
            Set.of("webfetch", "fetch", "curl", "http", "websearch", "download", "upload");

    public Risk classify(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Risk.UNKNOWN;
        }
        String normalized = toolName.trim().toLowerCase(Locale.ROOT);
        if (READ_ONLY_NAMES.contains(normalized)) {
            return Risk.READ_ONLY;
        }
        if (FILE_WRITE_NAMES.contains(normalized)) {
            return Risk.FILE_WRITE;
        }
        if (EXEC_NAMES.contains(normalized)) {
            return Risk.EXEC;
        }
        if (NETWORK_NAMES.contains(normalized)) {
            return Risk.NETWORK;
        }
        return classifyByPrefix(normalized);
    }

    private static Risk classifyByPrefix(String normalized) {
        if (normalized.startsWith("read_") || normalized.startsWith("get_") || normalized.startsWith("list_")) {
            return Risk.READ_ONLY;
        }
        if (normalized.startsWith("write_") || normalized.startsWith("edit_") || normalized.startsWith("delete_")) {
            return Risk.FILE_WRITE;
        }
        if (normalized.startsWith("exec_") || normalized.startsWith("run_")) {
            return Risk.EXEC;
        }
        if (normalized.startsWith("web") || normalized.startsWith("http_") || normalized.startsWith("fetch_")) {
            return Risk.NETWORK;
        }
        return Risk.UNKNOWN;
    }
}
