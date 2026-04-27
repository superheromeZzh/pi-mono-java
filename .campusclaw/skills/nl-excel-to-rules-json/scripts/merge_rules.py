from __future__ import annotations

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


_STABLE_ID_RE = re.compile(r".+_[0-9a-f]{8}$")
_POINT_KEY_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_\.]*$")
_PLACEHOLDER_NAMES = {"未命名", "unknown", "n/a", "na", "tbd", "todo", "故障", "异常"}


def _normalize_optional_text_fields(rule: Dict[str, Any]) -> None:
    # 这两个字段非必填，但统一落盘形态：缺失则补空字符串
    for k in ("原因分析", "专家处理建议"):
        if k not in rule or rule.get(k) is None:
            rule[k] = ""
        if not isinstance(rule.get(k), str):
            raise ValueError(f"incoming rule.{k} 若存在则必须是字符串（允许空字符串）")


def _validate_incoming_rule(rule: Dict[str, Any]) -> None:
    """
    合并前的硬门禁：防止“缺信息就默认填”导致脏数据落盘到统一 rules.json。
    - id：必须稳定（以 8 位 hex hash 后缀结尾）
    - name：必须非空且不能是明显占位（例如等于 component）
    - meta.points：必须是可用字段 key（禁止中文展示名）
    """
    rid = str(rule.get("id", "")).strip()
    if not rid:
        raise ValueError("incoming rule.id 不能为空")
    if not _STABLE_ID_RE.fullmatch(rid):
        raise ValueError(f"incoming rule.id 必须为稳定格式（*_a5bcef80），当前：{rid!r}")

    name = str(rule.get("name", "")).strip()
    if not name:
        raise ValueError(f"incoming rule.name 不能为空（rule_id={rid}）")
    if name.lower() in _PLACEHOLDER_NAMES:
        raise ValueError(f"incoming rule.name 疑似占位：{name!r}（rule_id={rid}）")

    meta = rule.get("meta", {}) or {}
    if not isinstance(meta, dict):
        raise ValueError(f"incoming rule.meta 必须是对象（rule_id={rid}）")
    component = str(meta.get("component", "")).strip()
    if component and name == component:
        raise ValueError(
            f"incoming rule.name 疑似占位（与 meta.component 相同：{name!r}）（rule_id={rid}）"
        )
    points = meta.get("points", [])
    if not isinstance(points, list) or not points:
        raise ValueError(f"incoming meta.points 必须是非空数组（rule_id={rid}）")
    for p in points:
        key = str(p).strip()
        if not key:
            raise ValueError(f"incoming meta.points 含空值（rule_id={rid}）")
        if any(ord(ch) > 127 for ch in key):
            raise ValueError(
                f"incoming meta.points 包含中文/非 ASCII：{key!r}（rule_id={rid}）。"
                "请改为真实字段 key（例如 room_co2_ppm）。"
            )
        if not _POINT_KEY_RE.fullmatch(key):
            raise ValueError(
                f"incoming meta.points 不是合法字段 key：{key!r}（rule_id={rid}）。"
                "允许：字母开头，包含字母/数字/_/.（例如 room_temp 或 damper.opening_sp）。"
            )

    _normalize_optional_text_fields(rule)


def _load_doc(path: Path) -> Dict[str, Any]:
    """读取 rules.json；若文件不存在则返回空文档。"""
    if not path.exists():
        return {"version": 1, "rules": []}
    doc = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(doc, dict):
        raise ValueError("rules.json 顶层必须是 JSON 对象")
    if doc.get("version") != 1:
        raise ValueError("rules.json 的 version 必须是 1")
    rules = doc.get("rules")
    if not isinstance(rules, list):
        raise ValueError("rules.json 的 rules 必须是数组")
    return doc


def _index_by_id(rules: List[Any]) -> Dict[str, int]:
    """建立 ruleId -> 下标 的索引（用于 O(1) 级别替换）。"""
    idx: Dict[str, int] = {}
    for i, r in enumerate(rules):
        if isinstance(r, dict) and isinstance(r.get("id"), str):
            rid = r["id"].strip()
            if rid:
                idx[rid] = i
    return idx


