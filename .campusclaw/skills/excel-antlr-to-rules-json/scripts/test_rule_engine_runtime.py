"""Smoke-test: every trigger.rule_engine compiles and matches() without error."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import rule_engine


def _transform_prev(text: str) -> str:
    return re.sub(r"\$?prev\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)", r"__prev_\1__", text)


def main() -> int:
    path = Path(sys.argv[1]).resolve()
    doc = json.loads(path.read_text(encoding="utf-8"))
    rules = doc.get("rules") or []
    fail: list[str] = []
    for r in rules:
        rid = str(r.get("id", ""))
        text = str((r.get("trigger") or {}).get("rule_engine", "")).strip()
        t = _transform_prev(text)
        try:
            compiled = rule_engine.Rule(t)
            points = [str(x).strip() for x in (r.get("meta") or {}).get("points") or [] if str(x).strip()]
            facts = {pt: 1 for pt in points}
            for pt in points:
                facts[f"__prev_{pt}__"] = 0
            compiled.matches(facts)
        except Exception as e:  # noqa: BLE001
            fail.append(f"{rid}: {e} | {text}")
    if fail:
        print(f"FAIL {len(fail)}/{len(rules)}")
        for line in fail:
            print(line)
        return 2
    print(f"OK: {len(rules)} rules compile + matches()")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
