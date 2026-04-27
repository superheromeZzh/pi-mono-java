from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path


def _configure_stdio_utf8() -> None:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")  # py3.7+
        except Exception:
            pass


_STABLE_ID_RE = re.compile(r".+_[0-9a-f]{8}$")
_POINT_KEY_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_\.]*$")
_PLACEHOLDER_NAMES = {"未命名", "unknown", "n/a", "na", "tbd", "todo", "故障", "异常"}


def _fail(msg: str) -> int:
    print(f"ERROR：{msg}")
    return 2


def _precheck_candidate(candidate: Path) -> int:
    """
    候选规则的“门禁”检查（只对 _candidate_rules.json 强制）：
    1) id 必须是稳定格式：以 _[0-9a-f]{8} 结尾（来自 stable hash 后缀）
       - 若你希望自定义 id，请在 Excel/NL 中显式给 rule_id（并保持全局唯一）
    2) meta.points 必须是“真实字段 key”，不允许中文/空格等展示名：
       - 允许的形式：room_temp / damper.opening_sp / foo_bar
    """
    try:
        doc = json.loads(candidate.read_text(encoding="utf-8"))
    except Exception as e:
        return _fail(f"候选文件不是合法 JSON：{candidate} ({e})")

    rules = doc.get("rules")
    if not isinstance(rules, list) or not rules:
        return _fail("候选文件 rules 必须是非空数组")

    for i, r in enumerate(rules):
        if not isinstance(r, dict):
            return _fail(f"rules[{i}] 必须是对象")

        rid = str(r.get("id", "")).strip()
        if not rid:
            return _fail(f"rules[{i}].id 不能为空")
        if not _STABLE_ID_RE.fullmatch(rid):
            return _fail(
                "候选规则的 id 必须使用稳定算法生成（以 8 位十六进制 hash 后缀结尾），"
                f"例如 *_a5bcef80。当前 rules[{i}].id={rid!r}。"
                "若你必须指定固定 id，请在 Excel/NL 中填写 rule_id。"
            )

        name = str(r.get("name", "")).strip()
        if not name:
            return _fail(f"rules[{i}].name 不能为空（必须给出明确的故障名称）")

        meta = r.get("meta", {}) or {}
        if not isinstance(meta, dict):
            return _fail(f"rules[{i}].meta 必须是对象")
        component = str(meta.get("component", "")).strip()
        # 避免“没给故障名称却被默认填充”的情况：把明显的占位名拦住
        if component and name == component:
            return _fail(
                f"rules[{i}].name 疑似占位（与 meta.component 相同：{name!r}）。"
                "请提供明确的故障名称（例如“检测温度超限”）。"
            )
        if name.lower() in _PLACEHOLDER_NAMES:
            return _fail(
                f"rules[{i}].name 疑似占位：{name!r}。"
                "请提供明确的故障名称（例如“检测温度超限”）。"
            )

        points = meta.get("points", [])
        if not isinstance(points, list) or not points:
            return _fail(f"rules[{i}].meta.points 必须是非空数组")
        for p in points:
            key = str(p).strip()
            if not key:
                return _fail(f"rules[{i}].meta.points 含空值")
            # 非 ASCII 基本都意味着“展示名”，需要你确认真实字段名
            if any(ord(ch) > 127 for ch in key):
                return _fail(
                    f"rules[{i}].meta.points 包含中文/非 ASCII：{key!r}。"
                    "请将点位替换为真实数据字段 key（例如 room_temp）。"
                )
            if not _POINT_KEY_RE.fullmatch(key):
                return _fail(
                    f"rules[{i}].meta.points 不是合法字段 key：{key!r}。"
                    "允许：字母开头，包含字母/数字/_/.（例如 room_temp 或 damper.opening_sp）。"
                )

    return 0


def main(argv: list[str]) -> int:
    _configure_stdio_utf8()

    if len(argv) != 2:
        print("用法：python verify_candidate_rules.py <candidate_rules.json>")
        return 2

    candidate = Path(argv[1]).expanduser().resolve()
    scripts_dir = Path(__file__).resolve().parent
    validate_py = scripts_dir / "validate_rules_json.py"
    synthetic_py = scripts_dir / "verify_synthetic_rules.py"

    if not candidate.exists():
        print(f"候选文件不存在：{candidate}")
        return 2

    print("=== 候选门禁：稳定 id + 点位 key ===")
    rc0 = _precheck_candidate(candidate)
    if rc0 != 0:
        return rc0

    print()
    print("=== 校验闭环：方案一（结构+自洽）===")
    rc1 = subprocess.call([sys.executable, str(validate_py), str(candidate)])
    if rc1 != 0:
        return rc1

    print()
    print("=== 校验闭环：方案二（合成数据）===")
    rc2 = subprocess.call([sys.executable, str(synthetic_py), str(candidate)])
    if rc2 != 0:
        return rc2

    print()
    print("OK：方案一+方案二均通过")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))

