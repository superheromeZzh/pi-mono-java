package com.huawei.hicampus.mate.matecampusclaw.ai.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent.*;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

class ApiProviderTest {

    private static final Api TEST_API = Api.ANTHROPIC_MESSAGES;

    private static final Model TEST_MODEL = new Model(
        "mock-model",
        "Mock Model",
        TEST_API,
        Provider.ANTHROPIC,
        "https://api.mock.test",
        false,
        List.of(InputModality.TEXT),
        new ModelCost(1.0, 2.0, 0.5, 0.5),
        100_000,
        4096,
        null,
        null,
        null
    );

    private static final Context TEST_CONTEXT = new Context(
        "You are a test assistant.",
        List.of(new UserMessage("Hello", System.currentTimeMillis())),
        null
    );

    // --- Interface Contract ---

    @Nested
    class InterfaceContract {

        @Test
        void getApiReturnsConfiguredApi() {
            var provider = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            assertEquals(Api.ANTHROPIC_MESSAGES, provider.getApi());
        }

        @Test
        void getApiReturnsDifferentApis() {
            var anthropic = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            var openai = new MockApiProvider(Api.OPENAI_RESPONSES);

            assertEquals(Api.ANTHROPIC_MESSAGES, anthropic.getApi());
            assertEquals(Api.OPENAI_RESPONSES, openai.getApi());
            assertNotEquals(anthropic.getApi(), openai.getApi());
        }
    }

    // --- Default Streaming Behavior ---

    @Nested
    class DefaultStreaming {

        @Test
        void streamProducesDefaultEventSequence() {
            var provider = new MockApiProvider(TEST_API);
            var stream = provider.stream(TEST_MODEL, TEST_CONTEXT, null);

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> assertInstanceOf(StartEvent.class, e))
                .assertNext(e -> assertInstanceOf(TextStartEvent.class, e))
                .assertNext(e -> {
                    var delta = assertInstanceOf(TextDeltaEvent.class, e);
                    assertEquals("Hello from mock", delta.delta());
                    assertEquals(0, delta.contentIndex());
                })
                .assertNext(e -> {
                    var end = assertInstanceOf(TextEndEvent.class, e);
                    assertEquals("Hello from mock", end.content());
                })
                .assertNext(e -> {
                    var done = assertInstanceOf(DoneEvent.class, e);
                    assertEquals(StopReason.STOP, done.reason());
                })
                .verifyComplete();
        }

