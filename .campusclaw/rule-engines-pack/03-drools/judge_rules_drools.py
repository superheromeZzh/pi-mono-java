from __future__ import annotations

import argparse
import json
import os
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import shutil
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

try:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass


class JudgeError(Exception):
    """巡检脚本业务异常（不向用户暴露堆栈，由 main 统一处理）。"""
    pass


HERE = Path(__file__).resolve().parent
PACK_ROOT = HERE.parent
DEFAULT_RULESET_PATH = HERE / "rules" / "ruleset.json"
DEFAULT_DRL_PATH = HERE / "rules" / "device_inspection.drl"

DEFAULT_MOCK_API_URL = "http://127.0.0.1:18080/fetch"
# Drools HTTP 服务基址（完整工程 spring-boot 默认端口 18081）
DEFAULT_DROOLS_API_BASE_URL = "http://127.0.0.1:18081"


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
        try:
            dt = datetime.fromisoformat(s)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return dt.timestamp()
        except Exception as e:  # noqa: BLE001
            raise JudgeError(f"时间戳无效：{ts!r}") from e
    raise JudgeError(f"时间戳类型无效：{type(ts).__name__}")


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
    candidates = [
        PACK_ROOT / "shared" / "mock_api_server.py",
        Path(r"C:\Users\Jason\.openclaw\workspace\mock_api_server.py"),
        Path.cwd() / "mock_api_server.py",
    ]
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


