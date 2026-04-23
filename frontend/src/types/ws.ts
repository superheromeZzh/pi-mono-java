/**
 * TypeScript types for the /api/ws/chat WebSocket protocol.
 *
 * Mirrors docs/asyncapi/chat-ws.yaml and the Java records under
 * modules/ai/src/main/java/com/campusclaw/ai/types/.
 *
 * All wire frames are JSON objects with a required `type` discriminator.
 */

export type ThinkingLevel = 'off' | 'minimal' | 'low' | 'medium' | 'high' | 'xhigh';

export type StopReason = 'stop' | 'length' | 'toolUse' | 'error' | 'aborted';

/** Token usage and cost for an assistant turn. Pass-through from the Java `Usage` record — tolerate extra keys. */
export interface Usage {
  inputTokens?: number;
  outputTokens?: number;
  cacheCreationTokens?: number;
  cacheReadTokens?: number;
  totalTokens?: number;
  cost?: { input?: number; output?: number; total?: number } | number;
  [key: string]: unknown;
}

// ============================================================================
// Content blocks inside an assistant / user message
// ============================================================================

export interface ThinkingBlock {
  type: 'thinking';
  thinking: string;
  thinkingSignature?: string | null;
  redacted: boolean;
}

export interface TextBlock {
  type: 'text';
  text: string;
  textSignature?: string | null;
}

export interface ToolCallBlock {
  type: 'toolCall';
  id: string;
  name: string;
  arguments: Record<string, unknown>;
  thoughtSignature?: string | null;
}

export interface ImageBlock {
  type: 'image';
  [key: string]: unknown;
}

export type ContentBlock = ThinkingBlock | TextBlock | ToolCallBlock | ImageBlock;

// ============================================================================
// Wire-level Message shapes (as Jackson-serialized on the server)
// ============================================================================

export interface UserMessage {
  role: 'user';
  content: ContentBlock[];
  timestamp: number;
}

export interface AssistantMessage {
  role: 'assistant';
  content: ContentBlock[];
  api?: string;
  provider?: string;
  model?: string;
  responseId?: string | null;
  usage?: Usage;
  stopReason?: StopReason;
  errorMessage?: string | null;
  timestamp?: number;
}

export interface ToolResultMessage {
  role: 'toolResult';
  toolCallId: string;
  toolName: string;
  content: ContentBlock[];
  details?: unknown;
  isError: boolean;
  timestamp: number;
}

export type Message = UserMessage | AssistantMessage | ToolResultMessage;

// ============================================================================
// Client → server commands
// ============================================================================

export type ClientCommand =
  | { type: 'prompt'; id?: string; message: string }
  | { type: 'steer'; id?: string; message: string }
  | { type: 'abort'; id?: string }
  | { type: 'new_session'; id?: string }
  | { type: 'set_model'; id?: string; model: string }
  | { type: 'set_thinking_level'; id?: string; level: ThinkingLevel }
  | { type: 'get_state'; id?: string }
  | { type: 'get_history'; id?: string }
  | { type: 'get_prompt_templates'; id?: string }
  | { type: 'list_skills'; id?: string }
  | { type: 'ping' };

export type ClientCommandType = ClientCommand['type'];

// ============================================================================
// Server → client frames
// ============================================================================

/** Synchronous response to a command that carried an `id`. */
export interface ResponseFrame {
  type: 'response';
  id?: string;
  success: boolean;
  data?: unknown;
  error?: string;
}

/** Data shape for `response` to `get_state`. */
export interface GetStateData {
  conversation_id: string;
  isStreaming: boolean;
  model: string;
  thinkingLevel: ThinkingLevel;
  messageCount: number;
}

// Event frames (asynchronous, pushed during an agent turn)
export type ServerEvent =
  | { type: 'agent_start'; conversation_id?: string }
  | { type: 'message_start'; conversation_id?: string; message?: Message }
  | { type: 'message_update'; message: Message }
  | { type: 'message_end'; message?: Message }
  | { type: 'tool_start'; toolCallId: string; toolName: string; args?: unknown }
  | { type: 'tool_update'; toolCallId: string; toolName: string; args?: unknown; partialResult?: unknown }
  | { type: 'tool_end'; toolCallId: string; toolName: string; isError: boolean; result?: unknown }
  | {
      type: 'done';
      conversation_id?: string;
      finalText?: string;
      usage?: Usage;
      stopReason?: StopReason;
    }
  | { type: 'error'; error: string; conversation_id?: string }
  | { type: 'pong' };

export type ServerFrame = ResponseFrame | ServerEvent;

// ============================================================================
// UI-only state (not on the wire)
// ============================================================================

/** A user-visible bubble in the chat log. */
export type UiMessage =
  | { kind: 'user'; text: string; timestamp: number }
  | { kind: 'assistant'; message: AssistantMessage }
  | { kind: 'meta'; label: string; text: string }
  | { kind: 'error'; text: string };

export interface ToolState {
  toolCallId: string;
  name: string;
  args: unknown;
  status: 'pending' | 'running' | 'done' | 'error';
  partialResult?: unknown;
  result?: unknown;
  isError?: boolean;
}

export interface LogEntry {
  dir: 'in' | 'out' | 'err' | 'info';
  text: string;
  ts: number;
}
