# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

CampusClaw (`com.campusclaw`, previously `pi-mono-java`) — a terminal AI coding agent built on JDK 21 + Spring Boot 3.4.1. Maven multi-module. The CLI entry point is `modules/coding-agent-cli`, producing `campusclaw-agent.jar`.

## Build & Run

The project requires **JDK 21** (not 17, not 25). `./mvnw`/`mvnw.cmd` uses whatever `JAVA_HOME` is set; `./campusclaw.sh` auto-detects JDK 21 via Homebrew, `/usr/libexec/java_home -v 21`, SDKMAN, or common Linux paths.

| Command | Purpose |
|---|---|
| `./campusclaw.sh -m glm-5` | Launch the CLI — auto-builds on first run and when Java/YAML/XML sources change |
| `./campusclaw.sh --rebuild ...` | Force rebuild before launch |
| `./mvnw package -pl modules/coding-agent-cli -am -DskipTests` | Build the fat JAR only |
| `./mvnw -pl modules/coding-agent-cli spring-boot:run -Dspring-boot.run.arguments='-m glm-5'` | Dev mode via Spring Boot Maven plugin |
| `./mvnw test` | Run all tests |
| `./mvnw -pl modules/agent-core test -Dtest=AgentLoopTest` | Run a single test class (surefire) |
| `./mvnw -pl modules/agent-core test -Dtest=AgentLoopTest#name` | Run a single test method |
| `./mvnw spotless:apply` | Format code (import order `java,javax,com,org,*`, removes unused imports) |
| `./mvnw verify` | Full build incl. tests |

Notes:
- Surefire runs with `-XX:+EnableDynamicAgentLoading` (configured in root POM) — Mockito needs it on JDK 21.
- The canonical `application.yml` lives at `modules/coding-agent-cli/src/main/resources/application.yml`. Editing it requires a rebuild; `campusclaw.sh` auto-detects yml changes under `modules/` and rebuilds before launch. (There used to be a second copy at the repo root — it was removed; do not reintroduce it.)

## Architecture

Module dependency graph (from `docs/module-architecture.md`):

```
ai ─────────────┐
                ├──→ agent-core ──┬──→ cron ─────┐
tui ────────────┤                 ├──→ assistant ┤
                └─────────────────┴──────────────┴──→ coding-agent-cli
```

| Module | Artifact | Role |
|---|---|---|
| `modules/ai` | `campusclaw-ai` | Unified LLM abstraction. Providers (Anthropic, OpenAI, Google GenAI/Vertex, Bedrock, Mistral, and ~18 OpenAI-compatible flavors) live under `provider/`; types under `types/`; model registry under `model/`. |
| `modules/tui` | `campusclaw-tui` | Terminal UI primitives built on JLine + Lanterna — full-screen renderer, ANSI utilities, components. No internal deps. |
| `modules/agent-core` | `campusclaw-agent-core` | Agent runtime. `Agent` is the façade; `AgentLoop` drives the LLM↔tool cycle; `ToolExecutionPipeline` runs tools with before/after hooks and JSON-schema validation; sealed `AgentEvent` hierarchy emits state transitions. |
| `modules/assistant` | `campusclaw-assistant` | Conversation channel + memory persistence (MyBatis/PostgreSQL). |
| `modules/cron` | `campusclaw-cron` | JobRunr-backed scheduled agent runs, exposed as an `AgentTool` for agents to self-schedule. |
| `modules/coding-agent-cli` | `campusclaw-coding-agent` | Spring Boot + Picocli application integrating everything. Contains the tool implementations (`tool/{read,write,edit,editdiff,bash,glob,grep,ls}`), mode dispatch (`mode/{tui,server,rpc}`), skill loader, session JSONL persistence, slash commands. |

Key runtime concepts:
- **Execution modes**: `--mode interactive|one-shot|rpc|server|print` selects a handler under `codingagent/mode/`. Server exposes HTTP (see `docs/openapi/campusclaw-api.yaml`), RPC uses stdin/stdout JSONL.
- **Hybrid tool execution** (see `ARCHITECTURE-HYBRID.md`): tools have a `Hybrid*` variant that routes between local JVM execution and a Docker sandbox sidecar based on risk. Controlled by `tool.execution.*` in `application.yml` (`default-mode: LOCAL|SANDBOX|AUTO`, `hybrid-enabled`). `LOCAL` is the default in the checked-in `application.yml`; set to `SANDBOX`/`AUTO` only when Docker is available.
- **Extensibility**: two mechanisms layered in `coding-agent-cli` — `skill/` (user-installable skill packs under `~/.campusclaw/packages`) and `extension/` (in-tree `ExtensionPoint` registrations for tools / commands / hooks).
- **Reactive stack**: `ai` and `agent-core` use Reactor `Mono/Flux` throughout for streaming LLM responses. Don't `.block()` on the event stream path.

