# campusclaw vs pi-mono (TypeScript) 完整对齐方案

## Context

campusclaw 基于 prompts.md 实现了核心框架，但与 pi-mono (TS) 在功能覆盖上差距显著。用户要求：
1. campusclaw-ai 优先支持 **zai (智谱)、kimi-coding、minimax** Provider
2. campusclaw-coding-agent **全部功能**对齐（含 RPC 模式等之前遗漏项）
3. 输出为 markdown 文件放在 campusclaw/ 下，并制定优先级

---

## 一、campusclaw-ai 模块

### 1.1 Provider 对比

| Provider | TS KnownProvider | Java 类 | 状态 | 优先级 |
|----------|-----------------|---------|------|--------|
| Anthropic | `anthropic` | `AnthropicProvider` | ✅ 已有 | - |
| OpenAI Completions | `openai` | `OpenAICompletionsProvider` | ✅ 已有 | - |
| OpenAI Responses | `openai` | `OpenAIResponsesProvider` | ✅ 已有 | - |
| AWS Bedrock | `amazon-bedrock` | `BedrockProvider` | ✅ 已有 | - |
| **智谱 ZAI** | `zai` | - | ❌ 缺失 | **P0** |
| **Kimi Coding** | `kimi-coding` | - | ❌ 缺失 | **P0** |
| **MiniMax** | `minimax` / `minimax-cn` | - | ❌ 缺失 | **P0** |
| Google Generative AI | `google` | - | ❌ 缺失 | P1 |
| Google Vertex AI | `google-vertex` | - | ❌ 缺失 | P1 |
| Mistral | `mistral` | - | ❌ 缺失 | P1 |
| Azure OpenAI | `azure-openai-responses` | - | ❌ 缺失 | P2 |
| OpenAI Codex | `openai-codex` | - | ❌ 缺失 | P2 |
| GitHub Copilot | `github-copilot` | - | ❌ 缺失 | P2 |
| Google Gemini CLI | `google-gemini-cli` | - | ❌ 缺失 | P3 |
| Google Antigravity | `google-antigravity` | - | ❌ 缺失 | P3 |
| xAI | `xai` | - | ❌ 缺失 | P2 |
| Groq | `groq` | - | ❌ 缺失 | P2 |
| Cerebras | `cerebras` | - | ❌ 缺失 | P3 |
| OpenRouter | `openrouter` | - | ❌ 缺失 | P2 |
| Vercel AI Gateway | `vercel-ai-gateway` | - | ❌ 缺失 | P3 |
| HuggingFace | `huggingface` | - | ❌ 缺失 | P3 |
| OpenCode | `opencode` / `opencode-go` | - | ❌ 缺失 | P3 |

**ZAI 实现要点:**
- API: `openai-completions` (复用 OpenAICompletionsProvider, 修改 baseUrl)
- Base URL: `https://api.z.ai/api/coding/paas/v4`
- Env: `ZAI_API_KEY`
- 特殊: `thinkingFormat: "zai"` (非标准 thinking 格式)

**Kimi Coding 实现要点:**
- API: `anthropic-messages` (复用 AnthropicProvider, 修改 baseUrl)
- Base URL: `https://api.kimi.com/coding`
- Env: `KIMI_API_KEY`
- Models: k2p5, kimi-k2-thinking

**MiniMax 实现要点:**
- API: `anthropic-messages` (复用 AnthropicProvider, 修改 baseUrl)
- Base URL: `https://api.minimax.io/anthropic` (国际) / CN 变体
- Env: `MINIMAX_API_KEY` / `MINIMAX_CN_API_KEY`
- 两个 Provider 变体: minimax + minimax-cn

### 1.2 基础设施对比

