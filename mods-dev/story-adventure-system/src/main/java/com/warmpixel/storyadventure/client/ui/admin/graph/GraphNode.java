package com.warmpixel.storyadventure.client.ui.admin.graph;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * Visual representation of a story node in the graph editor.
 * Supports drag, selection, and connection points.
 */
public class GraphNode {
    
    // Colors for different node types
    private static final int COLOR_DIALOGUE = 0xFF4488FF;
    private static final int COLOR_TASK = 0xFF44FF44;
    private static final int COLOR_PUZZLE = 0xFFFF8844;
    private static final int COLOR_COMBAT = 0xFFFF4444;
    private static final int COLOR_CUTSCENE = 0xFFCC44FF;
    private static final int COLOR_CHECKPOINT = 0xFFFFCC44;
    private static final int COLOR_BORDER = 0xFF333333;
    private static final int COLOR_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFFCCCCCC;
    
    public static final int NODE_WIDTH = 120;
    public static final int NODE_HEIGHT = 60;
    public static final int CONNECTOR_SIZE = 8;
    
    private final String nodeId;
    private String nodeType;
    private String displayName;
    private String description;
    
    // Position in graph space
    private float x;
    private float y;
    
    // Editor state
    private boolean selected = false;
    private boolean hovered = false;
    private boolean dragging = false;
    
    // Connection points
    private List<String> outgoingEdges = new ArrayList<>();
    private List<String> incomingEdges = new ArrayList<>();
    
    // Triggers
    private boolean hasOnEnter = false;
    private boolean hasOnExit = false;
    
