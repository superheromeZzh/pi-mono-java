# list_models 返回「可用模型」— 设计文档

> 结构采用 gstack `/plan-eng-review` 工程评审视角（Context / 关键定义 / 架构与数据流 / 设计决策 / 边界 / 性能(DFX) / 契约 / 测试 / 验证）。
> 设计决策逐条链接到 [`docs/decisions/`](../decisions/) 下的 ADR HTML。

## 1. Context

WS `list_models`（以及共享同一份逻辑的 REST `GET /api/settings/models`）此前默认按 `settings.enabledModels` 白名单过滤，**完全不看凭证**。结果列表里混入没有 API key 的模型——用户一选就在运行时报错（`stopReason=error`）。

根因：`ModelCatalogService.getAvailableModels()` 只做白名单 glob 展开；`hasCredentials()` 虽存在，但只作为每个模型条目的一个布尔**字段**返回，从不参与**过滤**。

目标：默认只返回「可用（有凭证）」模型；**custom 模型置顶**；`all:true` 仍可看全量（逃生口）。改**共享 service**，WS + REST 一致。TUI 选择器 / `-m` flag 不在范围——它们本就绕过 service 直查 `modelRegistry.getAllModels()`。

## 2. 「可用」的精确定义（credential sources）

一个模型 `hasCredentials() == true` 当且仅当满足以下任一（短路顺序），出处见 `ModelCatalogService.hasCredentials` 与 `SettingsBackedProviderConfigResolver.resolveApiKey`：

| # | 来源 | 说明 |
|---|---|---|
| 0 | `Provider.CUSTOM` | 永远可用——用户手动加的，自带凭证 |
| 1 | 模型内嵌 `apiKey` | `model.apiKey()` 非空 |
| 2a | `auth.json` | `~/.campusclaw/agent/auth.json`（`/auth login` 写入） |
| 2b | `settings.json` `provider.<id>.apiKey` | 支持 `${ENV}` 间接展开 |
| 2c | 模型内嵌 `apiKey` | 与 #1 同 |
| 2d | provider 专属环境变量 | `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` / `ZAI_API_KEY` / …；Google ADC；AWS Bedrock（`AWS_PROFILE` / IAM / bearer / container / IRSA）→ 命中返回 `<authenticated>` 哨兵 |

测试旁路：`providerConfigResolver == null`（2-arg 构造）时 `hasCredentials` 返回 `true`，保证测试不会意外清空目录。

## 3. 架构与数据流

单一改动点：`ModelCatalogService.getAvailableModels()`。调用方自动一致：

| 表面 | 入口 | 调用 |
|---|---|---|
| WS `list_models` | `ChatWebSocketHandler.handleListModels` | `all ? getAllModels() : getAvailableModels()` |
| REST `GET /api/settings/models` | `SettingsHandler.buildModelsSnapshot` | `getAvailableModels()` |

实现：`getAvailableModels()` = `narrowToEnabled(全量)`（白名单展开 + custom）→ 按 `hasCredentials` 过滤（custom 恒过）→ `CUSTOM_FIRST` 排序。`getAllModels()` 不过滤（`all:true` 逃生口），仅用 `CUSTOM_FIRST` 排序。

| 请求 | 改后行为 |
|---|---|
| `all:false`（默认） | 「白名单子集 ∩ 有凭证」+ custom 永远在内，custom 置顶 |
| `all:true` | 全量注册表（仍含无凭证），custom 置顶 |

## 4. 设计决策

| ID | 决策 | ADR |
|---|---|---|
| D1 | 以「有凭证」定义可用并参与过滤，复用 `hasCredentials()` | [ADR-0001](../decisions/0001-list-models-usable-credentials.html) |
| D2 | 过滤烘焙进共享 `getAvailableModels()`；`getAllModels()` 保持不过滤作 `all:true` 逃生口 | [ADR-0002](../decisions/0002-filter-in-shared-service.html) |
| D3 | `enabledModels` 保留为**交集**（凭证 ∩ 白名单）；没配白名单则=所有有凭证的 | [ADR-0003](../decisions/0003-keep-enabledmodels-intersection.html) |
| D4 | custom 置顶排序，`getAvailableModels` 与 `getAllModels` 都应用 | [ADR-0004](../decisions/0004-custom-models-first.html) |
| D5 | `filtered` 维持白名单语义、不新增字段；默认列表额外按凭证过滤这一事实写进契约 | [ADR-0005](../decisions/0005-filtered-flag-semantics.html) |

## 5. 边界情况

| # | 场景 | 行为 |
|---|---|---|
| E1 | 全新安装、无任何 key | 默认列表可能为空（仅 custom）；`all:true` 兜底 |
| E2 | custom 模型 | 永远可用、永远包含、永远置顶 |
| E3 | `${ENV}` 指向空 | 不可用 |
| E4 | Google ADC / AWS Bedrock | `<authenticated>` 哨兵，无字面 key 也算可用 |
| E5 | `all:true` | 仍返回无凭证模型，`filtered:false`；每条仍带 `hasCredentials` |
| E6 | 白名单内全无凭证 | 默认列表可能仅剩 custom |
| E7 | current model 被过滤掉 | `get_state` 仍返回它；前端高亮需容错或退回 `all:true` |

## 6. 性能 / DFX

`hasCredentials(m)` → `resolve()` 每次读 `settings.json` + `auth.json` + env，对 N 个模型逐个过滤 = O(N) 次文件读。该 O(N) 在改动前就已存在（每条要算 `hasCredentials` 填字段）；只要过滤与字段复用同一次判定即不加倍。可选优化：单次调用内按 provider 维度缓存「是否有非内嵌凭证」，把文件读降到 O(distinct providers)。当前实现保持简单直接，未引入缓存。

## 7. 契约改动

- AsyncAPI `docs/asyncapi/chat-ws.yaml`：`ListModels` summary、`ListModelsCmd.all`、`ListModelsResponseData.filtered`、`ModelInfo.hasCredentials` 均已说明「默认列表仅含有凭证的可用模型，custom 置顶；`all:true` 为全量逃生口」。
- OpenAPI `docs/openapi/campusclaw-api.yaml`：`getSettingsModels` 描述、`SettingsModelsSnapshot.availableModels` / `filtered` / `hasCredentials` 同步。

## 8. 测试

- `ModelCatalogServiceTest`：既有用例（2-arg null resolver，断言 `containsExactlyInAnyOrder`/`hasSize`）保持通过；新增凭证过滤（注入 resolver，部分 provider 有/无 key）、custom 置顶（`getAvailableModels` + `getAllModels`）断言。
- `SettingsHandlerTest`：mock 了 catalog，逻辑改动不影响，保持通过。
- `ChatWebSocketHandlerTest`：新增 `listModelsDefaultFiltersByCredentialsAllTrueBypasses`——默认仅可用、`all:true` 全量。
- 结果：相关 45 个用例全绿，checkstyle 0 违规。

## 9. 验证（end-to-end）

```bash
./mvnw -pl modules/coding-agent-cli -am test \
  -Dtest='ModelCatalogServiceTest,ChatWebSocketHandlerTest,SettingsHandlerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

- 起服务 `./campusclaw.sh --mode server --port 3000`；WS 发 `{"type":"list_models"}` → 仅可用 + custom 首；`{"type":"list_models","all":true}` → 全量。
- REST `curl localhost:3000/api/settings/models` → `availableModels` 仅可用、custom 首。
- 负向：`unset` 所有 `*_API_KEY` 且无 `auth.json` → 默认仅 custom；设一个 `ANTHROPIC_API_KEY` → 对应模型出现。
