# Deferred Work

未完成或暂缓实现的功能项清单。**新增 `TODO/FIXME` 注释会被 Checkstyle 规则 `no_todo_fixme_in_delivery_code` 拒绝**（规则定义见 [`codecheck.xml`](../codecheck.xml)），请改在此处登记。完成后从表中移除（git 历史保留追溯）。

> 若是已上线产品的缺陷，直接在 issue tracker 立单即可，无需登记到此表。本表面向「已知尚未实现的功能 / 主动暂缓的能力 / 阶段性占位逻辑」。

| ID | 模块 | 描述 | 触发条件 / 何时需要 | 关联 issue |
|---|---|---|---|---|
| DEF-001 | coding-agent-cli | 将剪贴板粘贴的图片真正附到 LLM 消息上。当前 `InteractiveMode#pasteImage` 仅保存到 tmp 文件并通过状态栏告知用户路径。 | 当多模态输入接入 InteractiveMode、`Agent` 支持携带 image 内容块时 | — |
| DEF-002 | coding-agent-cli | `pi install <source>` 真正执行 npm / git clone 安装。当前 `CampusClawCommand` 的 install 分支只打印提示，要求用户手动在 `settings.json` 的 `packages` 数组里追加。 | Skill 包远程安装能力（HTTP/git 拉取 + 校验）上线时 | — |
