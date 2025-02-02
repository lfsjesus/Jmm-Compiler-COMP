package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
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
        addVisit(ARRAY_INIT_EXPR, this::visitArrayInitExpr);
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

        // && short-circuit
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
                (node.getJmmParent().isInstance(ARRAY_LENGTH_EXPR)) ||
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

        // If it's varargs, we need to create an array with the parameters
        List<Symbol> tableMethodParams = new ArrayList<>();
        boolean isVarArgs = false;

        if (table.getMethods().contains(methodName)) {
            tableMethodParams = table.getParameters(methodName);
            try {
                isVarArgs = (boolean) tableMethodParams.get(tableMethodParams.size() - 1).getType().getObject("varargs");
            }
            catch (Exception e) {
                // do nothing
            }
        }

        int fixedParams = isVarArgs ? tableMethodParams.size() - 1 : params.size();


        for (int i = 0; i < fixedParams; i++) {
            var paramVisit = visit(params.get(i));
            computation.append(paramVisit.getComputation());
            codes.add(paramVisit.getCode());
        }

        if (isVarArgs) {
            List<JmmNode> varArgs = new ArrayList<>();
            for (int i = fixedParams; i < params.size(); i++) {
                varArgs.add(params.get(i));
            }

            var varArgsVisit = computeVarArg(varArgs);

            computation.append(varArgsVisit.getComputation());
            codes.add(varArgsVisit.getCode());
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

        for (int i = 0; i < codes.size(); i++) {
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
        else if (parent.isInstance(ARRAY_ACCESS_EXPR)) {
            needTemp = true;
            thisType = TypeUtils.getExprType(parent, table);
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


        if (!node.getParent().isInstance(ASSIGN_STMT)) {
            String temp = OptUtils.getTemp() + resOllirType;
            computation.append(temp).append(SPACE)
                    .append(ASSIGN).append(resOllirType)
                    .append(SPACE).append("!")
                    .append(resOllirType).append(SPACE).append(childVisit.getCode()).append(END_STMT);

            return new OllirExprResult(temp, computation);
        }

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

        boolean isField = false;

        if (node.getParent() != null && node.getParent().isInstance(ASSIGN_STMT)) {
            String nameIfField = node.getParent().getChild(0).get("name");
            isField = (table.getFields().stream().anyMatch(field -> field.getName().equals(nameIfField)
                    && table.getLocalVariables(TypeUtils.getMethodName(node)).stream().noneMatch(local -> local.getName().equals(nameIfField)
                    && table.getParameters(TypeUtils.getMethodName(node)).stream().noneMatch(param -> param.getName().equals(nameIfField)))));
        }

        if (node.getParent().isInstance(METHOD_CALL) || isField) {
            String ollirType = OptUtils.toOllirType(new Type(type, true));
            String temp = OptUtils.getTemp() + ollirType;
            computation.append(temp).append(SPACE)
                    .append(ASSIGN).append(ollirType)
                    .append(SPACE).append("new(array, ")
                    .append(sizeVisit.getCode()).append(")").append(ollirType)
                    .append(END_STMT);

            return new OllirExprResult(temp, computation);
        }

        String finalCode = "new(array, " + sizeVisit.getCode() + ")" + OptUtils.toOllirType(new Type(type, true));

        return new OllirExprResult(finalCode, computation);
    }

    private OllirExprResult visitArrayAccessExpr(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        JmmNode array = node.getJmmChild(0);
        JmmNode index = node.getJmmChild(1);

        boolean isField = (table.getFields().stream().anyMatch(field -> field.getName().equals(array.get("name"))
                && table.getLocalVariables(TypeUtils.getMethodName(array)).stream().noneMatch(local -> local.getName().equals(array.get("name"))
                && table.getParameters(TypeUtils.getMethodName(array)).stream().noneMatch(param -> param.getName().equals(array.get("name"))))));


        var arrayVisit = visit(array);
        var indexVisit = visit(index);

        computation.append(arrayVisit.getComputation());
        computation.append(indexVisit.getComputation());

        // remove ".array" from the type
        String arrayType = OptUtils.toOllirType(TypeUtils.getExprType(array, table)).replace(".array", "");

        // a[0] -> a[0.i32].i32
        if (!node.getJmmParent().isInstance(ASSIGN_STMT)) {
            if (isField) {
                String fieldTempNum = OptUtils.getTemp();
                String temp = fieldTempNum + ".array" + arrayType;

                computation.append(temp).append(SPACE)
                        .append(ASSIGN).append(arrayType.replace(".array", ""))
                        .append(SPACE).append("getfield(this, ").append(array.get("name")).append(".array").append(arrayType).append(")")
                        .append(".array").append(arrayType).append(END_STMT);

                String indexTemp = OptUtils.getTemp();
                computation.append(indexTemp).append(".i32").append(SPACE)
                        .append(ASSIGN).append(".i32")
                        .append(SPACE).append(fieldTempNum)
                        .append("[")
                        .append(indexVisit.getCode().replace(".array", ""))
                        .append("]")
                        .append(arrayType)
                        .append(END_STMT);

                code.append(indexTemp).append(".i32");

            }
            else {
                String temp = OptUtils.getTemp() + arrayType;

                computation.append(temp).append(SPACE)
                        .append(ASSIGN).append(arrayType.replace(".array", ""))
                        .append(SPACE).append(array.get("name")).append("[")
                        .append(indexVisit.getCode().replace("array", ""))
                        .append("]")
                        .append(arrayType).append(END_STMT);

                code.append(temp);
            }
        }
        else {
            if (isField) {
                String fieldTempNum = OptUtils.getTemp();
                String temp = fieldTempNum + ".array" + arrayType;

                computation.append(temp).append(SPACE)
                        .append(ASSIGN).append(arrayType.replace(".array", ""))
                        .append(SPACE).append("getfield(this, ").append(array.get("name")).append(".array").append(arrayType).append(")")
                        .append(".array").append(arrayType).append(END_STMT);

                code.append(fieldTempNum).append("[").append(indexVisit.getCode().replace(".array", "")).append("]").append(arrayType);
            }
            else {
                code.append(array.get("name")).append("[")
                    .append(indexVisit.getCode().replace(".array", ""))
                    .append("]")
                    .append(arrayType);
            }
        }

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitArrayLengthExpr(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

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

    private OllirExprResult visitArrayInitExpr(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        JmmNode elem = node.getJmmChild(0);

        String type = OptUtils.toOllirType(TypeUtils.getExprType(elem, table));

        String temp = OptUtils.getTemp() + ".array" + type;

        computation.append(temp).append(SPACE)
                .append(ASSIGN).append(".array").append(type)
                .append(SPACE).append("new(array, ")
                .append(node.getNumChildren()).append(".i32)").append(".array").append(type)
                .append(END_STMT);

        int varArgsNum = OptUtils.getNextVarArgsNum();

        computation.append("__varargs_array_").append(varArgsNum).append(".array").append(type)
                .append(SPACE)
                .append(ASSIGN).append(".array" + type)
                .append(SPACE).append(temp)
                .append(END_STMT);

        for (int i = 0; i < node.getNumChildren(); i++) {
            JmmNode child = node.getJmmChild(i);

            var childVisit = visit(child);

            computation.append(childVisit.getComputation());

            computation.append("__varargs_array_").append(varArgsNum)
                    .append(".array").append(type)
                    .append("[").append(i).append(".i32].i32 :=.i32 ")
                    .append(childVisit.getCode()).append(END_STMT);
        }

            code.append("__varargs_array_")
                    .append(varArgsNum)
                    .append(".array").append(type);



        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult computeVarArg(List<JmmNode> nodes) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        String type = (nodes.isEmpty()) ? ".i32" : OptUtils.toOllirType(TypeUtils.getExprType(nodes.get(0), table));

        String temp = OptUtils.getTemp() + ".array" + type;

        computation.append(temp).append(SPACE)
                .append(ASSIGN).append(".array").append(type)
                .append(SPACE).append("new(array, ")
                .append(nodes.size()).append(".i32)").append(".array").append(type)
                .append(END_STMT);

        if (nodes.isEmpty()) {
            return new OllirExprResult(temp, computation.toString());
        }

        int varArgsNum = OptUtils.getNextVarArgsNum();

        computation.append("__varargs_array_").append(varArgsNum).append(".array").append(type)
                .append(SPACE)
                .append(ASSIGN).append(".array").append(type)
                .append(SPACE).append(temp)
                .append(END_STMT);

        for (JmmNode node : nodes) {
            var childVisit = visit(node);
            computation.append(childVisit.getComputation());

            computation.append("__varargs_array_").append(varArgsNum)
                    .append(".array").append(type)
                    .append("[").append(nodes.indexOf(node)).append(".i32].i32 :=.i32 ")
                    .append(childVisit.getCode()).append(END_STMT);

        }

        return new OllirExprResult("__varargs_array_" + varArgsNum + ".array" + type, computation.toString());
    }
}
