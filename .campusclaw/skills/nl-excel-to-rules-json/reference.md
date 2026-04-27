# 参考：`rules.json` 与 Excel 列映射

## 目标文件
规则建议写入 `demo_rule_engine/rules.json`，结构如下：

```json
{
  "version": 1,
  "rules": [
    {
      "id": "vav_damper_control_error_overlimit",
      "name": "风阀控制误差超限",
      "naturalLanguage": "…可选：原始自然语言…",
      "meta": {
        "deviceType": "VAV",
        "component": "风阀",
        "points": ["damper_opening", "damper_opening_sp"],
        "unitHint": "percent"
      },
      "window": { "type": "rolling", "durationSeconds": 1800 },
      "trigger": { "expr": { "...": "..." } },
      "effective": {
        "metric": "ratio_true",
        "source": "trigger",
        "threshold": 0.95
      }
    }
  ]
}
```

说明：
- `effective.source` 当前约定为 `"trigger"`：占比统计来源于“逐点触发条件”的结果。
- `meta.points` 是 **引擎取值的 fact key**。若用户只提供中文点名，可以原样作为 key，但必须与 `trigger.expr` 里的 `{ "fact": ... }` 完全一致。

## Excel：一行一条规则（推荐）

推荐列名：
- `rule_id`（可选）
- `device_type`
- `component`
- `fault_name`
- `point_1`, `point_2`（可扩展 `point_3`…）
- `trigger_formula`（例如 `abs(point_1 - point_2) > 5`）
- `window`（例如 `30min`、`1800s`、`1h`）
- `effective_ratio`（例如 `95%`、`0.95`）
- `min_samples`（可选；不写则生成的 `rules.json` 也不包含 `minSamples`）

### 示例行
| device_type | component | fault_name | point_1 | point_2 | trigger_formula | window | effective_ratio | min_samples |
|---|---|---|---|---|---|---|---|---|
| VAV | 风阀 | 风阀控制误差超限 | damper_opening | damper_opening_sp | abs(point_1 - point_2) > 5 | 30min | 95% |  |

映射规则：
- 将 `trigger_formula` 中的 `point_1` / `point_2` 占位符替换为真实点位 key（与 `point_1` 等列一致）。

## 常见坑（必须追问/澄清）
- 公式缺少比较符（没有 `>`、`>=` 等）
- 阈值单位不清（例如 `5` 是百分比还是别的物理量）
- 时间窗缺失（滚动窗口长度）
- 若你省略 `minSamples`：稀疏采样时可能更容易误判（需要时再加回 `effective.minSamples`）
