# Plan: AsyncAPI 3.0 spec + WebSocket chat endpoint for ServerMode

> Implemented on 2026-04-23. Kept as-is for historical reference — do not treat it as current documentation.
> Current contract: [`docs/asyncapi/chat-ws.yaml`](../asyncapi/chat-ws.yaml).
> Current testing guide: [`docs/testing/ws-chat-testing.md`](../testing/ws-chat-testing.md).

## Context

The existing `ServerMode` (Reactor Netty + Spring WebFlux router) already exposes `POST /api/chat` as an SSE stream, driven by `SessionPool` + `AgentSession` as the SDK layer. The frontend needs an interactive channel that supports **打断 / 切模型 / 新开会话 / 中途 steer** in a single long-lived connection — things a one-shot SSE POST can't do cleanly.

This change adds:

1. A **WebSocket endpoint** `/api/ws/chat` that shares `SessionPool` with the SSE endpoint, exposes the full `AgentSession` command surface via JSON frames, and streams agent events back.
2. An **AsyncAPI 3.0** document at `docs/asyncapi/chat-ws.yaml` that is the canonical contract for frontend/gateway integration — the WS-world equivalent of an OpenAPI doc.

SSE and WS coexist; nothing is removed.

## Decisions locked in

- **Command scope**: full set — only commands backed by real `AgentSession` / `Agent` methods (`prompt`, `steer`, `abort`, `new_session`, `set_model`, `set_thinking_level`, `get_state`, `get_history`, `get_prompt_templates`, `list_skills`, `ping`).
- **Event scope**: 8 events wired to the WS wire (`agent_start`, `message_start`, `message_update`, `message_end`, `tool_start`, `tool_update`, `tool_end`, `done`) plus two out-of-band frames (`response`, `error`) and `pong` for heartbeat.
- **No refactor** of the existing SSE serializer in `ChatHandler.java:100-129`. The WS handler keeps its own independent `AgentEvent → JSON` mapping block. This means an 8-branch `instanceof` cascade lives in `ChatWebSocketHandler`; the SSE version in `ChatHandler` is left untouched.
- **Tests**: one minimal integration smoke test that drives the WS server with Reactor Netty's `HttpClient.websocket()`, sends a `prompt`, asserts `done` arrives.

## File inventory

### New

| Path | Purpose |
|---|---|
| `docs/asyncapi/chat-ws.yaml` | AsyncAPI 3.0 spec — single document, all commands + events |
| `modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/mode/server/ChatWebSocketHandler.java` | WS handler: `Publisher<Void> handle(WebsocketInbound, WebsocketOutbound, String)` |
| `modules/coding-agent-cli/src/test/java/com/campusclaw/codingagent/mode/server/ChatWebSocketHandlerTest.java` | Smoke test using Reactor Netty `HttpClient.websocket()` |

### Modified

| Path | Change |
|---|---|
| `modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/mode/server/ServerMode.java` | Switch `HttpServer.create().handle(adapter)` to `.route(r -> r.get("/api/ws/chat", ...).route(req -> true, adapter))`. Instantiate `ChatWebSocketHandler`. Add one startup log line and an `extractQueryParam` helper. |
| `docs/server-api.md` | Add a "WebSocket" subsection pointing at `docs/asyncapi/chat-ws.yaml` for the contract; keep existing SSE and RPC docs unchanged. |

Nothing else is touched. `ChatHandler.java`, `SessionPool.java`, `SkillHandler.java`, and all `AgentSession` code stay as-is.

## AsyncAPI 3.0 document (`docs/asyncapi/chat-ws.yaml`)

Structure:

```yaml
asyncapi: 3.0.0
info:
  title: CampusClaw Chat WebSocket API
  version: 1.0.0
  description: Interactive chat channel over WebSocket. Complements POST /api/chat (SSE).

servers:
  local:
    host: localhost:3000
    protocol: ws
    pathname: /api/ws/chat
  production:
    host: api.example.com
    protocol: wss
    pathname: /api/ws/chat
    security:
      - $ref: '#/components/securitySchemes/bearerToken'

channels:
  chat:
    address: /api/ws/chat
    parameters:
      conversation_id:
        description: Optional existing conversation to resume. Passed as query param on handshake.
    messages:
      # client → server
      Prompt:          { $ref: '#/components/messages/Prompt' }
      Steer:           { $ref: '#/components/messages/Steer' }
      Abort:           { $ref: '#/components/messages/Abort' }
      NewSession:      { $ref: '#/components/messages/NewSession' }
      SetModel:        { $ref: '#/components/messages/SetModel' }
      SetThinkingLevel:{ $ref: '#/components/messages/SetThinkingLevel' }
      GetState:        { $ref: '#/components/messages/GetState' }
      GetHistory:      { $ref: '#/components/messages/GetHistory' }
      GetPromptTemplates:{ $ref: '#/components/messages/GetPromptTemplates' }
      ListSkills:      { $ref: '#/components/messages/ListSkills' }
      Ping:            { $ref: '#/components/messages/Ping' }
      # server → client
      Response:        { $ref: '#/components/messages/Response' }
      AgentStart:      { $ref: '#/components/messages/AgentStart' }
      MessageStart:    { $ref: '#/components/messages/MessageStart' }
      MessageUpdate:   { $ref: '#/components/messages/MessageUpdate' }
      MessageEnd:      { $ref: '#/components/messages/MessageEnd' }
      ToolStart:       { $ref: '#/components/messages/ToolStart' }
      ToolUpdate:      { $ref: '#/components/messages/ToolUpdate' }
      ToolEnd:         { $ref: '#/components/messages/ToolEnd' }
      Done:            { $ref: '#/components/messages/Done' }
      ErrorEvent:      { $ref: '#/components/messages/ErrorEvent' }
      Pong:            { $ref: '#/components/messages/Pong' }

operations:
  sendCommand:
    action: send
    channel: { $ref: '#/channels/chat' }
    messages: [ /* all 11 client messages */ ]
  receiveEvent:
    action: receive
    channel: { $ref: '#/channels/chat' }
    messages: [ /* all 11 server messages */ ]

components:
  securitySchemes:
    bearerToken:
      type: httpApiKey
      in: query
      name: token
  messages:
    # each message has payload schema referencing components/schemas/*
  schemas:
    # per-frame schemas derived from:
    #   commands: see "Command reference" section below
    #   events:   field names taken verbatim from AgentEvent subtypes
```

