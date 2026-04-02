package com.huawei.hicampus.mate.matecampusclaw.agent;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import com.huawei.hicampus.mate.matecampusclaw.agent.context.ContextTransformer;
import com.huawei.hicampus.mate.matecampusclaw.agent.context.DefaultMessageConverter;
import com.huawei.hicampus.mate.matecampusclaw.agent.context.MessageConverter;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEventListener;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.MessageEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.MessageStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.MessageUpdateEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.ToolExecutionEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.ToolExecutionStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.loop.AgentLoop;
import com.huawei.hicampus.mate.matecampusclaw.agent.loop.AgentLoopConfig;
import com.huawei.hicampus.mate.matecampusclaw.agent.queue.MessageQueue;
import com.huawei.hicampus.mate.matecampusclaw.agent.state.AgentState;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AfterToolCallHandler;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentContext;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.BeforeToolCallHandler;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.ToolExecutionMode;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.ToolExecutionPipeline;
import com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.SimpleStreamOptions;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Facade for configuring and running the phase-4 agent runtime.
 */
@Service
public class Agent {

    private static final Executor VIRTUAL_THREAD_EXECUTOR = command -> Thread.ofVirtual().start(command);

    private final CampusClawAiService piAiService;
    private final AgentState state;
    private final MessageConverter messageConverter;
    private final ContextTransformer contextTransformer;
    private final ToolExecutionPipeline toolPipeline;
    private final ToolExecutionMode toolExecutionMode;
    private final MessageQueue steeringQueue;
    private final MessageQueue followUpQueue;
    private final SimpleStreamOptions baseStreamOptions;
    private final CopyOnWriteArrayList<AgentEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Object executionLock = new Object();

    private volatile CompletableFuture<Void> currentExecution = CompletableFuture.completedFuture(null);
    private volatile CancellationToken currentSignal;

    @Autowired
    public Agent(CampusClawAiService piAiService) {
        this(
            piAiService,
            new AgentState(),
            new DefaultMessageConverter(),
            null,
            new ToolExecutionPipeline(),
            ToolExecutionMode.SEQUENTIAL,
            new MessageQueue(),
            new MessageQueue(),
            SimpleStreamOptions.empty()
        );
    }

    Agent(
        CampusClawAiService piAiService,
        AgentState state,
        MessageConverter messageConverter,
        ContextTransformer contextTransformer,
        ToolExecutionPipeline toolPipeline,
        ToolExecutionMode toolExecutionMode,
        MessageQueue steeringQueue,
        MessageQueue followUpQueue,
        SimpleStreamOptions baseStreamOptions
    ) {
        this.piAiService = Objects.requireNonNull(piAiService, "piAiService");
        this.state = Objects.requireNonNull(state, "state");
        this.messageConverter = messageConverter != null ? messageConverter : new DefaultMessageConverter();
        this.contextTransformer = contextTransformer;
        this.toolPipeline = toolPipeline != null ? toolPipeline : new ToolExecutionPipeline();
        this.toolExecutionMode = toolExecutionMode != null ? toolExecutionMode : ToolExecutionMode.SEQUENTIAL;
        this.steeringQueue = steeringQueue != null ? steeringQueue : new MessageQueue();
        this.followUpQueue = followUpQueue != null ? followUpQueue : new MessageQueue();
        this.baseStreamOptions = baseStreamOptions != null ? baseStreamOptions : SimpleStreamOptions.empty();
    }

    public AgentState getState() {
        return state;
    }

    public void setSystemPrompt(String prompt) {
        state.setSystemPrompt(prompt);
    }

    public void setModel(Model model) {
        state.setModel(model);
    }

    public void setThinkingLevel(ThinkingLevel level) {
        state.setThinkingLevel(level);
    }

    public void setTools(List<AgentTool> tools) {
        state.setTools(tools);
    }

    public void replaceMessages(List<Message> messages) {
        state.replaceMessages(messages);
    }

    public void appendMessage(Message message) {
        state.appendMessage(message);
    }

    public void clearMessages() {
        state.clearMessages();
    }

    public void reset() {
        state.setSystemPrompt(null);
        state.setModel(null);
        state.setThinkingLevel(ThinkingLevel.OFF);
        state.setTools(List.of());
        state.clearMessages();
        state.setStreaming(false);
        state.setStreamMessage(null);
        state.clearPendingToolCalls();
        state.setError(null);
        clearSteeringQueue();
        clearFollowUpQueue();
    }

    public void setBeforeToolCall(BeforeToolCallHandler handler) {
        toolPipeline.setBeforeToolCall(handler);
    }

    public void setAfterToolCall(AfterToolCallHandler handler) {
        toolPipeline.setAfterToolCall(handler);
    }

    public void steer(Message message) {
        steeringQueue.enqueue(message);
    }

    public void followUp(Message message) {
        followUpQueue.enqueue(message);
    }

    public void clearSteeringQueue() {
        steeringQueue.clear();
    }

    public void clearFollowUpQueue() {
        followUpQueue.clear();
    }

