package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FileMutationQueueTest {

    private final FileMutationQueue queue = new FileMutationQueue();

    @Nested
    class BasicBehavior {

        @Test
        void returnsActionResult() throws Exception {
            String result = queue.withLock(Path.of("/tmp/test.txt"), () -> "hello");
            assertEquals("hello", result);
        }

        @Test
        void propagatesException() {
            assertThrows(RuntimeException.class, () ->
                    queue.withLock(Path.of("/tmp/test.txt"), () -> {
                        throw new RuntimeException("boom");
                    }));
        }

        @Test
        void releasesLockAfterException() throws Exception {
            // First call throws
            assertThrows(RuntimeException.class, () ->
                    queue.withLock(Path.of("/tmp/test.txt"), () -> {
                        throw new RuntimeException("fail");
                    }));

            // Second call should still succeed (lock was released)
            String result = queue.withLock(Path.of("/tmp/test.txt"), () -> "ok");
            assertEquals("ok", result);
        }

        @Test
        void normalizesDifferentRepresentationsOfSamePath() throws Exception {
            // These should all resolve to the same lock
            var entered = new AtomicBoolean(false);
            var barrier = new CyclicBarrier(2);
            var latch = new CountDownLatch(1);

            Path path1 = Path.of("/tmp/a/../b/file.txt");    // normalizes to /tmp/b/file.txt
            Path path2 = Path.of("/tmp/./b/file.txt");        // normalizes to /tmp/b/file.txt

            Thread t1 = new Thread(() -> {
                try {
                    queue.withLock(path1, () -> {
                        entered.set(true);
                        barrier.await(5, TimeUnit.SECONDS);
                        // Hold the lock until latch is counted down
                        latch.await(5, TimeUnit.SECONDS);
                        return null;
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            t1.start();

            // Wait until t1 has acquired the lock
            barrier.await(5, TimeUnit.SECONDS);
            assertTrue(entered.get());

            // Try to acquire lock on the same normalized path from main thread
            var t2Started = new AtomicBoolean(false);
            var t2Completed = new AtomicBoolean(false);
            Thread t2 = new Thread(() -> {
                try {
                    t2Started.set(true);
                    queue.withLock(path2, () -> {
                        t2Completed.set(true);
                        return null;
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            t2.start();

            // Give t2 time to start and block on the lock
            Thread.sleep(200);
            assertTrue(t2Started.get());
            assertFalse(t2Completed.get(), "t2 should be blocked waiting for the lock");

            // Release t1
            latch.countDown();
            t1.join(5000);
            t2.join(5000);
            assertTrue(t2Completed.get());
        }
    }

    @Nested
    class SameFileSerialization {

        @Test
        void sameFileOperationsAreSerial() throws Exception {
            Path file = Path.of("/tmp/serial-test.txt");
            int taskCount = 10;
            var executionOrder = Collections.synchronizedList(new ArrayList<Integer>());
            var concurrentCount = new AtomicInteger(0);
            var maxConcurrent = new AtomicInteger(0);
            var barrier = new CyclicBarrier(taskCount);

            ExecutorService executor = Executors.newFixedThreadPool(taskCount);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await(5, TimeUnit.SECONDS);  // all threads start ~simultaneously
                        queue.withLock(file, () -> {
                            int current = concurrentCount.incrementAndGet();
                            maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
                            executionOrder.add(taskId);
                            Thread.sleep(20);  // simulate work
                            concurrentCount.decrementAndGet();
                            return null;
                        });
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                }));
            }

            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
            executor.shutdown();

            // All tasks executed
            assertEquals(taskCount, executionOrder.size());
            // Max concurrent should be 1 (serialized)
            assertEquals(1, maxConcurrent.get(), "Same-file operations must be serialized");
        }
    }

    @Nested
    class DifferentFileParallelism {

        @Test
        void differentFilesRunConcurrently() throws Exception {
            int fileCount = 4;
            var insideLockLatch = new CountDownLatch(fileCount);
            var allCanProceed = new CountDownLatch(1);
            var concurrentCount = new AtomicInteger(0);
            var maxConcurrent = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(fileCount);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < fileCount; i++) {
                Path file = Path.of("/tmp/parallel-" + i + ".txt");
                futures.add(executor.submit(() -> {
                    try {
                        queue.withLock(file, () -> {
                            int current = concurrentCount.incrementAndGet();
                            maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
                            insideLockLatch.countDown();
                            // Wait until all threads are inside their locks
                            allCanProceed.await(5, TimeUnit.SECONDS);
                            concurrentCount.decrementAndGet();
                            return null;
                        });
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                }));
            }

            // Wait for all threads to be inside their respective locks
            assertTrue(insideLockLatch.await(5, TimeUnit.SECONDS),
                    "All threads should enter their locks concurrently");

            // All threads were inside locks at the same time
            assertEquals(fileCount, maxConcurrent.get(),
                    "Different-file operations should run concurrently");

            allCanProceed.countDown();

            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
            executor.shutdown();
        }
    }
}
