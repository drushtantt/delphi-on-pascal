grammar delphi;

/* ================= PROGRAM ================= */

program
  : PROGRAM ID SEMI
    (typeSection)?
    (methodImplSection)?
    (varSection)?
    block
    DOT
  ;

/* ================= TYPES ================= */

typeSection
  : TYPE typeDecl+
  ;

typeDecl
  : ID EQ classType SEMI       # classTypeDecl
  | ID EQ interfaceType SEMI    # interfaceTypeDecl
  ;

classType
  : CLASS (LPAREN ID RPAREN)? (IMPLEMENTS idList)? classMembers END
  ;

interfaceType
  : INTERFACE interfaceMembers END
  ;

interfaceMembers
  : (interfaceMethodDecl)*
  ;

interfaceMethodDecl
  : procedureHeader SEMI
  | functionHeader SEMI
  ;

classMembers
  : (visibilitySection | classMemberDecl)*
  ;

visibilitySection
  : (PRIVATE | PUBLIC | PROTECTED) COLON classMemberDecl*
  ;

classMemberDecl
  : fieldDecl
  | methodHeader SEMI
  ;

fieldDecl
  : idList COLON typeName SEMI
  ;

methodHeader
  : constructorHeader
  | destructorHeader
  | procedureHeader
  | functionHeader
  ;

constructorHeader
  : CONSTRUCTOR ID formalParams?
  ;

destructorHeader
  : DESTRUCTOR ID formalParams?
  ;

procedureHeader
  : PROCEDURE ID formalParams?
  ;

functionHeader
  : FUNCTION ID formalParams? COLON typeName
  ;

/* ================= METHOD IMPLEMENTATIONS ================= */

methodImplSection
  : methodImpl+
  ;

methodImpl
  : methodImplHeader SEMI block SEMI
  ;

methodImplHeader
  : CONSTRUCTOR ID DOT ID formalParams?
  | DESTRUCTOR  ID DOT ID formalParams?
  | PROCEDURE   ID DOT ID formalParams?
  | FUNCTION    ID DOT ID formalParams? COLON typeName
  ;

/* ================= VARS ================= */

varSection
  : VAR varDecl+
  ;

varDecl
  : idList COLON typeName SEMI
  ;

idList
  : ID (COMMA ID)*
  ;

formalParams
  : LPAREN (formalParam (SEMI formalParam)*)? RPAREN
  ;

formalParam
  : idList COLON typeName
  ;

typeName
  : INTEGER
  | ID
  ;

/* ================= STATEMENTS ================= */

block
  : BEGIN stmtList END
  ;

stmtList
  : statement (SEMI statement)* SEMI?
  |
  ;

statement
  : assignment
  | callStmt
  | compoundStmt
  | emptyStmt
  ;

compoundStmt
  : BEGIN stmtList END
  ;

emptyStmt
  :
  ;

assignment
  : lvalue ASSIGN expr
  ;

lvalue
  : ID (DOT ID)?
  ;

callStmt
  : callExpr
  ;

callExpr
  : ID actualParams?                 # builtinOrProcCall
  | ID DOT ID actualParams?          # methodOrStaticCall
  ;

actualParams
  : LPAREN (expr (COMMA expr)*)? RPAREN
  ;

/* ================= EXPRESSIONS ================= */

expr
  : expr op=('*'|'/') expr           # mulDiv
  | expr op=('+'|'-') expr           # addSub
  | INT                              # intLit
  | lvalue                           # lvalExpr
  | callExpr                         # callExprAlt
  | LPAREN expr RPAREN               # parens
  ;

/* ================= LEXER ================= */

PROGRAM     : 'program';
TYPE        : 'type';
VAR         : 'var';
CLASS       : 'class';
PRIVATE     : 'private';
PUBLIC      : 'public';
PROTECTED   : 'protected';
CONSTRUCTOR : 'constructor';
DESTRUCTOR  : 'destructor';
PROCEDURE   : 'procedure';
FUNCTION    : 'function';
BEGIN       : 'begin';
END         : 'end';
INTEGER     : 'integer';
INTERFACE   : 'interface';
IMPLEMENTS  : 'implements';

ASSIGN      : ':=';
EQ          : '=';
COLON       : ':';
SEMI        : ';';
COMMA       : ',';
DOT         : '.';
LPAREN      : '(';
RPAREN      : ')';

ID          : [a-zA-Z_][a-zA-Z0-9_]*;
INT         : [0-9]+;

WS          : [ \t\r\n]+ -> skip;
COMMENT1    : '{' .*? '}' -> skip;
COMMENT2    : '//' ~[\r\n]* -> skip;
