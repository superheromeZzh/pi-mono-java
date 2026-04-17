# TUI 问题修复笔记

本文档跟踪 TUI 交互模式的已知问题和修复进度。

## Passlist — 已修复

| # | 问题 | 根因 | 修复 | 涉及文件 |
|---|---|---|---|---|
| 1 | ESC 直接报 `Operation aborted`，用户无法重新编辑上一条消息 | 只做了 abort，没有回滚 UI / 历史，也没把文本放回编辑器 | abort 分支：从 `chatContainer` 移除 `UserMessageComponent` + `AssistantMessageComponent`；把 agent history 截断到 prompt 前的长度；把 input 文本写回 `editorContainer.getEditor().setText(input)`；不再显示 "Operation aborted" 文字 | `modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/mode/InteractiveMode.java`（`executePrompt`） |
| 2 | 快速连按字符时要松手后才显示，感觉不是实时 | `JLineTerminal` 的 drain 循环对每个按键都会 `peek(10)`，等 10ms 才退出，导致每次输入都额外延迟 | drain 逻辑改为"只对 `\033`（ESC 序列开头）做 drain"，普通字符读取后直接 dispatch | `modules/tui/src/main/java/com/campusclaw/tui/terminal/JLineTerminal.java`（`startInputThread`） |
| 3 | （修复 #2 过程中的自我回归）改成 `peek(0)` 后整个输入线程卡死 | JLine 3.26 的 `Timeout(0)` 被当作**无限等待**：`elapsed()` 返回 false、`timeout()` 返回 0、`Object.wait(0)` 永久阻塞 | 保留 `peek(10)` 用于 ESC 序列；非 ESC 分支不 drain（避开 timeout=0 陷阱）| 同上 |

## Todolist — 待修复

以下问题已记进 Claude Code 的 TaskList（见 `TaskList` 工具），这里同步一份便于查阅。

1. **SessionManager 支持移除最后一条消息（abort 回滚）**  
   JSONL 是 append-only 日志，被撤销的 user message 还会留在会话文件里，`/resume` 时会看到孤儿消息。ChatMemory（GaussDB）那边也是同样问题。需要让 SessionManager 能抹掉/标记 tombstone 最后一条，然后在 `executePrompt` abort 分支里一起调用。

2. **abort 时一并清理本轮的 `ToolStatusComponent`**  
   如果 agent 在被 abort 前已经启动了工具，`chatContainer` 里会残留这些工具状态行。需要在 `handleEvent` 里记录"本轮新增的组件"，abort 时批量 `removeChild`。

3. **验证 Alt+Enter / Kitty 编码的 submit 路径 abort 行为**  
   两个入口（Kitty `\033[13;2u` 与 ESC CR）都会把 editor 文本推进 `submitQueue`。理论上 `setText(input)` 恢复没问题，但需要回归测试一下 Kitty 编码路径是否有残留字节。

4. **粘贴大段文本时可能产生大量 render**  
   现在只对 ESC 序列做 drain，粘贴纯文本时每个字符都会单独触发 `tui.render()`。如果实测卡顿，可以在非 ESC 分支用 `peek(1)` 小批量 drain（注意不能用 0，见 #3）；或者让终端启用 bracketed paste（数据以 `\033[200~` 开头就自动走 drain 分支）。
