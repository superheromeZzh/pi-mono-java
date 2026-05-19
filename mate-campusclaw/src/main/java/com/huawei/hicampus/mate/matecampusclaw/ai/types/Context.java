/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

/**
 * The full context supplied to an LLM for a single turn of conversation.
 *
 * @param systemPrompt optional system-level instructions
 * @param messages     the conversation history
 * @param tools        optional list of tools available for the LLM to invoke
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record Context(
        @JsonProperty("systemPrompt") @Nullable String systemPrompt,
        @JsonProperty("messages") List<Message> messages,
        @JsonProperty("tools") @Nullable List<Tool> tools) {}
