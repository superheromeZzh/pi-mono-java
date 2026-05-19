import { reactive, ref } from 'vue';
import type {
  AssistantMessage,
  ClientCommand,
  ConversationSummary,
  GetStateData,
  ListModelsData,
  LogEntry,
  Message,
  ModelInfo,
  NamedEntry,
  ServerFrame,
  ThinkingLevel,
  ToolCallBlock,
  ToolState,
  UiMessage,
} from '../types/ws';

/** Duck-check: treat any message that is clearly not user/toolResult as
 *  assistant. Normally the backend emits {@code role:"assistant"}, but
 *  if the discriminator is missing (older deployment / bug) and the message
 *  looks structurally like an assistant message, render it anyway. */
function isAssistant(msg: Message | undefined): msg is AssistantMessage {
  if (!msg) return false;
  if (msg.role === 'assistant') return true;
  // Defensive: role missing, but content array present and not a tool/user shape
  if (msg.role == null && 'content' in msg && Array.isArray((msg as { content: unknown }).content)) {
    return !('toolCallId' in msg);
  }
  return false;
}

// ============================================================================
// Singleton module-level state. All components share one WS connection.
// ============================================================================

const ws = ref<WebSocket | null>(null);
const connected = ref(false);
const conversationId = ref<string | null>(null);
const model = ref<string | null>(null);
const thinkingLevel = ref<ThinkingLevel>('medium');
const isStreaming = ref(false);

const messages = ref<UiMessage[]>([]);
const tools = reactive<Record<string, ToolState>>({});
const eventLog = ref<LogEntry[]>([]);

// Catalogue of models the user may switch to. Populated by listModels(),
// which is called automatically once after each successful connect.
const availableModels = ref<ModelInfo[]>([]);
const modelsFiltered = ref(false);

// Persisted conversation list, fetched over plain HTTP (no WS required) so
// the picker is browsable before the user picks one to open.
const conversations = ref<ConversationSummary[]>([]);
const conversationsLoading = ref(false);

// Remember the WS URL of the most recent connect so server-side actions
// (e.g. new_session rotates the id) can refresh the conversation list
// without forcing every caller to thread the URL back through.
const lastWsUrl = ref<string | null>(null);

// Index into `messages` for the assistant bubble currently being updated by
// message_update frames. -1 means no active assistant bubble.
let currentAssistantIdx = -1;

// Correlation table for commands that carry an `id` field.
const pending = new Map<
  string,
  { resolve: (v: unknown) => void; reject: (e: Error) => void; timer: ReturnType<typeof setTimeout> }
>();
let reqSeq = 0;

// ============================================================================
// Logging
// ============================================================================

const LOG_CAP = 500;

function logEntry(dir: LogEntry['dir'], text: string) {
  eventLog.value.push({ dir, text, ts: Date.now() });
  if (eventLog.value.length > LOG_CAP) {
    eventLog.value.splice(0, eventLog.value.length - LOG_CAP);
  }
}

// ============================================================================
// Connection
// ============================================================================

function connect(url: string, convId?: string | null) {
  if (ws.value && ws.value.readyState === WebSocket.OPEN) {
    logEntry('info', '[already connected]');
    return;
  }
  lastWsUrl.value = url;
  const full = convId ? `${url}?conversation_id=${encodeURIComponent(convId)}` : url;
  logEntry('info', `[connect] ${full}`);
  let socket: WebSocket;
  try {
    socket = new WebSocket(full);
  } catch (e) {
    logEntry('err', `[connect failed] ${(e as Error).message}`);
    return;
  }
  ws.value = socket;
  socket.onopen = () => {
    connected.value = true;
    logEntry('info', '[open]');
    // Pull fresh state so UI shows model / thinking / conv immediately,
    // then ask for the available-model catalogue so the picker can render.
    // If the resumed session has prior messages on disk, pull them too so
    // the chat view rebuilds without a manual button click.
    void (async () => {
      const state = await getState();
      if (state && state.messageCount > 0) {
        await getHistory();
      }
    })();
    void listModels();
  };
  socket.onclose = (ev) => {
    connected.value = false;
    isStreaming.value = false;
    logEntry('err', `[close code=${ev.code} reason=${ev.reason || ''}]`);
  };
  socket.onerror = () => logEntry('err', '[error]');
  socket.onmessage = (ev) => onFrame(typeof ev.data === 'string' ? ev.data : String(ev.data));
}

function disconnect() {
  if (ws.value) {
    ws.value.close();
    ws.value = null;
  }
}

// ============================================================================
// Send helpers
// ============================================================================

const REQUEST_TIMEOUT_MS = 30_000;

