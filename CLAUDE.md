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

### 禁止空 catch 块吞异常

**规则**：`catch (...) { }` 形式（包括 `catch (Exception ignored) {}` 这种「假装合规」的写法）一律拒绝——异常被静默丢弃，故障一旦发生，日志/监控/复现路径全部失声，溯源只能靠猜。

合规出口只有两条：
1. **交给 SLF4J**：`log.debug/warn/error("contextual message", e)`，让异常进入正常日志链路；
2. **确实是无害的 fall-through**：catch 体内**必须**留一行解释性注释，写明*为什么*忽略安全（如 "Windows 不支持 POSIX 权限"、"格式错误的 SSE 行 — 跳过即可"），让 reviewer 一眼能判断这是「有意忽略」而非「忘了处理」。

**硬约束**（Checkstyle `no_empty_catch_block`，build-failing）：内置 `EmptyCatchBlock` 模块，`commentFormat=.*` 接受任意非空注释；变量名豁免留默认 `^$`——即便起名 `ignored`/`expected` 也照样拒，强制把理由写出来。

| ✅ 正例 | ❌ 反例 |
|---|---|
| `catch (IOException e) { log.warn("failed to flush cache", e); }` | `catch (IOException e) { }` |
| `catch (Exception ignored) { /* legacy unload also failed — file removal below is safe */ }` | `catch (Exception ignored) { }` |
| `catch (NumberFormatException ignored) { // skip unknown enum value from settings.json }` | `catch (Exception ignored) {}` |

**理由**：空 catch 是可观测性的最大单点漏洞——比 `printStackTrace`、比 `System.err.println` 都糟，因为后两者至少在 stderr 留下痕迹，空 catch 连这个都没有。即便确认异常无害，强制写注释也是廉价的"signed acknowledgement"，让六个月后维护此代码的人不必怀疑"这是不是 bug"。本仓 PR review 应当拒绝任何形如 `} catch (...) { }` 的新增——存量在引入规则时已清零，规则上锁防止回潮。

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

**规则**：独占一行的 `//` 注释，若紧跟在以 `;` 或 `}` 结尾的代码行下方，注释行上方必须空一行。紧贴在 `{` / `(` / `,` 之后的注释（块内开场注释、参数延续注释）不属于「新段落」，不在此规则覆盖范围内。

**硬约束**（Checkstyle `comment_blank_line_before`，RegexpMultiline，build-failing）：

✅ 正例：
```java
private static final String ANSI_DIM_KEY = "\033[38;2;102;102;102m";

// Background colors matching campusclaw dark theme
private static final String BG_PENDING = "\033[48;2;40;40;50m";
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

**理由**：「段落标题」紧贴上一段代码会让两段在视觉上糊成一片，读者第一眼分不清这条注释是 *上一段的尾巴* 还是 *下一段的标题*。空一行是廉价的视觉分隔。

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
