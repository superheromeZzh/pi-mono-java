"""
Generate mock API fixtures (<rule_id>.json) from rules_re.json.

Each fixture returns time series designed to TRIGGER the rule (fault scenario).
Verify with rule_engine + rolling-window logic aligned with device-inspection-re.
"""
from __future__ import annotations

import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Tuple

import rule_engine


def _transform_prev(text: str) -> str:
    return re.sub(r"\$?prev\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)", r"__prev_\1__", text)


def _device_id(device_type: str, component: str) -> str:
    return f"{device_type.strip()}_{component.strip()}"


def _guess_point_values(rule: Dict[str, Any], *, fault: bool) -> Dict[str, float]:
    """Heuristic point values from rule_engine expression shape."""
    expr = str((rule.get("trigger") or {}).get("rule_engine", ""))
    points = [str(p).strip() for p in (rule.get("meta") or {}).get("points") or [] if str(p).strip()]
    vals: Dict[str, float] = {p: 0.0 for p in points}

    if not points:
        return vals

    # supplyAirStatus == 1 and abs / _last (before generic alarm == 1)
    if "supplyAirStatus" in expr:
        vals["supplyAirStatus"] = 1.0
        if "supplyAirPressureSp" in expr:
            vals["supplyAirPressure"] = 100.0
            vals["supplyAirPressureSp"] = 50.0 if fault else 99.0
            return vals
        if "supplyAirPressure_last" in expr:
            vals["supplyAirPressure"] = 80.0
            vals["supplyAirPressure_last"] = 80.0 if fault else 120.0
            return vals

    # $abs((a - b)) > threshold
    m_abs = re.search(r"\$abs\s*\(\s*\((\w+)\s*-\s*(\w+)\s*\)\s*\)\s*>\s*([\d.]+)", expr)
    if m_abs:
        a, b, th = m_abs.group(1), m_abs.group(2), float(m_abs.group(3))
        vals[b] = 50.0
        vals[a] = (50.0 + th + 10.0) if fault else (50.0 + th - 2.0)
        for p in points:
            vals.setdefault(p, 0.0)
        return vals

    # X == prev(X) and optional ... == 1
    m_prev = re.search(r"(\w+)\s*==\s*prev\s*\(\s*(\w+)\s*\)", expr)
    if m_prev and m_prev.group(1) == m_prev.group(2):
        base = 22.0
        vals[m_prev.group(1)] = base
        for p in points:
            if p == m_prev.group(1):
                continue
            if re.search(rf"(?<![\w]){re.escape(p)}\s*==\s*1", expr):
                vals[p] = 1.0 if fault else 0.0
            else:
                vals.setdefault(p, 0.0)
        return vals

    # (X == 1) alarm style (single-point or simple alarm)
    if re.search(r"==\s*1", expr):
        matched_alarm = False
        for p in points:
            if re.search(rf"(?<![\w]){re.escape(p)}\s*==\s*1", expr):
                vals[p] = 1.0 if fault else 0.0
                matched_alarm = True
            else:
                vals[p] = 0.0
        if matched_alarm:
            return vals

    # X == X_last (same-timestamp compare of two points)
    if " == " in expr and len(points) >= 2 and any(k.endswith("_last") for k in points):
        base = 120.0
        for p in points:
            vals[p] = base
        if not fault and len(points) >= 2:
            vals[points[1]] = base + 15.0
        return vals

    # X > (X_setpoint * 1.1)
    m = re.search(r"\(\s*(\w+)\s*>\s*\(\s*(\w+)\s*\*\s*1\.1\s*\)\s*\)", expr)
    if m:
        a, b = m.group(1), m.group(2)
        if fault:
            vals[b] = 100.0
            vals[a] = 200.0
        else:
            vals[b] = 100.0
            vals[a] = 105.0
        for p in points:
            vals.setdefault(p, 50.0)
        return vals

    # (X < 0) or (X > HIGH)
    if " or " in expr and "< 0" in expr:
        p0 = points[0]
        m_hi = re.search(r">\s*([\d.]+)", expr)
        hi = float(m_hi.group(1)) if m_hi else 500.0
        vals[p0] = (hi + 100.0) if fault else (hi * 0.4)
        return vals

    # fallback
    for i, p in enumerate(points):
        vals[p] = float(1 + i) if not fault else float(100 + i)
    return vals