## Conventions to preserve

- Java 21 features are in active use (records, sealed interfaces, pattern matching) — don't downgrade.
- Spotless is enforced via `spotless-maven-plugin` with **palantirJavaFormat 2.66.0**; run `./mvnw spotless:apply` before committing or CI-equivalent checks will diverge. **Requires JDK 21** (palantir 不兼容 JDK 25 的 javac 内部 API)。
- Tests use JUnit 5 + Mockito + OkHttp `MockWebServer` (for provider integration tests).
- User config lives at `~/.campusclaw/settings.json` — not `~/.pi/` despite the legacy `.pi/` dir in the repo.

## Coding conventions enforced by build

每条规则都是 **build-failing**：违规会让 `./mvnw validate`（Checkstyle）或 `./mvnw spotless:check`（palantirJavaFormat）失败。`scripts/claude-hooks/checkstyle-on-stop.sh` 会在每轮收尾自动跑一遍——但写之前就守规可以避免被钩子拦下、回头改的成本。

### 文件头版权
每个 `.java` 文件第一行起、`package` 之前必须有：

```java
/*
 * Copyright (c) Huawei Technologies Co., Ltd. {YYYY}-{YYYY}. All rights reserved.
 */
```

年份范围正则是 `\d{4}-\d{4}`，单年用同年填两遍即可（如 `2026-2026`）。

### 禁止 TODO/FIXME 注释（交付代码）
正式交付给客户的代码不应包含未完成标记。Checkstyle 规则 `no_todo_fixme_in_delivery_code` 会拒绝任何形如 `// TODO`、`/* FIXME */`、`* TODO`（Javadoc 续行）的注释，覆盖 `src/main/java` 与 `src/test/java`。

未完成或暂缓实现的工作改记录到 [`docs/DEFERRED.md`](docs/DEFERRED.md)；如果是已上线产品的缺陷，请直接在 issue tracker 立单。代码里残留 TODO/FIXME 会让客户误解为已知缺陷。

极端情况下需要保留可用 `@SuppressWarnings("checkstyle:no_todo_fixme_in_delivery_code")` 局部豁免，但 PR review 应当拒绝任何这样的新增——存量为零，不要破例。

### 大括号位置（K&R，palantir 自动整形）
- `{` 永远在行尾，不要单独成行
- `} else {` / `} catch (...) {` / `} finally {` / `} while (...);` 衔接同行
- 单行守卫禁止：`if (cond) { stmt; }` 必须拆成多行

### Locale（大小写转换、数字格式化必须显式传 Locale）
| ❌ | ✅ |
|---|---|
| `s.toLowerCase()` | `s.toLowerCase(Locale.ROOT)` |
| `s.toUpperCase()` | `s.toUpperCase(Locale.ROOT)` |
| `String.format("%.2f", x)` | `String.format(Locale.ROOT, "%.2f", x)` |

机器可读字符串（HTTP header、env var、路径、数字格式）默认 `Locale.ROOT`，仅在确有用户语言场景才换其他 Locale。背景：`"I".toLowerCase()` 在土耳其 locale 下 == `"ı"`。

### 数字字面量后缀
- **long 类型变量赋值的整数字面量必须以 `L` 结尾**（大写）。`60_000` → `60_000L`。避免静默的 int→long 转换。
- 已经是 long 字面量的，必须用大写 `L` 而非小写 `l`（`UpperEll` 强制；小写 `l` 易与数字 `1` 混淆）。
- float 字面量必须以 `f` 或 `F` 结尾（Java 编译器本身就强制）。

### 每行只声明一个变量
Checkstyle 规则 `one_variable_per_declaration`（即 `MultipleVariableDeclarations`）禁止串联声明：

❌ 反例：
```java
int userCount = 0, assistantCount = 0, toolCount = 0;
int m = a.size(), n = b.size();
```

✅ 正例：
```java
int userCount = 0;
int assistantCount = 0;
int toolCount = 0;
```

理由：diff 时新增/删除某个变量会污染其他变量行；IDE 提取/重命名串联声明易遗漏；类型与多份初始化挤一行影响阅读。

注：`for (int i = 0, j = 0; ...; ...)` 不在禁止之列——Checkstyle 默认只校验 `VARIABLE_DEF` 而非 `FOR_INIT`，多变量 for 初始化器是惯用法。

### Imports（Spotless 自动处理）
分组顺序 `java`, `javax`, `com`, `org`, *，未使用的 import 会被自动删除。

**禁止 wildcard import**：`import java.util.*;` 这类会被 `AvoidStarImport` 拒绝，必须显式列出每个用到的类。

