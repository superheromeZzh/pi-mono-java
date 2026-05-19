/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.loop;

import java.util.Objects;

import com.campusclaw.agent.context.ContextTransformer;
import com.campusclaw.agent.context.DefaultMessageConverter;
import com.campusclaw.agent.context.MessageConverter;
import com.campusclaw.agent.queue.MessageQueue;
import com.campusclaw.agent.tool.ToolExecutionMode;
import com.campusclaw.agent.tool.ToolExecutionPipeline;
import com.campusclaw.ai.CampusClawAiService;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.SimpleStreamOptions;

/**
 * Configuration required to run the agent loop.
 *
 * <p>Supports both the legacy {@link CampusClawAiService} and the new pluggable
 * {@link StreamFunction} for LLM streaming. If {@code streamFunction} is
 * provided, it takes precedence over {@code piAiService}.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record AgentLoopConfig(
        CampusClawAiService piAiService,
        Model model,
        MessageConverter convertToLlm,
        ContextTransformer transformContext,
        ToolExecutionPipeline toolPipeline,
        ToolExecutionMode toolExecutionMode,
        MessageQueue steeringQueue,
        MessageQueue followUpQueue,
        SimpleStreamOptions streamOptions,
        StreamFunction streamFunction,
        SteeringMessageSupplier getSteeringMessages,
        SteeringMessageSupplier getFollowUpMessages) {

    /**
     * Legacy constructor used before the pluggable {@code StreamFunction} hook was added.
     * Delegates to the canonical constructor with {@code streamFunction},
     * {@code getSteeringMessages} and {@code getFollowUpMessages} defaulted to {@code null}.
     *
     * @param piAiService the {@link CampusClawAiService} that drives LLM streaming
     * @param model target LLM model
     * @param convertToLlm converter from internal messages to LLM-shaped messages
     * @param transformContext asynchronous context transformer applied before each turn
     * @param toolPipeline pipeline that executes tool calls returned by the LLM
     * @param toolExecutionMode sequential vs. parallel tool execution policy
     * @param steeringQueue queue of steering messages injected mid-turn
     * @param followUpQueue queue of follow-up messages injected after each turn
     * @param streamOptions base stream options (temperature, max tokens, etc.)
     */
    public AgentLoopConfig(
            CampusClawAiService piAiService,
            Model model,
            MessageConverter convertToLlm,
            ContextTransformer transformContext,
            ToolExecutionPipeline toolPipeline,
            ToolExecutionMode toolExecutionMode,
            MessageQueue steeringQueue,
            MessageQueue followUpQueue,
            SimpleStreamOptions streamOptions) {
        this(
                piAiService,
                model,
                convertToLlm,
                transformContext,
                toolPipeline,
                toolExecutionMode,
                steeringQueue,
                followUpQueue,
                streamOptions,
                null,
                null,
                null);
    }

    public AgentLoopConfig {
        Objects.requireNonNull(model, "model");
        if (piAiService == null && streamFunction == null) {
            throw new IllegalArgumentException("Either piAiService or streamFunction must be provided");
        }
        convertToLlm = convertToLlm != null ? convertToLlm : new DefaultMessageConverter();
        toolPipeline = toolPipeline != null ? toolPipeline : new ToolExecutionPipeline();
        toolExecutionMode = toolExecutionMode != null ? toolExecutionMode : ToolExecutionMode.SEQUENTIAL;
        steeringQueue = steeringQueue != null ? steeringQueue : new MessageQueue();
        followUpQueue = followUpQueue != null ? followUpQueue : new MessageQueue();
        streamOptions = streamOptions != null ? streamOptions : SimpleStreamOptions.empty();
    }

    /**
     * Returns the effective stream function, either the explicitly provided one
     * or one wrapping the {@link CampusClawAiService}.
     *
     * @return the configured {@link StreamFunction}, falling back to a method-reference
     *         wrapper around {@link CampusClawAiService#streamSimple} when none is set
     */
    public StreamFunction effectiveStreamFunction() {
        if (streamFunction != null) {
            return streamFunction;
        }
        return piAiService::streamSimple;
    }
}
