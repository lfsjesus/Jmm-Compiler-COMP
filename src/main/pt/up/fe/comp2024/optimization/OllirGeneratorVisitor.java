package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {
        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(METHOD_RETURN, this::visitReturn);
        //addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);



        setDefaultVisit(this::defaultVisit);
    }

    private String visitAssignStmt(JmmNode node, Void unused) {

        var lhs = exprVisitor.visit(node.getJmmChild(0));
        var rhs = exprVisitor.visit(node.getJmmChild(1));

        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(lhs.getComputation());
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);


        code.append(lhs.getCode());
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;


        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }
        code.append(expr.getComputation()); // to append tmp0 = 1 + 2, if return is 1 + 2; IMPROVE THIS!
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());
        //code.append(OptUtils.toOllirType(retType));

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));

        String id = node.getChildren().get(1).get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");
        boolean isStatic = NodeUtils.getBooleanAttribute(node, "isStatic", "false");

        if (isPublic) {
            code.append("public ");
        }

        if (isStatic) {
            code.append("static ");
        }
        // name
        var name = node.get("name");

        /*
        if (name.equals("main")) {
            System.out.printf("HERE");
        }

         */
        code.append(name);

        // param
        List<JmmNode> params = node.getChildren(PARAM);
        code.append("(");
        for (JmmNode param : params) {
            code.append(visit(param));
            code.append(", ");
        }
        if (!params.isEmpty()) {
            code.delete(code.length() - 2, code.length());
        }
        code.append(")");

        // type
        var retType = OptUtils.toOllirType(node.getJmmChild(0));
        code.append(retType);
        code.append(L_BRACKET);


        // rest of its children stmts
        var afterParam = 1 + params.size();
        var stop = (retType.equals(".V")) ? node.getNumChildren() : node.getNumChildren() - 1;
        for (int i = afterParam; i < stop; i++) {
            var child = node.getJmmChild(i);
            String childCode = null;
            if (child.isInstance(EXPR_STMT)) {
                child = child.getJmmChild(0);
                childCode = exprVisitor.visit(child).getCode();
            }
            else {
                childCode = visit(child);
            }
            code.append(childCode);
        }

        //extract the return statement
        if (retType.equals(".V")) {
            code.append("ret.V;\n");
        }
        else {
            var returnStmt = node.getJmmChild(node.getNumChildren() - 1);
            var returnCode = visit(returnStmt);
            code.append(returnCode);
        }


        code.append(R_BRACKET);
        code.append(NL);
        return code.toString();
    }

    private String visitVarDecl(JmmNode node, Void unused) {
        // WE NEED TO FIX THIS. DECLARED VARIABLES IN A METHOD THAT
        // ARE NOT BEING USED ARE NOT BEING GENERATED
        if (node.getJmmParent().isInstance(METHOD_DECL)) {
            return "";
        }

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));

        String id = node.getChildren().get(1).get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());

        if (table.getSuper() != null) {
            code.append(" extends ");
            code.append(table.getSuper());
        }


        code.append(L_BRACKET);

        code.append(NL);
        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            // Class fields: .field public fieldName.i32/bool;
            code.append(NL);
            if (VAR_DECL.check(child)) {
                code.append(".field public ");
                code.append(result);
                code.append(END_STMT);
                continue;
            }

            // Otherwise it is a method

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }



    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }


}