Payload schema rules — every frame is a JSON object with a required `type` discriminator.

**Commands** — optional `id` correlates with a `Response` frame.

| type | required fields | optional fields | Backend method |
|---|---|---|---|
| `prompt` | `message: string` | `id: string` | `session.prompt(message)` |
| `steer` | `message: string` | `id` | `session.steer(message)` |
| `abort` | — | `id` | `session.abort()` |
| `new_session` | — | `id` | `session.newSession()` |
| `set_model` | `model: string` | `id` | `session.setModel(modelId)` |
| `set_thinking_level` | `level: enum(off,minimal,low,medium,high,xhigh)` | `id` | `session.getAgent().setThinkingLevel(ThinkingLevel.fromValue(level))` |
| `get_state` | — | `id` | reads `isStreaming`, `getModelId`, `getAgent().getThinkingLevel()` |
| `get_history` | — | `id` | `session.getHistory()` |
| `get_prompt_templates` | — | `id` | `session.getPromptTemplates()` |
| `list_skills` | — | `id` | `session.getSkillRegistry().getAll()` |
| `ping` | — | — | emit `pong` |

**Events** — field names copied verbatim from sealed records in `modules/agent-core/.../event/`.

| wire `type` | fields | source class |
|---|---|---|
| `agent_start` | `conversation_id` | `AgentStartEvent` |
| `message_start` | `conversation_id`, `message` | `MessageStartEvent` |
| `message_update` | `message` | `MessageUpdateEvent.message()` |
| `message_end` | `message` | `MessageEndEvent.message()` |
| `tool_start` | `toolName`, `toolCallId`, `args` | `ToolExecutionStartEvent` |
| `tool_update` | `toolCallId`, `toolName`, `args`, `partialResult` | `ToolExecutionUpdateEvent` |
| `tool_end` | `toolCallId`, `toolName`, `isError`, `result` | `ToolExecutionEndEvent` |
| `done` | `conversation_id` | `AgentEndEvent` |
| `error` | `error: string`, `conversation_id` | — |
| `response` | `id`, `success: bool`, `data?`, `error?` | sync reply to client commands with `id` |
| `pong` | — | heartbeat |

Close codes published in the spec:

- `1000` normal
- `4400` bad frame / validation error
- `4401` unauthenticated
- `4409` conversation busy (already streaming — matches the HTTP 409 in `ChatHandler.java:71`)

## `ChatWebSocketHandler` design

Package: `com.campusclaw.codingagent.mode.server` (colocated with `ChatHandler`).

Signature:

```java
public Publisher<Void> handle(WebsocketInbound in, WebsocketOutbound out, String conversationIdHint)
```

Flow inside `handle`:

1. **Resolve conversation** — use `conversationIdHint` (extracted from query string in `ServerMode`). Call `pool.getOrCreate(convId)` → `SessionRef`.
2. **Outbound sink** — `Sinks.many().multicast().onBackpressureBuffer()` of `String`. Every server-to-client frame goes through it.
3. **Subscribe to agent events** — `Runnable unsub = session.subscribe(ev -> outbound.emitNext(toJson(ev, convId), BUSY_LOOP))`. `toJson` is the local 8-branch `instanceof` cascade (independent copy, per decision).
4. **Inbound command dispatch** — `in.receive().asString().doOnNext(raw -> dispatch(raw, session, convId, outbound)).then()`.
5. **Heartbeat** — `Flux.interval(Duration.ofSeconds(20)).map(i -> "{\"type\":\"pong\"}")` merged into outbound via `Flux.merge(outbound.asFlux(), heartbeat)`.
6. **Close handling** — `in.receiveCloseStatus().doFinally(sig -> { unsub.run(); if (session.isStreaming()) session.abort(); outbound.emitComplete(BUSY_LOOP); })`.
7. **Send** — `out.sendString(merged).then()`.
8. **Combine** — `return Mono.when(inbound, send, onClose)`.

