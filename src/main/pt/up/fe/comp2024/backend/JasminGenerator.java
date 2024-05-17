package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.*;
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

    int limitLocals;
    int limitStack;
    int currentStack;
    int numArgs;

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
        generators.put(UnaryOpInstruction.class, this::generateUnaryOpInstrCode);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCondInstrCode);
        generators.put(OpCondInstruction.class, this::generateOpCondInstrCode);
        generators.put(GotoInstruction.class, this::generateGoToInstrCode);

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
        String classVisibility = "";
        if (!classUnit.getClassAccessModifier().equals(AccessModifier.DEFAULT)) {
            classVisibility = classUnit.getClassAccessModifier().name().toLowerCase() + " ";
        }


        code.append(".class ").append(classVisibility);
        if (classUnit.isStaticClass()) {
            code.append("static ");
        }
        if (classUnit.isFinalClass()) {
            code.append("final ");
        }
        if (classUnit.getPackage() != null) {
            className = classUnit.getPackage() + '/';
        }
        code.append(className += classUnit.getClassName()).append(NL);
    }

    private void appendSuperClass(StringBuilder code, ClassUnit classUnit) {
        String superName = classUnit.getSuperClass();
        superName = (superName == null || superName.equals("Object")) ? "java/lang/Object" : superName;
        code.append(".super ").append(generateFullName(superName)).append(NL).append(NL);
    }

    private void generateFields(StringBuilder code, ClassUnit classUnit) {
        classUnit.getFields().stream()
                .map(field -> generators.apply(field) + NL)
                .forEach(code::append);
    }

    private void generateDefaultConstructor(StringBuilder code, ClassUnit classUnit) {
        String superName = classUnit.getSuperClass();
        superName = (superName == null || superName.equals("Object")) ? "java/lang/Object" : superName;
        String defaultConstructor = String.format("""
            ;default constructor
            .method public <init>()V
                aload_0
                invokespecial %s/<init>()V
                return
            .end method
            """, generateFullName(superName));


        code.append(NL).append(defaultConstructor);
    }

    // Method generation
    private void generateMethods(StringBuilder code, ClassUnit classUnit) {
        classUnit.getMethods().stream()
                .filter(method -> !method.isConstructMethod())  // Filter out constructor methods
                .map(generators::apply)  // Apply the generator function to each method
                .forEach(code::append);  // Append each generated string to the StringBuilder
    }

    // Field generation
    private String generateFieldDeclarationCode(Field field) {
        StringBuilder code = new StringBuilder();
        String name = field.getFieldName();
        String jasminType = generateJasminType(field.getFieldType());
        String visibility = "";

        if (!field.getFieldAccessModifier().equals(AccessModifier.DEFAULT)) {
            visibility = field.getFieldAccessModifier().name().toLowerCase() + " ";
        }
        code.append(".field ").append(visibility);

        if (field.isStaticField()) {
            code.append("static ");
        }
        if (field.isFinalField()) {
            code.append("final ");
        }
        code.append(name).append(' ').append(jasminType);

        if (field.isInitialized()) {
            code.append(" = ").append(field.getInitialValue());
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
    private String generateGetFieldInstrCode(GetFieldInstruction getFieldInstr) {
        StringBuilder code = new StringBuilder();
        code.append(generators.apply(getFieldInstr.getObject()));

        code.append("getfield ");
        generateFieldAccessCode(code, getFieldInstr);

        return code.toString();
    }

    // Instruction generation
    private String generatePutFieldInstrCode(PutFieldInstruction putFieldInstr) {
        StringBuilder code = new StringBuilder();
        this.decrementStack(2);
        code.append(generators.apply(putFieldInstr.getObject()));
        code.append(generators.apply(putFieldInstr.getValue()));


        code.append("putfield ");
        generateFieldAccessCode(code, putFieldInstr);

        return code.toString();
    }

    // Instruction generation
    private String generateAssignmentInstrCode(AssignInstruction assign) {
        StringBuilder code = new StringBuilder();


        Element lhs = assign.getDest();

        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        if (lhs instanceof ArrayOperand) {
            // load arrayRef
            int register = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
            this.incrementStack(1);

            code.append("aload").append(register > 3 ? " " : "_").append(register).append(NL);

            // load index
            code.append(generators.apply(((ArrayOperand) lhs).getIndexOperands().get(0)));
            // load value
            code.append(generators.apply(assign.getRhs()));

            code.append("iastore").append(NL);
            this.decrementStack(3);

            return code.toString();
        }
        int register = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        // use iinc if incrementing a variable


        if (assign.getRhs().getInstType().equals(InstructionType.BINARYOPER)) {
            BinaryOpInstruction binaryOp = (BinaryOpInstruction) assign.getRhs();
            if (binaryOp.getOperation().getOpType().equals(OperationType.ADD)) {
                boolean literalLeft = binaryOp.getLeftOperand().isLiteral();
                boolean literalRight = binaryOp.getRightOperand().isLiteral();

                // if one of the operands is a literal, we can use iinc
                if ((literalLeft != literalRight)) {
                    Operand variable = literalLeft ? (Operand) binaryOp.getRightOperand() : (Operand) binaryOp.getLeftOperand();

                    if (variable.getName().equals(((Operand) assign.getDest()).getName())) {
                        int increment = Integer.parseInt(literalLeft ? ((LiteralElement) binaryOp.getLeftOperand()).getLiteral() : ((LiteralElement) binaryOp.getRightOperand()).getLiteral());
                        if (increment <= 127 && increment >= -128) {
                            code.append("iinc ").append(register).append(' ').append(increment).append(NL);
                            return code.toString();
                        }
                    }
                }

            }
        }

        code.append(generators.apply(assign.getRhs()));


        switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> {
                this.decrementStack(1);
                code.append("istore").append(register > 3 ? " " : "_").append(register).append(NL);
            }
            case OBJECTREF, ARRAYREF, STRING, CLASS -> {
                this.decrementStack(1);
                code.append("astore").append(register > 3 ? " " : "_").append(register).append(NL);

            }
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

        //method.getInstructions().forEach(inst -> appendInstruction(code, inst));

        StringBuilder methodCode = new StringBuilder();
        this.limitLocals = computeLimitLocals(method);
        this.limitStack = 0;
        this.currentStack = 0;

        for (Instruction inst : method.getInstructions()) {
            for (Map.Entry<String, Instruction> label : method.getLabels().entrySet()) {
                if (inst.equals(label.getValue())) {
                    methodCode.append(label.getKey()).append(':').append(NL);
                }
            }
            appendInstruction(methodCode, inst);
        }

        appendStackAndLocalsLimits(code, limitLocals, limitStack);
        code.append(methodCode);

        code.append(".end method").append(NL);
    }

    private void appendStackAndLocalsLimits(StringBuilder code, int limitLocals, int limitStack) {
        code.append(TAB).append(".limit stack ").append(limitStack).append(NL);
        code.append(TAB).append(".limit locals ").append(limitLocals).append(NL);
    }
    private void appendInstruction(StringBuilder code, Instruction inst) {
        String instruction = StringLines.getLines(generators.apply(inst)).stream()
                .collect(Collectors.joining(NL + TAB, TAB, NL));
        code.append(instruction);
        handlePopAfterInvoke(inst, code);
    }

    private void handlePopAfterInvoke(Instruction inst, StringBuilder code) {
        if (inst instanceof CallInstruction && !((CallInstruction) inst).getReturnType().getTypeOfElement().equals(ElementType.VOID)) {
            if (((CallInstruction) inst).getInvocationType() != CallType.NEW) {
                code.append("pop").append(NL);
                this.decrementStack(1);
            }
        }
    }

    private String generateMethodSignatureAndBodyCode(Method method) {
        StringBuilder code = new StringBuilder();
        currentMethod = method;
        appendMethodSignature(code, method);
        appendMethodBody(code, method);
        currentMethod = null;
        return code.toString();
    }


    private String generateLoadOperandCode(Operand operand) {
        int register = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        // if we put "astore", we load with "aload"
        if (operand instanceof ArrayOperand) {
            StringBuilder code = new StringBuilder();
            this.incrementStack(1);

            code.append("aload ").append(register).append(NL);
            code.append(generators.apply(((ArrayOperand) operand).getIndexOperands().get(0)));
            code.append("iaload").append(NL);
            this.decrementStack(1);
            return code.toString();

        }

        this.incrementStack(1);

        return switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> "iload" + (register > 3 ? " " : "_") + register + NL;
            case THIS, OBJECTREF, ARRAYREF, STRING, CLASS -> "aload" + (register > 3 ? " " : "_") + register + NL;
            default -> "";
        };
    }


    private String generateBinaryOperationInstrCode(BinaryOpInstruction binaryOp) {
        StringBuilder code = new StringBuilder();
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));
        String op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case SUB -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        this.decrementStack(1);

        return code.toString();
    }

    private String generateReturnInstrCode(ReturnInstruction instruction) {
        StringBuilder code = new StringBuilder();  // Explicitly declare as StringBuilder

        if (instruction.hasReturnValue()) {
            code.append(generators.apply(instruction.getOperand()));
        }

        ElementType type = instruction.getElementType();
        switch (type) {
            case VOID -> code.append("return").append(NL);
            case INT32, BOOLEAN -> code.append("ireturn").append(NL);
            case OBJECTREF, ARRAYREF, STRING, CLASS -> code.append("areturn").append(NL);
        }

        return code.toString();
    }


    private String generateCallInstrCode(CallInstruction instruction) {
        StringBuilder code = new StringBuilder();

        CallType invocationType = instruction.getInvocationType();

        this.numArgs = 0;

        switch (invocationType) {
            case invokevirtual:
                code.append(generators.apply(instruction.getCaller()));
                this.numArgs = 1;
                for (Element elem : instruction.getOperands()) {
                    this.numArgs += 1;
                }
                // Fall through to append arguments
            case invokestatic:
                // Ensure this doesn't happen for invokevirtual twice
                if (invocationType.equals(CallType.invokestatic)) {
                    for (Element elem : instruction.getOperands()) {
                        this.numArgs += 1;
                    }
                }
            case invokespecial:
                instruction.getArguments().forEach(arg -> code.append(generators.apply(arg)));

                if (invocationType.equals(CallType.invokespecial)) {
                    code.append(generators.apply(instruction.getCaller()));
                }

                String name = (invocationType.equals(CallType.invokestatic)) ?
                        generateFullName(((Operand) instruction.getCaller()).getName()) :
                        generateFullName(((ClassType) instruction.getCaller().getType()).getName());

                code.append(invocationType.name()).append(' ');
                code.append(name).append('/');
                String methodName = ((LiteralElement) instruction.getMethodName()).getLiteral().replace("\"", "");
                code.append(methodName);
                code.append('(');
                instruction.getArguments().forEach(arg -> code.append(generateJasminType(arg.getType())));
                Type ret = instruction.getReturnType();

                this.numArgs = (ret.getTypeOfElement() == ElementType.VOID) ? this.numArgs : this.numArgs - 1;

                code.append(')').append(generateJasminType(ret)).append(NL);
                break;

            case NEW:
                this.numArgs = -2;
                for (Element elem : instruction.getOperands()) {
                    this.numArgs += 1;
                }
                if (instruction.getCaller() instanceof Operand && ((Operand) instruction.getCaller()).getName().equals("array")) {
                    // load array size
                    code.append(generators.apply(instruction.getArguments().get(0)));
                    code.append("newarray int").append(NL);
                    break;
                }


                code.append(invocationType.name().toLowerCase()).append(' ');
                Operand operand = (Operand) instruction.getCaller();
                code.append(operand.getName()).append(NL);
                break;

            case arraylength:
                code.append(generators.apply(instruction.getCaller()));
                code.append("arraylength").append(NL);
                break;

            default:
                throw new IllegalStateException("Unsupported invocation type: " + invocationType);
        }

        this.decrementStack(this.numArgs);

        return code.toString();
    }


    private String generateSingleOpInstrCode(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteralElementCode(LiteralElement literal) {
        this.incrementStack(1);
        StringBuilder code = new StringBuilder();

        if (!literal.getType().getTypeOfElement().equals(ElementType.INT32) && !literal.getType().getTypeOfElement().equals(ElementType.BOOLEAN)) {
            code.append("ldc ").append(literal.getLiteral()).append(NL);
        } else {
            int value = Integer.parseInt(literal.getLiteral());
            if (value <= 5 && value >= -1) {
                code.append("iconst_").append(value).append(NL);
            } else if (value <= 127 && value >= -128) {
                code.append("bipush ").append(value).append(NL);
            } else if (value <= 32767 && value >= -32768) {
                code.append("sipush ").append(value).append(NL);
            } else {
                code.append("ldc ").append(value).append(NL);
            }

        }
        return code.toString();
    }


    private String generateJasminType(Type type) {
        ElementType elementType = type.getTypeOfElement();
        return switch (elementType) {
            case BOOLEAN -> "Z";
            case VOID -> "V";
            case INT32 -> "I";
            case STRING -> "Ljava/lang/String;";
            case CLASS, OBJECTREF -> "L" + generateFullName(((ClassType) type).getName()) + ";";
            case ARRAYREF -> {
                ArrayType arrType = (ArrayType) type;
                yield "[".repeat(arrType.getNumDimensions()) +
                        generateJasminType(arrType.getElementType());
            }
            default -> "";
        };
    }

    private String generateFullName(String simpleName) {
        List<String> imports = ollirResult.getOllirClass().getImports();
        return imports.stream()
                .filter(imp -> imp.endsWith("." + simpleName))
                .findFirst()
                .map(imp -> imp.replace('.', '/'))
                .orElse(simpleName);
    }

    private String generateUnaryOpInstrCode(UnaryOpInstruction unaryOp) {
        StringBuilder code = new StringBuilder();
        code.append(generators.apply(unaryOp.getOperand()));
        String op = switch (unaryOp.getOperation().getOpType()) {
            case NOTB -> "iconst_1" + NL + "ixor";
            default -> throw new NotImplementedException(unaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);
        this.incrementStack(1);
        this.decrementStack(2);
        this.incrementStack(1);


        return code.toString();
    }

    private String generateSingleOpCondInstrCode(SingleOpCondInstruction singleOpCond) {
        StringBuilder code = new StringBuilder();
        code.append(generators.apply(singleOpCond.getOperands().get(0)));
        code.append("ifne").append(' ').append(singleOpCond.getLabel()).append(NL);
        this.decrementStack(1);

        return code.toString();
    }


    private String generateGoToInstrCode(GotoInstruction goTo) {
        return "goto " + goTo.getLabel() + NL;
    }

    private String generateOpCondInstrCode(OpCondInstruction opCond) {
        StringBuilder code = new StringBuilder();

        code.append(opCond.getOperands().stream()
                .map(generators::apply)
                .collect(Collectors.joining()));


        switch (opCond.getCondition().getOperation().getOpType()) {
            case LTH:
                if (opCond.getCondition().getOperands().get(1) instanceof LiteralElement) {
                    code.append("isub").append(NL);
                    code.append("iflt ").append(opCond.getLabel()).append(NL);
                    this.decrementStack(3);
                }

                else if (opCond.getCondition().getOperands().get(0) instanceof LiteralElement) {
                    code.append("isub").append(NL);
                    code.append("ifgt ").append(opCond.getLabel()).append(NL);
                    this.decrementStack(3);
                }

                else { // In case it's two variables, no need to subtract (do we need this???????)
                    code.append("if_icmplt ").append(opCond.getLabel()).append(NL);
                    this.decrementStack(2);
                }
                break;

            case GTE:
                if (opCond.getCondition().getOperands().get(1) instanceof LiteralElement) {
                    code.append("isub").append(NL);
                    code.append("ifge ").append(opCond.getLabel()).append(NL);
                    this.decrementStack(3);
                }

                else if (opCond.getCondition().getOperands().get(0) instanceof LiteralElement) {
                    code.append("isub").append(NL);
                    code.append("ifle ").append(opCond.getLabel()).append(NL);
                    this.decrementStack(3);
                }

                else { // In case it's two variables, no need to subtract (do we need this???????)
                    code.append("if_icmpge ").append(opCond.getLabel()).append(NL);
                    this.decrementStack(2);
                }
                break;


            default:
                throw new NotImplementedException(opCond.getCondition().getOperation().getOpType());
        }

        return code.toString();
    }

    private int computeLimitLocals(Method method) {
        Set<Integer> registers = new TreeSet<>();
        registers.add(0); // 'this' reference

        for (Descriptor desc : method.getVarTable().values()) {
            registers.add(desc.getVirtualReg());
        }
        return registers.size();
    }

    private void incrementStack(int increment) {
        this.currentStack += increment;
        this.limitStack = Math.max(this.currentStack, this.limitStack);
    }

    private void decrementStack(int decrement) {
        this.currentStack -= decrement;
    }


}