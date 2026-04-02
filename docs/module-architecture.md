# CampusClaw 模块架构

## 概览

CampusClaw 是一个 JDK 21 + Spring Boot 3.4 的 Maven 多模块项目，共 5 个模块，按职责分层。

## 依赖关系

```
ai ─────────────┐
                ├──→ agent-core ──┬──→ cron ──┐
tui ────────────┤                 │           │
                └─────────────────┴───────────┴──→ coding-agent-cli
```

- `ai` 和 `tui` 是底层模块，无内部依赖
- `agent-core` 依赖 `ai`
- `cron` 依赖 `agent-core`
- `coding-agent-cli` 依赖全部四个模块，是最终的应用入口

## 模块详情

### ai (`campusclaw-ai`)

LLM 多供应商抽象层，提供统一的消息、工具、流式交互类型。

| 包 | 职责 |
|---|------|
| `com.campusclaw.ai.types` | 核心领域对象：Message、ContentBlock、Tool、Provider 枚举等 |
| `com.campusclaw.ai.provider` | 多 Provider 抽象与实现（anthropic、openai、google、bedrock、mistral） |
| `com.campusclaw.ai.stream` | 流式消息处理 |
| `com.campusclaw.ai.model` | 模型信息与费用配置 |
| `com.campusclaw.ai.env` | 环境变量处理 |
| `com.campusclaw.ai.utils` | 工具函数 |

**关键依赖：** Anthropic SDK、OpenAI SDK、Google Cloud AI Platform、AWS Bedrock、Spring WebFlux、Reactor

### tui (`campusclaw-tui`)

终端 UI 框架，提供全屏渲染、ANSI 处理和组件系统。

| 包 | 职责 |
|---|------|
| `com.campusclaw.tui` | 主 Tui 类（全屏渲染器） |
| `com.campusclaw.tui.terminal` | 终端抽象（JLineTerminal、TestTerminal、TerminalSize） |
| `com.campusclaw.tui.ansi` | ANSI 转义序列工具（AnsiUtils、AnsiCodeTracker） |
| `com.campusclaw.tui.component` | UI 组件（Text、Container、CancellableLoader、FuzzyMatcher 等） |
| `com.campusclaw.tui.image` | 图片渲染支持 |

**关键依赖：** JLine、Lanterna、Jackson

### agent-core (`campusclaw-agent-core`)

Agent 运行时核心，编排多轮 LLM 对话、工具执行和状态管理。

| 包 | 职责 |
|---|------|
| `com.campusclaw.agent` | Agent 门面（编排 Agent 运行时） |
| `com.campusclaw.agent.loop` | Agent 执行循环（AgentLoop、AgentLoopConfig） |
| `com.campusclaw.agent.tool` | 工具处理（AgentTool、ToolExecutionPipeline、回调） |
| `com.campusclaw.agent.state` | 状态管理（AgentState、AgentStateSnapshot） |
| `com.campusclaw.agent.event` | 事件系统（TurnStartEvent、TurnEndEvent、ToolExecutionEvents） |
| `com.campusclaw.agent.context` | 上下文转换与消息转换 |
| `com.campusclaw.agent.queue` | 消息队列管理 |
| `com.campusclaw.agent.proxy` | 代理配置 |

**关键依赖：** campusclaw-ai、Reactor Core、Jackson、JSON Schema Validator、Spring Context、Micrometer

### cron (`campusclaw-cron`)

定时任务引擎，管理 CronJob 的定义、调度与执行。

| 包 | 职责 |
|---|------|
| `com.campusclaw.cron` | CronService 门面 |
| `com.campusclaw.cron.model` | 领域对象（CronJob、CronSchedule、CronEvent、CronJobState） |
| `com.campusclaw.cron.engine` | 执行引擎（CronEngine、CronJobExecutor、CronEventListener） |
| `com.campusclaw.cron.tool` | Agent 集成工具（CronTool） |
| `com.campusclaw.cron.store` | 持久化（CronStore、CronRunLog） |

**关键依赖：** campusclaw-agent-core、Jackson、Spring Context

### coding-agent-cli (`campusclaw-coding-agent`)

Spring Boot CLI 应用入口，整合所有模块，提供多种执行模式。

| 包 | 职责 |
|---|------|
| `com.campusclaw.codingagent` | CampusClawApplication（Spring Boot + Picocli 入口） |
| `com.campusclaw.codingagent.mode` | 执行模式（InteractiveMode、OneShotMode、PrintMode、Server/RPC/TUI） |
| `com.campusclaw.codingagent.skill` | Skill 框架（Skill、SkillLoader、SkillRegistry） |
| `com.campusclaw.codingagent.extension` | 扩展系统（Extension、ExtensionPoint、ExtensionRegistry） |
| `com.campusclaw.codingagent.settings` | 配置管理（Settings、SettingsManager） |
| `com.campusclaw.codingagent.keybinding` | 快捷键配置 |
| `com.campusclaw.codingagent.context` | 上下文文件加载 |
| `com.campusclaw.codingagent.auth` | 认证处理 |
| `com.campusclaw.codingagent.compaction` | 上下文压缩 |
| `com.campusclaw.codingagent.config` | 应用配置 |

**关键依赖：** 全部 4 个内部模块、Spring Boot（starter + webflux）、Picocli、SnakeYAML、Micrometer

## 架构特点

- **分层解耦：** 底层模块（ai、tui）无内部依赖，可独立使用；上层模块按需组合
- **响应式栈：** ai 和 agent-core 基于 Reactor 实现流式 LLM 交互
- **多 Provider：** ai 模块支持 Anthropic、OpenAI、Google、Bedrock、Mistral 五个 LLM 供应商
- **CLI 框架：** 顶层使用 Picocli + Spring Boot，支持交互式 TUI、单次执行、Server/RPC 等多种运行模式
- **可扩展性：** coding-agent-cli 提供 Skill 和 Extension 两套扩展机制
