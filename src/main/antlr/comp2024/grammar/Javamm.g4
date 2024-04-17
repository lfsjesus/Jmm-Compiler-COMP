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
STATIC : 'static' ;
MAIN : 'main' ; //not keyword

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
    : IMPORT packageName+=(ID|MAIN) (DOT packageName+=(ID|MAIN))* SEMI // This gives a list that is the package name
    ;

classDecl
    :   CLASS name=(ID|MAIN) (EXTENDS superName=(ID|MAIN))?
        LCURLY
        varDecl*
        methodDecl*
        RCURLY
    ;

varArgs
    : type VARARGS name=ID
    ;

varDecl
    : type declarable SEMI;

declarable
    : name=(ID | LENGTH | MAIN | STRING); // This is a variable declaration

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

methodDecl locals[boolean isPublic=false, boolean isStatic=false]
    : (PUBLIC {$isPublic=true;})? STATIC{$isStatic=true;} type name=MAIN LPAREN param RPAREN // Main Method Declaration
        LCURLY
        varDecl*
        stmt*
        RCURLY
    | (PUBLIC {$isPublic=true;})? // Regular Method Declaration
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
    : type declarable
    ;

stmt
    : expr SEMI #ExprStmt
    | LCURLY stmt* RCURLY #CurlyStmt
    | ifExpr (elseIfExpr)* elseExpr #IfStmt
    | WHILE LPAREN expr RPAREN stmt #WhileStmt
    | expr EQUALS expr SEMI #AssignStmt
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
    | NEW name=ID LPAREN RPAREN #NewClassObjExpr //
    | NEW name=(INT | FLOAT | DOUBLE | BOOLEAN) LBRACK expr RBRACK #NewArrayExpr //
    | LBRACK (expr (COMMA expr)*)? RBRACK #ArrayInitExpr //
    | expr LBRACK expr RBRACK #ArrayAccessExpr //
    | expr DOT LENGTH #ArrayLengthExpr //
    | methodCall #MethodCallExpr //
    | expr DOT methodCall #MethodCallExpr //
    | expr op= LESS expr #BinaryExpr //
    | op= NOT expr #NotExpr //
    | value=TRUE #TrueLiteral //
    | value=FALSE #FalseLiteral //
    | expr op= AND expr #BinaryExpr //
    | expr op= MUL expr #BinaryExpr //
    | expr op= DIV expr #BinaryExpr //
    | expr op= ADD expr #BinaryExpr //
    | expr op= SUB expr #BinaryExpr //
    | value=INTEGER #IntegerLiteral //
    | name=THIS #ThisLiteral //
    | name=LENGTH #LengthLiteral //
    | name=MAIN #MainLiteral //
    | name=ID #VarRefExpr //

    ;

