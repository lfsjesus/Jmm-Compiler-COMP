package pt.up.fe.comp2024.analysis;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public abstract class AnalysisVisitor extends PreorderJmmVisitor<SymbolTable, Void> implements AnalysisPass {

    private List<Report> reports;

    public AnalysisVisitor() {
        reports = new ArrayList<>();
        setDefaultValue(() -> null);
    }

    protected void addReport(Report report) {
        reports.add(report);
    }

    protected List<Report> getReports() {
        return reports;
    }


    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        // Visit the node
        visit(root, table);

        // Return reports
        return getReports();
    }

    public Type getNodeType(JmmNode node) {
        String type = node.getKind();

        switch (type) {
            case "TRUE_LITERAL", "FALSE_LITERAL":
                return new Type("boolean", false);
            case "INTEGER_LITERAL":
                return new Type("int", false);
            case "VAR_REF_EXPR":
                return new Type(node.get("type"), false);
            case "THIS_LITERAL":
                return new Type(node.get("type"), false);
            case "NEW_CLASS_OBJ_EXPR":
                return new Type(node.get("type"), false);
            case "NEW_ARRAY_EXPR":
                return new Type(node.get("type"), false);
            case "ARRAY_INIT_EXPR":
                return new Type(node.get("type"), false);
            case "ARRAY_ACCESS_EXPR":
                return new Type(node.get("type"), false);
            case "ARRAY_LENGTH_EXPR":
                return new Type("int", false);
            case "METHOD_CALL_EXPR":
                return new Type(node.get("type"), false);
            case "NOT_EXPR":
                return new Type("boolean", false);
            case "BINARY_EXPR":
                String operator = node.get("op");
                checkOperation(node); // check if the operation is valid
                if (operator.equals("+") || operator.equals("-") || operator.equals("*") || operator.equals("/")) {
                    return new Type("int", false);
                } else {
                    return new Type("boolean", false);
                }
            case "PAREN_EXPR":
                return getNodeType(node.getChildren().get(0));
            default:
                return new Type("Unknown", false);
        }
    }

    public Void checkOperation(JmmNode node) {
        JmmNode left = node.getChildren().get(0);
        JmmNode right = node.getChildren().get(1);

        String operator = node.get("op");

        Type leftType = getNodeType(left);
        Type rightType = getNodeType(right);

        if (!leftType.equals(rightType)) {
            // add report
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(node),
                    NodeUtils.getColumn(node),
                    "Incompatible types in operation " + operator,
                    null)
            );
        }

        if (operator.equals("&&")) {
            if (!leftType.getName().equals("boolean") || !rightType.getName().equals("boolean")) {
                // add report
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(node),
                        NodeUtils.getColumn(node),
                        "Incompatible types in logical operation " + operator,
                        null)
                );
            }
        } else if (operator.equals("int")) {
            if (!leftType.getName().equals("int") || !rightType.getName().equals("int")) {
                // add report
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(node),
                        NodeUtils.getColumn(node),
                        "Incompatible types in arithmetic operation " + operator,
                        null)
                );
            }
        }

        return null;
    }
}
