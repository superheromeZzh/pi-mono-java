from __future__ import annotations

import json
import sys
import hashlib
import os
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any, Dict, List, Tuple, Optional

# 复用巡检 skill 的表达式/异常类型（用于 mock API 返回窗口数据）
_SKILL_DIR = Path(__file__).resolve().parent / "scripts"
sys.path.insert(0, str(_SKILL_DIR))

from judge_rules import JudgeError  # type: ignore  # noqa: E402


FIXTURES_DIR = Path(os.environ.get("DEVICE_INSPECTION_FIXTURES_DIR", str(Path(__file__).resolve().parent / "mock_fixtures")))


try:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass


def _now_ts() -> float:
    return datetime.now(tz=timezone.utc).timestamp()


def _read_json_body(handler: BaseHTTPRequestHandler) -> Dict[str, Any]:
    length = int(handler.headers.get("Content-Length", "0") or "0")
    raw = handler.rfile.read(length) if length > 0 else b"{}"
    try:
        obj = json.loads(raw.decode("utf-8"))
    except Exception as e:  # noqa: BLE001
        raise ValueError(f"invalid json body: {e}") from e
    if not isinstance(obj, dict):
        raise ValueError("json body must be an object")
    return obj


def _device_id(device_type: str, component: str) -> str:
    dt = (device_type or "dev").strip()
    comp = (component or "comp").strip()
    return f"{dt}_{comp}"


def _load_fixture(*, request_id: str) -> Optional[List[Tuple[str, List[Dict[str, Any]]]]]:
    """
    读取离线夹具数据（fixtures）。
    文件路径：<FIXTURES_DIR>/<requestId>.json

    支持两种形态（单设备 / 多设备）：
    1) {"deviceId": "...", "data": [{ts, points:{...}}, ...]}
    2) {"data": [...]}  （deviceId 将由 query 推导）
    3) {"devices": [{"deviceId":"...","data":[...]}, ...]}
    """
    if not request_id:
        return None
    path = FIXTURES_DIR / f"{request_id}.json"
    if not path.exists():
        return None
    obj = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(obj, dict):
        raise JudgeError(f"fixture 文件必须是 JSON 对象：{path}")
    if isinstance(obj.get("devices"), list):
        out: List[Tuple[str, List[Dict[str, Any]]]] = []
        for d in obj["devices"]:
            if not isinstance(d, dict):
                continue
            did = str(d.get("deviceId", "")).strip()
            data = d.get("data")
            if not did or not isinstance(data, list) or not data:
                continue
            out.append((did, data))
        if not out:
            raise JudgeError(f"fixture.devices 必须包含至少 1 个有效设备：{path}")
        return out

    data = obj.get("data")
    if not isinstance(data, list) or not data:
        raise JudgeError(f"fixture.data 必须是非空数组：{path}")
    device_id = str(obj.get("deviceId", "")).strip()
    return [(device_id, data)]


def _should_fault(*, device_id: str, request_id: str, fault_rate: int = 10, end_ts: float = 0.0) -> bool:
    """
    决定本次查询返回“故障数据”还是“健康数据”。

    - fault_rate：0~100
    - 使用 hash(device_id|request_id|salt) 做确定性分配：同一输入每次结果一致，便于联调复现
    """
    fault_rate = max(0, min(100, int(fault_rate)))
    salt = int(float(end_ts) // 3600)  # 以小时为粒度
    key = f"{device_id}|{request_id}|{salt}".encode("utf-8")
    h = hashlib.sha1(key).hexdigest()
    bucket = int(h[:8], 16) % 100
    return bucket < fault_rate


def _build_timeseries_for_query(query: Dict[str, Any], *, end_ts: float) -> List[Tuple[str, List[Dict[str, Any]]]]:
    """
    mock API 的职责：仅按“设备/元器件/点位/有效时间”返回数据。
    """
    device_type = str(query.get("deviceType", "")).strip()
    component = str(query.get("component", "")).strip()
    points = query.get("points", [])
    if not isinstance(points, list):
        points = []
    points = [str(p).strip() for p in points if str(p).strip()]

    window_seconds = int(query.get("windowSeconds", 0) or 0)
    if window_seconds <= 0:
        raise JudgeError("invalid windowSeconds")

    start_ts = end_ts - float(window_seconds)
    step = 60
    samples = int(window_seconds / step) + 1
    samples = max(2, samples)

    request_id = str(query.get("requestId", "")).strip()
    device_id = _device_id(device_type, component)

    # 最高优先级：若存在离线夹具数据，则直接返回（保证演示可复现、无随机波动）
    fx = _load_fixture(request_id=request_id)
    if fx is not None:
        out = []
        for fx_device_id, fx_data in fx:
            out.append((fx_device_id or device_id, fx_data))
        return out

    fault = _should_fault(device_id=device_id, request_id=request_id, fault_rate=10, end_ts=end_ts)

    def _value_for_point(p: str, i: int) -> float:
        pl = p.lower()
        if "status" in pl or "状态" in p:
            return 1.0
        if "alarm" in pl or "fault" in pl or "告警" in p or "故障" in p:
            return 0.0 if fault else 1.0
        if not fault:
            if "co2" in pl:
                return 800.0
            if "temp" in pl:
                return 22.0
            if "pressure" in pl or "pa" in pl:
                return 200.0
            return 1.0
        return -10.0 if (i % 2 == 0) else 5000.0

    data: List[Dict[str, Any]] = []
    for i in range(samples):
        ts = start_ts + i * step
        pv: Dict[str, Any] = {}
        for j, p in enumerate(points):
            if len(points) >= 2 and j in (0, 1) and not ("status" in p.lower() or "alarm" in p.lower() or "fault" in p.lower()):
                if fault:
                    pv[p] = 5000.0 if j == 0 else -10.0
                else:
                    pv[p] = 100.0 if j == 0 else 101.0
            else:
                pv[p] = _value_for_point(p, i)
        data.append({"ts": ts, "points": pv})

    return [(_device_id(device_type, component), data)]


class Handler(BaseHTTPRequestHandler):
    def _send_json(self, code: int, obj: Any) -> None:
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/fetch":
            self._send_json(404, {"error": "not found"})
            return
        try:
            req = _read_json_body(self)
            end_ts = float(req.get("endTs", _now_ts()))

            queries: List[Dict[str, Any]] = []
            if isinstance(req.get("query"), dict):
                queries = [req["query"]]
            elif isinstance(req.get("queries"), list):
                queries = [q for q in req["queries"] if isinstance(q, dict)]
            else:
                raise ValueError("must provide query or queries")

            items: List[Dict[str, Any]] = []
            for q in queries:
                pairs = _build_timeseries_for_query(q, end_ts=end_ts)
                for did, data in pairs:
                    items.append(
                        {
                            "deviceId": did,
                            "requestId": str(q.get("requestId", "")).strip(),
                            "deviceType": str(q.get("deviceType", "")).strip(),
                            "component": str(q.get("component", "")).strip(),
                            "points": q.get("points", []),
                            "data": data,
                        }
                    )

            self._send_json(200, {"version": 1, "items": items})
        except Exception as e:  # noqa: BLE001
            self._send_json(400, {"error": str(e), "hint": "check request schema: query/queries", "received": req})

    def log_message(self, format: str, *args: Any) -> None:  # noqa: A002
        return


def main() -> int:
    host = "127.0.0.1"
    port = 18080
    httpd = HTTPServer((host, port), Handler)
    print(f"mock api listening on http://{host}:{port}/fetch")
    httpd.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

