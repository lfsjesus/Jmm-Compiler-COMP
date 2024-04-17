package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.List;

/**
 * Checks if the type of the expression in a return statement is compatible with the method return type.
 *
 * @author JBispo
 */
public class UndeclaredVariable extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.CLASS_DECL, this::visitClassDecl);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitClassDecl(JmmNode classDecl, SymbolTable table) {
        // check repeated imports
        if (table.getImports().stream().distinct().count() != table.getImports().size()) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(classDecl),
                    NodeUtils.getColumn(classDecl),
                    "Repeated import declaration",
                    null)
            );
        }



        if (table.getFields().stream().distinct().count() != table.getFields().size()) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(classDecl),
                    NodeUtils.getColumn(classDecl),
                    "Repeated field declaration",
                    null)
            );
        }

        return null;
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");

        // if method is "main", check if it's static, return type and argument is String[]
        if (currentMethod.equals("main")) {
            checkMainMethod(method);
        }

        checkMethodUniqueName(method, table);

        checkMethodParameters(method, table);

        checkMethodLocals(method, table);

        checkMethodReturnNumber(method);

        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        // Check if exists a parameter or variable declaration with the same name as the variable reference
        var varRefName = varRefExpr.get("name");

        // Var is a field, return
        if (table.getFields().stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            return null;
        }

        // Var is a parameter, return
        if (table.getParameters(currentMethod).stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            return null;
        }

        // Var is a declared variable, return
        if (table.getLocalVariables(currentMethod).stream()
                .anyMatch(varDecl -> varDecl.getName().equals(varRefName))) {
            return null;
        }

        // Check if it is an imported class since it is not in the symbol table
        if (hasImport(varRefName, table)) {
            return null;
        }

        // Create error report
        var message = String.format("Symbol '%s' is not a declared variable nor a imported class", varRefName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(varRefExpr),
                NodeUtils.getColumn(varRefExpr),
                message,
                null)
        );

        return null;
    }

    private void checkMainMethod(JmmNode mainMethodDecl) {
        boolean isStatic = NodeUtils.getBooleanAttribute(mainMethodDecl, "isStatic", "false");
        boolean isPublic = NodeUtils.getBooleanAttribute(mainMethodDecl, "isPublic", "false");
        boolean isVoid = mainMethodDecl.getChild(0).isInstance(Kind.VOID_TYPE);

        List<JmmNode> params = mainMethodDecl.getChildren(Kind.PARAM);

        boolean singleParam = params.size() == 1;

        JmmNode param = singleParam ? params.get(0) : null;
        JmmNode arrayType = singleParam ? param.getChild(0) : null;

        boolean isArray = singleParam && arrayType.isInstance(Kind.ARRAY_TYPE);
        boolean isStringArray = isArray && arrayType.getChild(0).isInstance(Kind.STRING_TYPE);

        if (!(isStatic && isPublic && isVoid && singleParam && isArray && isStringArray)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(mainMethodDecl),
                    NodeUtils.getColumn(mainMethodDecl),
                    "Main method must be public, static, void and receive a single parameter of type String[]",
                    null)
            );
        }

    }

    private void checkMethodParameters(JmmNode methodDecl, SymbolTable table) {
        List<JmmNode> params = methodDecl.getChildren(Kind.PARAM);

        // check if there are any repeated parameters
        for (int i = 0; i < params.size(); i++) {
            for (int j = i + 1; j < params.size(); j++) {
                if (params.get(i).getChild(1).get("name").equals(params.get(j).getChild(1).get("name"))) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(params.get(j)),
                            NodeUtils.getColumn(params.get(j)),
                            "Parameter '" + params.get(j).getChild(1).get("name") + "' is already declared",
                            null)
                    );
                }
            }
        }

    }

    private void checkMethodLocals(JmmNode methodDecl, SymbolTable table) {
        List<JmmNode> locals = methodDecl.getChildren(Kind.VAR_DECL);

        // check if there are any repeated locals
        for (int i = 0; i < locals.size(); i++) {
            for (int j = i + 1; j < locals.size(); j++) {
                if (locals.get(i).getChild(1).get("name").equals(locals.get(j).getChild(1).get("name"))) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(locals.get(j)),
                            NodeUtils.getColumn(locals.get(j)),
                            "Local variable '" + locals.get(j).getChild(1).get("name") + "' is already declared",
                            null)
                    );
                }
            }
        }

    }

    private void checkMethodUniqueName(JmmNode methodDecl, SymbolTable table) {
        String methodName = methodDecl.get("name");
        
        if (table.getMethods().stream().filter(name -> name.equals(methodName)).count() > 1) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDecl),
                    NodeUtils.getColumn(methodDecl),
                    "Method '" + methodName + "' is already declared",
                    null)
            );
        }
    }

    private void checkMethodReturnNumber(JmmNode methodDecl) {
        // Only allow regular METHOD_RETURN, as defined in grammar.
        List<JmmNode> returnStmts = methodDecl.getChildren(Kind.RETURN_STMT);

        boolean needsReturn = !methodDecl.getChild(0).isInstance(Kind.VOID_TYPE);

        if (needsReturn && !returnStmts.isEmpty()) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDecl),
                    NodeUtils.getColumn(methodDecl),
                    "Method must return a (single) value",
                    null)
            );
        }
    }


}
