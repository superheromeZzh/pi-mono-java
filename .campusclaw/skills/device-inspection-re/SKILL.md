---
name: device-inspection-re
description: >-
  按 rules_re.json 设备巡检。结果须含设备 ID、汇总表与明细表；故障名称须与 rules_re 中 name 完全一致、禁止缩写。
  与 excel-antlr-to-rules-json 配套。
---

# 设备巡检（rule-engine / rules_re.json）

根据 **`rules_re.json`** 执行规则并汇报告警。**设备 ID** + **表格**为强制；**故障名称**必须用 JSON 里的 `rule_name` **全称**，禁止用简称或归类语代替。

## 规则与夹具

| 项 | 路径 |
|----|------|
| 规则 | `${OPENCLAW_WORKSPACE}/rules/rules_re.json` |
| Mock 夹具 | `${OPENCLAW_WORKSPACE}/skills/device-inspection-re/mock_fixtures/<rule_id>.json` |
| 脚本 | `.../scripts/judge_rules.py` |

```bash
set DEVICE_INSPECTION_FIXTURES_DIR=%OPENCLAW_WORKSPACE%\skills\device-inspection-re\mock_fixtures
python ${OPENCLAW_WORKSPACE}/skills/device-inspection-re/scripts/judge_rules.py \
  --rules ${OPENCLAW_WORKSPACE}/rules/rules_re.json --json
```

## 设备 ID（强制）

- 标识字段：`deviceId` / `device_id`，默认 `{deviceType}_{component}`（如 `送排风_排风机`）。
- 表格「设备」列只填该 ID，**禁止**只写「排风机」「VAV」。
- 正常设备汇总行也须写完整 ID（如 `VAV_风阀`），勿省略后缀。

## JSON 字段（排版用，勿整段贴给用户）

| 字段 | 含义 |
|------|------|
| `total_devices` | 参与巡检设备台数 |
| `fault_count` | 异常设备台数 |
| `alerts_by_device` | `{ "设备ID": [ { "rule_id", "rule_name", "reason_analysis", "expert_advice", ... } ] }` |

**故障名称**一律取每条告警的 **`rule_name`**（与 `rules_re.json` 里 `rules[].name` 一致），不得自行改写。

---

## 向用户汇报的格式（LLM 必须遵守）

### 1. 摘要（两行）

```text
巡检完成 ✅

{N} 台设备，{F} 台异常，{A} 条告警
```

### 2. 按设备汇总表（必须）

| 设备 | 告警数 | 故障名称 |
|------|--------|----------|
| 送排风_排风机 | 11 | 排风机故障、送风机变频故障、…（见下） |

**「故障名称」列规则：**

- 列出该设备下**全部**告警的 **`rule_name` 原文**，用顿号 `、` 分隔。
- **禁止**缩写或归类，例如禁止：
  - `风机故障/变频/压差`
  - `CO/H2 恒定/异常/超限`
  - `风压恒定/异常`
- 若名称较多、单格过长，改为：**汇总表只写告警数**，紧接该设备一张**明细表**（见 §3），明细表里逐行打印完整 `rule_name`。

### 3. 告警明细表（必须，覆盖每条告警）

**每台有告警的设备**在汇总表之后（或名称过长时替代汇总表中的名称列）附一张表，**一行一条告警**：

| 设备 | 告警数 | 规则 ID | 故障名称 | 原因分析 | 专家处理建议 |
|------|--------|---------|----------|----------|--------------|
| 送排风_排风机 | 11 | dev_rule_a1029126 | 排风机故障 | … | … |
| 送排风_排风机 | | dev_rule_950f8f2a | 送风机变频故障 | … | … |

- **故障名称**列：与 `rule_name` **一字不差**（如「检测 CO2 浓度恒定」「新风机组送风机变频器故障」）。
- 空字段写 `—`。
- 告警数可在首行填写，后续行留空。

### 4. 正常设备（一行）

```text
✅ 正常：VAV_风阀、VAV_温控器/温度传感器、AHU_送风压力传感器
```

使用完整 **device_id**，与汇总表「设备」列一致。

### 5. 收尾（可选）

Mock 未换、结果与上次相同时，可说明「Mock 数据未变，结果一致」；若用户要真实数据或指定 `--end-ts`，再提示可重跑。**禁止**免责声明。

---

## 标准示例（结构固定；故障名须按当次 JSON 填全）

```text
巡检完成 ✅

7 台设备，4 台异常，24 条告警

| 设备 | 告警数 | 故障名称 |
|------|--------|----------|
| 送排风_排风机 | 11 | （见下表） |
| 送排风_送风机 | 11 | （见下表） |
| VAV_CO2传感器 | 1 | 检测 CO2 浓度恒定 |
| 新风机_送风机变频器 | 1 | 新风机组送风机变频器故障 |

**送排风_排风机**

| 设备 | 规则 ID | 故障名称 | 原因分析 | 专家处理建议 |
|------|---------|----------|----------|--------------|
| 送排风_排风机 | dev_rule_a1029126 | 排风机故障 | … | … |
| 送排风_排风机 | dev_rule_950f8f2a | 排风机变频故障 | … | … |
| … | … | …（共 11 行，每条 rule_name 完整） | … | … |

**送排风_送风机**（同样 11 行完整故障名称）

✅ 正常：VAV_风阀、VAV_温控器/温度传感器、AHU_送风压力传感器

Mock 数据未变时，结果与上次一致；要换真实数据源或指定时间范围可说明如何重跑。
```

---

## 禁止

- 缩写、合并故障名（`/`、`+`、`恒定/异常/超限` 等归类）
- 省略明细表（仅有汇总且名称被缩短）
- 无设备 ID、整段原始 JSON、正常设备逐条规则展开

## 与 excel-antlr-to-rules-json

Excel → `rules_re.json` → `generate_mock_fixtures.py` → 本技能 `judge_rules.py` → 按上文格式汇报（**完整 `rule_name`**）。
