package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;

/**
 * Optional overrides returned from the after-tool-call hook.
 */
public record AfterToolCallResult(
    List<ContentBlock> content,
    Object details,
    Boolean isError
) {

    public static AfterToolCallResult noOverride() {
        return new AfterToolCallResult(null, null, null);
    }
}
