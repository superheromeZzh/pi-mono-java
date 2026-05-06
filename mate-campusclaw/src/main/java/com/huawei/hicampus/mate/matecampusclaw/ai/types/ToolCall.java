/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

/**
 * A tool/function call content block.
 *
 * @param id               unique identifier for this tool call
 * @param name             the tool/function name to invoke
 * @param arguments        the arguments to pass to the tool
 * @param thoughtSignature optional signature for thought metadata (provider-specific)
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record ToolCall(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("arguments") Map<String, Object> arguments,
        @JsonProperty("thoughtSignature") @Nullable String thoughtSignature)
        implements ContentBlock {

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public ToolCall(String id, String name, Map<String, Object> arguments) {
        this(id, name, arguments, null);
    }
}
