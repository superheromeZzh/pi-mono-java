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
tui ────────────┤                 │              │
                └─────────────────┴──────────────┴──→ coding-agent-cli
```

| Module | Artifact | Role |
|---|---|---|
| `modules/ai` | `campusclaw-ai` | Unified LLM abstraction. Providers (Anthropic, OpenAI, Google GenAI/Vertex, Bedrock, Mistral, and ~18 OpenAI-compatible flavors) live under `provider/`; types under `types/`; model registry under `model/`. |
| `modules/tui` | `campusclaw-tui` | Terminal UI primitives built on JLine + Lanterna — full-screen renderer, ANSI utilities, components. No internal deps. |
| `modules/agent-core` | `campusclaw-agent-core` | Agent runtime. `Agent` is the façade; `AgentLoop` drives the LLM↔tool cycle; `ToolExecutionPipeline` runs tools with before/after hooks and JSON-schema validation; sealed `AgentEvent` hierarchy emits state transitions. |
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
- User config lives at `~/.campusclaw/agent/settings.json` — not `~/.pi/` despite the legacy `.pi/` dir in the repo.

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

### 字符 ↔ 字节转换必须显式指定编码

**规则**：任何把 `byte[]`/`InputStream`/`OutputStream` 与 `String`/`Reader`/`Writer` 互转的 API 都必须显式传 `Charset`，统一用 `StandardCharsets.UTF_8`（除非协议本身要求其他编码，如 `Windows-1252` 解析 legacy 文件——这种情况要在注释里写明出处）。

**硬约束**（Checkstyle 在 `codecheck.xml` 中以 `RegexpSinglelineJava` 强制，build-failing）：

| 规则 id | 命中 |
|---|---|
| `explicit_charset_get_bytes` | 无参 `.getBytes()` |
| `explicit_charset_input_stream_reader` | 整行无 `Charset/UTF_8` 的 `new InputStreamReader(...)` |
| `explicit_charset_output_stream_writer` | 同上 `new OutputStreamWriter(...)` |
| `explicit_charset_file_reader` | 同上 `new FileReader(...)` |
| `explicit_charset_file_writer` | 同上 `new FileWriter(...)`（包括双参追加形式） |

检测思路：行内出现违规调用且整行没有 `StandardCharsets` / `Charset` / `UTF_8` / `UTF_16` / `US_ASCII` / `ISO_8859` 任一字样即报错——`Charset.forName(...)`、`, StandardCharsets.UTF_8)` 都豁免。副作用是调用被换行折断时（`new InputStreamReader(` 与参数分两行）会误报，把调用合到单行即可（与 palantir 默认行为一致）。

**软约束**（规范要求，靠 review 把关；regex 难以无误判）：

| 场景 | 建议 |
|---|---|
| `new String(bytes)` | 显式 `new String(bytes, StandardCharsets.UTF_8)`；regex 难与合法的 `new String(char[])` / `new String(String)` 区分 |
| `new PrintWriter(out)` | `new PrintWriter(out, false, StandardCharsets.UTF_8)`；单参的 `PrintWriter(Writer)` 是合法重载，regex 无法看类型 |
| `new Scanner(in)` | `new Scanner(in, StandardCharsets.UTF_8)`；`new Scanner(String)` 把 String 当源文本，无编码概念 |
| `Files.readString(path)` | 显式 `Files.readString(path, StandardCharsets.UTF_8)`，即便 JDK 默认即 UTF-8 也写出来 |

**理由**：进程读取子进程输出、HTTP 响应、文件、`System.in` 时，若依赖平台默认 charset，Windows 上中文环境曾是 GBK，容器/CI 偶尔仍会出现非 UTF-8 默认；一旦命中，UTF-8 编码的字节流被按 GBK 解码就是不可恢复的乱码。JDK 18+ 已经把默认 charset 统一为 UTF-8（JEP 400），但仍可被 `-Dfile.encoding=...` 或老 JDK 行为覆盖。显式 `StandardCharsets.UTF_8` 让代码在所有 JVM 上行为一致，且 grep 排查时一目了然。

✅ 正例：
```java
try (var reader = new BufferedReader(
        new InputStreamReader(response.body(), StandardCharsets.UTF_8))) { ... }

String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
```

❌ 反例：
```java
new InputStreamReader(response.body())                        // 依赖平台默认
new String(process.getInputStream().readAllBytes())           // 同上
"payload".getBytes()                                          // 同上
```

注意 `new String(char[])`、`String.toCharArray()`、`Character.toChars(cp)` 这些 `char[] ↔ String` 之间的互转**不在此规则**——`char[]` 本身已经是 UTF-16 code unit 序列，不涉及编码协商，无需也无法传 charset。

### 记录日志必须走 SLF4J 门面，禁用 System.out/err 和 printStackTrace

**规则**：所有「记录日志」诉求一律用 SLF4J（`org.slf4j.Logger`）。`System.out.print*` / `System.err.print*` / `Throwable.printStackTrace()` 一律禁止——除非该类承担的就是 CLI 用户输出（Picocli 命令）、协议 stdout/stderr 输出（Print/Rpc 模式）、TUI 终端渲染、启动 banner 等「stdout/stderr 本就是接口」的角色。这类类必须在类声明上加 `@SuppressWarnings("checkstyle:no_system_out_err")` 显式声明意图——这样 reviewer 一眼能看出「这是协议/UI 输出，不是日志」。

**硬约束**（Checkstyle，build-failing）：

| 规则 id | 命中 |
|---|---|
| `no_system_out_err` | `System.out.(print\|println\|printf\|format\|append\|write)(...)`、`System.err.*` 同形 |
| `no_print_stack_trace` | `.printStackTrace(...)` |

检测：`RegexpSinglelineJava` 在 TreeWalker 内逐行扫描，`ignoreComments=true` 让 javadoc / 注释里提及 `System.out.println(...)` 不被误判。`System.out` / `System.err` 作为流引用（如 `var saved = System.out;`、`PrintStream out = System.err`）不触发——只有调用 `print*/format/append/write` 方法才报错。

**为什么不能用 `System.out` 当日志**：
- 没有时间戳/线程/级别信息——出问题时定位困难
- 不走 logback/log4j 配置，无法被 appender 收集到文件 / ELK
- 容器化部署里 stdout 与协议输出（JSONL/RPC）混流，污染契约
- 异常堆栈用 `printStackTrace()` 直接打到 stderr，没有上下文，且测试环境难以静默

✅ 正例（记日志走门面）：
```java
private static final Logger log = LoggerFactory.getLogger(MyService.class);

log.info("session opened: id={}", sessionId);
log.error("failed to publish event", e);
```

✅ 合法 stdout/stderr 出口（必须类级豁免，让意图显式）：
```java
@SuppressWarnings("checkstyle:no_system_out_err")
@CommandLine.Command(name = "list-models")
public class ListModelsCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Available models:");
        models.forEach(m -> System.out.println("  " + m));
    }
}
```

❌ 反例（这些都应该走 SLF4J）：
```java
System.err.println("Failed to publish event: " + e.getMessage());  // 改 log.error("...", e)
e.printStackTrace();                                                // 改 log.error("...", e)
System.out.println("DEBUG: state = " + state);                      // 改 log.debug("state={}", state)
```

**理由**：项目已普遍引入 SLF4J（128 个文件），`LoggingUncaughtExceptionHandler` 已经把后台线程未捕获异常导到 `log.error`。日志型 print 残留在代码里就是可观测性漏洞——上规则一次性堵住，并把「stdout 是契约」的少数类用注解显式标注，杜绝增量。`new InteractiveMode` 路径里类似的事件发布失败、`InteractiveMode#close` 兜底分支，这些都应当走 `log.error("...", e)`，不要在用户终端噪音里再混入诊断信息。

