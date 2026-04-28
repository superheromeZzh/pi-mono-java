<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useChatWs } from './composables/useChatWs';
import MessageList from './components/MessageList.vue';
import type { ThinkingLevel } from './types/ws';

const chat = useChatWs();

// ----- sidebar state (local) -----
const wsUrl = ref('ws://localhost:3000/api/ws/chat');
const convInput = ref('');
const levelInput = ref<ThinkingLevel>('medium');

// Sentinel value in the model <select> that opens a free-text input —
// for switching to ids the catalogue doesn't surface (custom endpoints,
// bypassing enabledModels, etc).
const CUSTOM_MODEL = '__custom__';
const modelSelected = ref<string>('');
const modelManual = ref('');

// Keep <select> in sync with the live model id reported by the server.
// If the current model isn't in availableModels we fall through to the
// custom-input option so the user can still see what's active.
watch(
  [() => chat.model.value, () => chat.availableModels.value.length],
  ([current]) => {
    if (!current) {
      if (modelSelected.value !== CUSTOM_MODEL) modelSelected.value = '';
      return;
    }
    const found = chat.availableModels.value.some((m) => m.id === current);
    if (found) {
      modelSelected.value = current;
    } else if (modelSelected.value !== CUSTOM_MODEL) {
      // Server reports a model that isn't in our catalogue → expose a manual entry.
      modelSelected.value = CUSTOM_MODEL;
      if (!modelManual.value) modelManual.value = current;
    }
  },
  { immediate: true },
);

// ----- input controls -----
const promptText = ref('');
const thinkingLevels: ThinkingLevel[] = ['off', 'minimal', 'low', 'medium', 'high', 'xhigh'];

const connStatus = computed(() => (chat.connected.value ? 'connected' : 'disconnected'));
const streamStatus = computed(() => (chat.isStreaming.value ? 'streaming' : 'idle'));
const isCustomModel = computed(() => modelSelected.value === CUSTOM_MODEL);

const usableModelsCount = computed(
  () => chat.availableModels.value.filter((m) => m.hasCredentials !== false).length,
);
const totalModelsCount = computed(() => chat.availableModels.value.length);

function modelLabel(m: {
  name: string;
  provider: string;
  reasoning?: boolean;
  hasCredentials?: boolean;
}) {
  const reasoning = m.reasoning ? ' ✦' : '';
  // Older servers may omit hasCredentials → treat absence as true (back-compat).
  const lock = m.hasCredentials === false ? '🔒 ' : '';
  return `${lock}${m.name} (${m.provider})${reasoning}`;
}

function onConnect() {
  chat.connect(wsUrl.value, convInput.value.trim() || null);
}
function onDisconnect() {
  chat.disconnect();
}
function onPrompt() {
  const t = promptText.value.trim();
  if (!t) return;
  promptText.value = '';
  void chat.prompt(t);
}
function onSteer() {
  const t = promptText.value.trim();
  if (!t) return;
  promptText.value = '';
  void chat.steer(t);
}
function onPromptKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
    e.preventDefault();
    onPrompt();
  }
}

function onApplyModel() {
  const target = isCustomModel.value ? modelManual.value.trim() : modelSelected.value.trim();
  if (!target) return;
  void chat.setModel(target);
}
function onRefreshModels(all = false) {
  void chat.listModels(all);
}
function onApplyThinking() {
  void chat.setThinking(levelInput.value);
}

function logDirClass(dir: 'in' | 'out' | 'err' | 'info') {
  return dir;
}
function logPrefix(dir: 'in' | 'out' | 'err' | 'info') {
  if (dir === 'in') return '◀ ';
  if (dir === 'out') return '▶ ';
  if (dir === 'err') return '! ';
  return '· ';
}
</script>

