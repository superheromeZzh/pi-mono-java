package com.huawei.hicampus.mate.matecampusclaw.agent.queue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

/**
 * Thread-safe queue for steering and follow-up messages.
 */
public class MessageQueue {

    public enum DeliveryMode {
        ALL,
        ONE_AT_A_TIME
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayDeque<Message> messages = new ArrayDeque<>();
    private DeliveryMode mode = DeliveryMode.ALL;

    public void enqueue(Message message) {
        Objects.requireNonNull(message, "message");

        lock.lock();
        try {
            messages.addLast(message);
        } finally {
            lock.unlock();
        }
    }

    public List<Message> drain(DeliveryMode mode) {
        lock.lock();
        try {
            return drainLocked(resolveMode(mode));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drains messages using the currently configured delivery mode.
     */
    public List<Message> drain() {
        lock.lock();
        try {
            return drainLocked(mode);
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            messages.clear();
        } finally {
            lock.unlock();
        }
    }

    public boolean hasMessages() {
        lock.lock();
        try {
            return !messages.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public void setMode(DeliveryMode mode) {
        lock.lock();
        try {
            this.mode = Objects.requireNonNull(mode, "mode");
        } finally {
            lock.unlock();
        }
    }

    private DeliveryMode resolveMode(DeliveryMode mode) {
        return mode != null ? mode : this.mode;
    }

    private List<Message> drainLocked(DeliveryMode mode) {
        if (messages.isEmpty()) {
            return List.of();
        }

        return switch (mode) {
            case ALL -> {
                var drained = new ArrayList<Message>(messages);
                messages.clear();
                yield List.copyOf(drained);
            }
            case ONE_AT_A_TIME -> List.of(messages.removeFirst());
        };
    }
}
