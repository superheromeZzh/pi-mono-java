package com.huawei.hicampus.mate.matecampusclaw.ai.stream;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.*;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

class AssistantMessageEventStreamTest {

    /** Creates a minimal AssistantMessage for use in tests. */
    private AssistantMessage sampleMessage(StopReason reason) {
        return new AssistantMessage(
            List.of(new TextContent("hello")),
            "anthropic-messages",
            "anthropic",
            "claude-opus-4-6",
            null,
            Usage.empty(),
            reason,
            null,
            System.currentTimeMillis()
        );
    }

    private AssistantMessage samplePartial() {
        return sampleMessage(StopReason.STOP);
    }

    // --- Full Streaming Sequence ---

    @Nested
    class FullStreamingSequence {

        @Test
        void startThenTextDeltasThenDone() {
            var stream = new AssistantMessageEventStream();
            var partial = samplePartial();
            var finalMsg = sampleMessage(StopReason.STOP);

            stream.push(new StartEvent(partial));
            stream.push(new TextStartEvent(0, partial));
            stream.pushTextDelta(0, "Hello", partial);
            stream.pushTextDelta(0, " world", partial);
            stream.push(new TextEndEvent(0, "Hello world", partial));
            stream.pushDone(StopReason.STOP, finalMsg);

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> assertInstanceOf(StartEvent.class, e))
                .assertNext(e -> assertInstanceOf(TextStartEvent.class, e))
                .assertNext(e -> {
                    var delta = assertInstanceOf(TextDeltaEvent.class, e);
                    assertEquals("Hello", delta.delta());
                    assertEquals(0, delta.contentIndex());
                })
                .assertNext(e -> {
                    var delta = assertInstanceOf(TextDeltaEvent.class, e);
                    assertEquals(" world", delta.delta());
                })
                .assertNext(e -> {
                    var end = assertInstanceOf(TextEndEvent.class, e);
                    assertEquals("Hello world", end.content());
                })
                .assertNext(e -> {
                    var done = assertInstanceOf(DoneEvent.class, e);
                    assertEquals(StopReason.STOP, done.reason());
                })
                .verifyComplete();

            StepVerifier.create(stream.result())
                .expectNext(finalMsg)
                .verifyComplete();
        }

        @Test
        void startThenErrorCompletes() {
            var stream = new AssistantMessageEventStream();
            var partial = samplePartial();
            var errorMsg = sampleMessage(StopReason.ERROR);

            stream.push(new StartEvent(partial));
            stream.pushTextDelta(0, "partial text", partial);
            stream.pushError("error", errorMsg);

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> assertInstanceOf(StartEvent.class, e))
                .assertNext(e -> assertInstanceOf(TextDeltaEvent.class, e))
                .assertNext(e -> {
                    var err = assertInstanceOf(ErrorEvent.class, e);
                    assertEquals("error", err.reason());
                })
                .verifyComplete();