<template>
  <header>
    <div class="dot" :class="{ on: chat.connected.value }"></div>
    <strong>CampusClaw WS</strong>
    <span class="sep">|</span>
    <span class="status">conv: {{ chat.conversationId.value ?? '-' }}</span>
    <span class="sep">|</span>
    <span class="status">model: {{ chat.model.value ?? '-' }}</span>
    <span class="sep">|</span>
    <span class="status">thinking: {{ chat.thinkingLevel.value }}</span>
    <span class="sep">|</span>
    <span class="status">{{ streamStatus }}</span>
    <span class="spacer"></span>
    <button @click="chat.getState()" :disabled="!chat.connected.value">Refresh</button>
    <button class="danger" @click="chat.newSession()" :disabled="!chat.connected.value">New session</button>
    <span class="conn-text">{{ connStatus }}</span>
  </header>

  <main class="layout">
    <MessageList />

    <aside>
      <h3>Connection</h3>
      <label
        >WS URL
        <input v-model="wsUrl" type="text" />
      </label>
      <label
        >conversation_id (resume)
        <input v-model="convInput" type="text" placeholder="(空 = 新建)" />
      </label>
      <div class="row">
        <button style="flex: 1" @click="onConnect" :disabled="chat.connected.value">Connect</button>
        <button style="flex: 1" class="danger" @click="onDisconnect" :disabled="!chat.connected.value">
          Disconnect
        </button>
      </div>

      <h3>Model / Thinking</h3>
      <label>
        Model
        <select v-model="modelSelected" :disabled="!chat.connected.value">
          <option v-if="!chat.availableModels.value.length" value="" disabled>
            (no catalog yet)
          </option>
          <option
            v-for="m in chat.availableModels.value"
            :key="`${m.provider}/${m.id}`"
            :value="m.id"
            :disabled="m.hasCredentials === false"
            :title="
              m.hasCredentials === false
                ? `No API key resolvable for ${m.provider}. Run /auth login ${m.provider} <key> in the CLI, set provider.${m.provider}.apiKey in settings.json, or export the matching env var.`
                : ''
            "
          >
            {{ modelLabel(m) }}
          </option>
          <option :value="CUSTOM_MODEL">Custom id…</option>
        </select>
      </label>
      <label v-if="isCustomModel">
        Custom model id
        <input v-model="modelManual" type="text" placeholder="e.g. glm-5 / sonnet" />
      </label>
      <div class="row">
        <button style="flex: 1" @click="onApplyModel" :disabled="!chat.connected.value">
          Apply model
        </button>
        <button
          style="flex: 0 0 auto"
          @click="onRefreshModels(false)"
          :disabled="!chat.connected.value"
          :title="chat.modelsFiltered.value ? 'Refresh (filtered by enabledModels)' : 'Refresh catalog'"
        >
          ↻
        </button>
      </div>
      <label v-if="totalModelsCount" class="hint">
        <template v-if="chat.modelsFiltered.value">
          Showing {{ totalModelsCount }} models filtered by
          <code>settings.enabledModels</code>;
          <a href="#" @click.prevent="onRefreshModels(true)">show all</a>.
        </template>
        <template v-if="usableModelsCount < totalModelsCount">
          🔒 {{ totalModelsCount - usableModelsCount }} of {{ totalModelsCount }} need an
          API key — set one via <code>/auth login &lt;provider&gt; &lt;key&gt;</code> in
          the CLI, or export the matching env var.
        </template>
      </label>

      <label
        >Thinking level
        <select v-model="levelInput">
          <option v-for="lv in thinkingLevels" :key="lv" :value="lv">{{ lv }}</option>
        </select>
      </label>
      <button @click="onApplyThinking" :disabled="!chat.connected.value">Apply thinking</button>

      <h3>Event log</h3>
      <div class="log">
        <div v-for="(e, idx) in chat.eventLog.value" :key="idx" :class="logDirClass(e.dir)">
          {{ logPrefix(e.dir) }}{{ e.text }}
        </div>
      </div>
    </aside>
  </main>

  <footer class="controls">
    <textarea
      v-model="promptText"
      placeholder="输入消息，Cmd/Ctrl+Enter 发送；运行中可点 Steer 调整方向"
      @keydown="onPromptKeydown"
    ></textarea>
    <div class="btn-col">
      <button class="primary" @click="onPrompt" :disabled="!chat.connected.value">Prompt</button>
      <button @click="onSteer" :disabled="!chat.connected.value">Steer</button>
      <button class="danger" @click="chat.abort()" :disabled="!chat.connected.value">Abort</button>
    </div>
  </footer>
</template>

<style scoped>
header {
  padding: 10px 16px;
  background: var(--panel);
  border-bottom: 1px solid var(--border);
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 13px;
}
header .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--err);
}
header .dot.on {
  background: var(--accent);
}
header .sep {
  color: var(--muted);
}
header .status {
  color: var(--muted);
}
header .spacer {
  flex: 1;
}
header .conn-text {
  color: var(--muted);
  font-size: 11px;
  min-width: 80px;
  text-align: right;
}

.layout {
  display: grid;
  grid-template-columns: 1fr 320px;
  flex: 1;
  min-height: 0;
}
.layout :deep(.messages) {
  border-right: 1px solid var(--border);
}

aside {
  background: var(--panel);
  border-left: 1px solid var(--border);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  font-size: 13px;
  min-height: 0;
}
aside h3 {
  margin: 0;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--muted);
}
aside label {
  display: block;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 4px;
}
aside label input,
aside label select {
  width: 100%;
  margin-top: 4px;
  display: block;
}
aside label.hint {
  font-size: 11px;
  color: var(--muted);
  line-height: 1.5;
}
aside label.hint a {
  color: var(--accent);
}
aside label.hint code {
  font-family: ui-monospace, monospace;
  background: var(--bg);
  padding: 1px 4px;
  border-radius: 3px;
}
.row {
  display: flex;
  gap: 6px;
}

.log {
  flex: 1;
  min-height: 120px;
  overflow-y: auto;
  font-family: ui-monospace, monospace;
  font-size: 11px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 8px;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--muted);
}
.log .in {
  color: var(--accent);
}
.log .out {
  color: var(--user);
}
.log .err {
  color: var(--err);
}
.log .info {
  color: var(--muted);
}

.controls {
  padding: 10px 16px;
  background: var(--panel);
  border-top: 1px solid var(--border);
  display: flex;
  gap: 8px;
  align-items: stretch;
}
.controls textarea {
  flex: 1;
  resize: none;
  min-height: 56px;
  max-height: 180px;
  line-height: 1.45;
  padding: 10px;
  font-size: 14px;
}
.btn-col {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
