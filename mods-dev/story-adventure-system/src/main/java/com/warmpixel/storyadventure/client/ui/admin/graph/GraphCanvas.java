package com.warmpixel.storyadventure.client.ui.admin.graph;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.*;

/**
 * Pannable and zoomable canvas for the story graph editor.
 * Manages node layout, rendering, and interaction.
 */
public class GraphCanvas {
    
    private static final int GRID_SIZE = 50;
    private static final int COLOR_GRID = 0x20FFFFFF;
    private static final int COLOR_GRID_MAJOR = 0x30FFFFFF;
    private static final float MIN_ZOOM = 0.3f;
    private static final float MAX_ZOOM = 2.0f;
    
    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    
    private float offsetX = 0;
    private float offsetY = 0;
    private float zoom = 1.0f;
    
    private GraphNode selectedNode = null;
    private GraphEdge selectedEdge = null;
    private GraphNode draggedNode = null;
    private float dragStartX, dragStartY;
    private float nodeStartX, nodeStartY;
    
    // Canvas bounds
    private int canvasX, canvasY, canvasWidth, canvasHeight;
    
    public GraphCanvas() {
    }
    
    public void setBounds(int x, int y, int width, int height) {
        this.canvasX = x;
        this.canvasY = y;
        this.canvasWidth = width;
        this.canvasHeight = height;
    }
    
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        // Clip to canvas area
        graphics.enableScissor(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight);
        
        // Background
        graphics.fill(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, 0xFF0A0A0A);
        
        // Grid
        renderGrid(graphics);
        
        // Edges (render before nodes so they appear behind)
        for (GraphEdge edge : edges) {
            GraphNode source = nodes.get(edge.getSourceNodeId());
            GraphNode target = nodes.get(edge.getTargetNodeId());
            edge.render(graphics, font, source, target, offsetX + canvasX / zoom, offsetY + canvasY / zoom, zoom);
        }
        
        // Nodes
        for (GraphNode node : nodes.values()) {
            node.render(graphics, font, offsetX + canvasX / zoom, offsetY + canvasY / zoom, zoom);
        }
        
        // Disable scissor
        graphics.disableScissor();
        
        // Canvas border
        graphics.fill(canvasX, canvasY, canvasX + canvasWidth, canvasY + 1, 0xFF330011);
        graphics.fill(canvasX, canvasY + canvasHeight - 1, canvasX + canvasWidth, canvasY + canvasHeight, 0xFF330011);
        graphics.fill(canvasX, canvasY, canvasX + 1, canvasY + canvasHeight, 0xFF330011);
        graphics.fill(canvasX + canvasWidth - 1, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, 0xFF330011);
        
