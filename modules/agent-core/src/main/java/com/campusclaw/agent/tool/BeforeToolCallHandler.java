package com.campusclaw.agent.tool;

/**
 * Hook invoked before a tool call is executed.
 */
@FunctionalInterface
public interface BeforeToolCallHandler {

    BeforeToolCallResult handle(BeforeToolCallContext context) throws Exception;
}
