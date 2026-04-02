package com.huawei.hicampus.mate.matecampusclaw.agent.tool;

import java.util.List;
import java.util.Objects;

import com.huawei.hicampus.mate.matecampusclaw.agent.state.AgentState;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

/**
 * Mutable agent context shared across loop execution and tool hooks.
 */
public class AgentContext {

    private final AgentState state;
    private volatile AssistantMessage assistantMessage;

    public AgentContext() {
        this(new AgentState(), null);
    }

    public AgentContext(AgentState state) {
        this(state, null);
    }

    public AgentContext(AgentState state, AssistantMessage assistantMessage) {
        this.state = Objects.requireNonNull(state, "state");
        this.assistantMessage = assistantMessage;
    }

    public AgentContext(String systemPrompt, List<AgentTool> tools, List<Message> messages) {
        this(systemPrompt, tools, messages, null);
    }

    public AgentContext(
        String systemPrompt,
        List<AgentTool> tools,
        List<Message> messages,
        AssistantMessage assistantMessage
    ) {
        this.state = new AgentState();
        this.state.setSystemPrompt(systemPrompt);
        this.state.setTools(tools);
        this.state.setMessages(messages);
        this.assistantMessage = assistantMessage;
    }

    public AgentContext(AssistantMessage assistantMessage, List<Message> messages) {
        this(null, List.of(), messages, assistantMessage);
    }

    public AgentState state() {
        return state;
    }

    public String systemPrompt() {
        return state.getSystemPrompt();
    }

    public void setSystemPrompt(String systemPrompt) {
        state.setSystemPrompt(systemPrompt);
    }

    public List<AgentTool> tools() {
        return state.getTools();
    }

    public void setTools(List<AgentTool> tools) {
        state.setTools(tools);
    }

    public List<Message> messages() {
        return state.getMessages();
    }

    public void replaceMessages(List<Message> messages) {
        state.replaceMessages(messages);
    }

    public void appendMessage(Message message) {
        state.appendMessage(message);
    }

    public void appendMessages(List<? extends Message> messages) {
        for (var message : messages) {
            state.appendMessage(message);
        }
    }

    public void clearMessages() {
        state.clearMessages();
    }

    public AssistantMessage assistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(AssistantMessage assistantMessage) {
        this.assistantMessage = assistantMessage;
    }
}
