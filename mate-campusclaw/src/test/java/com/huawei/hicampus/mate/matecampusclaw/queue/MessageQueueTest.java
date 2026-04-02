package com.huawei.hicampus.mate.matecampusclaw.agent.queue;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;

import org.junit.jupiter.api.Test;

class MessageQueueTest {

    @Test
    void allModeDrainsEverythingAtOnce() {
        var queue = new MessageQueue();
        var first = new UserMessage("first", 1L);
        var second = new UserMessage("second", 2L);

        queue.enqueue(first);
        queue.enqueue(second);

        var drained = queue.drain(MessageQueue.DeliveryMode.ALL);

        assertEquals(List.of(first, second), drained);
        assertFalse(queue.hasMessages());
        assertTrue(queue.drain(MessageQueue.DeliveryMode.ALL).isEmpty());
    }

    @Test
    void oneAtATimeModeDrainsMessagesIndividually() {
        var queue = new MessageQueue();
        var first = new UserMessage("first", 1L);
        var second = new UserMessage("second", 2L);
        var third = new UserMessage("third", 3L);

        queue.enqueue(first);
        queue.enqueue(second);
        queue.enqueue(third);

        assertEquals(List.of(first), queue.drain(MessageQueue.DeliveryMode.ONE_AT_A_TIME));
        assertTrue(queue.hasMessages());
        assertEquals(List.of(second), queue.drain(MessageQueue.DeliveryMode.ONE_AT_A_TIME));
        assertTrue(queue.hasMessages());
        assertEquals(List.of(third), queue.drain(MessageQueue.DeliveryMode.ONE_AT_A_TIME));
        assertFalse(queue.hasMessages());
        assertTrue(queue.drain(MessageQueue.DeliveryMode.ONE_AT_A_TIME).isEmpty());
    }

    @Test
    void configuredModeCanBeUsedForSubsequentDrains() {
        var queue = new MessageQueue();
        var first = new UserMessage("first", 1L);
        var second = new UserMessage("second", 2L);

        queue.setMode(MessageQueue.DeliveryMode.ONE_AT_A_TIME);
        queue.enqueue(first);
        queue.enqueue(second);

        assertEquals(List.of(first), queue.drain());
        assertEquals(List.of(second), queue.drain());
        assertTrue(queue.drain().isEmpty());
    }

    @Test
    void clearRemovesAllQueuedMessages() {
        var queue = new MessageQueue();

        queue.enqueue(new UserMessage("first", 1L));
        queue.enqueue(new UserMessage("second", 2L));
        queue.clear();

        assertFalse(queue.hasMessages());
        assertTrue(queue.drain(MessageQueue.DeliveryMode.ALL).isEmpty());
    }

    @Test
    void supportsConcurrentEnqueueFromExternalThreads() throws Exception {
        var queue = new MessageQueue();
        var taskCount = 100;
        var start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var i = 0; i < taskCount; i++) {
                final var index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    queue.enqueue(new UserMessage("message-" + index, index));
                    return null;
                }));
            }

            start.countDown();

            for (var future : futures) {
                future.get();
            }
        }

        assertTrue(queue.hasMessages());

        var drained = queue.drain(MessageQueue.DeliveryMode.ALL);
        var texts = drained.stream()
            .map(MessageQueueTest::messageText)
            .collect(Collectors.toSet());

        assertEquals(taskCount, drained.size());
        assertEquals(taskCount, texts.size());
        assertTrue(texts.contains("message-0"));
        assertTrue(texts.contains("message-99"));
        assertFalse(queue.hasMessages());
    }

    private static String messageText(Message message) {
        return ((TextContent) ((UserMessage) message).content().getFirst()).text();
    }
}