| 功能 | TS 文件 | Java | 状态 | 优先级 |
|------|---------|------|------|--------|
| 消息类型体系 | `types.ts` | Message/ContentBlock 等 | ✅ 已有 | - |
| 事件流 | `event-stream.ts` | EventStream | ✅ 已有 | - |
| Provider 注册 | `register-builtins.ts` | ApiProviderRegistry | ✅ 已有 | - |
| Model 注册 | `models.ts` | ModelRegistry | ✅ 已有 | - |
| **跨 Provider 消息转换** | `transform-messages.ts` | - | ❌ 缺失 | **P0** |
| **环境变量 API Key 解析** | `env-api-keys.ts` | - | ❌ 缺失 | **P0** |
| Google 共享工具 | `google-shared.ts` | - | ❌ 缺失 | P1 |
| OpenAI Responses 共享 | `openai-responses-shared.ts` | - | ❌ 缺失 | P2 |
| Simple Options 工厂 | `simple-options.ts` | - | ❌ 缺失 | P1 |
| 模型数据生成 | `models.generated.ts` | 硬编码 | ❌ 需改造 | P1 |

---

## 二、campusclaw-agent-core 模块

### 2.1 功能对比

| 功能 | TS | Java | 状态 | 优先级 |
|------|-----|------|------|--------|
| Agent Loop | `agent-loop.ts` | `AgentLoop` | ✅ 已有 | - |
| Agent 门面 | `agent.ts` | `Agent` | ✅ 已有 | - |
| AgentState | `types.ts` | `AgentState` | ✅ 已有 | - |
| AgentTool 接口 | `types.ts` | `AgentTool` | ✅ 已有 | - |
| Before/After 钩子 | `types.ts` | Handler 接口 | ✅ 已有 | - |
| 事件系统 | `types.ts` | AgentEvent 层级 | ✅ 已有 | - |
| 工具执行管线 | agent-loop | ToolExecutionPipeline | ✅ 已有 | - |
| MessageQueue | `types.ts` | MessageQueue | ✅ 已有 | - |
| ContextTransformer | `types.ts` | ContextTransformer | ✅ 已有 | - |
| **可插拔 StreamFunction** | streamFn in config | 硬依赖 CampusClawAiService | ⚠️ 需改进 | P1 |
| **函数式消息获取** | getSteeringMessages | 仅 MessageQueue | ⚠️ 需改进 | P1 |
| Proxy 集成 | `proxy.ts` | - | ❌ 缺失 | P3 |

> campusclaw-agent-core 整体对齐度较高，主要是灵活性改进。

---

## 三、campusclaw-tui 模块

### 3.1 组件对比

| 组件 | TS | Java | 状态 | 优先级 |
|------|-----|------|------|--------|
| Text | ✅ | ✅ | 已有 | - |
| Box | ✅ | ✅ | 已有 | - |
| Editor | ✅ | ✅ | 已有 | - |
| Input | ✅ | ✅ | 已有 | - |
| SelectList | ✅ | ✅ | 已有 | - |
| Markdown | ✅ | ✅ | 已有 | - |
| Container | ✅ | ✅ | 已有 | - |
| KillRing | ✅ | ✅ | 已有 | - |
| UndoStack | ✅ | ✅ | 已有 | - |
| **Loader** | ✅ | ❌ | 缺失 | P1 |
| **CancellableLoader** | ✅ | ❌ | 缺失 | P1 |
| **TruncatedText** | ✅ | ❌ | 缺失 | P2 |
| **Spacer** | ✅ | ❌ | 缺失 | P2 |
| **Image** | ✅ | ❌ | 缺失 | P3 |
| **SettingsList** | ✅ | ❌ | 缺失 | P2 |

### 3.2 基础设施对比

| 功能 | TS | Java | 状态 | 优先级 |
|------|-----|------|------|--------|
| Component 接口 | ✅ | ✅ | 已有 | - |
| Terminal 抽象 | ✅ | ✅ JLineTerminal | 已有 | - |
| DiffRenderer | ✅ | ✅ | 已有 | - |
| ANSI 工具 | ✅ | ✅ | 已有 | - |
| **StdinBuffer** | ✅ | ❌ | 缺失 | P2 |
| **Autocomplete** | ✅ | ❌ | 缺失 | P2 |
| **Fuzzy 搜索** | ✅ | ❌ | 缺失 | P2 |
| **Keybindings** | ✅ | ❌ | 缺失 | P2 |
| **Terminal Image** | ✅ | ❌ | 缺失 | P3 |

---

## 四、campusclaw-coding-agent 模块 (重点全量对齐)

### 4.1 工具对比

