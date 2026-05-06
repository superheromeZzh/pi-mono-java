/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

import java.util.List;
import java.util.Objects;

import com.huawei.hicampus.mate.matecampusclaw.agent.state.AgentState;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

/**
 * Mutable agent context shared across loop execution and tool hooks.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class AgentContext {

    private final AgentState state;
    private volatile AssistantMessage assistantMessage;

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public AgentContext() {
        this(new AgentState(), null);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public AgentContext(AgentState state) {
        this(state, null);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public AgentContext(AgentState state, AssistantMessage assistantMessage) {
        this.state = Objects.requireNonNull(state, "state");
        this.assistantMessage = assistantMessage;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public AgentContext(String systemPrompt, List<AgentTool> tools, List<Message> messages) {
        this(systemPrompt, tools, messages, null);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public AgentContext(
            String systemPrompt, List<AgentTool> tools, List<Message> messages, AssistantMessage assistantMessage) {
        this.state = new AgentState();
        this.state.setSystemPrompt(systemPrompt);
        this.state.setTools(tools);
        this.state.setMessages(messages);
        this.assistantMessage = assistantMessage;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public AgentContext(AssistantMessage assistantMessage, List<Message> messages) {
        this(null, List.of(), messages, assistantMessage);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public AgentState state() {
        return state;
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public String systemPrompt() {
        return state.getSystemPrompt();
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void setSystemPrompt(String systemPrompt) {
        state.setSystemPrompt(systemPrompt);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public List<AgentTool> tools() {
        return state.getTools();
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void setTools(List<AgentTool> tools) {
        state.setTools(tools);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public List<Message> messages() {
        return state.getMessages();
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void replaceMessages(List<Message> messages) {
        state.replaceMessages(messages);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void appendMessage(Message message) {
        state.appendMessage(message);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void appendMessages(List<? extends Message> messages) {
        for (var message : messages) {
            state.appendMessage(message);
        }
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public void clearMessages() {
        state.clearMessages();
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public AssistantMessage assistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(AssistantMessage assistantMessage) {
        this.assistantMessage = assistantMessage;
    }
}
