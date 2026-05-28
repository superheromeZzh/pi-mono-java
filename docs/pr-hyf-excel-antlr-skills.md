# PR：feat(skills) Excel 多表 ANTLR 编译与 rule-engine 巡检技能

**建议标题**

```
feat(skills): 新增 Excel 多表 ANTLR 编译与 rule-engine 巡检技能
```

**Base 分支**：`main`  
**Compare 分支**：`hyf`  
**创建链接**：https://github.com/superheromeZzh/pi-mono-java/compare/main...hyf?expand=1

---

## Summary

本 PR 在合并 **origin/main 最新代码** 后，向 `.campusclaw/skills/` 纳入两套配套 OpenClaw/CampusClaw Agent 技能，打通「Excel 故障规则表 → `rules_re.json` → 设备巡检」闭环。

### 1. `excel-antlr-to-rules-json`（Excel → ANTLR → rule-engine）

- **目标**：将 `故障规则.xlsx` 编译为 `rules_re.json`，`trigger.rule_engine` **仅由脚本生成**，禁止 Agent 手写表达式。
- **多表读取（本次核心修复）**：
  - `read_all_xlsx_sheets()` 按 workbook 顺序合并**所有含数据行的工作表**，跳过空表；不再只读激活页（`wb.active`）。
  - `compile_report.json` 增加 `sheets[]`（每表 `name` / `data_rows` / `ok` / `failed`）；失败行带 `sheet` + `row`。
  - 成功时 `excel_to_rules.py` 打印 `excel sheets merged: Sheet1(...), Sheet2(...)`，便于核对是否读全表（如 Sheet1+Sheet2 共约 29 条规则）。
- **失败改表流程**：Agent 只读 `templates/agent-guide.md` + `compile_report.json`；改表须**另存新 xlsx**（禁止覆盖原表）；支持 `apply_excel_patch.py`。
- **技术栈**：`grammar/Trigger.g4` + 预生成 `generated/Trigger*.py`；`rule-engine` 运行时校验；`verify_rules_re.py` 门禁。
- **约定**：`prev()` 表示上一拍（由巡检侧注入 `__prev_*__`）；禁止 `X_last` 假点位；`|差值|` → `abs(...)` 等见 agent-guide。

### 2. `device-inspection-re`（按 rules_re 巡检）

- **目标**：读取 `rules/rules_re.json`，对设备时序数据执行 rule-engine 判定并输出结构化巡检结果。
- **`judge_rules.py`**：支持 `prev` 注入、JSON 输出；Mock 路径 `DEVICE_INSPECTION_FIXTURES_DIR`。
- **汇报规范（SKILL 硬约束）**：必须含**设备 ID**、汇总表 + **完整 `rule_name` 明细表**（与 JSON 中 `rules[].name` 一致，禁止缩写）。
- **Mock 夹具**：`mock_fixtures/` 目录与 README 入库；**`*.json` 不入库**（`.gitignore`），本地用 `generate_mock_fixtures.py` 生成后联调。

### 3. 分支与合并说明

- 推送前已在本地 **`hyf` fast-forward 合并 `origin/main`**，与 main 无冲突；相对 `main` **仅新增 1 个提交**：`fade7a5e`。
- **未纳入仓库**：`excel-antlr-to-rules-json.backup-20260519/`（本地备份，勿放入 `skills/` 以免 Agent 误加载旧版单表脚本）。

### 4. 变更规模

- 约 **30 个文件**（含 ANTLR 生成物、文档与脚本；**不含** mock `*.json`）。

---

## Test plan

- [ ] `pip install -r .campusclaw/skills/excel-antlr-to-rules-json/requirements.txt`
- [ ] `python .campusclaw/skills/excel-antlr-to-rules-json/scripts/excel_to_rules.py <故障规则.xlsx> /tmp/_candidate_rules_re.json --report /tmp/compile_report.json`
  - [ ] stdout 出现 `excel sheets merged:` 且含 **Sheet1、Sheet2**（及各自行数）
  - [ ] `compile_report.json` 中 `summary.total` 与业务表行数一致（空 Sheet 被跳过）
- [ ] `python .campusclaw/skills/excel-antlr-to-rules-json/scripts/verify_rules_re.py /tmp/_candidate_rules_re.json`
- [ ] `python .campusclaw/skills/excel-antlr-to-rules-json/scripts/generate_mock_fixtures.py /tmp/_candidate_rules_re.json .campusclaw/skills/device-inspection-re/mock_fixtures`
- [ ] `set DEVICE_INSPECTION_FIXTURES_DIR=.campusclaw/skills/device-inspection-re/mock_fixtures`
- [ ] `python .campusclaw/skills/device-inspection-re/scripts/judge_rules.py --rules /tmp/_candidate_rules_re.json --json`
- [ ] OpenClaw 同步：将 `.campusclaw/skills/excel-antlr-to-rules-json` 镜像到 `~/.openclaw/workspace/skills/`（勿保留 `skills/` 下的 `.backup-*` 目录）
