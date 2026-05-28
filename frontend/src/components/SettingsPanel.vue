<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useSettings } from '../composables/useSettings';
import type { CustomModelConfig } from '../types/settings';

const props = defineProps<{
  wsUrl: string;
  open: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const settings = useSettings();

// Editable copies of the server snapshot. We don't bind directly to
// `snapshot.value.customModels` because the user may make multiple edits
// before clicking Save — we need a working draft separate from the loaded
// state.
const defaultModelDraft = ref<string>('');
const customDraft = ref<CustomModelConfig[]>([]);

// Re-pull whenever the panel opens or the WS URL behind it changes.
watch(
  () => [props.open, props.wsUrl] as const,
  async ([open, url]) => {
    if (!open || !url) return;
    const snap = await settings.refresh(url);
    if (snap) {
      defaultModelDraft.value = snap.defaultModel ?? '';
      customDraft.value = snap.customModels.map(cloneCustom);
    }
  },
  { immediate: true },
);

function cloneCustom(c: CustomModelConfig): CustomModelConfig {
  return {
    id: c.id,
    name: c.name ?? '',
    api: c.api,
    baseUrl: c.baseUrl,
    apiKey: c.apiKey,
    contextWindow: c.contextWindow ?? null,
    maxTokens: c.maxTokens ?? null,
    reasoning: c.reasoning ?? false,
    inputModalities: c.inputModalities ?? null,
    thinkingFormat: c.thinkingFormat ?? null,
  };
}

function addCustomRow() {
  customDraft.value.push({
    id: '',
    name: '',
    api: 'openai-completions',
    baseUrl: '',
    apiKey: '',
    contextWindow: null,
    maxTokens: null,
    reasoning: false,
    inputModalities: null,
    thinkingFormat: null,
  });
}

function removeCustomRow(idx: number) {
  customDraft.value.splice(idx, 1);
}

const hasDefaultModelChange = computed(
  () => (settings.snapshot.value?.defaultModel ?? '') !== defaultModelDraft.value,
);

const hasCustomChanges = computed(() => {
  const original = settings.snapshot.value?.customModels ?? [];
  if (original.length !== customDraft.value.length) return true;
  // Cheap structural compare via JSON. Order matters — the array is a list.
  return JSON.stringify(original) !== JSON.stringify(customDraft.value);
});

async function saveDefault() {
  if (!defaultModelDraft.value) return;
  await settings.setDefaultModel(props.wsUrl, defaultModelDraft.value);
}

async function saveCustoms() {
  // Strip empty optional fields so the JSON we send matches the schema's
  // "blank means absent" convention more closely.
  const cleaned = customDraft.value.map((c) => normaliseForWire(c));
  await settings.setCustomModels(props.wsUrl, cleaned);
}

function normaliseForWire(c: CustomModelConfig): CustomModelConfig {
  return {
    id: c.id.trim(),
    name: c.name?.trim() ? c.name.trim() : null,
    api: c.api.trim(),
    baseUrl: c.baseUrl.trim(),
    apiKey: c.apiKey,
    contextWindow: c.contextWindow ?? null,
    maxTokens: c.maxTokens ?? null,
    reasoning: c.reasoning ?? false,
    inputModalities: c.inputModalities && c.inputModalities.length ? c.inputModalities : null,
    thinkingFormat: c.thinkingFormat?.trim() ? c.thinkingFormat.trim() : null,
  };
}

function onClose() {
  emit('close');
}

function onRefresh() {
  void settings.refresh(props.wsUrl);
}
</script>

<template>
  <div v-if="open" class="settings-overlay" @click.self="onClose">
    <div class="settings-modal" role="dialog" aria-label="Model settings">
      <header class="settings-header">
        <h2>Model settings</h2>
        <span class="muted" v-if="settings.loading.value">loading…</span>
        <span class="spacer"></span>
        <button @click="onRefresh" :disabled="settings.loading.value">↻ Refresh</button>
        <button class="danger" @click="onClose">Close</button>
      </header>

      <div v-if="settings.lastError.value" class="banner err">
        {{ settings.lastError.value }}
      </div>
      <div v-else-if="settings.lastStatus.value" class="banner ok">
        {{ settings.lastStatus.value }}
      </div>

      <section class="block">
        <h3>Default model</h3>
        <p class="hint">
          写入 <code>settings.defaultModel</code>。对已经打开的 WS 连接不生效；
          打开新连接时会读到这个值。
        </p>
        <div class="row">
          <select v-model="defaultModelDraft" :disabled="settings.loading.value">
            <option value="" disabled>(选一个模型)</option>
            <option
              v-for="m in settings.snapshot.value?.availableModels ?? []"
              :key="`${m.provider}/${m.id}`"
              :value="m.id"
              :disabled="m.hasCredentials === false"
              :title="m.hasCredentials === false ? '该模型没有可解析的 API key' : ''"
            >
              {{ m.hasCredentials === false ? '🔒 ' : '' }}{{ m.name }} ({{ m.provider }})
            </option>
          </select>
          <button
            class="primary"
            @click="saveDefault"
            :disabled="!defaultModelDraft || !hasDefaultModelChange || settings.loading.value"
          >
            Save default
          </button>
        </div>
        <p class="hint" v-if="settings.snapshot.value?.filtered">
          列表被 <code>enabledModels</code> 过滤后只剩
          {{ settings.snapshot.value?.availableModels.length }} 项。
        </p>
      </section>

      <section class="block">
        <header class="block-head">
          <h3>Custom models</h3>
          <span class="muted">{{ customDraft.length }} 项</span>
          <span class="spacer"></span>
          <button @click="addCustomRow">+ Add</button>
          <button
            class="primary"
            @click="saveCustoms"
            :disabled="!hasCustomChanges || settings.loading.value"
          >
            Save all
          </button>
        </header>
        <p class="hint">
          全量替换 <code>settings.customModels</code>。保存成功后立即刷新
          <code>ModelRegistry</code>，下一次 WS 连接的 <code>list_models</code>
          就能看到新条目。<strong>id</strong> 和 <strong>api</strong> 必填，
          <strong>id</strong> 全局唯一。
        </p>

        <div v-if="!customDraft.length" class="empty">
          还没有自定义模型。点 <strong>+ Add</strong> 添加一行。
        </div>

        <div class="custom-list" v-else>
          <div v-for="(row, idx) in customDraft" :key="idx" class="custom-row">
            <div class="grid">
              <label>
                <span class="lbl">id *</span>
                <input v-model="row.id" placeholder="local-foo" />
              </label>
              <label>
                <span class="lbl">name</span>
                <input v-model="row.name" placeholder="Local Foo" />
              </label>
              <label>
                <span class="lbl">api *</span>
                <input v-model="row.api" placeholder="openai-completions / openai / anthropic" />
              </label>
              <label class="full">
                <span class="lbl">baseUrl</span>
                <input v-model="row.baseUrl" placeholder="http://localhost:1234/v1" />
              </label>
              <label class="full">
                <span class="lbl">apiKey</span>
                <input v-model="row.apiKey" placeholder="sk-… 或 ${ENV_VAR}" />
              </label>
              <label>
                <span class="lbl">contextWindow</span>
                <input
                  v-model.number="row.contextWindow"
                  type="number"
                  min="1"
                  placeholder="128000"
                />
              </label>
              <label>
                <span class="lbl">maxTokens</span>
                <input
                  v-model.number="row.maxTokens"
                  type="number"
                  min="1"
                  placeholder="8192"
                />
              </label>
              <label class="inline">
                <input type="checkbox" v-model="row.reasoning" />
                <span class="lbl">reasoning</span>
              </label>
              <label>
                <span class="lbl">thinkingFormat</span>
                <input v-model="row.thinkingFormat" placeholder="zai / anthropic / …" />
              </label>
            </div>
            <button class="row-del danger" @click="removeCustomRow(idx)" title="Remove">
              ✕
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.settings-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 5vh 16px;
  z-index: 50;
}
.settings-modal {
  background: var(--panel);
  color: inherit;
  border: 1px solid var(--border);
  border-radius: 8px;
  width: min(960px, 100%);
  max-height: 90vh;
  overflow-y: auto;
  padding: 16px 20px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.settings-header {
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--border);
  padding-bottom: 10px;
}
.settings-header h2 {
  margin: 0;
  font-size: 16px;
}
.settings-header .spacer {
  flex: 1;
}
.muted {
  color: var(--muted);
  font-size: 12px;
}
.banner {
  padding: 8px 10px;
  border-radius: 4px;
  font-size: 13px;
  border: 1px solid transparent;
}
.banner.err {
  background: color-mix(in srgb, var(--err) 18%, transparent);
  border-color: var(--err);
  color: var(--err);
}
.banner.ok {
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  border-color: var(--accent);
}
.block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
}
.block:last-of-type {
  border-bottom: none;
}
.block h3 {
  margin: 0;
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--muted);
}
.block-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.block-head .spacer {
  flex: 1;
}
.hint {
  margin: 0;
  font-size: 11.5px;
  color: var(--muted);
  line-height: 1.55;
}
.hint code {
  font-family: ui-monospace, monospace;
  background: var(--bg);
  padding: 1px 4px;
  border-radius: 3px;
}
.row {
  display: flex;
  gap: 8px;
  align-items: stretch;
}
.row select {
  flex: 1;
}
.empty {
  padding: 20px;
  text-align: center;
  color: var(--muted);
  background: var(--bg);
  border: 1px dashed var(--border);
  border-radius: 4px;
  font-size: 13px;
}
.custom-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.custom-row {
  position: relative;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 12px 38px 12px 12px;
}
.custom-row .grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px 10px;
}
.custom-row .grid label {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 11px;
  color: var(--muted);
}
.custom-row .grid label.full {
  grid-column: 1 / -1;
}
.custom-row .grid label.inline {
  flex-direction: row;
  align-items: center;
  gap: 6px;
}
.custom-row input {
  width: 100%;
  box-sizing: border-box;
  padding: 4px 6px;
  font-size: 12.5px;
}
.custom-row .lbl {
  font-size: 10.5px;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.custom-row .row-del {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  padding: 0;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
