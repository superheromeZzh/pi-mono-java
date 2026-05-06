/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.ops;

/**
 * Combined read and write operations for tools that need both (e.g. file editing).
 */
public interface EditOperations extends ReadOperations, WriteOperations {
}
