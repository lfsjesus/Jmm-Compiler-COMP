package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {

        JmmNode classDecl = root.getChildren(Kind.CLASS_DECL).get(0);
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");
        String superClass = null;

        if (classDecl.hasAttribute("superName")) {
            superClass = classDecl.get("superName");
        }

        var imports = buildImports(root);
        var methods = buildMethods(classDecl);
        //var returnTypes = buildReturnTypes(classDecl);
        //var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);


        //return new JmmSymbolTable(className, superClass, methods, returnTypes, params, locals, imports);
        return new JmmSymbolTable(className, superClass, methods, null, null, locals, imports);
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, Type> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), new Type(TypeUtils.getIntTypeName(), false)));

        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            List<Symbol> paramsList = new ArrayList<>();
            for (JmmNode param : method.getChildren(PARAM)) {
                String paramName = param.get("name"); // name of the parameter
                JmmNode type = param.getJmmChild(0);

                if (type.getKind().equals("ArrayType")) { // SHOULD I ADD A NEW KIND ??????????
                    String paramType = type.getJmmChild(0).get("name");
                    paramsList.add(new Symbol(new Type(paramType, true), paramName));
                    continue;
                }

                String paramType = param.getJmmChild(0).get("name"); // type of the parameter

                paramsList.add(new Symbol(new Type(paramType, false), paramName));
            }
            map.put(method.get("name"), paramsList);
        }

        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), getLocalsList(method)));

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {
        List<String> methods = new ArrayList<>();

        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            if (!method.hasAttribute("name")) {
                methods.add("main");
                continue;
            }
            methods.add(method.get("name"));
        }

        return methods;
    }

    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        // TODO: Simple implementation that needs to be expanded
        List<Symbol> localsList = new ArrayList<>();
        for(JmmNode varDecl : methodDecl.getChildren(VAR_DECL)) {
            String varName = varDecl.get("name");
            JmmNode type = varDecl.getJmmChild(0);

            if (type.getKind().equals("ArrayType")) { // SHOULD I ADD A NEW KIND ??????????
                String varType = type.getJmmChild(0).get("name");
                localsList.add(new Symbol(new Type(varType, true), varName));
                continue;
            }

            String varType = type.get("name");
            localsList.add(new Symbol(new Type(varType, false), varName));
        }

        return localsList;
    }

    private static List<String> buildImports(JmmNode root) {
        List<JmmNode> imports = root.getChildren(IMPORT_DECL);
        return imports.stream()
                    .map(imp -> imp.get("packageName"))
                    .toList();
    }


}
