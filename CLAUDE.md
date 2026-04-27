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
- Spotless is enforced via `spotless-maven-plugin`; run `./mvnw spotless:apply` before committing or CI-equivalent checks will diverge.
- Tests use JUnit 5 + Mockito + OkHttp `MockWebServer` (for provider integration tests).
- User config lives at `~/.campusclaw/settings.json` — not `~/.pi/` despite the legacy `.pi/` dir in the repo.

## Git workflow

The repo merges PRs with **Squash and merge**, which rewrites commit SHAs. Consequences:

- **One branch per change, deleted after merge.** Never reuse a feature branch after its PR is squash-merged — `git log origin/main..HEAD` will keep showing the old SHAs as "unmerged" even though the content is in main.
- **Always branch from latest main.** Before a new change: `git checkout main && git pull`, then `git checkout -b <type>/<slug>`.
- **Branch naming**: `fix/`, `feat/`, `chore/`, `refactor/`, `test/`, `docs/` + kebab-case slug (e.g. `fix/bash-windows-compat`).
- **Commit hygiene**: stage specific files (avoid `git add -A`); commit messages follow Conventional Commits with **Chinese descriptions** (`type(scope): 中文描述`); run `./mvnw spotless:apply` before committing.
- **After a PR merges**: `git checkout main && git pull && git branch -d <branch> && git push origin --delete <branch>`.
- **Never force-push to main.** Use `--force-with-lease` only on your own feature branch, and only when the user explicitly asks.

## Reference

- `README.md` — user-facing quickstart, CLI flags, supported providers.
- `docs/module-architecture.md` — authoritative module/package breakdown.
- `ARCHITECTURE-HYBRID.md`, `IMPLEMENTATION-HYBRID.md`, `DOCKER-SANDBOX-GUIDE.md` — hybrid local/sandbox execution design.
- `docs/openapi/campusclaw-api.yaml` — HTTP server mode API (OpenAPI 3, authoritative). `docs/server-api.md` is a deprecated historical snapshot.
- `docs/asyncapi/chat-ws.yaml` — `/api/ws/chat` WebSocket contract (AsyncAPI).
- `modules/*/`+`*-design.md` — per-module design docs (Story/AR format).
