# Generated from E:/pi-mono-java/.campusclaw/skills/excel-antlr-to-rules-json/grammar/Trigger.g4 by ANTLR 4.13.2
from antlr4 import *
if "." in __name__:
    from .TriggerParser import TriggerParser
else:
    from TriggerParser import TriggerParser

# This class defines a complete listener for a parse tree produced by TriggerParser.
class TriggerListener(ParseTreeListener):

    # Enter a parse tree produced by TriggerParser#root.
    def enterRoot(self, ctx:TriggerParser.RootContext):
        pass

    # Exit a parse tree produced by TriggerParser#root.
    def exitRoot(self, ctx:TriggerParser.RootContext):
        pass


    # Enter a parse tree produced by TriggerParser#expr.
    def enterExpr(self, ctx:TriggerParser.ExprContext):
        pass

    # Exit a parse tree produced by TriggerParser#expr.
    def exitExpr(self, ctx:TriggerParser.ExprContext):
        pass


    # Enter a parse tree produced by TriggerParser#nextToAnd.
    def enterNextToAnd(self, ctx:TriggerParser.NextToAndContext):
        pass

    # Exit a parse tree produced by TriggerParser#nextToAnd.
    def exitNextToAnd(self, ctx:TriggerParser.NextToAndContext):
        pass


    # Enter a parse tree produced by TriggerParser#orExpr.
    def enterOrExpr(self, ctx:TriggerParser.OrExprContext):
        pass

    # Exit a parse tree produced by TriggerParser#orExpr.
    def exitOrExpr(self, ctx:TriggerParser.OrExprContext):
        pass


    # Enter a parse tree produced by TriggerParser#nextToCmp.
    def enterNextToCmp(self, ctx:TriggerParser.NextToCmpContext):
        pass

    # Exit a parse tree produced by TriggerParser#nextToCmp.
    def exitNextToCmp(self, ctx:TriggerParser.NextToCmpContext):
        pass


    # Enter a parse tree produced by TriggerParser#andExpr.
    def enterAndExpr(self, ctx:TriggerParser.AndExprContext):
        pass

    # Exit a parse tree produced by TriggerParser#andExpr.
    def exitAndExpr(self, ctx:TriggerParser.AndExprContext):
        pass


    # Enter a parse tree produced by TriggerParser#compareExpr.
    def enterCompareExpr(self, ctx:TriggerParser.CompareExprContext):
        pass

    # Exit a parse tree produced by TriggerParser#compareExpr.
    def exitCompareExpr(self, ctx:TriggerParser.CompareExprContext):
        pass


    # Enter a parse tree produced by TriggerParser#nextToMath.
    def enterNextToMath(self, ctx:TriggerParser.NextToMathContext):
        pass

    # Exit a parse tree produced by TriggerParser#nextToMath.
    def exitNextToMath(self, ctx:TriggerParser.NextToMathContext):
        pass


    # Enter a parse tree produced by TriggerParser#mathBinaryExpr.
    def enterMathBinaryExpr(self, ctx:TriggerParser.MathBinaryExprContext):
        pass

    # Exit a parse tree produced by TriggerParser#mathBinaryExpr.
    def exitMathBinaryExpr(self, ctx:TriggerParser.MathBinaryExprContext):
        pass


    # Enter a parse tree produced by TriggerParser#nextToMul.
    def enterNextToMul(self, ctx:TriggerParser.NextToMulContext):
        pass

    # Exit a parse tree produced by TriggerParser#nextToMul.
    def exitNextToMul(self, ctx:TriggerParser.NextToMulContext):
        pass


    # Enter a parse tree produced by TriggerParser#mulBinaryExpr.
    def enterMulBinaryExpr(self, ctx:TriggerParser.MulBinaryExprContext):
        pass

    # Exit a parse tree produced by TriggerParser#mulBinaryExpr.
    def exitMulBinaryExpr(self, ctx:TriggerParser.MulBinaryExprContext):
        pass


    # Enter a parse tree produced by TriggerParser#nextToPrimary.
    def enterNextToPrimary(self, ctx:TriggerParser.NextToPrimaryContext):
        pass

    # Exit a parse tree produced by TriggerParser#nextToPrimary.
    def exitNextToPrimary(self, ctx:TriggerParser.NextToPrimaryContext):
        pass


    # Enter a parse tree produced by TriggerParser#parenExpr.
    def enterParenExpr(self, ctx:TriggerParser.ParenExprContext):
        pass

    # Exit a parse tree produced by TriggerParser#parenExpr.
    def exitParenExpr(self, ctx:TriggerParser.ParenExprContext):
        pass


    # Enter a parse tree produced by TriggerParser#funcExpr.
    def enterFuncExpr(self, ctx:TriggerParser.FuncExprContext):
        pass

    # Exit a parse tree produced by TriggerParser#funcExpr.
    def exitFuncExpr(self, ctx:TriggerParser.FuncExprContext):
        pass


    # Enter a parse tree produced by TriggerParser#atomExpr.
    def enterAtomExpr(self, ctx:TriggerParser.AtomExprContext):
        pass

    # Exit a parse tree produced by TriggerParser#atomExpr.
    def exitAtomExpr(self, ctx:TriggerParser.AtomExprContext):
        pass


    # Enter a parse tree produced by TriggerParser#atom.
    def enterAtom(self, ctx:TriggerParser.AtomContext):
        pass

    # Exit a parse tree produced by TriggerParser#atom.
    def exitAtom(self, ctx:TriggerParser.AtomContext):
        pass


    # Enter a parse tree produced by TriggerParser#args.
    def enterArgs(self, ctx:TriggerParser.ArgsContext):
        pass

    # Exit a parse tree produced by TriggerParser#args.
    def exitArgs(self, ctx:TriggerParser.ArgsContext):
        pass


    # Enter a parse tree produced by TriggerParser#func.
    def enterFunc(self, ctx:TriggerParser.FuncContext):
        pass

    # Exit a parse tree produced by TriggerParser#func.
    def exitFunc(self, ctx:TriggerParser.FuncContext):
        pass



del TriggerParser