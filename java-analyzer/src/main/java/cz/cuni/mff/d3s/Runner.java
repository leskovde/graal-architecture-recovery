package cz.cuni.mff.d3s;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.resolution.declarations.ResolvedDeclaration;
import com.github.javaparser.utils.SourceRoot;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Runner {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java -jar <path-to-jar> <mode> <path-to-graal-repo>");
            System.err.println("Modes:");
            System.err.println("\tc - count file extensions");
            System.err.println("\tclass - display class references");
            System.err.println("\tpackage - display package references");
            System.err.println("\tproject - display project references");
            System.exit(1);
        }

        var analyzer = new Analyzer(Path.of(args[1]));

        switch (args[0]) {
            case "c" -> countFileExtensions(analyzer);
            case "project" -> analyzeProjects(analyzer);
            case "class" -> analyzeClasses(analyzer);
            case "package" -> analyzePackages(analyzer);
            default -> {
                System.err.println("Unknown mode: " + args[0]);
                System.exit(1);
            }
        }
    }

    private static void analyzeProjects(Analyzer analyzer) {
        var result = runBuildToolAnalysis(analyzer);
        result.printProjectDependencies();
        result.showProjectDependencies();
    }

    private static void analyzePackages(Analyzer analyzer) {
        var result = runSourceAnalysis(analyzer);
        result.showPackageReferences();
    }

    private static void analyzeClasses(Analyzer analyzer) {
        var result = runSourceAnalysis(analyzer);
        result.showClassReferences();
    }

    private static BuildToolAnalysis runBuildToolAnalysis(Analyzer analyzer) {
        var analysisResult = new BuildToolAnalysis();
        for (var buildFile : analyzer.getAllBuildFiles()) {
            try {
                var projectName = analyzer.getProjectNameFromBuildTool(buildFile);
                var dependencies = analyzer.getProjectDependenciesFromBuildTool(buildFile);
                for (var dependency : dependencies) {
                    analysisResult.addProjectDependency(projectName, dependency);
                }
            } catch (Exception e) {
                System.err.println("File " + buildFile + " could not be parsed correctly: " + e);
            }
        }
        return analysisResult;
    }

    private static SourceAnalysis runSourceAnalysis(Analyzer analyzer) {
        var analysisResult = new SourceAnalysis();
        // Some projects are unparsable, so we only iterate over the relevant ones
        for (var project : new String[] { "compiler", "espresso", "regex", "sdk", "sulong", "tools", "visualizer" }) {
            var sources = analyzer.getJavaSourceRoots(project);
            for (var sourceRoot : sources) {
                if (sourceRoot.getRoot().toString().contains("test")
                        || sourceRoot.getRoot().toString().contains("benchmark")) {
                    System.out.println("Skipping test directory: " + sourceRoot.getRoot());
                    continue;
                }
                System.out.println("Analyzing: " + sourceRoot.getRoot());
                var compilationUnits = analyzer.getAllCompilationUnits(sourceRoot);
                for (var unit : compilationUnits) {
                    analyzeUnit(sourceRoot, unit, analysisResult);
                }
            }
        }
        analysisResult.resolveSymbols();
        analysisResult.dumpComments();
        return analysisResult;
    }

    private static void countFileExtensions(Analyzer analyzer) {
        // Print the 10 most common file extensions and their ratios in the Graal repository
        System.out.println("File extensions:");
        MapUtils.sortByValueDescending(analyzer.getFileExtensionCounts())
                .stream()
                .limit(10)
                .forEach(entry -> System.out.println(entry.getKey() + ": " + entry.getValue()));
        System.out.println("+---------------------------------+");
        System.out.println("File extension ratios:");
        MapUtils.sortByValueDescending(analyzer.getFileExtensionsRatios())
                .stream()
                .limit(10)
                .forEach(entry -> System.out.println(entry.getKey() + ": " + entry.getValue() + "%"));
        System.out.println("+---------------------------------+");
    }

    private static void analyzeUnit(SourceRoot sourceRoot, CompilationUnit unit, SourceAnalysis analysisResult) {
        var declaredPackage = unit.getPackageDeclaration();
        var usedImports = unit.getImports();
        processClassNames(sourceRoot, unit, analysisResult, declaredPackage, usedImports.stream().map(NodeWithName::getNameAsString).toList());
        for (var clazz : unit.getTypes()) {
            processClassMembers(analysisResult, clazz, declaredPackage);
        }
        for (var comment : unit.getAllComments()) {
            analysisResult.addComment(sourceRoot.getRoot(), comment.asString());
        }
    }

    private static void processClassMembers(SourceAnalysis analysisResult, TypeDeclaration<?> clazz, Optional<PackageDeclaration> declaredPackage) {
        processMethodCalls(analysisResult, clazz, declaredPackage);
        processFieldAccesses(analysisResult, clazz, declaredPackage);
    }

    private static void processFieldAccesses(SourceAnalysis analysisResult, TypeDeclaration<?> clazz, Optional<PackageDeclaration> declaredPackage) {
        var fieldAccesses = clazz.findAll(FieldAccessExpr.class).stream()
                .map(field -> {
                    try {
                        return field.resolve();
                    } catch (Exception e) {
                        analysisResult.addUnresolvedFieldAccess(clazz, field);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(ResolvedDeclaration::isField)
                .map(ResolvedDeclaration::asField)
                .collect(Collectors.toSet());
        for (var field : fieldAccesses) {
            analysisResult.addFieldAccess(declaredPackage, clazz, field);
        }
    }

    private static void processMethodCalls(SourceAnalysis analysisResult, TypeDeclaration<?> clazz, Optional<PackageDeclaration> declaredPackage) {
        var methodCalls = clazz.findAll(MethodCallExpr.class).stream()
                .map(call -> {
                    try {
                        return call.resolve();
                    } catch (Exception e) {
                        analysisResult.addUnresolvedCall(clazz, call);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (var method : methodCalls) {
            analysisResult.addMethodCall(declaredPackage, clazz, method);
        }
    }

    private static void processClassNames(SourceRoot sourceRoot, CompilationUnit unit, SourceAnalysis analysisResult, Optional<PackageDeclaration> declaredPackage, List<String> imports) {
        var declaredClasses = unit.getTypes().stream()
                .map(TypeDeclaration::getFullyQualifiedName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toArray(String[]::new);
        for (var clazz : declaredClasses) {
            analysisResult.addClass(sourceRoot, declaredPackage, clazz, imports);
        }
    }
}
