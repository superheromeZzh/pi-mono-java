from __future__ import annotations

import json
import sys
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any, Dict, List, Optional


def _configure_stdio_utf8() -> None:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except Exception:
            pass


class VerifyError(Exception):
    pass


def _get_fact(facts: Dict[str, Any], name: str) -> Any:
    if name not in facts:
        raise VerifyError(f"Missing fact: {name}")
    return facts[name]


def eval_expr(expr: Dict[str, Any], facts: Dict[str, Any]) -> Any:
    if "const" in expr:
        return expr["const"]
    if "fact" in expr:
        return _get_fact(facts, str(expr["fact"]))
    if "func" in expr:
        fn = expr["func"]
        args = [eval_expr(a, facts) for a in expr.get("args", [])]
        if fn == "abs":
            if len(args) != 1:
                raise VerifyError("abs() expects 1 arg")
            return abs(args[0])
        raise VerifyError(f"Unsupported func: {fn}")
    if "op" in expr:
        op = expr["op"]
        left = eval_expr(expr["left"], facts)
        right = eval_expr(expr["right"], facts)
        if op == "+":
            return left + right
        if op == "-":
            return left - right
        if op == "*":
            return left * right
        if op == "/":
            return left / right
        if op == ">":
            return left > right
        if op == ">=":
            return left >= right
        if op == "<":
            return left < right
        if op == "<=":
            return left <= right
        if op == "==":
            return left == right
        if op == "!=":
            return left != right
        if op == "and":
            return bool(left) and bool(right)
        if op == "or":
            return bool(left) or bool(right)
        raise VerifyError(f"Unsupported op: {op}")
    raise VerifyError("Invalid expr node")


def _make_facts(expr: Dict[str, Any], *, scenario: str) -> Optional[Dict[str, float]]:
    """
    Return a facts dict that makes expr evaluate:
      - healthy -> False
      - fault   -> True
    Only supports common patterns used in this project.
    """
    # Pattern 1: abs(A - B) > N
    if expr.get("op") == ">" and isinstance(expr.get("left"), dict) and isinstance(expr.get("right"), dict):
        left = expr["left"]
        right = expr["right"]
        if left.get("func") == "abs" and isinstance(right.get("const"), (int, float)):
            n = float(right["const"])
            args = left.get("args", []) or []
            if len(args) == 1 and isinstance(args[0], dict) and args[0].get("op") == "-":
                a = args[0].get("left", {})
                b = args[0].get("right", {})
                if isinstance(a, dict) and isinstance(b, dict) and "fact" in a and "fact" in b:
                    fa = str(a["fact"])
                    fb = str(b["fact"])
                    sp = 45.0
                    if scenario == "fault":
                        return {fb: sp, fa: sp + n + 1.0}
                    return {fb: sp, fa: sp + max(0.0, n - 1.0)}

    # Pattern 2: (X < low) or (X > high)
    if expr.get("op") == "or":
        l = expr.get("left", {})
        r = expr.get("right", {})
        if (
            isinstance(l, dict)
            and isinstance(r, dict)
            and l.get("op") == "<"
            and r.get("op") == ">"
            and isinstance(l.get("left"), dict)
            and isinstance(r.get("left"), dict)
            and l["left"].get("fact") == r["left"].get("fact")
            and isinstance(l.get("right"), dict)
            and isinstance(r.get("right"), dict)
            and isinstance(l["right"].get("const"), (int, float))
            and isinstance(r["right"].get("const"), (int, float))
        ):
            fx = str(l["left"]["fact"])
            low = float(l["right"]["const"])
            high = float(r["right"]["const"])
            if scenario == "fault":
                return {fx: high + 1.0}
            return {fx: (low + high) / 2.0}

    # Pattern 3: X == C / X != C
    if expr.get("op") in ("==", "!=") and isinstance(expr.get("left"), dict) and isinstance(expr.get("right"), dict):
        left = expr["left"]
        right = expr["right"]
        if "fact" in left and isinstance(right.get("const"), (int, float)):
            fx = str(left["fact"])
            c = float(right["const"])
            if expr["op"] == "==":
                # fault -> True, healthy -> False
                return {fx: c} if scenario == "fault" else {fx: c + 1.0}
            # op == "!="
            return {fx: c + 1.0} if scenario == "fault" else {fx: c}
        if "fact" in right and isinstance(left.get("const"), (int, float)):
            fx = str(right["fact"])
            c = float(left["const"])
            if expr["op"] == "==":
                return {fx: c} if scenario == "fault" else {fx: c + 1.0}
            return {fx: c + 1.0} if scenario == "fault" else {fx: c}

    return None


@dataclass(frozen=True)
class SyntheticResult:
    rule_id: str
    scenario: str
    matched: bool
    ratio_true: float
    samples: int
    threshold: float
    min_samples: int


def _evaluate_rule(rule: Dict[str, Any], *, scenario: str) -> SyntheticResult:
    window_seconds = int(rule["window"]["durationSeconds"])
    thr = float(rule["effective"]["threshold"])
    min_samples = int(rule["effective"].get("minSamples", 1))
    expr = rule["trigger"]["expr"]

    # Use 60s step like demo, generate enough samples to satisfy minSamples when present.
    step = 60
    samples = int(window_seconds / step) + 1
    hits = 0
    total = 0

    facts = _make_facts(expr, scenario=scenario)
    if facts is None:
        raise VerifyError(f"rule {rule.get('id')} trigger 不支持自动造数验证（需要扩展模式匹配）")

    for _ in range(samples):
        try:
            ok = bool(eval_expr(expr, facts))
        except VerifyError:
            ok = False
        total += 1
        hits += 1 if ok else 0

    ratio = hits / total if total else 0.0
    matched = (total >= min_samples) and (ratio >= thr)
    return SyntheticResult(
        rule_id=str(rule.get("id", "")),
        scenario=scenario,
        matched=matched,
        ratio_true=ratio,
        samples=total,
        threshold=thr,
        min_samples=min_samples,
    )


def main(argv: List[str]) -> int:
    _configure_stdio_utf8()
    if len(argv) != 2:
        print("用法：python verify_synthetic_rules.py <rules.json>")
        return 2

    path = Path(argv[1]).expanduser().resolve()
    doc = json.loads(path.read_text(encoding="utf-8"))
    rules = doc.get("rules", [])
    if not isinstance(rules, list) or not rules:
        raise VerifyError("rules.json 的 rules 必须是非空数组")

    mismatches = 0
    print("=== 合成数据验证（healthy 应不触发，fault 应触发）===")
    for r in rules:
        if not isinstance(r, dict):
            continue
        rid = str(r.get("id", "")).strip()
        if not rid:
            continue
        healthy = _evaluate_rule(r, scenario="healthy")
        fault = _evaluate_rule(r, scenario="fault")

        ok = (healthy.matched is False) and (fault.matched is True)
        mismatches += 0 if ok else 1

        print(
            json.dumps(
                {
                    "rule_id": rid,
                    "healthy": asdict(healthy),
                    "fault": asdict(fault),
                    "ok": ok,
                },
                ensure_ascii=False,
            )
        )

    if mismatches:
        print(f"FAIL mismatches={mismatches}")
        return 1
    print("OK：synthetic 验证通过")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))

