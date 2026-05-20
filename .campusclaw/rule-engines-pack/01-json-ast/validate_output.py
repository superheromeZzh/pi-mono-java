"""
校验 LLM 输出的 rules.json：先跑项目级结构校验，再检查 trigger.expr AST
与下游 judge_rules.eval_expr 一致（避免 func 误写比较等）。

用法：
  python validate_output.py path/to/rules.json
或在代码中：validate_rules_document(doc: dict) -> None
"""
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any, Dict, Set

_SHARED = Path(__file__).resolve().parent.parent / "shared"

_ALLOWED_OPS: Set[str] = {
    "+",
    "-",
    "*",
    "/",
    ">",
    ">=",
    "<",
    "<=",
    "==",
    "!=",
    "and",
    "or",
    "not",
}
_ALLOWED_FUNCS: Set[str] = {"+", "-", "*", "/", "abs", "min", "max", "clamp", "between", "in", "if"}
# 常见误生成：把比较写成 func（eval_expr 会报 Unsupported func）
_FORBIDDEN_AS_FUNC: Set[str] = {">", ">=", "<", "<=", "==", "!=", "and", "or", "not"}


def _collect_fact_names(expr: Any, out: Set[str] | None = None) -> Set[str]:
    if out is None:
        out = set()
    if isinstance(expr, dict):
        if "fact" in expr:
            name = str(expr.get("fact", "")).strip()
            if name:
                out.add(name)
        for k in ("left", "right"):
            if k in expr:
                _collect_fact_names(expr.get(k), out)
        args = expr.get("args")
        if isinstance(args, list):
            for a in args:
                _collect_fact_names(a, out)
    elif isinstance(expr, list):
        for it in expr:
            _collect_fact_names(it, out)
    return out


def validate_trigger_expr(expr: Any, *, path: str = "trigger.expr") -> None:
    if not isinstance(expr, dict):
        raise ValueError(f"{path} 必须是 JSON 对象")

    if "const" in expr:
        v = expr["const"]
        if not isinstance(v, (int, float, bool)):
            raise ValueError(f"{path}.const 只能是 number 或 bool")
        extra = set(expr.keys()) - {"const"}
        if extra:
            raise ValueError(f"{path} 含 const 时不应有其它键：{sorted(extra)}")
        return

    if "fact" in expr:
        name = str(expr.get("fact", "")).strip()
        if not name:
            raise ValueError(f"{path}.fact 不能为空字符串")
        extra = set(expr.keys()) - {"fact"}
        if extra:
            raise ValueError(f"{path} 含 fact 时不应有其它键：{sorted(extra)}")
        return

    if "func" in expr:
        fn = str(expr.get("func"))
        if fn in _FORBIDDEN_AS_FUNC:
            raise ValueError(
                f"{path} 禁止用 func={fn!r} 表示比较或逻辑；请改为 "
                f'{{"op":{fn!r},"left":...,"right":...}}（not 用 {{"op":"not","left":...}}）'
            )
        if fn not in _ALLOWED_FUNCS:
            raise ValueError(f"{path} 不支持的 func：{fn!r}；允许：{sorted(_ALLOWED_FUNCS)}")
        args = expr.get("args", [])
        if not isinstance(args, list):
            raise ValueError(f"{path}.args 必须是数组")
        for i, a in enumerate(args):
            validate_trigger_expr(a, path=f"{path}.args[{i}]")
        extra = set(expr.keys()) - {"func", "args"}
        if extra:
            raise ValueError(f"{path} func 节点多余键：{sorted(extra)}")
        return

    if "op" in expr:
        op = str(expr.get("op"))
        if op not in _ALLOWED_OPS:
            raise ValueError(f"{path} 不支持的 op：{op!r}；允许：{sorted(_ALLOWED_OPS)}")
        if op == "not":
            if "left" not in expr:
                raise ValueError(f'{path} op=="not" 必须包含 left')
            validate_trigger_expr(expr["left"], path=f"{path}.left")
            return
        if "left" not in expr or "right" not in expr:
            raise ValueError(f"{path} 二元 op 必须同时包含 left 与 right")
        validate_trigger_expr(expr["left"], path=f"{path}.left")
        validate_trigger_expr(expr["right"], path=f"{path}.right")
        return

    raise ValueError(f"{path} 无法识别：需 const / fact / func / op 四选一形态")


def validate_rules_document(doc: Dict[str, Any]) -> None:
    if str(_SHARED) not in sys.path:
        sys.path.insert(0, str(_SHARED))
    from validate_rules_json import validate_rules_doc  # noqa: WPS433

    validate_rules_doc(doc)
    rules = doc.get("rules") or []
    for i, r in enumerate(rules):
        if not isinstance(r, dict):
            continue
        expr = (r.get("trigger") or {}).get("expr")
        validate_trigger_expr(expr, path=f"rules[{i}].trigger.expr")
        meta = r.get("meta") or {}
        points = meta.get("points") or []
        if not isinstance(points, list):
            raise ValueError(f"rules[{i}].meta.points 必须是数组")
        fact_names = _collect_fact_names(expr)
        point_set = {str(p).strip() for p in points if str(p).strip()}
        if fact_names != point_set:
            raise ValueError(
                f"rules[{i}] meta.points 与 trigger.expr 中的 fact 集合必须完全一致：\n"
                f"  points（集合）= {sorted(point_set)}\n"
                f"  facts（集合） = {sorted(fact_names)}"
            )


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("用法：python validate_output.py <rules.json>", file=sys.stderr)
        return 2
    path = Path(argv[1]).expanduser().resolve()
    doc = json.loads(path.read_text(encoding="utf-8"))
    validate_rules_document(doc)
    print("OK：结构 + AST + points/facts 校验通过")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
