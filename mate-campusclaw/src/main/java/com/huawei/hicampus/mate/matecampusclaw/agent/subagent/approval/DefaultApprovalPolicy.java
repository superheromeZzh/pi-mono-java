/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.approval;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Default approval policy. Behaviour is controlled by {@code subagent.approval.default}:
 *
 * <ul>
 *   <li>{@code allow} (default) — every tool call AUTO_ALLOW. Sub-agent inherits the parent's
 *       trust domain; matches "bypass" semantics and is the only mode that "just works" until an
 *       interactive {@link ParentPermissionResolver} is wired.</li>
 *   <li>{@code ask} — READ_ONLY auto-allow, everything else ASK_PARENT. Only useful once a real
 *       interactive resolver (TUI prompt, RPC round-trip) replaces {@code TimeoutDeniedResolver}.
 *       </li>
 *   <li>{@code deny} — every tool call DENY. Useful for drills / sandbox testing.</li>
 * </ul>
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/14]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Component
public class DefaultApprovalPolicy implements ApprovalPolicy {

    private static final Logger log = LoggerFactory.getLogger(DefaultApprovalPolicy.class);

    /**
     * Approval mode selected by configuration.
     */
    public enum Mode {
        ALLOW,
        ASK,
        DENY
    }

    private final Mode mode;

    public DefaultApprovalPolicy(@Value("${subagent.approval.default:allow}") String configured) {
        this.mode = parse(configured);
        log.info("sub-agent approval mode: {}", mode);
    }

    @Override
    public ApprovalDecision decide(ApprovalClassifier.Risk risk, String toolName) {
        return switch (mode) {
            case ALLOW -> ApprovalDecision.AUTO_ALLOW;
            case DENY -> ApprovalDecision.DENY;
            case ASK ->
                risk == ApprovalClassifier.Risk.READ_ONLY ? ApprovalDecision.AUTO_ALLOW : ApprovalDecision.ASK_PARENT;
        };
    }

    private static Mode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mode.ALLOW;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "allow", "auto", "auto-allow" -> Mode.ALLOW;
            case "deny", "denied" -> Mode.DENY;
            case "ask", "ask-parent", "interactive" -> Mode.ASK;
            default -> {
                log.warn("unknown subagent.approval.default='{}', falling back to ALLOW", raw);
                yield Mode.ALLOW;
            }
        };
    }
}
