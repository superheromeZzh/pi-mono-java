package com.huawei.hicampus.mate.matecampusclaw.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.provider.ApiProvider;
import com.huawei.hicampus.mate.matecampusclaw.ai.provider.ApiProviderRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.provider.MockApiProvider;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

class CampusClawAiServiceTest {

    private static final Model ANTHROPIC_MODEL = new Model(
        "claude-sonnet-4-20250514", "Claude Sonnet 4",
        Api.ANTHROPIC_MESSAGES, Provider.ANTHROPIC,
        "https://api.anthropic.com", true,
        List.of(InputModality.TEXT, InputModality.IMAGE),
        new ModelCost(3.0, 15.0, 0.3, 3.75),
        200000, 16000, null, null,
        null
    );

    private static final Model OPENAI_MODEL = new Model(
        "gpt-4o", "GPT-4o",
        Api.OPENAI_RESPONSES, Provider.OPENAI,
        "https://api.openai.com", false,
        List.of(InputModality.TEXT, InputModality.IMAGE),
        new ModelCost(2.5, 10.0, 1.25, 2.5),
        128000, 16384, null, null,
        null
    );

    private ApiProviderRegistry providerRegistry;
    private ModelRegistry modelRegistry;
    private CampusClawAiService service;

    @BeforeEach
    void setUp() {
        providerRegistry = new ApiProviderRegistry(null);
        modelRegistry = new ModelRegistry();
        service = new CampusClawAiService(providerRegistry, modelRegistry);
    }

    // --- Construction ---

    @Nested
    class Construction {

        @Test
        void rejectsNullProviderRegistry() {
            assertThrows(NullPointerException.class,
                () -> new CampusClawAiService(null, modelRegistry));
        }

        @Test
        void rejectsNullModelRegistry() {
            assertThrows(NullPointerException.class,
                () -> new CampusClawAiService(providerRegistry, null));
        }

        @Test
        void exposesRegistries() {
            assertSame(providerRegistry, service.getProviderRegistry());
            assertSame(modelRegistry, service.getModelRegistry());
        }
    }

    // --- stream ---

    @Nested
    class Stream {

        @Test
        void delegatesToProviderStream() {
            var mockProvider = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            providerRegistry.register(mockProvider, "test");

            var context = new Context(null, List.of(new UserMessage("hi", 1)), null);
            var eventStream = service.stream(ANTHROPIC_MODEL, context, null);

            assertNotNull(eventStream);

            StepVerifier.create(eventStream.asFlux())
                .expectNextMatches(e -> e instanceof AssistantMessageEvent.StartEvent)
                .expectNextMatches(e -> e instanceof AssistantMessageEvent.TextStartEvent)
                .expectNextMatches(e -> e instanceof AssistantMessageEvent.TextDeltaEvent)
                .expectNextMatches(e -> e instanceof AssistantMessageEvent.TextEndEvent)
                .expectNextMatches(e -> e instanceof AssistantMessageEvent.DoneEvent)
                .verifyComplete();
        }

        @Test
        void throwsWhenNoProviderRegistered() {
            var context = new Context(null, List.of(new UserMessage("hi", 1)), null);

            var ex = assertThrows(IllegalArgumentException.class,
                () -> service.stream(ANTHROPIC_MODEL, context, null));
            assertTrue(ex.getMessage().contains("anthropic-messages"));
        }

        @Test
        void rejectsNullModel() {
            assertThrows(NullPointerException.class,
                () -> service.stream(null, new Context(null, List.of(), null), null));
        }

        @Test
        void passesOptionsToProvider() {
            // Use a custom provider that captures the options
            var capturedOptions = new Object() { StreamOptions value; };
            var provider = new ApiProvider() {
                @Override
                public Api getApi() { return Api.ANTHROPIC_MESSAGES; }

                @Override
                public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
                    capturedOptions.value = options;
                    return new MockApiProvider(Api.ANTHROPIC_MESSAGES).stream(model, context, options);
                }

                @Override
                public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
                    return new MockApiProvider(Api.ANTHROPIC_MESSAGES).streamSimple(model, context, options);
                }
            };
            providerRegistry.register(provider, "test");

