package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

/**
 * Hook invoked after tool execution completes.
 */
@FunctionalInterface
public interface AfterToolCallHandler {

    AfterToolCallResult handle(AfterToolCallContext context) throws Exception;
}