    public CompletableFuture<Void> prompt(String message) {
        Objects.requireNonNull(message, "message");
        return prompt(new UserMessage(message, System.currentTimeMillis()));
    }

    public CompletableFuture<Void> prompt(Message message) {
        Objects.requireNonNull(message, "message");
        return startExecution(List.of(message), false);
    }

    public CompletableFuture<Void> continueExecution() {
        return startExecution(List.of(), true);
    }

    public void abort() {
        var signal = currentSignal;
        if (signal != null) {
            signal.cancel();
        }
    }

    public CompletableFuture<Void> waitForIdle() {
        CompletableFuture<Void> execution;
        synchronized (executionLock) {
            execution = currentExecution;
        }
        return execution.handle((unused, throwable) -> null);
    }

    public Runnable subscribe(AgentEventListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private CompletableFuture<Void> startExecution(List<Message> messages, boolean continueOnly) {
        synchronized (executionLock) {
            if (!currentExecution.isDone()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Agent is already running"));
            }

            var model = state.getModel();
            if (model == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Model is not set"));
            }

            state.setError(null);
            var signal = new CancellationToken();
            currentSignal = signal;

            var context = new AgentContext(state);
            var loop = new AgentLoop(new AgentLoopConfig(
                piAiService,
                model,
                messageConverter,
                contextTransformer,
                toolPipeline,
                toolExecutionMode,
                steeringQueue,
                followUpQueue,
                buildStreamOptions()
            ));

            var execution = CompletableFuture.runAsync(() -> {
                if (continueOnly) {
                    loop.continueLoop(context, this::emit, signal);
                } else {
                    loop.run(messages, context, this::emit, signal);
                }
            }, VIRTUAL_THREAD_EXECUTOR).whenComplete((unused, throwable) -> {
                state.setStreaming(false);
                state.setStreamMessage(null);
                state.clearPendingToolCalls();
                synchronized (executionLock) {
                    currentSignal = null;
                }
                if (throwable != null) {
                    state.setError(formatError(throwable));
                }
            });

            currentExecution = execution;
            return execution;
        }
    }

    private SimpleStreamOptions buildStreamOptions() {
        return baseStreamOptions.toBuilder()
            .reasoning(state.getThinkingLevel())
            .build();
    }

    private void emit(AgentEvent event) {
        applyEventToState(event);
        for (var listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ignored) {
                // Listeners should not break the agent run.
            }
        }
    }

    private void applyEventToState(AgentEvent event) {
        switch (event) {
            case AgentStartEvent ignored -> {
                state.setStreaming(false);
                state.setStreamMessage(null);
                state.clearPendingToolCalls();
            }
            case AgentEndEvent e -> {
                state.setStreaming(false);
                state.setStreamMessage(null);
                state.replaceMessages(e.messages());
            }
            case MessageStartEvent e -> {
                if (e.message() instanceof AssistantMessage assistantMessage) {
                    state.setStreaming(true);
                    state.setStreamMessage(assistantMessage);
                }
            }
            case MessageUpdateEvent e -> {
                state.setStreaming(true);
                state.setStreamMessage(e.message());
            }
            case MessageEndEvent e -> {
                if (e.message() instanceof AssistantMessage) {
                    state.setStreaming(false);
                    state.setStreamMessage(null);
                }
            }
            case ToolExecutionStartEvent e -> state.addPendingToolCall(e.toolCallId());
            case ToolExecutionEndEvent e -> state.removePendingToolCall(e.toolCallId());
            default -> {
            }
        }
    }

    public static String formatError(Throwable throwable) {
        var current = throwable;
        // Unwrap standard wrapper exceptions
        while (current.getCause() != null
            && (current instanceof java.util.concurrent.CompletionException
            || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        // Build message including cause chain so the real error is visible
        // (e.g. "Request failed" from SDK wrapping an actual IOException)
        String message = current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
        var cause = current.getCause();
        if (cause != null && cause != current) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null && !causeMsg.isBlank() && !message.contains(causeMsg)) {
                message = message + ": " + causeMsg;
            }
            // One more level for deeply nested causes (e.g. SSLHandshakeException -> PKIX)
            var rootCause = cause.getCause();
            if (rootCause != null && rootCause != cause) {
                String rootMsg = rootCause.getMessage();
                if (rootMsg != null && !rootMsg.isBlank() && !message.contains(rootMsg)) {
                    message = message + ": " + rootMsg;
                }
            }
        }
        // Append proxy hint for connection failures
        if (isConnectionError(current)) {
            message += "\n  提示: 如需使用代理，请添加 --proxy http://127.0.0.1:<端口号> 或设置环境变量 HTTPS_PROXY";
        }
        return message;
    }

    private static boolean isConnectionError(Throwable t) {
        for (var c = t; c != null; c = c.getCause()) {
            if (c instanceof java.net.ConnectException
                || c instanceof java.net.SocketTimeoutException
                || (c.getMessage() != null && c.getMessage().contains("timed out"))) {
                return true;
            }
        }
        return false;
    }
}
