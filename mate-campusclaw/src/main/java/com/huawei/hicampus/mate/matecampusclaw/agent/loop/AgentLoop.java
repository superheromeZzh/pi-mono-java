package com.huawei.hicampus.mate.matecampusclaw.agent.loop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import com.huawei.hicampus.mate.matecampusclaw.agent.context.ContextTransformer;
import com.huawei.hicampus.mate.matecampusclaw.agent.context.MessageConverter;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEventListener;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.MessageEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.MessageStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.MessageUpdateEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.TurnEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.TurnStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.queue.MessageQueue;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentContext;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.ToolCallWithTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.ToolExecutionMode;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.ToolExecutionPipeline;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Context;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.SimpleStreamOptions;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Tool;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage;

/**
 * Core agent loop that streams assistant responses, executes tools, and manages turn continuation.
 */
public class AgentLoop {

    private final StreamFunction streamFunction;
    private final Model model;
    private final MessageConverter convertToLlm;
    private final ContextTransformer transformContext;
    private final ToolExecutionPipeline toolPipeline;
    private final ToolExecutionMode toolExecutionMode;
    private final MessageQueue steeringQueue;
    private final MessageQueue followUpQueue;
    private final SimpleStreamOptions streamOptions;
    private final SteeringMessageSupplier getSteeringMessages;
    private final SteeringMessageSupplier getFollowUpMessages;

    public AgentLoop(AgentLoopConfig config) {
        Objects.requireNonNull(config, "config");
        this.streamFunction = config.effectiveStreamFunction();
        this.model = config.model();
        this.convertToLlm = config.convertToLlm();
        this.transformContext = config.transformContext();
        this.toolPipeline = config.toolPipeline();
        this.toolExecutionMode = config.toolExecutionMode();
        this.steeringQueue = config.steeringQueue();
        this.followUpQueue = config.followUpQueue();
        this.streamOptions = config.streamOptions();
        this.getSteeringMessages = config.getSteeringMessages();
        this.getFollowUpMessages = config.getFollowUpMessages();
    }

    public List<Message> run(
        List<Message> prompts,
        AgentContext context,
        AgentEventListener listener,
        CancellationToken signal
    ) {
        return runInternal(prompts != null ? prompts : List.of(), context, listener, signal);
    }

    public List<Message> continueLoop(
        AgentContext context,
        AgentEventListener listener,
        CancellationToken signal
    ) {
        return runInternal(List.of(), context, listener, signal);
    }

    private List<Message> runInternal(
        List<Message> prompts,
        AgentContext context,
        AgentEventListener listener,
        CancellationToken signal
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(signal, "signal");

        AgentEventListener eventListener = listener != null ? listener : event -> {
        };

        if (!prompts.isEmpty()) {
            context.appendMessages(prompts);
        }

        List<Message> pendingTurnInputs = List.copyOf(prompts);
        eventListener.onEvent(new AgentStartEvent());

        try {
            while (!signal.isCancelled()) {
                eventListener.onEvent(new TurnStartEvent());
                emitPendingInputs(pendingTurnInputs, eventListener);

                var assistantMessage = invokeModel(context, eventListener, signal);
                context.appendMessage(assistantMessage);
                context.setAssistantMessage(assistantMessage);
                eventListener.onEvent(new MessageEndEvent(assistantMessage));

                // Stop on error or aborted stop reasons
                if (assistantMessage.stopReason() == StopReason.ERROR
                        || assistantMessage.stopReason() == StopReason.ABORTED) {
                    eventListener.onEvent(new TurnEndEvent(assistantMessage, List.of()));
                    break;
                }

                var toolCalls = extractToolCalls(assistantMessage);
                if (!toolCalls.isEmpty()) {
                    // Separate resolved tools from unknown tools
                    var resolved = new ArrayList<ToolCallWithTool>();
                    var unknownResults = new ArrayList<ToolResultMessage>();
                    resolveToolCallsSafe(toolCalls, context.tools(), resolved, unknownResults);

                    // Execute resolved tools via pipeline
                    var toolResults = new ArrayList<ToolResultMessage>();
                    if (!resolved.isEmpty()) {
                        toolResults.addAll(toolPipeline.executeAll(
                            resolved, toolExecutionMode, context, signal, eventListener));
                    }
                    // Add error results for unknown tools
                    toolResults.addAll(unknownResults);
                    context.appendMessages(new ArrayList<>(toolResults));

                    var steeringMessages = drainSteeringMessages();
                    if (!steeringMessages.isEmpty()) {
                        context.appendMessages(steeringMessages);
                    }

                    eventListener.onEvent(new TurnEndEvent(assistantMessage, toolResults));
                    pendingTurnInputs = steeringMessages;
                    continue;
                }

                var followUpMessages = drainFollowUpMessages();
                eventListener.onEvent(new TurnEndEvent(assistantMessage, List.of()));
                if (!followUpMessages.isEmpty()) {
                    context.appendMessages(followUpMessages);
                    pendingTurnInputs = followUpMessages;
                    continue;
                }

                break;
            }

            return context.messages();
        } finally {
            eventListener.onEvent(new AgentEndEvent(context.messages()));
        }
    }

