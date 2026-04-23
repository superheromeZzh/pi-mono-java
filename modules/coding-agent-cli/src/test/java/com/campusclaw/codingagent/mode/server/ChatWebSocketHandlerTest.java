package com.campusclaw.codingagent.mode.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
import com.campusclaw.codingagent.session.AgentSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
            return (Runnable) () -> { };
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
                .route(r -> r.get("/api/ws/chat",
                        (req, res) -> res.sendWebsocket((in, out) -> wsHandler.handle(in, out, null))))
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
                "messages", "anthropic", "sonnet",
                null, Usage.empty(), StopReason.STOP, null, 123L
        );

        promptScript = listener -> {
            listener.onEvent(new MessageStartEvent(assistantFinal));
            listener.onEvent(new MessageUpdateEvent(assistantFinal, null));
            listener.onEvent(new MessageEndEvent(assistantFinal));
            listener.onEvent(new AgentEndEvent(List.<Message>of(assistantFinal)));
        };

        List<JsonNode> frames = runPromptRoundTrip("{\"type\":\"prompt\",\"id\":\"t2\",\"message\":\"hi\"}");

        JsonNode done = frames.get(frames.size() - 1);
        assertEquals("done", done.path("type").asText());
        assertEquals("hello world", done.path("finalText").asText(),
                "done frame should carry finalText extracted from the last AssistantMessage");
        assertEquals("stop", done.path("stopReason").asText());
        assertTrue(done.has("usage"), "done frame should include usage");
    }

    @Test
    void modelLevelErrorEmitsErrorFrameBeforeDone() throws Exception {
        AssistantMessage errorMsg = new AssistantMessage(
                List.<ContentBlock>of(),
                "messages", "anthropic", "sonnet",
                null, Usage.empty(), StopReason.ERROR, "rate limited by upstream", 123L
        );

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
        assertEquals("rate limited by upstream", frames.get(errorIdx).path("error").asText());
        assertEquals("error", frames.get(doneIdx).path("stopReason").asText());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
                                    if ("done".equals(MAPPER.readTree(frame).path("type").asText())) {
                                        sawDone.countDown();
                                    }
                                } catch (Exception ignored) {
                                    // non-JSON frame, ignore
                                }
                            })
                            .takeUntil(frame -> {
                                try {
                                    return "done".equals(MAPPER.readTree(frame).path("type").asText());
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .then();
                    return Flux.merge(send, recv).then();
                })
                .timeout(Duration.ofSeconds(5))
                .blockLast();

        assertTrue(sawDone.await(5, TimeUnit.SECONDS),
                "Expected a `done` frame within 5s. Frames seen: " + raws);

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