function send<T = unknown>(cmd: ClientCommand, awaitResponse = false): Promise<T | undefined> {
  if (!ws.value || ws.value.readyState !== WebSocket.OPEN) {
    logEntry('err', '[not connected]');
    return Promise.reject(new Error('not connected'));
  }
  // All command variants declare `id?: string`, so assigning is type-safe.
  const payload: ClientCommand = awaitResponse
    ? ({ ...cmd, id: `req-${++reqSeq}` } as ClientCommand)
    : cmd;
  const raw = JSON.stringify(payload);
  ws.value.send(raw);
  logEntry('out', raw);
  if (!awaitResponse) return Promise.resolve(undefined);
  const id = (payload as { id: string }).id;
  return new Promise<T | undefined>((resolve, reject) => {
    const timer = setTimeout(() => {
      if (pending.delete(id)) reject(new Error('response timeout'));
    }, REQUEST_TIMEOUT_MS);
    pending.set(id, {
      resolve: (v) => resolve(v as T | undefined),
      reject,
      timer,
    });
  });
}

// ============================================================================
// High-level commands
// ============================================================================

async function prompt(text: string) {
  if (!text.trim()) return;
  messages.value.push({ kind: 'user', text, timestamp: Date.now() });
  isStreaming.value = true;
  try {
    await send<{ conversation_id: string }>({ type: 'prompt', message: text }, true);
  } catch (e) {
    isStreaming.value = false;
    messages.value.push({ kind: 'error', text: `prompt rejected: ${(e as Error).message}` });
  }
}

async function steer(text: string) {
  if (!text.trim()) return;
  messages.value.push({ kind: 'meta', label: 'Steer', text });
  try {
    await send({ type: 'steer', message: text }, true);
  } catch (e) {
    logEntry('err', `[steer rejected] ${(e as Error).message}`);
  }
}

async function abort() {
  try {
    await send({ type: 'abort' }, true);
  } catch (e) {
    logEntry('err', `[abort rejected] ${(e as Error).message}`);
  }
}

async function newSession() {
  try {
    const data = await send<{ conversation_id?: string }>({ type: 'new_session' }, true);
    messages.value = [];
    for (const k of Object.keys(tools)) delete tools[k];
    currentAssistantIdx = -1;
    // When server-side persistence is enabled, new_session rotates the
    // conversation_id so the cleared session writes to a fresh JSONL file.
    // Pull the rotated id straight from the response so the sidebar (and any
    // bookmarked URL) tracks the live id without waiting for agent_start.
    if (data && data.conversation_id) {
      conversationId.value = data.conversation_id;
    }
    await getState();
    // The fresh JSONL file should appear in the picker the next time the
    // user disconnects, so refresh the cached list now.
    if (lastWsUrl.value) {
      void listConversations(lastWsUrl.value);
    }
  } catch (e) {
    logEntry('err', `[new_session rejected] ${(e as Error).message}`);
  }
}

async function setModel(m: string) {
  if (!m.trim()) return;
  try {
    await send({ type: 'set_model', model: m }, true);
    await getState();
  } catch (e) {
    logEntry('err', `[set_model rejected] ${(e as Error).message}`);
  }
}

/**
 * Pull the catalogue of models the server is willing to expose.
 * Defaults to the filtered subset (settings.enabledModels) — pass {@code all}
 * to bypass and see every registered model.
 */
async function listModels(all = false) {
  try {
    const data = await send<ListModelsData>({ type: 'list_models', all }, true);
    if (data) {
      availableModels.value = Array.isArray(data.models) ? data.models : [];
      modelsFiltered.value = !!data.filtered;
      // The server's `current` is authoritative; sync it in case state drifted.
      if (data.current) model.value = data.current;
    }
  } catch (e) {
    logEntry('err', `[list_models failed] ${(e as Error).message}`);
  }
}

async function setThinking(level: ThinkingLevel) {
  try {
    await send({ type: 'set_thinking_level', level }, true);
    await getState();
  } catch (e) {
    logEntry('err', `[set_thinking rejected] ${(e as Error).message}`);
  }
}

/**
 * Translates the WS URL the user typed in the sidebar to the HTTP base used
 * by the conversation-list endpoint. We swap the scheme (ws→http / wss→https)
 * and drop the WS path so REST handlers under the same host:port can resolve.
 *
 * Falls back to the input string unchanged if the URL is unparseable, which
 * makes the picker quietly skip listing rather than throwing.
 */
function wsToHttpBase(wsUrl: string): string {
  try {
    const u = new URL(wsUrl);
    const httpProto = u.protocol === 'wss:' ? 'https:' : 'http:';
    return `${httpProto}//${u.host}`;
  } catch {
    return wsUrl;
  }
}

/**
 * Fetches the persisted conversation list from
 * {@code GET /api/conversations}. Stores the result on
 * {@link conversations} for the picker UI; safe to call before any WS
 * connection has been opened.
 */
