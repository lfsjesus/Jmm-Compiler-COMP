grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

// Comments
LINE_COMMENT : '//' .*? '\n' -> skip ;
MULTI_COMMENT : '/*' .*? '*/' -> skip ;

// Operators
EQUALS : '=';
MUL : '*' ;
DIV : '/' ;
ADD : '+' ;
SUB : '-' ;
AND : '&&' ;
NOT : '!' ;
LESS : '<';

// Symbols
SEMI : ';' ;
COMMA : ',' ;
VARARGS : '...' ;
DOT : '.' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LBRACK : '[' ;
RBRACK : ']' ;

// Keywords
CLASS : 'class' ;
EXTENDS : 'extends';
PUBLIC : 'public' ;
RETURN : 'return' ;
NEW : 'new' ;
LENGTH : 'length' ; //not keyword
THIS : 'this' ;
IMPORT: 'import' ;
TRUE : 'true';
FALSE : 'false';

// Control structures
IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;

// Types
INT : 'int' ;
BOOLEAN : 'boolean' ;
FLOAT : 'float' ;
DOUBLE : 'double' ;
STRING : 'String' ; //not keyword
VOID : 'void' ;


INTEGER : [0] | ([1-9][0-9]*) ;
ID : [a-zA-Z_$]([a-zA-Z_0-9$])* ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : IMPORT packageName+=ID (DOT packageName+=ID)* SEMI // This gives a list that is the package name
    ;

classDecl
    :   CLASS name=ID (EXTENDS superName=ID)?
        LCURLY
        varDecl*
        methodDecl*
        RCURLY
    ;

varArgs
    : type VARARGS name=ID
    ;

varDecl // WE MAY NEED TO THINK ABOUT THIS, REGARDING MAIN, LENGTH...
    : type declarable SEMI;

declarable
    : name=(ID | 'length' | 'main'); // This is a variable declaration

type
    : type LBRACK RBRACK #ArrayType
    | name=INT #IntType
    | name=BOOLEAN #BooleanType
    | name=STRING #StringType
    | name=FLOAT #FloatType
    | name=DOUBLE #DoubleType
    | name=VOID #VoidType
    | name=ID #ClassType
    ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})? 'static' type name='main' LPAREN param RPAREN // best way is to use the main method in methodDecl
        LCURLY
        varDecl*
        stmt*
        RCURLY
    | (PUBLIC {$isPublic=true;})?
        type name=ID LPAREN (varArgs | param (COMMA param)* (COMMA varArgs)?)? RPAREN
        LCURLY
        (
            varDecl*
            stmt*
            methodReturn
        )?
        RCURLY
    ;

methodCall
    : name=ID LPAREN (expr (COMMA expr)*)? RPAREN
    ;

methodReturn
    : RETURN expr SEMI
    ;

param
    : type name=ID
    ;

stmt
    : expr SEMI #ExprStmt
    | LCURLY stmt* RCURLY #BlockStmt
    | ifExpr (elseIfExpr)* elseExpr #IfStmt
    | WHILE LPAREN expr RPAREN stmt #WhileStmt
    | expr EQUALS expr SEMI #AssignStmt //
    | methodReturn #ReturnStmt
    ;

ifExpr
    : IF LPAREN expr RPAREN stmt;

elseIfExpr
    : ELSE IF LPAREN expr RPAREN stmt;

elseExpr
    : ELSE stmt;

expr
    : LPAREN expr RPAREN #ParenExpr //
    | NEW name=ID LPAREN RPAREN #NewClassExpr //
    | NEW name=INT LBRACK expr RBRACK #NewArrayExpr //
    | LBRACK (expr (COMMA expr)*)? RBRACK #ArrayInitExpr //
    | expr LBRACK expr RBRACK #ArrayAccessExpr //
    | expr DOT LENGTH #ArrayLengthExpr //
    | expr DOT methodCall #MethodCallExpr //
    | expr op= LESS expr #BinaryExpr //
    | op= NOT expr #UnaryExpr //
    | value=TRUE #TrueLiteral //
    | value=FALSE #FalseLiteral //
    | expr op= AND expr #BinaryExpr //
    | expr op= MUL expr #BinaryExpr //
    | expr op= DIV expr #BinaryExpr //
    | expr op= ADD expr #BinaryExpr //
    | expr op= SUB expr #BinaryExpr //
    | value=INTEGER #IntegerLiteral //
    | name=THIS #ThisLiteral //
    | name=ID #VarRefExpr //
    ;