### 禁止 catch 块静默吞异常（必须有真实处理）

**规则**：`catch (...) { }` 与 `catch (...) { /* 只有注释 */ }` 一律拒绝——前者直接吞，后者是 signed silence（看似有意为之，但对运行时可观测性仍是零）。故障一旦发生，日志/监控/复现路径全部失声，溯源只能靠猜。

合规处理（catch 体必须出现以下之一）：

1. **交给 SLF4J**：`log.debug/warn/error("contextual message", e)`——异常进入日志框架链路，能被 appender 收集、能在测试里静默、带上下文（logger 名、线程、级别、时间戳）；
2. **恢复中断位**：`Thread.currentThread().interrupt();`——吞 `InterruptedException` 必备，否则上游的 cancellation / shutdown 信号被静默丢弃；
3. **包装重抛**：`throw new ...Exception(e)`——把底层异常转成调用方能识别的抽象层次（见 [[no_throw_runtime_exception]]）；
4. **状态修复**：`flag = true;` / `map.put(...)` / `return defaultValue;` 等实际推进控制流的语句。

注释仍然欢迎写，但**注释 ≠ 处理**——必须额外有上述之一。

**硬约束**（两条联动，均 build-failing）：

| 规则 id | 触发 |
|---|---|
| `no_empty_catch_block` | catch 体完全为空 `{ }`（包括 `catch (Exception ignored) {}`） |
| `no_silent_catch` | catch 体只有空白和注释，无任何实际语句（RegexpMultiline 跨行匹配） |

变量名豁免留默认 `^$`——即便起名 `ignored`/`expected` 也照样拒，强制把日志写出来。

| ✅ 正例 | ❌ 反例 |
|---|---|
| `catch (IOException e) { log.warn("failed to flush cache", e); }` | `catch (IOException e) { }` |
| `catch (InterruptedException e) { Thread.currentThread().interrupt(); log.debug("...", e); }` | `catch (Exception ignored) { /* swallow */ }` |
| `catch (IOException e) { /* expected on cancel */ log.debug("drain closed", e); }` | `catch (NumberFormatException ignored) { // skip unknown }` |
| `catch (ParseException e) { throw new ConfigLoadException("bad cron expr", e); }` | `catch (IOException ignored) {}` |

