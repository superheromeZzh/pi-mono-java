# Generated from E:/pi-mono-java/.campusclaw/skills/excel-antlr-to-rules-json/grammar/Trigger.g4 by ANTLR 4.13.2
from antlr4 import *
if "." in __name__:
    from .TriggerParser import TriggerParser
else:
    from TriggerParser import TriggerParser

# This class defines a complete generic visitor for a parse tree produced by TriggerParser.

class TriggerVisitor(ParseTreeVisitor):

    # Visit a parse tree produced by TriggerParser#root.
    def visitRoot(self, ctx:TriggerParser.RootContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#expr.
    def visitExpr(self, ctx:TriggerParser.ExprContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#nextToAnd.
    def visitNextToAnd(self, ctx:TriggerParser.NextToAndContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#orExpr.
    def visitOrExpr(self, ctx:TriggerParser.OrExprContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#nextToCmp.
    def visitNextToCmp(self, ctx:TriggerParser.NextToCmpContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#andExpr.
    def visitAndExpr(self, ctx:TriggerParser.AndExprContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#compareExpr.
    def visitCompareExpr(self, ctx:TriggerParser.CompareExprContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#nextToMath.
    def visitNextToMath(self, ctx:TriggerParser.NextToMathContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#mathBinaryExpr.
    def visitMathBinaryExpr(self, ctx:TriggerParser.MathBinaryExprContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#nextToMul.
    def visitNextToMul(self, ctx:TriggerParser.NextToMulContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#mulBinaryExpr.
    def visitMulBinaryExpr(self, ctx:TriggerParser.MulBinaryExprContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#nextToPrimary.
    def visitNextToPrimary(self, ctx:TriggerParser.NextToPrimaryContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#parenExpr.
    def visitParenExpr(self, ctx:TriggerParser.ParenExprContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#funcExpr.
    def visitFuncExpr(self, ctx:TriggerParser.FuncExprContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#atomExpr.
    def visitAtomExpr(self, ctx:TriggerParser.AtomExprContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#atom.
    def visitAtom(self, ctx:TriggerParser.AtomContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#args.
    def visitArgs(self, ctx:TriggerParser.ArgsContext):
        return self.visitChildren(ctx)


    # Visit a parse tree produced by TriggerParser#func.
    def visitFunc(self, ctx:TriggerParser.FuncContext):
        return self.visitChildren(ctx)



del TriggerParser