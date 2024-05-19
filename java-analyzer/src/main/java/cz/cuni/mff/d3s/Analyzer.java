package cz.cuni.mff.d3s;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy;
import com.github.javaparser.utils.SourceRoot;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Analyzer {
    private final Path repoPath;

    public Analyzer(Path repoPath) {
        this.repoPath = repoPath;
        TypeSolver typeSolver = new ReflectionTypeSolver();
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        StaticJavaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
    }

    public Map<String, Integer> getFileExtensionCounts() {
        var counts = new HashMap<String, Integer>();
        for (Path file : getAllRegularFiles(repoPath)) {
            String extension = Files.getFileExtension(file.getFileName().toString());
            if (extension.isEmpty()) {
                continue;
            }
            counts.put(extension, counts.getOrDefault(extension, 0) + 1);
        }
        return counts;
    }

    public Map<String, Double> getFileExtensionsRatios() {
        var counts = getFileExtensionCounts();
        var total = counts.values().stream().mapToInt(Integer::intValue).sum();
        var ratios = new HashMap<String, Double>();
        for (var entry : counts.entrySet()) {
            ratios.put(entry.getKey(), entry.getValue() / (double) total * 100);
        }
        return ratios;
    }

    public List<SourceRoot> getJavaSourceRoots(String project) {
        return new SymbolSolverCollectionStrategy().collect(repoPath.resolve(project)).getSourceRoots();
    }

    public List<CompilationUnit> getAllCompilationUnits(SourceRoot sourceRoot) {
        List<CompilationUnit> compilationUnits = new ArrayList<>();
        try {
            for (var result : sourceRoot.tryToParse()) {
                if (!result.isSuccessful() || result.getResult().isEmpty()) {
                    System.out.println("Failed to parse " + result.getProblems());
                    continue;
                }
                var compilationUnit = result.getResult().get();
                compilationUnits.add(compilationUnit);
            }
        } catch (Exception e) {
            System.out.println("Failed to parse " + sourceRoot);
        }
        return compilationUnits;
    }

    private List<Path> getAllRegularFiles(Path root) {
        return FileUtils.listFiles(root.toFile(), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE)
                .stream()
                .filter(java.io.File::isFile)
                .map(java.io.File::toPath)
                .collect(Collectors.toList());
    }

    private List<Path> getAllSubdirectories(Path root) {
        return FileUtils.listFilesAndDirs(root.toFile(), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE)
                .stream()
                .filter(java.io.File::isDirectory)
                .map(java.io.File::toPath)
                .collect(Collectors.toList());
    }
}
