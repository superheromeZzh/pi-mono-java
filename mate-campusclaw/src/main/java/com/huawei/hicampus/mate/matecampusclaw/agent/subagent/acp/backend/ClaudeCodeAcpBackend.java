/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp.backend;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ApprovalClassifier;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ApprovalPolicy;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval.ParentPermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link ProcessAcpBackend} preset for the official Claude Code ACP server.
 *
 * <p>Default invocation is {@code claude --acp}. Both the command and arguments are overridable
 * through configuration to track future Claude Code CLI changes without code edits.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class ClaudeCodeAcpBackend extends ProcessAcpBackend {

    public static final String ID = "claude-code";

    public ClaudeCodeAcpBackend(
            ObjectMapper mapper,
            ApprovalClassifier classifier,
            ApprovalPolicy policy,
            ParentPermissionResolver parentResolver) {
        this(mapper, classifier, policy, parentResolver, "claude", List.of("--acp"));
    }

    public ClaudeCodeAcpBackend(
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
