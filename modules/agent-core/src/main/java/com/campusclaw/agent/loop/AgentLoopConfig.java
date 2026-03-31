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
    SteeringMessageSupplier getFollowUpMessages
) {

    /** Legacy constructor for backward compatibility. */
    public AgentLoopConfig(
        CampusClawAiService piAiService,
        Model model,
        MessageConverter convertToLlm,
        ContextTransformer transformContext,
        ToolExecutionPipeline toolPipeline,
        ToolExecutionMode toolExecutionMode,
        MessageQueue steeringQueue,
        MessageQueue followUpQueue,
        SimpleStreamOptions streamOptions
    ) {
        this(piAiService, model, convertToLlm, transformContext, toolPipeline,
            toolExecutionMode, steeringQueue, followUpQueue, streamOptions,
            null, null, null);
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
     * or one wrapping the CampusClawAiService.
     */
    public StreamFunction effectiveStreamFunction() {
        if (streamFunction != null) return streamFunction;
        return piAiService::streamSimple;
    }
}