| 工具 | TS | Java | 状态 | 优先级 |
|------|-----|------|------|--------|
| Bash | `bash.ts` | `BashTool` | ✅ 已有 | - |
| Read | `read.ts` | `ReadTool` | ✅ 已有 | - |
| Write | `write.ts` | `WriteTool` | ✅ 已有 | - |
| Edit | `edit.ts` | `EditTool` | ✅ 已有 | - |
| Grep | `grep.ts` | `GrepTool` | ✅ 已有 | - |
| Glob/Find | `find.ts` | `GlobTool` | ✅ 已有 (名称不同) | - |
| **Ls** | `ls.ts` | - | ❌ 缺失 | **P0** |
| **EditDiff** | `edit-diff.ts` | - | ❌ 缺失 | P1 |

### 4.2 运行模式对比

| 模式 | TS | Java | 状态 | 优先级 |
|------|-----|------|------|--------|
| Interactive | ✅ 35+ TUI 组件 | ✅ InteractiveMode | ✅ 已有 (基础) | - |
| One-Shot | ✅ | ✅ OneShotMode | ✅ 已有 | - |
| **Print** | ✅ text/json 输出 | ❌ | ❌ 缺失 | **P0** |
| **RPC** | ✅ JSONL stdin/stdout | ❌ | ❌ 缺失 | **P0** |

**RPC 模式命令清单 (TS 已实现):**

| 类别 | 命令 |
|------|------|
| 会话控制 | `prompt`, `steer`, `follow_up`, `abort`, `new_session` |
| 状态查询 | `get_state`, `get_messages`, `get_session_stats`, `get_available_models` |
| 模型控制 | `set_model`, `cycle_model` |
| 思考级别 | `set_thinking_level`, `cycle_thinking_level` |
| 队列模式 | `set_steering_mode`, `set_follow_up_mode` |
| 压缩 | `compact`, `set_auto_compaction` |
| 重试 | `set_auto_retry`, `abort_retry` |
| Bash | `bash`, `abort_bash` |
| 会话管理 | `export_html`, `switch_session`, `fork`, `set_session_name` |

### 4.3 核心系统对比

| 功能 | TS 位置 | Java | 状态 | 优先级 |
|------|---------|------|------|--------|
| SystemPromptBuilder | `system-prompt.ts` | ✅ SystemPromptBuilder | 已有 | - |
| CLI 入口 | `cli.ts` | ✅ CampusClawCommand | 已有 | - |
| AgentSession | `agent-session.ts` | ✅ AgentSession | 已有 | - |
| SessionPersistence | `session-manager.ts` | ✅ SessionPersistence | 已有 (基础) | - |
| Skill 系统 | `skills.ts` | ✅ SkillLoader/Registry | 已有 | - |
| FileMutationQueue | file-mutation-queue | ✅ | 已有 | - |
| PathUtils | path-utils | ✅ | 已有 | - |
| TruncationUtils | truncate | ✅ | 已有 | - |
| Tool Operations 抽象 | 各 tool | ✅ ReadOps/WriteOps 等 | 已有 | - |
| **Context Compaction** | `compaction/` (4 文件) | ❌ | 缺失 | **P0** |
| **Settings 管理** | `settings-manager.ts` | ❌ | 缺失 | **P0** |
| **Auth 存储** | `auth-storage.ts` | ❌ | 缺失 | **P0** |
| **Slash Commands** | `slash-commands.ts` (20 个) | ❌ | 缺失 | **P0** |
| **Keybindings 系统** | `keybindings.ts` (21 绑定) | ❌ | 缺失 | **P0** |
| **Session 分支/树** | session-manager (fork/tree) | ❌ | 缺失 | **P0** |
| **HTML 导出** | `export-html/` | ❌ | 缺失 | P1 |
| **Prompt Templates** | `prompt-templates.ts` | ❌ | 缺失 | P1 |
| **Extension 系统** | `extensions/` | ❌ | 缺失 | P1 |
| **Model Resolver** | `model-resolver.ts` | ❌ | 缺失 | P1 |
| **Resource Loader** | `resource-loader.ts` | ❌ | 缺失 | P1 |
| **Git 集成** | `utils/git.ts` | ❌ | 缺失 | P1 |
| **剪贴板支持** | `utils/clipboard*.ts` | ❌ | 缺失 | P2 |
| **Image 处理** | `utils/image-*.ts` | ❌ | 缺失 | P2 |
| **Diff 可视化** | `components/diff.ts` | ❌ | 缺失 | P2 |
| **Theme 系统** | theme 配置 | ❌ | 缺失 | P2 |
| **Output Guard** | `output-guard.ts` | ❌ | 缺失 | P1 |
| **Diagnostics** | `diagnostics.ts` | ❌ | 缺失 | P2 |
| **Footer Data** | `footer-data-provider.ts` | ❌ | 缺失 | P2 |
| **Config Value 解析** | `resolve-config-value.ts` | ❌ | 缺失 | P1 |
| **Package Manager** | `package-manager.ts` | ❌ | 缺失 | P2 |
| **Migrations** | `migrations.ts` | ❌ | 缺失 | P2 |
| **Changelog** | `utils/changelog.ts` | ❌ | 缺失 | P3 |
| **Source Info** | `source-info.ts` | ❌ | 缺失 | P2 |
| **Timings** | `timings.ts` | ❌ | 缺失 | P3 |
| **MIME 检测** | `utils/mime.ts` | ❌ | 缺失 | P2 |

