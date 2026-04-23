<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import { useChatWs } from '../composables/useChatWs';
import AssistantBubble from './AssistantBubble.vue';

const { messages } = useChatWs();
const scrollEl = ref<HTMLElement | null>(null);

// Auto-scroll to bottom on any message change.
watch(
  () => messages.value.length,
  () => {
    void nextTick(() => {
      if (scrollEl.value) scrollEl.value.scrollTop = scrollEl.value.scrollHeight;
    });
  },
);
</script>

<template>
  <div ref="scrollEl" class="messages">
    <template v-for="(msg, idx) in messages" :key="idx">
      <!-- User bubble -->
      <div v-if="msg.kind === 'user'" class="bubble user">
        <div class="role">User</div>
        <div class="content">{{ msg.text }}</div>
      </div>

      <!-- Assistant bubble -->
      <AssistantBubble v-else-if="msg.kind === 'assistant'" :message="msg.message" />

      <!-- Meta (steer / done summary) -->
      <div v-else-if="msg.kind === 'meta'" class="bubble meta">
        <div class="role">{{ msg.label }}</div>
        <div class="content">{{ msg.text }}</div>
      </div>

      <!-- Error -->
      <div v-else-if="msg.kind === 'error'" class="bubble error">
        <div class="role">Error</div>
        <div class="content">{{ msg.text }}</div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.messages {
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}
.bubble {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
  background: var(--panel);
}
.bubble .role {
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
.bubble.user {
  border-left: 3px solid var(--user);
}
.bubble.user .role {
  color: var(--user);
}
.bubble.meta {
  border-left: 3px solid var(--warn);
}
.bubble.meta .role {
  color: var(--warn);
}
.bubble.error {
  border-left: 3px solid var(--err);
}
.bubble.error .role {
  color: var(--err);
}
.content {
  font-size: 14px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-wrap: break-word;
}
</style>
