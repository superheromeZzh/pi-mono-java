# CampusClaw 定时任务模块 (campusclaw-cron) 设计文档

## Context

为 CampusClaw AI 编程助手添加定时任务能力，参考 OpenClaw 的 cron 扩展思路。用户可通过 LLM 对话创建/管理定时任务，由独立 Agent 实例自动执行。

核心约束：**不修改 agent-core、ai、tui 三个模块的 Java 源码**。cron 作为独立 Maven 模块，仅通过 Spring DI 和 coding-agent-cli 的少量集成代码接入。

## 依赖关系

```
coding-agent-cli ──→ campusclaw-cron ──→ agent-core ──→ ai
                 ──→ agent-core
                 ──→ tui
```

修改范围：
- `pom.xml`（根）：+1 行 module 声明
- `modules/coding-agent-cli/pom.xml`：+1 行依赖
- `InteractiveMode.java`：+4 行（start/stop cron engine）
- `CampusClawCommand.java`：+3 行（注入并传递 CronService）

## OpenClaw 设计对照

| 方面 | OpenClaw 做法 | CampusClaw 对应 |
|------|-------------|----------------|
| 模块关系 | 独立包，不改 pi-mono | 独立 Gradle 模块，不改核心 3 模块 |
| 调度类型 | at / every / cron 表达式 | 相同 |
| 执行模型 | 隔离 session + 主 session | 用 agent-core 的 `Agent` 类直接创建隔离实例 |
| 持久化 | JSON 文件 + JSONL 日志 | 相同 |
| 并发控制 | Promise 锁 + skip-if-running | ReentrantLock + skip-if-running |
| 用户接口 | LLM tool + CLI 命令 | LLM tool（AgentTool） |
| 接入方式 | 依赖注入到 gateway | Spring `@Component` 自动发现 |

## Phase 1: 模块骨架

### 1.1 settings.gradle.kts — 注册模块
```kotlin
include(":modules:campusclaw-cron")
project(":modules:campusclaw-cron").projectDir = file("modules/cron")
```

