/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.tui.ansi;

/**
 * Represents a segment of text that is either an ANSI escape sequence or visible text.
 *
 * @param text   the text content of this segment
 * @param isAnsi true if this segment is an ANSI escape sequence, false if visible text
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record AnsiSegment(String text, boolean isAnsi) {}
