# Generated from E:/pi-mono-java/.campusclaw/skills/excel-antlr-to-rules-json/grammar/Trigger.g4 by ANTLR 4.13.2
# encoding: utf-8
from antlr4 import *
from io import StringIO
import sys
if sys.version_info[1] > 5:
	from typing import TextIO
else:
	from typing.io import TextIO

def serializedATN():
    return [
        4,1,24,103,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,2,6,7,
        6,2,7,7,7,2,8,7,8,2,9,7,9,2,10,7,10,1,0,1,0,1,0,1,1,1,1,1,2,1,2,
        1,2,1,2,1,2,1,2,5,2,34,8,2,10,2,12,2,37,9,2,1,3,1,3,1,3,1,3,1,3,
        1,3,5,3,45,8,3,10,3,12,3,48,9,3,1,4,1,4,1,4,1,4,1,4,3,4,55,8,4,1,
        5,1,5,1,5,1,5,1,5,1,5,5,5,63,8,5,10,5,12,5,66,9,5,1,6,1,6,1,6,1,
        6,1,6,1,6,5,6,74,8,6,10,6,12,6,77,9,6,1,7,1,7,1,7,1,7,1,7,1,7,1,
        7,1,7,1,7,1,7,3,7,89,8,7,1,8,1,8,1,9,1,9,1,9,5,9,96,8,9,10,9,12,
        9,99,9,9,1,10,1,10,1,10,0,4,4,6,10,12,11,0,2,4,6,8,10,12,14,16,18,
        20,0,5,1,0,3,9,1,0,10,11,1,0,12,13,1,0,22,23,1,0,17,21,99,0,22,1,
        0,0,0,2,25,1,0,0,0,4,27,1,0,0,0,6,38,1,0,0,0,8,54,1,0,0,0,10,56,
        1,0,0,0,12,67,1,0,0,0,14,88,1,0,0,0,16,90,1,0,0,0,18,92,1,0,0,0,
        20,100,1,0,0,0,22,23,3,2,1,0,23,24,5,0,0,1,24,1,1,0,0,0,25,26,3,
        4,2,0,26,3,1,0,0,0,27,28,6,2,-1,0,28,29,3,6,3,0,29,35,1,0,0,0,30,
        31,10,2,0,0,31,32,5,1,0,0,32,34,3,6,3,0,33,30,1,0,0,0,34,37,1,0,
        0,0,35,33,1,0,0,0,35,36,1,0,0,0,36,5,1,0,0,0,37,35,1,0,0,0,38,39,
        6,3,-1,0,39,40,3,8,4,0,40,46,1,0,0,0,41,42,10,2,0,0,42,43,5,2,0,
        0,43,45,3,8,4,0,44,41,1,0,0,0,45,48,1,0,0,0,46,44,1,0,0,0,46,47,
        1,0,0,0,47,7,1,0,0,0,48,46,1,0,0,0,49,50,3,10,5,0,50,51,7,0,0,0,
        51,52,3,10,5,0,52,55,1,0,0,0,53,55,3,10,5,0,54,49,1,0,0,0,54,53,
        1,0,0,0,55,9,1,0,0,0,56,57,6,5,-1,0,57,58,3,12,6,0,58,64,1,0,0,0,
        59,60,10,2,0,0,60,61,7,1,0,0,61,63,3,12,6,0,62,59,1,0,0,0,63,66,
        1,0,0,0,64,62,1,0,0,0,64,65,1,0,0,0,65,11,1,0,0,0,66,64,1,0,0,0,
        67,68,6,6,-1,0,68,69,3,14,7,0,69,75,1,0,0,0,70,71,10,2,0,0,71,72,
        7,2,0,0,72,74,3,14,7,0,73,70,1,0,0,0,74,77,1,0,0,0,75,73,1,0,0,0,
        75,76,1,0,0,0,76,13,1,0,0,0,77,75,1,0,0,0,78,79,5,14,0,0,79,80,3,
        2,1,0,80,81,5,15,0,0,81,89,1,0,0,0,82,83,3,20,10,0,83,84,5,14,0,
        0,84,85,3,18,9,0,85,86,5,15,0,0,86,89,1,0,0,0,87,89,3,16,8,0,88,
        78,1,0,0,0,88,82,1,0,0,0,88,87,1,0,0,0,89,15,1,0,0,0,90,91,7,3,0,
        0,91,17,1,0,0,0,92,97,3,2,1,0,93,94,5,16,0,0,94,96,3,2,1,0,95,93,
        1,0,0,0,96,99,1,0,0,0,97,95,1,0,0,0,97,98,1,0,0,0,98,19,1,0,0,0,
        99,97,1,0,0,0,100,101,7,4,0,0,101,21,1,0,0,0,7,35,46,54,64,75,88,
        97
    ]

