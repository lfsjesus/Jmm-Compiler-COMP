package pt.up.fe.comp2024.analysis.passes;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

public class ExpressionAnalyzer extends AnalysisVisitor{
    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(Kind.PAREN_EXPR, this::visitParenExpr);
        //addVisit(Kind.NEW_CLASS_OBJ_EXPR, this::visitNewClassObjExpr);
        //addVisit(Kind.NEW_ARRAY_EXPR, this::visitNewArrayExpr);
        //addVisit(Kind.ARRAY_INIT_EXPR, this::visitArrayInitExpr);
        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(Kind.ARRAY_LENGTH_EXPR, this::visitArrayLengthExpr);
        //addVisit(Kind.METHOD_CALL_EXPR, this::visitMethodCallExpr);
        addVisit(Kind.NOT_EXPR, this::visitNotExpr);
        //addVisit(Kind.TRUE_LITERAL, this::visitTrueLiteral);
        //addVisit(Kind.FALSE_LITERAL, this::visitFalseLiteral);
        //addVisit(Kind.INTEGER_LITERAL, this::visitIntegerLiteral);
        //addVisit(Kind.THIS_LITERAL, this::visitThisLiteral);
        //addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitNotExpr(JmmNode node, SymbolTable table) {
        JmmNode expr = node.getChildren().get(0);
        Type type = getNodeType(expr);

        if (type.isArray()) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Operator '!' cannot be applied to type " + type.getName(), null));
        } else if (!type.getName().equals("boolean")) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "Operator '!' cannot be applied to type " + type.getName(), null));
        }

        return null;
    }

    private Void visitParenExpr(JmmNode node, SymbolTable table) {
        return checkOperation(node);
    }

    private Void visitBinaryExpr(JmmNode node, SymbolTable table) {
        return checkOperation(node);
    }

    private Void visitArrayAccessExpr(JmmNode node, SymbolTable table) {
        JmmNode array = node.getChildren().get(0);
        JmmNode index = node.getChildren().get(1);

        Type arrayType = getNodeType(array);
        Type indexType = getNodeType(index);

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

        Type arrayType = getNodeType(array);

        if (!arrayType.isArray()) {
            addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(node), NodeUtils.getColumn(node), "The type of the expression must be an array type but it resolved to " + arrayType.getName(), null));
        }

        return null;
    }

}