`dispatch(raw, session, convId, out)`:

- Parse `JsonNode` → read `type` and optional `id`.
- For `prompt`: emit `response success=true data={conversation_id}` immediately, then call `session.prompt(message)` (CompletableFuture). `busy` check via `session.isStreaming()` returns `response success=false error="busy"`. Errors from the future produce an `error` frame on outbound.
- For `steer`: always allowed mid-run.
- For `set_thinking_level`: wrap `ThinkingLevel.fromValue(...)` in try/catch → `response success=false error="Invalid thinking level"`.
- For unknown `type`: `response success=false error="unknown command type"`.

**Thread-safety**: `Sinks.Many` multicast is picked because three concurrent producers emit into it (agent subscribe callback, command dispatch, heartbeat interval). All `emitNext` / `emitComplete` calls use `Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(50))` to retry on `FAIL_NON_SERIALIZED` contention.

## `ServerMode` wiring change

Current:

```java
var httpHandler = RouterFunctions.toHttpHandler(routes);
var adapter = new ReactorHttpHandlerAdapter(httpHandler);

var server = HttpServer.create()
        .host(host).port(port)
        .handle(adapter)
        .bindNow();
```

New:

```java
var httpHandler = RouterFunctions.toHttpHandler(routes);
var adapter = new ReactorHttpHandlerAdapter(httpHandler);
var wsHandler = new ChatWebSocketHandler(sessionPool);

var server = HttpServer.create()
        .host(host).port(port)
        .route(r -> r
                .get("/api/ws/chat", (req, res) -> {
                    String convId = extractQueryParam(req.uri(), "conversation_id");
                    return res.sendWebsocket((in, out) -> wsHandler.handle(in, out, convId));
                })
                .route(req -> true, adapter))
        .bindNow();
```

`ReactorHttpHandlerAdapter` implements `BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>>`, which matches both `.handle(...)` and `.route(predicate, handler)` signatures — so the fallback catches every non-WS request and routes it through the existing Spring WebFlux router unchanged.

Also add one startup log line: `System.out.println("  WS     /api/ws/chat");` in the endpoint listing block, and an `extractQueryParam(String uri, String name)` helper (uses `java.net.URLDecoder` on the query segment).

## Smoke test (`ChatWebSocketHandlerTest.java`)

- Start a real Reactor Netty server on port 0 bound only to the WS route (`HttpServer.route(r -> r.get("/api/ws/chat", ...))`) — no need for full `ServerMode` wiring.
- Mock `SessionPool` + `AgentSession`. Capture the `AgentEventListener` from `session.subscribe`.
- When `session.prompt(...)` is called, synchronously fire `AgentStartEvent → MessageStartEvent → MessageUpdateEvent → MessageEndEvent → AgentEndEvent` through the captured listener and return `CompletableFuture.completedFuture(null)`.
- Connect from the test using `HttpClient.create().websocket().uri("ws://127.0.0.1:{port}/api/ws/chat")`.
- Send `{"type":"prompt","id":"t1","message":"hi"}`.
- Collect frames with `.takeUntil(frame -> type == "done")` and a 5-second timeout.
- Assertions:
  1. First frame is `{type:"response", id:"t1", success:true, ...}`.
  2. At least one of `agent_start` / `message_start` / `message_update` appears.
  3. Final frame is `{type:"done", ...}`.

This keeps the test self-contained (no `CampusClawAiService`, no `SystemPromptBuilder`, no model files) and fast (~1 second).

## Critical files read before coding

- `modules/agent-core/src/main/java/com/campusclaw/agent/event/AgentEvent.java` + sibling `*Event.java` files — confirmed record component names.
- `modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/session/AgentSession.java` — confirmed `newSession()`, `getPromptTemplates()`, `getSkillRegistry()`, `getAgent()`, `getHistory()`, etc.
- `modules/ai/src/main/java/com/campusclaw/ai/types/ThinkingLevel.java` — `fromValue(String)` confirmed, variants are `off, minimal, low, medium, high, xhigh`.
- `modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/mode/server/ChatHandler.java:100-129` — reference for SSE event mapping; semantics copied but not shared code.

## Verification

1. **Build**: `./mvnw -pl modules/coding-agent-cli -am package -DskipTests` succeeds.
2. **Format**: `./mvnw spotless:apply` leaves tree clean (per CLAUDE.md).
3. **Unit**: `./mvnw -pl modules/coding-agent-cli test -Dtest=ChatWebSocketHandlerTest` green.
4. **Full test**: `./mvnw test` stays green (no regression in existing tests). Current suite: 514 tests.
5. **Manual E2E** — see `docs/testing/ws-chat-testing.md`.
6. **AsyncAPI validate**: `npx @asyncapi/cli validate docs/asyncapi/chat-ws.yaml` — reports "File is valid" (informational hint about 3.1.0 is not a failure).