def _biz_key(rule: Dict[str, Any]) -> Tuple[str, str, str]:
    """
    业务唯一键（方案 B）：
    - deviceType（meta.deviceType）
    - component（meta.component）
    - name（rule.name）
    任一缺失则返回空键（"", "", ""），不参与去重判断。
    """
    name = str(rule.get("name", "")).strip()
    meta = rule.get("meta", {}) or {}
    if not isinstance(meta, dict):
        return ("", "", "")
    device_type = str(meta.get("deviceType", "")).strip()
    component = str(meta.get("component", "")).strip()
    if not (device_type and component and name):
        return ("", "", "")
    return (device_type, component, name)


def _index_by_biz_key(rules: List[Any]) -> Dict[Tuple[str, str, str], int]:
    """建立 (deviceType, component, name) -> 下标 的索引（用于判断是否已存在）。"""
    idx: Dict[Tuple[str, str, str], int] = {}
    for i, r in enumerate(rules):
        if not isinstance(r, dict):
            continue
        k = _biz_key(r)
        if k != ("", "", ""):
            idx[k] = i
    return idx


def merge_rules(unified_path: Path, incoming_path: Path, *, dry_run: bool = False) -> Tuple[int, int, int]:
    """
    将 incoming_rules.json 合并进 unified_rules.json。

    追加策略（按你的需求）：
    - 若 unified 中已存在相同 id：不覆盖，计为 skipped，并在控制台提示“已存在”
    - 否则：追加到 rules 数组末尾

    返回：(替换条数, 追加条数, 跳过条数)

    dry_run=True 时：仅输出“将追加/将跳过”的判断结果，不写入 unified_path。
    """
    unified = _load_doc(unified_path)
    incoming = _load_doc(incoming_path)

    unified_rules: List[Any] = list(unified.get("rules", []))
    incoming_rules: List[Any] = list(incoming.get("rules", []))

    idx = _index_by_id(unified_rules)
    biz_idx = _index_by_biz_key(unified_rules)

    replaced = 0
    appended = 0
    skipped = 0

    for r in incoming_rules:
        if not isinstance(r, dict):
            skipped += 1
            continue
        _validate_incoming_rule(r)
        rid = str(r.get("id", "")).strip()
        if not rid:
            skipped += 1
            continue

        # 兜底：如果 incoming 自带 id 且 unified 里已存在同 id，认为已存在（跳过）
        if rid in idx:
            skipped += 1
            print(f"已存在，跳过：{rid}")
            continue

        # 方案 B：用业务键 (deviceType, component, name) 判断是否已存在
        k = _biz_key(r)
        if k != ("", "", "") and k in biz_idx:
            skipped += 1
            existing_id = ""
            existing = unified_rules[biz_idx[k]]
            if isinstance(existing, dict):
                existing_id = str(existing.get("id", "")).strip()
            msg = f"已存在，跳过：{k[0]}/{k[1]}/{k[2]}"
            if existing_id:
                msg += f" (existing_id={existing_id})"
            print(msg)
            continue

        if dry_run:
            appended += 1
            print(f"将追加：{rid}")
            continue

        unified_rules.append(r)
        idx[rid] = len(unified_rules) - 1
        if k != ("", "", ""):
            biz_idx[k] = len(unified_rules) - 1
        appended += 1

    if not dry_run:
        out = {"version": 1, "rules": unified_rules}
        unified_path.parent.mkdir(parents=True, exist_ok=True)
        unified_path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    return replaced, appended, skipped


def main(argv: List[str]) -> int:
    _configure_stdio_utf8()
    dry_run = False
    args = list(argv[1:])
    if "--dry-run" in args:
        dry_run = True
        args.remove("--dry-run")

    if len(args) != 2:
        print("用法：python merge_rules.py [--dry-run] <统一 rules.json> <本次生成的 rules.json>")
        return 2

    unified_path = Path(args[0]).expanduser().resolve()
    incoming_path = Path(args[1]).expanduser().resolve()

    replaced, appended, skipped = merge_rules(unified_path, incoming_path, dry_run=dry_run)
    if dry_run:
        print(f"预演完成（未写入）：{unified_path}")
    else:
        print(f"已合并写入：{unified_path}")
    print(f"替换={replaced} 追加={appended} 跳过={skipped}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
