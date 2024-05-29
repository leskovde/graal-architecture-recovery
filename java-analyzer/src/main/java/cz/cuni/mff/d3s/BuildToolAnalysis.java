package cz.cuni.mff.d3s;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;

public class BuildToolAnalysis {
    private final Multimap<String, String> projectReferences = HashMultimap.create();

    private final Graph projectRelationships = new SingleGraph("Project dependencies");

    {
        projectRelationships.setStrict(false);
        projectRelationships.setAutoCreate(true);
        projectRelationships.setAttribute("ui.stylesheet",
                "node{\n" +
                        "    size: 30px, 30px;\n" +
                        "    fill-color: #f7f7f0;\n" +
                        "    text-mode: normal; \n" +
                        "}");
        System.setProperty("org.graphstream.ui", "swing");
    }

    public void addProjectDependency(String projectName, String dependency) {
        projectReferences.put(projectName, dependency);
        projectRelationships.addEdge(projectName + "-" + dependency, projectName, dependency);
    }

    public void showProjectDependencies() {
        for (Node node : projectRelationships) {
            node.setAttribute("ui.label", node.getId());
        }
        projectRelationships.display();
    }

    public void printProjectDependencies() {
        // Print a PlantUML package diagram
        StringBuilder builder = new StringBuilder();
        builder.append("@startuml\n");
        for (var entry : projectReferences.entries()) {
            builder.append("[")
                   .append(entry.getKey())
                   .append("] --> [")
                   .append(entry.getValue())
                   .append("]\n");
        }
        builder.append("@enduml");
        System.out.println(builder);
    }
}