### 4.4 Settings 详细配置项 (TS 已实现)

| 配置项 | 类型 | 说明 |
|--------|------|------|
| `defaultProvider` | string | 默认 Provider |
| `defaultModel` | string | 默认模型 |
| `defaultThinkingLevel` | string | 默认思考级别 |
| `transport` | "sse"/"websocket"/"auto" | 传输方式 |
| `steeringMode` | "all"/"one-at-a-time" | 队列模式 |
| `followUpMode` | "all"/"one-at-a-time" | 跟进队列模式 |
| `compaction.enabled` | boolean | 启用压缩 |
| `compaction.reserveTokens` | number (16384) | 保留 token 数 |
| `compaction.keepRecentTokens` | number (20000) | 保留最近 token |
| `retry.enabled` | boolean | 启用重试 |
| `retry.maxRetries` | number | 最大重试次数 |
| `theme` | string | 主题名称 |
| `hideThinkingBlock` | boolean | 隐藏思考块 |
| `terminal.showImages` | boolean | 终端显示图片 |
| `shellPath` | string | Shell 路径 |
| `enableSkillCommands` | boolean | 启用 Skill 命令 |
| `packages` | string[] | 扩展包 |
| `sessionDir` | string | Session 目录 |

### 4.5 Slash Commands 清单 (TS 已实现)

| 命令 | 说明 |
|------|------|
| `/settings` | 打开设置面板 |
| `/model` | 切换模型 |
| `/scoped-models` | 管理模型范围 |
| `/export` | 导出会话 HTML |
| `/import` | 导入会话 |
| `/share` | 分享会话 |
| `/copy` | 复制最后回复 |
| `/name` | 命名会话 |
| `/session` | 切换会话 |
| `/changelog` | 查看更新日志 |
| `/hotkeys` | 查看快捷键 |
| `/fork` | 分叉会话 |
| `/tree` | 查看会话树 |
| `/login` | 登录 |
| `/logout` | 登出 |
| `/new` | 新建会话 |
| `/compact` | 手动压缩上下文 |
| `/resume` | 恢复会话 |
| `/reload` | 重载配置 |
| `/quit` | 退出 |

### 4.6 Keybindings 清单 (TS 已实现)

| 绑定 | 快捷键 | 说明 |
|------|--------|------|
| `app.interrupt` | Escape | 中断 |
| `app.clear` | Ctrl+C | 清除 |
| `app.exit` | Ctrl+D | 退出 |
| `app.suspend` | Ctrl+Z | 挂起 |
| `app.thinking.cycle` | Shift+Tab | 切换思考级别 |
| `app.model.cycleForward` | Ctrl+P | 下一个模型 |
| `app.model.cycleBackward` | Shift+Ctrl+P | 上一个模型 |
| `app.model.select` | Ctrl+L | 选择模型 |
| `app.tools.expand` | Ctrl+O | 展开工具 |
| `app.thinking.toggle` | Ctrl+T | 切换思考块可见性 |
| `app.editor.external` | Ctrl+G | 外部编辑器 |
| `app.message.followUp` | Alt+Enter | 跟进消息 |
| `app.clipboard.pasteImage` | Ctrl+V / Alt+V | 粘贴图片 |
| `app.session.new` | (可配) | 新会话 |
| `app.session.tree` | (可配) | 会话树 |
| `app.session.fork` | (可配) | 分叉 |
| `app.session.resume` | (可配) | 恢复 |

