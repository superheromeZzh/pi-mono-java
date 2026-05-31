from __future__ import annotations

import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Set, Tuple

_SCRIPT_DIR = Path(__file__).resolve().parent
_GENERATED = _SCRIPT_DIR.parent / "generated"
if str(_GENERATED) not in sys.path:
    sys.path.insert(0, str(_GENERATED))

from antlr4 import CommonTokenStream, InputStream  # type: ignore
from antlr4.error.ErrorListener import ErrorListener  # type: ignore

from TriggerLexer import TriggerLexer
from TriggerParser import TriggerParser

from ast_builder import TriggerAstBuilder, semantic_check
from emit_rule_engine import emit_rule_engine


class _CollectErrors(ErrorListener):
    def __init__(self) -> None:
        self.messages: List[str] = []

    def syntaxError(self, recognizer, offendingSymbol, line, column, msg, e) -> None:  # noqa: ANN001
        self.messages.append(f"line {line}:{column} {msg}")


def normalize_logic_operators(formula: str) -> str:
    """Excel often uses single | / & ; grammar expects || and &&."""
    s = formula
    s = s.replace("||", "\x00OR\x00")
    s = s.replace("&&", "\x00AND\x00")
    s = re.sub(r"\|(?!\|)", "||", s)
    s = re.sub(r"(?<![&])&(?![&])", "&&", s)
    s = s.replace("\x00OR\x00", "||")
    s = s.replace("\x00AND\x00", "&&")
    return s


def substitute_point_placeholders(formula: str, point_keys: List[str]) -> str:
    s = str(formula).strip()
    for idx, key in enumerate(point_keys, start=1):
        placeholder = f"point_{idx}"
        if re.match(r"^[A-Za-z_][A-Za-z0-9_]*$", key):
            replacement = key
        else:
            replacement = f"[{key}]"
        s = re.sub(rf"\b{placeholder}\b", replacement, s)
    return s


def compile_trigger_formula(
    formula: str,
    point_keys: List[str],
) -> Tuple[Dict[str, Any] | None, str | None, List[str]]:
    """
    Parse trigger_formula with ANTLR (grammar/Trigger.g4).
    Returns (json_ast, rule_engine_text, errors).
    """
    errors: List[str] = []
    raw = str(formula).strip()
    if not raw:
        return None, None, ["empty trigger_formula"]

    substituted = normalize_logic_operators(substitute_point_placeholders(raw, point_keys))
    allowed: Set[str] = set(point_keys)

    lexer = TriggerLexer(InputStream(substituted))
    listener = _CollectErrors()
    lexer.removeErrorListeners()
    lexer.addErrorListener(listener)

    tokens = CommonTokenStream(lexer)
    parser = TriggerParser(tokens)
    parser.removeErrorListeners()
    parser.addErrorListener(listener)

    tree = parser.root()
    if listener.messages:
        return None, None, listener.messages

    try:
        ast = TriggerAstBuilder().visit(tree)
        semantic_check(ast, allowed)
        re_text = emit_rule_engine(ast)
    except Exception as e:  # noqa: BLE001
        return None, None, [str(e)]

    return ast, re_text, []


def main(argv: List[str]) -> int:
    if len(argv) != 3:
        print("usage: python compile_trigger.py '<formula>' 'point1,point2'")
        return 2
    formula = argv[1]
    points = [p.strip() for p in argv[2].split(",") if p.strip()]
    ast, re_text, errs = compile_trigger_formula(formula, points)
    if errs:
        for e in errs:
            print(f"ERROR: {e}")
        return 2
    import json

    print("rule_engine:", re_text)
    print("ast:", json.dumps(ast, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