    private void emitPendingInputs(List<Message> pendingTurnInputs, AgentEventListener listener) {
        for (var message : pendingTurnInputs) {
            listener.onEvent(new MessageStartEvent(message));
            listener.onEvent(new MessageEndEvent(message));
        }
    }

    private AssistantMessage invokeModel(
        AgentContext context,
        AgentEventListener listener,
        CancellationToken signal
    ) {
        var transformedMessages = transformMessages(context.messages(), signal);
        var llmMessages = convertToLlm.convert(transformedMessages);
        var llmContext = new Context(
            context.systemPrompt(),
            llmMessages,
            toLlmTools(context.tools())
        );

        var stream = streamFunction.stream(model, llmContext, streamOptions);
        AssistantMessage assistantMessage = null;
        var assistantStarted = false;

        for (var event : stream.asFlux().toIterable()) {
            if (signal.isCancelled()) break;
            var currentMessage = extractAssistantMessage(event);
            if (!assistantStarted) {
                listener.onEvent(new MessageStartEvent(currentMessage));
                assistantStarted = true;
            }
            if (!(event instanceof AssistantMessageEvent.StartEvent)) {
                listener.onEvent(new MessageUpdateEvent(currentMessage, event));
            }
            assistantMessage = currentMessage;
        }

        if (assistantMessage == null) {
            assistantMessage = stream.result().block();
            if (assistantMessage != null && !assistantStarted) {
                listener.onEvent(new MessageStartEvent(assistantMessage));
            }
        }

        if (assistantMessage == null) {
            throw new IllegalStateException("LLM stream completed without producing an assistant message");
        }

        return assistantMessage;
    }

    private List<Message> transformMessages(List<Message> messages, CancellationToken signal) {
        if (transformContext == null) {
            return messages;
        }

        try {
            return transformContext.transform(messages, signal).join();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to transform context", e);
        }
    }

    private List<Tool> toLlmTools(List<AgentTool> tools) {
        if (tools.isEmpty()) {
            return List.of();
        }

        return tools.stream()
            .map(tool -> new Tool(tool.name(), tool.description(), tool.parameters()))
            .toList();
    }

    private List<ToolCall> extractToolCalls(AssistantMessage assistantMessage) {
        var toolCalls = new ArrayList<ToolCall>();
        for (var block : assistantMessage.content()) {
            if (block instanceof ToolCall toolCall) {
                toolCalls.add(toolCall);
            }
        }
        return List.copyOf(toolCalls);
    }

    /**
     * Resolves tool calls, separating known tools from unknown ones.
     * Unknown tools get an error result instead of throwing, matching TS behavior.
     */
    private void resolveToolCallsSafe(
            List<ToolCall> toolCalls, List<AgentTool> tools,
            List<ToolCallWithTool> resolved, List<ToolResultMessage> unknownResults) {

        var toolsByName = new LinkedHashMap<String, AgentTool>();
        for (var tool : tools) {
            toolsByName.put(tool.name(), tool);
        }

        for (var toolCall : toolCalls) {
            var tool = toolsByName.get(toolCall.name());
            if (tool != null) {
                resolved.add(new ToolCallWithTool(toolCall, tool, toolCall.arguments()));
            } else {
                // Return error result for unknown tool (agent can adapt)
                unknownResults.add(new ToolResultMessage(
                        toolCall.id(),
                        toolCall.name(),
                        List.of(new TextContent("Tool " + toolCall.name() + " not found", null)),
                        null,
                        true,
                        System.currentTimeMillis()
                ));
            }
        }
    }

    private List<Message> drainSteeringMessages() {
        if (getSteeringMessages != null) {
            var msgs = getSteeringMessages.get();
            if (msgs != null && !msgs.isEmpty()) return msgs;
        }
        return steeringQueue.drain();
    }

    private List<Message> drainFollowUpMessages() {
        if (getFollowUpMessages != null) {
            var msgs = getFollowUpMessages.get();
            if (msgs != null && !msgs.isEmpty()) return msgs;
        }
        return followUpQueue.drain();
    }

    private AssistantMessage extractAssistantMessage(AssistantMessageEvent event) {
        return switch (event) {
            case AssistantMessageEvent.StartEvent e -> e.partial();
            case AssistantMessageEvent.TextStartEvent e -> e.partial();
            case AssistantMessageEvent.TextDeltaEvent e -> e.partial();
            case AssistantMessageEvent.TextEndEvent e -> e.partial();
            case AssistantMessageEvent.ThinkingStartEvent e -> e.partial();
            case AssistantMessageEvent.ThinkingDeltaEvent e -> e.partial();
            case AssistantMessageEvent.ThinkingEndEvent e -> e.partial();
            case AssistantMessageEvent.ToolCallStartEvent e -> e.partial();
            case AssistantMessageEvent.ToolCallDeltaEvent e -> e.partial();
            case AssistantMessageEvent.ToolCallEndEvent e -> e.partial();
            case AssistantMessageEvent.DoneEvent e -> e.message();
            case AssistantMessageEvent.ErrorEvent e -> e.error();
        };
    }
}
