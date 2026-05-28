// Wire types for the /api/settings/* REST endpoints. Mirrors the Java records
// in modules/coding-agent-cli/.../settings/Settings.java and the response
// shapes built in mode/server/SettingsHandler.java. Keep field names in sync
// with the OpenAPI spec at docs/openapi/campusclaw-api.yaml.

export interface CustomModelConfig {
  id: string;
  name?: string | null;
  api: string;
  baseUrl: string;
  apiKey: string;
  contextWindow?: number | null;
  maxTokens?: number | null;
  reasoning?: boolean | null;
  inputModalities?: string[] | null;
  thinkingFormat?: string | null;
}

export interface AvailableModelEntry {
  id: string;
  name: string;
  provider: string;
  contextWindow: number;
  maxTokens: number;
  reasoning: boolean;
  hasCredentials: boolean;
  cost?: {
    input: number;
    output: number;
    cacheRead: number;
    cacheWrite: number;
  };
}

export interface SettingsModelsSnapshot {
  defaultModel: string | null;
  customModels: CustomModelConfig[];
  availableModels: AvailableModelEntry[];
  filtered: boolean;
}

export interface ApiError {
  error: string;
}
