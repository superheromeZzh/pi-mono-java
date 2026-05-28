"""
设备巡检：取数接口 + PyPI rule-engine 表达式（rules_re.json 中 trigger.rule_engine）。
与 device-inspection 的 judge_rules.py 流程对齐，仅替换「单点条件求值」为 rule_engine.Rule。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import socket
import subprocess
import sys
import time
import urllib.request
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Set

import rule_engine

try:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass


class JudgeError(Exception):
    pass


_SCRIPT_FILE = Path(__file__).resolve()
RULES_ROOT = _SCRIPT_FILE.parents[3]  # .../.openclaw/workspace or skill tree root
DEFAULT_RULES_PATH = RULES_ROOT / "rules" / "rules_re.json"
DEFAULT_MOCK_API_URL = "http://127.0.0.1:18080/fetch"

_RE_KEYWORDS: Set[str] = {
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


def _default_rules_path() -> Path:
    if DEFAULT_RULES_PATH.exists():
        return DEFAULT_RULES_PATH
    alt = Path(r"C:\Users\Jason\.openclaw\workspace\rules\rules_re.json")
    if alt.exists():
        return alt
    sys.exit(f"[FATAL] rules file not found: {DEFAULT_RULES_PATH}")


def _parse_ts_to_epoch_seconds(ts: Any) -> float:
    if isinstance(ts, (int, float)):
        return float(ts)
    if isinstance(ts, str):
        s = ts.strip()
        try:
            return float(s)
        except Exception:
            pass
        if s.endswith("Z"):
            s = s[:-1] + "+00:00"
        dt = datetime.fromisoformat(s)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.timestamp()
    raise JudgeError(f"Invalid ts type: {type(ts)}")


def _load_rules_doc(path: Path) -> Dict[str, Any]:
    doc = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(doc, dict):
        raise JudgeError(f"rules file must be an object: {path}")
    if doc.get("version") != 1:
        raise JudgeError(f"version must be 1: {path}")
    rules = doc.get("rules")
    if not isinstance(rules, list):
        raise JudgeError(f"rules must be an array: {path}")
    return doc


def _merge_rules_docs(*docs: Dict[str, Any]) -> List[Dict[str, Any]]:
    by_id: Dict[str, Dict[str, Any]] = {}
    ordered: List[str] = []
    for doc in docs:
        for r in doc.get("rules", []) or []:
            if not isinstance(r, dict):
                continue
            rid = str(r.get("id", "")).strip()
            if not rid:
                continue
            if rid not in by_id:
                ordered.append(rid)
            by_id[rid] = r
    return [by_id[i] for i in ordered if i in by_id]


def _symbol_names_in_expression(text: str) -> Set[str]:
    out: Set[str] = set()
    normalized = re.sub(r"\$?prev\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)", r"__prev_\1__", text)
    for m in re.finditer(r"(?<!\$)\b([A-Za-z_][A-Za-z0-9_]*)\b", normalized):
        name = m.group(1)
        if name.lower() in _RE_KEYWORDS:
            continue
        out.add(name)
    return out


def _transform_prev_expression(text: str) -> str:
    return re.sub(r"\$?prev\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)", r"__prev_\1__", text)


def _is_port_listening(host: str, port: int, *, timeout_s: float = 0.5) -> bool:
    s = socket.socket()
    try:
        s.settimeout(timeout_s)
        return s.connect_ex((host, port)) == 0
    finally:
        try:
            s.close()
        except Exception:
            pass


def _try_start_mock_api_server() -> None:
    if _is_port_listening("127.0.0.1", 18080):
        return
    here = Path(__file__).resolve()
    candidates = [
        RULES_ROOT / "skills" / "device-inspection" / "mock_api_server.py",
        RULES_ROOT / "skills" / "device-inspection-re" / "mock_api_server.py",
        Path.cwd() / "mock_api_server.py",
    ]
    for anc in here.parents:
        candidates.append(anc / "mock_api_server.py")
    script = next((p for p in candidates if p.exists()), None)
    if script is None:
        return
    try:
        subprocess.Popen(
            [sys.executable, str(script)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            stdin=subprocess.DEVNULL,
            creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0),
        )
    except Exception:
        return
    deadline = time.time() + 3.0
    while time.time() < deadline:
        if _is_port_listening("127.0.0.1", 18080):
            return
        time.sleep(0.1)


def _call_mock_api(*, queries: List[Dict[str, Any]], end_ts: float) -> Dict[str, Any]:
    url = os.environ.get("DEVICE_INSPECTION_API_URL", DEFAULT_MOCK_API_URL).strip() or DEFAULT_MOCK_API_URL
    body = json.dumps({"endTs": end_ts, "queries": queries}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url=url, method="POST", data=body, headers={"Content-Type": "application/json; charset=utf-8"}
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read()
    except Exception:
        _try_start_mock_api_server()
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read()
    obj = json.loads(raw.decode("utf-8"))
    if not isinstance(obj, dict):
        raise JudgeError("API response must be a JSON object")
    return obj


def _normalize_data_points(data: Any) -> List[Dict[str, Any]]:
    if not isinstance(data, list) or not data:
        raise JudgeError("data must be a non-empty JSON array")
    out: List[Dict[str, Any]] = []
    for i, item in enumerate(data):
        if not isinstance(item, dict):
            raise JudgeError(f"data[{i}] must be an object")
        if "ts" not in item:
            raise JudgeError(f"data[{i}] missing ts")
        if "points" in item and isinstance(item.get("points"), dict):
            facts = dict(item["points"])
        else:
            facts = {k: v for k, v in item.items() if k not in {"ts", "device_type", "deviceType", "component"}}
        out.append({"ts": _parse_ts_to_epoch_seconds(item["ts"]), **facts})
    out.sort(key=lambda p: float(p["ts"]))
    return out


@dataclass(frozen=True)
class JudgeResult:
    rule_id: str
    rule_name: str
    matched: bool
    ratio_true: float
    samples: int
    threshold: float
    min_samples: int


def judge_rule(rule: Dict[str, Any], series: List[Dict[str, Any]], *, compiled: rule_engine.Rule) -> JudgeResult:
    window_seconds = int(rule["window"]["durationSeconds"])
    threshold = float(rule["effective"]["threshold"])
    min_samples = int(rule["effective"].get("minSamples", 1))

    rule_text = str(rule.get("trigger", {}).get("rule_engine", ""))
    uses_prev = "$prev" in rule_text or re.search(r"(?<!\$)prev\s*\(", rule_text) is not None

    now_ts = max(float(p["ts"]) for p in series)
    start_ts = now_ts - window_seconds
    window_points = [p for p in series if start_ts <= float(p["ts"]) <= now_ts]

    hits = 0
    total = 0
    for idx, p in enumerate(window_points):
        if uses_prev and idx == 0:
            continue
        current_facts = {k: v for k, v in p.items() if k != "ts"}
        prev_point = window_points[idx - 1]
        prev_values = {f"__prev_{k}__": prev_point.get(k) for k in current_facts if k in prev_point}
        current_facts.update(prev_values)
        total += 1
        try:
            ok = bool(compiled.matches(current_facts))
        except Exception:
            ok = False
        hits += 1 if ok else 0

    ratio = (hits / total) if total else 0.0
    matched = (total >= min_samples) and (ratio >= threshold)
    return JudgeResult(
        rule_id=str(rule.get("id", "")),
        rule_name=str(rule.get("name", "")),
        matched=matched,
        ratio_true=float(ratio),
        samples=int(total),
        threshold=threshold,
        min_samples=min_samples,
    )


@dataclass(frozen=True)
class Alert:
    device_id: str
    rule_id: str
    rule_name: str
    message: str
    reason_analysis: str
    expert_advice: str


def build_alert(rule: Dict[str, Any], jr: JudgeResult, *, device_id: str = "") -> Alert:
    rid = str(rule.get("id", "")).strip()
    name = str(rule.get("name", "")).strip()
    msg = f"告警：{name}（rule_id={rid}）"
    ra = str(rule.get("原因分析", "") or "").strip()
    ea = str(rule.get("专家处理建议", "") or "").strip()
    return Alert(device_id=str(device_id or "").strip(), rule_id=rid, rule_name=name, message=msg, reason_analysis=ra, expert_advice=ea)


def main(argv: Optional[List[str]] = None) -> int:
    p = argparse.ArgumentParser(description="设备巡检（rule-engine 表达式版）")
    p.add_argument("--rules", default=str(_default_rules_path()), help="rules_re.json 路径")
    p.add_argument("--rules-extra", action="append", default=[], help="额外合并的 rules 文件（可多次）")
    p.add_argument("--end-ts", default=None, help="结束时间 Unix 秒或 ISO8601，默认当前 UTC")
    p.add_argument("--json", action="store_true", help="JSON 输出")
    p.add_argument("--device-type", default=None, help="按设备类型过滤（如 VAV, AHU, 新风机）")
    args = p.parse_args(argv)

    primary = Path(args.rules)
    docs: List[Dict[str, Any]] = [_load_rules_doc(primary)]
    for extra in args.rules_extra or []:
        ep = Path(str(extra))
        if ep.exists():
            docs.append(_load_rules_doc(ep))
    rules = _merge_rules_docs(*docs)
    if not rules:
        raise JudgeError("merged rules must be non-empty")

    compiled_by_id: Dict[str, rule_engine.Rule] = {}
    for r in rules:
        trig = r.get("trigger") or {}
        text = str(trig.get("rule_engine", "")).strip()
        rid = str(r.get("id", "")).strip()
        if not rid or not text:
            raise JudgeError(f"rule {rid!r} missing trigger.rule_engine")
        transformed = _transform_prev_expression(text)
        try:
            compiled_by_id[rid] = rule_engine.Rule(transformed)
        except Exception as e:
            raise JudgeError(f"rule {rid} rule_engine syntax error: {e}") from e

    end_ts = _parse_ts_to_epoch_seconds(args.end_ts) if args.end_ts else datetime.now(tz=timezone.utc).timestamp()

    queries: List[Dict[str, Any]] = []
    for r in rules:
        meta = r.get("meta") or {}
        window = r.get("window") or {}
        trig = r.get("trigger") or {}
        text = str(trig.get("rule_engine", "")).strip()
        device_type = str(meta.get("deviceType", "")).strip()
        component = str(meta.get("component", "")).strip()
        points = [str(x).strip() for x in (meta.get("points") or []) if str(x).strip()]
        extra = sorted(_symbol_names_in_expression(text))
        real_extra = [p for p in extra if not p.startswith("__prev_")]
        points = sorted(set(points) | set(real_extra))
        window_seconds = int(window.get("durationSeconds", 0) or 0)
        if not device_type or not component or not points:
            continue
        if args.device_type:
            filter_type = args.device_type.strip().upper()
            if device_type.upper() != filter_type:
                continue
        queries.append(
            {
                "requestId": str(r.get("id", "")).strip(),
                "ruleName": str(r.get("name", "")).strip(),
                "deviceType": device_type,
                "component": component,
                "points": points,
                "windowSeconds": window_seconds,
            }
        )

    t_api_start = time.time()
    api_resp = _call_mock_api(queries=queries, end_ts=end_ts)
    t_api_end = time.time()
    items = api_resp.get("items", [])
    if not isinstance(items, list):
        raise JudgeError("items must be an array")

    rules_by_id = {str(r.get("id", "")).strip(): r for r in rules if isinstance(r, dict)}
    alerts: List[Dict[str, Any]] = []

    t_eval_start = time.time()
    for it in items:
        if not isinstance(it, dict):
            continue
        rule_id = str(it.get("requestId", "")).strip()
        device_id = str(it.get("deviceId", "")).strip()
        if not rule_id or rule_id not in rules_by_id:
            continue
        rule = rules_by_id[rule_id]
        norm = _normalize_data_points(it.get("data", []))
        jr = judge_rule(rule, norm, compiled=compiled_by_id[rule_id])
        if jr.matched:
            alerts.append(asdict(build_alert(rule, jr, device_id=device_id)))
    t_eval_end = time.time()

    alerts_by_device: Dict[str, List[Dict[str, Any]]] = {}
    for a in alerts:
        did = str(a.get("device_id", "")).strip() or "unknown"
        alerts_by_device.setdefault(did, []).append(a)
    fault_devices = sorted([d for d, arr in alerts_by_device.items() if arr and d != "unknown"])
    fault_count = len(fault_devices)

    all_devices = set()
    for it in items:
        if isinstance(it, dict):
            did = str(it.get("deviceId", "")).strip()
            if did:
                all_devices.add(did)
    total_devices = len(all_devices)
    normal_count = total_devices - fault_count

    results = {
        "total_devices": total_devices,
        "fault_devices": fault_devices,
        "fault_count": fault_count,
        "normal_count": normal_count,
        "alerts_by_device": alerts_by_device,
        "end_ts": end_ts,
    }
    if args.json:
        results["timing"] = {
            "api_ms": round((t_api_end - t_api_start) * 1000, 1),
            "eval_ms": round((t_eval_end - t_eval_start) * 1000, 1),
            "total_ms": round((t_eval_end - t_api_start) * 1000, 1),
        }
        print(json.dumps(results, ensure_ascii=False))
    else:
        total_ms = round((t_eval_end - t_api_start) * 1000, 1)
        if not fault_devices:
            print(
                f"总设备 {total_devices} 台，正常 {normal_count} 台，故障 0 台 | "
                f"取数 {round((t_api_end-t_api_start)*1000,1)}ms，"
                f"评估 {round((t_eval_end-t_eval_start)*1000,1)}ms，总耗时 {total_ms}ms"
            )
        else:
            print(
                f"总设备 {total_devices} 台，故障 {fault_count} 台，正常 {normal_count} 台 | "
                f"取数 {round((t_api_end-t_api_start)*1000,1)}ms，"
                f"评估 {round((t_eval_end-t_eval_start)*1000,1)}ms，总耗时 {total_ms}ms"
            )
            print("设备\t故障\t原因分析\t专家处理建议")

            def _cell(x: Any) -> str:
                s = str(x or "").strip()
                if not s:
                    return "—"
                return s.replace("\t", " ").replace("\r\n", " ").replace("\n", " ").replace("\r", " ")

            for d in fault_devices:
                for a in alerts_by_device.get(d, []):
                    print(
                        f"{_cell(a.get('device_id', d))}\t{_cell(a.get('rule_name', ''))}\t"
                        f"{_cell(a.get('reason_analysis', ''))}\t{_cell(a.get('expert_advice', ''))}"
                    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except JudgeError as e:
        print(str(e), file=sys.stderr)
        raise SystemExit(1) from e
