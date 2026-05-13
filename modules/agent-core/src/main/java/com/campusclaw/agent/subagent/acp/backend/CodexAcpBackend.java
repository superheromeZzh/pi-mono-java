/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.acp.backend;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.campusclaw.agent.subagent.approval.ApprovalClassifier;
import com.campusclaw.agent.subagent.approval.ApprovalPolicy;
import com.campusclaw.agent.subagent.approval.ParentPermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link ProcessAcpBackend} preset for the Zed {@code codex-acp} server (covers OpenAI Codex and
 * Gemini-style harnesses).
 *
 * <p>Default invocation is the bare {@code codex-acp} binary. Both the command and arguments are
 * overridable through configuration to track future Codex CLI changes without code edits.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class CodexAcpBackend extends ProcessAcpBackend {

    public static final String ID = "codex";

    public CodexAcpBackend(
            ObjectMapper mapper,
            ApprovalClassifier classifier,
            ApprovalPolicy policy,
            ParentPermissionResolver parentResolver) {
        this(mapper, classifier, policy, parentResolver, "codex-acp", List.of());
    }

    public CodexAcpBackend(
            ObjectMapper mapper,
            ApprovalClassifier classifier,
            ApprovalPolicy policy,
            ParentPermissionResolver parentResolver,
            String command,
            List<String> args) {
        super(
                ID,
                new Config(command, args, Map.of(), "campusclaw-acp", "1.0.0", Duration.ofMinutes(15L)),
                mapper,
                classifier,
                policy,
                parentResolver);
    }
}
