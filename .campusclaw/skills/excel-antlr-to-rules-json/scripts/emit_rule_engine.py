from __future__ import annotations

from typing import Any, List

# Builtins in rules_re.json use $ prefix (see rule-engines-pack rules_re.json)
_DOLLAR_FUNCS = frozenset({"abs", "min", "max", "avg"})
# prev: judge_rules accepts prev(x) / $prev(x) before __prev_ injection
_PREV_FUNCS = frozenset({"prev"})


def emit_rule_engine(expr: Any) -> str:
    """Convert JSON AST (from TriggerAstBuilder) to PyPI rule-engine expression text."""
    if not isinstance(expr, dict):
        raise ValueError("expression must be a dict node")

    if "fact" in expr:
        return str(expr["fact"])

    if "const" in expr:
        v = expr["const"]
        if isinstance(v, bool):
            return "true" if v else "false"
        if isinstance(v, float) and v == int(v):
            return str(int(v))
        return str(v)

    if "func" in expr:
        fn = str(expr["func"])
        args = expr.get("args") or []
        inner = ", ".join(emit_rule_engine(a) for a in args)
        if fn in _PREV_FUNCS:
            return f"prev({inner})"
        if fn in _DOLLAR_FUNCS:
            return f"${fn}({inner})"
        return f"{fn}({inner})"

    if "op" in expr:
        op = str(expr["op"])
        if op == "not":
            return f"not {emit_rule_engine(expr['left'])}"
        left = emit_rule_engine(expr["left"])
        right = emit_rule_engine(expr["right"])
        if op in ("or", "and"):
            return f"({left} {op} {right})"
        return f"({left} {op} {right})"

    raise ValueError(f"unsupported AST node: {expr!r}")


def merge_points_with_expression(points: List[str], rule_engine_text: str) -> List[str]:
    """Align meta.points with identifiers referenced in rule_engine (judge_rules does similar)."""
    import re

    keywords = {
        "and",
        "or",
        "not",
        "true",
        "false",
        "null",
        "in",
        "matches",
        "contains",
        "startswith",
        "endswith",
    }
    normalized = re.sub(
        r"\$?prev\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)",
        r"__prev_\1__",
        rule_engine_text,
    )
    found: List[str] = list(points)
    seen = set(found)
    for m in re.finditer(r"(?<!\$)\b([A-Za-z_][A-Za-z0-9_]*)\b", normalized):
        name = m.group(1)
        if name.lower() in keywords or name.startswith("__prev_"):
            continue
        if name not in seen:
            seen.add(name)
            found.append(name)
    return found