**理由**：静默 catch 是可观测性的最大单点漏洞——比 `printStackTrace`、比 `System.err.println` 都糟，因为后两者至少在 stderr 留下痕迹，静默 catch 连这个都没有。CLAUDE.md 早期版本允许「无害 fall-through + 注释」作为合规出口（仅 `no_empty_catch_block`），实践证明这条豁免容易被滥用——「best-effort」「fall through」这类注释往往写完就忘，等到生产环境真的爆问题时一行 stack 都没有。所以 2026-05 收紧：增加 `no_silent_catch` 把这条豁免也堵上，要"有意忽略"就必须落到 `log.debug` 里——成本极低（一行 + e 参数），但运行时凭证完整。本仓 PR review 应当拒绝任何形如 `} catch (...) { /* ... */ }` 的新增——存量在引入规则时已清零，规则上锁防止回潮。

### 抛出的异常必须与方法抽象层次匹配（禁止 throw new RuntimeException）

**规则**：方法抛出的异常类型应与该方法本身所处的抽象层次对应——文件持久化层抛 `UncheckedIOException`，业务状态校验层抛 `IllegalStateException`，参数校验抛 `IllegalArgumentException`，领域特定错误抛对应的领域异常。**禁止** `throw new RuntimeException(...)`：它是所有 unchecked 异常的根，过于笼统，永远无法表达任何方法的抽象层次——调用方拿到它既无法 catch 到「文件失败」「状态非法」这种特定语义，也丢失了「这是什么性质的错误」的结构化信息，监控和日志聚合也无从分类。

**硬约束**（Checkstyle `no_throw_runtime_exception`，build-failing；存量 0，规则上锁防止回潮）：

| 规则 id | 命中 |
|---|---|
| `no_throw_runtime_exception` | `throw new RuntimeException(...)`——`\bthrow\s+new\s+RuntimeException\s*\(` |

正则锚定到 `throw new` 语句，不会误命中 catch 子句、`throws` 声明、`Class<? extends RuntimeException>` 类型引用；子类（`MyRuntimeException`、`SkillLoadException extends RuntimeException`）也不会被误判。其他三个根类（`Exception` / `Throwable` / `Error`）当前存量为 0，由 PR review 把关——若未来需要补硬规则，扩展同一正则即可。

**推荐替换表**：

| 场景 | 替换 |
|---|---|
| catch `IOException`（包括 `Files.*`、socket、stream 失败） | `throw new UncheckedIOException(msg, e)` |
| 类被构造或方法被调用时进入了「不该出现的状态」（如 schema 构造失败、初始化条件违反、状态机非法转移） | `throw new IllegalStateException(msg, e)` |
| 入参不合法（null、out-of-range、格式不符） | `throw new IllegalArgumentException(msg)` |
| 容器中找不到必需键 | `throw new NoSuchElementException(msg)` |
| 不支持的运行时操作（如未实现的协议分支） | `throw new UnsupportedOperationException(msg)` |
| 仓库领域错误 | 现有 `SkillLoadException` / `SkillInstallException` / `SessionPersistenceException` / `SubAgentException` 等；确实需要新类型才新建 |

| ✅ 正例 | ❌ 反例 |
|---|---|
| `throw new UncheckedIOException("Failed to write skill state file: " + file, e);` | `throw new RuntimeException("Failed to write skill state file: " + file, e);` |
| `throw new IllegalStateException("Failed to build EditDiff schema", e);` | `throw new RuntimeException("Failed to build EditDiff schema", e);` |
| `throw new SkillLoadException("Skill manifest missing entry: " + name);` | `throw new RuntimeException("Skill manifest missing entry: " + name);` |
| `throw new IllegalArgumentException("port out of range: " + port);` | `throw new RuntimeException("bad port: " + port);` |

**理由**：用 `RuntimeException` 抛出错误等价于「我不愿意花一秒钟去想这个错误属于哪一类」——调用方为了区分语义只能 `e.getMessage().contains("Failed to write")` 这种字符串嗅探，与「类型即文档」的初衷背离。本仓已经有清晰的两条主路径：`UncheckedIOException`（5 处，包 IOException）+ `IllegalStateException`（14 处，断言式失败）；以及 6 个领域异常类。规则上锁是为了让后续提交一开始就走对路径，而非等 review 阶段返工。测试代码里 `throw new RuntimeException("not supposed to reach")` 应改写为 `throw new AssertionError(...)`——后者才是 JUnit 5 / AssertJ 体系里「断言失败」的正确表达。

### 测试代码必须存在真实断言，禁止虚假断言

**规则**：测试方法里出现的 `assert*` / `assertThat(...)` 调用必须实际验证被测代码产生的值。语义上恒成立、不引用任何被测变量的「虚假断言」一律拒绝——它在 CI 上永远绿，看似有覆盖率实则什么都没测，是单测里最隐蔽的「无效资产」。

**硬约束**（Checkstyle，build-failing；存量 0，规则上锁防止回潮）：

| 规则 id | 命中 |
|---|---|
| `no_fake_assertion_constant` | `assertTrue(true)` / `assertFalse(false)` / `assertNull(null)`（含 2-arg 带 message 形式） |
| `no_fake_assertion_self_compare` | `assert{Equals,Same,NotEquals,NotSame}(x, x)`——两侧字面完全相同（正则用 `\1` 反向引用严格匹配） |
| `no_fake_assertion_assertj_literal` | `assertThat(true|false|null).is{True,False,Null,NotNull}` |

