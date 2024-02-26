grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
MUL : '*' ;
DIV : '/' ;
ADD : '+' ;
SUB : '-' ;
AND : '&&' ;
OR : '||' ;
NOT : '!' ;
TRUE : 'true';
FALSE : 'false';

CLASS : 'class' ;
INT : 'int' ;
PUBLIC : 'public' ;
RETURN : 'return' ;

INTEGER : [0-9] ;
ID : [a-zA-Z]+ ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : classDecl EOF
    ;


classDecl
    : CLASS name=ID
        LCURLY
        methodDecl*
        RCURLY
    ;

varDecl
    : type name=ID SEMI
    ;

type
    : name= INT ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN param RPAREN
        LCURLY varDecl* stmt* RCURLY
    ;

param
    : type name=ID
    ;

stmt
    : expr EQUALS expr SEMI #AssignStmt //
    | RETURN expr SEMI #ReturnStmt
    ;

expr
    : op= NOT expr #UnaryExpr //
    | TRUE #TrueLiteral //
    | FALSE #FalseLiteral //
    | expr op= AND expr #BinaryExpr //
    | expr op= OR expr #BinaryExpr //
    | expr op= MUL expr #BinaryExpr //
    | expr op= DIV expr #BinaryExpr //
    | expr op= ADD expr #BinaryExpr //
    | expr op= SUB expr #BinaryExpr //
    | value=INTEGER #IntegerLiteral //
    | name=ID #VarRefExpr //
    ;



