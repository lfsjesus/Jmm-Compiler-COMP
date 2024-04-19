package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    String className;

    Method currentMethod;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;
        className = "";

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Field.class, this::generateField);
        generators.put(GetFieldInstruction.class, this::generateGetFieldInst);
        generators.put(PutFieldInstruction.class, this::generatePutFieldInst);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(CallInstruction.class, this::generateCallInst);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        StringBuilder code = new StringBuilder();

        String classAccessModifier = classUnit.getClassAccessModifier() != AccessModifier.DEFAULT ?
                classUnit.getClassAccessModifier().name().toLowerCase() + " " :
                "";
        code.append(".class ").append(classAccessModifier);
        if (classUnit.isStaticClass()) {
            code.append("static ");
        }
        if (classUnit.isFinalClass()) {
            code.append("final ");
        }

        // generate class name
        String packageName = classUnit.getPackage();
        if (packageName != null) {
            className = packageName + '/';
        }

        className += classUnit.getClassName();
        code.append(className).append(NL);

        code.append(".super ");
        String superClass = classUnit.getSuperClass();
        if (superClass == null || superClass.equals("Object")) {
            superClass="java/lang/Object";
        }
        superClass = generateFullyQualified(superClass);
        code.append(superClass).append(NL).append(NL);

        for (Field field : classUnit.getFields()) {
            code.append(generators.apply(field)).append(NL);
        }

        // generate a single constructor method
        String defaultConstructor = String.format("""
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """, superClass);
        code.append(NL).append(defaultConstructor);

        for (Method method : classUnit.getMethods()) {
            // Ignore constructor because it was already generated
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }

    private String generateField(Field field) {
        StringBuilder code = new StringBuilder();

        String modifier = field.getFieldAccessModifier() != AccessModifier.DEFAULT ?
                field.getFieldAccessModifier().name().toLowerCase() + " " :
                "";
        code.append(".field ").append(modifier);
        if (field.isStaticField()) {
            code.append("static ");
        }
        if (field.isFinalField()) {
            code.append("final ");
        }

        String fieldName = field.getFieldName();
        String typeDescriptor = generateTypeDescriptor(field.getFieldType());
        code.append(fieldName).append(' ').append(typeDescriptor);

        if (field.isInitialized()) {
            int initialValue = field.getInitialValue();
            code.append(" = ").append(initialValue);
        }

        return code.toString();
    }

    private String generateMethod(Method method) {
        currentMethod = method;

        StringBuilder code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        code.append(NL).append(".method ").append(modifier);

        // This will only happen with 'main' in Java--
        if (method.isStaticMethod()) {
            code.append("static ");
        }
        if (method.isFinalMethod()) {
            code.append("final ");
        }

        String methodName = method.getMethodName();
        code.append(methodName);

        code.append('(');
        for (Element param : method.getParams()) {
            code.append(generateTypeDescriptor(param.getType()));
        }

        Type returnType = method.getReturnType();
        code.append(')').append(generateTypeDescriptor(returnType)).append(NL);

        // Stack and locals limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (Instruction inst : method.getInstructions()) {
            String instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));


            // if we call a mmethod like this.int() it puts things on the stack
            // so we need to pop them
            // we need to make sure it is not an assign instruction
            if (inst instanceof CallInstruction) {
                var callInst = (CallInstruction) inst;
                if (callInst.getInvocationType() == CallType.invokevirtual) {
                    instCode += "pop" + NL;
                }
            }


            code.append(instCode);
        }

        code.append(".end method").append(NL);

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateGetFieldInst(GetFieldInstruction getFieldInstruction) {
        StringBuilder code = new StringBuilder();
        code.append(generators.apply(getFieldInstruction.getObject()));

        code.append("getfield ");
        if (Objects.equals(getFieldInstruction.getObject().getName(), "this")) {
            code.append(className).append('/');
        }
        code.append(getFieldInstruction.getField().getName()).append(' ');

        Type fieldType = getFieldInstruction.getField().getType();
        code.append(generateTypeDescriptor(fieldType)).append(NL);

        return code.toString();
    }

    private String generatePutFieldInst(PutFieldInstruction putFieldInstruction) {
        StringBuilder code = new StringBuilder();
        code.append(generators.apply(putFieldInstruction.getObject()));

        code.append(generators.apply(putFieldInstruction.getValue()));

        code.append("putfield ");
        if (Objects.equals(putFieldInstruction.getObject().getName(), "this")) {
            code.append(className).append('/');
        }
        code.append(putFieldInstruction.getField().getName()).append(' ');

        Type fieldType = putFieldInstruction.getField().getType();
        code.append(generateTypeDescriptor(fieldType)).append(NL);

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        StringBuilder code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        Element lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> code.append("istore ").append(reg).append(NL);
            case OBJECTREF, ARRAYREF, STRING, CLASS -> code.append("astore ").append(reg).append(NL);
        }

        return code.toString();
    }

    private String generateCallInst(CallInstruction callInstruction) {
        var code = new StringBuilder();

        var callType = callInstruction.getInvocationType();
        if (callType == CallType.invokevirtual) {
            code.append(generators.apply(callInstruction.getCaller()));
        }

        for (var operand : callInstruction.getArguments()) {
            code.append(generators.apply(operand));
        }

        if (callType == CallType.NEW) {
            code.append(callType.name().toLowerCase()).append(' ');
            var operand = (Operand) callInstruction.getCaller();
            code.append(operand.getName()).append(NL);
        }
        else {
            String className = callType == CallType.invokestatic ?
                    generateFullyQualified(((Operand) callInstruction.getCaller()).getName()) :
                    generateFullyQualified(((ClassType) callInstruction.getCaller().getType()).getName());

            if (callType == CallType.invokespecial) {
                code.append(generators.apply(callInstruction.getCaller()));
            }
            code.append(callType.name()).append(' ');
            code.append(className).append('/');
            var name = ((LiteralElement) callInstruction.getMethodName()).getLiteral().replace("\"", "");
            code.append(name);
            code.append('(');
            for (var arg : callInstruction.getArguments()) {
                code.append(generateTypeDescriptor(arg.getType()));
            }
            var returnType = callInstruction.getReturnType();
            code.append(')').append(generateTypeDescriptor(returnType)).append(NL);
        }

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        return switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> "iload " + reg + NL;
            case THIS, OBJECTREF, ARRAYREF, STRING, CLASS -> "aload " + reg + NL;
            default -> "";
        };
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case SUB -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        if (returnInst.hasReturnValue()) {
            code.append(generators.apply(returnInst.getOperand()));
        }

        var returnType = returnInst.getElementType();
        switch (returnType) {
            case VOID -> code.append("return").append(NL);
            case INT32, BOOLEAN -> code.append("ireturn").append(NL);
            case OBJECTREF, ARRAYREF, STRING, CLASS -> code.append("areturn").append(NL);
        }

        return code.toString();
    }

    private String generateTypeDescriptor(Type type) {
        var elementType = type.getTypeOfElement();
        return switch (elementType) {
            case VOID -> "V";
            case INT32 -> "I";
            case BOOLEAN -> "Z";
            case STRING -> "Ljava/lang/String;";
            case OBJECTREF, CLASS -> "L" + generateFullyQualified(((ClassType) type).getName()) + ";";
            case ARRAYREF -> {
                var code = new StringBuilder();
                var arrayType = (ArrayType) type;

                var numDimensions = arrayType.getNumDimensions();
                code.append("[".repeat(numDimensions));

                code.append(generateTypeDescriptor(arrayType.getElementType()));
                yield code.toString();
            }
            default -> "";
        };
    }

    private String generateFullyQualified(String name) {
        var imports = ollirResult.getOllirClass().getImports();
        for (var imp : imports) {
            String impClassName = imp.substring(imp.lastIndexOf('.') + 1);
            if (impClassName.equals(name)) {
                return imp.replace('.', '/');
            }
        }
        return name;
    }

}