**结构空行**：版权头 / package / imports / 类之间必须空行分隔，由 `EmptyLineSeparator` 强制（覆盖 PACKAGE_DEF / IMPORT / STATIC_IMPORT 三个 token，避免误伤方法内的 local record/class）。

### 圈复杂度（CC ≤ 15）
方法的 cyclomatic complexity 不得超过 15（`switchBlockAsSingleDecisionPoint=true`，整段 switch 算 1 个决策点）。超阈值的方法必须拆分提取，**不要新增 `@SuppressWarnings("checkstyle:huge_cyclomatic_complexity")`**——存量带此注解的是历史欠债，等待重构，不是范例。

### 后台线程必须装 UncaughtExceptionHandler（软约束，全员遵守）

**不**由 Checkstyle 拦截——AST 级判断「`new Thread(...)` 后是否在 `start()` 之前调用了 `setUncaughtExceptionHandler`」复杂度与收益不成正比，靠规范+评审保证。但凡发现存量违规，与硬规则同等优先级修复。

**规则**：任何直接 `new Thread(...)` 实例化（包括 `ThreadFactory` lambda 内部那行 `new Thread(r, "name")`）都**必须**在线程 `.start()` 之前调用 `setUncaughtExceptionHandler`。默认走仓库提供的单例：

```java
import com.campusclaw.agent.util.LoggingUncaughtExceptionHandler;
```

只有线程真有定制错误处理需求（例如失败要重启、要回写状态）才另写 handler。

**理由**：不装 handler，后台线程抛未捕获异常 JVM 默认只打到 `System.err` 并悄悄终止线程——心跳、流读取、调度任务在生产可能静默瘫痪，日志/告警拿不到任何信号。

✅ 正例（独立线程）：
```java
Thread drainer = new Thread(this::drain, "bash-output-drainer");
drainer.setDaemon(true);
drainer.setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.INSTANCE);
drainer.start();
```

✅ 正例（ThreadFactory lambda）：
```java
Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "cron-engine");
    t.setDaemon(true);
    t.setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.INSTANCE);
    return t;
});
```

❌ 反例（裸链式，handler 没法挂）：
```java
new Thread(task).start();                              // 没装 handler
new Thread(task, "worker").start();                    // 同上
```

链式 `new Thread(...).start()` 必须先拆成局部变量再装 handler——这是「方便挂 handler」的硬要求，不是风格偏好。

**范围**：本规则只覆盖 `new Thread(...)` 直接实例化。`Executors.newFixedThreadPool(...)` 默认 thread factory、`CompletableFuture.runAsync(...)` / `ForkJoinPool.commonPool()` 等共享池走 Future 异常路径，不在此规则覆盖范围。

### 任何方法的 Javadoc 写了就要写全
**不强制**方法必须有 Javadoc——但只要写了，就必须 tag 齐全且与签名一致。规则覆盖所有访问级别（public / protected / package-private / private），原则是「写就写好，不写不查」。

具体语义：
- 所有参数都必须有 `@param`（缺一个就报错）
- 非 void 方法必须有 `@return`
- 签名声明的受检异常必须有 `@throws X`（`validateThrows=true`）
- tag 与签名不一致仍然报错（如 `@param x` 但参数叫 `message`）
- **未写 Javadoc 的方法不报错**

❌ 反例（Javadoc 描述给了，tag 缺）：

```java
/** Inference rule shared with ConversationLister. */
static String inferRole(Map<String, Object> message) {  // 报错：缺 @param message + @return
    ...
}
```

✅ 正例 A（tag 齐全）：

```java
/**
 * Returns the number of buffered characters.
 *
 * @return current buffer length in chars
 */
public int size() {
    return buffer.length();
}
```

✅ 正例 B（干脆不写 Javadoc，自然不报错）：

```java
static String inferRole(Map<String, Object> message) {
    ...
}
```

### 顶层 public 类必须有 Javadoc + @version
新增 public class / interface / enum / record / @interface 时，类声明前必须有 Javadoc，且包含 `@version` 标签：

```java
/**
 * Asynchronously transforms agent messages before they are sent to the model.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface ContextTransformer {
```

`@version` 格式正则 `\[br_eCampusCore [^,\]]+,\s*\d{4}/\d{2}/\d{2}\]`。`@since` 不强校验但建议同时写。功能描述用**英文**（与现有约定一致，AI / IDE 工具都按英文 Javadoc 优化）。

### 写完 Java 之后
Stop 钩子会自动跑 `spotless:check` + `checkstyle:check`。主动修复：

```bash
./mvnw -q spotless:apply checkstyle:check
```

（前提：`JAVA_HOME` 指向 JDK 21；`~/.zshrc` 已配置默认走 21。）

## Mate-campusclaw mirror

