from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any, Dict, List


def _configure_stdio_utf8() -> None:
    """尽量让 Windows 控制台按 UTF-8 输出，减少中文乱码。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")  # py3.7+
        except Exception:
            pass


# 每条规则必须具备的顶层字段（与 demo 引擎约定一致）
REQUIRED_RULE_KEYS = {"id", "name", "meta", "window", "trigger", "effective"}


def _fail(msg: str) -> None:
    raise ValueError(msg)


def _iter_facts(expr: Any) -> List[str]:
    out: List[str] = []
    if not isinstance(expr, dict):
        return out
    if "fact" in expr:
        out.append(str(expr["fact"]))
        return out
    if "func" in expr:
        for a in expr.get("args", []) or []:
            out.extend(_iter_facts(a))
        return out
    if "op" in expr:
        out.extend(_iter_facts(expr.get("left")))
        out.extend(_iter_facts(expr.get("right")))
        return out
    if "const" in expr:
        return out
    return out


def validate_rules_doc(doc: Dict[str, Any]) -> None:
    """校验 rules.json 的最小结构约束。"""
    if not isinstance(doc, dict):
        _fail("rules.json 顶层必须是 JSON 对象")
    if doc.get("version") != 1:
        _fail("rules.json 的 version 必须是 1")
    rules = doc.get("rules")
    if not isinstance(rules, list) or not rules:
        _fail("rules.json 的 rules 必须是非空数组")

    for i, r in enumerate(rules):
        if not isinstance(r, dict):
            _fail(f"rules[{i}] 必须是对象")
        missing = REQUIRED_RULE_KEYS - set(r.keys())
        if missing:
            _fail(f"rules[{i}] 缺少字段：{sorted(missing)}")

        if not str(r["id"]).strip():
            _fail(f"rules[{i}].id 不能为空")
        if not str(r["name"]).strip():
            _fail(f"rules[{i}].name 不能为空")

        # 可选字段：原因分析 / 专家处理建议
        # - 不要求必填
        # - 若存在，必须是字符串（允许空字符串）
        for opt in ("原因分析", "专家处理建议"):
            if opt in r and not isinstance(r.get(opt), str):
                _fail(f"rules[{i}].{opt} 若存在则必须是字符串（允许空字符串）")

        meta = r["meta"]
        if not isinstance(meta, dict):
            _fail(f"rules[{i}].meta 必须是对象")
        for k in ("deviceType", "component", "points"):
            if k not in meta:
                _fail(f"rules[{i}].meta 缺少字段：{k}")
        if not str(meta.get("deviceType", "")).strip():
            _fail(f"rules[{i}].meta.deviceType 不能为空")
        if not str(meta.get("component", "")).strip():
            _fail(f"rules[{i}].meta.component 不能为空")
        if not isinstance(meta["points"], list) or len(meta["points"]) < 1:
            _fail(f"rules[{i}].meta.points 必须是非空数组")
        if "deviceId" in meta and meta.get("deviceId") is not None and str(meta.get("deviceId")).strip() == "":
            _fail(f"rules[{i}].meta.deviceId 若存在则不能为空（不需要可直接省略字段）")

        window = r["window"]
        if window.get("type") != "rolling":
            _fail(f"rules[{i}].window.type 必须是 rolling")
        ds = window.get("durationSeconds")
        if not isinstance(ds, int) or ds <= 0:
            _fail(f"rules[{i}].window.durationSeconds 必须是正整数")

        effective = r["effective"]
        if effective.get("metric") != "ratio_true":
            _fail(f"rules[{i}].effective.metric 必须是 ratio_true")
        if effective.get("source") != "trigger":
            _fail(f"rules[{i}].effective.source 必须是 trigger")
        thr = effective.get("threshold")
        if not isinstance(thr, (int, float)) or not (0.0 <= float(thr) <= 1.0):
            _fail(f"rules[{i}].effective.threshold 必须在 [0, 1]")
        if "minSamples" in effective:
            ms = effective.get("minSamples")
            if not isinstance(ms, int) or ms < 1:
                _fail(f"rules[{i}].effective.minSamples 必须是 >=1 的整数")

        trigger = r["trigger"]
        if not isinstance(trigger, dict) or "expr" not in trigger:
            _fail(f"rules[{i}].trigger.expr 缺失")

        expr = trigger.get("expr")
        facts = _iter_facts(expr)
        points = [str(x).strip() for x in meta.get("points", []) if str(x).strip()]
        missing_facts = sorted({f for f in facts if f and f not in set(points)})
        if missing_facts:
            _fail(f"rules[{i}].trigger.expr 引用了不在 meta.points 中的 fact：{missing_facts}")


def main(argv: List[str]) -> int:
    _configure_stdio_utf8()
    if len(argv) != 2:
        print("用法：python validate_rules_json.py <rules.json>")
        return 2
    path = Path(argv[1]).expanduser().resolve()
    doc = json.loads(path.read_text(encoding="utf-8"))
    validate_rules_doc(doc)
    print("OK：校验通过")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
