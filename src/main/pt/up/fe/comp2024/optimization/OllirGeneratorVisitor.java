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
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        setDefaultVisit(this::defaultVisit);
    }



    private String visitAssignStmt(JmmNode node, Void unused) {

        var lhs = exprVisitor.visit(node.getJmmChild(0));
        var rhs = exprVisitor.visit(node.getJmmChild(1));

        boolean fieldBeingAssigned = table.getFields().stream().anyMatch(field -> field.getName().equals(node.getJmmChild(0).get("name")) &&
                                    table.getLocalVariables(TypeUtils.getMethodName(node)).stream().noneMatch(local -> local.getName().equals(node.getJmmChild(0).get("name"))) &&
                                    table.getParameters(TypeUtils.getMethodName(node)).stream().noneMatch(param -> param.getName().equals(node.getJmmChild(0).get("name"))));

        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(lhs.getComputation());
        code.append(rhs.getComputation());

        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);

        code.append(lhs.getCode());
        code.append(SPACE);

        if (!fieldBeingAssigned) {
            code.append(ASSIGN);
            code.append(typeString);
            code.append(SPACE);
            code.append(rhs.getCode());
        }
        else {
            code.append(rhs.getCode());
            code.append(")");
            code.append(".V");
        }

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

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {
        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        String id = node.getChildren().get(1).get("name");

        return id + typeCode;
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

        String name = node.get("name");
        code.append(name);

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

        String retType = OptUtils.toOllirType(node.getJmmChild(0));
        code.append(retType);
        code.append(L_BRACKET);


        // visit the children
        int afterParam = 1 + params.size();
        int stop = (retType.equals(".V")) ? node.getNumChildren() : node.getNumChildren() - 1;
        for (int i = afterParam; i < stop; i++) {
            JmmNode child = node.getJmmChild(i);
            String childCode = "";
            String computation = "";
            if (child.isInstance(EXPR_STMT)) {
                child = child.getJmmChild(0);
                OllirExprResult childResult = exprVisitor.visit(child);
                childCode = childResult.getCode();
                computation = childResult.getComputation();
            }
            else {
                childCode = visit(child);
            }
            code.append(computation);
            code.append(childCode);

        }

        // return statement
        if (retType.equals(".V")) {
            code.append("ret.V;\n");
        }
        else {
            JmmNode returnStmt = node.getJmmChild(node.getNumChildren() - 1);
            String returnCode = visit(returnStmt);
            code.append(returnCode);
        }

        code.append(R_BRACKET);
        code.append(NL);
        return code.toString();
    }

    private String visitVarDecl(JmmNode node, Void unused) {
        if (node.getJmmParent().isInstance(METHOD_DECL)) {
            return "";
        }

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        String id = node.getChildren().get(1).get("name");

        return id + typeCode;
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
        boolean needNl = true;

        for (JmmNode child : node.getChildren()) {
            String result = visit(child);
            code.append(NL);

            if (VAR_DECL.check(child)) {
                code.append(".field public ");
                code.append(result);
                code.append(END_STMT);
                continue;
            }

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

        // imports
        List<String> imports = table.getImports().stream()
                .map(imported -> imported.replace(", ", "."))
                .toList();

        for (String imported : imports) {
            code.append("import ");
            code.append(imported);
            code.append(END_STMT);
        }

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
