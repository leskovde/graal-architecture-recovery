package cz.cuni.mff.d3s;

import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.github.javaparser.utils.SourceRoot;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SourceAnalysis {
    private final Multimap<String, String> packagesPerProjectRoot = HashMultimap.create();
    private final Multimap<String, String> classesPerPackage = HashMultimap.create();
    private final Map<String, String> classToPackage = new HashMap<>();
    private final Multimap<String, String> classReferences = HashMultimap.create();
    private final Multimap<String, String> importsPerClass = HashMultimap.create();
    private final Multimap<String, String> packageReferences = HashMultimap.create();
    private final Multimap<String, MethodCallExpr> unresolvedCalls = HashMultimap.create();
    private final Multimap<String, FieldAccessExpr> unresolvedFieldAccesses = HashMultimap.create();
    private final Multimap<String, String> commentsPerSourceRoot = ArrayListMultimap.create();

    private final Graph packagePerProjectHierarchy = new SingleGraph("Project hierarchy (Modules)");
    private final Graph classPerPackageHierarchy = new SingleGraph("Module hierarchy (Classes)");
    private final Graph classRelationships = new SingleGraph("Class dependencies");
    private final Graph packageRelationships = new SingleGraph("Package dependencies");

    {
        for (var graph : new Graph[] {
                packagePerProjectHierarchy, classPerPackageHierarchy, classRelationships, packageRelationships}) {
            graph.setStrict(false);
            graph.setAutoCreate(true);
            graph.setAttribute("ui.stylesheet",
                    "node{\n" +
                            "    size: 30px, 30px;\n" +
                            "    fill-color: #f7f7f0;\n" +
                            "    text-mode: normal; \n" +
                            "}");
            System.setProperty("org.graphstream.ui", "swing");
        }
    }

    public void addClass(SourceRoot projectRoot, Optional<PackageDeclaration> packageDeclaration, String className, List<String> imports) {
        if (packageDeclaration.isPresent()) {
            var projectPath = projectRoot.getRoot().toString();
            var packageName = packageDeclaration.get().getNameAsString();
            addPackagePerProject(projectPath, packageName);
            addClassPerPackage(packageName, className);
            for (var imp : imports) {
                importsPerClass.put(className, imp);
            }
        }
    }

    private void addClassPerPackage(String packageName, String className) {
        classesPerPackage.put(packageName, className);
        classToPackage.put(className, packageName);
        classPerPackageHierarchy.addEdge(packageName + "-" + className, packageName, className);
    }

    public void addMethodCall(Optional<PackageDeclaration> packageDeclaration, TypeDeclaration<?> clazz, ResolvedMethodLikeDeclaration method) {
        if (packageDeclaration.isPresent() && clazz.getFullyQualifiedName().isPresent()) {
            var className = clazz.getFullyQualifiedName().get();
            var referencedClassName = method.declaringType().getQualifiedName();
            if (!method.getPackageName().startsWith("java.")) {
                addClassReference(className, referencedClassName);
                addPackageReference(packageDeclaration.get().getNameAsString(), method.getPackageName());
            }
        }
    }

    public void addFieldAccess(Optional<PackageDeclaration> packageDeclaration, TypeDeclaration<?> clazz, ResolvedFieldDeclaration field) {
        if (packageDeclaration.isPresent() && clazz.getFullyQualifiedName().isPresent()) {
            var className = clazz.getFullyQualifiedName().get();
            var referencedClassName = field.declaringType().getQualifiedName();
            addClassReference(className, referencedClassName);
            addPackageReference(packageDeclaration.get().getNameAsString(), field.declaringType().getPackageName());
        }
    }

    public void addComment(Path sourceRoot, String comment) {
        if (!comment.contains("Copyright")) {
            commentsPerSourceRoot.put(sourceRoot.toString(), comment);
        }
    }

    public void addUnresolvedCall(TypeDeclaration<?> clazz, MethodCallExpr call) {
        if (clazz.getFullyQualifiedName().isPresent()) {
            var className = clazz.getFullyQualifiedName().get();
            unresolvedCalls.put(className, call);
        }
    }

    public void addUnresolvedFieldAccess(TypeDeclaration<?> clazz, FieldAccessExpr accessExpr) {
        if (clazz.getFullyQualifiedName().isPresent()) {
            var className = clazz.getFullyQualifiedName().get();
            unresolvedFieldAccesses.put(className, accessExpr);
        }
    }

    public void resolveSymbols() {
        for (var entry : unresolvedCalls.entries()) {
            try {
                var scope = entry.getValue().getScope();
                if (scope.isPresent() && scope.get().isNameExpr()) {
                    var declaringType = scope.get().asNameExpr().getName().asString();
                    var fullDeclaringTypeName = resolveClassReference(declaringType, importsPerClass.get(entry.getKey()));
                    if (fullDeclaringTypeName != null) {
                        addClassReference(entry.getKey(), fullDeclaringTypeName);
                        addPackageReference(classToPackage.get(entry.getKey()), classToPackage.get(fullDeclaringTypeName));
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to resolve " + entry.getValue());
            }
        }
        for (var entry : unresolvedFieldAccesses.entries()) {
            try {
                var declaringType = entry.getValue().getScope().asNameExpr().getName().asString();
                var fullDeclaringTypeName = resolveClassReference(declaringType, importsPerClass.get(entry.getKey()));
                if (fullDeclaringTypeName != null) {
                    addClassReference(entry.getKey(), fullDeclaringTypeName);
                    addPackageReference(classToPackage.get(entry.getKey()), classToPackage.get(fullDeclaringTypeName));
                }
            } catch (Exception e) {
                System.err.println("Failed to resolve " + entry.getValue());
            }
        }
    }

    public void showProjectPackages() {
        for (Node node : packagePerProjectHierarchy) {
            node.setAttribute("ui.label", node.getId());
        }
        packagePerProjectHierarchy.display();
    }

    public void showPackageClasses() {
        for (Node node : classPerPackageHierarchy) {
            node.setAttribute("ui.label", node.getId());
        }
        classPerPackageHierarchy.display();
    }

    public void showClassReferences() {
        for (Node node : classRelationships) {
            node.setAttribute("ui.label", node.getId());
        }
        classRelationships.display();
    }

    public void showPackageReferences() {
        for (Node node : packageRelationships) {
            node.setAttribute("ui.label", node.getId());
        }
        packageRelationships.display();
    }

    public void dumpComments() {
        for (var fileName : commentsPerSourceRoot.keySet()) {
            try (var writer = new PrintWriter(fileName + "_comments.txt")) {
                for (var entry : commentsPerSourceRoot.entries()) {
                    writer.println(entry.getKey() + " has the following comment: " + entry.getValue());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void addPackageReference(String packageName, String referencedPackageName) {
        packageReferences.put(packageName, referencedPackageName);
        packageRelationships.addEdge(packageName + "-" + referencedPackageName, packageName, referencedPackageName);
    }

    private void addClassReference(String className, String referencedClassName) {
        classReferences.put(className, referencedClassName);
        classRelationships.addEdge(className + "-" + referencedClassName, className, referencedClassName);
    }

    private void addPackagePerProject(String projectPath, String packageName) {
        packagesPerProjectRoot.put(projectPath, packageName);
        packagePerProjectHierarchy.addEdge(projectPath + "-" + packageName, projectPath, packageName);
    }

    private String resolveClassReference(String className, Collection<String> imports) {
        // Class does not have the full name, must be resolved from the imports
        for (var seenClass : classesPerPackage.values()
                .stream()
                .filter(c -> c.endsWith(className))
                .collect(Collectors.toSet())) {
            for (var imp : imports) {
                var parts = imp.split("\\.");
                for (int i = parts.length; i > 0; i--) {
                    var prefix = String.join(".", Arrays.copyOfRange(parts, 0, i));
                    if (seenClass.startsWith(prefix)) {
                        return seenClass;
                    }
                }
            }
        }
        return null;
    }
}
