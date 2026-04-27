from __future__ import annotations

import argparse
import json
import sys
import hashlib
import os
import urllib.request
import subprocess
import time
import socket
from dataclasses import dataclass, asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

try:
    # Windows 控制台常见默认编码不是 UTF-8；这里尽量强制为 UTF-8，减少中文乱码。
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass


class JudgeError(Exception):
    pass


# 本脚本路径：.../.campusclaw/skills/device-inspection/scripts/judge_rules.py
_SCRIPT_FILE = Path(__file__).resolve()
_CAMPUSCLAW_ROOT = _SCRIPT_FILE.parents[3]  # .../.campusclaw
# 与本仓库一同分发的预制规则（相对路径，任意机器可用）
DEFAULT_BUNDLED_RULES_PATH = _CAMPUSCLAW_ROOT / "rules" / "rules.json"
# 可选：若你在其他项目里放了 demo_rule_engine，且从该项目目录启动，则可用 cwd 下的规则
DEFAULT_PROJECT_RULES_PATH = Path("demo_rule_engine") / "rules.json"
DEFAULT_MOCK_API_URL = "http://127.0.0.1:18080/fetch"


def _merge_facts(a: Dict[str, Any], b: Dict[str, Any]) -> Dict[str, Any]:
    out = dict(a)
    for k, v in b.items():
        if k in out and out[k] != v:
            raise JudgeError(f"Conflicting fact assignments: {k}={out[k]!r} vs {v!r}")
        out[k] = v
    return out


def _parse_ts_to_epoch_seconds(ts: Any) -> float:
    if isinstance(ts, (int, float)):
        return float(ts)
    if isinstance(ts, str):
        s = ts.strip()
        # 兼容纯数字字符串（epoch seconds）
        try:
            return float(s)
        except Exception:
            pass
        # 兼容以 Z 结尾的 UTC 时间
        if s.endswith("Z"):
            s = s[:-1] + "+00:00"
        try:
            dt = datetime.fromisoformat(s)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return dt.timestamp()
        except Exception as e:  # noqa: BLE001
            raise JudgeError(f"Invalid ts: {ts!r}") from e
    raise JudgeError(f"Invalid ts type: {type(ts)}")


def _device_id_for(device_type: str, component: str) -> str:
    key = f"{device_type}|{component}".encode("utf-8")
    h = hashlib.sha1(key).hexdigest()[:10]
    dt = (device_type or "dev").lower().replace(" ", "_")
    comp = (component or "comp").lower().replace(" ", "_")
    return f"{dt}_{comp}_{h}"


def _load_rules_doc(path: Path) -> Dict[str, Any]:
    doc = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(doc, dict):
        raise JudgeError(f"rules.json must be an object: {path}")
    if doc.get("version") != 1:
        raise JudgeError(f"rules.json version must be 1: {path}")
    rules = doc.get("rules")
    if not isinstance(rules, list):
        raise JudgeError(f"rules.json rules must be an array: {path}")
    return doc


def _merge_rules_docs(*docs: Dict[str, Any]) -> List[Dict[str, Any]]:
    """合并多个 rules.json：按规则 id 覆盖（后者覆盖前者）。"""
    by_id: Dict[str, Dict[str, Any]] = {}
    ordered: List[str] = []
    for doc in docs:
        rules = doc.get("rules", [])
        if not isinstance(rules, list):
            continue
        for r in rules:
            if not isinstance(r, dict):
                continue
            rid = str(r.get("id", "")).strip()
            if not rid:
                continue
            if rid not in by_id:
                ordered.append(rid)
            by_id[rid] = r
    return [by_id[rid] for rid in ordered if rid in by_id]


