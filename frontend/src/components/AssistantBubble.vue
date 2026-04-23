<script setup lang="ts">
import type { AssistantMessage } from '../types/ws';
import ToolCallCard from './ToolCallCard.vue';

defineProps<{ message: AssistantMessage }>();
</script>

<template>
  <div class="bubble assistant">
    <div class="role">Assistant</div>
    <div class="body">
      <template v-for="(block, idx) in message.content" :key="idx">
        <!-- Thinking -->
        <details v-if="block.type === 'thinking'" class="thinking" open>
          <summary>💭 Thinking{{ block.redacted ? ' (redacted)' : '' }}</summary>
          <div>{{ block.thinking }}</div>
        </details>

        <!-- Text -->
        <div v-else-if="block.type === 'text'" class="text">
          {{ block.text }}
        </div>

        <!-- Tool call -->
        <ToolCallCard
          v-else-if="block.type === 'toolCall'"
          :tool-call-id="block.id"
          :fallback-name="block.name"
          :fallback-args="block.arguments"
        />

        <!-- Image / unknown — ignored in this minimal client -->
      </template>
    </div>
  </div>
</template>

<style scoped>
.bubble {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
  background: var(--panel);
  border-left: 3px solid var(--accent);
}
.role {
  font-size: 11px;
  color: var(--accent);
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
.body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.text {
  font-size: 14px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-wrap: break-word;
}
.thinking {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--border);
  border-radius: 6px;
  margin: 4px 0;
  padding: 8px 12px;
  color: var(--thinking);
  font-size: 12px;
  font-style: italic;
  white-space: pre-wrap;
}
.thinking summary {
  cursor: pointer;
  color: var(--muted);
  font-style: normal;
  outline: none;
}
.thinking summary::-webkit-details-marker {
  color: var(--muted);
}
.thinking div {
  margin-top: 6px;
}
</style>
