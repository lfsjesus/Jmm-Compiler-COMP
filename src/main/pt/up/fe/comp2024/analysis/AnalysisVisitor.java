package pt.up.fe.comp2024.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

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
            case "IntegerLiteral", "ArrayLengthExpr":
                return new Type("int", false);
            case "VarRefExpr", "LengthLiteral", "MainLiteral":
                String methodName = TypeUtils.getMethodName(node);
                return getVarType(node.get("name"), methodName, table);
            case "ThisLiteral":
                return new Type(table.getClassName(), false);
            case "NewClassObjExpr":
                return new Type(node.get("name"), false);
            case "NewArrayExpr":
                return new Type(node.get("name"), true);
            case "ArrayInitExpr":
                return new Type(getNodeType(node.getChildren().get(0), table).getName(), true);
            case "ArrayAccessExpr":
                String arrayType = getNodeType(node.getChildren().get(0), table).getName();
                return new Type(arrayType, false);
            case "MethodCallExpr", "MethodCall":
                return getReturnType(node, table);
            case "ReturnStmt":
                return getNodeType(node.getChild(0).getChild(0), table);
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

        if (leftType == null || rightType == null) {
            return null;
        }

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

        for (Symbol arg : args) {
            if (arg.getName().equals(varName)) {
                return arg.getType();
            }
        }

        for (Symbol local : locals) {
            if (local.getName().equals(varName)) {
                return local.getType();
            }
        }

        for (Symbol global : globals) {
            if (global.getName().equals(varName)) {
                return global.getType();
            }
        }

        return null;
    }

    public boolean hasImport(String className, SymbolTable table) {
        List<String> imports = table.getImports();

        // each string is an import in form "[word1, word2, word3]". check if last word is the class name
        for (String importName : imports) {
            try {
                String subString = importName.substring(importName.length() - className.length() - 1, importName.length() - 1);
                if (subString.equals(className)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        return false;

    }

    public Type getReturnType(JmmNode node, SymbolTable table) {
        if (node.getKind().equals("MethodCallExpr")) {
            // get last child
            return getReturnType(node.getChildren().get(node.getChildren().size() - 1), table);
        } else if (node.getKind().equals("MethodCall")) {
            // either it's in the table or accept if imported
            Type typeNode = getNodeType(node.getJmmParent().getChildren().get(0), table);
            if (typeNode == null) {
                return null;
            }
            if (table.getReturnType(node.get("name")) != null) {
                return table.getReturnType(node.get("name"));
            } else if (hasImport(typeNode.getName(), table)) {
                return new Type(typeNode.getName(), false);
            } else if (table.getSuper() != null) {
                JmmNode a = node.getParent().getChildren().get(0);
                String type = table.getLocalVariables(TypeUtils.getMethodName(node)).get(0).getType().getName();
                return new Type(type, false);
            }

            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(node),
                    NodeUtils.getColumn(node),
                    "Method " + node.get("name") + " is not declared",
                    null)
            );

            // In case, for example, the method is not declared (callToUndeclaredMethod file). This or stop immediately?
            return null;
        }

        return null;
    }


}
