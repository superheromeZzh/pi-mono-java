# CampusClaw WS Frontend

Vue 3 + TypeScript + Vite client for the `/api/ws/chat` WebSocket endpoint.

Protocol contract: [`../docs/asyncapi/chat-ws.yaml`](../docs/asyncapi/chat-ws.yaml).

Renders assistant output in TUI-like style:

- 💭 Thinking — collapsible reasoning blocks
- 📝 Text — streaming assistant response
- 🔧 Tool call — card with live status (`pending → running → done / error`) and final result

Supports all 11 client commands exposed by the backend (`prompt` / `steer` / `abort` / `new_session` / `set_model` / `set_thinking_level` / `get_state` / `get_history` / `get_prompt_templates` / `list_skills` / `ping`).

## Setup

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
```

## Run the backend first

```bash
# repo root
./campusclaw.sh --mode server --port 3000 -m glm-5
```

The frontend defaults to `ws://localhost:3000/api/ws/chat`. Change via the sidebar "WS URL" field, or uncomment the `proxy` block in `vite.config.ts` to route through Vite.

## Useful scripts

| Command | Purpose |
|---|---|
| `npm run dev` | Vite dev server with HMR |
| `npm run build` | `vue-tsc --noEmit` + production bundle to `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run typecheck` | Run `vue-tsc --noEmit` without building |

## Code layout

```
src/
├── main.ts                     # app bootstrap
├── App.vue                     # page shell (header + sidebar + messages + input)
├── style.css                   # shared CSS variables (dark TUI theme)
├── env.d.ts                    # Vite + .vue SFC typing
├── types/
│   └── ws.ts                   # TS types mirroring docs/asyncapi/chat-ws.yaml
├── composables/
│   └── useChatWs.ts            # singleton WebSocket state, command senders, frame dispatch
└── components/
    ├── MessageList.vue         # scroll area, delegates bubble rendering by role
    ├── AssistantBubble.vue     # renders thinking / text / toolCall content blocks
    └── ToolCallCard.vue        # tool call card wired to reactive tool state
```

## How state flows

1. `useChatWs()` holds a **singleton** WebSocket and a few refs (`connected`, `messages`, `tools`, `isStreaming`, `model`, `thinkingLevel`, `conversationId`, `eventLog`).
2. `App.vue` binds sidebar inputs to the composable's actions (`connect`, `prompt`, `setModel`, …).
3. `MessageList` reads `messages` and renders `UserBubble` / `AssistantBubble` / meta bubbles.
4. `AssistantBubble` walks `message.content[]` and renders each block by `type`. When a block is `toolCall`, it delegates to `ToolCallCard`.
5. `ToolCallCard` looks up the tool's live state from `tools[toolCallId]`, so updates from `tool_start` / `tool_update` / `tool_end` events show up even though the card lives inside a message that re-renders on every `message_update`.

## Adapting for production

- Replace `ws://localhost:3000/api/ws/chat` in `App.vue` with your real host.
- Add a bearer token on handshake: `ws://…/api/ws/chat?conversation_id=…&token=<JWT>` (backend must verify — see `docs/plans/ws-chat-followups.md` §6).
- Frontend does no markdown rendering yet — text is plain. Drop in `marked` or `markdown-it` if you need code blocks / lists.
- No reconnect logic. Add exponential backoff in `useChatWs.connect()` if you need it.
