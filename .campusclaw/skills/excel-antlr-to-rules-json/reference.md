# 维护者参考（改 Trigger.g4 / 重建 ANTLR）

Agent 日常改表请只读 **`templates/agent-guide.md`**，不必读本文。

## 重建 generated（需 JDK 21 + JAR）

```bash
# Windows
./scripts/regenerate_antlr.ps1

# Linux/macOS
./scripts/regenerate_antlr.sh
```

- 语法源：`grammar/Trigger.g4`（勿用已删除的 `RulesTrigger.g4`）
- JAR：首次下载到 `%USERPROFILE%\.antlr\` 或 `~/.antlr/`
- 产出：`generated/Trigger*.py`（提交到仓库；运行时不需 JAR）

## 调试

```bash
python scripts/dump_parse_tree.py "[FaultAlarm] == 1" "FaultAlarm"
python scripts/compile_trigger.py "[FaultAlarm] == 1" "FaultAlarm"
python scripts/test_rule_engine_runtime.py
```

## 运行时流水线

```text
Excel 触发条件 → ANTLR → AST → emit_rule_engine → rules_re.json (trigger.rule_engine)
```

`apply_excel_patch.py`：可选；默认写出 `<输入>_patched.xlsx`，不覆盖原文件。