| ✅ 正例 | ❌ 反例 |
|---|---|
| `assertEquals(expected, service.compute(input))` | `assertEquals(1, 1)` |
| `assertTrue(result.isPresent())` | `assertTrue(true)` |
| `assertThat(parsed.tokens()).hasSize(3)` | `assertThat(true).isTrue()` |
| `assertNull(cache.get(missingKey))` | `assertNull(null)` |
| `assertNotEquals(originalHash, mutatedHash)` | `assertNotEquals("a", "a")` |

**覆盖范围**：`src/main` 与 `src/test/java` 都生效——生产代码里 `assertTrue(true)` 同样是 dead code（多半是误删条件后的残余）。`ignoreComments=true` 让 javadoc 举反例不被误判。

**软约束**（Checkstyle regex 跨方法体判定不稳健，硬规则没法上锁——但和硬规则同档次，写测试时必须遵守，PR review 拒绝任何违例）：

#### 软约束 1：`@Test` 方法必须至少有一处真实断言

`@Test`（含 `@ParameterizedTest` / `@RepeatedTest`）方法体内必须出现以下任一调用：
- JUnit 5：`assert*`（含 `assertDoesNotThrow` / `assertThrows`）、`fail(...)`；
- AssertJ：`assertThat(...).xxx(...)`、`assertThatThrownBy(...)`、`assertThatNoException()...`；
- Mockito：`verify(...)`、`verifyNoInteractions(...)`、`verifyNoMoreInteractions(...)`、`inOrder(...).verify(...)`；
- Reactor `StepVerifier`：`.expectNext(...)`、`.expectError(...)`、`.verifyComplete()`、`.verifyError(...)`。

「这个方法跑通就算过」不是断言。**意图是「不抛异常」就显式 `assertDoesNotThrow(() -> ...)` 或 `assertThatNoException().isThrownBy(() -> ...)`**，让意图变成可失败的断言。

| ✅ 正例 | ❌ 反例 |
|---|---|
| `assertThatNoException().isThrownBy(client::shutdown);` | `client.shutdown(); // Should not throw` |
| `assertDoesNotThrow(() -> registry.forgetSession(s));` | `registry.forgetSession(s); // not tracked → no-op` |

#### 软约束 2：断言不能只有 `assertNotNull` / `isNotNull`

一个 `@Test` 方法的**所有断言**加起来只检查「非 null」时——典型是 `assertNotNull(x)` 单独出现，或 `assertNotNull(x); assertTrue(x.isEmpty())` 这种 `assertNotNull` + 紧跟一个 `assertTrue/assertEquals(x.isEmpty()/.size())` 让 null-check 变成纯冗余——一律视为「没有真实断言」。

原因：JVM 保证 `new X(...)` 永不返回 null；`Optional` / `Stream` / 集合 accessor 的契约也是「永不 null」；只检查非空等于什么都没测。要么换成真实值断言，要么删掉那个 null 检查、把后续的真实断言留住。

| ✅ 正例 | ❌ 反例 |
|---|---|
| `assertEquals("hint", ac.getInput().getPlaceholder());` | `assertNotNull(ac.getInput());`（单独出现） |
| `assertTrue(suggestions.isEmpty());` | `assertNotNull(suggestions); assertTrue(suggestions.isEmpty());` |
| `assertDoesNotThrow(() -> resolver.resolve(p));` | `Optional<String> r = resolver.resolve(p); assertNotNull(r);` |

#### 软约束 3：断言不能用被测代码自身的副产物自比

`assertEquals(service.compute(x), service.compute(x))` 两次调用结果自比——绕过 Checkstyle 反向引用检测，但语义同样空洞，review 时拒。

#### 盘点命令

写完一批单测后，跑下面两条命令自检（也可以挂进 PR review checklist）：

```bash
# 1. 找完全没有任何断言/verify/fail 的测试文件（粗筛）
grep -rL 'assert\|verify\|fail(' modules/**/src/test/java --include='*.java'

# 2. 跑 java-ut-coverage-loop skill 自带的 quality checker（精确）
~/.claude/skills/java-ut-coverage-loop/scripts/check_test_quality.py \
    modules/**/src/test/java/**/<NewTest>.java
```

`check_test_quality.py` 覆盖 `no-assertion`（软约束 1）、`only-not-null-assert`（软约束 2）、`tautological-assert`（硬规则）、`stub-on-sut`、`tests-private-via-reflection` 等多条规则，输出 JSON。补完单测**必须**跑这一遍，errors > 0 不算完工。

**理由**：虚假断言比缺单测更糟。缺单测时大家心里有数、覆盖率指标也会报警；虚假断言把覆盖率刷上去，给团队和审计者一个"已被测试"的假信号，问题真的发生时反而失去最关键的"测试早就该发现"那一层防御。AI 生成的测试代码尤其容易出 `assertTrue(true)` 这种占位——规则上锁是为了让这类提交在 build 阶段就被拦下，不进入 review 噪音。

### 日志消息禁用中文（含标点）

**规则**：所有 SLF4J 日志调用——`log.trace/debug/info/warn/error`、`logger.log(...)`——的**消息模板字面量**不得包含中文字符或中文标点（`：，。、；！？""''（）【】《》`）。运行时拼进 `{}` 占位符的**变量值**（用户输入、文件内容、上游响应等）不在此规则范围——这些值本身不可控，规则只管模板。

