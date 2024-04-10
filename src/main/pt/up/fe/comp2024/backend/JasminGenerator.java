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
        StringBuilder code = new StringBuilder();

        // Class declaration
        String accessModifier = classUnit.getClassAccessModifier() != AccessModifier.DEFAULT ?
                classUnit.getClassAccessModifier().name().toLowerCase() + " " : "";
        String classModifiers = (classUnit.isStaticClass() ? "static " : "") + (classUnit.isFinalClass() ? "final " : "");
        String packageName = classUnit.getPackage() != null ? classUnit.getPackage() + '/' : "";
        String className = packageName + classUnit.getClassName();

        code.append(".class ").append(accessModifier).append(classModifiers).append(className).append(NL);

        // Superclass
        String superClass = classUnit.getSuperClass() == null ? "java/lang/Object" : classUnit.getSuperClass();
        code.append(".super ").append(superClass).append(NL).append(NL);

        // Fields
        for (var field : classUnit.getFields()) {
            code.append(generators.apply(field)).append(NL);
        }

        // Default constructor
        code.append(NL).append(String.format("""
            ;default constructor
            .method public <init>()V
                aload_0
                invokespecial %s/<init>()V
                return
            .end method
            """, superClass));

        // Methods
        for (var method : classUnit.getMethods()) {
            if (!method.isConstructMethod()) { // Exclude constructor
                code.append(generators.apply(method));
            }
        }

        return code.toString();
    }



    private String generateMethod(Method method) {
        // Set the current method context
        currentMethod = method;

        StringBuilder code = new StringBuilder();

        // Construct method declaration
        String modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " : "";
        code.append(NL).append(".method ").append(modifier)
                .append(method.isStaticMethod() ? "static " : "")
                .append(method.isFinalMethod() ? "final " : "")
                .append(method.getMethodName());

        // Append method parameters
        code.append('(');
        method.getParams().forEach(param -> code.append(toJvmTypeDescriptor(param.getType())));
        code.append(')').append(toJvmTypeDescriptor(method.getReturnType())).append(NL); //generateTypeDescriptor not defined yet

        // Add fixed limits for stack and locals
        code.append(TAB).append(".limit stack 99").append(NL)
                .append(TAB).append(".limit locals 99").append(NL);

        // Process instructions
        method.getInstructions().forEach(inst -> {
            String instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));
            code.append(instCode);
        });

        // End method declaration
        code.append(".end method").append(NL);

        // Reset the current method context
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
        code.append("istore ").append(reg).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // Retrieve the virtual register for the operand from the current method's variable table.
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        // Determine the load instruction based on the operand's type.
        String loadInstruction = switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> "iload ";
            case THIS, OBJECTREF, ARRAYREF, STRING, CLASS -> "aload ";
            default -> throw new IllegalArgumentException("Unsupported operand type");
        };

        // Return the load instruction followed by the register number and a newline.
        return loadInstruction + reg + NL;
    }


    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case SUB -> "isub";
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
            case ARRAYREF -> {
                ArrayType arrayType = (ArrayType) type;
                String elementTypeDescriptor = toJvmTypeDescriptor(arrayType.getElementType());
                yield "L".repeat(arrayType.getNumDimensions()) + elementTypeDescriptor + ";";
            }
            default -> "";
        };
    }
}

