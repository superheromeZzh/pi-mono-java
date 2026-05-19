/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.event;

import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted for incremental assistant message updates.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record MessageUpdateEvent(
        @JsonProperty("message") Message message,
        @JsonProperty("assistantMessageEvent") AssistantMessageEvent assistantMessageEvent)
        implements AgentEvent {}
