/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.extension;

import java.util.List;

import com.campusclaw.agent.event.AgentEventListener;
import com.campusclaw.agent.tool.AfterToolCallHandler;
import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.agent.tool.BeforeToolCallHandler;
import com.campusclaw.codingagent.command.SlashCommand;

/**
 * An extension that can contribute tools, commands, and hooks to the coding agent.
 *
 * <p>Extensions are discovered by the {@link ExtensionRegistry} from configured
 * packages (npm/git/local directories). Each extension declares what it provides
 * via the getter methods below.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface Extension {

    /**
     * Unique identifier for this extension.
     *
     * @return the result
     */
    String id();

    /**
     * Human-readable name.
     *
     * @return the result
     */
    String name();

    /**
     * Additional tools provided by this extension.
     *
     * @return the result
     */
    default List<AgentTool> tools() {
        return List.of();
    }

    /**
     * Additional slash commands provided by this extension.
     *
     * @return the result
     */
    default List<SlashCommand> commands() {
        return List.of();
    }

    /**
     * Before-tool-call hooks.
     *
     * @return the result
     */
    default List<BeforeToolCallHandler> beforeToolCallHandlers() {
        return List.of();
    }

    /**
     * After-tool-call hooks.
     *
     * @return the result
     */
    default List<AfterToolCallHandler> afterToolCallHandlers() {
        return List.of();
    }

    /**
     * Event listeners.
     *
     * @return the result
     */
    default List<AgentEventListener> eventListeners() {
        return List.of();
    }

    /** Called when the extension is loaded. */
    default void onLoad() {}

    /** Called when the extension is unloaded. */
    default void onUnload() {}
}