def _build_series(
    rule: Dict[str, Any],
    *,
    fault: bool,
    end_ts: float | None = None,
) -> List[Dict[str, Any]]:
    window_seconds = int(rule["window"]["durationSeconds"])
    target_samples = max(10, min(120, window_seconds // 30 + 1))
    step = max(30.0, float(window_seconds) / max(1, target_samples - 1))
    samples = target_samples
    end = end_ts if end_ts is not None else datetime.now(tz=timezone.utc).timestamp()
    start = end - float(window_seconds)
    base_vals = _guess_point_values(rule, fault=fault)
    expr = str((rule.get("trigger") or {}).get("rule_engine", ""))
    freeze = fault and ("prev(" in expr or re.search(r"==\s*1", expr) is not None)

    series: List[Dict[str, Any]] = []
    for i in range(samples):
        ts = start + i * step
        points = dict(base_vals)
        if not freeze and not fault and len(points) > 1:
            for k in points.keys():
                if k.endswith("_last"):
                    continue
                points[k] = points[k] + float(i % 3)
        series.append({"ts": ts, "points": points})
    return series


def _judge_series(rule: Dict[str, Any], series: List[Dict[str, Any]]) -> bool:
    """Rolling ratio_true — mirrors device-inspection-re judge_rules.judge_rule."""
    text = str((rule.get("trigger") or {}).get("rule_engine", ""))
    compiled = rule_engine.Rule(_transform_prev(text))
    window_seconds = int(rule["window"]["durationSeconds"])
    threshold = float(rule["effective"]["threshold"])
    min_samples = int(rule["effective"].get("minSamples", 1))
    uses_prev = "prev(" in text or "$prev(" in text

    norm = sorted(
        [{"ts": float(p["ts"]), **dict(p.get("points") or {})} for p in series],
        key=lambda x: float(x["ts"]),
    )
    now_ts = max(float(p["ts"]) for p in norm)
    start_ts = now_ts - window_seconds
    window_points = [p for p in norm if start_ts <= float(p["ts"]) <= now_ts]

    hits = 0
    total = 0
    for idx, p in enumerate(window_points):
        if uses_prev and idx == 0:
            continue
        facts = {k: v for k, v in p.items() if k != "ts"}
        prev_point = window_points[idx - 1]
        for k in list(facts.keys()):
            if k in prev_point:
                facts[f"__prev_{k}__"] = prev_point[k]
        total += 1
        try:
            if compiled.matches(facts):
                hits += 1
        except Exception:
            pass
    ratio = (hits / total) if total else 0.0
    return (total >= min_samples) and (ratio >= threshold)


def _write_fixture(
    out_dir: Path,
    rule: Dict[str, Any],
    series: List[Dict[str, Any]],
) -> Path:
    rid = str(rule["id"]).strip()
    meta = rule.get("meta") or {}
    device_type = str(meta.get("deviceType", "")).strip()
    component = str(meta.get("component", "")).strip()
    device_id = _device_id(device_type, component)
    doc = {
        "requestId": rid,
        "ruleName": str(rule.get("name", "")),
        "deviceType": device_type,
        "component": component,
        "scenario": "fault",
        "devices": [{"deviceId": device_id, "data": series}],
    }
    path = out_dir / f"{rid}.json"
    path.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def generate_all(rules_path: Path, out_dir: Path) -> Tuple[int, List[str]]:
    doc = json.loads(rules_path.read_text(encoding="utf-8"))
    rules = doc.get("rules") or []
    out_dir.mkdir(parents=True, exist_ok=True)
    ok = 0
    problems: List[str] = []
    for rule in rules:
        rid = str(rule.get("id", "")).strip()
        series = _build_series(rule, fault=True)
        if not _judge_series(rule, series):
            # retry with inverted heuristic
            series = _build_series(rule, fault=True, end_ts=datetime.now(tz=timezone.utc).timestamp() + 1)
            if not _judge_series(rule, series):
                problems.append(f"{rid}: fixture does not trigger rule ({rule.get('trigger', {}).get('rule_engine')})")
                continue
        _write_fixture(out_dir, rule, series)
        ok += 1
    return ok, problems


def main(argv: List[str]) -> int:
    if len(argv) < 3:
        print("usage: python generate_mock_fixtures.py <rules_re.json> <output_dir>")
        return 2
    rules_path = Path(argv[1]).expanduser().resolve()
    out_dir = Path(argv[2]).expanduser().resolve()
    ok, problems = generate_all(rules_path, out_dir)
    print(f"wrote {ok} fixture(s) -> {out_dir}")
    if problems:
        print(f"warnings: {len(problems)}")
        for p in problems:
            print(" ", p)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