        @Test
        void streamSimpleProducesDefaultEventSequence() {
            var provider = new MockApiProvider(TEST_API);
            var stream = provider.streamSimple(TEST_MODEL, TEST_CONTEXT, null);

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> assertInstanceOf(StartEvent.class, e))
                .assertNext(e -> assertInstanceOf(TextStartEvent.class, e))
                .assertNext(e -> assertInstanceOf(TextDeltaEvent.class, e))
                .assertNext(e -> assertInstanceOf(TextEndEvent.class, e))
                .assertNext(e -> assertInstanceOf(DoneEvent.class, e))
                .verifyComplete();
        }

        @Test
        void streamResultResolvesToFinalMessage() {
            var provider = new MockApiProvider(TEST_API);
            var stream = provider.stream(TEST_MODEL, TEST_CONTEXT, null);

            StepVerifier.create(stream.result())
                .assertNext(msg -> {
                    assertEquals(StopReason.STOP, msg.stopReason());
                    assertEquals(TEST_API.value(), msg.api());
                    assertEquals("mock", msg.provider());
                    assertEquals("mock-model", msg.model());
                    assertFalse(msg.content().isEmpty());
                })
                .verifyComplete();
        }

        @Test
        void streamWithOptionsUsesDefaults() {
            var provider = new MockApiProvider(TEST_API);
            var options = StreamOptions.builder()
                .temperature(0.5)
                .maxTokens(1024)
                .build();

            var stream = provider.stream(TEST_MODEL, TEST_CONTEXT, options);

            StepVerifier.create(stream.asFlux())
                .expectNextCount(5)
                .verifyComplete();
        }

        @Test
        void streamSimpleWithOptionsUsesDefaults() {
            var provider = new MockApiProvider(TEST_API);
            var options = SimpleStreamOptions.builder()
                .temperature(0.7)
                .reasoning(ThinkingLevel.MEDIUM)
                .build();

            var stream = provider.streamSimple(TEST_MODEL, TEST_CONTEXT, options);

            StepVerifier.create(stream.asFlux())
                .expectNextCount(5)
                .verifyComplete();
        }
    }

    // --- Custom Event Sequences ---

    @Nested
    class CustomEvents {

        @Test
        void customEventSequence() {
            var partial = sampleMessage(StopReason.STOP);
            var finalMsg = sampleMessage(StopReason.STOP);
            var events = List.<AssistantMessageEvent>of(
                new StartEvent(partial),
                new TextStartEvent(0, partial),
                new TextDeltaEvent(0, "custom", partial),
                new TextDeltaEvent(0, " response", partial),
                new TextEndEvent(0, "custom response", partial),
                new DoneEvent(StopReason.STOP, finalMsg)
            );

            var provider = new MockApiProvider(TEST_API, events);
            var stream = provider.stream(TEST_MODEL, TEST_CONTEXT, null);

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> assertInstanceOf(StartEvent.class, e))
                .assertNext(e -> assertInstanceOf(TextStartEvent.class, e))
                .assertNext(e -> {
                    var delta = assertInstanceOf(TextDeltaEvent.class, e);
                    assertEquals("custom", delta.delta());
                })
                .assertNext(e -> {
                    var delta = assertInstanceOf(TextDeltaEvent.class, e);
                    assertEquals(" response", delta.delta());
                })
                .assertNext(e -> assertInstanceOf(TextEndEvent.class, e))
                .assertNext(e -> {
                    var done = assertInstanceOf(DoneEvent.class, e);
                    assertEquals(StopReason.STOP, done.reason());
                })
                .verifyComplete();
        }

        @Test
        void customErrorSequence() {
            var partial = sampleMessage(StopReason.ERROR);
            var errorMsg = new AssistantMessage(
                List.of(),
                TEST_API.value(),
                "mock",
                "mock-model",
                null,
                Usage.empty(),
                StopReason.ERROR,
                "API rate limit exceeded",
                System.currentTimeMillis()
            );
            var events = List.<AssistantMessageEvent>of(
                new StartEvent(partial),
                new ErrorEvent("error", errorMsg)
            );

            var provider = new MockApiProvider(TEST_API, events);
            var stream = provider.stream(TEST_MODEL, TEST_CONTEXT, null);

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> assertInstanceOf(StartEvent.class, e))
                .assertNext(e -> {
                    var err = assertInstanceOf(ErrorEvent.class, e);
                    assertEquals("error", err.reason());
                })
                .verifyComplete();

            StepVerifier.create(stream.result())
                .assertNext(msg -> {
                    assertEquals(StopReason.ERROR, msg.stopReason());
                    assertEquals("API rate limit exceeded", msg.errorMessage());
                })
                .verifyComplete();
        }

        @Test
        void customToolCallSequence() {
            var partial = sampleMessage(StopReason.TOOL_USE);
            var toolCall = new ToolCall("tc-1", "readFile", java.util.Map.of("path", "/tmp/test.txt"), null);
            var finalMsg = new AssistantMessage(
                List.of(toolCall),
                TEST_API.value(),
                "mock",
                "mock-model",
                null,
                Usage.empty(),
                StopReason.TOOL_USE,
                null,
                System.currentTimeMillis()
            );
            var events = List.<AssistantMessageEvent>of(
                new StartEvent(partial),
                new ToolCallStartEvent(0, partial),
                new ToolCallDeltaEvent(0, "{\"path\":\"/tmp/test.txt\"}", partial),
                new ToolCallEndEvent(0, toolCall, partial),
                new DoneEvent(StopReason.TOOL_USE, finalMsg)
            );

            var provider = new MockApiProvider(TEST_API, events);
            var stream = provider.stream(TEST_MODEL, TEST_CONTEXT, null);

            StepVerifier.create(stream.asFlux())
                .assertNext(e -> assertInstanceOf(StartEvent.class, e))
                .assertNext(e -> assertInstanceOf(ToolCallStartEvent.class, e))
                .assertNext(e -> {
                    var delta = assertInstanceOf(ToolCallDeltaEvent.class, e);
                    assertTrue(delta.delta().contains("path"));
                })
                .assertNext(e -> {
                    var end = assertInstanceOf(ToolCallEndEvent.class, e);
                    assertEquals("readFile", end.toolCall().name());
                })
                .assertNext(e -> {
                    var done = assertInstanceOf(DoneEvent.class, e);
                    assertEquals(StopReason.TOOL_USE, done.reason());
                })
                .verifyComplete();

            StepVerifier.create(stream.result())
                .assertNext(msg -> assertEquals(StopReason.TOOL_USE, msg.stopReason()))
                .verifyComplete();
        }
    }

    // --- Stream and StreamSimple Consistency ---

    @Nested
    class StreamConsistency {

        @Test
        void streamAndStreamSimpleProduceSameEventsForDefaultProvider() {
            var provider = new MockApiProvider(TEST_API);

            var s1 = provider.stream(TEST_MODEL, TEST_CONTEXT, null);
            var s2 = provider.streamSimple(TEST_MODEL, TEST_CONTEXT, null);

            var count1 = s1.asFlux().count().block();
            var count2 = s2.asFlux().count().block();
            assertEquals(count1, count2);
        }

        @Test
        void eachStreamCallReturnsIndependentStream() {
            var provider = new MockApiProvider(TEST_API);

            var s1 = provider.stream(TEST_MODEL, TEST_CONTEXT, null);
            var s2 = provider.stream(TEST_MODEL, TEST_CONTEXT, null);

            assertNotSame(s1, s2);

            StepVerifier.create(s1.result())
                .assertNext(msg -> assertEquals(StopReason.STOP, msg.stopReason()))
                .verifyComplete();

            StepVerifier.create(s2.result())
                .assertNext(msg -> assertEquals(StopReason.STOP, msg.stopReason()))
                .verifyComplete();
        }
    }

    // --- Helpers ---

    private static AssistantMessage sampleMessage(StopReason reason) {
        return new AssistantMessage(
            List.of(new TextContent("test")),
            TEST_API.value(),
            "mock",
            "mock-model",
            null,
            Usage.empty(),
            reason,
            null,
            System.currentTimeMillis()
        );
    }
}
