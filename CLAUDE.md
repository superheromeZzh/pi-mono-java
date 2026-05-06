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
- `scripts/sync-mate-campusclaw.sh` + `scripts/sync-mate-exclude.txt` — sync `modules/*` → `mate-campusclaw/` (see "Mate-campusclaw mirror" section).
