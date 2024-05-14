package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
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
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(PAREN_EXPR, this::visitParenExpr);
        addVisit(NOT_EXPR, this::visitNotExpr);
        addVisit(THIS_LITERAL, this::visitVarRef);
        addVisit(LENGTH_LITERAL, this::visitVarRef);
        addVisit(MAIN_LITERAL, this::visitVarRef);
        addVisit(NEW_CLASS_OBJ_EXPR, this::visitNewClassObjExpr);
        addVisit(NEW_ARRAY_EXPR, this::visitNewArrayExpr);
        addVisit(ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(ARRAY_LENGTH_EXPR, this::visitArrayLengthExpr);
        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        if (node.getNumChildren() == 0) {
            return OllirExprResult.EMPTY;
        }
        return visit(node.getJmmChild(0));

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

        // If we have && operator we need gotos
        if (node.get("op").equals("&&")) {
            StringBuilder computation = new StringBuilder();
            StringBuilder code = new StringBuilder();

            var lhs = visit(left);

            computation.append(lhs.getComputation());

            int trueLabelNum = OptUtils.getNextTrueLabelNum();
            computation.append("if(").append(lhs.getCode())
                    .append(") ")
                    .append("goto true_").append(trueLabelNum)
                    .append(END_STMT);

            String temp = OptUtils.getTemp() + OptUtils.toOllirType(TypeUtils.getExprType(node, table));

            computation.append(temp).append(SPACE)
                                    .append(ASSIGN)
                                    .append(OptUtils.toOllirType(TypeUtils.getExprType(node, table)))
                                    .append(SPACE)
                                    .append("0.bool")
                                    .append(END_STMT);

            int endLabelNum = OptUtils.getNextEndLabelNum();

            computation.append("goto end_").append(endLabelNum).append(END_STMT);


            // Label true_0
            computation.append("true_").append(trueLabelNum).append(":").append('\n');

            var rhs = visit(right);

            computation.append(rhs.getComputation());
            // now previous temp is the result of the code of rhs
            computation.append(temp).append(SPACE).append(ASSIGN)
                    .append(OptUtils.toOllirType(TypeUtils.getExprType(node, table)))
                    .append(SPACE).append(rhs.getCode()).append(END_STMT);

            // Label end_0
            computation.append("end_").append(endLabelNum).append(":").append('\n');

            return new OllirExprResult(temp, computation);
        }

        if (node.get("op").equals("<")) {
            StringBuilder computation = new StringBuilder();
            StringBuilder code = new StringBuilder();

            var lhs = visit(left);
            var rhs = visit(right);

            computation.append(lhs.getComputation());
            computation.append(rhs.getComputation());

            int trueLabelNum = OptUtils.getNextTrueLabelNum();

            computation.append("if(").append(lhs.getCode())
                    .append(" <.bool ")
                    .append(rhs.getCode()).append(") goto true_")
                    .append(trueLabelNum).append(END_STMT);

            String temp = OptUtils.getTemp() + OptUtils.toOllirType(TypeUtils.getExprType(node, table));

            computation.append(temp).append(SPACE)
                                    .append(ASSIGN)
                                    .append(OptUtils.toOllirType(TypeUtils.getExprType(node, table)))
                                    .append(SPACE)
                                    .append("0.bool")
                                    .append(END_STMT);

            int endLabelNum = OptUtils.getNextEndLabelNum();

            computation.append("goto end_").append(endLabelNum).append(END_STMT);

            computation.append("true_").append(trueLabelNum).append(":").append('\n');

            computation.append(temp).append(SPACE).append(ASSIGN)
                    .append(OptUtils.toOllirType(TypeUtils.getExprType(node, table)))
                    .append(SPACE).append("1.bool").append(END_STMT);

            computation.append("end_").append(endLabelNum).append(":").append('\n');

            return new OllirExprResult(temp, computation);


        }

        var lhs = visit(left);
        var rhs = visit(right);

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

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

        boolean isField = table.getFields().stream().anyMatch(field -> field.getName().equals(id))
                            && table.getLocalVariables(TypeUtils.getMethodName(node)).stream().noneMatch(local -> local.getName().equals(id))
                            && table.getParameters(TypeUtils.getMethodName(node)).stream().noneMatch(param -> param.getName().equals(id));
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        String ollirType = OptUtils.toOllirType(TypeUtils.getVarType(id, TypeUtils.getMethodName(node), table));

        if (isField &&
                ((node.getJmmParent().isInstance(ASSIGN_STMT) && node.getJmmParent().getChild(1).equals(node)) ||
                (node.getJmmParent().isInstance(METHOD_RETURN)) ||
                (node.getJmmParent().isInstance(METHOD_CALL)) ||
                (node.getJmmParent().isInstance(BINARY_EXPR)) ||
                (node.getJmmParent().isInstance(PAREN_EXPR))) )

                {
            String temp = OptUtils.getTemp() + OptUtils.toOllirType(TypeUtils.getVarType(id, TypeUtils.getMethodName(node), table));
            computation.append(temp).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE).append("getfield(this, ").append(id).append(ollirType).append(")").append(ollirType).append(END_STMT);
            code.append(temp);
        }
        else if (isField && node.getJmmParent().isInstance(ASSIGN_STMT) && node.getJmmParent().getChild(0).equals(node)) {
            code.append("putfield(this, ").append(id).append(ollirType).append(",");
        }
        else {
            code.append(id).append(ollirType);
        }


        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

    private OllirExprResult visitMethodCallExpr(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        JmmNode caller = node.getJmmChild(0);
        JmmNode methodCall = node.getJmmChild(1);

        String methodName = methodCall.get("name");

        List<JmmNode> params = methodCall.getChildren();

        List<String> codes = new ArrayList<>();

        for (JmmNode param : params) {
            var paramVisit = visit(param);
            computation.append(paramVisit.getComputation());
            codes.add(paramVisit.getCode());
        }

        String invokeType = getInvokeType(methodCall);

        code.append(invokeType);
        code.append("(");


        var methodCallerVisit = visit(caller);
        String codeName = methodCallerVisit.getCode();

        computation.append(methodCallerVisit.getComputation());

        if (codeName.isEmpty()) {
            codeName = "this";
        }


        switch (invokeType) {
            case "invokestatic":
                code.append(codeName.split("\\.")[0]);
                break;
            case "invokespecial":
                code.append(codeName);
                break;
            case "invokevirtual":
                code.append(codeName);
                if (codeName.equals("this")) {
                    code.append(".");
                    code.append(table.getClassName());
                }
                break;
        }

        code.append(", \"");
        code.append(methodName);
        code.append("\"");

        for (int i = 0; i < params.size(); i++) {
            code.append(", ");
            code.append(codes.get(i));
        }

        JmmNode parent = node.getJmmParent();
        Type thisType = null;
        String temp = "";
        boolean needTemp = false;

        if (parent.isInstance(BINARY_EXPR)) {
            needTemp = true;
            JmmNode assignStmt = parent.getJmmParent();
            if (assignStmt != null && invokeType.equals("invokevirtual")) {
                thisType = TypeUtils.getExprType(assignStmt.getJmmChild(0), table);
                code.append(")");
                code.append(OptUtils.toOllirType(thisType));
            }
            else {
                thisType = new Type("void", false);
                code.append(")");
                code.append(OptUtils.toOllirType(thisType));
            }
        }
        else if (parent.isInstance(ASSIGN_STMT)) {
            needTemp = true;
            thisType = TypeUtils.getExprType(parent.getJmmChild(0), table);
            code.append(")");
            code.append(OptUtils.toOllirType(thisType));
        }
        else if (parent.isInstance(NEW_CLASS_OBJ_EXPR)) {
            needTemp = true;
            thisType = new Type(parent.get("name"), false);
            code.append(")");
            code.append(OptUtils.toOllirType(thisType));
        }
        else if (parent.isInstance(PAREN_EXPR)) {
            needTemp = true;
            thisType = TypeUtils.getExprType(parent.getJmmChild(0), table);
            code.append(")");
            code.append(OptUtils.toOllirType(thisType));
        }
        else if (parent.isInstance(METHOD_CALL)) {
            needTemp = true;
            try {
                thisType = table.getReturnType(node.getChild(1).get("name"));
                if (thisType == null) {
                    if (invokeType.equals("invokevirtual")) {
                        int index = parent.getChildren().indexOf(node);
                        thisType = table.getParameters(parent.get("name")).get(index).getType();
                    }
                }

            }
            catch (Exception e) {
                thisType = new Type("void", false);
            }

            if (thisType == null) {
                thisType = new Type("void", false);
            }
            code.append(")");
            code.append(OptUtils.toOllirType(thisType));
        }
        else if (parent.isInstance(METHOD_RETURN)) {
            needTemp = true;
            thisType = table.getReturnType(TypeUtils.getMethodName(parent));
            code.append(")");
            code.append(OptUtils.toOllirType(thisType));
        }

        else {
            // check if caller is not static
            if (table.getReturnType(methodName) != null && invokeType.equals("invokevirtual")) {
                thisType = table.getReturnType(methodName);
            }
            else {
                thisType = new Type("void", false);
            }

            code.append(")");
            code.append(OptUtils.toOllirType(thisType));
        }

        if (!needTemp) {
            code.append(END_STMT);
            return new OllirExprResult(code.toString(), computation.toString());
        }

        temp = OptUtils.getTemp() + OptUtils.toOllirType(thisType);

        computation.append(temp).append(SPACE).append(ASSIGN)
                .append(OptUtils.toOllirType(thisType)).append(SPACE)
                .append(code).append(END_STMT);
        return new OllirExprResult(temp, computation);
    }

    private OllirExprResult visitParenExpr(JmmNode node, Void unused) {
        return visit(node.getJmmChild(0));
    }

    private String getInvokeType(JmmNode node) {
        JmmNode parentNode = node.getParent().getChild(0);

        while (parentNode.isInstance(PAREN_EXPR)) {
            parentNode = parentNode.getChild(0);
        }
        Type varType = TypeUtils.getVarType(parentNode.get("name"), TypeUtils.getMethodName(node), table);
        boolean isVarDeclared = TypeUtils.isVarDeclared(parentNode.get("name"), TypeUtils.getMethodName(node), table);

        boolean typeThisAndMethodIsDeclared = varType.getName().equals(table.getClassName()) && table.getMethods().stream().anyMatch(method -> method.equals(node.get("name")));
        boolean extendsClassThis = table.getSuper() != null && table.getClassName().equals(varType.getName());

        boolean isImportedAndNewObj = false;

        try {
            // check if varType name matches any imported class
            isImportedAndNewObj = table.getImports().stream().map(imported -> imported.split(", ")[imported.split(",").length - 1]).anyMatch(imported -> imported.equals(varType.getName()));
            isImportedAndNewObj = isImportedAndNewObj && parentNode.isInstance(NEW_CLASS_OBJ_EXPR);
        }
        catch (Exception e) {
            // do nothing
        }

        if (isVarDeclared || typeThisAndMethodIsDeclared || extendsClassThis || isImportedAndNewObj) {
            return "invokevirtual";
        }

        return "invokestatic";

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

        String constructor = "invokespecial(" + temp + ", \"<init>\").V;\n";

        computation.append(constructor);

        return new OllirExprResult(temp, computation);

    }

    private OllirExprResult visitNotExpr(JmmNode node, Void unused) {
        JmmNode child = node.getJmmChild(0);
        var childVisit = visit(child);

        StringBuilder computation = new StringBuilder();
        computation.append(childVisit.getComputation());

        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);

        // NOT SURE IF THIS IS WORKING FOR ALL CASES
        String code = "!" + resOllirType + SPACE + childVisit.getCode();




        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewArrayExpr(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        JmmNode size = node.getJmmChild(0);

        var sizeVisit = visit(size);
        String type = node.get("name");

        computation.append(sizeVisit.getComputation());

        String finalCode = "new(array, " + sizeVisit.getCode() + ")" + OptUtils.toOllirType(new Type(type, true));

        return new OllirExprResult(finalCode, computation);
    }

    /*
    private OllirExprResult visitIfExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        JmmNode condition = node.getJmmChild(0);

        var conditionVisit = visit(condition);

        computation.append(conditionVisit.getComputation());

        int ifLabelNum = OptUtils.getNextIfLabelNum();

        computation.append("if(")
                .append(conditionVisit.getCode())
                .append(") goto if_")
                .append(ifLabelNum)
                .append(END_STMT);

        // put the else code here
        JmmNode elseNode = node.getParent().getJmmChild(1).getChild(0).getChild(0);

        if (elseNode.isInstance(EXPR_STMT) || elseNode.isInstance(IF_STMT) || elseNode.isInstance(ASSIGN_STMT)) {
            elseNode = elseNode.getJmmChild(0);
        }

        var elseVisit = visit(elseNode);


        computation.append(elseVisit.getComputation())
                    .append(elseVisit.getCode())
                    .append("goto ")
                    .append("endif_")
                    .append(ifLabelNum)
                    .append(END_STMT);


        // If_0 label

        computation.append("if_").append(ifLabelNum).append(":").append('\n');

        JmmNode thenNode = node.getJmmChild(1).getChild(0);

        if (thenNode.isInstance(EXPR_STMT) || thenNode.isInstance(IF_STMT) || thenNode.isInstance(ASSIGN_STMT)) {
            thenNode = thenNode.getJmmChild(0);
        }

        var thenVisit = visit(thenNode);

        computation.append(thenVisit.getComputation())
                    .append(thenVisit.getCode())
                    .append("endif_").append(ifLabelNum).append(":").append('\n');



        return new OllirExprResult(code.toString(), computation.toString());
    }
    */

    private OllirExprResult visitArrayAccessExpr(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        JmmNode array = node.getJmmChild(0);
        JmmNode index = node.getJmmChild(1);

        var arrayVisit = visit(array);
        var indexVisit = visit(index);

        computation.append(arrayVisit.getComputation());
        computation.append(indexVisit.getComputation());

        // remove ".array" from the type
        String arrayType = OptUtils.toOllirType(TypeUtils.getExprType(array, table)).replace(".array", "");

        // a[0] -> a[0.i32].i32
        code.append(array.get("name")).append("[")
                .append(indexVisit.getCode())
                .append("]")
                .append(arrayType);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitArrayLengthExpr(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        // a.length --> temp.i32 := .i32 arraylength(a.array.i32).i32

        JmmNode array = node.getJmmChild(0);

        var arrayVisit = visit(array);

        computation.append(arrayVisit.getComputation());

        String temp = OptUtils.getTemp() + ".i32";

        computation.append(temp).append(SPACE)
                .append(ASSIGN).append(".i32")
                .append(SPACE).append("arraylength(")
                .append(arrayVisit.getCode())
                .append(")").append(".i32").append(END_STMT);

        code.append(temp);

        return new OllirExprResult(code.toString(), computation.toString());



    }
}
