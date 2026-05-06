<script setup lang="ts">
import { computed } from 'vue';
import type { ConversationSummary } from '../types/ws';

const props = defineProps<{
  conversations: ConversationSummary[];
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: 'open', id: string): void;
  (e: 'refresh'): void;
  (e: 'new'): void;
}>();

const isEmpty = computed(() => !props.loading && props.conversations.length === 0);

function relativeTime(iso: string): string {
  const ts = Date.parse(iso);
  if (Number.isNaN(ts)) return iso;
  const diffMs = Date.now() - ts;
  const sec = Math.round(diffMs / 1000);
  if (sec < 60) return `${sec}s ago`;
  const min = Math.round(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr}h ago`;
  const d = Math.round(hr / 24);
  if (d < 30) return `${d}d ago`;
  return new Date(ts).toLocaleDateString();
}
</script>

<template>
  <section class="picker">
    <header class="picker-header">
      <div>
        <h2>Conversations</h2>
        <span class="hint">{{ conversations.length }} saved · click any to open</span>
      </div>
      <div class="picker-actions">
        <button @click="emit('new')" class="primary">+ New conversation</button>
        <button @click="emit('refresh')" :disabled="loading" :title="loading ? 'Loading…' : 'Refresh list'">
          ↻
        </button>
      </div>
    </header>

    <div v-if="loading" class="state">Loading…</div>
    <div v-else-if="isEmpty" class="state empty">
      <p>No saved conversations yet for this working directory.</p>
      <p class="hint">
        Click <strong>+ New conversation</strong> above to start one — it'll be persisted to
        <code>~/.campusclaw/agent/sessions/</code>.
      </p>
    </div>

    <ul v-else class="list">
      <li
        v-for="c in conversations"
        :key="c.id"
        class="row"
        @click="emit('open', c.id)"
        tabindex="0"
        @keydown.enter="emit('open', c.id)"
        @keydown.space.prevent="emit('open', c.id)"
      >
        <div class="title">{{ c.title }}</div>
        <div class="meta">
          <span>{{ c.messageCount }} {{ c.messageCount === 1 ? 'msg' : 'msgs' }}</span>
          <span class="dot">·</span>
          <span :title="c.updatedAt">{{ relativeTime(c.updatedAt) }}</span>
          <span class="dot">·</span>
          <code class="id">{{ c.id }}</code>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.picker {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background: var(--bg);
}

.picker-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border);
  padding-bottom: 12px;
}

.picker-header h2 {
  margin: 0;
  font-size: 18px;
}

.picker-actions {
  display: flex;
  gap: 8px;
}

.hint {
  color: var(--muted);
  font-size: 12px;
}

.state {
  padding: 32px;
  text-align: center;
  color: var(--muted);
}

.state.empty p {
  margin: 4px 0;
}

.state code {
  background: var(--panel);
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 12px;
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.row {
  cursor: pointer;
  padding: 12px 14px;
  border-radius: 6px;
  background: var(--panel);
  border: 1px solid var(--border);
  transition: background 0.12s, border-color 0.12s;
}

.row:hover,
.row:focus {
  background: var(--panel-hover, #2a2a2a);
  border-color: var(--accent, #4ade80);
  outline: none;
}

.title {
  font-size: 14px;
  margin-bottom: 4px;
  color: var(--fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 11px;
  color: var(--muted);
}

.id {
  font-family: monospace;
  background: rgba(255, 255, 255, 0.05);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 10px;
}

.dot {
  opacity: 0.5;
}
</style>
