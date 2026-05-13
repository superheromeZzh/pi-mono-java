/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp;

import java.util.Locale;

import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentEvent;

/**
 * ACP-wire stop reasons reported by {@code session/prompt}.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public enum AcpStopReason {
    END_TURN,
    MAX_TOKENS,
    MAX_TURN_REQUESTS,
    REFUSAL,
    CANCELLED;

    public static AcpStopReason fromWire(String raw) {
        if (raw == null) {
            return END_TURN;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "end_turn" -> END_TURN;
            case "max_tokens" -> MAX_TOKENS;
            case "max_turn_requests", "max_turn_request" -> MAX_TURN_REQUESTS;
            case "refusal" -> REFUSAL;
            case "cancelled", "canceled" -> CANCELLED;
            default -> END_TURN;
        };
    }

    public SubAgentEvent.StopReason toSubAgent() {
        return switch (this) {
            case END_TURN -> SubAgentEvent.StopReason.END_TURN;
            case MAX_TOKENS, MAX_TURN_REQUESTS -> SubAgentEvent.StopReason.MAX_TOKENS;
            case REFUSAL -> SubAgentEvent.StopReason.REFUSAL;
            case CANCELLED -> SubAgentEvent.StopReason.CANCELLED;
        };
    }
}
