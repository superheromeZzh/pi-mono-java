import { reactive, ref } from 'vue';
import type {
  AssistantMessage,
  ClientCommand,
  GetStateData,
  LogEntry,
  Message,
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
    // Pull fresh state so UI shows model / thinking / conv immediately.
    void getState();
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
    await send({ type: 'new_session' }, true);
    messages.value = [];
    for (const k of Object.keys(tools)) delete tools[k];
    currentAssistantIdx = -1;
    await getState();
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

async function setThinking(level: ThinkingLevel) {
  try {
    await send({ type: 'set_thinking_level', level }, true);
    await getState();
  } catch (e) {
    logEntry('err', `[set_thinking rejected] ${(e as Error).message}`);
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
  } catch (e) {
    logEntry('err', `[get_state failed] ${(e as Error).message}`);
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
  };
}
