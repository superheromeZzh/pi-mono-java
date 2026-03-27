package com.mariozechner.pi.agent.loop;

import com.mariozechner.pi.agent.context.ContextTransformer;
import com.mariozechner.pi.agent.context.MessageConverter;
import com.mariozechner.pi.agent.event.AgentEndEvent;
import com.mariozechner.pi.agent.event.AgentEventListener;
import com.mariozechner.pi.agent.event.AgentStartEvent;
import com.mariozechner.pi.agent.event.MessageEndEvent;
import com.mariozechner.pi.agent.event.MessageStartEvent;
import com.mariozechner.pi.agent.event.MessageUpdateEvent;
import com.mariozechner.pi.agent.event.TurnEndEvent;
import com.mariozechner.pi.agent.event.TurnStartEvent;
import com.mariozechner.pi.agent.queue.MessageQueue;
import com.mariozechner.pi.agent.tool.AgentContext;
import com.mariozechner.pi.agent.tool.AgentTool;
import com.mariozechner.pi.agent.tool.CancellationToken;
import com.mariozechner.pi.agent.tool.ToolCallWithTool;
import com.mariozechner.pi.agent.tool.ToolExecutionMode;
import com.mariozechner.pi.agent.tool.ToolExecutionPipeline;
import com.mariozechner.pi.ai.stream.AssistantMessageEvent;
import com.mariozechner.pi.ai.types.AssistantMessage;
import com.mariozechner.pi.ai.types.Context;
import com.mariozechner.pi.ai.types.Message;
import com.mariozechner.pi.ai.types.Model;
import com.mariozechner.pi.ai.types.SimpleStreamOptions;
import com.mariozechner.pi.ai.types.StopReason;
import com.mariozechner.pi.ai.types.Tool;
import com.mariozechner.pi.ai.types.ToolCall;
import com.mariozechner.pi.ai.types.ToolResultMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
                    var toolResults = toolPipeline.executeAll(
                        resolveToolCalls(toolCalls, context.tools()),
                        toolExecutionMode,
                        context,
                        signal,
                        eventListener
                    );
                    context.appendMessages(toolResults);

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

    private List<ToolCallWithTool> resolveToolCalls(List<ToolCall> toolCalls, List<AgentTool> tools) {
        var toolsByName = new LinkedHashMap<String, AgentTool>();
        for (var tool : tools) {
            toolsByName.put(tool.name(), tool);
        }

        var resolved = new ArrayList<ToolCallWithTool>(toolCalls.size());
        for (var toolCall : toolCalls) {
            var tool = toolsByName.get(toolCall.name());
            if (tool == null) {
                throw new IllegalArgumentException("No AgentTool registered for tool call: " + toolCall.name());
            }
            resolved.add(new ToolCallWithTool(toolCall, tool, toolCall.arguments()));
        }
        return List.copyOf(resolved);
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
