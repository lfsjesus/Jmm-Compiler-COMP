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
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(CallInstruction.class, this::generateInstructionForMethodCall);
        generators.put(Field.class, this::generateField);
        generators.put(GetFieldInstruction.class, this::generateGetFieldInstruction);
        generators.put(PutFieldInstruction.class, this::generatePutFieldInstruction);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }

    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        var classAccessModifier = classUnit.getClassAccessModifier() != AccessModifier.DEFAULT ?
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
        var packageName = classUnit.getPackage();
        if (packageName != null) {
            className = packageName + '/';
        }
        className += classUnit.getClassName();
        code.append(className).append(NL);

        code.append(".super ");
        var superClass = classUnit.getSuperClass();
        if (superClass == null) {
            superClass="java/lang/Object";
        }
        code.append(superClass).append(NL).append(NL);

        for (var field : classUnit.getFields()) {
            code.append(generators.apply(field)).append(NL);
        }

        // generate a single constructor method
        var defaultConstructor = String.format("""
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """, superClass);
        code.append(NL).append(defaultConstructor);

        // generate code for all other methods
        for (var method : classUnit.getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }

    private String generateMethod(Method method) {

        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";
        code.append(NL).append(".method ").append(modifier);
        if (method.isStaticMethod()) {
            code.append("static ");
        }
        if (method.isFinalMethod()) {
            code.append("final ");
        }

        var methodName = method.getMethodName();
        code.append(methodName);

        code.append('(');
        for (var param : method.getParams()) {
            code.append(toJvmTypeDescriptor(param.getType()));
        }
        var returnType = method.getReturnType();
        code.append(')').append(toJvmTypeDescriptor(returnType)).append(NL);

        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }

        code.append(".end method").append(NL);

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        // TODO: Hardcoded for int type, needs to be expanded
        String storeInstruction = switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> "istore ";
            case OBJECTREF, ARRAYREF, STRING, CLASS -> "astore ";
            default -> throw new IllegalArgumentException("Unsupported operand type");
        };
        code.append(storeInstruction).append(reg).append(NL);

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
        StringBuilder code = new StringBuilder();

        // Append the operand if there is a return value
        if (returnInst.hasReturnValue()) {
            code.append(generators.apply(returnInst.getOperand()));
        }

        // Determine the return instruction based on the return type
        String returnInstruction = switch (returnInst.getElementType()) {
            case INT32, BOOLEAN -> "ireturn";
            case OBJECTREF, ARRAYREF, STRING, CLASS -> "areturn";
            case VOID -> "return";
            // Include a default case to handle unexpected types safely
            default -> "return"; // or throw an exception depending on the context
        };

        // Append the return instruction with a newline
        code.append(returnInstruction).append(NL);

        return code.toString();
    }

    private String toJvmTypeDescriptor(Type type) {
        return switch (type.getTypeOfElement()) {
            case VOID -> "V";
            case INT32 -> "I";
            case BOOLEAN -> "Z";
            case STRING -> "java/lang/String";
            case CLASS -> ((ClassType) type).getName();
            case ARRAYREF -> generateArrayTypeDescriptor((ArrayType) type);
            default -> "";
        };
    }

    private String generateArrayTypeDescriptor(ArrayType arrayType) {
        String dimensions = "[".repeat(arrayType.getNumDimensions());
        String elementTypeDescriptor = toJvmTypeDescriptor(arrayType.getElementType());
        // If elementTypeDescriptor starts with 'L' or '[', it's an object or another array;
        // Otherwise, it's a primitive type and doesn't need 'L' and ';' around it.
        return dimensions + (elementTypeDescriptor.startsWith("L") || elementTypeDescriptor.startsWith("[") ? "" : "L")
                + elementTypeDescriptor + (elementTypeDescriptor.startsWith("L") || elementTypeDescriptor.startsWith("[") ? "" : ";");
    }

    private String generateField(Field field) {
        String modifier = field.getFieldAccessModifier() != AccessModifier.DEFAULT
                ? field.getFieldAccessModifier().name().toLowerCase() + " " : "";
        String modifiers = modifier + (field.isStaticField() ? "static " : "") + (field.isFinalField() ? "final " : "");
        String typeDescriptor = toJvmTypeDescriptor(field.getFieldType());
        String initialValue = field.isInitialized() ? " = " + field.getInitialValue() : "";

        return ".field " + modifiers + field.getFieldName() + ' ' + typeDescriptor + initialValue + "\n";
    }

    private String generateGetFieldInstruction(GetFieldInstruction getFieldInstruction) {
        String objectCode = generators.apply(getFieldInstruction.getObject());
        String fieldRef = getFieldReference(getFieldInstruction.getObject(), getFieldInstruction.getField());
        String fieldTypeDescriptor = toJvmTypeDescriptor(getFieldInstruction.getField().getType());

        return objectCode + "getfield " + fieldRef + ' ' + fieldTypeDescriptor + "\n";
    }

    private String generatePutFieldInstruction(PutFieldInstruction putFieldInstruction) {
        String objectCode = generators.apply(putFieldInstruction.getObject());
        String valueCode = generators.apply(putFieldInstruction.getValue());
        String fieldRef = getFieldReference(putFieldInstruction.getObject(), putFieldInstruction.getField());
        String fieldTypeDescriptor = toJvmTypeDescriptor(putFieldInstruction.getField().getType());

        return objectCode + valueCode + "putfield " + fieldRef + ' ' + fieldTypeDescriptor + "\n";
    }

    private String getFieldReference(Operand object, Operand field) {
        return (Objects.equals(object.getName(), "this") ? className + '/' : "") + field.getName();
    }


    private String generateInstructionForMethodCall(CallInstruction callInstruction) {
        StringBuilder code = new StringBuilder();

        // Append arguments
        callInstruction.getArguments().forEach(arg -> code.append(generators.apply(arg)));

        // Handle the "new" call type separately
        if (callInstruction.getInvocationType() == CallType.NEW) {
            return handleNewCallType(code, callInstruction);
        }

        // Handle other call types
        return handleOtherCallTypes(code, callInstruction);
    }

    private String handleNewCallType(StringBuilder code, CallInstruction callInstruction) {
        code.append("new ")
                .append(((Operand) callInstruction.getCaller()).getName())
                .append(NL);
        return code.toString();
    }

    private String handleOtherCallTypes(StringBuilder code, CallInstruction callInstruction) {
        code.append(generators.apply(callInstruction.getCaller()))
                .append(callInstruction.getInvocationType().name()).append(' ')
                .append(getClassAndMethodName(callInstruction));

        // Append method signature
        String methodSignature = callInstruction.getArguments().stream()
                .map(arg -> toJvmTypeDescriptor(arg.getType()))
                .collect(Collectors.joining("", "(", ")"))
                + toJvmTypeDescriptor(callInstruction.getReturnType());

        code.append(methodSignature).append(NL);
        return code.toString();
    }

    private String getClassAndMethodName(CallInstruction callInstruction) {
        String className = ((ClassType) callInstruction.getOperands().get(0).getType()).getName();
        String methodName = ((LiteralElement) callInstruction.getMethodName()).getLiteral().replace("\"", "");
        return className + '/' + methodName;
    }


}





