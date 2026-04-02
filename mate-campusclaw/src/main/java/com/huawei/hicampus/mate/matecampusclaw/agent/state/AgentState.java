package com.huawei.hicampus.mate.matecampusclaw.agent.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel;

/**
 * Thread-safe mutable container for agent runtime state.
 */
public class AgentState {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private String systemPrompt;
    private Model model;
    private ThinkingLevel thinkingLevel;
    private List<AgentTool> tools;
    private List<Message> messages;
    private volatile boolean streaming;
    private volatile Message streamMessage;
    private final Set<String> pendingToolCalls;
    private String error;

    public AgentState() {
        this.thinkingLevel = ThinkingLevel.MEDIUM;
        this.tools = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.pendingToolCalls = ConcurrentHashMap.newKeySet();
    }

    public String getSystemPrompt() {
        lock.readLock().lock();
        try {
            return systemPrompt;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setSystemPrompt(String systemPrompt) {
        lock.writeLock().lock();
        try {
            this.systemPrompt = systemPrompt;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Model getModel() {
        lock.readLock().lock();
        try {
            return model;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setModel(Model model) {
        lock.writeLock().lock();
        try {
            this.model = model;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public ThinkingLevel getThinkingLevel() {
        lock.readLock().lock();
        try {
            return thinkingLevel;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setThinkingLevel(ThinkingLevel thinkingLevel) {
        lock.writeLock().lock();
        try {
            this.thinkingLevel = thinkingLevel;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<AgentTool> getTools() {
        lock.readLock().lock();
        try {
            return List.copyOf(tools);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setTools(List<AgentTool> tools) {
        lock.writeLock().lock();
        try {
            this.tools = new ArrayList<>(tools);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Message> getMessages() {
        lock.readLock().lock();
        try {
            return List.copyOf(messages);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setMessages(List<Message> messages) {
        lock.writeLock().lock();
        try {
            this.messages = new ArrayList<>(messages);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void replaceMessages(List<Message> messages) {
        setMessages(messages);
    }

    public void appendMessage(Message message) {
        lock.writeLock().lock();
        try {
            messages.add(message);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearMessages() {
        lock.writeLock().lock();
        try {
            messages.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isStreaming() {
        lock.readLock().lock();
        try {
            return streaming;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setStreaming(boolean streaming) {
        lock.writeLock().lock();
        try {
            this.streaming = streaming;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Message getStreamMessage() {
        lock.readLock().lock();
        try {
            return streamMessage;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setStreamMessage(Message streamMessage) {
        lock.writeLock().lock();
        try {
            this.streamMessage = streamMessage;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Set<String> getPendingToolCalls() {
        lock.readLock().lock();
        try {
            return Set.copyOf(pendingToolCalls);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setPendingToolCalls(Set<String> pendingToolCalls) {
        lock.writeLock().lock();
        try {
            this.pendingToolCalls.clear();
            this.pendingToolCalls.addAll(pendingToolCalls);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean addPendingToolCall(String toolCallId) {
        lock.writeLock().lock();
        try {
            return pendingToolCalls.add(toolCallId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean removePendingToolCall(String toolCallId) {
        lock.writeLock().lock();
        try {
            return pendingToolCalls.remove(toolCallId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean hasPendingToolCall(String toolCallId) {
        lock.readLock().lock();
        try {
            return pendingToolCalls.contains(toolCallId);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clearPendingToolCalls() {
        lock.writeLock().lock();
        try {
            pendingToolCalls.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String getError() {
        lock.readLock().lock();
        try {
            return error;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setError(String error) {
        lock.writeLock().lock();
        try {
            this.error = error;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public AgentStateSnapshot snapshot() {
        lock.readLock().lock();
        try {
            return new AgentStateSnapshot(
                systemPrompt,
                model,
                thinkingLevel,
                tools,
                messages,
                streaming,
                streamMessage,
                pendingToolCalls,
                error
            );
        } finally {
            lock.readLock().unlock();
        }
    }
}
