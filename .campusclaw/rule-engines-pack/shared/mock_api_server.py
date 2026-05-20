from __future__ import annotations

import json
import sys
import hashlib
import os
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any, Dict, List, Tuple, Optional

# 复用 01-json-ast 的 JudgeError（本上传包内路径）
_SHARED_DIR = Path(__file__).resolve().parent
_PACK_ROOT = _SHARED_DIR.parent
_AST_DIR = _PACK_ROOT / "01-json-ast"
sys.path.insert(0, str(_AST_DIR))

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

    允许两种形态（单设备 / 多设备）：
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

    约定：
    - fault_rate：0~100（默认 30%，让巡检结果更像真实场景：部分设备有告警，部分无告警）
    - 使用 hash(device_id|request_id) 做确定性分配：同一输入每次结果一致，便于联调复现
    """
    fault_rate = max(0, min(100, int(fault_rate)))
    # 加一点时间扰动：不同巡检时刻会有不同设备“中招”，更像真实巡检波动
    salt = int(float(end_ts) // 3600)  # 以小时为粒度
    key = f"{device_id}|{request_id}|{salt}".encode("utf-8")
    h = hashlib.sha1(key).hexdigest()
    bucket = int(h[:8], 16) % 100
    return bucket < fault_rate


def _build_timeseries_for_query(query: Dict[str, Any], *, end_ts: float) -> List[Tuple[str, List[Dict[str, Any]]]]:
    """
    mock API 的职责：仅按“设备/元器件/点位/有效时间”返回数据。

    说明：
    - mock 并不知道具体规则阈值，因此这里采用两套通用造数策略：
      - 健康数据：尽量落在合理范围、差值较小，降低触发概率
      - 故障数据：给极端值/大差值，提高触发概率
    - 默认仅一部分请求返回故障数据（见 _should_fault），避免“必故障”。
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
    rule_name = str(query.get("ruleName", "")).strip()
    device_id = _device_id(device_type, component)

    # 最高优先级：若存在离线夹具数据，则直接返回（保证演示可复现、无随机波动）
    fx = _load_fixture(request_id=request_id)
    if fx is not None:
        out = []
        for fx_device_id, fx_data in fx:
            out.append((fx_device_id or device_id, fx_data))
        return out

    # 随机故障比例 0~100（仅用于无夹具时的通用造数）。有 mock_fixtures/<requestId>.json 时优先用夹具，与 Drools ruleset 规则 id 同名即可稳定复现。
    fault_rate = max(0, min(100, int(os.environ.get("DEVICE_INSPECTION_MOCK_FAULT_RATE", "10"))))
    fault = _should_fault(device_id=device_id, request_id=request_id, fault_rate=fault_rate, end_ts=end_ts)

    # 强制指定故障名称必触发（便于验证“新增规则 -> 巡检判断”的闭环）
    if rule_name == "CO2传感器读数异常漂移":
        fault = True

        # 该规则常见写法是对 room_co2 做上下限判定并要求高比例命中（例如 30min 内 90%）。
        # 为了确保能触发，把 room_co2 造为持续越界（而不是交替越界/正常导致命中比例不足）。
        if "room_co2" in points:
            step = 60
            samples = int(window_seconds / step) + 1
            samples = max(2, samples)
            start_ts = end_ts - float(window_seconds)

            data: List[Dict[str, Any]] = []
            for i in range(samples):
                ts = start_ts + i * step
                pv: Dict[str, Any] = {}
                for p in points:
                    if p == "room_co2":
                        pv[p] = -10.0  # 始终 < 300，保证 ratio_true≈1
                    else:
                        pv[p] = 1.0
                data.append({"ts": ts, "points": pv})

            return [(device_id, data)]

    # 强制：CO2 传感器“数值呆滞/响应异常”必触发
    # 由于该类规则往往包含趋势/联动逻辑（需要特征点位），mock 在这里直接返回一段“死值 + 阀开度下降 + CO2 不变”的数据。
    if rule_name == "CO2传感器数值呆滞/响应异常":
        fault = True

        step = 300  # 5min
        samples = int(window_seconds / step) + 1
        samples = max(4, samples)
        start_ts = end_ts - float(window_seconds)

        data: List[Dict[str, Any]] = []
        base_damper = 60.0
        for i in range(samples):
            ts = start_ts + i * step
            pv: Dict[str, Any] = {}
            for p in points:
                # 真实值点位
                if p == "room_co2":
                    pv[p] = 800.0  # CO2 死值（不变）
                elif p == "room_co2_30min_ago":
                    pv[p] = 800.0
                elif p == "room_co2_2h_ago":
                    pv[p] = 800.0
                elif p == "fresh_air_damper_opening":
                    pv[p] = base_damper - 2.0 * i  # 阀开度逐步减小
                elif p == "damper_opening":
                    pv[p] = base_damper - 2.0 * i
                elif p == "damper_opening_30min_ago":
                    pv[p] = base_damper - 2.0 * max(0, i - 6)  # 30min≈6个点
                # 特征点位（若规则 DSL 选择用这些字段表达趋势/联动）
                elif p == "co2_change_rate_2h":
                    pv[p] = 0.0  # 2h 变动率 < 1%
                elif p == "co2_delta_10min":
                    pv[p] = 0.0  # 10min 内 CO2 无变化（<=0）
                elif p == "damper_opening_delta_10min":
                    pv[p] = -2.0 if i > 0 else 0.0  # 10min 内阀开度在减小（<0）
                else:
                    pv[p] = 1.0
            data.append({"ts": ts, "points": pv})

        return [(device_id, data)]

    # 针对“CO2 传感器数值呆滞/响应异常”这类新增规则的专用造数：
    # 只根据点位集合识别（mock API 不读取规则内容）。
    # 一旦 rules.json 新增该规则并包含这些点位，这里就会自动返回一段可触发的时间序列数据。
    pts_set = {p for p in points}
    if {
        "room_co2",
        "co2_change_rate_2h",
        "damper_opening_delta_10min",
        "co2_delta_10min",
    }.issubset(pts_set):
        # 1 小时窗口：每 5 分钟一个点；CO2 恒定不变、变动率为 0，
        # 同时制造“阀开度在减小”的信号，CO2 不变/下降（用 delta<=0 表示）
        step = 300
        samples = int(window_seconds / step) + 1
        samples = max(4, samples)
        start_ts = end_ts - float(window_seconds)

        data: List[Dict[str, Any]] = []
        base_damper = 60.0
        for i in range(samples):
            ts = start_ts + i * step
            pv: Dict[str, Any] = {}
            for p in points:
                if p == "room_co2":
                    pv[p] = 800.0
                elif p == "co2_change_rate_2h":
                    pv[p] = 0.0
                elif p == "damper_opening_delta_10min":
                    pv[p] = -2.0 if i > 0 else 0.0
                elif p == "co2_delta_10min":
                    pv[p] = 0.0
                elif p == "fresh_air_damper_opening":
                    pv[p] = base_damper - 2.0 * i
                else:
                    pv[p] = 1.0
            data.append({"ts": ts, "points": pv})

        return [(device_id, data)]

    # 通用造数（健康/故障两套）：
    # - status/状态：健康与故障都置 1（便于通过运行状态 gate，避免因为 status=0 导致整条规则永远不触发）
    # - Alarm/Fault：健康置 1（表示正常），故障置 0（你当前某条规则语义为 0=不正常）
    # - 数值类：健康落在常见范围；故障给极端值或大差值
    def _value_for_point(p: str, i: int) -> float:
        pl = p.lower()
        if "status" in pl or "状态" in p:
            return 1.0
        if "alarm" in pl or "fault" in pl or "告警" in p or "故障" in p:
            return 0.0 if fault else 1.0

        # 健康值：温度/压力/CO2 取相对合理的常见值
        if not fault:
            if "co2" in pl:
                return 800.0
            if "temp" in pl:
                return 22.0
            if "pressure" in pl or "pa" in pl:
                return 200.0
            return 1.0

        # 故障值：交替给极低/极高值
        return -10.0 if (i % 2 == 0) else 5000.0

    data: List[Dict[str, Any]] = []
    for i in range(samples):
        ts = start_ts + i * step
        pv: Dict[str, Any] = {}
        # abs(A-B) 类：故障时制造大差值；健康时让差值很小
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

            # 接口输入：queries（可批量）
            # 每条 query：deviceType / component / points / windowSeconds（必填），endTs 可在顶层提供
            queries: List[Dict[str, Any]] = []
            if isinstance(req.get("query"), dict):
                queries = [req["query"]]
            elif isinstance(req.get("queries"), list):
                queries = [q for q in req["queries"] if isinstance(q, dict)]
            else:
                raise ValueError("must provide query or queries")

            items = []
            for q in queries:
                pairs = _build_timeseries_for_query(q, end_ts=end_ts)
                for device_id, data in pairs:
                    items.append(
                        {
                            "deviceId": device_id,
                            "requestId": str(q.get("requestId", "")).strip(),
                            "deviceType": str(q.get("deviceType", "")).strip(),
                            "component": str(q.get("component", "")).strip(),
                            "points": q.get("points", []),
                            "data": data,
                        }
                    )

            self._send_json(200, {"version": 1, "items": items})
        except Exception as e:  # noqa: BLE001
            # 便于调用方排查：把收到的请求也回传
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