async function listConversations(wsUrl: string) {
  const base = wsToHttpBase(wsUrl);
  conversationsLoading.value = true;
  try {
    const resp = await fetch(`${base}/api/conversations`);
    if (!resp.ok) {
      logEntry('err', `[list_conversations failed] HTTP ${resp.status}`);
      return;
    }
    const body = (await resp.json()) as { conversations?: ConversationSummary[] };
    conversations.value = Array.isArray(body.conversations) ? body.conversations : [];
    logEntry('info', `[conversations] ${conversations.value.length} entries`);
  } catch (e) {
    logEntry('err', `[list_conversations error] ${(e as Error).message}`);
  } finally {
    conversationsLoading.value = false;
  }
}

async function getState() {
  try {
    const data = await send<GetStateData>({ type: 'get_state' }, true);
    if (data) {
      conversationId.value = data.conversation_id ?? null;
      model.value = data.model ?? null;
      thinkingLevel.value = data.thinkingLevel ?? 'medium';
      isStreaming.value = !!data.isStreaming;
    }
    return data;
  } catch (e) {
    logEntry('err', `[get_state failed] ${(e as Error).message}`);
    return undefined;
  }
}

/**
 * Pull the full message history from the server and rebuild the chat view.
 *
 * <p>After a reconnect with a known {@code conversation_id}, the server will
 * have restored the JSONL-persisted history into the in-memory session, but
 * the frontend's {@code messages} array starts empty. Calling this rebuilds
 * the user/assistant bubbles so the chat looks like the user never left.
 *
 * <p>{@link ToolResultMessage} entries are skipped: tool execution state is
 * reconstructed from {@code tool_start/tool_update/tool_end} events during a
 * live turn, not from past tool result messages, so replaying them as
 * standalone bubbles would be misleading.
 */
async function listSkills(): Promise<NamedEntry[]> {
  try {
    const data = await send<{ skills: NamedEntry[] }>({ type: 'list_skills' }, true);
    return Array.isArray(data?.skills) ? data!.skills : [];
  } catch (e) {
    logEntry('err', `[list_skills failed] ${(e as Error).message}`);
    throw e;
  }
}

async function getPromptTemplates(): Promise<NamedEntry[]> {
  try {
    const data = await send<{ templates: NamedEntry[] }>({ type: 'get_prompt_templates' }, true);
    return Array.isArray(data?.templates) ? data!.templates : [];
  } catch (e) {
    logEntry('err', `[get_prompt_templates failed] ${(e as Error).message}`);
    throw e;
  }
}

/** Inserts a system bubble (slash-command output, local notice, etc) into the chat. Does not hit the WS. */
function pushSystem(label: string, text: string) {
  messages.value.push({ kind: 'system', label, text });
}

/** Locally clears the chat view without touching server state. Used by /clear. */
function clearMessages() {
  messages.value = [];
  for (const k of Object.keys(tools)) delete tools[k];
  currentAssistantIdx = -1;
}

async function getHistory() {
  try {
    const data = await send<{ messages: Message[] }>({ type: 'get_history' }, true);
    if (!data || !Array.isArray(data.messages)) return;
    const rebuilt: UiMessage[] = [];
    for (const msg of data.messages) {
      if (msg.role === 'user') {
        const text = Array.isArray(msg.content)
          ? msg.content
              .filter((b): b is { type: 'text'; text: string } =>
                !!b && (b as { type?: string }).type === 'text')
              .map((b) => b.text)
              .join('')
          : '';
        rebuilt.push({ kind: 'user', text, timestamp: msg.timestamp ?? Date.now() });
      } else if (isAssistant(msg)) {
        rebuilt.push({ kind: 'assistant', message: msg });
      }
    }
    messages.value = rebuilt;
    currentAssistantIdx = -1;
    logEntry('info', `[history loaded] ${rebuilt.length} messages`);
  } catch (e) {
    logEntry('err', `[get_history failed] ${(e as Error).message}`);
  }
}

// ============================================================================
// Frame dispatch
// ============================================================================

