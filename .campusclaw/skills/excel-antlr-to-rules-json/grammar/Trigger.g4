grammar Trigger;

/* ==========================================
   核心解析规则 (文法层)
   ========================================== */
root     : expr EOF ;

expr     : logicalOrExpr ;

// 最底层优先级：逻辑或
logicalOrExpr
         : logicalOrExpr '||' logicalAndExpr     # orExpr
         | logicalAndExpr                        # nextToAnd
         ;

// 倒数第二优先级：逻辑且
logicalAndExpr
         : logicalAndExpr '&&' cmpExpr           # andExpr
         | cmpExpr                               # nextToCmp
         ;

// 倒数第三优先级：比较运算符
cmpExpr  : left=mathExpr op=( '==' | '>' | '<' | '>=' | '<=' | '!=' | '=' ) right=mathExpr # compareExpr
         | mathExpr                                                                        # nextToMath
         ;

// 数学加减优先级
mathExpr : left=mathExpr op=( '+' | '-' ) right=mulExpr                              # mathBinaryExpr
         | mulExpr                                                                   # nextToMul
         ;

// 数学乘除优先级
mulExpr  : left=mulExpr op=( '*' | '/' ) right=primaryExpr                           # mulBinaryExpr
         | primaryExpr                                                               # nextToPrimary
         ;

// 基础原子项 / 优先级最高
primaryExpr
         : '(' expr ')'                          # parenExpr
         | func '(' args ')'                     # funcExpr
         | atom                                  # atomExpr
         ;

atom     : FACT | NUMBER ;
args     : expr (',' expr)* ;

// 正式将 'prev' 收编为官方认可的合规函数名
func     : 'abs' | 'min' | 'max' | 'avg' | 'prev' ;


/* ==========================================
   词法解析规则 (词法层)
   ========================================== */

// 增强型 FACT 词法：剥离旧账本伪特征后缀
FACT     : '[' [a-zA-Z_0-9\u4e00-\u9fa5\-]+ ']'
         | [a-zA-Z_][a-zA-Z0-9_]*
         ;

NUMBER   : '-'? [0-9]+ ('.' [0-9]+)? ;
WS       : [ \t\r\n]+ -> skip ;
