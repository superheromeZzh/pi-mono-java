---
name: nl-excel-to-rules-json
description: 将中文自然语言规则描述或 Excel（.xlsx）规则表转换为项目可用的 `rules.json`（JSON AST）DSL，供 demo 规则引擎执行。适用于用户提供中文规则、提到 Excel/表格/.xlsx、或要求生成/校验 `rules.json` 的场景。若必填信息缺失或存在歧义，使用“每轮最多 1 个问题”的问答向导补齐；在信息齐全或已明确默认假设后，输出完整 `rules.json`，并在 OpenClaw 场景下合并写入统一文件。
---

# 自然语言 / Excel → `rules.json`（JSON AST）

## 目标
把以下任意一种输入：
- 中文自然语言规则描述
- Excel 表格（**一行一条规则**）

转换为与 `demo_rule_engine/rules.json` 兼容的 JSON AST（包含时间窗 + 占比判定）。

## 输出契约（强约束）
当必填字段已知（或已给出明确默认假设并得到用户确认）后，输出 **一份完整** 的 `rules.json`，使用 Markdown 代码围栏（`json`）包裹：
- 顶层必须是：`{ "version": 1, "rules": [ ... ] }`
- 每条 rule 必须包含：`id`、`name`、`meta`、`window`、`trigger`、`effective`

若信息缺失/有歧义：**先不要输出 JSON**。按下面的 **问答向导** 继续追问。

## OpenClaw：统一落盘（单一 `rules.json`）
当本 Skill 在 OpenClaw 中使用时，必须遵守 **“先验证、后保存、用户确认才落盘”**：

- 统一规则目录使用占位变量：`${OPENCLAW_WORKSPACE}/rules/`（例如 `~/.openclaw/workspace/rules/`）
- **严禁**在验证通过前把内容写入统一文件 `${OPENCLAW_WORKSPACE}/rules/rules.json`
- 必须先生成本次规则到一个 **临时文件**（例如：`${OPENCLAW_WORKSPACE}/rules/_candidate_rules.json`）
- 必须跑完 **方案一 + 方案二** 且都通过
- 通过后再问用户“是否保存到统一 rules.json？”
- 用户回复“保存”才允许执行合并落盘

### 最重要的硬规则（必须遵守）
- **生成阶段只允许写候选文件**：`${OPENCLAW_WORKSPACE}/rules/_candidate_rules.json`
- **统一文件只允许通过合并脚本写入**：只允许调用 `scripts/merge_rules.py`，禁止任何直接 `Write`/覆盖统一 `rules.json`

### 可直接复制的落盘流程模板（按顺序执行）
1) 把本次生成的规则写入候选文件（不要写统一文件）：

```text
${OPENCLAW_WORKSPACE}/rules/_candidate_rules.json
```

2) 校验闭环（必须同时跑方案一 + 方案二；禁止只跑方案一）：

```bash
python ${OPENCLAW_WORKSPACE}/skills/nl-excel-to-rules-json/scripts/verify_candidate_rules.py ${OPENCLAW_WORKSPACE}/rules/_candidate_rules.json
```

3) 查重预演（不落盘）：

```bash
python ${OPENCLAW_WORKSPACE}/skills/nl-excel-to-rules-json/scripts/merge_rules.py --dry-run ${OPENCLAW_WORKSPACE}/rules/rules.json ${OPENCLAW_WORKSPACE}/rules/_candidate_rules.json
```

4) 若预演提示“已存在，跳过”：告知用户已存在，无需再次添加，结束。

5) 若预演提示“将追加”：询问用户是否保存；用户回复“保存”后执行真实合并：

```bash
python ${OPENCLAW_WORKSPACE}/skills/nl-excel-to-rules-json/scripts/merge_rules.py ${OPENCLAW_WORKSPACE}/rules/rules.json ${OPENCLAW_WORKSPACE}/rules/_candidate_rules.json
```

统一文件路径为：

- `${OPENCLAW_WORKSPACE}/rules/rules.json`

