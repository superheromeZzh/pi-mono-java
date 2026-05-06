/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

/**
 * Emitted when a message completes processing or streaming.
 */
public record MessageEndEvent(@JsonProperty("message") Message message) implements AgentEvent {}
