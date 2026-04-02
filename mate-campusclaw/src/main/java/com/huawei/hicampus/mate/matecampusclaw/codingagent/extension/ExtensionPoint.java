package com.huawei.hicampus.mate.matecampusclaw.codingagent.extension;

/**
 * Extension points where external extensions can hook into the coding agent.
 */
public enum ExtensionPoint {

    /** Register additional agent tools. */
    TOOL,

    /** Register additional slash commands. */
    COMMAND,

    /** Register before-tool-call hooks. */
    BEFORE_TOOL_CALL,

    /** Register after-tool-call hooks. */
    AFTER_TOOL_CALL,

    /** Register context transformers. */
    CONTEXT_TRANSFORMER,

    /** Register event listeners. */
    EVENT_LISTENER
}
