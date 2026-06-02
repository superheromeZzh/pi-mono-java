/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.mode.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.campusclaw.agent.event.AgentEndEvent;
import com.campusclaw.agent.event.AgentEventListener;
import com.campusclaw.agent.event.AgentStartEvent;
import com.campusclaw.agent.event.MessageEndEvent;
import com.campusclaw.agent.event.MessageStartEvent;
import com.campusclaw.agent.event.MessageUpdateEvent;
import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.StopReason;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.ai.types.Usage;
import com.campusclaw.ai.types.UserMessage;
import com.campusclaw.codingagent.session.AgentSession;
import com.campusclaw.codingagent.session.SessionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

/**
 * Smoke test for {@link ChatWebSocketHandler} that starts a real Reactor Netty
 * server with the WS route, connects with Reactor Netty's {@link HttpClient}
 * websocket client, and verifies the handshake → prompt → event stream → done
 * cycle works end to end.
 */
class ChatWebSocketHandlerTest {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandlerTest.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DisposableServer server;
    private AgentSession session;
    private SessionPool pool;
    private AtomicReference<AgentEventListener> listenerRef;

    // Test-supplied script that replays a specific event sequence through the
    // captured listener when session.prompt(...) is called.
    private Consumer<AgentEventListener> promptScript;

    @BeforeEach
    void setUp() {
        listenerRef = new AtomicReference<>();
        promptScript = null;

        session = mock(AgentSession.class);
        pool = mock(SessionPool.class);

        String conversationId = "test-" + UUID.randomUUID();
        when(pool.getOrCreate(any())).thenReturn(new SessionPool.SessionRef(conversationId, session));

        when(session.isInitialized()).thenReturn(true);
        when(session.isStreaming()).thenReturn(false);

        // Capture the event listener so the test can fire events back through it
        when(session.subscribe(any(AgentEventListener.class))).thenAnswer(invocation -> {
            listenerRef.set(invocation.getArgument(0));
            return (Runnable) () -> {};
        });

        // When the handler calls session.prompt(), run whatever event script the
        // active test installed into `promptScript`.
        when(session.prompt(anyString())).thenAnswer(invocation -> {
            AgentEventListener listener = listenerRef.get();
            assertNotNull(listener, "session.subscribe must be invoked before prompt");
            if (promptScript != null) {
                promptScript.accept(listener);
            }
            return CompletableFuture.completedFuture(null);
        });

        ChatWebSocketHandler wsHandler = new ChatWebSocketHandler(pool);

        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(r -> r.get(
                        "/api/ws/chat", (req, res) -> res.sendWebsocket((in, out) -> wsHandler.handle(in, out, null))))
                .bindNow();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.disposeNow();
        }
    }

    @Test
    void promptTriggersResponseAndDoneEvents() throws Exception {
        promptScript = listener -> {
            listener.onEvent(new AgentStartEvent());
            listener.onEvent(new MessageStartEvent(null));
            listener.onEvent(new MessageUpdateEvent(null, null));
            listener.onEvent(new MessageEndEvent(null));
            listener.onEvent(new AgentEndEvent(List.of()));
        };

        List<JsonNode> frames = runPromptRoundTrip("{\"type\":\"prompt\",\"id\":\"t1\",\"message\":\"hi\"}");

        // Assertion 1: first frame is the `response` to our `prompt` command
        JsonNode first = frames.get(0);
        assertEquals("response", first.path("type").asText());
        assertEquals("t1", first.path("id").asText());
        assertTrue(first.path("success").asBoolean());

        // Assertion 2: a message lifecycle event arrived
        boolean sawLifecycle = frames.stream().anyMatch(f -> {
            String t = f.path("type").asText();
            return "agent_start".equals(t) || "message_start".equals(t) || "message_update".equals(t);
        });
        assertTrue(sawLifecycle, "Expected agent_start / message_start / message_update");

        // Assertion 3: final `done` frame observed
        JsonNode last = frames.get(frames.size() - 1);
        assertEquals("done", last.path("type").asText());
    }

    @Test
    void doneFrameCarriesFinalTextAndUsageAndStopReason() throws Exception {
        AssistantMessage assistantFinal = new AssistantMessage(
                List.<ContentBlock>of(new TextContent("hello world", null)),
                "messages",
                "anthropic",
                "sonnet",
                null,
                Usage.empty(),
                StopReason.STOP,
                null,
                123L);

        promptScript = listener -> {
            listener.onEvent(new MessageStartEvent(assistantFinal));
            listener.onEvent(new MessageUpdateEvent(assistantFinal, null));
            listener.onEvent(new MessageEndEvent(assistantFinal));
            listener.onEvent(new AgentEndEvent(List.<Message>of(assistantFinal)));
        };

        List<JsonNode> frames = runPromptRoundTrip("{\"type\":\"prompt\",\"id\":\"t2\",\"message\":\"hi\"}");

        JsonNode done = frames.get(frames.size() - 1);
        assertEquals("done", done.path("type").asText());
        assertEquals(
                "hello world",
                done.path("finalText").asText(),
                "done frame should carry finalText extracted from the last AssistantMessage");
        assertEquals("stop", done.path("stopReason").asText());
        assertTrue(done.has("usage"), "done frame should include usage");
    }

    @Test
    void messageFramesCarryRoleDiscriminator() throws Exception {
        // Regression: when an AssistantMessage is put into a Map<String, Object>
        // and serialized, Jackson loses the @JsonTypeInfo discriminator and omits
        // "role":"assistant". Frontends filter by role, so losing it means the
        // assistant bubble never renders. The fix routes Messages through
        // MESSAGE_WRITER (writerFor(Message.class)) before embedding.
        AssistantMessage assistantFinal = new AssistantMessage(
                List.<ContentBlock>of(new TextContent("hi back", null)),
                "messages",
                "anthropic",
                "sonnet",
                null,
                Usage.empty(),
                StopReason.STOP,
                null,
                123L);
        promptScript = listener -> {
            listener.onEvent(new MessageStartEvent(assistantFinal));
            listener.onEvent(new MessageUpdateEvent(assistantFinal, null));
            listener.onEvent(new MessageEndEvent(assistantFinal));
            listener.onEvent(new AgentEndEvent(List.<Message>of(assistantFinal)));
        };

        List<JsonNode> frames = runPromptRoundTrip("{\"type\":\"prompt\",\"id\":\"role1\",\"message\":\"hi\"}");

        for (String ft : List.of("message_start", "message_update", "message_end")) {
            int i = indexOfType(frames, ft);
            assertTrue(i >= 0, ft + " frame must be present");
            JsonNode msg = frames.get(i).path("message");
            assertTrue(!msg.isMissingNode(), ft + " frame must carry a message field");
            assertEquals(
                    "assistant", msg.path("role").asText(), ft + " frame's message must include role discriminator");
        }
    }

    @Test
    void modelLevelErrorEmitsErrorFrameBeforeDone() throws Exception {
        AssistantMessage errorMsg = new AssistantMessage(
                List.<ContentBlock>of(),
                "messages",
                "anthropic",
                "sonnet",
                null,
                Usage.empty(),
                StopReason.ERROR,
                "rate limited by upstream",
                123L);

        promptScript = listener -> {
            listener.onEvent(new MessageStartEvent(errorMsg));
            listener.onEvent(new MessageEndEvent(errorMsg));
            listener.onEvent(new AgentEndEvent(List.<Message>of(errorMsg)));
        };

        List<JsonNode> frames = runPromptRoundTrip("{\"type\":\"prompt\",\"id\":\"t3\",\"message\":\"hi\"}");

        // Between message_end and done there must be an error frame
        int messageEndIdx = indexOfType(frames, "message_end");
        int errorIdx = indexOfType(frames, "error");
        int doneIdx = indexOfType(frames, "done");

        assertTrue(messageEndIdx >= 0, "message_end must be present");
        assertTrue(errorIdx > messageEndIdx, "error frame must follow message_end");
        assertTrue(doneIdx > errorIdx, "done must come after the error frame");
        assertEquals(
                "rate limited by upstream", frames.get(errorIdx).path("error").asText());
        assertEquals("error", frames.get(doneIdx).path("stopReason").asText());
    }

    // Stand up a fresh ModelRegistry seeded with two test models so we exercise
    // the actual filtering logic rather than mocking the catalogue.
    private com.campusclaw.ai.model.ModelRegistry buildSeededTestModelRegistry() {
        var modelRegistry = new com.campusclaw.ai.model.ModelRegistry();
        modelRegistry.register(testModel("test-a", "Test A", false, new com.campusclaw.ai.types.ModelCost(1, 2, 0, 0)));
        modelRegistry.register(testModel("test-b", "Test B", true, new com.campusclaw.ai.types.ModelCost(0, 0, 0, 0)));
        return modelRegistry;
    }

    private static com.campusclaw.ai.types.Model testModel(
            String id, String name, boolean reasoning, com.campusclaw.ai.types.ModelCost cost) {
        return new com.campusclaw.ai.types.Model(
                id,
                name,
                com.campusclaw.ai.types.Api.OPENAI_COMPLETIONS,
                com.campusclaw.ai.types.Provider.OPENAI,
                "https://example.com",
                reasoning,
                List.of(com.campusclaw.ai.types.InputModality.TEXT),
                cost,
                128000,
                4096,
                null,
                null,
                null);
    }

    private static com.campusclaw.ai.types.Model testModelWithProvider(
            String id, com.campusclaw.ai.types.Provider provider) {
        return new com.campusclaw.ai.types.Model(
                id,
                id,
                com.campusclaw.ai.types.Api.OPENAI_COMPLETIONS,
                provider,
                "https://example.com",
                false,
                List.of(com.campusclaw.ai.types.InputModality.TEXT),
                new com.campusclaw.ai.types.ModelCost(1, 2, 0, 0),
                128000,
                4096,
                null,
                null,
                null);
    }

    @Test
    void listModelsReturnsAvailableModelsWithCurrent() throws Exception {
        var modelRegistry = buildSeededTestModelRegistry();
        var settingsManager = mock(com.campusclaw.codingagent.settings.SettingsManager.class);
        when(settingsManager.load()).thenReturn(com.campusclaw.codingagent.settings.Settings.empty());
        var catalog = new com.campusclaw.codingagent.model.ModelCatalogService(modelRegistry, settingsManager);
        when(session.getModelId()).thenReturn("test-a");
        ChatWebSocketHandler handler = new ChatWebSocketHandler(pool, catalog);

        // Replace the no-catalog server from setUp() with one wired up.
        if (server != null) {
            server.disposeNow();
        }
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(r -> r.get(
                        "/api/ws/chat", (req, res) -> res.sendWebsocket((in, out) -> handler.handle(in, out, null))))
                .bindNow();

        JsonNode response = runRequestResponse("{\"type\":\"list_models\",\"id\":\"lm1\"}", "lm1");
        assertEquals("response", response.path("type").asText());
        assertTrue(response.path("success").asBoolean(), "list_models should succeed");
        JsonNode data = response.path("data");
        assertEquals("test-a", data.path("current").asText());
        assertEquals(false, data.path("filtered").asBoolean(), "no enabledModels → not filtered");
        JsonNode models = data.path("models");
        assertTrue(models.isArray(), "models should be an array");
        assertEquals(2, models.size());

        // Sorted by provider then id, so test-a comes before test-b.
        assertEquals("test-a", models.get(0).path("id").asText());
        assertEquals("openai", models.get(0).path("provider").asText());
        assertTrue(models.get(0).has("contextWindow"));
        assertTrue(models.get(0).has("cost"));
    }

    @Test
    void listModelsDefaultFiltersByCredentialsAllTrueBypasses() throws Exception {
        var modelRegistry = new com.campusclaw.ai.model.ModelRegistry();
        modelRegistry.register(testModelWithProvider("keyed-a", com.campusclaw.ai.types.Provider.ANTHROPIC));
        modelRegistry.register(testModelWithProvider("unkeyed-z", com.campusclaw.ai.types.Provider.OPENAI));
        var settingsManager = mock(com.campusclaw.codingagent.settings.SettingsManager.class);
        when(settingsManager.load()).thenReturn(com.campusclaw.codingagent.settings.Settings.empty());
        var resolver = mock(com.campusclaw.ai.env.ProviderConfigResolver.class);
        when(resolver.resolve(eq(com.campusclaw.ai.types.Provider.ANTHROPIC), any()))
                .thenReturn(new com.campusclaw.ai.env.ResolvedProviderConfig("sk-key", null, null));
        when(resolver.resolve(eq(com.campusclaw.ai.types.Provider.OPENAI), any()))
                .thenReturn(new com.campusclaw.ai.env.ResolvedProviderConfig(null, null, null));
        var catalog =
                new com.campusclaw.codingagent.model.ModelCatalogService(modelRegistry, settingsManager, resolver);
        when(session.getModelId()).thenReturn("keyed-a");
        ChatWebSocketHandler handler = new ChatWebSocketHandler(pool, catalog);

        if (server != null) {
            server.disposeNow();
        }
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(r -> r.get(
                        "/api/ws/chat", (req, res) -> res.sendWebsocket((in, out) -> handler.handle(in, out, null))))
                .bindNow();

        // Default (all:false) → only the credentialed model is offered.
        JsonNode def = runRequestResponse("{\"type\":\"list_models\",\"id\":\"d\"}", "d");
        assertTrue(def.path("success").asBoolean(), "list_models should succeed");
        JsonNode defModels = def.path("data").path("models");
        assertEquals(1, defModels.size(), "default lists only usable (credentialed) models");
        assertEquals("keyed-a", defModels.get(0).path("id").asText());

        // all:true → full registry escape hatch, includes the uncredentialed model.
        JsonNode all = runRequestResponse("{\"type\":\"list_models\",\"id\":\"a\",\"all\":true}", "a");
        assertTrue(all.path("success").asBoolean(), "list_models all:true should succeed");
        assertEquals(2, all.path("data").path("models").size(), "all:true returns the full registry");
    }

    // =========================================================================
    // Persistence — verifies the WS handler routes append calls through
    // AgentSession.getSessionManager() in the same shape as InteractiveMode.
    // =========================================================================

    @Test
    void promptAppendsUserMessageBeforeForwardingToAgent() throws Exception {
        SessionManager sm = mock(SessionManager.class);
        when(session.getSessionManager()).thenReturn(sm);

        promptScript = listener -> {
            listener.onEvent(new MessageStartEvent(null));
            listener.onEvent(new MessageEndEvent(null));
            listener.onEvent(new AgentEndEvent(List.of()));
        };

        runPromptRoundTrip("{\"type\":\"prompt\",\"id\":\"p1\",\"message\":\"hello\"}");

        // The user message must hit the SessionManager exactly once before the
        // assistant turn produces its own append from the MessageEnd subscriber.
        verify(sm, times(1)).appendMessage(any(UserMessage.class));
    }

    @Test
    void messageEndAppendsAssistantMessageThroughSessionManager() throws Exception {
        SessionManager sm = mock(SessionManager.class);
        when(session.getSessionManager()).thenReturn(sm);

        AssistantMessage assistantFinal = new AssistantMessage(
                List.<ContentBlock>of(new TextContent("ok", null)),
                "messages",
                "anthropic",
                "sonnet",
                null,
                Usage.empty(),
                StopReason.STOP,
                null,
                1L);

        promptScript = listener -> {
            listener.onEvent(new MessageStartEvent(assistantFinal));
            listener.onEvent(new MessageEndEvent(assistantFinal));
            listener.onEvent(new AgentEndEvent(List.<Message>of(assistantFinal)));
        };

        runPromptRoundTrip("{\"type\":\"prompt\",\"id\":\"p2\",\"message\":\"hi\"}");

        verify(sm).appendMessage(eq(assistantFinal));
    }

    @Test
    void newSessionRotatesConversationIdWhenPersistenceEnabled() throws Exception {
        SessionManager sm = mock(SessionManager.class);
        when(session.getSessionManager()).thenReturn(sm);

        JsonNode response = runRequestResponse("{\"type\":\"new_session\",\"id\":\"ns1\"}", "ns1");

        assertEquals("response", response.path("type").asText());
        assertTrue(response.path("success").asBoolean(), "new_session should succeed");
        String returned = response.path("data").path("conversation_id").asText();
        assertNotNull(returned, "new_session response must carry the rotated conversation_id");
        assertNotEquals("", returned, "rotated conversation_id must not be empty");
        verify(sm).close();

        // Session was rotated: pool.rekey is called and getSessionManager is
        // updated to a fresh SessionManager. The mock SM should not have
        // received any further append calls.
        verify(sm, never()).appendMessage(any());
    }

    @Test
    void newSessionKeepsConversationIdWhenPersistenceDisabled() throws Exception {
        // session.getSessionManager() returns null by default → persistence-off branch.
        JsonNode response = runRequestResponse("{\"type\":\"new_session\",\"id\":\"ns2\"}", "ns2");

        assertTrue(response.path("success").asBoolean());

        // The returned id must be the same one assigned at handshake (no rotation
        // when persistence is off, since there is no JSONL file to refresh).
        assertNotEquals("", response.path("data").path("conversation_id").asText());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private JsonNode runRequestResponse(String cmd, String expectedId) throws Exception {
        Queue<String> raws = new ConcurrentLinkedQueue<>();
        AtomicReference<JsonNode> matched = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        HttpClient.create()
                .websocket()
                .uri("ws://" + server.host() + ":" + server.port() + "/api/ws/chat")
                .handle((in, out) -> {
                    Mono<Void> send = out.sendString(Mono.just(cmd)).then();
                    Mono<Void> recv = in.receive()
                            .asString()
                            .doOnNext(frame -> {
                                raws.add(frame);
                                try {
                                    JsonNode node = MAPPER.readTree(frame);
                                    if ("response".equals(node.path("type").asText())
                                            && expectedId.equals(node.path("id").asText())) {
                                        matched.set(node);
                                        latch.countDown();
                                    }
                                } catch (Exception e) {
                                    // non-matching/non-JSON frame — keep waiting for the response
                                    log.debug("ws frame did not parse as expected response (continuing wait)", e);
                                }
                            })
                            .takeUntil(frame -> {
                                try {
                                    JsonNode node = MAPPER.readTree(frame);
                                    return "response".equals(node.path("type").asText())
                                            && expectedId.equals(node.path("id").asText());
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .then();
                    return Flux.merge(send, recv).then();
                })
                .timeout(Duration.ofSeconds(5))
                .blockLast();

        assertTrue(
                latch.await(5, TimeUnit.SECONDS),
                "Expected a response with id=" + expectedId + " within 5s. Frames seen: " + raws);
        return matched.get();
    }

    private List<JsonNode> runPromptRoundTrip(String cmd) throws Exception {
        Queue<String> raws = new ConcurrentLinkedQueue<>();
        CountDownLatch sawDone = new CountDownLatch(1);

        HttpClient.create()
                .websocket()
                .uri("ws://" + server.host() + ":" + server.port() + "/api/ws/chat")
                .handle((in, out) -> {
                    Mono<Void> send = out.sendString(Mono.just(cmd)).then();
                    Mono<Void> recv = in.receive()
                            .asString()
                            .doOnNext(frame -> {
                                raws.add(frame);
                                try {
                                    if ("done"
                                            .equals(MAPPER.readTree(frame)
                                                    .path("type")
                                                    .asText())) {
                                        sawDone.countDown();
                                    }
                                } catch (Exception e) {
                                    // non-JSON frame, ignore
                                    log.debug("ws frame did not parse as JSON (continuing)", e);
                                }
                            })
                            .takeUntil(frame -> {
                                try {
                                    return "done"
                                            .equals(MAPPER.readTree(frame)
                                                    .path("type")
                                                    .asText());
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .then();
                    return Flux.merge(send, recv).then();
                })
                .timeout(Duration.ofSeconds(5))
                .blockLast();

        assertTrue(sawDone.await(5, TimeUnit.SECONDS), "Expected a `done` frame within 5s. Frames seen: " + raws);

        List<JsonNode> parsed = new ArrayList<>();
        for (String raw : raws) {
            parsed.add(MAPPER.readTree(raw));
        }
        assertTrue(!parsed.isEmpty(), "Expected at least one frame");
        return parsed;
    }

    private int indexOfType(List<JsonNode> frames, String type) {
        for (int i = 0; i < frames.size(); i++) {
            if (type.equals(frames.get(i).path("type").asText())) {
                return i;
            }
        }
        return -1;
    }
}
