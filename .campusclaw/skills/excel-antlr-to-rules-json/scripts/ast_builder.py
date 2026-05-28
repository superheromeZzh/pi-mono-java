from __future__ import annotations

import sys
from pathlib import Path
from typing import Any, List, Set

_GENERATED = Path(__file__).resolve().parent.parent / "generated"
if str(_GENERATED) not in sys.path:
    sys.path.insert(0, str(_GENERATED))

from TriggerParser import TriggerParser
from TriggerVisitor import TriggerVisitor


def _normalize_cmp_op(op: str) -> str:
    if op == "=":
        return "=="
    return op


def _fact_name_from_token(text: str) -> str:
    s = text.strip()
    if s.startswith("[") and s.endswith("]"):
        return s[1:-1]
    return s


class TriggerAstBuilder(TriggerVisitor):
    """Walk ANTLR parse tree and build rules.json trigger.expr JSON AST."""

    def visitRoot(self, ctx: TriggerParser.RootContext) -> Any:
        return self.visit(ctx.expr())

    def visitOrExpr(self, ctx: TriggerParser.OrExprContext) -> Any:
        return {
            "op": "or",
            "left": self.visit(ctx.logicalOrExpr()),
            "right": self.visit(ctx.logicalAndExpr()),
        }

    def visitAndExpr(self, ctx: TriggerParser.AndExprContext) -> Any:
        return {
            "op": "and",
            "left": self.visit(ctx.logicalAndExpr()),
            "right": self.visit(ctx.cmpExpr()),
        }

    def visitCompareExpr(self, ctx: TriggerParser.CompareExprContext) -> Any:
        op = _normalize_cmp_op(ctx.op.text)
        return {
            "op": op,
            "left": self.visit(ctx.left),
            "right": self.visit(ctx.right),
        }

    def visitMathBinaryExpr(self, ctx: TriggerParser.MathBinaryExprContext) -> Any:
        return {
            "op": ctx.op.text,
            "left": self.visit(ctx.left),
            "right": self.visit(ctx.right),
        }

    def visitMulBinaryExpr(self, ctx: TriggerParser.MulBinaryExprContext) -> Any:
        return {
            "op": ctx.op.text,
            "left": self.visit(ctx.left),
            "right": self.visit(ctx.right),
        }

    def visitParenExpr(self, ctx: TriggerParser.ParenExprContext) -> Any:
        return self.visit(ctx.expr())

    def visitFuncExpr(self, ctx: TriggerParser.FuncExprContext) -> Any:
        fname = ctx.func().getText()
        args: List[Any] = []
        if ctx.args() is not None:
            args = [self.visit(e) for e in ctx.args().expr()]
        return {"func": fname, "args": args}

    def visitAtomExpr(self, ctx: TriggerParser.AtomExprContext) -> Any:
        return self.visit(ctx.atom())

    def visitAtom(self, ctx: TriggerParser.AtomContext) -> Any:
        if ctx.NUMBER() is not None:
            text = ctx.NUMBER().getText()
            if "." in text:
                return {"const": float(text)}
            return {"const": int(text)}
        return {"fact": _fact_name_from_token(ctx.FACT().getText())}

    def visitNextToAnd(self, ctx: TriggerParser.NextToAndContext) -> Any:
        return self.visit(ctx.logicalAndExpr())

    def visitNextToCmp(self, ctx: TriggerParser.NextToCmpContext) -> Any:
        return self.visit(ctx.cmpExpr())

    def visitNextToMath(self, ctx: TriggerParser.NextToMathContext) -> Any:
        return self.visit(ctx.mathExpr())

    def visitNextToMul(self, ctx: TriggerParser.NextToMulContext) -> Any:
        return self.visit(ctx.mulExpr())

    def visitNextToPrimary(self, ctx: TriggerParser.NextToPrimaryContext) -> Any:
        return self.visit(ctx.primaryExpr())


def collect_facts(expr: Any, out: Set[str] | None = None) -> Set[str]:
    if out is None:
        out = set()
    if not isinstance(expr, dict):
        return out
    if "fact" in expr:
        out.add(str(expr["fact"]))
        return out
    if "func" in expr:
        for a in expr.get("args", []) or []:
            collect_facts(a, out)
        return out
    if "op" in expr:
        if "left" in expr:
            collect_facts(expr["left"], out)
        if "right" in expr:
            collect_facts(expr["right"], out)
        return out
    return out


def semantic_check(expr: Any, allowed_facts: Set[str]) -> None:
    facts = collect_facts(expr)
    unknown = sorted(f for f in facts if f not in allowed_facts)
    if unknown:
        raise ValueError(f"unknown fact(s) not in meta.points: {unknown}")
