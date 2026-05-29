# Agent 改表指南（失败时只读这一篇）

配合 `compile_report.json` 使用。语法权威：`grammar/Trigger.g4`。

**样例说明**：下文与 `templates/`、`examples/` 中的设备名、点位 key、规则 ID 均为**虚构冷冻站**教学数据，与真实业务表无关；改表时只参考**错误类型**与改法，点位名须与**当前 Excel 行**一致。

**读表范围**：`excel_to_rules.py` 会合并 xlsx 里**所有含数据行的工作表**（`wb.worksheets` 顺序），不是只读激活页。  
成功后脚本会打印 `excel sheets merged: Sheet1(...), Sheet2(...)`；`compile_report.json` 里有 `sheets[]` 数组。

---

## 1. Excel 标准列

| 列名 | 必填 | 说明 |
|------|------|------|
| 设备 / 元器件 / 故障名称 | 是 | 写入 `rules_re` 的 meta |
| 点位 | 是 | 英文 key，逗号分隔；**只列真实点**，不要 `X_last` 假点 |
| 触发条件 | 是 | ANTLR 表达式，见 §2 |
| 有效数据 | 是 | 见 §3 |
| 原因分析 / 专家处理建议 | 否 | 原样进 JSON |

别名映射见 `scripts/excel_io.py`（`触发条件`→`trigger_formula` 等）。

**另存（强制）**：禁止覆盖 `故障规则.xlsx` → 用 `故障规则_patched.xlsx` 或 `故障规则_编译用.xlsx`。

---

## 2. 触发条件

**只写公式**，不写故障说明。三列对齐：故障名称（人话）、点位（key 列表）、触发条件（公式）。

### 2.1 口语 → 公式（虚构冷冻站样例，**非业务表**）

| 口语 | 应写 | 点位列 |
|------|------|--------|
| 告警为真 | `[X] == 1` | `X` |
| 超标 10% | `[X] > ([X_setpoint] * 1.1)` | `X, X_setpoint` |
| 恒定 / `Ni-Ni-1=0` | `[X] == prev([X])` | 只列 `X` |
| 小于 0 或大于 500 | `([X] < 0) \|\| ([X] > 500)` | `X` |
| 运行中且回水温度不变 | `([chillerRunStatus]==1) && ([chwReturnTemp]==prev([chwReturnTemp]))` | 对应英文 key |
| 排出压力偏差 >15 | `([pumpRunStatus]==1) && abs([pumpDischargePressure]-[pumpDischargePressureSp])>15` | 含 `Sp` |

**恒定**：用 `prev`，**不要** `chwReturnTemp_last`（多一个假点位）。`prev` 由巡检注入上一拍，不占点表。

### 2.2 语法雷区

| 错误 | 改法 |
|------|------|
| `\|a-b\|>5` | `abs([a]-[b]) > 5` |
| `Ni-Ni-1=0` | `[chwReturnTemp] == prev([chwReturnTemp])` |
| `℃`、`或`、`uS`、`kPa` | 去掉单位；`或`→`\|\|` |
| `AND` / `and` / 单元格换行 | 单行 `&&` |
| 说明写进公式（`0-正常\|1-故障`） | 只留 `[CoolingTowerFanFault] == 1` |
| 点位列写 `[x](englishKey)` | 点位列只留 `englishKey`，公式用 `[...]` 同名 |

### 2.3 改表检查清单

1. 点位列与公式 fact 一致  
2. 告警用 `== 1`  
3. 另存新 xlsx，不覆盖原表  
4. 重跑 `excel_to_rules.py`（输入新文件）

### 2.4 问用户（每轮 1 问）

```text
Locked: …
Missing: …
Next question: 行 N「有效数据」是否填「无需设置」？…
```

---

## 3. 有效数据

| 单元格 | 含义 |
|--------|------|
| `无需设置` | 60s，100% |
| `30min内，90%` | 1800s，0.9 |
| `15min内，90%` | 900s，0.9 |

禁止长句：`无需设置诊断时间和延迟时间` → 改为 `无需设置`。

---

## 4. 虚构样表失败对照（结构演示，**非业务表**）

以下为 `lab_equipment_rules.xlsx`「校验页」形态的**教学样例**；设备/点位与真实 `故障规则.xlsx` 无关，仅演示同类语法错误如何改。改表时仍以 **`compile_report.json` 里你的 `sheet` + `row`** 为准。

| 行 | 典型错误 | 建议触发条件（样例） |
|----|----------|----------------------|
| 2 | `\|开度-指令\|>3` | `abs([valvePosition]-[valvePositionCmd])>3` |
| 3 | `Ni-Ni-1=0` + `AND` | `([chwReturnTemp]==prev([chwReturnTemp]))&&([chillerRunStatus]==1)&&([buildingOccupancyMode]==1)` |
| 4 | `℃`、中文「或」 | `([chwReturnTemp]<-2)\|\|([chwReturnTemp]>18)` |
| 5 | `Ni-Ni-1=0` | `[condenserWaterTemp]==prev([condenserWaterTemp])` |
| 6 | `uS`、中文「或」 | `([towerConductivity]<50)\|\|([towerConductivity]>800)` |
| 7 | 有效数据长句 | `无需设置`；触发 `[CoolingTowerFanFault]==1` |
| 8 | `and`、`\|差值\|`、`kPa` | `([pumpRunStatus]==1)&&abs([pumpDischargePressure]-[pumpDischargePressureSp])>15` |

样例报告：`examples/compile_report_example_sheet2.json`。

---

## 5. 输出 JSON（只读）

见 [rules_re-output-example.json](rules_re-output-example.json)。**禁止手写** `trigger.rule_engine`。

可选补丁格式：[patches.example.json](patches.example.json) → `apply_excel_patch.py`（默认输出 `*_patched.xlsx`）。

---

## 6. 中间产物清理（强制）

本 skill 的中间文件在用完后必须删除，只保留 **`rules_re.json`** 与用户原表 **`故障规则.xlsx`**。

| 删除 | 位置 | 保留到何时 |
|------|------|------------|
| `_candidate_rules_re.json`、`compile_report*.json` | `rules/` | 已复制为 `rules_re.json` 且验证通过 |
| `patches.json` | `rules/` | `apply_excel_patch` 已生成 patched xlsx 且编译成功后 |
| `*_patched*.xlsx`、`*_编译用.xlsx` | `skills/`（等） | 已用该 xlsx 编译成功并确认 `rules_re.json` 后 |

```bash
python ${OPENCLAW_WORKSPACE}/skills/excel-antlr-to-rules-json/scripts/cleanup_rules_intermediates.py \
  ${OPENCLAW_WORKSPACE}
```

编译仍失败时**不要**清理 `compile_report.json` 与 patch xlsx，需对照 `sheet` + `row` 继续改表。