---

## 五、实施优先级

### P0 — 必须实现 (核心功能缺失)

| # | 任务 | 模块 | 复杂度 | 说明 |
|---|------|------|--------|------|
| 1 | ZAI Provider | campusclaw-ai | 低 | 复用 OpenAICompletionsProvider + 自定义 baseUrl + zai thinking format |
| 2 | Kimi Coding Provider | campusclaw-ai | 低 | 复用 AnthropicProvider + 自定义 baseUrl |
| 3 | MiniMax Provider (含 CN) | campusclaw-ai | 低 | 复用 AnthropicProvider + 两个 baseUrl 变体 |
| 4 | EnvApiKeyResolver | campusclaw-ai | 中 | 23+ Provider 环境变量映射, ADC 检测, Bedrock 凭证 |
| 5 | MessageTransformer | campusclaw-ai | 中 | Tool Call ID 归一化, Thinking 丢弃, 孤立 ToolCall 合成 |
| 6 | LsTool | campusclaw-coding-agent | 低 | 目录列表, LsOperations 接口 |
| 7 | Context Compaction | campusclaw-coding-agent | 高 | Compactor + FileOperationTracker + LLM 摘要 + AgentLoop 集成 |
| 8 | Settings 管理 | campusclaw-coding-agent | 中 | global + project 配置, 深度合并, 文件锁 |
| 9 | Auth 存储 | campusclaw-coding-agent | 中 | API Key + OAuth 凭证, 0o600 权限, 刷新 |
| 10 | RPC 模式 | campusclaw-coding-agent | 高 | JSONL 协议, 20+ 命令, stdin/stdout |
| 11 | Print 模式 | campusclaw-coding-agent | 低 | text/json 输出, 退出码 |
| 12 | Session 分支/树 | campusclaw-coding-agent | 高 | fork/tree 导航, JSONL 条目带 parent ID |
| 13 | Slash Commands | campusclaw-coding-agent | 中 | 20 个内置命令框架 |
| 14 | Keybindings 系统 | campusclaw-coding-agent | 中 | 可自定义快捷键, 21 个默认绑定 |

### P1 — 重要提升

| # | 任务 | 模块 | 说明 |
|---|------|------|------|
| 15 | Google Provider | campusclaw-ai | google-shared + google + vertex |
| 16 | Mistral Provider | campusclaw-ai | mistral-conversations API |
| 17 | Simple Options 工厂 | campusclaw-ai | 统一构建 SimpleStreamOptions |
| 18 | 模型数据生成 | campusclaw-ai | models.generated 机制 |
| 19 | 可插拔 StreamFunction | campusclaw-agent-core | AgentLoop 解耦 |
| 20 | 函数式消息获取 | campusclaw-agent-core | getSteeringMessages/getFollowUpMessages |
| 21 | Extension 系统 | campusclaw-coding-agent | 工具/命令/钩子注册 |
| 22 | Prompt Templates | campusclaw-coding-agent | $1/$2/$@ 参数替换 |
| 23 | HTML 导出 | campusclaw-coding-agent | ANSI→HTML + 主题 |
| 24 | Git 集成 | campusclaw-coding-agent | 分支名/未提交检测 |
| 25 | Model Resolver | campusclaw-coding-agent | 模型回退/范围解析 |
| 26 | Resource Loader | campusclaw-coding-agent | 统一资源发现 |
| 27 | Output Guard | campusclaw-coding-agent | RPC 模式输出隔离 |
| 28 | Config Value 解析 | campusclaw-coding-agent | 环境变量展开 |
| 29 | EditDiff 工具 | campusclaw-coding-agent | diff 格式编辑 |
| 30 | Loader / CancellableLoader | campusclaw-tui | 加载动画组件 |

### P2 — 扩展完善

