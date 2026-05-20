/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoopManagerTest {

    private LoopManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Nested
    class BeforeInit {

        @Test
        void isInitializedFalse() {
            manager = new LoopManager();
            assertThat(manager.isInitialized()).isFalse();
        }

        @Test
        void startThrowsWhenNotInitialized() {
            manager = new LoopManager();
            assertThatThrownBy(() -> manager.start("p", 1000))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not initialized");
        }

        @Test
        void stopAndListWhenEmpty() {
            manager = new LoopManager();
            assertThat(manager.stop("missing")).isFalse();
            assertThat(manager.stopAll()).isEqualTo(0);
            assertThat(manager.list()).isEmpty();
        }

        @Test
        void submitMessageWithoutQueueReturnsFalse() {
            manager = new LoopManager();
            assertThat(manager.submitMessage("hi")).isFalse();
        }
    }

    @Nested
    class AfterInit {

        @Test
        void initSetsInitialized() {
            manager = new LoopManager();
            manager.init(new LinkedBlockingQueue<>(), new AtomicBoolean(false));
            assertThat(manager.isInitialized()).isTrue();
        }

        @Test
        void submitMessageWithQueueWorks() {
            manager = new LoopManager();
            var queue = new LinkedBlockingQueue<String>();
            manager.init(queue, new AtomicBoolean(false));
            assertThat(manager.submitMessage("hi")).isTrue();
            assertThat(queue).contains("hi");
        }

        @Test
        void startCreatesEntryAndScheduledExecution() throws InterruptedException {
            manager = new LoopManager();
            var queue = new LinkedBlockingQueue<String>();
            manager.init(queue, new AtomicBoolean(false));
            String id = manager.start("prompt-x", 50);
            assertThat(manager.list()).hasSize(1);

            // Wait briefly for one tick
            String submitted = queue.poll(2, TimeUnit.SECONDS);
            assertThat(submitted).isEqualTo("prompt-x");
            assertThat(manager.stop(id)).isTrue();
            assertThat(manager.list()).isEmpty();
        }

        @Test
        void busyAgentSkipsSubmission() throws InterruptedException {
            manager = new LoopManager();
            var queue = new LinkedBlockingQueue<String>();
            var busy = new AtomicBoolean(true);
            manager.init(queue, busy);
            manager.start("skipped", 50);

            // Give time for several ticks while busy
            String submitted = queue.poll(300, TimeUnit.MILLISECONDS);
            assertThat(submitted).isNull();
        }

        @Test
        void stopAllCancelsEverything() {
            manager = new LoopManager();
            manager.init(new LinkedBlockingQueue<>(), new AtomicBoolean(false));
            manager.start("a", 1_000_000);
            manager.start("b", 1_000_000);
            assertThat(manager.list()).hasSize(2);
            int count = manager.stopAll();
            assertThat(count).isEqualTo(2);
            assertThat(manager.list()).isEmpty();
        }

        @Test
        void shutdownClearsScheduler() {
            manager = new LoopManager();
            manager.init(new LinkedBlockingQueue<>(), new AtomicBoolean(false));
            manager.start("p", 1_000_000);
            manager.shutdown();
            assertThat(manager.isInitialized()).isFalse();
            manager = null; // tearDown shouldn't double-shutdown
        }
    }
}