def _default_rules_path() -> Path:
    """
    默认规则文件（均为相对/可移植路径，不绑定某台电脑的用户目录）：
    1) 环境变量 DEVICE_INSPECTION_RULES_PATH（若设置且文件存在）
    2) 仓库内预制规则：.campusclaw/rules/rules.json
    3) 当前工作目录下 demo_rule_engine/rules.json（若存在）
    4) 仍回退到 2) 的路径（便于报错信息指向明确位置）
    """
    env = str(os.environ.get("DEVICE_INSPECTION_RULES_PATH", "") or "").strip()
    if env:
        p = Path(env).expanduser()
        if p.is_file():
            return p
    if DEFAULT_BUNDLED_RULES_PATH.is_file():
        return DEFAULT_BUNDLED_RULES_PATH
    if DEFAULT_PROJECT_RULES_PATH.is_file():
        return DEFAULT_PROJECT_RULES_PATH
    return DEFAULT_BUNDLED_RULES_PATH


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
    """
    若 mock API 未运行，尝试自动启动它（只在本机环境生效）。

    约定启动脚本路径（均为可移植路径）：
    1) 环境变量 DEVICE_INSPECTION_MOCK_API_SCRIPT（若设置且文件存在）
    2) 与本脚本同技能目录下的 mock_api_server.py
    3) 当前工作目录下的 mock_api_server.py
    """
    if _is_port_listening("127.0.0.1", 18080):
        return

    here = Path(__file__).resolve()
    skill_root = here.parents[1]
    env_script = str(os.environ.get("DEVICE_INSPECTION_MOCK_API_SCRIPT", "") or "").strip()
    candidates: List[Path] = []
    if env_script:
        candidates.append(Path(env_script).expanduser())
    candidates.extend(
        [
            skill_root / "mock_api_server.py",
            Path.cwd() / "mock_api_server.py",
        ]
    )
    script = next((p for p in candidates if p.exists()), None)
    if script is None:
        return

    try:
        subprocess.Popen(  # noqa: S603
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
    """
    调用 mock API 取数：
    - 输入：queries（由规则提取出的 deviceType/component/points/windowSeconds 等）+ endTs
    - 输出：{"version":1,"items":[{"deviceId":...,"requestId":...,"data":[{ts,points:{...}}, ...]}]}
    """
    url = os.environ.get("DEVICE_INSPECTION_API_URL", DEFAULT_MOCK_API_URL).strip() or DEFAULT_MOCK_API_URL
    body = json.dumps({"endTs": end_ts, "queries": queries}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url=url, method="POST", data=body, headers={"Content-Type": "application/json; charset=utf-8"})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:  # noqa: S310
            raw = resp.read()
    except Exception:
        _try_start_mock_api_server()
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:  # noqa: S310
                raw = resp.read()
        except Exception as e:  # noqa: BLE001
            raise JudgeError(f"调用取数接口失败：{e}")
    try:
        obj = json.loads(raw.decode("utf-8"))
    except Exception as e:  # noqa: BLE001
        raise JudgeError(f"接口返回不是合法 JSON：{e}")
    if not isinstance(obj, dict):
        raise JudgeError("接口返回必须是 JSON 对象")
    return obj


def _collect_fact_names(expr: Any, out: Optional[set] = None) -> set:
    """
    从 trigger.expr AST 中递归收集所有 { "fact": "..." } 字段名。
    用于自动补齐 meta.points 没列出的依赖点位，避免“缺点位导致永远不触发”。
    """
    if out is None:
        out = set()
    if isinstance(expr, dict):
        if "fact" in expr:
            out.add(str(expr.get("fact", "")).strip())
        for k in ("left", "right"):
            if k in expr:
                _collect_fact_names(expr.get(k), out)
        if "args" in expr and isinstance(expr.get("args"), list):
            for a in expr.get("args") or []:
                _collect_fact_names(a, out)
        for v in expr.values():
            if isinstance(v, (dict, list)):
                _collect_fact_names(v, out)
    elif isinstance(expr, list):
        for it in expr:
            _collect_fact_names(it, out)
    return out


def _get_fact(facts: Dict[str, Any], name: str) -> Any:
    if name not in facts:
        raise JudgeError(f"Missing fact: {name}")
    return facts[name]


def eval_expr(expr: Dict[str, Any], facts: Dict[str, Any]) -> Any:
    if "const" in expr:
        return expr["const"]
    if "fact" in expr:
        return _get_fact(facts, str(expr["fact"]))
    if "func" in expr:
        fn = str(expr["func"])
        args = [eval_expr(a, facts) for a in (expr.get("args", []) or [])]
        if fn in {"+", "-", "*", "/"}:
            if len(args) != 2:
                raise JudgeError(f"{fn}() expects 2 args")
            a0, a1 = args[0], args[1]
            if fn == "+":
                return a0 + a1
            if fn == "-":
                return a0 - a1
            if fn == "*":
                return a0 * a1
            return a0 / a1
        if fn == "abs":
            if len(args) != 1:
                raise JudgeError("abs() expects 1 arg")
            return abs(args[0])
        if fn == "min":
            if len(args) < 1:
                raise JudgeError("min() expects at least 1 arg")
            return min(args)
        if fn == "max":
            if len(args) < 1:
                raise JudgeError("max() expects at least 1 arg")
            return max(args)
        if fn == "clamp":
            if len(args) != 3:
                raise JudgeError("clamp() expects 3 args")
            x, lo, hi = args
            return max(lo, min(hi, x))
        if fn == "between":
            if len(args) != 3:
                raise JudgeError("between() expects 3 args")
            x, lo, hi = args
            return (x >= lo) and (x <= hi)
        if fn == "in":
            if len(args) != 2:
                raise JudgeError("in() expects 2 args: (x, list)")
            x, items = args
            if not isinstance(items, list):
                raise JudgeError("in() expects 2nd arg to be a list")
            return x in items
        if fn == "if":
            if len(args) != 3:
                raise JudgeError("if() expects 3 args")
            cond, a, b = args
            return a if bool(cond) else b
        raise JudgeError(f"Unsupported func: {fn}")
    if "op" in expr:
        op = str(expr["op"])
        if op == "not":
            left = eval_expr(expr["left"], facts)
            return not bool(left)
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
        raise JudgeError(f"Unsupported op: {op}")
    raise JudgeError("Invalid expr node")


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


def judge_rule(rule: Dict[str, Any], series: List[Dict[str, Any]]) -> JudgeResult:
    window_seconds = int(rule["window"]["durationSeconds"])
    threshold = float(rule["effective"]["threshold"])
    min_samples = int(rule["effective"].get("minSamples", 1))
    expr = rule["trigger"]["expr"]

    now_ts = max(float(p["ts"]) for p in series)
    start_ts = now_ts - window_seconds
    window_points = [p for p in series if start_ts <= float(p["ts"]) <= now_ts]

    hits = 0
    total = 0
    for p in window_points:
        facts = {k: v for k, v in p.items() if k != "ts"}
        try:
            ok = bool(eval_expr(expr, facts))
        except JudgeError:
            ok = False
        total += 1
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
    p = argparse.ArgumentParser()
    p.add_argument(
        "--rules",
        default=str(_default_rules_path()),
        help="规则文件路径（默认：.campusclaw/rules/rules.json；可用环境变量 DEVICE_INSPECTION_RULES_PATH 覆盖）",
    )
    p.add_argument(
        "--rules-extra",
        action="append",
        default=[],
        help="额外要合并的 rules.json（可多次提供；后者覆盖前者）。",
    )
    p.add_argument(
        "--end-ts",
        default=None,
        help="结束时间戳（epoch seconds 或 ISO8601）。默认：当前 UTC 时间",
    )
    p.add_argument("--json", action="store_true", help="输出 JSON")
    args = p.parse_args(argv)

    primary_rules_path = Path(args.rules)
    docs: List[Dict[str, Any]] = []
    docs.append(_load_rules_doc(primary_rules_path))
    for extra in list(args.rules_extra or []):
        ep = Path(str(extra))
        if ep.exists():
            docs.append(_load_rules_doc(ep))

    rules = _merge_rules_docs(*docs)
    if not rules:
        raise JudgeError("merged rules must be a non-empty array")

    end_ts = _parse_ts_to_epoch_seconds(args.end_ts) if args.end_ts is not None else datetime.now(tz=timezone.utc).timestamp()

    queries: List[Dict[str, Any]] = []
    for r in rules:
        if not isinstance(r, dict):
            continue
        meta = r.get("meta") or {}
        window = r.get("window") or {}
        trigger = r.get("trigger") or {}
        expr = trigger.get("expr")
        device_type = str(meta.get("deviceType", "")).strip()
        component = str(meta.get("component", "")).strip()
        points = meta.get("points", [])
        if not isinstance(points, list):
            points = []
        points = [str(p).strip() for p in points if str(p).strip()]
        fact_points = sorted([p for p in _collect_fact_names(expr) if p])
        points = sorted(set(points) | set(fact_points))
        window_seconds = int(window.get("durationSeconds", 0) or 0)
        if not device_type or not component or not points or window_seconds <= 0:
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

    api_resp = _call_mock_api(queries=queries, end_ts=end_ts)
    items = api_resp.get("items", [])
    if not isinstance(items, list):
        raise JudgeError("接口返回字段 items 必须是数组")

    alerts: List[Dict[str, Any]] = []

    rules_by_id: Dict[str, Dict[str, Any]] = {str(r.get("id", "")).strip(): r for r in rules if isinstance(r, dict)}
    for it in items:
        if not isinstance(it, dict):
            continue
        rule_id = str(it.get("requestId", "")).strip()
        device_id = str(it.get("deviceId", "")).strip()
        data = it.get("data", [])
        if not rule_id or rule_id not in rules_by_id:
            continue
        rule = rules_by_id[rule_id]
        norm = _normalize_data_points(data)

        jr = judge_rule(rule, norm)
        if jr.matched:
            alerts.append(asdict(build_alert(rule, jr, device_id=device_id)))

    alerts_by_device: Dict[str, List[Dict[str, Any]]] = {}
    for a in alerts:
        did = str(a.get("device_id", "")).strip() or "unknown"
        alerts_by_device.setdefault(did, []).append(a)
    fault_devices = sorted([d for d, arr in alerts_by_device.items() if arr and d != "unknown"])

    results = {
        "fault_devices": fault_devices,
        "alerts_by_device": alerts_by_device,
        "end_ts": end_ts,
    }

    if args.json:
        print(json.dumps(results, ensure_ascii=False))
    else:
        if not fault_devices:
            print("OK：无告警")
        else:
            print("设备\t故障\t原因分析\t专家处理建议")

            def _cell(x: Any) -> str:
                s = str(x or "").strip()
                if not s:
                    return "—"
                s = s.replace("\t", " ").replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
                return s

            for d in fault_devices:
                for a in alerts_by_device.get(d, []):
                    device = _cell(a.get("device_id", d))
                    fault_name = _cell(a.get("rule_name", ""))
                    reason = _cell(a.get("reason_analysis", ""))
                    advice = _cell(a.get("expert_advice", ""))
                    print(f"{device}\t{fault_name}\t{reason}\t{advice}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