`mate-campusclaw/` is a single-module mirror of `modules/*` maintained for integration into a corporate `mate` parent project. Package is rewritten `com.campusclaw` → `com.huawei.hicampus.mate.matecampusclaw`. The mirror is **generated** — make changes in `modules/*`, then sync.

| Command | Purpose |
|---|---|
| `./scripts/sync-mate-campusclaw.sh` | Stage from `modules/*`, apply to `mate-campusclaw/`, run `mvn compile` to verify |
| `./scripts/sync-mate-campusclaw.sh --dry-run` | Show what apply would change without writing |
| `./scripts/sync-mate-campusclaw.sh --no-apply` | Only stage to `build/mate-campusclaw/`; leave `mate-campusclaw/` untouched |
| `./scripts/sync-mate-campusclaw.sh --no-verify` | Skip the mvn compile step |

Phases:
1. **Stage** — copy `modules/{ai,tui,agent-core,assistant,cron,coding-agent-cli}` into `build/mate-campusclaw/`, rewriting the package in `.java/.yml/.properties/.imports/...`.
2. **Apply** — `rsync --delete` from `build/` to in-tree `mate-campusclaw/`. Paths listed in `scripts/sync-mate-exclude.txt` are preserved (mate-side-only files that have no counterpart in `modules/*`).
3. **Verify** — compile `mate-campusclaw/` with auto-detected JDK 21 (same lookup order as `campusclaw.sh`).

When adding a new file directly under `mate-campusclaw/` that has no counterpart in `modules/*`, append its path to `scripts/sync-mate-exclude.txt`, otherwise the next `--delete` will remove it. Currently registered: `assistant/config/`, `codingagent/channel/`. Resources `application.properties` and `application-assistant.yml` are hand-tuned per environment — the script never touches them; only `schema.sql` and `META-INF/spring/*.imports` propagate from `modules/*`.

### pre-push guard

`scripts/git-hooks/pre-push` blocks `git push` whenever the push range touches `modules/` or `mate-campusclaw/` and the mirror is out of sync. Activate it once per clone:

```bash
git config core.hooksPath scripts/git-hooks
```

The hook runs the sync script in dry-run + no-verify mode and parses rsync's `--itemize-changes` output. Pushes that don't touch `modules/`, `mate-campusclaw/`, or `scripts/sync-mate*` skip the check. Bypass with `git push --no-verify` when intentional.

## Git workflow

The repo merges PRs with **Merge commit**（保留每个 commit 的真实 SHA 和顺序，main 上额外多一个 merge commit 记录合并这件事）。

- **Branch from latest main for each topic.** `git checkout main && git pull`, then `git checkout -b <type>/<slug>`. 一个分支可以承载一个完整 feature 的多次提交，不需要把每个原子改动拆成单独分支；但不同主题的工作请分到不同分支，避免 PR 又大又杂。
- **Branch naming**: `fix/`, `feat/`, `chore/`, `refactor/`, `test/`, `docs/` + kebab-case slug（例如 `fix/bash-windows-compat`）。
- **Commit hygiene**: stage specific files (avoid `git add -A`); commit messages follow Conventional Commits with **Chinese descriptions** (`type(scope): 中文描述`); run `./mvnw spotless:apply` before committing.
- **保持 PR 与 main 同步**：如果开发期间 main 推进了，在自己分支上 `git fetch origin && git merge origin/main`，或 `git rebase origin/main` + `git push --force-with-lease`。前者无需 force-push 但会引入 merge commit 进 feature 分支，后者保持线性但需要 force-push（仅限自己分支）。
- **合并后清理**: `git checkout main && git pull && git branch -d <branch> && git push origin --delete <branch>`。Merge commit 策略下 git 能正确识别分支已合并，`-d` 即可删除（无需 `-D`）。
- **Never force-push to main.** `--force-with-lease` 只用于自己的 feature 分支，且仅在用户明确要求时使用。

## Reference

- `README.md` — user-facing quickstart, CLI flags, supported providers.
- `docs/module-architecture.md` — authoritative module/package breakdown.
- `ARCHITECTURE-HYBRID.md`, `IMPLEMENTATION-HYBRID.md`, `DOCKER-SANDBOX-GUIDE.md` — hybrid local/sandbox execution design.
- `docs/openapi/campusclaw-api.yaml` — HTTP server mode API (OpenAPI 3, authoritative). `docs/server-api.md` is a deprecated historical snapshot.
- `docs/asyncapi/chat-ws.yaml` — `/api/ws/chat` WebSocket contract (AsyncAPI).
- `modules/*/`+`*-design.md` — per-module design docs (Story/AR format).
- `scripts/sync-mate-campusclaw.sh` + `scripts/sync-mate-exclude.txt` — sync `modules/*` → `mate-campusclaw/` (see "Mate-campusclaw mirror" section).
