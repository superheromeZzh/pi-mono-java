/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.keybinding;

/**
 * Single key-to-action binding entry. Pairs the action id (e.g. {@code app.model.cycleForward}),
 * the configured key combo (e.g. {@code ctrl+p}), and a human-readable description used when
 * rendering the hotkeys help screen.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record KeyBinding(String action, String key, String description) {}
