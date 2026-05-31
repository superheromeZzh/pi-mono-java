from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any, Dict, List

try:
    import rule_engine
except ImportError as e:  # pragma: no cover
    raise SystemExit("missing rule-engine; install: pip install rule-engine") from e


def _configure_stdio_utf8() -> None:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except Exception:
            pass


def _transform_prev_expression(text: str) -> str:
    import re

    return re.sub(r"\$?prev\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)", r"__prev_\1__", text)


def verify_rules_re_doc(doc: Dict[str, Any]) -> List[str]:
    errors: List[str] = []
    if doc.get("version") != 1:
        errors.append("version must be 1")
    rules = doc.get("rules")
    if not isinstance(rules, list):
        return ["rules must be an array"]
    for i, r in enumerate(rules):
        rid = str(r.get("id", "")).strip()
        trig = r.get("trigger") or {}
        text = str(trig.get("rule_engine", "")).strip()
        if not text:
            errors.append(f"rules[{i}] ({rid}) missing trigger.rule_engine")
            continue
        transformed = _transform_prev_expression(text)
        try:
            rule_engine.Rule(transformed)
        except Exception as e:  # noqa: BLE001
            errors.append(f"rules[{i}] ({rid}) rule_engine syntax error: {e}")
    return errors


def main(argv: List[str]) -> int:
    _configure_stdio_utf8()
    if len(argv) != 2:
        print("usage: python verify_rules_re.py <rules_re.json>")
        return 2
    path = Path(argv[1]).expanduser().resolve()
    doc = json.loads(path.read_text(encoding="utf-8"))
    errors = verify_rules_re_doc(doc)
    if errors:
        for e in errors:
            print(f"ERROR: {e}")
        return 2
    print(f"OK: {len(doc.get('rules', []))} rule(s) compile with rule_engine")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
