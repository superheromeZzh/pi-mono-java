package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEventListener;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.ToolExecutionEndEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.ToolExecutionStartEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.event.ToolExecutionUpdateEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

/**
 * Executes tool calls with hook processing, validation, and event emission.
 */
public class ToolExecutionPipeline {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private volatile BeforeToolCallHandler beforeToolCallHandler;
    private volatile AfterToolCallHandler afterToolCallHandler;

    public void setBeforeToolCall(BeforeToolCallHandler handler) {
        this.beforeToolCallHandler = handler;
    }

    public void setAfterToolCall(AfterToolCallHandler handler) {
        this.afterToolCallHandler = handler;
    }

    public ToolResultMessage execute(
        AgentTool tool,
        ToolCall toolCall,
        Map<String, Object> validatedArgs,
        AgentContext context,
        CancellationToken signal,
        AgentEventListener listener
    ) {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(toolCall, "toolCall");
        Objects.requireNonNull(validatedArgs, "validatedArgs");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(signal, "signal");

        AgentEventListener eventListener = listener != null ? listener : event -> {
        };
        var toolName = toolCall.name();

        try {
            var beforeResult = runBeforeHook(toolCall, validatedArgs, context);
            if (beforeResult != null && beforeResult.block()) {
                return toToolResultMessage(
                    toolCall,
                    toolName,
                    errorResult(beforeResult.reason() != null
                        ? beforeResult.reason()
                        : "Tool call blocked by beforeToolCall handler"),
                    true
                );
            }
        } catch (Exception e) {
            return toToolResultMessage(toolCall, toolName, errorResult(messageForException(e)), true);
        }

        eventListener.onEvent(new ToolExecutionStartEvent(toolCall.id(), toolName, validatedArgs));

        AgentToolResult result;
        var isError = false;
        try {
            validateArguments(tool, validatedArgs);
            result = normalizeResult(tool.execute(
                toolCall.id(),
                validatedArgs,
                signal,
                partialResult -> eventListener.onEvent(new ToolExecutionUpdateEvent(
                    toolCall.id(),
                    toolName,
                    validatedArgs,
                    partialResult
                ))
            ));
        } catch (Exception e) {
            result = errorResult(messageForException(e));
            isError = true;
        }

        try {
            var afterResult = runAfterHook(toolCall, validatedArgs, context, result, isError);
            if (afterResult != null) {
                result = applyAfterResult(result, afterResult);
                if (afterResult.isError() != null) {
                    isError = afterResult.isError();
                }
            }
        } catch (Exception e) {
            result = errorResult(messageForException(e));
            isError = true;
        }

        eventListener.onEvent(new ToolExecutionEndEvent(toolCall.id(), toolName, result, isError));
        return toToolResultMessage(toolCall, toolName, result, isError);
    }

    public List<ToolResultMessage> executeAll(
        List<ToolCallWithTool> calls,
        ToolExecutionMode mode,
        AgentContext context,
        CancellationToken signal,
        AgentEventListener listener
    ) {
        Objects.requireNonNull(calls, "calls");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(signal, "signal");

        if (calls.isEmpty()) {
            return List.of();
        }

        return switch (mode != null ? mode : ToolExecutionMode.SEQUENTIAL) {
            case SEQUENTIAL -> executeSequentially(calls, context, signal, listener);
            case PARALLEL -> executeInParallel(calls, context, signal, listener);
        };
    }

    private List<ToolResultMessage> executeSequentially(
        List<ToolCallWithTool> calls,
        AgentContext context,
        CancellationToken signal,
        AgentEventListener listener
    ) {
        var results = new ArrayList<ToolResultMessage>(calls.size());
        for (var call : calls) {
            results.add(execute(call.tool(), call.toolCall(), call.validatedArgs(), context, signal, listener));
        }
        return List.copyOf(results);
    }

    private List<ToolResultMessage> executeInParallel(
        List<ToolCallWithTool> calls,
        AgentContext context,
        CancellationToken signal,
        AgentEventListener listener
    ) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<ToolResultMessage>>(calls.size());
            for (var call : calls) {
                futures.add(executor.submit(() ->
                    execute(call.tool(), call.toolCall(), call.validatedArgs(), context, signal, listener)
                ));
            }

            var results = new ArrayList<ToolResultMessage>(calls.size());
            for (var future : futures) {
                results.add(future.get());
            }
            return List.copyOf(results);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing tools in parallel", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unexpected failure while executing tools in parallel", e);
        }
    }

    private BeforeToolCallResult runBeforeHook(
        ToolCall toolCall,
        Map<String, Object> validatedArgs,
        AgentContext context
    ) throws Exception {
        var handler = beforeToolCallHandler;
        if (handler == null) {
            return null;
        }

        return handler.handle(new BeforeToolCallContext(
            context.assistantMessage(),
            toolCall,
            validatedArgs,
            context
        ));
    }

    private AfterToolCallResult runAfterHook(
        ToolCall toolCall,
        Map<String, Object> validatedArgs,
        AgentContext context,
        AgentToolResult result,
        boolean isError
    ) throws Exception {
        var handler = afterToolCallHandler;
        if (handler == null) {
            return null;
        }

        return handler.handle(new AfterToolCallContext(
            context.assistantMessage(),
            toolCall,
            validatedArgs,
            result,
            isError,
            context
        ));
    }

    private void validateArguments(AgentTool tool, Map<String, Object> validatedArgs) {
        var schema = schemaFactory.getSchema(tool.parameters());
        var errors = schema.validate(objectMapper.valueToTree(validatedArgs));
        if (!errors.isEmpty()) {
            var message = errors.stream()
                .map(Object::toString)
                .sorted()
                .reduce((left, right) -> left + "; " + right)
                .orElse("Unknown schema validation error");
            throw new IllegalArgumentException("Tool arguments failed validation: " + message);
        }
    }

    private AgentToolResult applyAfterResult(AgentToolResult baseResult, AfterToolCallResult afterResult) {
        var content = afterResult.content() != null ? List.copyOf(afterResult.content()) : baseResult.content();
        var details = afterResult.details() != null ? afterResult.details() : baseResult.details();
        return new AgentToolResult(content, details);
    }

    private AgentToolResult normalizeResult(AgentToolResult result) {
        if (result == null) {
            return new AgentToolResult(List.of(), null);
        }

        List<ContentBlock> content = result.content() != null ? List.copyOf(result.content()) : List.of();
        return new AgentToolResult(content, result.details());
    }

    private AgentToolResult errorResult(String message) {
        return new AgentToolResult(List.of(new TextContent(message)), null);
    }

    private ToolResultMessage toToolResultMessage(
        ToolCall toolCall,
        String toolName,
        AgentToolResult result,
        boolean isError
    ) {
        return new ToolResultMessage(
            toolCall.id(),
            toolName,
            result.content(),
            result.details(),
            isError,
            System.currentTimeMillis()
        );
    }

    private String messageForException(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
