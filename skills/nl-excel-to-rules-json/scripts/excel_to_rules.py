from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Tuple


def _configure_stdio_utf8() -> None:
    """尽量让 Windows 控制台按 UTF-8 输出，减少中文乱码。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")  # py3.7+
        except Exception:
            pass


def _parse_duration_seconds(s: str) -> int:
    """将时间窗字符串解析为秒（整数）。"""
    s = str(s).strip().lower()
    m = re.fullmatch(r"(\d+)\s*(s|sec|secs|second|seconds)", s)
    if m:
        return int(m.group(1))
    m = re.fullmatch(r"(\d+)\s*(m|min|mins|minute|minutes)", s)
    if m:
        return int(m.group(1)) * 60
    m = re.fullmatch(r"(\d+)\s*(h|hr|hrs|hour|hours)", s)
    if m:
        return int(m.group(1)) * 3600
    # 兼容写法：30min
    m = re.fullmatch(r"(\d+)\s*min", s)
    if m:
        return int(m.group(1)) * 60
    raise ValueError(f"不支持的时间窗格式：{s!r}")


def _parse_ratio(x: Any) -> float:
    """解析占比阈值：支持 0.95 / 95% / 95（>1 视为百分比）。"""
    if x is None:
        raise ValueError("缺少 effective_ratio（占比阈值）")
    if isinstance(x, (int, float)):
        v = float(x)
        return v / 100.0 if v > 1.0 else v
    s = str(x).strip()
    if s.endswith("%"):
        return float(s[:-1].strip()) / 100.0
    v = float(s)
    return v / 100.0 if v > 1.0 else v


# 中文短语 → 英文 token（按长度从长到短替换，避免子串误伤）
_CN_TOKEN_REPLACEMENTS: Tuple[Tuple[str, str], ...] = (
    ("风阀控制误差超限", "damper_control_error_overlimit"),
    ("检测温度超限", "temp_sensor_over_range"),
    ("温度传感器", "temp_sensor"),
    ("CO2传感器", "co2_sensor"),
    ("风阀开度设定值", "damper_opening_sp"),
    ("风阀开度", "damper_opening"),
    ("风阀", "damper"),
    ("室内温度", "room_temp"),
    ("控制误差超限", "control_error_overlimit"),
    ("误差超限", "error_overlimit"),
    ("传感器", "sensor"),
    ("超限", "overlimit"),
    ("温度", "temp"),
)


def _device_type_slug(device_type: str) -> str:
    s = str(device_type).strip().lower()
    s = re.sub(r"[^a-z0-9]+", "_", s)
    s = re.sub(r"_+", "_", s).strip("_")
    return s or "dev"


def _readable_slug_from_cn(text: str) -> str:
    s = str(text).strip()
    if not s:
        return ""
    for zh, en in _CN_TOKEN_REPLACEMENTS:
        if zh in s:
            s = s.replace(zh, f" {en} ")
    # 去掉剩余中文等非 ASCII 标识符字符（已由短语映射覆盖常见场景）
    s = re.sub(r"[^\x00-\x7F]+", " ", s)
    s = s.lower()
    s = re.sub(r"[^a-z0-9]+", "_", s)
    s = re.sub(r"_+", "_", s).strip("_")
    return s


def _stable_rule_id(device_type: str, component: str, fault_name: str) -> str:
    """
    当 Excel 未提供 rule_id 时，生成稳定 id：
    - 可读前缀：deviceType + component + fault_name 的短语映射（尽量可读）
    - 稳定后缀：sha1(deviceType|component|fault_name) 前 8 位（保证同一业务三元组永远一致）
    """
    dt = str(device_type).strip()
    comp = str(component).strip()
    name = str(fault_name).strip()
    key = f"{dt}|{comp}|{name}".encode("utf-8")
    h8 = hashlib.sha1(key).hexdigest()[:8]

    dt_slug = _device_type_slug(dt)
    comp_slug = _readable_slug_from_cn(comp)
    name_slug = _readable_slug_from_cn(name)
    parts = [p for p in (comp_slug, name_slug) if p]
    mid = "_".join(parts) if parts else "rule"
    mid = re.sub(r"_+", "_", mid).strip("_")
    # 控制总长，避免 id 过长；后缀哈希仍保证稳定
    if len(mid) > 56:
        mid = mid[:56].rstrip("_")

    rid = f"{dt_slug}_{mid}_{h8}"
    rid = re.sub(r"_+", "_", rid).strip("_")
    return rid or f"rule_{h8}"


def _compile_trigger_ast(formula: str, point_keys: List[str]) -> Dict[str, Any]:
    """
    将 `trigger_formula` 编译为受限 AST。

    当前支持（刻意保持简单，避免 Excel 任意表达式带来安全风险）：
      abs(point_1 - point_2) > 5
    其中 point_1/point_2 会被替换为真实点位 key。
    """
    s = str(formula).strip()
    if not s:
        raise ValueError("缺少 trigger_formula（触发公式）")

    # 替换 point_i 占位符
    for idx, key in enumerate(point_keys, start=1):
        s = re.sub(rf"\bpoint_{idx}\b", key, s)

    m = re.fullmatch(
        r"abs\(\s*([a-zA-Z0-9_\.]+)\s*-\s*([a-zA-Z0-9_\.]+)\s*\)\s*([<>]=?|==|!=)\s*([0-9]+(\.[0-9]+)?)",
        s,
    )
    if not m:
        raise ValueError(f"不支持的 trigger_formula（当前仅支持 abs(A-B) OP N）：{formula!r}")

    a, b, op, num = m.group(1), m.group(2), m.group(3), float(m.group(4))
    return {
        "op": op,
        "left": {"func": "abs", "args": [{"op": "-", "left": {"fact": a}, "right": {"fact": b}}]},
        "right": {"const": num},
    }


def _read_xlsx_rows(path: Path) -> List[Dict[str, Any]]:
    """读取 xlsx 的第一个工作表为 dict 列表（首行为表头）。"""
    try:
        import openpyxl  # type: ignore
    except Exception as e:  # pragma: no cover
        raise RuntimeError("缺少依赖 openpyxl，请先安装：pip install openpyxl") from e

    wb = openpyxl.load_workbook(path, data_only=True)
    ws = wb.active
    rows = list(ws.iter_rows(values_only=True))
    if not rows:
        return []

    headers = [str(h).strip() if h is not None else "" for h in rows[0]]
    out: List[Dict[str, Any]] = []
    for r in rows[1:]:
        if all(v is None or str(v).strip() == "" for v in r):
            continue
        obj: Dict[str, Any] = {}
        for h, v in zip(headers, r):
            if h:
                obj[h] = v
        out.append(obj)
    return out


def convert_xlsx_to_rules(xlsx_path: Path) -> Dict[str, Any]:
    """把 xlsx 转成 {version, rules} 文档。"""
    rows = _read_xlsx_rows(xlsx_path)
    rules: List[Dict[str, Any]] = []

    for row in rows:
        device_type = str(row.get("device_type", "")).strip()
        # 注意：DSL 不写 deviceId；设备实例由上报数据的 device_id 决定
        component = str(row.get("component", "")).strip()
        name = str(row.get("fault_name", "")).strip()
        reason_analysis = str(row.get("reason_analysis", "") or "").strip()
        expert_advice = str(row.get("expert_advice", "") or "").strip()

        if not (device_type and component and name):
            raise ValueError(f"行数据缺少必填列 device_type/component/fault_name：{row}")

        point_keys: List[str] = []
        for i in range(1, 21):
            k = f"point_{i}"
            if k in row and row[k] is not None and str(row[k]).strip():
                point_keys.append(str(row[k]).strip())
        if len(point_keys) < 2:
            raise ValueError(f"至少需要 point_1 与 point_2，当前为：{point_keys}；行：{row}")

        rule_id = str(row.get("rule_id", "")).strip() or _stable_rule_id(device_type, component, name)
        window_seconds = _parse_duration_seconds(str(row.get("window", "")).strip())
        threshold = _parse_ratio(row.get("effective_ratio"))
        min_samples_raw = row.get("min_samples", None)
        trigger_formula = str(row.get("trigger_formula", "")).strip()

        effective: Dict[str, Any] = {
            "metric": "ratio_true",
            "source": "trigger",
            "threshold": float(threshold),
        }
        if min_samples_raw is not None and str(min_samples_raw).strip() != "":
            effective["minSamples"] = int(min_samples_raw)

        rule = {
            "id": rule_id,
            "name": name,
            "原因分析": reason_analysis,
            "专家处理建议": expert_advice,
            "naturalLanguage": None,
            "meta": {
                "deviceType": device_type,
                "component": component,
                "points": point_keys,
            },
            "window": {"type": "rolling", "durationSeconds": int(window_seconds)},
            "trigger": {"expr": _compile_trigger_ast(trigger_formula, point_keys)},
            "effective": effective,
        }
        rules.append(rule)

    return {"version": 1, "rules": rules}


def main(argv: List[str]) -> int:
    _configure_stdio_utf8()
    if len(argv) != 3:
        print("用法：python excel_to_rules.py <输入.xlsx> <输出 rules.json>")
        return 2

    xlsx_path = Path(argv[1]).expanduser().resolve()
    out_path = Path(argv[2]).expanduser().resolve()
    doc = convert_xlsx_to_rules(xlsx_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"已写入：{out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
