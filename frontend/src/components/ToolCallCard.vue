<script setup lang="ts">
import { computed } from 'vue';
import { useChatWs } from '../composables/useChatWs';

const props = defineProps<{
  /** The toolCall content block id (matches ToolState.toolCallId) */
  toolCallId: string;
  /** Fallback name / args if no tool state yet */
  fallbackName: string;
  fallbackArgs: unknown;
}>();

const { tools } = useChatWs();

const state = computed(() => tools[props.toolCallId]);

const status = computed(() => state.value?.status ?? 'pending');
const name = computed(() => state.value?.name || props.fallbackName || 'tool');
const args = computed(() => state.value?.args ?? props.fallbackArgs);

const formattedArgs = computed(() => format(args.value));
const formattedResult = computed(() => {
  const s = state.value;
  if (!s) return null;
  if (s.result !== undefined) return format(s.result);
  if (s.partialResult !== undefined) return `(streaming) ${format(s.partialResult)}`;
  return null;
});

function format(v: unknown): string {
  if (v == null) return '';
  if (typeof v === 'string') return v;
  try {
    return JSON.stringify(v, null, 2);
  } catch {
    return String(v);
  }
}
</script>

<template>
  <div class="toolcall" :data-tool-id="toolCallId">
    <div class="header">
      <span>🔧 {{ name }}</span>
      <span class="status" :class="status">{{ status }}</span>
    </div>
    <pre v-if="formattedArgs">{{ formattedArgs }}</pre>
    <div v-if="formattedResult" class="result" :class="{ error: state?.isError }">
      {{ (state?.isError ? '✖ ' : '✓ ') + formattedResult }}
    </div>
  </div>
</template>

<style scoped>
.toolcall {
  background: var(--tool-bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  margin: 8px 0;
  padding: 8px 12px;
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-size: 12px;
}
.header {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--tool);
  font-weight: 600;
}
.status {
  margin-left: auto;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  padding: 1px 6px;
  border: 1px solid var(--border);
  border-radius: 3px;
  color: var(--muted);
}
.status.pending {
  color: var(--muted);
}
.status.running {
  color: var(--warn);
  border-color: var(--warn);
}
.status.done {
  color: var(--accent);
  border-color: var(--accent);
}
.status.error {
  color: var(--err);
  border-color: var(--err);
}
pre {
  margin: 6px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text);
  background: rgba(0, 0, 0, 0.25);
  padding: 6px 8px;
  border-radius: 4px;
  font-family: inherit;
  font-size: inherit;
}
.result {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--border);
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
}
.result.error {
  color: var(--err);
}
</style>