        // Zoom indicator
        String zoomText = String.format("%.0f%%", zoom * 100);
        graphics.drawString(font, zoomText, canvasX + canvasWidth - 40, canvasY + 5, 0x80FFFFFF);
    }
    
    private void renderGrid(GuiGraphics graphics) {
        int scaledGridSize = (int)(GRID_SIZE * zoom);
        if (scaledGridSize < 10) return; // Don't render grid when zoomed out too far
        
        int startX = canvasX + (int)(offsetX * zoom) % scaledGridSize;
        int startY = canvasY + (int)(offsetY * zoom) % scaledGridSize;
        
        // Vertical lines
        for (int x = startX; x < canvasX + canvasWidth; x += scaledGridSize) {
            boolean major = ((x - startX) / scaledGridSize) % 5 == 0;
            graphics.fill(x, canvasY, x + 1, canvasY + canvasHeight, major ? COLOR_GRID_MAJOR : COLOR_GRID);
        }
        
        // Horizontal lines
        for (int y = startY; y < canvasY + canvasHeight; y += scaledGridSize) {
            boolean major = ((y - startY) / scaledGridSize) % 5 == 0;
            graphics.fill(canvasX, y, canvasX + canvasWidth, y + 1, major ? COLOR_GRID_MAJOR : COLOR_GRID);
        }
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isInCanvas((float)mouseX, (float)mouseY)) return false;
        
        // Convert to graph space
        float graphX = (float)(mouseX - canvasX) / zoom - offsetX;
        float graphY = (float)(mouseY - canvasY) / zoom - offsetY;
        
        if (button == 0) { // Left click - select or start drag
            // Check nodes (in reverse order for top-most first)
            List<GraphNode> nodeList = new ArrayList<>(nodes.values());
            Collections.reverse(nodeList);
            
            for (GraphNode node : nodeList) {
                if (node.containsPoint((float)mouseX, (float)mouseY, 
                    offsetX + canvasX / zoom, offsetY + canvasY / zoom, zoom)) {
                    selectNode(node);
                    draggedNode = node;
                    dragStartX = (float)mouseX;
                    dragStartY = (float)mouseY;
                    nodeStartX = node.getX();
                    nodeStartY = node.getY();
                    return true;
                }
            }
            
            // Check edges
            for (GraphEdge edge : edges) {
                GraphNode source = nodes.get(edge.getSourceNodeId());
                GraphNode target = nodes.get(edge.getTargetNodeId());
                if (edge.containsPoint((float)mouseX, (float)mouseY, source, target,
                    offsetX + canvasX / zoom, offsetY + canvasY / zoom, zoom)) {
                    selectEdge(edge);
                    return true;
                }
            }
            
            // Click on empty space - deselect
            deselectAll();
            
            // Start panning
            dragStartX = (float)mouseX;
            dragStartY = (float)mouseY;
            return true;
        }
        
        return false;
    }
    
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!isInCanvas((float)mouseX, (float)mouseY) && draggedNode == null) return false;
        
        if (button == 0) {
            if (draggedNode != null) {
                // Drag node
                float dx = (float)(mouseX - dragStartX) / zoom;
                float dy = (float)(mouseY - dragStartY) / zoom;
                draggedNode.setPosition(nodeStartX + dx, nodeStartY + dy);
                return true;
            } else {
                // Pan canvas
                float dx = (float)(mouseX - dragStartX);
                float dy = (float)(mouseY - dragStartY);
                offsetX += dx / zoom;
                offsetY += dy / zoom;
                dragStartX = (float)mouseX;
                dragStartY = (float)mouseY;
                return true;
            }
        }
        
        return false;
    }
    
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggedNode != null) {
            draggedNode.setDragging(false);
            draggedNode = null;
            return true;
        }
        return false;
    }
    
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isInCanvas((float)mouseX, (float)mouseY)) return false;
        
        float oldZoom = zoom;
        
        if (delta > 0) {
            zoom = Math.min(MAX_ZOOM, zoom * 1.1f);
        } else {
            zoom = Math.max(MIN_ZOOM, zoom / 1.1f);
        }
        
        // Adjust offset to zoom toward mouse position
        float mouseGraphX = (float)(mouseX - canvasX) / oldZoom - offsetX;
        float mouseGraphY = (float)(mouseY - canvasY) / oldZoom - offsetY;
        
        offsetX = (float)(mouseX - canvasX) / zoom - mouseGraphX;
        offsetY = (float)(mouseY - canvasY) / zoom - mouseGraphY;
        
        return true;
    }
    
    public void mouseMoved(double mouseX, double mouseY) {
        // Update hover states
        for (GraphNode node : nodes.values()) {
            node.setHovered(node.containsPoint((float)mouseX, (float)mouseY,
                offsetX + canvasX / zoom, offsetY + canvasY / zoom, zoom));
        }
        
        for (GraphEdge edge : edges) {
            GraphNode source = nodes.get(edge.getSourceNodeId());
            GraphNode target = nodes.get(edge.getTargetNodeId());
            edge.setHovered(edge.containsPoint((float)mouseX, (float)mouseY, source, target,
                offsetX + canvasX / zoom, offsetY + canvasY / zoom, zoom));
        }
    }
    
    private boolean isInCanvas(float x, float y) {
        return x >= canvasX && x < canvasX + canvasWidth && 
               y >= canvasY && y < canvasY + canvasHeight;
    }
    
    public void addNode(GraphNode node) {
        nodes.put(node.getNodeId(), node);
    }
    
    public void addEdge(GraphEdge edge) {
        edges.add(edge);
        GraphNode source = nodes.get(edge.getSourceNodeId());
        GraphNode target = nodes.get(edge.getTargetNodeId());
        if (source != null) source.addOutgoingEdge(edge.getTargetNodeId());
        if (target != null) target.addIncomingEdge(edge.getSourceNodeId());
    }
    
    public void clear() {
        nodes.clear();
        edges.clear();
        selectedNode = null;
        selectedEdge = null;
    }
    
    public void selectNode(GraphNode node) {
        deselectAll();
        selectedNode = node;
        if (node != null) node.setSelected(true);
    }
    
    public void selectEdge(GraphEdge edge) {
        deselectAll();
        selectedEdge = edge;
        if (edge != null) edge.setSelected(true);
    }
    
    public void deselectAll() {
        if (selectedNode != null) selectedNode.setSelected(false);
        if (selectedEdge != null) selectedEdge.setSelected(false);
        selectedNode = null;
        selectedEdge = null;
    }
    
    /**
     * Automatically arrange nodes in a left-to-right flow layout.
     */
    public void autoLayout(String entryNodeId) {
        if (nodes.isEmpty()) return;
        
        // BFS from entry node
        Map<String, Integer> levels = new HashMap<>();
        Map<Integer, List<String>> levelNodes = new HashMap<>();
        
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        String startNode = entryNodeId != null && nodes.containsKey(entryNodeId) ? 
            entryNodeId : nodes.keySet().iterator().next();
        
        queue.add(startNode);
        levels.put(startNode, 0);
        visited.add(startNode);
        
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            int level = levels.get(nodeId);
            levelNodes.computeIfAbsent(level, k -> new ArrayList<>()).add(nodeId);
            
            GraphNode node = nodes.get(nodeId);
            if (node != null) {
                for (String targetId : node.getOutgoingEdges()) {
                    if (!visited.contains(targetId)) {
                        visited.add(targetId);
                        levels.put(targetId, level + 1);
                        queue.add(targetId);
                    }
                }
            }
        }
        
        // Add unvisited nodes
        for (String nodeId : nodes.keySet()) {
            if (!visited.contains(nodeId)) {
                int maxLevel = levelNodes.isEmpty() ? 0 : Collections.max(levelNodes.keySet()) + 1;
                levelNodes.computeIfAbsent(maxLevel, k -> new ArrayList<>()).add(nodeId);
            }
        }
        
        // Position nodes
        int xSpacing = GraphNode.NODE_WIDTH + 80;
        int ySpacing = GraphNode.NODE_HEIGHT + 40;
        
        for (Map.Entry<Integer, List<String>> entry : levelNodes.entrySet()) {
            int level = entry.getKey();
            List<String> nodesAtLevel = entry.getValue();
            
            int x = level * xSpacing + 50;
            int startY = -(nodesAtLevel.size() - 1) * ySpacing / 2;
            
            for (int i = 0; i < nodesAtLevel.size(); i++) {
                GraphNode node = nodes.get(nodesAtLevel.get(i));
                if (node != null) {
                    node.setPosition(x, startY + i * ySpacing);
                }
            }
        }
        
        // Center view
        centerView();
    }
    
    public void centerView() {
        if (nodes.isEmpty()) return;
        
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
        
        for (GraphNode node : nodes.values()) {
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX() + GraphNode.NODE_WIDTH);
            maxY = Math.max(maxY, node.getY() + GraphNode.NODE_HEIGHT);
        }
        
        float graphWidth = maxX - minX;
        float graphHeight = maxY - minY;
        
        // Fit to canvas
        float zoomX = canvasWidth / (graphWidth + 100);
        float zoomY = canvasHeight / (graphHeight + 100);
        zoom = Math.min(1.0f, Math.min(zoomX, zoomY));
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
        
        // Center
        float centerX = (minX + maxX) / 2;
        float centerY = (minY + maxY) / 2;
        offsetX = canvasWidth / (2 * zoom) - centerX;
        offsetY = canvasHeight / (2 * zoom) - centerY;
    }
    
    // Getters
    public GraphNode getSelectedNode() { return selectedNode; }
    public GraphEdge getSelectedEdge() { return selectedEdge; }
    public Map<String, GraphNode> getNodes() { return nodes; }
    public List<GraphEdge> getEdges() { return edges; }
    public float getZoom() { return zoom; }
    public void setZoom(float zoom) { this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom)); }
}
