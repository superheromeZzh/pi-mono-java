/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.event;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

/**
 * Emitted when an agent run ends with the final message history.
 */
public record AgentEndEvent(@JsonProperty("messages") List<Message> messages) implements AgentEvent {}
