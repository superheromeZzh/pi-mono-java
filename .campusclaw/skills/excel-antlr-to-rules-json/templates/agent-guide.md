# Agent 改表指南（失败时只读这一篇）

配合 `compile_report.json` 使用。语法权威：`grammar/Trigger.g4`。

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

### 2.1 口语 → 公式

| 口语 | 应写 | 点位列 |
|------|------|--------|
| 告警为真 | `[X] == 1` | `X` |
| 超标 10% | `[X] > ([X_setpoint] * 1.1)` | `X, X_setpoint` |
| 恒定 / `Ni-Ni-1=0` | `[X] == prev([X])` | 只列 `X` |
| 小于 0 或大于 500 | `([X] < 0) \|\| ([X] > 500)` | `X` |
| 运行中且压力不变 | `([supplyAirStatus]==1) && ([supplyAirPressure]==prev([supplyAirPressure]))` | 对应英文 key |
| 压差 >20 | `([supplyAirStatus]==1) && abs([supplyAirPressure]-[supplyAirPressureSp])>20` | 含 `Sp` |

**恒定**：用 `prev`，**不要** `CO_last`（多一个假点位）。`prev` 由巡检注入上一拍，不占点表。

### 2.2 语法雷区

| 错误 | 改法 |
|------|------|
| `\|a-b\|>5` | `abs([a]-[b]) > 5` |
| `Ni-Ni-1=0` | `[zoneTemperature] == prev([zoneTemperature])` |
| `℃`、`或`、`PPM`、`Pa` | 去掉单位；`或`→`\|\|` |
| `AND` / `and` / 单元格换行 | 单行 `&&` |
| 说明写进公式（`0-正常\|1-故障`） | 只留 `[SupplyFanInverterFaultAlarm] == 1` |
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

## 4. Sheet2 真实失败对照（历史 7/7）

改表时对照 `sheet` + `row`（`compile_report.json`）。

| 行 | 典型错误 | 建议触发条件 |
|----|----------|--------------|
| 2 | `\|开度-设定\|>5` | `abs([damperPosition]-[damperPositionSetpoint])>5` |
| 3 | `Ni-Ni-1=0` + `AND` | `([zoneTemperature]==prev([zoneTemperature]))&&([ahuSupplyFanStatus]==1)&&([boxOccupancyModeSetpoint]==1)` |
| 4 | `℃`、中文「或」 | `([室内温度]<-5)\|\|([室内温度]>40)` |
| 5 | `Ni-Ni-1=0` | `[zoneCO2]==prev([zoneCO2])` |
| 6 | `PPM`、中文「或」 | `([室内二氧化碳含量]<0)\|\|([室内二氧化碳含量]>2000)` |
| 7 | 有效数据长句 | `无需设置`；触发 `[SupplyFanInverterFaultAlarm]==1` |
| 8 | `and`、`\|差值\|` | `([supplyAirStatus]==1)&&abs([supplyAirPressure]-[supplyAirPressureSp])>20` |

样例报告：`examples/compile_report_fault_rules_sheet2.json`。

---

## 5. 输出 JSON（只读）

见 [rules_re-output-example.json](rules_re-output-example.json)。**禁止手写** `trigger.rule_engine`。

可选补丁格式：[patches.example.json](patches.example.json) → `apply_excel_patch.py`（默认输出 `*_patched.xlsx`）。
