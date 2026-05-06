/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.event;

import com.campusclaw.ai.types.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Emitted when a message enters processing or streaming.
 */
public record MessageStartEvent(@JsonProperty("message") Message message) implements AgentEvent {}