**为什么不限中文区销售也要管**：CampusClaw 不只服务中文区客户。日志消费方包括海外团队的 SRE、ELK/Splunk 全文检索、grep/awk 流水线、CI 截图、客户附带的 support bundle——一旦消息含中文：
- 海外协作者读不懂、机器翻译丢失上下文，事故响应变慢
- 字节流被错误 charset 解码即不可恢复乱码（同 [[feedback_explicit_charset]] 一脉相承）
- grep 正则、`logstash` mutate filter 难以匹配，告警规则失灵
- 终端/IDE/邮件客户端字体缺失或 BiDi 渲染下错位
- Audit / compliance 日志按英文存档是合规习惯

**硬约束**（Checkstyle `no_chinese_in_log`，build-failing；存量 0，规则上锁防止回潮）：

| 规则 id | 命中 |
|---|---|
| `no_chinese_in_log` | `(log\|logger\|LOGGER\|LOG).(trace\|debug\|info\|warn\|error)(` 后首个字符串字面量内含 Unicode 4E00–9FFF（CJK 表意） / 3000–303F（CJK 符号标点） / FF00–FFEF（半角与全角形式）任意字符 |

| ✅ 正例 | ❌ 反例 |
|---|---|
| `log.info("docker available: {}", available);` | `log.info("Docker 可用: {}", available);` |
| `log.warn("sandbox unavailable");` | `log.warn("沙箱不可用！");` |
| `log.error("auto-recovery test failed");` | `log.error("✗ 自动恢复测试失败!");` |
| `log.info("worker container id: {}", id);` | `log.info("Worker 容器 ID: {}", id);` |

**用户可见文本**（CLI 输出、TUI 渲染、面向终端用户的提示）走 i18n / 资源束或直接 println 即可，与日志走两条路——日志是给运维和开发看的诊断流，不是给最终用户的产品文案。

**绕过**：极端情况（如复现某 charset bug 时必须打中文示例）可在该方法上加 `@SuppressWarnings("checkstyle:no_chinese_in_log")` 局部豁免——但 PR review 应拒绝任何新增豁免，存量为零，不要破例。盘点命令：

```bash
grep -rnE '\b(log|logger|LOGGER|LOG)\.(trace|debug|info|warn|error)\s*\(\s*"[^"]*[一-鿿　-〿＀-￯]' --include='*.java' modules/
```

### 注释符与注释内容之间必须有空格

**规则**：行首的 `//` 与 `/*` 后必须紧跟空白（空格/制表符/换行）或重复同样的符号（`///`、`/**`）。`//foo` / `/*foo*/` 一律拒绝。

**硬约束**（Checkstyle `comment_marker_space`，build-failing）：

| ✅ 正例 | ❌ 反例 |
|---|---|
| `// foo` | `//foo` |
| `/* foo */` | `/*foo*/` |
| `/** Javadoc */` | — |
| `///` 三斜线、`//` 空注释 | — |

**检测范围**：正则锚定到「行首空白 + 注释符」，避免字符串字面量里的 `://`、`-//Apple//DTD ...`、shell 脚本里的 `s/foo//` 等被误判。行尾追加的 trailing 注释（如 `int x = 1; //foo`）不在硬规则覆盖范围内——这类情况绝大多数还是会被人手工写成 `// foo`，由代码评审把关即可。

**理由**：注释符紧贴注释内容（`//foo`）会让 IDE 的语法着色把整个 token 当成一个词处理，可读性骤降；折叠/链接识别也常失效。社区主流风格指南（Google Java Style §4.8.6.1、Sun/Oracle 旧规约）均要求空格。零成本守规、build 失败即修。

### 段落注释上方必须空一行

**规则**：独占一行的 `//` 注释，若紧跟在以 `;` 或 `}` 结尾的代码行下方，注释行上方必须空一行。两种触发形态：

1. 上一行以 `;`/`}` 结尾，无 trailing 注释；
2. 上一行是 `;` + trailing `// xxx`——trailing 部分属于上一行那条语句，与下一段段落注释仍是两件事，仍需空行分开。

紧贴在 `{` / `(` / `,` 之后的注释（块内开场注释、参数延续注释）不属于「新段落」，不在此规则覆盖范围内。

**硬约束**（Checkstyle `comment_blank_line_before`，RegexpMultiline，build-failing）：

✅ 正例：
```java
private static final String ANSI_DIM_KEY = "\033[38;2;102;102;102m";

// Background colors matching campusclaw dark theme
private static final String BG_PENDING = "\033[48;2;40;40;50m";
```

```java
foo(); // does the thing

// next paragraph describing the next block
bar();
```

```java
public void foo() {
    // short-circuit on empty input
    if (input.isEmpty()) {
        return;
    }
}
```

❌ 反例：
```java
private static final String ANSI_DIM_KEY = "\033[38;2;102;102;102m";
// Background colors matching campusclaw dark theme
private static final String BG_PENDING = "\033[48;2;40;40;50m";
```

```java
foo(); // does the thing
// next paragraph describing the next block
bar();
```

