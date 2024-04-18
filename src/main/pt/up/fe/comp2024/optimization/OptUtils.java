package pt.up.fe.comp2024.optimization;

import org.specs.comp.ollir.Instruction;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.List;
import java.util.Optional;

import static pt.up.fe.comp2024.ast.Kind.INT_TYPE;
import static pt.up.fe.comp2024.ast.Kind.TYPE;

public class OptUtils {
    private static int tempNumber = -1;

    public static String getTemp() {

        return getTemp("tmp");
    }

    public static String getTemp(String prefix) {

        return prefix + getNextTempNum();
    }

    public static int getNextTempNum() {

        tempNumber += 1;
        return tempNumber;
    }

    public static String toOllirType(JmmNode typeNode) {
        List<Kind> validTypes = List.of(Kind.INT_TYPE, Kind.BOOLEAN_TYPE, Kind.ARRAY_TYPE, Kind.VOID_TYPE, Kind.STRING_TYPE, Kind.CLASS_TYPE);

        String typeKind = typeNode.getKind();

        if (!validTypes.contains(Kind.fromString(typeKind))) {
            throw new NotImplementedException("Type " + typeKind + " not supported");
        }

        // Handle array types
        if (typeKind.equals("ArrayType")) {
            String arrayType = typeNode.getChildren().get(0).get("name") + "[]";
            return toOllirType(arrayType);
        }

        String typeName = typeNode.get("name");

        return toOllirType(typeName);
    }

    public static String toOllirType(Type type) {
        return toOllirType(type.getName());
    }

    private static String toOllirType(String typeName) {

        String type = "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "void" -> "V";
            case "String[]" -> "array.String";
            // else, it's .class

            default -> typeName;
        };

        return type;
    }


}
