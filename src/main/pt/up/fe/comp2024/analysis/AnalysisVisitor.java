package pt.up.fe.comp2024.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
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

    public Type getNodeType(JmmNode node, SymbolTable table) {
        String type = node.getKind();

        switch (type) {
            case "TrueLiteral", "FalseLiteral", "NotExpr":
                return new Type("boolean", false);
            case "IntegerLiteral":
                return new Type("int", false);
            case "VarRefExpr":
                String methodName = getMethodName(node);
                return getVarType(node.get("name"), methodName, table);
            case "ThisLiteral":
                return new Type(node.get("type"), false);
            case "NewClassObjExpr":
                return new Type(node.get("name"), false);
            case "NewArrayExpr":
                return new Type(node.get("type"), false);
            case "ArrayInitExpr":
                return new Type(getNodeType(node.getChildren().get(0), table).getName(), true);
            case "ArrayAccessExpr":
                return getNodeType(node.getChildren().get(0), table); // type of element is the same as the array
            case "ArrayLengthExpr":
                return new Type("int", false);
            case "MethodCallExpr":
                return getNodeType(node.getChildren().get(0), table);
            case "MethodCall":
                return table.getReturnType(node.get("name"));
            case "BinaryExpr":
                String operator = node.get("op");
                checkOperation(node, table); // check if the operation is valid
                if (operator.equals("+") || operator.equals("-") || operator.equals("*") || operator.equals("/")) {
                    return new Type("int", false);
                } else {
                    return new Type("boolean", false);
                }
            case "ParenExpr":
                return getNodeType(node.getChildren().get(0), table);
            case "MethodDecl":
                return table.getReturnType(node.get("name")); // MAKES SENSE TO HAVE GETRETURNTYPE?
            default:
                return new Type("Unknown", false);
        }
    }

    public Void checkOperation(JmmNode node, SymbolTable table) {
        JmmNode left = node.getChildren().get(0);
        JmmNode right = node.getChildren().get(1);

        String operator = node.get("op");

        Type leftType = getNodeType(left, table);
        Type rightType = getNodeType(right, table);

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
        } else if (operator.equals("+") || operator.equals("-") || operator.equals("*") || operator.equals("/")) {
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

    public String getMethodName(JmmNode node) {
        while (node != null && !node.getKind().equals("MethodDecl")) {
            node = node.getJmmParent();
        }

        if (node != null && node.hasAttribute("name")) {
            return node.get("name");
        }

        return null;
    }

    public Type getVarType(String varName, String methodName, SymbolTable table) {
        List<Symbol> args = table.getParameters(methodName);
        List<Symbol> locals = table.getLocalVariables(methodName);
        List<Symbol> globals = table.getFields();
        List<String> imports = table.getImports();
        String extendsClass = table.getSuper();

        if (imports.contains(varName)) {
            return new Type(varName, false);
        }

        if (extendsClass != null && extendsClass.equals(varName)) {
            return new Type(varName, false);
        }

        if (args != null) {
            for (Symbol arg : args) {
                if (arg.getName().equals(varName)) {
                    return arg.getType();
                }
            }
        }

        if (locals != null) {
            for (Symbol local : locals) {
                if (local.getName().equals(varName)) {
                    return local.getType();
                }
            }
        }

        if (globals != null) {
            for (Symbol global : globals) {
                if (global.getName().equals(varName)) {
                    return global.getType();
                }
            }
        }

        return new Type("Unknown", false);
    }

    public boolean hasImport(String className, SymbolTable table) {
        List<String> imports = table.getImports();

        // each string is an import in form "[word1, word2, word3]". cheack if last word is the class name
        for (String importName : imports) {
            try {
                String subString = importName.substring(importName.lastIndexOf(".") + 1);
                if (subString.equals(className)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        return false;

    }


}
