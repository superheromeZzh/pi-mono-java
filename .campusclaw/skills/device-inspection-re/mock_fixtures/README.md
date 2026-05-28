# Mock fixtures for `rules_re.json`（故障规则.xlsx，多 Sheet）

**不入 Git**：`*.json` 已在仓库 `.gitignore` 中排除，每台机器本地生成。

由 `excel-antlr-to-rules-json/scripts/generate_mock_fixtures.py` 生成。

- 每条规则一个文件：`<rule_id>.json`（当前约 29 条）
- `requestId` 须与 `rules_re.json` 里 `rules[].id` 一致
- 时序为**应触发故障**场景（`scenario: fault`），窗口内约 **10～120** 个采样点（视 `durationSeconds` 而定）
- 告警类（如 `SupplyFanInverterFaultAlarm == 1`）：全程 `1.0` 表示故障

## Regenerate

```bash
python ${OPENCLAW_WORKSPACE}/skills/excel-antlr-to-rules-json/scripts/generate_mock_fixtures.py \
  ${OPENCLAW_WORKSPACE}/rules/rules_re.json \
  ${OPENCLAW_WORKSPACE}/skills/device-inspection-re/mock_fixtures
```

## 设备 ID

夹具里每台设备必须有 **`deviceId`**（巡检结果里的设备 ID）：

- 默认：`{deviceType}_{component}`，例如 `送排风_排风机`、`新风机_送风机变频器`
- 与 `generate_mock_fixtures.py` 生成逻辑一致

## 运行巡检

```bash
set DEVICE_INSPECTION_FIXTURES_DIR=%OPENCLAW_WORKSPACE%\skills\device-inspection-re\mock_fixtures
python ${OPENCLAW_WORKSPACE}/skills/device-inspection-re/scripts/judge_rules.py \
  --rules ${OPENCLAW_WORKSPACE}/rules/rules_re.json --json
```

汇报格式见 **`../SKILL.md`**（摘要、分组、Markdown 表格）。
