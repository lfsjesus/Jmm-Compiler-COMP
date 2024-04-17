package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.List;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded

        var kind = Kind.fromString(expr.getKind());

        return switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr);
            case VAR_REF_EXPR -> getVarType(expr.get("name"), getMethodName(expr), table);
            case THIS_LITERAL -> new Type(table.getClassName(), false); // IS THIS RIGHT??
            case INTEGER_LITERAL -> new Type(INT_TYPE_NAME, false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        // TODO: Simple implementation that needs to be expanded

        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*" -> new Type(INT_TYPE_NAME, false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }


    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        String methodName = getMethodName(varRefExpr);


        return new Type(INT_TYPE_NAME, false);
    }

    public static Type getVarType(String varName, String methodName, SymbolTable table) {
        List<Symbol> args = table.getParameters(methodName);
        List<Symbol> locals = table.getLocalVariables(methodName);
        List<Symbol> globals = table.getFields();
        List<String> imports = table.getImports();
        String className = table.getClassName();
        String extendsClass = table.getSuper();

        if (imports.contains(varName)) {
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
