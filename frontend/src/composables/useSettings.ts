import { ref } from 'vue';
import type {
  ApiError,
  CustomModelConfig,
  SettingsModelsSnapshot,
} from '../types/settings';

// Local copy of useChatWs.ts's wsToHttpBase — keeping it duplicated avoids
// expanding the chat composable's public surface for one helper.
function wsToHttpBase(wsUrl: string): string {
  try {
    const u = new URL(wsUrl);
    const httpProto = u.protocol === 'wss:' ? 'https:' : 'http:';
    return `${httpProto}//${u.host}`;
  } catch {
    return wsUrl;
  }
}

async function readError(resp: Response): Promise<string> {
  try {
    const data = (await resp.json()) as Partial<ApiError>;
    if (data && typeof data.error === 'string') {
      return data.error;
    }
  } catch {
    // body wasn't JSON — fall through to HTTP status
  }
  return `HTTP ${resp.status}`;
}

/**
 * Composable wrapping the three /api/settings/* REST endpoints. Mirrors the
 * shape of useChatWs — refs for state, async functions for actions. All
 * methods take the WS URL as input and resolve the HTTP base from it, so the
 * panel survives the user retyping the WS URL.
 */
export function useSettings() {
  const snapshot = ref<SettingsModelsSnapshot | null>(null);
  const loading = ref(false);
  const lastError = ref<string | null>(null);
  const lastStatus = ref<string | null>(null);

  async function refresh(wsUrl: string): Promise<SettingsModelsSnapshot | null> {
    const base = wsToHttpBase(wsUrl);
    loading.value = true;
    lastError.value = null;
    try {
      const resp = await fetch(`${base}/api/settings/models`);
      if (!resp.ok) {
        lastError.value = await readError(resp);
        return null;
      }
      const data = (await resp.json()) as SettingsModelsSnapshot;
      snapshot.value = data;
      return data;
    } catch (e) {
      lastError.value = (e as Error).message;
      return null;
    } finally {
      loading.value = false;
    }
  }

  async function setDefaultModel(wsUrl: string, modelId: string): Promise<boolean> {
    const base = wsToHttpBase(wsUrl);
    lastError.value = null;
    lastStatus.value = null;
    try {
      const resp = await fetch(`${base}/api/settings/models/default`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ model: modelId }),
      });
      if (!resp.ok) {
        lastError.value = await readError(resp);
        return false;
      }
      lastStatus.value = `Default model set: ${modelId}`;
      await refresh(wsUrl);
      return true;
    } catch (e) {
      lastError.value = (e as Error).message;
      return false;
    }
  }

  async function setCustomModels(
    wsUrl: string,
    customs: CustomModelConfig[],
  ): Promise<boolean> {
    const base = wsToHttpBase(wsUrl);
    lastError.value = null;
    lastStatus.value = null;
    try {
      const resp = await fetch(`${base}/api/settings/customModels`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(customs),
      });
      if (!resp.ok) {
        lastError.value = await readError(resp);
        return false;
      }
      lastStatus.value = `Saved customModels (${customs.length} entries)`;
      await refresh(wsUrl);
      return true;
    } catch (e) {
      lastError.value = (e as Error).message;
      return false;
    }
  }

  return {
    snapshot,
    loading,
    lastError,
    lastStatus,
    refresh,
    setDefaultModel,
    setCustomModels,
  };
}
