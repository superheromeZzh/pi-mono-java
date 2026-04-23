<script setup lang="ts">
import { computed, ref } from 'vue';
import { useChatWs } from './composables/useChatWs';
import MessageList from './components/MessageList.vue';
import type { ThinkingLevel } from './types/ws';

const chat = useChatWs();

// ----- sidebar state (local) -----
const wsUrl = ref('ws://localhost:3000/api/ws/chat');
const convInput = ref('');
const modelInput = ref('');
const levelInput = ref<ThinkingLevel>('medium');

// ----- input controls -----
const promptText = ref('');
const thinkingLevels: ThinkingLevel[] = ['off', 'minimal', 'low', 'medium', 'high', 'xhigh'];

const connStatus = computed(() => (chat.connected.value ? 'connected' : 'disconnected'));
const streamStatus = computed(() => (chat.isStreaming.value ? 'streaming' : 'idle'));

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
  void chat.setModel(modelInput.value.trim());
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
      <label
        >Model
        <input v-model="modelInput" type="text" placeholder="e.g. glm-5 / sonnet" />
      </label>
      <button @click="onApplyModel" :disabled="!chat.connected.value">Apply model</button>

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
