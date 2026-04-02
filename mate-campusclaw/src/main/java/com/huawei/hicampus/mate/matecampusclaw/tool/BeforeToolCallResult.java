package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

/**
 * Result returned from the before-tool-call hook.
 */
public record BeforeToolCallResult(
    boolean block,
    String reason
) {

    public static BeforeToolCallResult allow() {
        return new BeforeToolCallResult(false, null);
    }

    public static BeforeToolCallResult block(String reason) {
        return new BeforeToolCallResult(true, reason);
    }
}
