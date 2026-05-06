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

### Imports（Spotless 自动处理）
分组顺序 `java`, `javax`, `com`, `org`, *，未使用的 import 会被自动删除。

### 圈复杂度（CC ≤ 15）
方法的 cyclomatic complexity 不得超过 15（`switchBlockAsSingleDecisionPoint=true`，整段 switch 算 1 个决策点）。超阈值的方法必须拆分提取，**不要新增 `@SuppressWarnings("checkstyle:huge_cyclomatic_complexity")`**——存量带此注解的是历史欠债，等待重构，不是范例。

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
