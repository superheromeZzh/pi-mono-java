/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.loop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import com.campusclaw.agent.context.ContextTransformer;
import com.campusclaw.agent.context.MessageConverter;
import com.campusclaw.agent.event.AgentEndEvent;
import com.campusclaw.agent.event.AgentEventListener;
import com.campusclaw.agent.event.AgentStartEvent;
import com.campusclaw.agent.event.MessageEndEvent;
import com.campusclaw.agent.event.MessageStartEvent;
import com.campusclaw.agent.event.MessageUpdateEvent;
import com.campusclaw.agent.event.TurnEndEvent;
import com.campusclaw.agent.event.TurnStartEvent;
import com.campusclaw.agent.queue.MessageQueue;
import com.campusclaw.agent.subagent.acp.AcpTransport;
import com.campusclaw.agent.tool.AgentContext;
import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.agent.tool.CancellationToken;
import com.campusclaw.agent.tool.ToolCallWithTool;
import com.campusclaw.agent.tool.ToolExecutionMode;
import com.campusclaw.agent.tool.ToolExecutionPipeline;
import com.campusclaw.ai.stream.AssistantMessageEvent;
import com.campusclaw.ai.stream.AssistantMessageEventStream;
import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.Context;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.SimpleStreamOptions;
import com.campusclaw.ai.types.StopReason;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.ai.types.Tool;
import com.campusclaw.ai.types.ToolCall;
import com.campusclaw.ai.types.ToolResultMessage;

import reactor.core.publisher.Sinks;