### 合并语义（必须遵守）
- 若统一文件不存在：从 `{ "version": 1, "rules": [] }` 开始
- 对本次生成文档中的每条规则：
  - 若统一文件中已存在**相同业务键**（`meta.deviceType + meta.component + name`）：**不覆盖**，并提示用户“已存在，跳过”
  - 兜底：若 incoming 自带 `id` 且统一文件中已存在相同 `rules[].id`：也视为已存在并跳过
  - 否则：**追加**到 `rules` 数组末尾
- `version` 固定为 `1`
- 使用 UTF-8 写入 JSON，缩进固定为 2 个空格

### 推荐落盘方式（可重复执行）
仅当 **用户明确回复“保存”** 且 **验证闭环已通过** 时，才运行合并脚本：

```bash
python ${OPENCLAW_WORKSPACE}/skills/nl-excel-to-rules-json/scripts/merge_rules.py ${OPENCLAW_WORKSPACE}/rules/rules.json <path-to-newly-generated-rules.json>
```

保存前查重预演（不落盘）：

```bash
python ${OPENCLAW_WORKSPACE}/skills/nl-excel-to-rules-json/scripts/merge_rules.py --dry-run ${OPENCLAW_WORKSPACE}/rules/rules.json <path-to-newly-generated-rules.json>
```

注意：
- 确保目录 `${OPENCLAW_WORKSPACE}/rules/` 存在（不存在则创建）
- 合并后可选择运行校验：

```bash
python ${OPENCLAW_WORKSPACE}/skills/nl-excel-to-rules-json/scripts/validate_rules_json.py ${OPENCLAW_WORKSPACE}/rules/rules.json
```

## 对话体验规则（降低挫败感）
- **不要重复追问**：同一会话里用户已回答的信息视为 **已锁定（Locked）**。
- **每轮最多 1 个问题**（除非用户明确要求一次问多个）。
- **禁止默认推断补齐必填信息**：触发条件 / 时间窗口 / 判定阈值 / 枚举语义（例如 0/1 含义）等，只要用户未明确确认，一律列入 **Missing** 并追问；不得用“默认值/经验值”推进生成或落盘。
- **允许给出候选点位字段名建议，但必须用户确认**：用户只给中文点位名、没给数据字段名（key）时，可以提出一个“建议字段名”（例如 `室内温度` → 建议 `room_temp`），但必须让用户明确回复“是/否”，且用户未确认前不得写入候选文件。
- **禁止从“点位名字/字段名”推断触发条件**：即使点位名包含 Alarm/Fault 等字样，也不能擅自写成 `== 1` 或 `== true`。必须让用户明确说明：
  - 触发条件（例如 `SupplyFanInverterFaultAlarm == 1`）
  - 以及枚举语义（例如 `0=正常, 1=故障`），否则该项必须列入 Missing。
- **触发条件已完整**：若自然语言已包含阈值与比较关系（例如 `< -5` 或 `> 40`），**不要**再让用户改写成公式，除非无法编译为 AST。
- **单位提示**：温度场景写入 `meta.unitHint: "celsius"`；风阀百分比场景写入 `meta.unitHint: "percent"`。

## 每条规则的必填字段
- `id`：稳定标识（snake_case）。由 `scripts/excel_to_rules.py` 从 Excel 自动生成且未填 `rule_id` 时：`{deviceType 的 ASCII slug}_{component 与 fault_name 的短语映射}_{sha1(deviceType|component|fault_name) 前 8 位十六进制}`；需要固定 id 时在表中填 `rule_id` 覆盖。
- `name`：故障名称（中文可）
- `meta.deviceType`：设备类型/族（例如 `VAV`）
- `meta.component`：例如 `风阀`
- `meta.points`：点位 key 列表（可先中文/自由文本，但必须与数据字段一致）
- `window.durationSeconds`：整数秒（例如 30min → 1800）
- `trigger.expr`：表达式 AST（见下文）
- `effective.metric`：必须是 `"ratio_true"`
- `effective.threshold`：0–1 浮点（例如 95% → 0.95）
- `effective.minSamples`：**可选**；默认建议不写（引擎会按 `1` 处理）

## 可选字段（不必填，但要引导用户选择；不填默认为空字符串）
- `原因分析`：字符串
- `专家处理建议`：字符串

## 问答向导（必须）
维护一个内部的“已锁定字段清单”。每一轮都必须输出：
- **Locked**：本轮已确认的信息
- **Missing**：仍缺哪些必填信息（只列清单，禁止夹带提问）
- **Next question**：本轮只问 1 个问题（从 Missing 中挑优先级最高的一个来问）

