package com.warmpixel.storyadventure.client.ui.admin.graph;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.List;

/**
 * Represents a visual edge (connection) between two nodes in the graph.
 * Renders as a bezier curve with condition labels.
 */
public class GraphEdge {
    
    private static final int COLOR_DEFAULT = 0xFFAAAAAA;
    private static final int COLOR_SELECTED = 0xFFFFFFFF;
    private static final int COLOR_CONDITIONAL = 0xFFFFCC44;
    
    private final String sourceNodeId;
    private final String targetNodeId;
    private List<String> conditions;
    private int priority = 0;
    
    private boolean selected = false;
    private boolean hovered = false;
    
    public GraphEdge(String sourceNodeId, String targetNodeId) {
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
    }
    
    public void render(GuiGraphics graphics, Font font, GraphNode sourceNode, GraphNode targetNode,
                       float offsetX, float offsetY, float zoom) {
        if (sourceNode == null || targetNode == null) return;
        
        float[] start = sourceNode.getOutputConnectorPos(offsetX, offsetY, zoom);
        float[] end = targetNode.getInputConnectorPos(offsetX, offsetY, zoom);
        
        float startX = start[0];
        float startY = start[1];
        float endX = end[0];
        float endY = end[1];
        
        // Choose color
        int color = selected ? COLOR_SELECTED : (hasConditions() ? COLOR_CONDITIONAL : COLOR_DEFAULT);
        if (hovered) {
            color = (color & 0x00FFFFFF) | 0xFF000000; // Full opacity on hover
        } else {
            color = (color & 0x00FFFFFF) | 0xCC000000; // Slightly transparent
        }
        
        // Draw bezier curve
        drawBezierCurve(graphics, startX, startY, endX, endY, color, zoom);
        
        // Draw arrow at end
        drawArrow(graphics, endX, endY, startX, startY, color, zoom);
        
        // Draw condition label in middle
        if (hasConditions() && zoom > 0.5f) {
            float midX = (startX + endX) / 2;
            float midY = (startY + endY) / 2 - 10 * zoom;
            
            String condLabel = conditions.size() == 1 ? getConditionLabel(conditions.get(0)) 
                : conditions.size() + " 条件";
            
            // Background for label
            int labelWidth = font.width(condLabel) + 4;
            graphics.fill((int)(midX - labelWidth / 2), (int)(midY - 5),
                         (int)(midX + labelWidth / 2), (int)(midY + 8), 0xCC101010);
            
            graphics.drawCenteredString(font, condLabel, (int)midX, (int)midY, COLOR_CONDITIONAL);
        }
    }
    
    private void drawBezierCurve(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color, float zoom) {
        // Control points for smooth curve
        float dx = Math.abs(x2 - x1);
        float controlOffset = Math.max(dx * 0.5f, 50 * zoom);
        
        float cx1 = x1 + controlOffset;
        float cy1 = y1;
        float cx2 = x2 - controlOffset;
        float cy2 = y2;
        
        // Draw curve as line segments
        int segments = Math.max(10, (int)(dx / (10 * zoom)));
        float prevX = x1, prevY = y1;
        
        for (int i = 1; i <= segments; i++) {
            float t = (float) i / segments;
            float u = 1 - t;
            
            // Cubic bezier formula
            float x = u*u*u*x1 + 3*u*u*t*cx1 + 3*u*t*t*cx2 + t*t*t*x2;
            float y = u*u*u*y1 + 3*u*u*t*cy1 + 3*u*t*t*cy2 + t*t*t*y2;
            
            drawLine(graphics, (int)prevX, (int)prevY, (int)x, (int)y, color);
            prevX = x;
            prevY = y;
        }
    }
    
    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        // Bresenham-ish thick line
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        
        if (dx >= dy) {
            // More horizontal
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            for (int x = minX; x <= maxX; x++) {
                float t = dx == 0 ? 0 : (float)(x - x1) / (x2 - x1);
                int y = (int)(y1 + t * (y2 - y1));
                graphics.fill(x, y, x + 1, y + 2, color);
            }
        } else {
            // More vertical
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            for (int y = minY; y <= maxY; y++) {
                float t = dy == 0 ? 0 : (float)(y - y1) / (y2 - y1);
                int x = (int)(x1 + t * (x2 - x1));
                graphics.fill(x, y, x + 2, y + 1, color);
            }
        }
    }
    
    private void drawArrow(GuiGraphics graphics, float tipX, float tipY, float fromX, float fromY, int color, float zoom) {
        float angle = (float) Math.atan2(tipY - fromY, tipX - fromX);
        float arrowSize = 8 * zoom;
        
        // Arrow head points
        float angle1 = angle + (float) Math.PI * 0.85f;
        float angle2 = angle - (float) Math.PI * 0.85f;
        
        int ax1 = (int)(tipX + Math.cos(angle1) * arrowSize);
        int ay1 = (int)(tipY + Math.sin(angle1) * arrowSize);
        int ax2 = (int)(tipX + Math.cos(angle2) * arrowSize);
        int ay2 = (int)(tipY + Math.sin(angle2) * arrowSize);
        
        drawLine(graphics, (int)tipX, (int)tipY, ax1, ay1, color);
        drawLine(graphics, (int)tipX, (int)tipY, ax2, ay2, color);
    }
    
    private String getConditionLabel(String condition) {
        return switch (condition.toUpperCase()) {
            case "COMBAT_WON" -> "战斗胜利";
            case "COMBAT_LOST" -> "战斗失败";
            case "COMBAT_ESCAPED" -> "逃跑";
            case "PUZZLE_SOLVED" -> "谜题解开";
            case "PUZZLE_FAILED" -> "谜题失败";
            case "TASK_COMPLETE" -> "任务完成";
            case "TASK_FAILED" -> "任务失败";
            case "DIALOGUE_CHOICE" -> "对话选择";
            case "VOTE" -> "投票";
            default -> condition;
        };
    }
    
    public boolean containsPoint(float pointX, float pointY, GraphNode sourceNode, GraphNode targetNode,
                                  float offsetX, float offsetY, float zoom) {
        if (sourceNode == null || targetNode == null) return false;
        
        float[] start = sourceNode.getOutputConnectorPos(offsetX, offsetY, zoom);
        float[] end = targetNode.getInputConnectorPos(offsetX, offsetY, zoom);
        
        // Simple distance-to-line check
        float dx = end[0] - start[0];
        float dy = end[1] - start[1];
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return false;
        
        float t = Math.max(0, Math.min(1, 
            ((pointX - start[0]) * dx + (pointY - start[1]) * dy) / (length * length)));
        
        float nearestX = start[0] + t * dx;
        float nearestY = start[1] + t * dy;
        
        float dist = (float) Math.sqrt((pointX - nearestX) * (pointX - nearestX) + 
                                        (pointY - nearestY) * (pointY - nearestY));
        
        return dist < 10 * zoom;
    }
    
    public boolean hasConditions() {
        return conditions != null && !conditions.isEmpty();
    }
    
    // Getters and setters
    public String getSourceNodeId() { return sourceNodeId; }
    public String getTargetNodeId() { return targetNodeId; }
    public List<String> getConditions() { return conditions; }
    public void setConditions(List<String> conditions) { this.conditions = conditions; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isHovered() { return hovered; }
    public void setHovered(boolean hovered) { this.hovered = hovered; }
}
