"""
Debug: show ANTLR token stream, parse tree (syntax tree), and JSON AST.

Usage:
  python dump_parse_tree.py "abs([风阀开度]-[设定值])>5" "风阀开度,设定值"
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

_SCRIPT_DIR = Path(__file__).resolve().parent
_GENERATED = _SCRIPT_DIR.parent / "generated"
if str(_GENERATED) not in sys.path:
    sys.path.insert(0, str(_GENERATED))

from antlr4 import CommonTokenStream, InputStream  # type: ignore
from antlr4.tree.Trees import Trees  # type: ignore

from TriggerLexer import TriggerLexer
from TriggerParser import TriggerParser

from ast_builder import TriggerAstBuilder
from compile_trigger import substitute_point_placeholders
from emit_rule_engine import emit_rule_engine


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("usage: python dump_parse_tree.py '<formula>' 'point1,point2'")
        return 2
    formula = argv[1]
    points = [p.strip() for p in argv[2].split(",") if p.strip()]
    text = substitute_point_placeholders(formula, points)

    lexer = TriggerLexer(InputStream(text))
    tokens = CommonTokenStream(lexer)
    parser = TriggerParser(tokens)
    tree = parser.root()

    print("=== 1) Source (after point_i substitution) ===")
    print(text)
    print()
    print("=== 2) Token stream ===")
    lexer2 = TriggerLexer(InputStream(text))
    for t in lexer2.getAllTokens():
        if t.type == -1:
            continue
        name = lexer2.symbolicNames[t.type] if t.type < len(lexer2.symbolicNames) else str(t.type)
        print(f"  {name!s:16} {t.text!r}")
    print()
    print("=== 3) Parse tree (ANTLR syntax tree, Lisp form) ===")
    print(Trees.toStringTree(tree, None, parser))
    print()
    print("=== 4) JSON AST (internal) ===")
    ast = TriggerAstBuilder().visit(tree)
    print(json.dumps(ast, ensure_ascii=False, indent=2))
    print()
    print("=== 5) rule_engine (rules_re.json trigger) ===")
    print(emit_rule_engine(ast))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