    public GraphNode(String nodeId, String nodeType, float x, float y) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.displayName = nodeId;
        this.x = x;
        this.y = y;
    }
    
    public void render(GuiGraphics graphics, Font font, float offsetX, float offsetY, float zoom) {
        int screenX = (int)((x + offsetX) * zoom);
        int screenY = (int)((y + offsetY) * zoom);
        int w = (int)(NODE_WIDTH * zoom);
        int h = (int)(NODE_HEIGHT * zoom);
        
        // Skip if off-screen
        if (screenX + w < 0 || screenY + h < 0 || screenX > graphics.guiWidth() || screenY > graphics.guiHeight()) {
            return;
        }
        
        int typeColor = getTypeColor();
        
        // Node background
        int bgColor = selected ? 0xFF2A1A1A : (hovered ? 0xFF1A1010 : 0xFF101010);
        graphics.fill(screenX, screenY, screenX + w, screenY + h, bgColor);
        
        // Type-colored left bar
        graphics.fill(screenX, screenY, screenX + (int)(4 * zoom), screenY + h, typeColor);
        
        // Border
        int borderColor = selected ? COLOR_SELECTED : (hovered ? typeColor : COLOR_BORDER);
        graphics.fill(screenX, screenY, screenX + w, screenY + 1, borderColor);
        graphics.fill(screenX, screenY + h - 1, screenX + w, screenY + h, borderColor);
        graphics.fill(screenX, screenY, screenX + 1, screenY + h, borderColor);
        graphics.fill(screenX + w - 1, screenY, screenX + w, screenY + h, borderColor);
        
        // Type icon/badge
        String typeIcon = getTypeIcon();
        graphics.drawString(font, typeIcon, screenX + (int)(8 * zoom), screenY + (int)(5 * zoom), typeColor);
        
        // Node ID (truncated)
        String displayId = displayName.length() > 14 ? displayName.substring(0, 12) + ".." : displayName;
        graphics.drawString(font, displayId, screenX + (int)(8 * zoom), screenY + (int)(20 * zoom), COLOR_TEXT);
        
        // Type label
        String typeLabel = getTypeLabel();
        graphics.drawString(font, typeLabel, screenX + (int)(8 * zoom), screenY + (int)(35 * zoom), 
            (typeColor & 0x00FFFFFF) | 0x80000000);
        
        // Trigger indicators
        if (hasOnEnter) {
            graphics.fill(screenX + w - (int)(20 * zoom), screenY + (int)(5 * zoom), 
                         screenX + w - (int)(12 * zoom), screenY + (int)(13 * zoom), 0xFF44FF44);
        }
        if (hasOnExit) {
            graphics.fill(screenX + w - (int)(10 * zoom), screenY + (int)(5 * zoom), 
                         screenX + w - (int)(2 * zoom), screenY + (int)(13 * zoom), 0xFFFF4444);
        }
        
        // Input connector (left side)
        if (!incomingEdges.isEmpty() || true) { // Always show for receiving
            int connY = screenY + h / 2 - (int)(CONNECTOR_SIZE * zoom / 2);
            graphics.fill(screenX - (int)(CONNECTOR_SIZE * zoom / 2), connY,
                         screenX + (int)(CONNECTOR_SIZE * zoom / 2), connY + (int)(CONNECTOR_SIZE * zoom),
                         typeColor);
        }
        
        // Output connectors (right side)
        int connY = screenY + h / 2 - (int)(CONNECTOR_SIZE * zoom / 2);
        graphics.fill(screenX + w - (int)(CONNECTOR_SIZE * zoom / 2), connY,
                     screenX + w + (int)(CONNECTOR_SIZE * zoom / 2), connY + (int)(CONNECTOR_SIZE * zoom),
                     typeColor);
    }
    
    public boolean containsPoint(float pointX, float pointY, float offsetX, float offsetY, float zoom) {
        float screenX = (x + offsetX) * zoom;
        float screenY = (y + offsetY) * zoom;
        float w = NODE_WIDTH * zoom;
        float h = NODE_HEIGHT * zoom;
        
        return pointX >= screenX && pointX < screenX + w && pointY >= screenY && pointY < screenY + h;
    }
    
    public float[] getOutputConnectorPos(float offsetX, float offsetY, float zoom) {
        float screenX = (x + offsetX) * zoom + NODE_WIDTH * zoom;
        float screenY = (y + offsetY) * zoom + NODE_HEIGHT * zoom / 2;
        return new float[]{screenX, screenY};
    }
    
    public float[] getInputConnectorPos(float offsetX, float offsetY, float zoom) {
        float screenX = (x + offsetX) * zoom;
        float screenY = (y + offsetY) * zoom + NODE_HEIGHT * zoom / 2;
        return new float[]{screenX, screenY};
    }
    
    private int getTypeColor() {
        return switch (nodeType.toUpperCase()) {
            case "DIALOGUE" -> COLOR_DIALOGUE;
            case "TASK" -> COLOR_TASK;
            case "PUZZLE" -> COLOR_PUZZLE;
            case "COMBAT" -> COLOR_COMBAT;
            case "CUTSCENE" -> COLOR_CUTSCENE;
            case "CHECKPOINT" -> COLOR_CHECKPOINT;
            default -> COLOR_BORDER;
        };
    }
    
    private String getTypeIcon() {
        return switch (nodeType.toUpperCase()) {
            case "DIALOGUE" -> "💬";
            case "TASK" -> "📋";
            case "PUZZLE" -> "🧩";
            case "COMBAT" -> "⚔";
            case "CUTSCENE" -> "🎬";
            case "CHECKPOINT" -> "💾";
            default -> "📦";
        };
    }
    
    private String getTypeLabel() {
        return switch (nodeType.toUpperCase()) {
            case "DIALOGUE" -> "对话";
            case "TASK" -> "任务";
            case "PUZZLE" -> "谜题";
            case "COMBAT" -> "战斗";
            case "CUTSCENE" -> "过场";
            case "CHECKPOINT" -> "存档点";
            default -> nodeType;
        };
    }
    
    // Getters and setters
    public String getNodeId() { return nodeId; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String type) { this.nodeType = type; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String name) { this.displayName = name; }
    public String getDescription() { return description; }
    public void setDescription(String desc) { this.description = desc; }
    public float getX() { return x; }
    public float getY() { return y; }
    public void setPosition(float x, float y) { this.x = x; this.y = y; }
    public void move(float dx, float dy) { this.x += dx; this.y += dy; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isHovered() { return hovered; }
    public void setHovered(boolean hovered) { this.hovered = hovered; }
    public boolean isDragging() { return dragging; }
    public void setDragging(boolean dragging) { this.dragging = dragging; }
    public List<String> getOutgoingEdges() { return outgoingEdges; }
    public void addOutgoingEdge(String targetId) { outgoingEdges.add(targetId); }
    public List<String> getIncomingEdges() { return incomingEdges; }
    public void addIncomingEdge(String sourceId) { incomingEdges.add(sourceId); }
    public boolean hasOnEnter() { return hasOnEnter; }
    public void setHasOnEnter(boolean has) { this.hasOnEnter = has; }
    public boolean hasOnExit() { return hasOnExit; }
    public void setHasOnExit(boolean has) { this.hasOnExit = has; }
}
