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
        JmmNode classDecl;

        try {
            classDecl = root.getChildren(Kind.CLASS_DECL).get(0);
        } catch (Exception e) {
            throw new RuntimeException("No class declaration found");
        }


        String className = classDecl.get("name");
        String superClass = null;

        if (classDecl.hasAttribute("superName")) {
            superClass = classDecl.get("superName");
        }

        List<String> imports = buildImports(root);
        List<String> methods = buildMethods(classDecl);
        Map<String, Type> returnTypes = buildReturnTypes(classDecl);
        Map<String, List<Symbol>> params = buildParams(classDecl);
        Map<String, List<Symbol>> locals = buildLocals(classDecl);
        List<Symbol> fields = buildFields(classDecl);

        return new JmmSymbolTable(className, superClass, fields, methods, returnTypes, params, locals, imports);
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            JmmNode type = method.getJmmChild(0);

            if (type.getKind().equals("ArrayType")) {
                String returnType = type.getJmmChild(0).get("name");
                map.put(method.get("name"), new Type(returnType, true));
                continue;
            }

            String returnType = type.get("name");
            map.put(method.get("name"), new Type(returnType, false));
        }


        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            List<Symbol> paramsList = new ArrayList<>();

            for (JmmNode param : method.getChildren(PARAM)) {
                JmmNode paramNameNode = param.getJmmChild(1);
                String paramName = paramNameNode.get("name");
                JmmNode type = param.getJmmChild(0);

                if (type.getKind().equals("ArrayType")) {
                    String paramType = type.getJmmChild(0).get("name");
                    paramsList.add(new Symbol(new Type(paramType, true), paramName));
                    continue;
                }

                String paramType = param.getJmmChild(0).get("name");

                paramsList.add(new Symbol(new Type(paramType, false), paramName));
            }

            // VarArgs
            for (JmmNode varArg : method.getChildren(VAR_ARGS)) {
                String paramName = varArg.get("name");
                JmmNode type = varArg.getJmmChild(0);

                String paramType = varArg.getJmmChild(0).get("name");

                paramsList.add(new Symbol(new Type(paramType, true), paramName));
            }

            map.put(method.get("name"), paramsList);
        }

        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        for (JmmNode method : classDecl.getChildren(METHOD_DECL))
        {
            map.put(method.get("name"),getLocalsList(method));
        }

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {
        List<String> methods = new ArrayList<>();

        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            methods.add(method.get("name"));
        }

        return methods;
    }

    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        List<Symbol> localsList = new ArrayList<>();
        for(JmmNode varDecl : methodDecl.getChildren(VAR_DECL)) {
            JmmNode type = varDecl.getJmmChild(0);
            JmmNode declarable = varDecl.getJmmChild(1);
            String varName = declarable.get("name");

            if (type.getKind().equals("ArrayType")) {
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

    public static List<Symbol> buildFields(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();

        for (JmmNode varDecl : classDecl.getChildren(VAR_DECL)) {
            JmmNode type = varDecl.getJmmChild(0);
            JmmNode declarable = varDecl.getJmmChild(1);
            String varName = declarable.get("name");

            if (type.getKind().equals("ArrayType")) {
                String varType = type.getJmmChild(0).get("name");
                fields.add(new Symbol(new Type(varType, true), varName));
                continue;
            }

            String varType = type.get("name");
            fields.add(new Symbol(new Type(varType, false), varName));
        }

        return fields;
    }

}