            StepVerifier.create(stream.result())
                .expectNext(errorMsg)
                .verifyComplete();
        }
    }

    // --- Convenience Methods ---

    @Nested
    class ConvenienceMethods {

        @Test
        void pushTextDeltaCreatesCorrectEvent() {
            var stream = new AssistantMessageEventStream();
            var partial = samplePartial();
            var finalMsg = sampleMessage(StopReason.STOP);

            stream.pushTextDelta(2, "delta-text", partial);
            stream.pushDone(StopReason.STOP, finalMsg);

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> {
                    var delta = assertInstanceOf(TextDeltaEvent.class, e);
                    assertEquals(2, delta.contentIndex());
                    assertEquals("delta-text", delta.delta());
                    assertSame(partial, delta.partial());
                })
                .assertNext(e -> assertInstanceOf(DoneEvent.class, e))
                .verifyComplete();
        }

        @Test
        void pushDoneCompletesStreamWithMessage() {
            var stream = new AssistantMessageEventStream();
            var finalMsg = sampleMessage(StopReason.TOOL_USE);

            stream.pushDone(StopReason.TOOL_USE, finalMsg);

            StepVerifier.create(stream.result())
                .expectNext(finalMsg)
                .verifyComplete();

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> {
                    var done = assertInstanceOf(DoneEvent.class, e);
                    assertEquals(StopReason.TOOL_USE, done.reason());
                    assertSame(finalMsg, done.message());
                })
                .verifyComplete();
        }

        @Test
        void pushErrorCompletesStreamWithError() {
            var stream = new AssistantMessageEventStream();
            var errorMsg = sampleMessage(StopReason.ABORTED);

            stream.pushError("aborted", errorMsg);

            StepVerifier.create(stream.result())
                .expectNext(errorMsg)
                .verifyComplete();

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> {
                    var err = assertInstanceOf(ErrorEvent.class, e);
                    assertEquals("aborted", err.reason());
                    assertSame(errorMsg, err.error());
                })
                .verifyComplete();
        }
    }

    // --- Auto-Completion Semantics ---

    @Nested
    class AutoCompletion {

        @Test
        void doneEventAutoCompletesStream() {
            var stream = new AssistantMessageEventStream();
            var finalMsg = sampleMessage(StopReason.STOP);

            stream.push(new StartEvent(samplePartial()));
            stream.pushDone(StopReason.STOP, finalMsg);

            // Push after done should be ignored
            stream.pushTextDelta(0, "ignored", samplePartial());

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> assertInstanceOf(StartEvent.class, e))
                .assertNext(e -> assertInstanceOf(DoneEvent.class, e))
                .verifyComplete();
        }

        @Test
        void errorEventAutoCompletesStream() {
            var stream = new AssistantMessageEventStream();
            var errorMsg = sampleMessage(StopReason.ERROR);

            stream.push(new StartEvent(samplePartial()));
            stream.pushError("error", errorMsg);

            // Push after error should be ignored
            stream.pushTextDelta(0, "ignored", samplePartial());

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> assertInstanceOf(StartEvent.class, e))
                .assertNext(e -> assertInstanceOf(ErrorEvent.class, e))
                .verifyComplete();
        }

        @Test
        void pushAfterDoneIgnored() {
            var stream = new AssistantMessageEventStream();
            var finalMsg = sampleMessage(StopReason.LENGTH);

            stream.pushDone(StopReason.LENGTH, finalMsg);
            stream.push(new StartEvent(samplePartial()));
            stream.pushTextDelta(0, "ignored", samplePartial());

            StepVerifier.create(stream.asFlux().count())
                .expectNext(1L)
                .verifyComplete();

            StepVerifier.create(stream.result())
                .expectNext(finalMsg)
                .verifyComplete();
        }
    }

    // --- Explicit End and Error ---

    @Nested
    class ExplicitEndAndError {

        @Test
        void explicitEndWithResult() {
            var stream = new AssistantMessageEventStream();
            var partial = samplePartial();
            var result = sampleMessage(StopReason.STOP);

            stream.push(new StartEvent(partial));
            stream.end(result);

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> assertInstanceOf(StartEvent.class, e))
                .verifyComplete();

            StepVerifier.create(stream.result())
                .expectNext(result)
                .verifyComplete();
        }

        @Test
        void errorTerminatesStream() {
            var stream = new AssistantMessageEventStream();
            stream.push(new StartEvent(samplePartial()));
            stream.error(new RuntimeException("connection lost"));

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> assertInstanceOf(StartEvent.class, e))
                .expectErrorMessage("connection lost")
                .verify();

            StepVerifier.create(stream.result())
                .expectErrorMessage("connection lost")
                .verify();
        }
    }

    // --- Result Extraction ---

    @Nested
    class ResultExtraction {

        @Test
        void doneEventExtractsMessage() {
            var stream = new AssistantMessageEventStream();
            var msg = sampleMessage(StopReason.STOP);

            stream.pushDone(StopReason.STOP, msg);

            StepVerifier.create(stream.result())
                .assertNext(result -> {
                    assertSame(msg, result);
                    assertEquals(StopReason.STOP, result.stopReason());
                })
                .verifyComplete();
        }

        @Test
        void errorEventExtractsErrorMessage() {
            var stream = new AssistantMessageEventStream();
            var errorMsg = new AssistantMessage(
                List.of(),
                "anthropic-messages",
                "anthropic",
                "claude-opus-4-6",
                null,
                Usage.empty(),
                StopReason.ERROR,
                "Something went wrong",
                System.currentTimeMillis()
            );

            stream.pushError("error", errorMsg);

            StepVerifier.create(stream.result())
                .assertNext(result -> {
                    assertSame(errorMsg, result);
                    assertEquals(StopReason.ERROR, result.stopReason());
                    assertEquals("Something went wrong", result.errorMessage());
                })
                .verifyComplete();
        }

        @Test
        void resultAvailableIndependentlyOfFlux() {
            var stream = new AssistantMessageEventStream();
            var msg = sampleMessage(StopReason.STOP);

            stream.push(new StartEvent(samplePartial()));
            stream.pushTextDelta(0, "text", samplePartial());
            stream.pushDone(StopReason.STOP, msg);

            // Check result without consuming Flux
            StepVerifier.create(stream.result())
                .expectNext(msg)
                .verifyComplete();
        }
    }
}
