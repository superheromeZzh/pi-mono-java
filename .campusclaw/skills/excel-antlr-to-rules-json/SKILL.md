---
name: excel-antlr-to-rules-json
description: >-
  Excel 规则表 → rules_re.json（ANTLR + rule-engine）。失败时读 templates/agent-guide.md，
  另存新 xlsx 后重跑。
---

# Excel → ANTLR → `rules_re.json`

## 目录结构

```text
excel-antlr-to-rules-json/
├── SKILL.md                 ← 本文件（流程与命令）
├── reference.md             ← 仅维护者（改 .g4、重建 ANTLR）
├── requirements.txt
├── grammar/Trigger.g4
├── generated/               ← Trigger*.py（运行时依赖）
├── scripts/                 ← excel_to_rules.py 等
├── templates/
│   ├── agent-guide.md       ← 失败改表：LLM 只读这一篇
│   ├── rules_re-output-example.json
│   └── patches.example.json
└── examples/                ← 静态样例（无业务 xlsx）
```

重构前完整备份：勿放在 `skills/` 下（会被 Agent 误读）；OpenClaw 示例路径  
`~/.openclaw/backups/excel-antlr-to-rules-json.backup-20260519/`

## 目标

`故障规则.xlsx` → **`rules_re.json`**（`trigger.rule_engine` 仅由脚本生成）。

## 硬规则

1. 禁止手写 `trigger.rule_engine`  
2. 失败 → 读 **`templates/agent-guide.md`** + `compile_report.json` → 改表 → **另存新 xlsx** → 重跑  
3. **禁止覆盖** `故障规则.xlsx`（用 `故障规则_patched.xlsx` 等）  
4. 不确定时每轮最多 1 个问题  
5. 全部成功 → `verify_rules_re.py` → 用户确认后写 `rules/rules_re.json`  
6. 读取 xlsx **所有含数据的工作表**（非仅激活页）；`compile_report.json` 含 `sheets[]`；成功时脚本打印 `excel sheets merged: ...`

## 流程

```text
故障规则.xlsx（只读）
    → excel_to_rules.py（可用 故障规则_patched.xlsx）
    → _candidate_rules_re.json + compile_report.json
    → verify_rules_re.py
    → 用户确认 → rules/rules_re.json
```

## 命令

```bash
pip install -r ${OPENCLAW_WORKSPACE}/skills/excel-antlr-to-rules-json/requirements.txt

python .../scripts/excel_to_rules.py \
  path/to/故障规则_patched.xlsx \
  ${OPENCLAW_WORKSPACE}/rules/_candidate_rules_re.json \
  --report ${OPENCLAW_WORKSPACE}/rules/compile_report.json

python .../scripts/verify_rules_re.py \
  ${OPENCLAW_WORKSPACE}/rules/_candidate_rules_re.json
```

可选：按 `rules_re.json` 生成本地 mock 时序（**不入库**，输出目录自定）：

```bash
python .../scripts/generate_mock_fixtures.py \
  ${OPENCLAW_WORKSPACE}/rules/rules_re.json \
  path/to/local/mock_fixtures
```

可选补丁（不覆盖原表）：

```bash
python .../scripts/apply_excel_patch.py patches.json 故障规则.xlsx
```

## Excel 列（摘要）

详见 `templates/agent-guide.md` §1–§3。

| 列 | 说明 |
|----|------|
| 设备 / 元器件 / 故障名称 | meta |
| 点位 | 与公式一致；恒定用 `prev`，不要 `_last` |
| 触发条件 | ANTLR 输入 |
| 有效数据 | `30min内，90%` / `无需设置` |
