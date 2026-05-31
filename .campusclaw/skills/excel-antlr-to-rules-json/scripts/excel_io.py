from __future__ import annotations

import hashlib
import re
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

# Standard Excel column names (one rule per row)
STANDARD_COLUMNS = (
    "rule_id",
    "device_type",
    "component",
    "fault_name",
    "point_1",
    "point_2",
    "point_3",
    "point_4",
    "point_5",
    "trigger_formula",
    "window",
    "effective_ratio",
    "min_samples",
    "reason_analysis",
    "expert_advice",
)

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


def parse_duration_seconds(s: str) -> int:
    """Parse window string to seconds."""
    text = str(s).strip().lower()
    m = re.fullmatch(r"(\d+)\s*(s|sec|secs|second|seconds)", text)
    if m:
        return int(m.group(1))
    m = re.fullmatch(r"(\d+)\s*(m|min|mins|minute|minutes)", text)
    if m:
        return int(m.group(1)) * 60
    m = re.fullmatch(r"(\d+)\s*(h|hr|hrs|hour|hours)", text)
    if m:
        return int(m.group(1)) * 3600
    m = re.fullmatch(r"(\d+)\s*min", text)
    if m:
        return int(m.group(1)) * 60
    raise ValueError(f"unsupported window format: {text!r}")


def parse_ratio(x: Any) -> float:
    """Parse effective_ratio: 0.95 / 95% / 95."""
    if x is None:
        raise ValueError("missing effective_ratio")
    if isinstance(x, (int, float)):
        v = float(x)
        return v / 100.0 if v > 1.0 else v
    s = str(x).strip()
    if s.endswith("%"):
        return float(s[:-1].strip()) / 100.0
    v = float(s)
    return v / 100.0 if v > 1.0 else v


def device_type_slug(device_type: str) -> str:
    s = str(device_type).strip().lower()
    s = re.sub(r"[^a-z0-9]+", "_", s)
    s = re.sub(r"_+", "_", s).strip("_")
    return s or "dev"


def readable_slug_from_cn(text: str) -> str:
    s = str(text).strip()
    if not s:
        return ""
    for zh, en in _CN_TOKEN_REPLACEMENTS:
        if zh in s:
            s = s.replace(zh, f" {en} ")
    s = re.sub(r"[^\x00-\x7F]+", " ", s)
    s = s.lower()
    s = re.sub(r"[^a-z0-9]+", "_", s)
    s = re.sub(r"_+", "_", s).strip("_")
    return s


def stable_rule_id(device_type: str, component: str, fault_name: str) -> str:
    dt = str(device_type).strip()
    comp = str(component).strip()
    name = str(fault_name).strip()
    key = f"{dt}|{comp}|{name}".encode("utf-8")
    h8 = hashlib.sha1(key).hexdigest()[:8]
    dt_slug = device_type_slug(dt)
    comp_slug = readable_slug_from_cn(comp)
    name_slug = readable_slug_from_cn(name)
    parts = [p for p in (comp_slug, name_slug) if p]
    mid = "_".join(parts) if parts else "rule"
    mid = re.sub(r"_+", "_", mid).strip("_")
    if len(mid) > 56:
        mid = mid[:56].rstrip("_")
    rid = f"{dt_slug}_{mid}_{h8}"
    rid = re.sub(r"_+", "_", rid).strip("_")
    return rid or f"rule_{h8}"


def _parse_worksheet_rows(ws: Any) -> Tuple[List[str], List[Dict[str, Any]]]:
    """Parse one worksheet: headers + non-empty data rows. Excel row numbers are 1-based."""
    rows = list(ws.iter_rows(values_only=True))
    if not rows:
        return [], []

    headers = [str(h).strip() if h is not None else "" for h in rows[0]]
    out: List[Dict[str, Any]] = []
    for excel_row_idx, r in enumerate(rows[1:], start=2):
        if all(v is None or str(v).strip() == "" for v in r):
            continue
        obj: Dict[str, Any] = {"__excel_row__": excel_row_idx}
        for h, v in zip(headers, r):
            if h:
                obj[h] = v
        out.append(obj)
    return headers, out


