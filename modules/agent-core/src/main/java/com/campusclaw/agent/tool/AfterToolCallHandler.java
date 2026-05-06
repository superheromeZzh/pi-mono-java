/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.tool;

/**
 * Hook invoked after tool execution completes.
 */
@FunctionalInterface
public interface AfterToolCallHandler {

    AfterToolCallResult handle(AfterToolCallContext context) throws Exception;
}