### Locked 摘要格式（每轮）
```text
Locked:
- 设备类型（deviceType）: ...
- 元器件（component）: ...
- 故障名称（name）: ...
- 点位字段名（meta.points / 数据字段 key）: ...（若已知）
- 触发条件（trigger）: ...（若已知）
- 有效数据窗口（window）: ...
- 判定阈值（effective.threshold）: ...
- 最小样本数（minSamples，可选）: ...（不写表示使用引擎默认）

Missing（还缺的信息；每轮都要列出，禁止省略；只列清单不提问）：
- ...（例如：故障名称 / 点位字段名 / 触发条件 / 有效数据窗口 / 判定阈值）
 - （可选）原因分析
 - （可选）专家处理建议

Next question（每轮最多 1 个问题；必须从 Missing 中选择 1 个来问）：
- ...
```

### 追问优先级（动态）
1) **点位字段名（数据字段 key）**（仅在无法推断或用户拒绝默认 key 时）
2) **触发条件**（仅在阈值/比较符缺失，或单位语义不清时）
3) **时间窗**（缺失才问）
4) **占比阈值**（缺失才问）
5) **minSamples（可选）**
   - 默认：**不问**（省略字段即可）
   - 仅当用户明确要求“窗口内至少 N 个点才判定”时才追问并写入 `effective.minSamples`

### 可直接复制的问题模板
- 点位字段名确认（推荐）：
  - 你提到的点位“室内温度”，在你数据里的**字段名（key）**我将使用：`room_temp`。请回复 **是**；如否请直接给出你的真实字段名（单行即可）。
- 时间窗：
  - 有效数据的时间窗口多长？（示例：`15min` / `900s`）
- 占比阈值：
  - 窗口内需要多少比例的采样点满足触发条件？（示例：`90%`）
- minSamples（可选）：
  - 你希望窗口内至少多少个采样点才允许判定？（例如 `20`；不需要则回复 **跳过**）
- 原因分析（可选）：
  - 这条故障你希望写一段“原因分析”吗？不需要请回复 **跳过**。
- 专家处理建议（可选）：
  - 这条故障你希望写一段“专家处理建议”吗？不需要请回复 **跳过**。

### 若可用 AskQuestion
尽量用它提出 **单个** 下一步问题，但仍需遵守“不重复追问已锁定字段”。

## Excel：一行一条规则（推荐列）
当用户提供 Excel（或截图表格）时，优先映射为以下列名：
- `device_type`
- `component`
- `fault_name`
- `point_1`, `point_2`（可扩展 `point_3`…）
- `trigger_formula`（例如 `abs(point_1 - point_2) > 5`）
- `window`（例如 `30min`）
- `effective_ratio`（例如 `95%`）
- `min_samples`（可选；不写则不生成 `effective.minSamples`）
- `rule_id`（可选；缺失则按上条算法由 `device_type` + `component` + `fault_name` 生成稳定 `id`）
- `reason_analysis`（可选；写入规则字段 `原因分析`，缺失则默认为空字符串）
- `expert_advice`（可选；写入规则字段 `专家处理建议`，缺失则默认为空字符串）

若列名不同：做最佳努力映射，并明确写出“你假设的列映射关系”。

## 表达式 AST（受限）
用受限 JSON AST 表达触发条件：
- 取点位：`{ "fact": "damper_opening" }`
- 常量：`{ "const": 5 }`
- 运算：`{ "op": ">", "left": <expr>, "right": <expr> }`
- 算术：`+ - * /`
- 比较：`> >= < <= == !=`
- 逻辑：`and` / `or` / `not`
- 函数：`abs` / `min` / `max` / `clamp` / `between` / `in` / `if`

支持的函数约定（统一用 `{ "func": "...", "args": [...] }`）：
- `abs(x)`：绝对值
- `min(a,b,...)` / `max(a,b,...)`
- `clamp(x, lo, hi)`：将 x 限制在 [lo, hi]
- `between(x, lo, hi)`：等价于 `x>=lo and x<=hi`
- `in(x, [a,b,c])`：集合包含（第二个参数是 JSON 数组常量）
- `if(cond, a, b)`：条件表达式