def read_all_xlsx_sheets(path: Path) -> List[Tuple[str, List[str], List[Dict[str, Any]]]]:
    """
    Read every worksheet that has at least one data row.

    Returns a list of (sheet_name, headers, row_dicts) in workbook order.
    Skips sheets with no data rows (header-only or blank).
    """
    try:
        import openpyxl  # type: ignore
    except Exception as e:  # pragma: no cover
        raise RuntimeError("missing openpyxl; install: pip install openpyxl") from e

    wb = openpyxl.load_workbook(path, data_only=True)
    sheets_out: List[Tuple[str, List[str], List[Dict[str, Any]]]] = []
    for ws in wb.worksheets:
        headers, data_rows = _parse_worksheet_rows(ws)
        if not data_rows:
            continue
        sheets_out.append((ws.title, headers, data_rows))
    return sheets_out


def read_xlsx_rows(path: Path) -> Tuple[str, List[str], List[Dict[str, Any]]]:
    """Read all sheets and flatten into one list (each row has __excel_sheet__)."""
    sheets = read_all_xlsx_sheets(path)
    if not sheets:
        return "", [], []
    all_rows: List[Dict[str, Any]] = []
    primary_headers: List[str] = []
    sheet_names: List[str] = []
    for sheet_name, headers, data_rows in sheets:
        sheet_names.append(sheet_name)
        if not primary_headers:
            primary_headers = headers
        for row in data_rows:
            row["__excel_sheet__"] = sheet_name
            all_rows.append(row)
    return ",".join(sheet_names), primary_headers, all_rows


def parse_effective_data(raw: Any) -> Tuple[int, float]:
    """
    Parse combined effective-data cells such as:
      - 无需设置
      - 30min内，90%
      - 60min内，100%
      - 1小时内，100%
      - 15分钟内，90%
    Returns (durationSeconds, ratio_threshold).
    """
    s = str(raw).strip()
    if not s or s in ("无需设置", "无", "N/A", "-", "—"):
        return 60, 1.0

    minutes: int | None = None
    m_min = re.search(r"(\d+)\s*min", s, flags=re.IGNORECASE)
    if m_min:
        minutes = int(m_min.group(1))
    m_min_cn = re.search(r"(\d+)\s*分(?:钟)?(?:内)?", s)
    if m_min_cn:
        minutes = int(m_min_cn.group(1))
    m_hour = re.search(r"(\d+)\s*小时", s)
    if m_hour:
        minutes = int(m_hour.group(1)) * 60

    pct_m = re.search(r"(\d+(?:\.\d+)?)\s*%", s)
    threshold = float(pct_m.group(1)) / 100.0 if pct_m else 1.0

    if minutes is None:
        raise ValueError(f"cannot parse effective data column: {s!r}")
    return minutes * 60, threshold


def collect_point_keys(row: Dict[str, Any]) -> List[str]:
    keys: List[str] = []
    for i in range(1, 21):
        col = f"point_{i}"
        if col in row and row[col] is not None and str(row[col]).strip():
            keys.append(str(row[col]).strip())
    for col in ("point_tags", "点位", "point"):
        raw = row.get(col)
        if raw is None or str(raw).strip() == "":
            continue
        for part in str(raw).replace("，", ",").split(","):
            p = part.strip()
            if p and p not in keys:
                keys.append(p)
    return keys


def normalize_header_map(headers: List[str]) -> Dict[str, str]:
    """Map actual header -> standard column name (best effort)."""
    aliases: Dict[str, str] = {
        "设备": "device_type",
        "设备类型": "device_type",
        "元器件": "component",
        "故障名称": "fault_name",
        "触发公式": "trigger_formula",
        "触发条件": "trigger_formula",
        "点位": "point_tags",
        "时间窗": "window",
        "有效窗口": "window",
        "有效数据": "effective_data",
        "占比阈值": "effective_ratio",
        "判定占比": "effective_ratio",
        "原因分析": "reason_analysis",
        "专家处理建议": "expert_advice",
        "规则id": "rule_id",
    }
    mapping: Dict[str, str] = {}
    for h in headers:
        if not h:
            continue
        key = h.strip()
        low = key.lower().replace(" ", "_")
        if low in STANDARD_COLUMNS:
            mapping[key] = low
        elif key in aliases:
            mapping[key] = aliases[key]
        else:
            mapping[key] = low
    return mapping


def row_with_standard_keys(row: Dict[str, Any], header_map: Dict[str, str]) -> Dict[str, Any]:
    out: Dict[str, Any] = {"__excel_row__": row.get("__excel_row__")}
    for src, val in row.items():
        if src == "__excel_row__":
            continue
        std = header_map.get(src, src)
        out[std] = val
    return out
