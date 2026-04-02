package com.huawei.hicampus.mate.matecampusclaw.ai.stream;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

class EventStreamTest {

    // --- Basic Push and Subscribe ---

    @Nested
    class BasicPushAndSubscribe {

        @Test
        void pushSingleEvent() {
            var stream = new EventStream<String, String>(e -> false, e -> e);
            stream.push("hello");
            stream.end();

            StepVerifier.create(stream.asFlux())
                .expectNext("hello")
                .verifyComplete();
        }

        @Test
        void pushMultipleEventsInOrder() {
            var stream = new EventStream<String, String>(e -> false, e -> e);
            stream.push("a");
            stream.push("b");
            stream.push("c");
            stream.end();

            StepVerifier.create(stream.asFlux())
                .expectNext("a", "b", "c")
                .verifyComplete();
        }

        @Test
        void pushBeforeSubscribeBuffersEvents() {
            var stream = new EventStream<Integer, Integer>(e -> false, e -> e);
            // Push events before any subscriber
            stream.push(1);
            stream.push(2);
            stream.push(3);
            stream.end();

            // Subscribe after all events pushed — should still get them
            StepVerifier.create(stream.asFlux())
                .expectNext(1, 2, 3)
                .verifyComplete();
        }

        @Test
        void fluxCompletesOnEnd() {
            var stream = new EventStream<String, String>(e -> false, e -> e);
            stream.end();

            StepVerifier.create(stream.asFlux())
                .verifyComplete();
        }
    }

    // --- Result Handling ---

    @Nested
    class ResultHandling {

        @Test
        void endWithExplicitResult() {
            var stream = new EventStream<String, String>(e -> false, e -> e);
            stream.push("event");
            stream.end("final-result");

            StepVerifier.create(stream.result())
                .expectNext("final-result")
                .verifyComplete();
        }

        @Test
        void resultViaIsComplete() {
            var stream = new EventStream<String, String>(
                e -> e.equals("DONE"),
                e -> "extracted:" + e
            );
            stream.push("a");
            stream.push("DONE");

            StepVerifier.create(stream.result())
                .expectNext("extracted:DONE")
                .verifyComplete();
        }

        @Test
        void endWithoutResultCompletesEmpty() {
            var stream = new EventStream<String, String>(e -> false, e -> e);
            stream.push("a");
            stream.end();

            StepVerifier.create(stream.result())
                .verifyComplete();
        }

        @Test
        void resultAvailableIndependentlyOfFlux() {
            // Result resolves as soon as isComplete fires, even if Flux not consumed
            var stream = new EventStream<String, String>(
                e -> e.equals("DONE"),
                e -> "result"
            );
            stream.push("DONE");

            // Check result without consuming Flux
            StepVerifier.create(stream.result())
                .expectNext("result")
                .verifyComplete();
        }
    }

    // --- IsComplete Auto-End ---

    @Nested
    class IsCompleteAutoEnd {

        @Test
        void completionEventIsDelivered() {
            var stream = new EventStream<String, String>(
                e -> e.equals("END"),
                e -> e
            );
            stream.push("a");
            stream.push("b");
            stream.push("END");

            StepVerifier.create(stream.asFlux())
                .expectNext("a", "b", "END")
                .verifyComplete();
        }

        @Test
        void fluxCompletesAfterIsComplete() {
            var stream = new EventStream<String, String>(
                e -> e.startsWith("done:"),
                e -> e.substring(5)
            );
            stream.push("event1");
            stream.push("done:result");

            // Flux should emit both events then complete
            StepVerifier.create(stream.asFlux())
                .expectNext("event1", "done:result")
                .verifyComplete();

            // Result should be extracted
            StepVerifier.create(stream.result())
                .expectNext("result")
                .verifyComplete();
        }

        @Test
        void pushAfterAutoCompleteIgnored() {
            var stream = new EventStream<String, String>(
                e -> e.equals("END"),
                e -> e
            );
            stream.push("a");
            stream.push("END");
            stream.push("should-be-ignored");

            StepVerifier.create(stream.asFlux())
                .expectNext("a", "END")
                .verifyComplete();
        }
    }

    // --- Error Propagation ---

    @Nested
    class ErrorPropagation {

        @Test
        void errorPropagatesToFlux() {
            var stream = new EventStream<String, String>(e -> false, e -> e);
            stream.push("a");
            stream.error(new RuntimeException("boom"));

            StepVerifier.create(stream.asFlux())
                .expectNext("a")
                .expectErrorMessage("boom")
                .verify();
        }