def _call_api(*, queries: List[Dict[str, Any]], end_ts: float) -> Dict[str, Any]:
    url = os.environ.get("DEVICE_INSPECTION_API_URL", DEFAULT_MOCK_API_URL).strip() or DEFAULT_MOCK_API_URL
    body = json.dumps({"endTs": end_ts, "queries": queries}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url=url,
        method="POST",
        data=body,
        headers={"Content-Type": "application/json; charset=utf-8"},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:  # noqa: S310
            raw = resp.read()
    except Exception:
        _try_start_mock_api_server()
        with urllib.request.urlopen(req, timeout=10) as resp:  # noqa: S310
            raw = resp.read()
    obj = json.loads(raw.decode("utf-8"))
    if not isinstance(obj, dict):
        raise JudgeError("接口返回必须是 JSON 对象")
    return obj


def _normalize_series(data: Any) -> List[Dict[str, Any]]:
    if not isinstance(data, list) or not data:
        raise JudgeError("data 必须是非空 JSON 数组")
    out: List[Dict[str, Any]] = []
    for i, item in enumerate(data):
        if not isinstance(item, dict):
            raise JudgeError(f"data[{i}] 必须是 JSON 对象")
        if "ts" not in item:
            raise JudgeError(f"data[{i}] 缺少字段 ts")
        if "points" in item and isinstance(item.get("points"), dict):
            facts = dict(item["points"])
        else:
            facts = {k: v for k, v in item.items() if k not in {"ts", "device_type", "deviceType", "component"}}
        out.append({"ts": _parse_ts_to_epoch_seconds(item["ts"]), **facts})
    out.sort(key=lambda p: float(p["ts"]))
    return out


def _window_points(series: List[Dict[str, Any]], *, window_seconds: int) -> List[Dict[str, Any]]:
    if not series:
        return []
    now_ts = max(float(p["ts"]) for p in series)
    start_ts = now_ts - float(window_seconds)
    return [p for p in series if start_ts <= float(p["ts"]) <= now_ts]


def _drools_eval_hits_http(*, rule_id: str, series: List[Dict[str, Any]]) -> Dict[str, Any]:
    """调用 Spring Boot Drools 服务的 /api/v1/evaluate（规则已打包在服务端，无需传 drlPath）。"""
    base = os.environ.get("DROOLS_API_BASE_URL", DEFAULT_DROOLS_API_BASE_URL).strip().rstrip("/")
    url = f"{base}/api/v1/evaluate"
    body = json.dumps({"ruleId": rule_id, "series": series}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url=url,
        method="POST",
        data=body,
        headers={"Content-Type": "application/json; charset=utf-8"},
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:  # noqa: S310
            raw = resp.read()
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace") if e.fp else str(e)
        raise JudgeError(f"Drools HTTP 服务返回错误 {e.code}：{detail}") from e
    obj = json.loads(raw.decode("utf-8"))
    if not isinstance(obj, dict):
        raise JudgeError("Drools HTTP 返回体必须是 JSON 对象")
    return obj


def _drools_eval_hits_maven(*, drl_path: Path, rule_id: str, series: List[Dict[str, Any]]) -> Dict[str, Any]:
    """备用：通过 Maven 子进程加载磁盘 DRL（开发或未启动 HTTP 服务时）。"""
    runner_dir = HERE / "scripts" / "drools-runner"
    payload = {"drlPath": str(drl_path), "ruleId": rule_id, "series": series}
    mvn = (
        os.environ.get("MAVEN_CMD", "").strip()
        or shutil.which("mvn")
        or shutil.which("mvn.cmd")
        or shutil.which("mvn.bat")
    )
    if not mvn:
        # Scoop 常见安装路径（Cursor 子进程有时未继承用户 PATH）
        home = Path.home()
        candidates = [
            home / "scoop" / "shims" / "mvn.cmd",
            home / "scoop" / "shims" / "mvn.bat",
            home / "scoop" / "apps" / "maven" / "current" / "bin" / "mvn.cmd",
            home / "scoop" / "apps" / "maven" / "current" / "bin" / "mvn.bat",
        ]
        mvn_path = next((str(p) for p in candidates if p.exists()), "")
        mvn = mvn_path or None
    try:
        base_cmd = [str(mvn), "-f", str(runner_dir / "pom.xml"), "-DskipTests", "compile", "exec:java"]
        p = subprocess.run(  # noqa: S603
            base_cmd,
            input=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    except FileNotFoundError as e:
        raise JudgeError(
            "找不到 Maven（mvn）命令。请先安装 Maven 并将 mvn 加入 PATH，然后重试。"
        ) from e

    def _extract_last_json_obj(text: str) -> Optional[Dict[str, Any]]:
        s = text.strip()
        if not s:
            return None
        dec = json.JSONDecoder()
        # 从末尾向前扫描，找到 `{` 后尝试解析 JSON 对象（兼容 Maven 日志与 JSON 混排）
        for i in range(len(s) - 1, -1, -1):
            if s[i] != "{":
                continue
            try:
                obj, end = dec.raw_decode(s[i:])
                if isinstance(obj, dict) and "hits" in obj and "total" in obj:
                    return obj
            except Exception:
                continue
        return None

    stdout_text = p.stdout.decode("utf-8", errors="replace")
    stderr_text = p.stderr.decode("utf-8", errors="replace")
    obj0 = _extract_last_json_obj(stdout_text) or _extract_last_json_obj(stderr_text)
    if obj0 is not None:
        return obj0

    if p.returncode != 0:
        # 失败时用 -e 再打一次，便于输出完整 Java 堆栈
        p2 = subprocess.run(  # noqa: S603
            [str(mvn), "-e", "-f", str(runner_dir / "pom.xml"), "-DskipTests", "compile", "exec:java"],
            input=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        stderr = p2.stderr.decode("utf-8", errors="replace").strip() or p.stderr.decode("utf-8", errors="replace").strip()
        stdout = p2.stdout.decode("utf-8", errors="replace").strip() or p.stdout.decode("utf-8", errors="replace").strip()
        obj2 = _extract_last_json_obj(stdout) or _extract_last_json_obj(stderr)
        if obj2 is not None:
            return obj2
        detail = stderr or stdout or "Drools 执行器运行失败"
        raise JudgeError(detail)
    raise JudgeError("Drools 执行器未输出可解析的 JSON 结果")


def _drools_eval_hits(*, drl_path: Path, rule_id: str, series: List[Dict[str, Any]]) -> Dict[str, Any]:
    """
    优先走 HTTP（完整 Drools 工程）；若未启动服务且允许回退，则使用 Maven 子进程。
    环境变量：
    - DROOLS_USE_MAVEN_ONLY=1：仅使用 Maven，不请求 HTTP
    - DROOLS_HTTP_FALLBACK_MAVEN=0：HTTP 连不上时不回退（默认 1 为允许回退）
    """
    maven_only = os.environ.get("DROOLS_USE_MAVEN_ONLY", "").strip().lower() in ("1", "true", "yes")
    allow_fallback = os.environ.get("DROOLS_HTTP_FALLBACK_MAVEN", "1").strip().lower() not in ("0", "false", "no")

    if not maven_only:
        try:
            return _drools_eval_hits_http(rule_id=rule_id, series=series)
        except urllib.error.URLError as e:
            if allow_fallback:
                return _drools_eval_hits_maven(drl_path=drl_path, rule_id=rule_id, series=series)
            raise JudgeError(
                f"无法连接 Drools HTTP 服务（{os.environ.get('DROOLS_API_BASE_URL', DEFAULT_DROOLS_API_BASE_URL)}）。"
                f"请先启动 services/drools-inspection-api，或设置 DROOLS_HTTP_FALLBACK_MAVEN=1 使用 Maven 子进程。详情：{e}"
            ) from e

    return _drools_eval_hits_maven(drl_path=drl_path, rule_id=rule_id, series=series)


@dataclass(frozen=True)
class Alert:
    device_id: str
    rule_id: str
    rule_name: str
    reason_analysis: str
    expert_advice: str


def _cell(x: Any) -> str:
    s = str(x or "").strip()
    if not s:
        return "—"
    return s.replace("\t", " ").replace("\r\n", " ").replace("\n", " ").replace("\r", " ")


def main(argv: Optional[List[str]] = None) -> int:
    p = argparse.ArgumentParser(
        description="Drools 原生 DRL 设备巡检：调用取数接口 → 窗口比例 → 仅输出故障告警（DEVICE_INSPECTION_API_URL）"
    )
    p.add_argument("--ruleset", default=str(DEFAULT_RULESET_PATH), help="巡检元数据 ruleset.json 路径")
    p.add_argument("--drl", default=str(DEFAULT_DRL_PATH), help="Drools 规则文件（.drl）路径")
    p.add_argument("--end-ts", default=None, help="评估结束时间：Unix 秒或 ISO8601；默认当前 UTC")
    p.add_argument("--json", action="store_true", help="以 JSON 输出结果")
    args = p.parse_args(argv)

    ruleset_path = Path(args.ruleset)
    drl_path = Path(args.drl)
    if not ruleset_path.exists():
        raise JudgeError(f"未找到 ruleset 文件：{ruleset_path}")
    if not drl_path.exists():
        raise JudgeError(f"未找到 DRL 文件：{drl_path}")

    doc = json.loads(ruleset_path.read_text(encoding="utf-8"))
    rules = doc.get("rules", [])
    if not isinstance(rules, list) or not rules:
        raise JudgeError("ruleset.json 中 rules 必须是非空数组")

    end_ts = _parse_ts_to_epoch_seconds(args.end_ts) if args.end_ts is not None else datetime.now(tz=timezone.utc).timestamp()

    # 构造取数接口请求体（字段形状与现有 mock API 一致）
    queries: List[Dict[str, Any]] = []
    for r in rules:
        if not isinstance(r, dict):
            continue
        meta = r.get("meta") or {}
        window = r.get("window") or {}
        device_type = str(meta.get("deviceType", "")).strip()
        component = str(meta.get("component", "")).strip()
        points = meta.get("points", [])
        if not isinstance(points, list):
            points = []
        points = [str(x).strip() for x in points if str(x).strip()]
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

    api_resp = _call_api(queries=queries, end_ts=end_ts)
    raw_items = api_resp.get("items", [])
    if not isinstance(raw_items, list):
        raise JudgeError("接口返回的 items 必须是数组")
    items = [it for it in raw_items if isinstance(it, dict)]

    rules_by_id = {str(r.get("id", "")).strip(): r for r in rules if isinstance(r, dict)}
    alerts: List[Alert] = []

    for it in items:
        rule_id = str(it.get("requestId", "")).strip()
        device_id = str(it.get("deviceId", "")).strip() or "unknown"
        if not rule_id or rule_id not in rules_by_id:
            continue
        r = rules_by_id[rule_id]
        series = _normalize_series(it.get("data", []))
        window_seconds = int(((r.get("window") or {}).get("durationSeconds", 0)) or 0)
        eff = r.get("effective") or {}
        threshold = float(eff.get("threshold", 1.0))
        min_samples = int(eff.get("minSamples", 1))

        windowed = _window_points(series, window_seconds=window_seconds)
        total = len(windowed)
        if total <= 0:
            continue

        drools_resp = _drools_eval_hits(drl_path=drl_path, rule_id=rule_id, series=windowed)
        hits = int(drools_resp.get("hits", 0) or 0)
        ratio = (hits / total) if total else 0.0
        matched = (total >= min_samples) and (ratio >= threshold)
        if matched:
            alerts.append(
                Alert(
                    device_id=device_id,
                    rule_id=rule_id,
                    rule_name=str(r.get("name", "")).strip(),
                    reason_analysis=str(r.get("原因分析", "") or "").strip(),
                    expert_advice=str(r.get("专家处理建议", "") or "").strip(),
                )
            )

    alerts_by_device: Dict[str, List[Dict[str, Any]]] = {}
    for a in alerts:
        alerts_by_device.setdefault(a.device_id or "unknown", []).append(asdict(a))
    fault_devices = sorted([d for d, arr in alerts_by_device.items() if arr and d != "unknown"])

    if args.json:
        print(json.dumps({"fault_devices": fault_devices, "alerts_by_device": alerts_by_device, "end_ts": end_ts}, ensure_ascii=False))
        return 0

    if not fault_devices:
        print("OK：无告警")
        return 0

    print("设备\t故障\t原因分析\t专家处理建议")
    for d in fault_devices:
        for a in alerts_by_device.get(d, []):
            print(
                f"{_cell(a.get('device_id', d))}\t{_cell(a.get('rule_name',''))}\t{_cell(a.get('reason_analysis',''))}\t{_cell(a.get('expert_advice',''))}"
            )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except JudgeError as e:
        print(str(e), file=sys.stderr)
        raise SystemExit(1) from e

