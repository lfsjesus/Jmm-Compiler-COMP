package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(TRUE_LITERAL, this::visitTrueLiteral);
        addVisit(FALSE_LITERAL, this::visitFalseLiteral);
        addVisit(METHOD_CALL_EXPR, this::visitMethodCallExpr);

        addVisit(NEW_CLASS_OBJ_EXPR, this::visitNewClassObjExpr);
        setDefaultVisit(this::defaultVisit);
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitTrueLiteral(JmmNode node, Void unused) {
        var boolType = new Type(TypeUtils.getBoolTypeName(), false);
        String ollirBoolType = OptUtils.toOllirType(boolType);
        String code = "1" + ollirBoolType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitFalseLiteral(JmmNode node, Void unused) {
        var boolType = new Type(TypeUtils.getBoolTypeName(), false);
        String ollirBoolType = OptUtils.toOllirType(boolType);
        String code = "0" + ollirBoolType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        JmmNode left = node.getJmmChild(0);
        JmmNode right = node.getJmmChild(1);

        var lhs = visit(left);
        var rhs = visit(right);

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;


        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = TypeUtils.getExprType(node, table);
        computation.append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        var id = node.get("name");
        // each field has name attribute, check matches
        boolean isField = table.getFields().stream().anyMatch(field -> field.getName().equals(id));
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        String ollirType = OptUtils.toOllirType(TypeUtils.getVarType(id, TypeUtils.getMethodName(node), table));

        // check if node appears in the right side of an assignment: is second child of assign stmt
        if (isField && node.getJmmParent().isInstance(ASSIGN_STMT) && node.getJmmParent().getChild(1).equals(node)) {
            String temp = OptUtils.getTemp() + OptUtils.toOllirType(TypeUtils.getVarType(id, TypeUtils.getMethodName(node), table));
            computation.append(temp).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE).append("getfield(this, ").append(id).append(ollirType).append(")").append(ollirType).append(END_STMT);
            code.append(temp);
        }
        else if (isField && node.getJmmParent().isInstance(ASSIGN_STMT) && node.getJmmParent().getChild(0).equals(node)) {
            // use putfield: putfield(this, field.type, value.type).type -> this is an assignment like class_field = value
            code.append("putfield(this, ").append(id).append(ollirType).append(",");
        }
        else {
            code.append(id).append(ollirType);
        }


        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        var methodName = node.get("name");
        var methodType = table.getReturnType(methodName);
        var ollirType = OptUtils.toOllirType(methodType);

        var code = methodName + ollirType;

        return new OllirExprResult(code);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

    private OllirExprResult visitMethodCallExpr(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        JmmNode caller = node.getJmmChild(0); // where method is being called
        JmmNode methodCall = node.getJmmChild(1); // method being called
        String methodName = methodCall.get("name");

        List<JmmNode> params = methodCall.getChildren();

        /*
        supposing int a;
        a = 1;
        io.println(a);

        we need to generate the following code:
        invokestatic(io, "println", a.i32).V;
            */
        String invokeType = getInvokeType(methodCall);

        code.append(invokeType);
        code.append("(");


        //code.append(caller.get("name"));
        switch (invokeType) {
            case "invokestatic":
                code.append(caller.get("name"));
                break;
            case "invokespecial":
                code.append("this");
                break;
            case "invokevirtual":
                code.append(caller.get("name"));
                code.append(".");

                Type type = TypeUtils.getExprType(caller, table);

                code.append(type.getName());


                break;
        }

        code.append(", \"");
        code.append(methodName);
        code.append("\"");

        for (JmmNode param : params) {
            code.append(", ");
            code.append(visit(param).getCode()); // invokestatic(io, "println", a.i32, b.i32).V; -> a.i32, b.i32
        }

        JmmNode parent = node.getJmmParent();
        Type thisType = null;
        String temp = "";
        boolean needTemp = false;
        if (parent.isInstance(BINARY_EXPR)) {
            needTemp = true;
            JmmNode assignStmt = parent.getJmmParent();
            if (assignStmt != null) {
                thisType = TypeUtils.getExprType(assignStmt.getJmmChild(0), table);
                code.append(")");
                code.append(OptUtils.toOllirType(thisType));
            }
            // HANDLE OTHER CASES SUCH AS RETURN STMT
        }
        else if (parent.isInstance(ASSIGN_STMT)) {
            needTemp = true;
            thisType = TypeUtils.getExprType(parent.getJmmChild(0), table);
            code.append(")");
            code.append(OptUtils.toOllirType(thisType));
        }
        else {
            thisType = new Type("void", false);
            code.append(")");
            code.append(OptUtils.toOllirType(thisType));
        }

        if (!needTemp) {
            code.append(END_STMT);
            return new OllirExprResult(code.toString());
        }

        temp = OptUtils.getTemp() + OptUtils.toOllirType(thisType);
        computation.append(temp).append(SPACE).append(ASSIGN).append(OptUtils.toOllirType(thisType)).append(SPACE).append(code).append(END_STMT);
        return new OllirExprResult(temp, computation);
    }

    private String getInvokeType(JmmNode node) {
        // if call on object of current class -> invokespecial
        // if call on object of imported class -> invokestatic
        // if call on object where method is defined in current class -> invokevirtual
        if (node.get("name").equals("println")) {
            System.out.println("io");
        }
        if (table.getMethods().contains(node.get("name"))) {
            return "invokevirtual";
        }
        else if (table.getImports().stream().map(imported -> imported.split(", ")[imported.split(",").length - 1]).anyMatch(imported -> imported.equals(node.getJmmParent().getChild(0).get("name")))) {
            return "invokestatic";
        }

        return "invokespecial";
    }

    private OllirExprResult visitNewClassObjExpr(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        String className = node.get("name");
        code.append("new(");
        code.append(className);
        code.append(").");
        code.append(className);

        String type = OptUtils.toOllirType(new Type(className, false));

        String temp = OptUtils.getTemp() + type;

        computation.append(temp).append(SPACE).append(ASSIGN).append(type).append(SPACE).append(code).append(END_STMT);

        // constructor: invokeespecial(tmp.CLASS, "").V;
        String constructor = "invokespecial(" + temp + ", \"<init>\").V;\n";

        computation.append(constructor);

        return new OllirExprResult(temp, computation);

    }
}