示例：`abs(damper_opening - damper_opening_sp) > 5`

```json
{
  "op": ">",
  "left": {
    "func": "abs",
    "args": [
      {
        "op": "-",
        "left": { "fact": "damper_opening" },
        "right": { "fact": "damper_opening_sp" }
      }
    ]
  },
  "right": { "const": 5 }
}
```

## 时间窗归一化
- `30min` → `durationSeconds: 1800`
- `1h` → `3600`
- 支持 `N min` / `N s` 这类写法（实现细节见脚本）

## 固定常量字段（无需询问用户）
- `effective.metric`：固定为 `"ratio_true"`
- `effective.source`：固定为 `"trigger"`
- `window.type`：固定为 `"rolling"`
- `effective.minSamples`：可选字段；**只有用户明确提出**“至少 N 个样本才判定”时才写入，否则省略

## 不能默认的必填项（必须来自用户明确确认）
- `trigger.expr`（触发条件/阈值/枚举语义）
- `window.durationSeconds`（有效数据窗口）
- `effective.threshold`（判定占比阈值）

## 严禁“缺信息就默认填”（硬规则）
- **缺了任何必填信息都必须提醒用户并追问**（遵守“每轮最多 1 个问题”），禁止为了推进流程而臆造/默认填充：`name`、点位 key、阈值、窗口、占比阈值等。
- 合并落盘前会有脚本门禁：若 `id` 非稳定格式、`name` 疑似占位、`meta.points` 含中文展示名/非法 key，将直接失败并阻止写入统一 `rules.json`。

## 校验闭环（推荐）
在生成一条/一批规则后，必须进入下面的 **闭环验证**。验证不通过就要反思并修正规则，直到通过为止；通过后再问用户是否保存。

### 闭环步骤（必须严格按顺序执行）
1) 先生成规则（先不要落盘到统一文件），写入临时文件（建议路径）：`${OPENCLAW_WORKSPACE}/rules/_candidate_rules.json`
2) 方案一：结构与自洽校验（必须）
3) 方案二：合成数据验证（推荐，默认执行）
4) 若 2) 或 3) 失败：必须输出“失败原因 + 反思 + 修正动作”，并**重新编辑规则**，然后回到 2) 重试
5) 若 2) 与 3) 都通过：先做一次“查重预演”（`merge_rules.py --dry-run`）。若提示“已存在，跳过”，则告知用户规则已存在，无需再次添加，并结束
6) 若查重预演显示存在“将追加”：输出“验证通过摘要”，并询问用户是否保存
7) 用户回复“保存”：才执行合并落盘（merge_rules.py）；用户回复“不保存”：结束，不写入统一文件

### 方案一：结构与自洽校验（必须）

```bash
python ${OPENCLAW_WORKSPACE}/skills/nl-excel-to-rules-json/scripts/validate_rules_json.py <path-to-rules.json>
```

该校验会额外检查：
- `effective.source` 必须为 `"trigger"`
- `trigger.expr` 引用的所有 `fact` 必须都出现在 `meta.points`

### 方案二：合成数据验证（推荐）
  - 自动为每条规则构造 healthy / fault 两组 facts，并验证 healthy 不触发、fault 触发
  - 若触发表达式超出当前支持模式，会直接报错提示需要扩展造数器

```bash
python ${OPENCLAW_WORKSPACE}/skills/nl-excel-to-rules-json/scripts/verify_synthetic_rules.py <path-to-rules.json>
```

### 失败时的输出格式（必须）
当任一步失败时，必须给出三段信息，且只针对本次失败的最关键点：
- **失败原因**：包含 rule_id 与字段路径（例如 `rules[0].effective.source missing`）
- **反思**：这次失败说明自然语言→DSL 哪一步理解/翻译出了问题
- **修正动作**：你将如何修改 DSL（补字段/改 points 与 fact 对齐/修正窗口阈值/修正表达式 AST），并立即进入下一轮验证

## 延伸阅读
- 更完整的 `rules.json` 形状与 Excel 映射示例：`reference.md`
- `.xlsx` → `rules.json`：`scripts/excel_to_rules.py`
- OpenClaw 统一合并落盘：`scripts/merge_rules.py`
