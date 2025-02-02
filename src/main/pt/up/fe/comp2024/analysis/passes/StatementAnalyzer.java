package pt.up.fe.comp2024.analysis.passes;
import org.w3c.dom.Node;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.Arrays;
import java.util.List;

public class StatementAnalyzer extends AnalysisVisitor{
    @Override
    public void buildVisitor() {
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.IF_STMT, this::visitIfStmt);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
        addVisit(Kind.METHOD_RETURN, this::visitReturnStmt);
    }

    private Void visitWhileStmt(JmmNode node, SymbolTable table) {
        JmmNode condition = node.getChildren().get(0);
        JmmNode body = node.getChildren().get(1);

        Type conditionType = getNodeType(condition, table);

        if (!conditionType.getName().equals("boolean") || conditionType.isArray()) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(condition), NodeUtils.getColumn(condition), "Condition of while statement must be of type boolean", null));
        }

        visit(body, table);

        return null;
    }

    private Void visitIfStmt(JmmNode node, SymbolTable table) {
        JmmNode condition = node.getChildren().get(0).getChild(0);
        JmmNode stmt = node.getChildren().get(1);

        Type conditionType = getNodeType(condition, table);

        if (!conditionType.getName().equals("boolean") || conditionType.isArray()) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(condition), NodeUtils.getColumn(condition), "Condition of if statement must be of type boolean", null));
        }

        for (JmmNode child : stmt.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitReturnStmt(JmmNode node, SymbolTable table) {
        JmmNode stmt = node.isInstance(Kind.RETURN_STMT) ? node : node.getChildren().get(0);

        Type returnType = getNodeType(stmt, table);
        Type declaredReturnType = getNodeType(NodeUtils.getMethodNode(stmt), table);

        if (returnType == null) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(stmt), NodeUtils.getColumn(stmt), "Unable to determine return type", null));
            return null;
        }

        // check if the return type is an import
        if (hasImport(returnType.getName(), table)) {
            return null;
        }

        // check if the return type matches the method return type
        if (!returnType.equals(declaredReturnType)) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(stmt), NodeUtils.getColumn(stmt), "Return type does not match method return type", null));
        }


        for (JmmNode child : stmt.getChildren()) {
            visit(child, table);
        }

        return null;
    }

    private Void visitAssignStmt(JmmNode node, SymbolTable table) {
        JmmNode left = node.getChildren().get(0);
        JmmNode right = node.getChildren().get(1);

        Type leftType = getNodeType(left, table);
        Type rightType = getNodeType(right, table);

        // Something went wrong when trying to get the type of the left or right side
        if (leftType == null || rightType == null) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Incompatible types in assignment", null));
            return null;
        }

        // Take care of the case where the left side is an array access
        if (!left.isInstance(Kind.VAR_REF_EXPR) &&
                !(left.isInstance(Kind.ARRAY_ACCESS_EXPR) && (left.getChild(0).isInstance(Kind.VAR_REF_EXPR) ||
                        left.getChild(0).isInstance(Kind.MAIN_LITERAL) ||
                        left.getChild(0).isInstance(Kind.LENGTH_LITERAL))) &&
                !left.isInstance(Kind.MAIN_LITERAL) &&
                !left.isInstance(Kind.LENGTH_LITERAL)) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Left side must be an Identifier", null));
            return null;
        }

        if (hasImport(rightType.getName(), table) && !right.isInstance(Kind.NEW_CLASS_OBJ_EXPR)) {
            return null;
        }

        List<String> primitiveTypes = Arrays.asList("int", "boolean");

        // Check if the left side is a primitive type and the right side is an import
        if (primitiveTypes.contains(leftType.getName()) && hasImport(rightType.getName(), table) && !right.isInstance(Kind.NEW_CLASS_OBJ_EXPR)) { // we don't know what rightType is, if it's import, accept it
            return null;
        }

        String extendedClass = table.getSuper();

        if (extendedClass != null) {
            if (leftType.getName().equals(extendedClass) || rightType.getName().equals(extendedClass) || (rightType.getName().equals(table.getClassName()))) {
                return null;
            }
        }

        if (!leftType.equals(rightType) || leftType.isArray() != rightType.isArray()) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Incompatible types in assignment", null));
        }

        return null;
    }
}
