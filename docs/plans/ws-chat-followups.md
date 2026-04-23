# WS Chat — Deferred follow-ups

Suggestions raised while reviewing the `/api/ws/chat` protocol. **None of these are bugs.** They are forward-looking extensions that need product / scope alignment before being implemented.

Items already shipped (2026-04-23):

- ✅ `done` frame carries `finalText` / `usage` / `stopReason`
- ✅ Model-level errors (`AssistantMessage.stopReason == error`) emit an additional `error` frame after `message_end`

---

## 1. Image / attachment input on the `prompt` command

**Current state.** The `prompt` / `steer` commands only accept a `message: string` field. The Java `UserMessage` record actually supports arbitrary `List<ContentBlock>`, including `ImageContent`. But the WS layer loses that expressivity — today a frontend cannot send an image.

**Proposed change.**

```jsonc
{
  "type": "prompt",
  "id": "req-1",
  "message": "What's in this picture?",
  "images": [
    { "type": "base64", "mediaType": "image/png", "data": "iVBORw0KGgo..." }
  ]
}
```

Backend builds a `UserMessage` with `TextContent` + `ImageContent` blocks, passes to `session.prompt(...)`.

**Open questions.**

- Do we limit image size / count at the WS layer, or let the model-side provider reject?
- How do we encode images: base64 inline (simple, heavy) vs a separate `/api/attachments` upload + reference id (complex, thin WS frames)?
- Same question for PDF / text attachments.

**Effort.** ~1–2h backend + `UserMessage` plumbing check, ~30min spec update.

---

## 2. Tool `tool_update` event — format contract

**Current state.** The handler serializes `ToolExecutionUpdateEvent` into a `tool_update` frame with `partialResult`. But in `agent-core`, whether this event fires at all depends on each individual tool's implementation. Right now:

- No formal contract about what `partialResult` looks like (string? structured diff?)
- Frontends cannot rely on `tool_update` happening — they see `pending → done` for most tools

**Proposed change.** Pick one of:

- **Option A (light).** Add a comment / convention: tools that stream partial results must emit strings or JSON-serializable objects. Keep `partialResult` as `any`.
- **Option B (heavy).** Introduce tagged variants like `{partialKind: "stdout", chunk: "..."}` for streaming bash output, `{partialKind: "progress", percent: 0.5}` for progress bars. Frontend can render each kind differently.

**Open questions.**

- Which tools will ever stream? Probably just `bash` (stdout/stderr lines) and long-running HTTP tools.
- Do we want the partial result aggregated (monotonically growing) or just chunks (decoder-style)?

**Effort.** A — 0 backend work, doc only. B — 1–2h per tool plus frontend rendering.

---

## 3. Bandwidth: `message_update` carries full `AssistantMessage` every frame

**Current state.** Each `message_update` retransmits the entire assistant message (all thinking + text + tool-call blocks). For long answers + extended thinking, this gets expensive quickly (quadratic-ish cost in characters transmitted).

**Why it's like this.** Matches agent-core's internal event shape. Simple for frontends — they just replace-in-place.

**Proposed change (if we see the pain).**

- **Option A.** Send deltas: `{type: "text_delta", contentIndex: 2, delta: "xxx"}`. Frontend reconstructs the message. Aligns with how upstream LLM SDKs (Anthropic, OpenAI) actually stream.
- **Option B.** Keep full-message updates, but gzip the WS frame.

**Trigger.** Revisit if any of: a single assistant message exceeds ~20 KB, network egress costs matter, users report lag on long answers.

**Effort.** A — 2–4h (handler + frontend parser rewrite). B — ~30min (enable per-message compression).

---

## 4. Streaming `usage` / cost counter

**Current state.** `usage` is exposed once at `done` time (via the final assistant message). There is no running counter during a long turn.

**Proposed change.**

- Emit a `usage` frame at each `message_end` (not just `done`), carrying the delta since last emission. Or
- Emit a `usage` frame on a fixed cadence (every N tokens / every 5s).

**Trigger.** Useful if we want a live "tokens used / estimated cost" indicator in the UI. Not urgent.

**Effort.** 30min backend, ~30min frontend.

---

## 5. Explicit `tool_call` lifecycle alignment with Message content blocks

**Current state.** When the assistant emits a `toolCall` content block in its message, and the agent-core runtime then executes it, the frontend receives:

- `message_update` — carrying the `toolCall` content block with `id`, `name`, `arguments`
- `tool_start` / `tool_end` — carrying `toolCallId`, `toolName`, `args`, `result`

**Two sources of truth with different field naming** (`id` vs `toolCallId`, `name` vs `toolName`, `arguments` vs `args`). Frontends have to deduplicate by `toolCallId`.

**Proposed change.** Either:

- **Option A.** Normalize field names in `tool_*` events to match `ToolCall` content block (`id` / `name` / `arguments`).
- **Option B.** Leave as-is and enshrine the dedup rule prominently in the spec.

**Trade-off.** Option A is cleaner but changes the wire protocol (breaking). Option B is zero cost.

**Recommendation.** Defer until we have multiple frontend consumers complaining.

---

## 6. WebSocket-level auth currently missing

**Current state.** The AsyncAPI spec mentions `bearerToken` via query parameter, but `ServerMode` does not enforce it. Local dev is wide open.

**Proposed change.** Before production:

1. Read `token` query param in `ServerMode.run`'s ws route.
2. Validate via a pluggable `TokenVerifier` (stub for now — always-true in local, JWT in production).
3. On failure, `res.status(401).send()` instead of `sendWebsocket(...)`.

**Trigger.** Required before exposing to internet.

**Effort.** 1–2h.

---

## 7. Rate limiting / per-connection frame budget

**Current state.** A misbehaving client can flood `prompt` / `steer` commands. The only protection is the `isStreaming` 409-equivalent gate.

**Proposed change.** Add a token-bucket rate limiter (e.g. max N frames/sec per connection). Or cap `prompt` size.

**Trigger.** Before production.

**Effort.** 1h.

---

## 8. Reconnect-with-history `get_history` frame size

**Current state.** `get_history` returns the full message history in one response data payload. Large sessions (hundreds of messages) could blow up the frame size.

**Proposed change.** Add pagination (`{type: "get_history", id, offset, limit}`) or lazy replay (`replay_after: <entryId>`).

**Trigger.** Only if we support long-lived sessions with many turns.

**Effort.** 1–2h backend + frontend.

---

## When to revisit

These items should be pulled into a plan one at a time, not batched. Each has its own trade-off and likely user impact. The recommended ordering if / when we prioritize:

1. **#6 auth** — blocker for production exposure
2. **#7 rate limiting** — blocker for production exposure
3. **#1 image input** — unlocks a real frontend capability
4. **#4 streaming usage** — nice-to-have for UX
5. **#3 bandwidth** — only if measurements show it matters
6. **#2 tool_update contract** — only if specific tools demand it
7. **#8 history pagination** — only for very long sessions
8. **#5 field-name normalization** — lowest priority, cosmetic
