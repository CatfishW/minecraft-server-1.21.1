package com.warmpixel.storyadventure.loader;

import com.warmpixel.storyadventure.core.graph.StageGraph;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.core.graph.StageEdge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates story graphs for correctness and completeness.
 */
public class StoryValidator {
    
    /**
     * Validate a story graph.
     * 
     * @return List of validation errors (empty if valid)
     */
    public List<String> validate(StageGraph graph) {
        List<String> errors = new ArrayList<>();
        
        // Check required fields
        if (graph.getStoryId() == null || graph.getStoryId().isEmpty()) {
            errors.add("Story ID is required");
        }
        
        if (graph.getEntryNodeId() == null || graph.getEntryNodeId().isEmpty()) {
            errors.add("Entry node ID is required");
        }
        
        // Check entry node exists
        if (graph.getEntryNode() == null) {
            errors.add("Entry node '" + graph.getEntryNodeId() + "' does not exist");
        }
        
        // Validate each node
        Set<String> nodeIds = new HashSet<>();
        for (StageNode node : graph.getAllNodes()) {
            nodeIds.add(node.getId());
            
            if (node.getType() == null) {
                errors.add("Node '" + node.getId() + "' has no type");
            }
        }
        
        // Validate edge targets exist
        for (StageNode node : graph.getAllNodes()) {
            for (StageEdge edge : node.getEdges()) {
                if (!nodeIds.contains(edge.getTargetNodeId())) {
                    errors.add("Node '" + node.getId() + "' has edge to non-existent node '" 
                        + edge.getTargetNodeId() + "'");
                }
            }
        }
        
        // Check for unreachable nodes (optional warning)
        Set<String> reachable = findReachableNodes(graph);
        for (String nodeId : nodeIds) {
            if (!reachable.contains(nodeId) && !nodeId.equals(graph.getEntryNodeId())) {
                // This is a warning, not an error
                // Could be intentional for hidden/bonus content
            }
        }
        
        // Check player limits
        if (graph.getMinPlayers() < 1) {
            errors.add("Minimum players must be at least 1");
        }
        
        if (graph.getMaxPlayers() < graph.getMinPlayers()) {
            errors.add("Maximum players must be >= minimum players");
        }
        
        return errors;
    }
    
    /**
     * Find all nodes reachable from the entry node.
     */
    private Set<String> findReachableNodes(StageGraph graph) {
        Set<String> reachable = new HashSet<>();
        Set<String> toVisit = new HashSet<>();
        toVisit.add(graph.getEntryNodeId());
        
        while (!toVisit.isEmpty()) {
            String nodeId = toVisit.iterator().next();
            toVisit.remove(nodeId);
            
            if (reachable.add(nodeId)) {
                StageNode node = graph.getNode(nodeId);
                if (node != null) {
                    for (StageEdge edge : node.getEdges()) {
                        if (!reachable.contains(edge.getTargetNodeId())) {
                            toVisit.add(edge.getTargetNodeId());
                        }
                    }
                }
            }
        }
        
        return reachable;
    }
    
    /**
     * Check if a story has any ending nodes (nodes with no outgoing edges).
     */
    public boolean hasEndings(StageGraph graph) {
        for (StageNode node : graph.getAllNodes()) {
            if (node.getEdges().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