### 1.2 modules/cron/build.gradle.kts
```kotlin
dependencies {
    api(project(":modules:campusclaw-agent-core"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.springframework:spring-context")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

### 1.3 modules/coding-agent-cli/build.gradle.kts — 添加依赖
```kotlin
api(project(":modules:campusclaw-cron"))
```

无需新增外部依赖。cron 表达式用 Spring 内置 `CronExpression`（spring-context 6.2.1）。

## Phase 2: 数据模型

包路径：`com.campusclaw.cron.model`

### 2.1 CronSchedule.java — sealed 调度类型
```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = At.class, name = "at"),
    @Type(value = Every.class, name = "every"),
    @Type(value = CronExpr.class, name = "cron")
})
public sealed interface CronSchedule {
    record At(long timestampMs) implements CronSchedule {}
    record Every(long intervalMs) implements CronSchedule {}
    record CronExpr(String expression, @Nullable String timezone) implements CronSchedule {}
}
```
`CronExpr` 由 Spring `CronExpression.parse()` 验证。

### 2.2 CronPayload.java — sealed 任务内容
```java
public sealed interface CronPayload {
    record AgentPrompt(
        String prompt,
        @Nullable String systemPrompt,
        @Nullable String modelId,
        @Nullable List<String> allowedTools
    ) implements CronPayload {}
}
```
仅保留 `AgentPrompt` 一种变体。未来如需扩展（如 webhook、shell command），sealed interface 天然支持。

### 2.3 CronJob.java — 核心实体
```java
public record CronJob(
    String id, String name, @Nullable String description,
    boolean enabled, boolean deleteAfterRun,
    CronSchedule schedule, CronPayload payload,
    CronJobState state, long createdAtMs
) {}
```

### 2.4 CronJobState.java — 运行时状态
```java
public record CronJobState(
    long nextRunAtMs, long runningAtMs,
    long lastRunAtMs, @Nullable String lastRunStatus,
    int consecutiveErrors, int totalRuns
) {}
```

### 2.5 CronRunRecord.java — 单次运行记录
```java
public record CronRunRecord(
    String runId, String jobId,
    long startedAtMs, long finishedAtMs,
    RunStatus status, @Nullable String error, int turnCount
) {
    public enum RunStatus { RUNNING, SUCCESS, FAILED, CANCELLED }
}
```

### 2.6 CronEvent.java — 引擎事件
```java
public sealed interface CronEvent {
    record JobStarted(String jobId, String jobName, String runId) implements CronEvent {}
    record JobCompleted(String jobId, String jobName, String runId) implements CronEvent {}
    record JobFailed(String jobId, String jobName, String runId, String error) implements CronEvent {}
}
```

## Phase 3: 持久化

### 3.1 CronStore.java (`@Service`)
- 位置：`~/.campusclaw/agent/cron/jobs.json`
- 格式：`{ "version": 1, "jobs": [...] }`
- 线程安全：`ReentrantReadWriteLock`
- 方法：`load()`, `save()`, `addJob()`, `removeJob()`, `updateJob()`
- Jackson ObjectMapper 序列化，同项目已有的 SessionManager 模式

### 3.2 CronRunLog.java (`@Service`)
- 位置：`~/.campusclaw/agent/cron/runs/{jobId}.jsonl`
- Append-only JSONL 格式
- 方法：`appendRun(CronRunRecord)`, `getRecentRuns(jobId, limit)`

## Phase 4: 执行引擎

### 4.1 CronEventListener.java — 通知回调
```java
@FunctionalInterface
public interface CronEventListener {
    void onCronEvent(CronEvent event);
}
```

### 4.2 CronJobExecutor.java (`@Service`)

核心执行流程：
1. `new Agent(piAiService)` 创建隔离实例（公开构造器创建全新 AgentState/executionLock，线程安全）
2. 通过 `ModelRegistry` 解析 modelId → Model（遍历 providers 精确匹配）
3. 按 `allowedTools` 过滤注入的 `List<AgentTool>`
4. 设置 system prompt / model / tools
5. 调用 `agent.prompt(prompt)` 后 `waitForIdle().get(timeout, SECONDS)`
6. 超时调 `agent.abort()`，默认 300 秒

依赖注入：`CampusClawAiService`, `ModelRegistry`, `CronRunLog`, `List<AgentTool>`

### 4.3 CronEngine.java (`@Service`)

**不用 SmartLifecycle**（会在所有模式下自动启动，包括 one-shot 和 --list-models）。改为显式 `start()/stop()`，由 InteractiveMode 调用。

调度机制：
- 单线程 `ScheduledExecutorService` 驱动 tick
- `CronSchedule.At`：计算延迟，单次 `schedule()`
- `CronSchedule.Every`：`scheduleAtFixedRate()`
- `CronSchedule.CronExpr`：手动链式调度 — `CronExpression.next()` 算出下次时间，`schedule()` 单次任务，完成后再算+再调度

并发控制：

| 机制 | 实现 |
|------|------|
| Store 读写锁 | `ReentrantReadWriteLock` |
| Tick 互斥 | `ReentrantLock` |
| Skip-if-running | `runningAtMs != 0` 跳过 |
| 指数退避 | `min(1000 * 2^consecutiveErrors, 3600000)` ms |
| 自动禁用 | 连续 3 次错误 → `enabled = false` |
| 陈旧标记 | 启动时清除 >2h 的 runningAtMs |

维护 `Map<String, ScheduledFuture<?>>` 用于取消/重调度。

## Phase 5: 门面

### 5.1 CronService.java (`@Service`)

协调 CronStore + CronEngine，提供统一 API：
- `createJob(name, schedule, payload) -> CronJob`
- `deleteJob(jobId)`, `listJobs()`, `getJob(jobId)`
- `enableJob(jobId)`, `disableJob(jobId)`
- `triggerJob(jobId) -> CronRunRecord`（立即执行）
- `getRecentRuns(jobId, limit)`
- `start()`, `stop()`, `addListener(CronEventListener)`

## Phase 6: LLM 工具

### 6.1 CronTool.java (`@Component implements AgentTool`)

Spring 自动发现，注入到 `CampusClawCommand` 的 `List<AgentTool>`。

- name: `"cron"`, label: `"Cron"`
- actions: `create` / `list` / `delete` / `trigger` / `status` / `runs`
- `create` 参数：name, schedule_type (at/every/cron), schedule_value, prompt, model?, system_prompt?, tools?
- `delete`/`trigger`/`status`/`runs` 参数：job_id

## Phase 7: 集成

### 7.1 CampusClawCommand.java
- 注入 `@Nullable CronService cronService`（`@Autowired(required = false)`）
- 传递给 InteractiveMode 构造器

### 7.2 InteractiveMode.java
- 构造器增加 `@Nullable CronService cronService` 参数
- `run()` 方法中 tui 初始化后：`if (cronService != null) cronService.start();`
- finally 块中：`if (cronService != null) cronService.stop();`

## 模块包结构

```
modules/cron/src/main/java/com/campusclaw/cron/
├── model/
│   ├── CronSchedule.java          # sealed: At / Every / CronExpr
│   ├── CronPayload.java           # sealed: AgentPrompt
│   ├── CronJob.java               # record: 核心实体
│   ├── CronJobState.java          # record: 运行时状态
│   ├── CronRunRecord.java         # record: 单次运行记录
│   └── CronEvent.java             # sealed: 引擎事件
├── store/
│   ├── CronStore.java             # @Service JSON 文件持久化
│   └── CronRunLog.java            # @Service JSONL 运行日志
├── engine/
│   ├── CronEventListener.java     # @FunctionalInterface 事件回调
│   ├── CronJobExecutor.java       # @Service Agent 执行器
│   └── CronEngine.java            # @Service 调度引擎
├── tool/
│   └── CronTool.java              # @Component implements AgentTool
└── CronService.java               # @Service 门面
```

```
modules/cron/src/test/java/com/campusclaw/cron/
├── model/CronScheduleTest.java
├── store/CronStoreTest.java
├── engine/CronEngineTest.java
└── tool/CronToolTest.java
```

## 文件清单

| # | 文件 | 操作 |
|---|------|------|
| 1 | `settings.gradle.kts` | 改 +2 行 |
| 2 | `modules/cron/build.gradle.kts` | 新建 |
| 3-8 | `modules/cron/src/.../model/*.java` | 新建 6 文件 |
| 9-10 | `modules/cron/src/.../store/*.java` | 新建 2 文件 |
| 11-13 | `modules/cron/src/.../engine/*.java` | 新建 3 文件 |
| 14 | `modules/cron/src/.../CronService.java` | 新建 |
| 15 | `modules/cron/src/.../tool/CronTool.java` | 新建 |
| 16 | `modules/coding-agent-cli/build.gradle.kts` | 改 +1 行 |
| 17 | `InteractiveMode.java` | 改 +4 行 |
| 18 | `CampusClawCommand.java` | 改 +3 行 |
| T1-T4 | `modules/cron/src/test/.../` | 新建 4 测试文件 |

## 实现顺序

1. Phase 1 — 模块骨架 → `./gradlew :modules:campusclaw-cron:build` 编译通过
2. Phase 2 — 数据模型 → Jackson 序列化往返测试
3. Phase 3 — 持久化 → CronStore CRUD + 临时目录测试
4. Phase 4 — 执行引擎 → Mock Executor 验证 tick + 调度逻辑
5. Phase 5 — CronService 门面
6. Phase 6 — CronTool → 单元测试 action 路由
7. Phase 7 — 集成 InteractiveMode → 端到端

## 验证方案

1. **构建**：`./gradlew :modules:campusclaw-cron:build`
2. **单元测试**：Jackson 序列化、CronStore CRUD、调度时间计算、CronTool action 路由
3. **集成测试**：`EverySchedule(100ms)` + Mock CronJobExecutor 验证 tick 触发
4. **端到端**：`./campusclaw.sh` 启动 → 对话中用 cron tool 创建任务 → 等待执行 → 查看运行日志

## 关键参考文件

- `modules/agent-core/.../Agent.java` — 公开构造器 line 65-77，隔离实例创建
- `modules/ai/.../CampusClawAiService.java` — 注入到 executor
- `modules/ai/.../model/ModelRegistry.java` — 模型解析
- `modules/coding-agent-cli/.../cli/CampusClawCommand.java` — `List<AgentTool>` 注入模式 line 62-73
- `modules/coding-agent-cli/.../mode/InteractiveMode.java` — start/stop 集成点 line 135, 576-578
