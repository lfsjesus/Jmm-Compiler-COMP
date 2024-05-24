package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.List;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String BOOL_TYPE_NAME = "boolean";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

    public static String getBoolTypeName() {
        return BOOL_TYPE_NAME;
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        var kind = Kind.fromString(expr.getKind());

        return switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr);
            case VAR_REF_EXPR -> getVarType(expr.get("name"), getMethodName(expr), table);
            case THIS_LITERAL -> new Type(table.getClassName(), false);
            case LENGTH_LITERAL -> getVarType("length", getMethodName(expr), table);
            case INTEGER_LITERAL, ARRAY_LENGTH_EXPR -> new Type(INT_TYPE_NAME, false);
            case ARRAY_ACCESS_EXPR -> new Type(getVarType(expr.getChild(0).get("name"), getMethodName(expr), table).getName(), false); // because the access is an int or a boolean
            case NEW_ARRAY_EXPR -> new Type(expr.get("name"), true);
            case ARRAY_INIT_EXPR -> {
                JmmNode firstElement = expr.getChildren().get(0);
                Type firstElementType = getExprType(firstElement, table);
                yield new Type(firstElementType.getName(), true);
            }
            case NEW_CLASS_OBJ_EXPR -> new Type(expr.get("name"), false);
            case TRUE_LITERAL, FALSE_LITERAL, NOT_EXPR -> new Type(BOOL_TYPE_NAME, false);
            case METHOD_CALL_EXPR -> {
                String methodName = expr.getChild(1).get("name");
                List<String> imports = table.getImports();

                if (imports.stream().map(imported -> imported.split(", ")[imported.split(",").length - 1]).anyMatch(imported -> imported.equals(expr.getChild(0).get("name")))) {
                    yield new Type("void", false);
                }

                yield table.getReturnType(methodName);

            }

            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*", "/", "-" -> new Type(INT_TYPE_NAME, false);
            case "&&", "<" -> new Type(BOOL_TYPE_NAME, false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }


    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        String methodName = getMethodName(varRefExpr);


        return new Type(INT_TYPE_NAME, false);
    }

    public static Type getVarType(String varName, String methodName, SymbolTable table) {
        if (varName.equals("this")) {
            return new Type(table.getClassName(), false);
        }

        List<Symbol> args = table.getParameters(methodName);
        List<Symbol> locals = table.getLocalVariables(methodName);
        List<Symbol> globals = table.getFields();
        List<String> imports = table.getImports();
        String className = table.getClassName();
        String extendsClass = table.getSuper();

        if (table.getImports().stream().map(imported -> imported.split(", ")[imported.split(",").length - 1]).anyMatch(imported -> imported.equals(varName))) {
            return new Type(varName, false);
        }

        if (extendsClass != null && extendsClass.equals(varName)) {
            return new Type(varName, false);
        }

        if (className.equals(varName)) {
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

        return new Type("Unknown", false);
    }

    public static boolean isVarDeclared(String varName, String methodName, SymbolTable table) {
        List<Symbol> args = table.getParameters(methodName);
        List<Symbol> locals = table.getLocalVariables(methodName);
        List<Symbol> globals = table.getFields();


        for (Symbol arg : args) {
            if (arg.getName().equals(varName)) {
                return true;
            }
        }

        for (Symbol local : locals) {
            if (local.getName().equals(varName)) {
                return true;
            }
        }

        for (Symbol global : globals) {
            if (global.getName().equals(varName)) {
                return true;
            }
        }

        return false;
    }

    public static String getMethodName(JmmNode node) {
        JmmNode methodNode = NodeUtils.getMethodNode(node);

        if (methodNode != null && methodNode.hasAttribute("name")) {
            return methodNode.get("name");
        }
        return null;
    }



    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        // TODO: Simple implementation that needs to be expanded
        return sourceType.getName().equals(destinationType.getName());
    }
}