/**
 * Core agent loop that streams assistant responses, executes tools, and manages turn continuation.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
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
            List<Message> prompts, AgentContext context, AgentEventListener listener, CancellationToken signal) {
        return runInternal(prompts != null ? prompts : List.of(), context, listener, signal);
    }

    public List<Message> continueLoop(AgentContext context, AgentEventListener listener, CancellationToken signal) {
        return runInternal(List.of(), context, listener, signal);
    }

    private List<Message> runInternal(
            List<Message> prompts, AgentContext context, AgentEventListener listener, CancellationToken signal) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(signal, "signal");
        AgentEventListener eventListener = listener != null ? listener : event -> {};
        if (!prompts.isEmpty()) {
            context.appendMessages(prompts);
        }
        List<Message> pendingTurnInputs = List.copyOf(prompts);
        eventListener.onEvent(new AgentStartEvent());
        int turn = 0;
        try {
            while (!signal.isCancelled()) {
                turn++;
                AcpTransport.note("AgentLoop.turn=" + turn + " start msgCount="
                        + context.messages().size());
                eventListener.onEvent(new TurnStartEvent());
                emitPendingInputs(pendingTurnInputs, eventListener);
                AssistantMessage assistantMessage = invokeModelTraced(turn, context, eventListener, signal);
                context.appendMessage(assistantMessage);
                context.setAssistantMessage(assistantMessage);
                eventListener.onEvent(new MessageEndEvent(assistantMessage));
                if (assistantMessage.stopReason() == StopReason.ERROR
                        || assistantMessage.stopReason() == StopReason.ABORTED) {
                    AcpTransport.note("AgentLoop.turn=" + turn + " end stopReason=" + assistantMessage.stopReason());
                    eventListener.onEvent(new TurnEndEvent(assistantMessage, List.of()));
                    break;
                }
                var toolCalls = extractToolCalls(assistantMessage);
                AcpTransport.note("AgentLoop.turn=" + turn + " extractedToolCalls=" + toolCalls.size());
                if (!toolCalls.isEmpty()) {
                    pendingTurnInputs =
                            runToolPhaseTraced(turn, context, signal, eventListener, assistantMessage, toolCalls);
                    continue;
                }
                var followUpMessages = drainFollowUpMessages();
                eventListener.onEvent(new TurnEndEvent(assistantMessage, List.of()));
                if (followUpMessages.isEmpty()) {
                    AcpTransport.note("AgentLoop.turn=" + turn + " end no-tools no-followup");
                    break;
                }
                context.appendMessages(followUpMessages);
                pendingTurnInputs = followUpMessages;
            }
            AcpTransport.note("AgentLoop.exit cancelled=" + signal.isCancelled() + " totalTurns=" + turn);
            return context.messages();
        } finally {
            eventListener.onEvent(new AgentEndEvent(context.messages()));
        }
    }

    private AssistantMessage invokeModelTraced(
            int turn, AgentContext context, AgentEventListener listener, CancellationToken signal) {
        try {
            return invokeModel(context, listener, signal);
        } catch (RuntimeException ex) {
            AcpTransport.note("AgentLoop.turn=" + turn + " invokeModel threw: " + ex);
            throw ex;
        }
    }

    private List<Message> runToolPhaseTraced(
            int turn,
            AgentContext context,
            CancellationToken signal,
            AgentEventListener eventListener,
            AssistantMessage assistantMessage,
            List<ToolCall> toolCalls) {
        List<Message> result;
        try {
            result = runToolPhase(context, signal, eventListener, assistantMessage, toolCalls);
        } catch (RuntimeException ex) {
            AcpTransport.note("AgentLoop.turn=" + turn + " runToolPhase threw: " + ex);
            throw ex;
        }
        AcpTransport.note("AgentLoop.turn=" + turn + " runToolPhase done msgCount="
                + context.messages().size() + " cancelled=" + signal.isCancelled());
        return result;
    }

    private List<Message> runToolPhase(
            AgentContext context,
            CancellationToken signal,
            AgentEventListener eventListener,
            AssistantMessage assistantMessage,
            List<ToolCall> toolCalls) {
        var resolved = new ArrayList<ToolCallWithTool>();
        var unknownResults = new ArrayList<ToolResultMessage>();
        resolveToolCallsSafe(toolCalls, context.tools(), resolved, unknownResults);
        AcpTransport.note("AgentLoop.runToolPhase resolved=" + resolved.size() + " unknown=" + unknownResults.size());
        var toolResults = new ArrayList<ToolResultMessage>();
        if (!resolved.isEmpty()) {
            AcpTransport.note("AgentLoop.runToolPhase calling toolPipeline.executeAll");
            toolResults.addAll(toolPipeline.executeAll(resolved, toolExecutionMode, context, signal, eventListener));
            AcpTransport.note("AgentLoop.runToolPhase toolPipeline.executeAll returned results=" + toolResults.size());
        }
        toolResults.addAll(unknownResults);
        context.appendMessages(new ArrayList<>(toolResults));
        var steeringMessages = drainSteeringMessages();
        if (!steeringMessages.isEmpty()) {
            context.appendMessages(steeringMessages);
        }
        AcpTransport.note("AgentLoop.runToolPhase emitting TurnEndEvent steering=" + steeringMessages.size());
        eventListener.onEvent(new TurnEndEvent(assistantMessage, toolResults));
        AcpTransport.note("AgentLoop.runToolPhase TurnEndEvent emitted, returning");
        return steeringMessages;
    }

    private void emitPendingInputs(List<Message> pendingTurnInputs, AgentEventListener listener) {
        for (var message : pendingTurnInputs) {
            listener.onEvent(new MessageStartEvent(message));
            listener.onEvent(new MessageEndEvent(message));
        }
    }

    /**
     * Result of consuming the LLM event stream until cancellation or completion.
     */
    private record StreamConsumeResult(AssistantMessage message, boolean assistantStarted) {}

    private AssistantMessage invokeModel(AgentContext context, AgentEventListener listener, CancellationToken signal) {
        var transformedMessages = transformMessages(context.messages(), signal);
        var llmMessages = convertToLlm.convert(transformedMessages);
        var llmContext = new Context(context.systemPrompt(), llmMessages, toLlmTools(context.tools()));
        var stream = streamFunction.stream(model, llmContext, streamOptions);
        var cancelSink = Sinks.<Object>one();
        signal.onCancel(() -> cancelSink.tryEmitEmpty());
        var result = consumeStream(stream, cancelSink, listener, signal);
        if (signal.isCancelled()) {
            return synthesizeAbortedMessage(result, listener);
        }
        var assistantMessage = result.message();
        if (assistantMessage == null) {
            assistantMessage = stream.result().block();
            if (assistantMessage != null && !result.assistantStarted()) {
                listener.onEvent(new MessageStartEvent(assistantMessage));
            }
        }
        if (assistantMessage == null) {
            throw new IllegalStateException("LLM stream completed without producing an assistant message");
        }
        noteAssistant(assistantMessage, context.messages().size());
        return assistantMessage;
    }

    private static void noteAssistant(AssistantMessage msg, int messageCount) {
        int textChars = 0;
        int toolCalls = 0;
        int thinking = 0;
        int otherBlocks = 0;
        for (var block : msg.content()) {
            if (block instanceof TextContent tc) {
                textChars += tc.text() == null ? 0 : tc.text().length();
            } else if (block instanceof ToolCall) {
                toolCalls++;
            } else if (block.getClass().getSimpleName().contains("Thinking")) {
                thinking++;
            } else {
                otherBlocks++;
            }
        }
        AcpTransport.note("AgentLoop.invokeModel returned: textChars=" + textChars
                + " toolCalls=" + toolCalls + " thinking=" + thinking + " otherBlocks=" + otherBlocks
                + " stopReason=" + msg.stopReason() + " msgCountInCtx=" + messageCount);
    }

    private StreamConsumeResult consumeStream(
            AssistantMessageEventStream stream,
            Sinks.One<Object> cancelSink,
            AgentEventListener listener,
            CancellationToken signal) {
        AssistantMessage assistantMessage = null;
        var assistantStarted = false;
        for (var event : stream.asFlux().takeUntilOther(cancelSink.asMono()).toIterable()) {
            if (signal.isCancelled()) {
                break;
            }
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
        return new StreamConsumeResult(assistantMessage, assistantStarted);
    }

    // Synthesize an ABORTED message so the outer loop terminates cleanly
    // instead of falling through to result().block(), which would hang on the
    // torn-down stream.
    private AssistantMessage synthesizeAbortedMessage(StreamConsumeResult result, AgentEventListener listener) {
        var msg = result.message();
        var aborted = new AssistantMessage(
                msg != null ? msg.content() : List.of(),
                msg != null ? msg.api() : model.api().value(),
                msg != null ? msg.provider() : model.provider().value(),
                msg != null ? msg.model() : model.id(),
                msg != null ? msg.responseId() : null,
                msg != null ? msg.usage() : null,
                StopReason.ABORTED,
                null,
                System.currentTimeMillis());
        if (!result.assistantStarted()) {
            listener.onEvent(new MessageStartEvent(aborted));
        }
        return aborted;
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
     *
     * @param toolCalls the tool calls emitted by the assistant
     * @param tools the catalog of tools currently available to the agent
     * @param resolved out-parameter populated with calls whose tool was found
     * @param unknownResults out-parameter populated with error results for unknown tools
     */
    private void resolveToolCallsSafe(
            List<ToolCall> toolCalls,
            List<AgentTool> tools,
            List<ToolCallWithTool> resolved,
            List<ToolResultMessage> unknownResults) {

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
                        System.currentTimeMillis()));
            }
        }
    }

    private List<Message> drainSteeringMessages() {
        if (getSteeringMessages != null) {
            var msgs = getSteeringMessages.get();
            if (msgs != null && !msgs.isEmpty()) {
                return msgs;
            }
        }
        return steeringQueue.drain();
    }

    private List<Message> drainFollowUpMessages() {
        if (getFollowUpMessages != null) {
            var msgs = getFollowUpMessages.get();
            if (msgs != null && !msgs.isEmpty()) {
                return msgs;
            }
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
