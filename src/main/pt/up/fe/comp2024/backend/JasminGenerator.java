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
        // Basic elements
        generators.put(ClassUnit.class, this::generateClassCode);
        generators.put(Field.class, this::generateFieldDeclarationCode);
        generators.put(Method.class, this::generateMethodSignatureAndBodyCode);
        generators.put(Operand.class, this::generateLoadOperandCode);
        generators.put(LiteralElement.class, this::generateLiteralElementCode);

        // Instruction handling
        generators.put(AssignInstruction.class, this::generateAssignmentInstrCode);
        generators.put(CallInstruction.class, this::generateCallInstrCode);
        generators.put(SingleOpInstruction.class, this::generateSingleOpInstrCode);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOperationInstrCode);
        generators.put(ReturnInstruction.class, this::generateReturnInstrCode);

        // Field access instructions
        generators.put(GetFieldInstruction.class, this::generateGetFieldInstrCode);
        generators.put(PutFieldInstruction.class, this::generatePutFieldInstrCode);
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

    // Class generation
    private String generateClassCode(ClassUnit classUnit) {
        StringBuilder code = new StringBuilder();

        appendClassDeclaration(code, classUnit);
        appendSuperClass(code, classUnit);

        // Field generation
        generateFields(code, classUnit);

        // Default constructor
        generateDefaultConstructor(code, classUnit);

        // Methods generation
        generateMethods(code, classUnit);

        return code.toString();
    }

    // Helper methods for class generation
    private void appendClassDeclaration(StringBuilder code, ClassUnit classUnit) {
        // Start with the base declaration
        code.append(".class ");

        // Use switch-case to determine the access modifier text
        switch (classUnit.getClassAccessModifier()) {
            case PUBLIC:
                code.append("public ");
                break;
            case PRIVATE:
                code.append("private ");
                break;
            case PROTECTED:
                code.append("protected ");
                break;
            case DEFAULT:
                // No modifier needed, skip
                break;
        }

        // Check for static and final modifiers
        if (classUnit.isStaticClass()) {
            code.append("static ");
        }
        if (classUnit.isFinalClass()) {
            code.append("final ");
        }

        // Append the fully qualified class name
        String className = classUnit.getClassName();
        String packageName = classUnit.getPackage();
        if (packageName != null && !packageName.isEmpty()) {
            className = packageName + '/' + className;
        }

        // Finish the declaration
        code.append(className).append(NL);
    }


    private void appendSuperClass(StringBuilder code, ClassUnit classUnit) {
        String superClass = classUnit.getSuperClass();
        if (superClass == null || superClass.equals("Object")) {
            superClass = "java/lang/Object";
        }
        superClass = generateFullName(superClass);
        code.append(".super ").append(superClass).append(NL).append(NL);
    }

    private void generateFields(StringBuilder code, ClassUnit classUnit) {
        for (Field field : classUnit.getFields()) {
            code.append(generators.apply(field)).append(NL);
        }
    }

    private void generateDefaultConstructor(StringBuilder code, ClassUnit classUnit) {
        String superClass = classUnit.getSuperClass();
        if (superClass == null || superClass.equals("Object")) {
            superClass = "java/lang/Object";
        }
        superClass = generateFullName(superClass);
        String defaultConstructor = String.format("""
            ;default constructor
            .method public <init>()V
                aload_0
                invokespecial %s/<init>()V
                return
            .end method
            """, superClass);
        code.append(NL).append(defaultConstructor);
    }

    // Method generation
    private void generateMethods(StringBuilder code, ClassUnit classUnit) {
        for (Method method : classUnit.getMethods()) {
            if (!method.isConstructMethod()) {  // assuming constructor method means default constructor
                code.append(generators.apply(method));
            }
        }
    }

    // Field generation
    private String generateFieldDeclarationCode(Field field) {
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
        String typeDescriptor = generateJasminType(field.getFieldType());
        code.append(fieldName).append(' ').append(typeDescriptor);

        if (field.isInitialized()) {
            int initialValue = field.getInitialValue();
            code.append(" = ").append(initialValue);
        }

        return code.toString();
    }

    // Field access instructions
    private void appendClassNameIfThisReference(StringBuilder code, String objectName) {
        if (Objects.equals(objectName, "this")) {
            code.append(className).append('/');
        }
    }

    // Field access instructions
    private void generateFieldAccessCode(StringBuilder code, FieldInstruction fieldInstruction) {
        appendClassNameIfThisReference(code, fieldInstruction.getObject().getName());
        code.append(fieldInstruction.getField().getName()).append(' ');
        code.append(generateJasminType(fieldInstruction.getField().getType())).append(NL);
    }

    // Instruction generation
    private String generateGetFieldInstrCode(GetFieldInstruction getFieldInstruction) {
        StringBuilder code = new StringBuilder();
        code.append(generators.apply(getFieldInstruction.getObject()));

        code.append("getfield ");
        generateFieldAccessCode(code, getFieldInstruction);

        return code.toString();
    }

    // Instruction generation
    private String generatePutFieldInstrCode(PutFieldInstruction putFieldInstruction) {
        StringBuilder code = new StringBuilder();
        code.append(generators.apply(putFieldInstruction.getObject()));
        code.append(generators.apply(putFieldInstruction.getValue()));

        code.append("putfield ");
        generateFieldAccessCode(code, putFieldInstruction);

        return code.toString();
    }

    // Instruction generation
    private String generateAssignmentInstrCode(AssignInstruction assign) {
        StringBuilder code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        Element lhs = assign.getDest();

        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        // get register
        int reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> code.append("istore ").append(reg).append(NL);
            case OBJECTREF, ARRAYREF, STRING, CLASS -> code.append("astore ").append(reg).append(NL);
        }

        return code.toString();
    }

    private void appendMethodSignature(StringBuilder code, Method method) {
        String modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " : "";
        code.append(NL).append(".method ").append(modifier);
        if (method.isStaticMethod()) code.append("static ");
        if (method.isFinalMethod()) code.append("final ");
        code.append(method.getMethodName()).append('(');
        method.getParams().forEach(param -> code.append(generateJasminType(param.getType())));
        code.append(')').append(generateJasminType(method.getReturnType())).append(NL);
    }

    private void appendMethodBody(StringBuilder code, Method method) {
        appendStackAndLocalsLimits(code); // Example of extracting stack and locals setting
        method.getInstructions().forEach(inst -> appendInstruction(code, inst));
        code.append(".end method").append(NL);
    }

    private void appendStackAndLocalsLimits(StringBuilder code) {
        code.append(TAB).append(".limit stack ").append(99).append(NL);
        code.append(TAB).append(".limit locals ").append(99).append(NL);
    }
    private void appendInstruction(StringBuilder code, Instruction inst) {
        String instCode = StringLines.getLines(generators.apply(inst)).stream()
                .collect(Collectors.joining(NL + TAB, TAB, NL));
        code.append(instCode);
        handleNonVoidReturn(inst, code);
    }

    private void handleNonVoidReturn(Instruction inst, StringBuilder code) {
        if (inst instanceof CallInstruction && !((CallInstruction) inst).getReturnType().getTypeOfElement().equals(ElementType.VOID)) {
            if (((CallInstruction) inst).getInvocationType() != CallType.NEW) {
                code.append("pop").append(NL);
            }
        }
    }

    private String generateMethodSignatureAndBodyCode(Method method) {
        StringBuilder code = new StringBuilder();
        currentMethod = method;

        // Append the method signature using the refactored method
        appendMethodSignature(code, method);

        // Append the method body including instructions and stack limits using the refactored methods
        appendMethodBody(code, method);

        // Unset the current method context after completion
        currentMethod = null;

        return code.toString();
    }


    private String generateLoadOperandCode(Operand operand) {
        // get register
        int reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        return switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> "iload " + reg + NL;
            case THIS, OBJECTREF, ARRAYREF, STRING, CLASS -> "aload " + reg + NL;
            default -> "";
        };
    }

    private String generateBinaryOperationInstrCode(BinaryOpInstruction binaryOp) {
        StringBuilder code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        String op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case SUB -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateReturnInstrCode(ReturnInstruction returnInst) {
        StringBuilder code = new StringBuilder();  // Explicitly declare as StringBuilder

        if (returnInst.hasReturnValue()) {
            code.append(generators.apply(returnInst.getOperand()));
        }

        ElementType returnType = returnInst.getElementType();  // Explicitly declare as ElementType
        switch (returnType) {
            case VOID -> code.append("return").append(NL);
            case INT32, BOOLEAN -> code.append("ireturn").append(NL);
            case OBJECTREF, ARRAYREF, STRING, CLASS -> code.append("areturn").append(NL);
        }

        return code.toString();
    }


    private String generateCallInstrCode(CallInstruction callInstruction) {
        StringBuilder code = new StringBuilder();

        CallType callType = callInstruction.getInvocationType();
        switch (callType) {
            case invokevirtual:
                code.append(generators.apply(callInstruction.getCaller()));
                // Fall through to append arguments
            case invokestatic:
            case invokespecial:
                for (Element operand : callInstruction.getArguments()) {
                    code.append(generators.apply(operand));
                }

                if (callType == CallType.invokespecial) {
                    code.append(generators.apply(callInstruction.getCaller()));
                }

                String className = (callType == CallType.invokestatic) ?
                        generateFullName(((Operand) callInstruction.getCaller()).getName()) :
                        generateFullName(((ClassType) callInstruction.getCaller().getType()).getName());

                code.append(callType.name()).append(' ');
                code.append(className).append('/');
                String methodName = ((LiteralElement) callInstruction.getMethodName()).getLiteral().replace("\"", "");
                code.append(methodName);
                code.append('(');
                for (Element arg : callInstruction.getArguments()) {
                    code.append(generateJasminType(arg.getType()));
                }
                Type returnType = callInstruction.getReturnType();
                code.append(')').append(generateJasminType(returnType)).append(NL);
                break;

            case NEW:
                code.append(callType.name().toLowerCase()).append(' ');
                Operand operand = (Operand) callInstruction.getCaller();
                code.append(operand.getName()).append(NL);
                break;

            default:
                // Optionally handle unexpected cases
                throw new IllegalStateException("Unsupported invocation type: " + callType);
        }

        return code.toString();
    }


    private String generateSingleOpInstrCode(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteralElementCode(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }



    private String generateJasminType(Type type) {
        ElementType elementType = type.getTypeOfElement();
        return switch (elementType) {
            case VOID -> "V";
            case INT32 -> "I";
            case BOOLEAN -> "Z";
            case STRING -> "Ljava/lang/String;";
            case OBJECTREF, CLASS -> "L" + generateFullName(((ClassType) type).getName()) + ";";
            case ARRAYREF -> {
                StringBuilder code = new StringBuilder();
                ArrayType arrayType = (ArrayType) type;

                int numDimensions = arrayType.getNumDimensions();
                code.append("[".repeat(numDimensions));

                code.append(generateJasminType(arrayType.getElementType()));
                yield code.toString();
            }
            default -> "";
        };
    }

    private String generateFullName(String name) {

        List<String> imports = ollirResult.getOllirClass().getImports();
        for (String imp : imports) {
            String impClassName = imp.substring(imp.lastIndexOf('.') + 1);
            if (impClassName.equals(name)) {
                return imp.replace('.', '/');
            }
        }
        return name;
    }

}