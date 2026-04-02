package com.huawei.hicampus.mate.matecampusclaw.agent.loop;

import java.util.Objects;

import com.huawei.hicampus.mate.matecampusclaw.agent.context.ContextTransformer;
import com.huawei.hicampus.mate.matecampusclaw.agent.context.DefaultMessageConverter;
import com.huawei.hicampus.mate.matecampusclaw.agent.context.MessageConverter;
import com.huawei.hicampus.mate.matecampusclaw.agent.queue.MessageQueue;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.ToolExecutionMode;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.ToolExecutionPipeline;
import com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.SimpleStreamOptions;

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
