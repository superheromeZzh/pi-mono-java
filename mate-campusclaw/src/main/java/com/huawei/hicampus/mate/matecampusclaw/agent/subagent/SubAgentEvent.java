/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent;

import java.util.Map;

/**
 * Sealed union of events streamed from a sub-agent backend back to the parent agent.
 *
 * <p>Modelled after the ACP {@code session/update} notification surface, but transport-agnostic so
 * HTTP/WebSocket backends can emit the same shape.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public sealed interface SubAgentEvent {

    /**
     * Stream variant. {@code OUTPUT} is the agent's main reply; {@code THOUGHT} is reasoning.
     */
    enum Stream {
        OUTPUT,
        THOUGHT
    }

    /**
     * Status of a tool call lifecycle event.
     */
    enum ToolCallStatus {
        STARTED,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    /**
     * Reason a sub-agent turn ended. Mirrors ACP stop reasons.
     */
    enum StopReason {
        END_TURN,
        MAX_TOKENS,
        REFUSAL,
        CANCELLED,
        ERROR
    }

    /**
     * Text chunk emitted by the sub-agent (assistant output or reasoning).
     */
    record TextDelta(Stream stream, String text) implements SubAgentEvent {}

    /**
     * Sub-agent invoked a tool.
     */
    record ToolCall(String toolCallId, String name, String title, ToolCallStatus status) implements SubAgentEvent {}

    /**
     * Backend status update (token usage, available commands, mode, etc.).
     */
    record Status(String summary, Map<String, Object> details) implements SubAgentEvent {}

    /**
     * Backend asked the parent for permission to perform an action.
     *
     * <p>The parent must respond via {@link SubAgentBackend#respondPermission}.
     */
    record PermissionRequest(String requestId, String toolName, Map<String, Object> params) implements SubAgentEvent {}

    /**
     * Turn finished successfully (or was cancelled).
     */
    record Done(StopReason stopReason) implements SubAgentEvent {}

    /**
     * Terminal error.
     */
    record Error(String code, String message, boolean retryable) implements SubAgentEvent {}
}