        @Test
        void errorPropagatesToResult() {
            var stream = new EventStream<String, String>(e -> false, e -> e);
            stream.error(new RuntimeException("boom"));

            StepVerifier.create(stream.result())
                .expectErrorMessage("boom")
                .verify();
        }

        @Test
        void pushAfterErrorIgnored() {
            var stream = new EventStream<String, String>(e -> false, e -> e);
            stream.error(new RuntimeException("boom"));
            stream.push("ignored");

            StepVerifier.create(stream.asFlux())
                .expectErrorMessage("boom")
                .verify();
        }

        @Test
        void endAfterErrorIgnored() {
            var stream = new EventStream<String, String>(e -> false, e -> e);
            stream.error(new RuntimeException("boom"));
            stream.end("ignored");

            // Result should still be the error, not the end value
            StepVerifier.create(stream.result())
                .expectErrorMessage("boom")
                .verify();
        }
    }

    // --- Edge Cases ---

    @Nested
    class EdgeCases {

        @Test
        void pushAfterEndIgnored() {
            var stream = new EventStream<String, String>(e -> false, e -> e);
            stream.push("a");
            stream.end();
            stream.push("should-not-appear");

            StepVerifier.create(stream.asFlux())
                .expectNext("a")
                .verifyComplete();
        }

        @Test
        void doubleEndIgnored() {
            var stream = new EventStream<String, String>(e -> false, e -> e);
            stream.push("a");
            stream.end("result1");
            stream.end("result2");

            StepVerifier.create(stream.result())
                .expectNext("result1")
                .verifyComplete();

            StepVerifier.create(stream.asFlux())
                .expectNext("a")
                .verifyComplete();
        }

        @Test
        void endWithResultAfterIsCompleteIgnored() {
            var stream = new EventStream<String, String>(
                e -> e.equals("END"),
                e -> "auto-result"
            );
            stream.push("END");
            stream.end("explicit-result");

            // Auto-detected result should win
            StepVerifier.create(stream.result())
                .expectNext("auto-result")
                .verifyComplete();
        }

        @Test
        void errorAfterEndIgnored() {
            var stream = new EventStream<String, String>(e -> false, e -> e);
            stream.end("result");
            stream.error(new RuntimeException("should-not-propagate"));

            StepVerifier.create(stream.result())
                .expectNext("result")
                .verifyComplete();
        }

        @Test
        void emptyStreamEndImmediately() {
            var stream = new EventStream<String, String>(e -> false, e -> e);
            stream.end();

            StepVerifier.create(stream.asFlux())
                .verifyComplete();

            StepVerifier.create(stream.result())
                .verifyComplete();
        }
    }

    // --- Concurrent Push ---

    @Nested
    class ConcurrentPush {

        @Test
        void concurrentPushAllEventsDelivered() throws InterruptedException {
            var stream = new EventStream<Integer, Integer>(e -> false, e -> e);
            int threadCount = 10;
            int eventsPerThread = 100;
            var latch = new CountDownLatch(threadCount);

            try (var executor = Executors.newFixedThreadPool(threadCount)) {
                for (int t = 0; t < threadCount; t++) {
                    final int threadId = t;
                    executor.submit(() -> {
                        for (int i = 0; i < eventsPerThread; i++) {
                            stream.push(threadId * eventsPerThread + i);
                        }
                        latch.countDown();
                    });
                }
                latch.await();
            }
            stream.end();

            StepVerifier.create(stream.asFlux().count())
                .expectNext((long) threadCount * eventsPerThread)
                .verifyComplete();
        }

        @Test
        void concurrentPushWithIsComplete() throws InterruptedException {
            // Use a sentinel value to trigger isComplete
            int sentinel = -1;
            var stream = new EventStream<Integer, Integer>(
                e -> e == sentinel,
                e -> e
            );

            int threadCount = 5;
            int eventsPerThread = 50;
            var latch = new CountDownLatch(threadCount);

            try (var executor = Executors.newFixedThreadPool(threadCount)) {
                for (int t = 0; t < threadCount; t++) {
                    final int threadId = t;
                    executor.submit(() -> {
                        for (int i = 0; i < eventsPerThread; i++) {
                            stream.push(threadId * eventsPerThread + i);
                        }
                        latch.countDown();
                    });
                }
                latch.await();
            }

            // Now push the sentinel
            stream.push(sentinel);

            // Result should be the sentinel
            StepVerifier.create(stream.result())
                .expectNext(sentinel)
                .verifyComplete();

            // Flux should complete (all events + sentinel)
            StepVerifier.create(stream.asFlux().count())
                .assertNext(count -> assertTrue(count > 0 && count <= (long) threadCount * eventsPerThread + 1))
                .verifyComplete();
        }
    }
}