class TriggerParser ( Parser ):

    grammarFileName = "Trigger.g4"

    atn = ATNDeserializer().deserialize(serializedATN())

    decisionsToDFA = [ DFA(ds, i) for i, ds in enumerate(atn.decisionToState) ]

    sharedContextCache = PredictionContextCache()

    literalNames = [ "<INVALID>", "'||'", "'&&'", "'=='", "'>'", "'<'", 
                     "'>='", "'<='", "'!='", "'='", "'+'", "'-'", "'*'", 
                     "'/'", "'('", "')'", "','", "'abs'", "'min'", "'max'", 
                     "'avg'", "'prev'" ]

    symbolicNames = [ "<INVALID>", "<INVALID>", "<INVALID>", "<INVALID>", 
                      "<INVALID>", "<INVALID>", "<INVALID>", "<INVALID>", 
                      "<INVALID>", "<INVALID>", "<INVALID>", "<INVALID>", 
                      "<INVALID>", "<INVALID>", "<INVALID>", "<INVALID>", 
                      "<INVALID>", "<INVALID>", "<INVALID>", "<INVALID>", 
                      "<INVALID>", "<INVALID>", "FACT", "NUMBER", "WS" ]

    RULE_root = 0
    RULE_expr = 1
    RULE_logicalOrExpr = 2
    RULE_logicalAndExpr = 3
    RULE_cmpExpr = 4
    RULE_mathExpr = 5
    RULE_mulExpr = 6
    RULE_primaryExpr = 7
    RULE_atom = 8
    RULE_args = 9
    RULE_func = 10

    ruleNames =  [ "root", "expr", "logicalOrExpr", "logicalAndExpr", "cmpExpr", 
                   "mathExpr", "mulExpr", "primaryExpr", "atom", "args", 
                   "func" ]

    EOF = Token.EOF
    T__0=1
    T__1=2
    T__2=3
    T__3=4
    T__4=5
    T__5=6
    T__6=7
    T__7=8
    T__8=9
    T__9=10
    T__10=11
    T__11=12
    T__12=13
    T__13=14
    T__14=15
    T__15=16
    T__16=17
    T__17=18
    T__18=19
    T__19=20
    T__20=21
    FACT=22
    NUMBER=23
    WS=24

    def __init__(self, input:TokenStream, output:TextIO = sys.stdout):
        super().__init__(input, output)
        self.checkVersion("4.13.2")
        self._interp = ParserATNSimulator(self, self.atn, self.decisionsToDFA, self.sharedContextCache)
        self._predicates = None




    class RootContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser

        def expr(self):
            return self.getTypedRuleContext(TriggerParser.ExprContext,0)


        def EOF(self):
            return self.getToken(TriggerParser.EOF, 0)

        def getRuleIndex(self):
            return TriggerParser.RULE_root

        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterRoot" ):
                listener.enterRoot(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitRoot" ):
                listener.exitRoot(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitRoot" ):
                return visitor.visitRoot(self)
            else:
                return visitor.visitChildren(self)




    def root(self):

        localctx = TriggerParser.RootContext(self, self._ctx, self.state)
        self.enterRule(localctx, 0, self.RULE_root)
        try:
            self.enterOuterAlt(localctx, 1)
            self.state = 22
            self.expr()
            self.state = 23
            self.match(TriggerParser.EOF)
        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.exitRule()
        return localctx


    class ExprContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser

        def logicalOrExpr(self):
            return self.getTypedRuleContext(TriggerParser.LogicalOrExprContext,0)


        def getRuleIndex(self):
            return TriggerParser.RULE_expr

        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterExpr" ):
                listener.enterExpr(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitExpr" ):
                listener.exitExpr(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitExpr" ):
                return visitor.visitExpr(self)
            else:
                return visitor.visitChildren(self)




    def expr(self):

        localctx = TriggerParser.ExprContext(self, self._ctx, self.state)
        self.enterRule(localctx, 2, self.RULE_expr)
        try:
            self.enterOuterAlt(localctx, 1)
            self.state = 25
            self.logicalOrExpr(0)
        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.exitRule()
        return localctx


    class LogicalOrExprContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser


        def getRuleIndex(self):
            return TriggerParser.RULE_logicalOrExpr

     
        def copyFrom(self, ctx:ParserRuleContext):
            super().copyFrom(ctx)


    class NextToAndContext(LogicalOrExprContext):

        def __init__(self, parser, ctx:ParserRuleContext): # actually a TriggerParser.LogicalOrExprContext
            super().__init__(parser)
            self.copyFrom(ctx)

        def logicalAndExpr(self):
            return self.getTypedRuleContext(TriggerParser.LogicalAndExprContext,0)


        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterNextToAnd" ):
                listener.enterNextToAnd(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitNextToAnd" ):
                listener.exitNextToAnd(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitNextToAnd" ):
                return visitor.visitNextToAnd(self)
            else:
                return visitor.visitChildren(self)


    class OrExprContext(LogicalOrExprContext):

        def __init__(self, parser, ctx:ParserRuleContext): # actually a TriggerParser.LogicalOrExprContext
            super().__init__(parser)
            self.copyFrom(ctx)

        def logicalOrExpr(self):
            return self.getTypedRuleContext(TriggerParser.LogicalOrExprContext,0)

        def logicalAndExpr(self):
            return self.getTypedRuleContext(TriggerParser.LogicalAndExprContext,0)


        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterOrExpr" ):
                listener.enterOrExpr(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitOrExpr" ):
                listener.exitOrExpr(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitOrExpr" ):
                return visitor.visitOrExpr(self)
            else:
                return visitor.visitChildren(self)



    def logicalOrExpr(self, _p:int=0):
        _parentctx = self._ctx
        _parentState = self.state
        localctx = TriggerParser.LogicalOrExprContext(self, self._ctx, _parentState)
        _prevctx = localctx
        _startState = 4
        self.enterRecursionRule(localctx, 4, self.RULE_logicalOrExpr, _p)
        try:
            self.enterOuterAlt(localctx, 1)
            localctx = TriggerParser.NextToAndContext(self, localctx)
            self._ctx = localctx
            _prevctx = localctx

            self.state = 28
            self.logicalAndExpr(0)
            self._ctx.stop = self._input.LT(-1)
            self.state = 35
            self._errHandler.sync(self)
            _alt = self._interp.adaptivePredict(self._input,0,self._ctx)
            while _alt!=2 and _alt!=ATN.INVALID_ALT_NUMBER:
                if _alt==1:
                    if self._parseListeners is not None:
                        self.triggerExitRuleEvent()
                    _prevctx = localctx
                    localctx = TriggerParser.OrExprContext(self, TriggerParser.LogicalOrExprContext(self, _parentctx, _parentState))
                    self.pushNewRecursionContext(localctx, _startState, self.RULE_logicalOrExpr)
                    self.state = 30
                    if not self.precpred(self._ctx, 2):
                        from antlr4.error.Errors import FailedPredicateException
                        raise FailedPredicateException(self, "self.precpred(self._ctx, 2)")
                    self.state = 31
                    self.match(TriggerParser.T__0)
                    self.state = 32
                    self.logicalAndExpr(0) 
                self.state = 37
                self._errHandler.sync(self)
                _alt = self._interp.adaptivePredict(self._input,0,self._ctx)

        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.unrollRecursionContexts(_parentctx)
        return localctx


    class LogicalAndExprContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser


        def getRuleIndex(self):
            return TriggerParser.RULE_logicalAndExpr

     
        def copyFrom(self, ctx:ParserRuleContext):
            super().copyFrom(ctx)


    class NextToCmpContext(LogicalAndExprContext):

        def __init__(self, parser, ctx:ParserRuleContext): # actually a TriggerParser.LogicalAndExprContext
            super().__init__(parser)
            self.copyFrom(ctx)

        def cmpExpr(self):
            return self.getTypedRuleContext(TriggerParser.CmpExprContext,0)


        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterNextToCmp" ):
                listener.enterNextToCmp(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitNextToCmp" ):
                listener.exitNextToCmp(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitNextToCmp" ):
                return visitor.visitNextToCmp(self)
            else:
                return visitor.visitChildren(self)


    class AndExprContext(LogicalAndExprContext):

        def __init__(self, parser, ctx:ParserRuleContext): # actually a TriggerParser.LogicalAndExprContext
            super().__init__(parser)
            self.copyFrom(ctx)

        def logicalAndExpr(self):
            return self.getTypedRuleContext(TriggerParser.LogicalAndExprContext,0)

        def cmpExpr(self):
            return self.getTypedRuleContext(TriggerParser.CmpExprContext,0)


        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterAndExpr" ):
                listener.enterAndExpr(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitAndExpr" ):
                listener.exitAndExpr(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitAndExpr" ):
                return visitor.visitAndExpr(self)
            else:
                return visitor.visitChildren(self)



    def logicalAndExpr(self, _p:int=0):
        _parentctx = self._ctx
        _parentState = self.state
        localctx = TriggerParser.LogicalAndExprContext(self, self._ctx, _parentState)
        _prevctx = localctx
        _startState = 6
        self.enterRecursionRule(localctx, 6, self.RULE_logicalAndExpr, _p)
        try:
            self.enterOuterAlt(localctx, 1)
            localctx = TriggerParser.NextToCmpContext(self, localctx)
            self._ctx = localctx
            _prevctx = localctx

            self.state = 39
            self.cmpExpr()
            self._ctx.stop = self._input.LT(-1)
            self.state = 46
            self._errHandler.sync(self)
            _alt = self._interp.adaptivePredict(self._input,1,self._ctx)
            while _alt!=2 and _alt!=ATN.INVALID_ALT_NUMBER:
                if _alt==1:
                    if self._parseListeners is not None:
                        self.triggerExitRuleEvent()
                    _prevctx = localctx
                    localctx = TriggerParser.AndExprContext(self, TriggerParser.LogicalAndExprContext(self, _parentctx, _parentState))
                    self.pushNewRecursionContext(localctx, _startState, self.RULE_logicalAndExpr)
                    self.state = 41
                    if not self.precpred(self._ctx, 2):
                        from antlr4.error.Errors import FailedPredicateException
                        raise FailedPredicateException(self, "self.precpred(self._ctx, 2)")
                    self.state = 42
                    self.match(TriggerParser.T__1)
                    self.state = 43
                    self.cmpExpr() 
                self.state = 48
                self._errHandler.sync(self)
                _alt = self._interp.adaptivePredict(self._input,1,self._ctx)

        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.unrollRecursionContexts(_parentctx)
        return localctx


    class CmpExprContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser


        def getRuleIndex(self):
            return TriggerParser.RULE_cmpExpr

     
        def copyFrom(self, ctx:ParserRuleContext):
            super().copyFrom(ctx)



    class NextToMathContext(CmpExprContext):

        def __init__(self, parser, ctx:ParserRuleContext): # actually a TriggerParser.CmpExprContext
            super().__init__(parser)
            self.copyFrom(ctx)

        def mathExpr(self):
            return self.getTypedRuleContext(TriggerParser.MathExprContext,0)


        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterNextToMath" ):
                listener.enterNextToMath(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitNextToMath" ):
                listener.exitNextToMath(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitNextToMath" ):
                return visitor.visitNextToMath(self)
            else:
                return visitor.visitChildren(self)


    class CompareExprContext(CmpExprContext):

        def __init__(self, parser, ctx:ParserRuleContext): # actually a TriggerParser.CmpExprContext
            super().__init__(parser)
            self.left = None # MathExprContext
            self.op = None # Token
            self.right = None # MathExprContext
            self.copyFrom(ctx)

        def mathExpr(self, i:int=None):
            if i is None:
                return self.getTypedRuleContexts(TriggerParser.MathExprContext)
            else:
                return self.getTypedRuleContext(TriggerParser.MathExprContext,i)


        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterCompareExpr" ):
                listener.enterCompareExpr(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitCompareExpr" ):
                listener.exitCompareExpr(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitCompareExpr" ):
                return visitor.visitCompareExpr(self)
            else:
                return visitor.visitChildren(self)



    def cmpExpr(self):

        localctx = TriggerParser.CmpExprContext(self, self._ctx, self.state)
        self.enterRule(localctx, 8, self.RULE_cmpExpr)
        self._la = 0 # Token type
        try:
            self.state = 54
            self._errHandler.sync(self)
            la_ = self._interp.adaptivePredict(self._input,2,self._ctx)
            if la_ == 1:
                localctx = TriggerParser.CompareExprContext(self, localctx)
                self.enterOuterAlt(localctx, 1)
                self.state = 49
                localctx.left = self.mathExpr(0)
                self.state = 50
                localctx.op = self._input.LT(1)
                _la = self._input.LA(1)
                if not((((_la) & ~0x3f) == 0 and ((1 << _la) & 1016) != 0)):
                    localctx.op = self._errHandler.recoverInline(self)
                else:
                    self._errHandler.reportMatch(self)
                    self.consume()
                self.state = 51
                localctx.right = self.mathExpr(0)
                pass

            elif la_ == 2:
                localctx = TriggerParser.NextToMathContext(self, localctx)
                self.enterOuterAlt(localctx, 2)
                self.state = 53
                self.mathExpr(0)
                pass


        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.exitRule()
        return localctx


    class MathExprContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser


        def getRuleIndex(self):
            return TriggerParser.RULE_mathExpr

     
        def copyFrom(self, ctx:ParserRuleContext):
            super().copyFrom(ctx)


    class MathBinaryExprContext(MathExprContext):

        def __init__(self, parser, ctx:ParserRuleContext): # actually a TriggerParser.MathExprContext
            super().__init__(parser)
            self.left = None # MathExprContext
            self.op = None # Token
            self.right = None # MulExprContext
            self.copyFrom(ctx)

        def mathExpr(self):
            return self.getTypedRuleContext(TriggerParser.MathExprContext,0)

        def mulExpr(self):
            return self.getTypedRuleContext(TriggerParser.MulExprContext,0)


        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterMathBinaryExpr" ):
                listener.enterMathBinaryExpr(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitMathBinaryExpr" ):
                listener.exitMathBinaryExpr(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitMathBinaryExpr" ):
                return visitor.visitMathBinaryExpr(self)
            else:
                return visitor.visitChildren(self)


    class NextToMulContext(MathExprContext):

        def __init__(self, parser, ctx:ParserRuleContext): # actually a TriggerParser.MathExprContext
            super().__init__(parser)
            self.copyFrom(ctx)

        def mulExpr(self):
            return self.getTypedRuleContext(TriggerParser.MulExprContext,0)


        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterNextToMul" ):
                listener.enterNextToMul(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitNextToMul" ):
                listener.exitNextToMul(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitNextToMul" ):
                return visitor.visitNextToMul(self)
            else:
                return visitor.visitChildren(self)



    def mathExpr(self, _p:int=0):
        _parentctx = self._ctx
        _parentState = self.state
        localctx = TriggerParser.MathExprContext(self, self._ctx, _parentState)
        _prevctx = localctx
        _startState = 10
        self.enterRecursionRule(localctx, 10, self.RULE_mathExpr, _p)
        self._la = 0 # Token type
        try:
            self.enterOuterAlt(localctx, 1)
            localctx = TriggerParser.NextToMulContext(self, localctx)
            self._ctx = localctx
            _prevctx = localctx

            self.state = 57
            self.mulExpr(0)
            self._ctx.stop = self._input.LT(-1)
            self.state = 64
            self._errHandler.sync(self)
            _alt = self._interp.adaptivePredict(self._input,3,self._ctx)
            while _alt!=2 and _alt!=ATN.INVALID_ALT_NUMBER:
                if _alt==1:
                    if self._parseListeners is not None:
                        self.triggerExitRuleEvent()
                    _prevctx = localctx
                    localctx = TriggerParser.MathBinaryExprContext(self, TriggerParser.MathExprContext(self, _parentctx, _parentState))
                    localctx.left = _prevctx
                    self.pushNewRecursionContext(localctx, _startState, self.RULE_mathExpr)
                    self.state = 59
                    if not self.precpred(self._ctx, 2):
                        from antlr4.error.Errors import FailedPredicateException
                        raise FailedPredicateException(self, "self.precpred(self._ctx, 2)")
                    self.state = 60
                    localctx.op = self._input.LT(1)
                    _la = self._input.LA(1)
                    if not(_la==10 or _la==11):
                        localctx.op = self._errHandler.recoverInline(self)
                    else:
                        self._errHandler.reportMatch(self)
                        self.consume()
                    self.state = 61
                    localctx.right = self.mulExpr(0) 
                self.state = 66
                self._errHandler.sync(self)
                _alt = self._interp.adaptivePredict(self._input,3,self._ctx)

        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.unrollRecursionContexts(_parentctx)
        return localctx


    class MulExprContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser


        def getRuleIndex(self):
            return TriggerParser.RULE_mulExpr

     
        def copyFrom(self, ctx:ParserRuleContext):
            super().copyFrom(ctx)


    class MulBinaryExprContext(MulExprContext):

        def __init__(self, parser, ctx:ParserRuleContext): # actually a TriggerParser.MulExprContext
            super().__init__(parser)
            self.left = None # MulExprContext
            self.op = None # Token
            self.right = None # PrimaryExprContext
            self.copyFrom(ctx)

        def mulExpr(self):
            return self.getTypedRuleContext(TriggerParser.MulExprContext,0)

        def primaryExpr(self):
            return self.getTypedRuleContext(TriggerParser.PrimaryExprContext,0)


        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterMulBinaryExpr" ):
                listener.enterMulBinaryExpr(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitMulBinaryExpr" ):
                listener.exitMulBinaryExpr(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitMulBinaryExpr" ):
                return visitor.visitMulBinaryExpr(self)
            else:
                return visitor.visitChildren(self)


    class NextToPrimaryContext(MulExprContext):

        def __init__(self, parser, ctx:ParserRuleContext): # actually a TriggerParser.MulExprContext
            super().__init__(parser)
            self.copyFrom(ctx)

        def primaryExpr(self):
            return self.getTypedRuleContext(TriggerParser.PrimaryExprContext,0)


        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterNextToPrimary" ):
                listener.enterNextToPrimary(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitNextToPrimary" ):
                listener.exitNextToPrimary(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitNextToPrimary" ):
                return visitor.visitNextToPrimary(self)
            else:
                return visitor.visitChildren(self)



    def mulExpr(self, _p:int=0):
        _parentctx = self._ctx
        _parentState = self.state
        localctx = TriggerParser.MulExprContext(self, self._ctx, _parentState)
        _prevctx = localctx
        _startState = 12
        self.enterRecursionRule(localctx, 12, self.RULE_mulExpr, _p)
        self._la = 0 # Token type
        try:
            self.enterOuterAlt(localctx, 1)
            localctx = TriggerParser.NextToPrimaryContext(self, localctx)
            self._ctx = localctx
            _prevctx = localctx

            self.state = 68
            self.primaryExpr()
            self._ctx.stop = self._input.LT(-1)
            self.state = 75
            self._errHandler.sync(self)
            _alt = self._interp.adaptivePredict(self._input,4,self._ctx)
            while _alt!=2 and _alt!=ATN.INVALID_ALT_NUMBER:
                if _alt==1:
                    if self._parseListeners is not None:
                        self.triggerExitRuleEvent()
                    _prevctx = localctx
                    localctx = TriggerParser.MulBinaryExprContext(self, TriggerParser.MulExprContext(self, _parentctx, _parentState))
                    localctx.left = _prevctx
                    self.pushNewRecursionContext(localctx, _startState, self.RULE_mulExpr)
                    self.state = 70
                    if not self.precpred(self._ctx, 2):
                        from antlr4.error.Errors import FailedPredicateException
                        raise FailedPredicateException(self, "self.precpred(self._ctx, 2)")
                    self.state = 71
                    localctx.op = self._input.LT(1)
                    _la = self._input.LA(1)
                    if not(_la==12 or _la==13):
                        localctx.op = self._errHandler.recoverInline(self)
                    else:
                        self._errHandler.reportMatch(self)
                        self.consume()
                    self.state = 72
                    localctx.right = self.primaryExpr() 
                self.state = 77
                self._errHandler.sync(self)
                _alt = self._interp.adaptivePredict(self._input,4,self._ctx)

        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.unrollRecursionContexts(_parentctx)
        return localctx


    class PrimaryExprContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser


        def getRuleIndex(self):
            return TriggerParser.RULE_primaryExpr

     
        def copyFrom(self, ctx:ParserRuleContext):
            super().copyFrom(ctx)



    class FuncExprContext(PrimaryExprContext):

        def __init__(self, parser, ctx:ParserRuleContext): # actually a TriggerParser.PrimaryExprContext
            super().__init__(parser)
            self.copyFrom(ctx)

        def func(self):
            return self.getTypedRuleContext(TriggerParser.FuncContext,0)

        def args(self):
            return self.getTypedRuleContext(TriggerParser.ArgsContext,0)


        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterFuncExpr" ):
                listener.enterFuncExpr(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitFuncExpr" ):
                listener.exitFuncExpr(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitFuncExpr" ):
                return visitor.visitFuncExpr(self)
            else:
                return visitor.visitChildren(self)


    class AtomExprContext(PrimaryExprContext):

        def __init__(self, parser, ctx:ParserRuleContext): # actually a TriggerParser.PrimaryExprContext
            super().__init__(parser)
            self.copyFrom(ctx)

        def atom(self):
            return self.getTypedRuleContext(TriggerParser.AtomContext,0)


        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterAtomExpr" ):
                listener.enterAtomExpr(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitAtomExpr" ):
                listener.exitAtomExpr(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitAtomExpr" ):
                return visitor.visitAtomExpr(self)
            else:
                return visitor.visitChildren(self)


    class ParenExprContext(PrimaryExprContext):

        def __init__(self, parser, ctx:ParserRuleContext): # actually a TriggerParser.PrimaryExprContext
            super().__init__(parser)
            self.copyFrom(ctx)

        def expr(self):
            return self.getTypedRuleContext(TriggerParser.ExprContext,0)


        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterParenExpr" ):
                listener.enterParenExpr(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitParenExpr" ):
                listener.exitParenExpr(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitParenExpr" ):
                return visitor.visitParenExpr(self)
            else:
                return visitor.visitChildren(self)



    def primaryExpr(self):

        localctx = TriggerParser.PrimaryExprContext(self, self._ctx, self.state)
        self.enterRule(localctx, 14, self.RULE_primaryExpr)
        try:
            self.state = 88
            self._errHandler.sync(self)
            token = self._input.LA(1)
            if token in [14]:
                localctx = TriggerParser.ParenExprContext(self, localctx)
                self.enterOuterAlt(localctx, 1)
                self.state = 78
                self.match(TriggerParser.T__13)
                self.state = 79
                self.expr()
                self.state = 80
                self.match(TriggerParser.T__14)
                pass
            elif token in [17, 18, 19, 20, 21]:
                localctx = TriggerParser.FuncExprContext(self, localctx)
                self.enterOuterAlt(localctx, 2)
                self.state = 82
                self.func()
                self.state = 83
                self.match(TriggerParser.T__13)
                self.state = 84
                self.args()
                self.state = 85
                self.match(TriggerParser.T__14)
                pass
            elif token in [22, 23]:
                localctx = TriggerParser.AtomExprContext(self, localctx)
                self.enterOuterAlt(localctx, 3)
                self.state = 87
                self.atom()
                pass
            else:
                raise NoViableAltException(self)

        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.exitRule()
        return localctx


    class AtomContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser

        def FACT(self):
            return self.getToken(TriggerParser.FACT, 0)

        def NUMBER(self):
            return self.getToken(TriggerParser.NUMBER, 0)

        def getRuleIndex(self):
            return TriggerParser.RULE_atom

        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterAtom" ):
                listener.enterAtom(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitAtom" ):
                listener.exitAtom(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitAtom" ):
                return visitor.visitAtom(self)
            else:
                return visitor.visitChildren(self)




    def atom(self):

        localctx = TriggerParser.AtomContext(self, self._ctx, self.state)
        self.enterRule(localctx, 16, self.RULE_atom)
        self._la = 0 # Token type
        try:
            self.enterOuterAlt(localctx, 1)
            self.state = 90
            _la = self._input.LA(1)
            if not(_la==22 or _la==23):
                self._errHandler.recoverInline(self)
            else:
                self._errHandler.reportMatch(self)
                self.consume()
        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.exitRule()
        return localctx


    class ArgsContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser

        def expr(self, i:int=None):
            if i is None:
                return self.getTypedRuleContexts(TriggerParser.ExprContext)
            else:
                return self.getTypedRuleContext(TriggerParser.ExprContext,i)


        def getRuleIndex(self):
            return TriggerParser.RULE_args

        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterArgs" ):
                listener.enterArgs(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitArgs" ):
                listener.exitArgs(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitArgs" ):
                return visitor.visitArgs(self)
            else:
                return visitor.visitChildren(self)




    def args(self):

        localctx = TriggerParser.ArgsContext(self, self._ctx, self.state)
        self.enterRule(localctx, 18, self.RULE_args)
        self._la = 0 # Token type
        try:
            self.enterOuterAlt(localctx, 1)
            self.state = 92
            self.expr()
            self.state = 97
            self._errHandler.sync(self)
            _la = self._input.LA(1)
            while _la==16:
                self.state = 93
                self.match(TriggerParser.T__15)
                self.state = 94
                self.expr()
                self.state = 99
                self._errHandler.sync(self)
                _la = self._input.LA(1)

        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.exitRule()
        return localctx


    class FuncContext(ParserRuleContext):
        __slots__ = 'parser'

        def __init__(self, parser, parent:ParserRuleContext=None, invokingState:int=-1):
            super().__init__(parent, invokingState)
            self.parser = parser


        def getRuleIndex(self):
            return TriggerParser.RULE_func

        def enterRule(self, listener:ParseTreeListener):
            if hasattr( listener, "enterFunc" ):
                listener.enterFunc(self)

        def exitRule(self, listener:ParseTreeListener):
            if hasattr( listener, "exitFunc" ):
                listener.exitFunc(self)

        def accept(self, visitor:ParseTreeVisitor):
            if hasattr( visitor, "visitFunc" ):
                return visitor.visitFunc(self)
            else:
                return visitor.visitChildren(self)




    def func(self):

        localctx = TriggerParser.FuncContext(self, self._ctx, self.state)
        self.enterRule(localctx, 20, self.RULE_func)
        self._la = 0 # Token type
        try:
            self.enterOuterAlt(localctx, 1)
            self.state = 100
            _la = self._input.LA(1)
            if not((((_la) & ~0x3f) == 0 and ((1 << _la) & 4063232) != 0)):
                self._errHandler.recoverInline(self)
            else:
                self._errHandler.reportMatch(self)
                self.consume()
        except RecognitionException as re:
            localctx.exception = re
            self._errHandler.reportError(self, re)
            self._errHandler.recover(self, re)
        finally:
            self.exitRule()
        return localctx



    def sempred(self, localctx:RuleContext, ruleIndex:int, predIndex:int):
        if self._predicates == None:
            self._predicates = dict()
        self._predicates[2] = self.logicalOrExpr_sempred
        self._predicates[3] = self.logicalAndExpr_sempred
        self._predicates[5] = self.mathExpr_sempred
        self._predicates[6] = self.mulExpr_sempred
        pred = self._predicates.get(ruleIndex, None)
        if pred is None:
            raise Exception("No predicate with index:" + str(ruleIndex))
        else:
            return pred(localctx, predIndex)

    def logicalOrExpr_sempred(self, localctx:LogicalOrExprContext, predIndex:int):
            if predIndex == 0:
                return self.precpred(self._ctx, 2)
         

    def logicalAndExpr_sempred(self, localctx:LogicalAndExprContext, predIndex:int):
            if predIndex == 1:
                return self.precpred(self._ctx, 2)
         

    def mathExpr_sempred(self, localctx:MathExprContext, predIndex:int):
            if predIndex == 2:
                return self.precpred(self._ctx, 2)
         

    def mulExpr_sempred(self, localctx:MulExprContext, predIndex:int):
            if predIndex == 3:
                return self.precpred(self._ctx, 2)
         