**理由**：「段落标题」紧贴上一段代码会让两段在视觉上糊成一片，读者第一眼分不清这条注释是 *上一段的尾巴* 还是 *下一段的标题*。空一行是廉价的视觉分隔。

### Javadoc 块上方必须空一行

**规则**：Javadoc 起始 `/**` 若紧跟在以 `;` 或 `}` 结尾的代码行下方，Javadoc 上方必须空一行——与 [[comment_blank_line_before]] 同源同理，把成员声明与紧贴它上方的上一个成员视觉分开。Class/method 体首个 Javadoc 紧贴 `{` 不受影响（`{` 不在前置字符集合里）。

**硬约束**（Checkstyle `javadoc_blank_line_before`，RegexpMultiline，build-failing）：

✅ 正例：
```java
private final ObjectMapper mapper;

/**
 * Typed writer for {@link Message}.
 */
private final ObjectWriter messageWriter;
```

❌ 反例：
```java
private final ObjectMapper mapper;
/**
 * Typed writer for {@link Message}.
 */
private final ObjectWriter messageWriter;
```

**理由**：和段落 `//` 注释完全同构——Javadoc 紧贴上一个成员的 `;` 或 `}`，读者第一眼分不清这段 Javadoc 是「上一个成员的尾注释」还是「下一个成员的标题」。空一行是廉价的视觉分隔，spotless 不会自动加，靠 Checkstyle 上锁。

### 行尾 trailing 注释 // 前必须有空格（软约束）

`int x = 1;//foo` 不允许，应写 `int x = 1; // foo`。不进 Checkstyle 是因为 hard 检查需要排除字符串里的 `://` URL、sed 脚本 `s/foo//;...` 等，正则误报率高。由代码评审把关。

### 非常量字段与局部变量必须 lowerCamelCase

**规则**：非 `static final` 的字段（实例字段 + 静态非 final 字段）以及方法体内的局部变量（含 `final` 局部）名必须匹配 `^[a-z][a-zA-Z0-9]*$`——小写字母开头，纯字母数字，禁止下划线、美元号、Hungarian 前缀（`m_xxx`）。`static final` 常量按惯例 UPPER_SNAKE_CASE，不在此规则覆盖范围；`for (int i = 0; ...)` 这类循环单字符变量豁免。

**硬约束**（build-failing）：四条 Checkstyle 规则联合生效——
- `non_constant_field_camel_case_instance`（MemberName）实例字段
- `non_constant_field_camel_case_static`（StaticVariableName）静态非 final 字段
- `non_constant_field_camel_case_local`（LocalVariableName）方法体内局部变量，`allowOneCharVarInForLoop=true`
- `non_constant_field_camel_case_local_final`（LocalFinalVariableName）方法体内 `final` 局部变量

| ✅ 正例 | ❌ 反例 |
|---|---|
| `private String userName` | `private String user_name` |
| `private int retryCount` | `private int RetryCount` |
| `private static Logger log` | `private static Logger LOG_handle` |
| `private boolean isActive` | `private boolean _count` / `m_value` |
| `String dim = "..."; String reset = "...";` | `String DIM = "..."; String RST = "...";` |
| `for (int i = 0; i < n; i++)`（单字符豁免） | — |

**理由**：Java 业界惯例（JLS、Google Java Style、Oracle Code Conventions）一致要求字段与局部变量 lowerCamelCase。混用 snake_case / UpperCamel / `m_` 前缀让 import/grep/IDE 重构难以一致命中。常量与变量视觉区分（UPPER_SNAKE vs lowerCamel）让读者第一眼判断「这是不可变共享值」还是「会变的状态」——方法内拿 `String DIM = ...` 当变量会被误认为常量。

### 不在无关概念之间重用名字（避免遮蔽 / 隐藏 / 遮掩）（软约束）

**规则**：同一个标识符在一个类的不同作用域里只表达同一个概念。具体三种「遮蔽（shadowing）」必须避免：

1. **局部变量遮蔽字段**：方法体内的局部变量与本类某个字段同名，且**两者表示的不是同一件事**——典型如字段 `Map<K,V> models`（注册表）与方法内 `List<Model> models`（刚解析出的一批数据）。
2. **参数遮蔽字段且二者概念无关**：方法参数与字段同名却语义不同。`set/builder` 风格「参数即将赋给字段」这种**同概念**情况不算（见下方豁免）。
3. **嵌套类字段遮蔽外层字段**：内部类/嵌套类定义与外层类同名字段，且不是「同一份数据的内层视图」。

不强制走 Checkstyle 是因为 `HiddenField` 无法稳健区分「同概念别名」与「无关概念撞名」——Builder fluent setter（`temperature(Double temperature)`）、record 的 canonical constructor、`with` 拷贝方法都属于「同概念」，强行上锁会产出大量假阳性。靠 PR review + 写代码时自觉。

**豁免**（这些撞名是合法约定，不属于「无关概念」）：