| # | 任务 | 模块 | 说明 |
|---|------|------|------|
| 31 | Azure OpenAI Provider | campusclaw-ai | 复用 Responses 共享 |
| 32 | xAI / Groq / OpenRouter | campusclaw-ai | OpenAI-Compatible 通用 |
| 33 | OpenAI Codex Provider | campusclaw-ai | OAuth + Responses 共享 |
| 34 | GitHub Copilot Provider | campusclaw-ai | 动态 header |
| 35 | 剪贴板支持 | campusclaw-coding-agent | OSC 52 + pbcopy/xclip |
| 36 | Image 处理 | campusclaw-coding-agent | 缩放/EXIF/格式转换 |
| 37 | Diff 可视化 | campusclaw-coding-agent | 彩色 side-by-side diff |
| 38 | Theme 系统 | campusclaw-coding-agent | 40+ 可配颜色 |
| 39 | Package Manager | campusclaw-coding-agent | npm/git 包发现 |
| 40 | Source Info | campusclaw-coding-agent | 资源来源追踪 |
| 41 | Diagnostics | campusclaw-coding-agent | 资源校验/冲突检测 |
| 42 | MIME 检测 | campusclaw-coding-agent | 文件类型识别 |
| 43 | Migrations | campusclaw-coding-agent | Session 格式迁移 |
| 44 | Footer Data | campusclaw-coding-agent | 底栏信息显示 |
| 45 | StdinBuffer | campusclaw-tui | 转义序列装配 |
| 46 | Autocomplete + Fuzzy | campusclaw-tui | 文件路径补全 |
| 47 | TruncatedText + Spacer | campusclaw-tui | 布局组件 |
| 48 | SettingsList | campusclaw-tui | 设置列表组件 |
| 49 | Keybindings 组件 | campusclaw-tui | 可配键绑定 |

### P3 — 后续打磨

| # | 任务 | 模块 |
|---|------|------|
| 50 | Google Gemini CLI / Antigravity | campusclaw-ai |
| 51 | Cerebras / HuggingFace / OpenCode | campusclaw-ai |
| 52 | Proxy 集成 | campusclaw-agent-core |
| 53 | Image 组件 + Kitty/iTerm2 协议 | campusclaw-tui |
| 54 | Changelog | campusclaw-coding-agent |
| 55 | Timings | campusclaw-coding-agent |

---

## 六、统计概览

### 已实现 vs 缺失

| 模块 | 已实现 | 缺失 | 对齐率 |
|------|--------|------|--------|
| campusclaw-ai (Provider) | 4 | 19 | 17% |
| campusclaw-ai (基础设施) | 4 | 6 | 40% |
| campusclaw-agent-core | 9 | 3 | 75% |
| campusclaw-tui (组件) | 9 | 6 | 60% |
| campusclaw-tui (基础设施) | 5 | 5 | 50% |
| campusclaw-coding-agent (工具) | 6 | 2 | 75% |
| campusclaw-coding-agent (模式) | 2 | 2 | 50% |
| campusclaw-coding-agent (核心系统) | 10 | 24 | 29% |
| **总计** | **49** | **67** | **42%** |

### P0 工作量估算

| 任务 | 预估代码行数 |
|------|-------------|
| ZAI + Kimi + MiniMax Provider | ~600 |
| EnvApiKeyResolver | ~300 |
| MessageTransformer | ~400 |
| LsTool | ~200 |
| Context Compaction | ~800 |
| Settings 管理 | ~500 |
| Auth 存储 | ~400 |
| RPC 模式 | ~1200 |
| Print 模式 | ~200 |
| Session 分支/树 | ~800 |
| Slash Commands | ~600 |
| Keybindings 系统 | ~300 |
| **P0 总计** | **~6,300** |

---

## 七、验证方案

```bash
# 全量构建
./gradlew build

# 模块测试
./gradlew :modules:ai:test
./gradlew :modules:agent-core:test
./gradlew :modules:coding-agent-cli:test

# Provider 集成测试
ZAI_API_KEY=xxx ./gradlew :modules:ai:test --tests "*ZaiProvider*"
KIMI_API_KEY=xxx ./gradlew :modules:ai:test --tests "*KimiProvider*"

# RPC 模式测试
echo '{"type":"prompt","message":"hello"}' | java -jar campusclaw-coding-agent.jar --mode rpc
```