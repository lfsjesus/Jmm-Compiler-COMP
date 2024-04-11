package pt.up.fe.comp2024.analysis.passes;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.List;

public class ExpressionAnalyzer extends AnalysisVisitor{
    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(Kind.PAREN_EXPR, this::visitParenExpr);
        //addVisit(Kind.NEW_CLASS_OBJ_EXPR, this::visitNewClassObjExpr);
        //addVisit(Kind.NEW_ARRAY_EXPR, this::visitNewArrayExpr);
        addVisit(Kind.ARRAY_INIT_EXPR, this::visitArrayInitExpr);
        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(Kind.ARRAY_LENGTH_EXPR, this::visitArrayLengthExpr);
        addVisit(Kind.METHOD_CALL_EXPR, this::visitMethodCallExpr);
        //addVisit(Kind.METHOD_CALL, this::visitMethodCallExpr);
        addVisit(Kind.NOT_EXPR, this::visitNotExpr);
        //addVisit(Kind.TRUE_LITERAL, this::visitTrueLiteral);
        //addVisit(Kind.FALSE_LITERAL, this::visitFalseLiteral);
        //addVisit(Kind.INTEGER_LITERAL, this::visitIntegerLiteral);
        //addVisit(Kind.THIS_LITERAL, this::visitThisLiteral);
        //addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitNotExpr(JmmNode node, SymbolTable table) {
        JmmNode expr = node.getChildren().get(0);
        Type type = getNodeType(expr, table);

        if (type.isArray()) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Operator '!' cannot be applied to type " + type.getName(), null));
        } else if (!type.getName().equals("boolean")) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Operator '!' cannot be applied to type " + type.getName(), null));
        }

        return null;
    }

    private Void visitParenExpr(JmmNode node, SymbolTable table) {
        return checkOperation(node, table);
    }

    private Void visitBinaryExpr(JmmNode node, SymbolTable table) {
        return checkOperation(node, table);
    }

    private Void visitArrayAccessExpr(JmmNode node, SymbolTable table) {
        JmmNode array = node.getChildren().get(0);
        JmmNode index = node.getChildren().get(1);

        Type arrayType = getNodeType(array, table);
        Type indexType = getNodeType(index, table);

        if (!arrayType.isArray()) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "The type of the expression must be an array type but it resolved to " + arrayType.getName(), null));
        }

        if (!indexType.getName().equals("int")) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "The type of the expression must be an int type but it resolved to " + indexType.getName(), null));
        }

        return null;
    }

    private Void visitArrayLengthExpr(JmmNode node, SymbolTable table) {
        JmmNode array = node.getChildren().get(0);

        Type arrayType = getNodeType(array, table);

        if (!arrayType.isArray()) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "The type of the expression must be an array type but it resolved to " + arrayType.getName(), null));
        }

        return null;
    }

    private Void visitArrayInitExpr(JmmNode node, SymbolTable table) {
        // loop through all children and check if they are of the same type
        Type type = null;

        for (JmmNode child : node.getChildren()) {
            Type childType = getNodeType(child, table);
            if (type == null) {
                type = childType;
            } else if (!type.equals(childType)) {
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Array elements must be of the same type", null));
                return null;
            }
        }

        return null;
    }

    public Void visitMethodCallExpr(JmmNode node, SymbolTable table) {
        // check import
        if (hasImport(getNodeType(node, table).getName(), table)) {
            return null;
        }

        String methodName = null;

        if (node.getKind().equals("MethodCall")) {
            methodName = node.get("name");

        } else if (node.getKind().equals("MethodCallExpr")) {
            // get last
            methodName = node.getChildren().get(node.getChildren().size() - 1).get("name");
        }

        // check if it's the extend
        String superName = table.getSuper();
        String currentClass = table.getClassName();
        if (superName != null && (superName.equals(getNodeType(node, table).getName()) || currentClass.equals(getNodeType(node, table).getName()))) {
            return null;
        }

        if (!table.getMethods().contains(methodName)) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Method " + methodName + " is not declared", null));
            return null;
        }

        // check if parameters types are correct
        List<Symbol> parameters = table.getParameters(methodName);
        // get passed arguments
        List<JmmNode> arguments = node.getChildren(Kind.METHOD_CALL).get(0).getChildren();

        boolean varargs = (parameters.size() < arguments.size() && parameters.get(parameters.size() - 1).getType().isArray());
        varargs = varargs || (parameters.size() == arguments.size() && parameters.get(parameters.size() - 1).getType().isArray());

        // check if varargs
        if (parameters.size() > arguments.size() && !varargs) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Method " + methodName + " has wrong number of arguments", null));
            return null;
        }
        else if (parameters.size() < arguments.size() && !varargs) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Method " + methodName + " has wrong number of arguments", null));
            return null;
        }

        // check types
        for (int i = 0; i < parameters.size(); i++) {
            Type parameterType = parameters.get(i).getType();
            Type argumentType = getNodeType(arguments.get(i), table);

            if (varargs && (i == parameters.size() - 1)) {
                if (!parameterType.getName().equals(argumentType.getName())) {
                    addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Method " + methodName + " has wrong type of arguments", null));
                    return null;
                }
            } else if (!parameterType.equals(argumentType)) {
                addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Method " + methodName + " has wrong type of arguments", null));
                return null;
            }
        }


        return null;
    }



}