            var options = StreamOptions.builder().temperature(0.7).maxTokens(100).build();
            var context = new Context(null, List.of(new UserMessage("hi", 1)), null);
            service.stream(ANTHROPIC_MODEL, context, options);

            assertSame(options, capturedOptions.value);
        }
    }

    // --- streamSimple ---

    @Nested
    class StreamSimple {

        @Test
        void delegatesToProviderStreamSimple() {
            var mockProvider = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            providerRegistry.register(mockProvider, "test");

            var context = new Context(null, List.of(new UserMessage("hi", 1)), null);
            var eventStream = service.streamSimple(ANTHROPIC_MODEL, context, null);

            assertNotNull(eventStream);

            StepVerifier.create(eventStream.asFlux())
                .expectNextMatches(e -> e instanceof AssistantMessageEvent.StartEvent)
                .expectNextMatches(e -> e instanceof AssistantMessageEvent.TextStartEvent)
                .expectNextMatches(e -> e instanceof AssistantMessageEvent.TextDeltaEvent)
                .expectNextMatches(e -> e instanceof AssistantMessageEvent.TextEndEvent)
                .expectNextMatches(e -> e instanceof AssistantMessageEvent.DoneEvent)
                .verifyComplete();
        }

        @Test
        void throwsWhenNoProviderRegistered() {
            var context = new Context(null, List.of(new UserMessage("hi", 1)), null);

            assertThrows(IllegalArgumentException.class,
                () -> service.streamSimple(ANTHROPIC_MODEL, context, null));
        }

        @Test
        void passesSimpleOptionsToProvider() {
            var capturedOptions = new Object() { SimpleStreamOptions value; };
            var provider = new ApiProvider() {
                @Override
                public Api getApi() { return Api.ANTHROPIC_MESSAGES; }

                @Override
                public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
                    return new MockApiProvider(Api.ANTHROPIC_MESSAGES).stream(model, context, options);
                }

                @Override
                public AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options) {
                    capturedOptions.value = options;
                    return new MockApiProvider(Api.ANTHROPIC_MESSAGES).streamSimple(model, context, options);
                }
            };
            providerRegistry.register(provider, "test");

            var options = SimpleStreamOptions.builder()
                .temperature(0.5)
                .reasoning(ThinkingLevel.HIGH)
                .build();
            var context = new Context(null, List.of(new UserMessage("hi", 1)), null);
            service.streamSimple(ANTHROPIC_MODEL, context, options);

            assertSame(options, capturedOptions.value);
        }
    }

    // --- complete ---

    @Nested
    class Complete {

        @Test
        void consumesStreamAndReturnsAssistantMessage() {
            var mockProvider = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            providerRegistry.register(mockProvider, "test");

            var context = new Context(null, List.of(new UserMessage("hi", 1)), null);

            StepVerifier.create(service.complete(ANTHROPIC_MODEL, context, null))
                .assertNext(msg -> {
                    assertNotNull(msg);
                    assertEquals(StopReason.STOP, msg.stopReason());
                    assertFalse(msg.content().isEmpty());
                })
                .verifyComplete();
        }

        @Test
        void completeWithCustomEvents() {
            var finalMessage = new AssistantMessage(
                List.of(new TextContent("custom response")),
                Api.ANTHROPIC_MESSAGES.value(),
                Provider.ANTHROPIC.value(),
                "claude-sonnet-4-20250514",
                null,
                new Usage(10, 20, 0, 0, 30, Cost.empty()),
                StopReason.STOP,
                null,
                System.currentTimeMillis()
            );

            var events = List.<AssistantMessageEvent>of(
                new AssistantMessageEvent.StartEvent(finalMessage),
                new AssistantMessageEvent.TextStartEvent(0, finalMessage),
                new AssistantMessageEvent.TextDeltaEvent(0, "custom response", finalMessage),
                new AssistantMessageEvent.TextEndEvent(0, "custom response", finalMessage),
                new AssistantMessageEvent.DoneEvent(StopReason.STOP, finalMessage)
            );

            var mockProvider = new MockApiProvider(Api.ANTHROPIC_MESSAGES, events);
            providerRegistry.register(mockProvider, "test");

            var context = new Context(null, List.of(new UserMessage("hi", 1)), null);

            StepVerifier.create(service.complete(ANTHROPIC_MODEL, context, null))
                .assertNext(msg -> {
                    assertEquals("custom response",
                        ((TextContent) msg.content().get(0)).text());
                    assertEquals(10, msg.usage().input());
                    assertEquals(20, msg.usage().output());
                })
                .verifyComplete();
        }

        @Test
        void throwsWhenNoProviderRegistered() {
            var context = new Context(null, List.of(new UserMessage("hi", 1)), null);

            assertThrows(IllegalArgumentException.class,
                () -> service.complete(ANTHROPIC_MODEL, context, null));
        }
    }

    // --- completeSimple ---

    @Nested
    class CompleteSimple {

        @Test
        void consumesStreamAndReturnsAssistantMessage() {
            var mockProvider = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            providerRegistry.register(mockProvider, "test");

            var context = new Context(null, List.of(new UserMessage("hi", 1)), null);

            StepVerifier.create(service.completeSimple(ANTHROPIC_MODEL, context, null))
                .assertNext(msg -> {
                    assertNotNull(msg);
                    assertEquals(StopReason.STOP, msg.stopReason());
                })
                .verifyComplete();
        }
    }

    // --- Convenience complete(Model, String) ---

    @Nested
    class ConvenienceComplete {

        @Test
        void createsContextFromStringAndCompletes() {
            var mockProvider = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            providerRegistry.register(mockProvider, "test");

            StepVerifier.create(service.complete(ANTHROPIC_MODEL, "Hello, world!"))
                .assertNext(msg -> {
                    assertNotNull(msg);
                    assertEquals(StopReason.STOP, msg.stopReason());
                })
                .verifyComplete();
        }

        @Test
        void rejectsNullUserMessage() {
            providerRegistry.register(new MockApiProvider(Api.ANTHROPIC_MESSAGES), "test");

            assertThrows(NullPointerException.class,
                () -> service.complete(ANTHROPIC_MODEL, (String) null));
        }
    }

    // --- Multiple providers ---

    @Nested
    class MultipleProviders {

        @Test
        void routesToCorrectProviderByApi() {
            var anthropicProvider = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            var openaiProvider = new MockApiProvider(Api.OPENAI_RESPONSES);

            providerRegistry.register(anthropicProvider, "test");
            providerRegistry.register(openaiProvider, "test");

            var context = new Context(null, List.of(new UserMessage("hi", 1)), null);

            // Both should work and route correctly
            var anthropicStream = service.stream(ANTHROPIC_MODEL, context, null);
            assertNotNull(anthropicStream);

            var openaiStream = service.stream(OPENAI_MODEL, context, null);
            assertNotNull(openaiStream);
        }
    }

    // --- Error event handling ---

    @Nested
    class ErrorEventHandling {

        @Test
        void completeReturnsErrorMessage() {
            var errorMessage = new AssistantMessage(
                List.of(),
                Api.ANTHROPIC_MESSAGES.value(),
                Provider.ANTHROPIC.value(),
                "claude-sonnet-4-20250514",
                null,
                Usage.empty(),
                StopReason.ERROR,
                "Something went wrong",
                System.currentTimeMillis()
            );

            var events = List.<AssistantMessageEvent>of(
                new AssistantMessageEvent.StartEvent(errorMessage),
                new AssistantMessageEvent.ErrorEvent("error", errorMessage)
            );

            var mockProvider = new MockApiProvider(Api.ANTHROPIC_MESSAGES, events);
            providerRegistry.register(mockProvider, "test");

            var context = new Context(null, List.of(new UserMessage("hi", 1)), null);

            StepVerifier.create(service.complete(ANTHROPIC_MODEL, context, null))
                .assertNext(msg -> {
                    assertEquals(StopReason.ERROR, msg.stopReason());
                    assertEquals("Something went wrong", msg.errorMessage());
                })
                .verifyComplete();
        }
    }
}