| 场景 | 例子 |
|---|---|
| 构造器 / setter / fluent builder 把入参赋给同名字段 | `public Builder temperature(Double temperature) { this.temperature = temperature; ... }` |
| Record 的 canonical / compact constructor 参数与组件同名 | `record Point(int x, int y) { Point { if (x < 0) ... } }` |
| `with*` 拷贝方法接收的新值与字段同名 | `Foo withName(String name) { return new Foo(name, ...); }` |
| `for (var x : items)` 与字段 `x` 同名但 for 体只在迭代意义下使用 | 强不推荐，能改名就改名；保留为豁免 |

**违规重命名思路**：让局部 / 参数名体现**该作用域内的特定语义**，而不是复用字段那个泛化名字。

| 字段（泛化语义） | 局部 / 参数（具体语义） |
|---|---|
| `models`（注册表全集） | `loaded`（这次从 JSON 解析出的批次）/ `incoming` / `parsed` |
| `sessions`（会话池） | `expired`（被淘汰的）/ `selected`（命中的） |
| `config`（注入的全局配置） | `override`（被覆盖的子集） |
| `client`（持有的外部客户端实例） | `temporary`（临时构造、只用一次的） |

| ✅ 正例 | ❌ 反例 |
|---|---|
| `var loaded = mapper.readValue(in, ...); registerAll(loaded);` | `var models = mapper.readValue(in, ...); registerAll(models);`（外层有字段 `models`） |
| `Builder temperature(Double temperature) { this.temperature = temperature; ... }`（豁免） | — |
| `void merge(Map<K,V> incoming) { this.cache.putAll(incoming); }` | `void merge(Map<K,V> cache) { this.cache.putAll(cache); }`（参数与字段同名却不同物） |

**理由**：读 `registerAll(models)` 时，读者会下意识把 `models` 关联到外层那个 `Map<Provider, Map<String, Model>>` 字段——而实际它指向一个刚解析出来的 `List<Model>`，类型与语义都不同。IDE 不会警告，因为局部合法地遮蔽了字段；编译器也不会警告（unlike Rust）。后续维护者把 `models` 误当字段访问、或试图把 `this.models` 抠出来重构，就会埋雷。撞名的代价是「读代码时多花一秒钟反应」乘以「这段代码被读到的次数」——免费的可读性收益就是改个名字。

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

### 圈复杂度（CC ≤ 20）
方法的 cyclomatic complexity 不得超过 20（`switchBlockAsSingleDecisionPoint=true`，整段 switch 算 1 个决策点）。超阈值的方法必须拆分提取，**不要新增 `@SuppressWarnings("checkstyle:huge_cyclomatic_complexity")`**——存量带此注解的是历史欠债，等待重构，不是范例。

### 方法长度（NBNC ≤ 50 行）
单个方法的「非空非（行）注释」行数不得超过 50（`huge_method` 规则，Checkstyle `MethodLength` + `countEmpty=false`）。计入：方法签名行、`}`、含代码的行尾注释、块注释 `/* ... */` 内部行；跳过：空行、纯 `//` 单行注释。

超阈值的方法**优先拆分**：抽出明确职责的私有方法、用 record/sealed type 把分支表代码化、把数据初始化（如查表）改成静态常量或工厂。只有在拆解会损害可读性（如协议 stream 解析、Unicode 范围表）才用 `@SuppressWarnings("checkstyle:huge_method")` 保留，并在 PR description 里写明理由。**不要新增**与历史保留方法对齐的批量豁免——存量带此注解的是历史欠债，等待重构。

### 控制条件表达式不得过度复杂（逻辑运算符 ≤ 5）

**规则**：单个布尔表达式中的逻辑运算符（`&&`、`||`、`&`、`|`、`^`）总数不得超过 5；同时禁止把赋值表达式嵌进控制条件等更大表达式里执行（如 `if ((x = foo()) != null)`）。两条由 Checkstyle 内置 `BooleanExpressionComplexity`（`max=5`）与 `InnerAssignment` 实现，build-failing。

**为什么 5**：经验线——少于 5 通常仍在「一眼看懂」的可读区间；超过 5 几乎必然要求读者心里画括号才能推理优先级，特别是 `&&` / `||` 混排时。**长 OR 链的枚举式分发**（一堆 `flag1 || flag2 || ...`、`type == A || type == B || ...`、`name.endsWith(".png") || name.endsWith(".jpg") || ...`）同样被这条规则拦下，因为它们更适合改成命名集合或拆分子方法——可读性更高、可单测、扩展时不会越改越长。

**为什么禁内嵌赋值**：`if ((line = reader.readLine()) != null) { ... }` 一行里同时发生「赋值」和「条件判断」两件事——读者很容易把它误读成 `if (x == next())`（== vs = 笔误），编译器无法分辨意图。豁免：`for` 循环的 init / update 槽位、`try-with-resources` 资源声明——这两种语法上就要求在「条件位置」绑定变量。

**违规重构思路**（按优先级）：

| 模式 | 推荐改写 |
|---|---|
| 长 OR 链对常量 / 字面量比较 | 抽成 `Set<T> ALLOWED = Set.of(...)`，`return ALLOWED.contains(value)` |
| 长 OR 链对 `endsWith` / `matches` 等谓词 | 抽成 `List<T>` + `stream().anyMatch(name::endsWith)` |
| 长 OR/AND 混合表达式 | 拆成命名良好的 `boolean isXxx()` 子方法，每个子方法 ≤ 5 个运算符 |
| 内嵌赋值 `while ((b = in.read()) != -1)` | 拆开：先赋值后判断；或改 try-with-resources 模式 |