function onFrame(raw: string) {
  logEntry('in', raw);
  let frame: ServerFrame;
  try {
    frame = JSON.parse(raw) as ServerFrame;
  } catch {
    logEntry('err', '[bad json]');
    return;
  }

  // Correlated response
  if (frame.type === 'response') {
    if (frame.id) {
      const entry = pending.get(frame.id);
      if (entry) {
        clearTimeout(entry.timer);
        pending.delete(frame.id);
        if (frame.success) entry.resolve(frame.data);
        else entry.reject(new Error(frame.error || 'unknown error'));
      }
    }
    return;
  }

  switch (frame.type) {
    case 'agent_start':
      if (frame.conversation_id) conversationId.value = frame.conversation_id;
      isStreaming.value = true;
      return;

    case 'message_start':
      if (isAssistant(frame.message)) {
        messages.value.push({ kind: 'assistant', message: frame.message });
        currentAssistantIdx = messages.value.length - 1;
      }
      // toolResult messages are rendered via tool_end, ignore here.
      return;

    case 'message_update': {
      if (!isAssistant(frame.message)) return;
      if (currentAssistantIdx < 0) {
        messages.value.push({ kind: 'assistant', message: frame.message });
        currentAssistantIdx = messages.value.length - 1;
      } else {
        // Replace-in-place: each update carries the full current AssistantMessage.
        messages.value[currentAssistantIdx] = { kind: 'assistant', message: frame.message };
      }
      syncToolsFromAssistant(frame.message);
      return;
    }

    case 'message_end': {
      if (isAssistant(frame.message)) {
        if (currentAssistantIdx >= 0) {
          messages.value[currentAssistantIdx] = { kind: 'assistant', message: frame.message };
        } else {
          // Defensive: message_end arrived without a preceding message_start that
          // pushed a bubble (rare but possible with aggressive event batching).
          messages.value.push({ kind: 'assistant', message: frame.message });
        }
        syncToolsFromAssistant(frame.message);
      }
      currentAssistantIdx = -1;
      return;
    }

    case 'tool_start':
      upsertTool(frame.toolCallId, frame.toolName, frame.args, 'running');
      return;

    case 'tool_update': {
      const t = upsertTool(frame.toolCallId, frame.toolName, frame.args, 'running');
      t.partialResult = frame.partialResult;
      return;
    }

    case 'tool_end': {
      const t = upsertTool(frame.toolCallId, frame.toolName, undefined, frame.isError ? 'error' : 'done');
      t.result = frame.result;
      t.isError = frame.isError;
      return;
    }

    case 'done':
      isStreaming.value = false;
      if (frame.stopReason) {
        const parts: string[] = [`stop: ${frame.stopReason}`];
        if (frame.usage) {
          const u = frame.usage;
          const segs: string[] = [];
          if (typeof u.inputTokens === 'number') segs.push(`in=${u.inputTokens}`);
          if (typeof u.outputTokens === 'number') segs.push(`out=${u.outputTokens}`);
          if (segs.length) parts.push(`usage(${segs.join(', ')})`);
        }
        messages.value.push({ kind: 'meta', label: 'done', text: parts.join(' · ') });
      }
      return;

    case 'error':
      isStreaming.value = false;
      messages.value.push({ kind: 'error', text: frame.error });
      return;

    case 'pong':
      return;
  }
}

// ============================================================================
// Helpers
// ============================================================================

/** Make sure the tools map has entries for every toolCall block in the current
 *  assistant message. This handles the case where the toolCall content block
 *  arrives via message_update BEFORE tool_start fires. */
function syncToolsFromAssistant(message: AssistantMessage) {
  for (const block of message.content ?? []) {
    if (block.type === 'toolCall') {
      const tc = block as ToolCallBlock;
      if (!tools[tc.id]) {
        tools[tc.id] = {
          toolCallId: tc.id,
          name: tc.name,
          args: tc.arguments,
          status: 'pending',
        };
      } else {
        // Keep args in sync as they stream in.
        tools[tc.id].args = tc.arguments;
        if (!tools[tc.id].name && tc.name) tools[tc.id].name = tc.name;
      }
    }
  }
}

function upsertTool(id: string, name: string | undefined, args: unknown, status: ToolState['status']): ToolState {
  const existing = tools[id];
  if (existing) {
    if (name) existing.name = name;
    if (args !== undefined) existing.args = args;
    existing.status = status;
    return existing;
  }
  const fresh: ToolState = {
    toolCallId: id,
    name: name ?? '',
    args,
    status,
  };
  tools[id] = fresh;
  return fresh;
}

// ============================================================================
// Public composable
// ============================================================================

export function useChatWs() {
  // Refs returned directly (not wrapped in `readonly()`) so that nested
  // types (e.g. AssistantMessage.content) stay mutable — components need to
  // pass them down as props that accept non-readonly arrays. Consumers are
  // expected to go through the action functions rather than mutate refs.
  return {
    connected,
    conversationId,
    model,
    thinkingLevel,
    isStreaming,
    messages,
    eventLog,
    tools, // reactive; components read by id
    availableModels,
    modelsFiltered,
    conversations,
    conversationsLoading,

    // actions
    connect,
    disconnect,
    prompt,
    steer,
    abort,
    newSession,
    setModel,
    setThinking,
    getState,
    getHistory,
    listModels,
    listConversations,
    listSkills,
    getPromptTemplates,
    pushSystem,
    clearMessages,
  };
}
