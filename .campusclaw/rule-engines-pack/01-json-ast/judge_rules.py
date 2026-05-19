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


_HERE = Path(__file__).resolve().parent
_PACK_ROOT = _HERE.parent
DEFAULT_PROJECT_RULES_PATH = _HERE / "rules" / "rules.json"
DEFAULT_OPENCLAW_UNIFIED_RULES_PATH = Path(r"C:\Users\Jason\.openclaw\workspace\rules\rules.json")
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
    # OpenClaw 工作流里统一 rules.json 是单一真源：若存在则优先用它；否则回退到项目 demo 规则。
    if DEFAULT_OPENCLAW_UNIFIED_RULES_PATH.exists():
        return DEFAULT_OPENCLAW_UNIFIED_RULES_PATH
    return DEFAULT_PROJECT_RULES_PATH


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

    约定启动脚本路径：
    - OpenClaw：C:/Users/Jason/.openclaw/workspace/mock_api_server.py
    - 项目根目录：<repo>/mock_api_server.py
    """
    if _is_port_listening("127.0.0.1", 18080):
        return

    candidates = [
        _PACK_ROOT / "shared" / "mock_api_server.py",
        Path(r"C:\Users\Jason\.openclaw\workspace\mock_api_server.py"),
        Path.cwd() / "mock_api_server.py",
    ]
    script = next((p for p in candidates if p.exists()), None)
    if script is None:
        return

    try:
        # 后台启动，不阻塞巡检流程
        subprocess.Popen(  # noqa: S603
            [sys.executable, str(script)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            stdin=subprocess.DEVNULL,
            creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0),
        )
    except Exception:
        return

    # 等待端口就绪（最多 3 秒）
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
        # 若接口不可达，尝试自动启动 mock API 后再重试一次
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
        # 兼容某些节点携带其它子字段
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
        # 兼容部分生成器把四则运算编码成 func 形式（例如 {"func":"*","args":[a,b]}）
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


def _make_facts(expr: Dict[str, Any], *, want_true: bool) -> Optional[Dict[str, Any]]:
    """
    构造一组 facts，使 expr 的结果为 want_true。
    基于模式匹配，尽量保持确定性（用于 --synthetic / --fetch 模拟取数）。
    """
    if not isinstance(expr, dict):
        return None

    # abs(A - B) > N
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
                    if want_true:
                        return {fb: sp, fa: sp + n + 1.0}
                    return {fb: sp, fa: sp + max(0.0, n - 1.0)}

    # (X < low) or (X > high)
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
            if want_true:
                return {fx: high + 1.0}
            return {fx: (low + high) / 2.0}

    # 简单比较：(fact OP const) 或 (const OP fact)
    if expr.get("op") in (">", ">=", "<", "<=", "==", "!=") and isinstance(expr.get("left"), dict) and isinstance(expr.get("right"), dict):
        op = str(expr["op"])
        left = expr["left"]
        right = expr["right"]

        def _pick_value(*, want_true: bool, op: str, c: float) -> float:
            if op == ">":
                return (c + 1.0) if want_true else (c - 1.0)
            if op == ">=":
                return c if want_true else (c - 1.0)
            if op == "<":
                return (c - 1.0) if want_true else (c + 1.0)
            if op == "<=":
                return c if want_true else (c + 1.0)
            if op == "==":
                return c if want_true else (c + 1.0)
            return (c + 1.0) if want_true else c

        if "fact" in left and isinstance(right.get("const"), (int, float)):
            fx = str(left["fact"])
            c = float(right["const"])
            return {fx: _pick_value(want_true=want_true, op=op, c=c)}

        if "fact" in right and isinstance(left.get("const"), (int, float)):
            fx = str(right["fact"])
            c = float(left["const"])
            rev = {">": "<", ">=": "<=", "<": ">", "<=": ">=", "==": "==", "!=": "!="}[op]
            return {fx: _pick_value(want_true=want_true, op=rev, c=c)}

    return None


def _construct_facts(expr: Dict[str, Any], *, want: bool) -> Dict[str, Any]:
    if not isinstance(expr, dict):
        raise JudgeError("Invalid expr node")

    if "op" in expr:
        direct = _make_facts(expr, want_true=want)
        if direct is not None:
            return direct

        op = str(expr.get("op"))
        if op == "not":
            return _construct_facts(expr.get("left", {}), want=not want)
        if op == "and":
            if want:
                return _merge_facts(
                    _construct_facts(expr.get("left", {}), want=True),
                    _construct_facts(expr.get("right", {}), want=True),
                )
            try:
                return _construct_facts(expr.get("left", {}), want=False)
            except JudgeError:
                return _construct_facts(expr.get("right", {}), want=False)
        if op == "or":
            if want:
                try:
                    return _construct_facts(expr.get("left", {}), want=True)
                except JudgeError:
                    return _construct_facts(expr.get("right", {}), want=True)
            return _merge_facts(
                _construct_facts(expr.get("left", {}), want=False),
                _construct_facts(expr.get("right", {}), want=False),
            )

    raise JudgeError("trigger 不支持自动造数验证（需要扩展模式匹配）")


def _build_window_series(rule: Dict[str, Any], *, want_ratio_ge_threshold: bool) -> List[Dict[str, Any]]:
    window_seconds = int(rule["window"]["durationSeconds"])
    thr = float(rule["effective"]["threshold"])
    expr = rule["trigger"]["expr"]

    step = 60
    samples = int(window_seconds / step) + 1
    if samples <= 0:
        raise JudgeError("window samples <= 0")

    required_hits = int((thr * samples) + 0.999999)  # 向上取整（ceil）
    required_hits = max(0, min(samples, required_hits))
    below_hits = max(0, required_hits - 1)
    want_hits = required_hits if want_ratio_ge_threshold else below_hits

    true_facts = _construct_facts(expr, want=True)
    false_facts = _construct_facts(expr, want=False)

    now_ts = 1_700_000_000.0
    start_ts = now_ts - window_seconds
    series: List[Dict[str, Any]] = []
    for i in range(samples):
        ts = start_ts + i * step
        facts = true_facts if i < want_hits else false_facts
        series.append({"ts": ts, **facts})
    return series


def _normalize_data_points(data: Any) -> List[Dict[str, Any]]:
    if not isinstance(data, list) or not data:
        raise JudgeError("data must be a non-empty JSON array")

    out: List[Dict[str, Any]] = []
    for i, item in enumerate(data):
        if not isinstance(item, dict):
            raise JudgeError(f"data[{i}] must be an object")
        if "ts" not in item:
            raise JudgeError(f"data[{i}] missing ts")

        # 格式 B：reading（包含 points 字典）
        if "points" in item and isinstance(item.get("points"), dict):
            facts = dict(item["points"])
        else:
            # 格式 A：点位直接铺在顶层（排除常见元数据字段）
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
        help="规则文件路径（默认：若存在则优先 OpenClaw 统一规则，否则为 demo_rule_engine/rules.json）",
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

    # 设备巡检：唯一流程（调接口取数 -> 规则判断 -> 输出故障设备）
    end_ts = _parse_ts_to_epoch_seconds(args.end_ts) if args.end_ts is not None else datetime.now(tz=timezone.utc).timestamp()

    # 从规则提取出“取数接口”所需的参数（deviceType/component/points/windowSeconds）
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
        # 自动补齐 trigger.expr 里实际依赖的 fact 名称
        fact_points = sorted([p for p in _collect_fact_names(expr) if p])
        points = sorted(set(points) | set(fact_points))
        window_seconds = int(window.get("durationSeconds", 0) or 0)
        if not device_type or not component or not points or window_seconds <= 0:
            continue
        queries.append(
            {
                "requestId": str(r.get("id", "")).strip(),  # 用规则 id 作为关联键
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
            # TSV 输出（便于复制到 Excel）
            print("设备\t故障\t原因分析\t专家处理建议")

            def _cell(x: Any) -> str:
                s = str(x or "").strip()
                if not s:
                    return "—"
                # 压平换行/制表符，避免破坏 TSV
                s = s.replace("\t", " ").replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
                return s

            for d in fault_devices:
                for a in alerts_by_device.get(d, []):
                    device = _cell(a.get("device_id", d))
                    fault_name = _cell(a.get("rule_name", ""))  # 更适合表格展示
                    reason = _cell(a.get("reason_analysis", ""))
                    advice = _cell(a.get("expert_advice", ""))
                    print(f"{device}\t{fault_name}\t{reason}\t{advice}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