✅ 正例：

```java
private static final Set<Integer> PUNCTUATION_CHARACTER_TYPES = Set.of(
        (int) Character.CONNECTOR_PUNCTUATION,
        (int) Character.DASH_PUNCTUATION,
        // ...
        (int) Character.MATH_SYMBOL);

private static boolean isPunctuation(String s) {
    if (s == null || s.isEmpty()) {
        return false;
    }
    return PUNCTUATION_CHARACTER_TYPES.contains(Character.getType(s.codePointAt(0)));
}
```

```java
private static final List<String> IMAGE_FILE_EXTENSIONS =
        List.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg", ".ico", ".tiff");

public static boolean isImageFile(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return IMAGE_FILE_EXTENSIONS.stream().anyMatch(name::endsWith);
}
```

❌ 反例：

```java
return type == Character.CONNECTOR_PUNCTUATION
        || type == Character.DASH_PUNCTUATION
        || type == Character.START_PUNCTUATION
        || type == Character.END_PUNCTUATION
        || type == Character.INITIAL_QUOTE_PUNCTUATION
        || type == Character.FINAL_QUOTE_PUNCTUATION
        || type == Character.OTHER_PUNCTUATION
        || type == Character.MATH_SYMBOL;   // 7 个 || > 5
```

```java
if ((line = reader.readLine()) != null) { ... }   // 内嵌赋值
```

当前仓内存量已为 0，规则上锁防止回潮。

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
1. **Stage** — copy `modules/{ai,tui,agent-core,cron,coding-agent-cli}` into `build/mate-campusclaw/`, rewriting the package in `.java/.yml/.properties/.imports/...`.
2. **Apply** — `rsync --delete` from `build/` to in-tree `mate-campusclaw/`. Paths listed in `scripts/sync-mate-exclude.txt` are preserved (mate-side-only files that have no counterpart in `modules/*`).
3. **Verify** — compile `mate-campusclaw/` with auto-detected JDK 21 (same lookup order as `campusclaw.sh`).

When adding a new file directly under `mate-campusclaw/` that has no counterpart in `modules/*`, append its path to `scripts/sync-mate-exclude.txt`, otherwise the next `--delete` will remove it (currently none registered). The hand-tuned `application.properties` is environment-specific — the script never touches it; only `META-INF/spring/*.imports` propagate from `modules/*`.

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

## 决策记录与设计文档约定

每个涉及实现的需求/特性、以及每个架构或设计决策，都必须留下两类可追溯产物。

### 设计文档 — `docs/designs/`
- 每个特性或模块在 `docs/designs/` 下创建或更新一份 markdown 设计文档。
- 采用 gstack `/plan-eng-review` 工程评审结构：Context（为什么）/ 关键定义 / 架构与数据流 / 设计决策（链接到对应 ADR）/ 边界情况 / 性能(DFX) / 契约改动 / 测试 / 验证。
- 文件名：模块级用 `<module>.md`；特性级用 `<feature-slug>.md`。

### 决策记录（ADR）— `docs/decisions/`
- 每个设计/架构决策记录为一个**自包含 HTML**：`docs/decisions/NNNN-<slug>.html`（`NNNN` 四位零填充、全局递增、不复用）。
- 单文件可直接浏览器打开、内联 CSS，风格对齐已有 ADR（如 `docs/decisions/0001-list-models-usable-credentials.html`）。
- 必含字段：Status（Proposed/Accepted/Superseded）、Date、Context、Decision、考虑过的选项及取舍（Pro/Con）、所选方案与理由、Consequences（正/负/后续）、Related（链接设计文档与关联 ADR）。
- 决策被推翻：新建 ADR 并把旧 ADR Status 改 Superseded，双向链接。

### 联动
- 设计文档「设计决策」小节逐条链接到对应 `docs/decisions/*.html`。
- `docs/` 与 `CLAUDE.md` 不在 mate-campusclaw 镜像范围，无需 sync。

## Reference

- `README.md` — user-facing quickstart, CLI flags, supported providers.
- `docs/module-architecture.md` — authoritative module/package breakdown.
- `ARCHITECTURE-HYBRID.md`, `IMPLEMENTATION-HYBRID.md`, `DOCKER-SANDBOX-GUIDE.md` — hybrid local/sandbox execution design.
- `docs/openapi/campusclaw-api.yaml` — HTTP server mode API (OpenAPI 3, authoritative). `docs/server-api.md` is a deprecated historical snapshot.
- `docs/asyncapi/chat-ws.yaml` — `/api/ws/chat` WebSocket contract (AsyncAPI).
- `modules/*/`+`*-design.md` — per-module design docs (Story/AR format).
- `docs/designs/*.md` — feature/module design docs (gstack `/plan-eng-review` format); see "决策记录与设计文档约定".
- `docs/decisions/*.html` — ADR decision records (one self-contained HTML per decision).
- `scripts/sync-mate-campusclaw.sh` + `scripts/sync-mate-exclude.txt` — sync `modules/*` → `mate-campusclaw/` (see "Mate-campusclaw mirror" section).
