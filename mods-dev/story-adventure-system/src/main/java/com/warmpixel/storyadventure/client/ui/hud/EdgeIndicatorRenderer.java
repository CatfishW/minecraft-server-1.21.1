package com.warmpixel.storyadventure.client.ui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Off-screen edge indicators pointing to objectives.
 * Stranger Things neon style arrows at screen edges.
 */
public class EdgeIndicatorRenderer implements HudRenderCallback {
    
    private static final int COLOR_OBJECTIVE = 0xFFFFCC00;
    private static final int COLOR_DANGER = 0xFFFF4444;
    private static final int COLOR_CLUE = 0xFF44CCFF;
    
    private static final int INDICATOR_SIZE = 12;
    private static final int MARGIN = 20;
    
    private static EdgeIndicatorRenderer instance;
    private List<IndicatorTarget> targets = new ArrayList<>();
    private boolean enabled = false;
    
    public static void register() {
        instance = new EdgeIndicatorRenderer();
        HudRenderCallback.EVENT.register(instance);
    }
    
    public static EdgeIndicatorRenderer getInstance() {
        return instance;
    }
    
    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!enabled || targets.isEmpty()) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        
        Vec3 playerPos = mc.player.position();
        float playerYaw = mc.player.getYRot();
        
        for (IndicatorTarget target : targets) {
            // Calculate direction to target
            double dx = target.x - playerPos.x;
            double dz = target.z - playerPos.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            
            if (distance < 5) continue; // Too close, don't show indicator
            
            // Calculate angle to target relative to player view
            double angleToTarget = Math.toDegrees(Math.atan2(-dx, dz));
            double relativeAngle = angleToTarget - playerYaw;
            
            // Normalize angle to -180 to 180
            while (relativeAngle > 180) relativeAngle -= 360;
            while (relativeAngle < -180) relativeAngle += 360;
            
            // Check if target is roughly on screen (within ~60 degree FOV)
            if (Math.abs(relativeAngle) < 50) continue;
            
            // Calculate screen edge position
            int indicatorX, indicatorY;
            double rad = Math.toRadians(relativeAngle);
            
            // Project to screen edge
            double projX = Math.sin(rad);
            double projY = -Math.cos(rad) * 0.3; // Squish vertical
            
            // Scale to screen with margins
            double maxX = (screenWidth / 2.0) - MARGIN - INDICATOR_SIZE;
            double maxY = (screenHeight / 2.0) - MARGIN - INDICATOR_SIZE;
            
            double scale = Math.min(maxX / Math.abs(projX), maxY / Math.max(0.1, Math.abs(projY)));
            
            indicatorX = (int)(centerX + projX * scale);
            indicatorY = (int)(centerY + projY * scale);
            
            // Clamp to screen edges
            indicatorX = Math.max(MARGIN, Math.min(screenWidth - MARGIN - INDICATOR_SIZE, indicatorX));
            indicatorY = Math.max(MARGIN, Math.min(screenHeight - MARGIN - INDICATOR_SIZE, indicatorY));
            
            // Draw indicator
            drawIndicator(graphics, indicatorX, indicatorY, target.color, relativeAngle, distance, target.label);
        }
    }
    
    private void drawIndicator(GuiGraphics graphics, int x, int y, int color, double angle, double distance, String label) {
        // Draw arrow pointing in direction
        int arrowSize = INDICATOR_SIZE;
        
        // Determine arrow direction (simplified to 8 directions)
        int dir = (int)((angle + 180 + 22.5) / 45) % 8;
        
        // Draw outer glow
        int glowColor = (0x40 << 24) | (color & 0x00FFFFFF);
        graphics.fill(x - 2, y - 2, x + arrowSize + 2, y + arrowSize + 2, glowColor);
        
        // Draw arrow shape based on direction
        drawArrow(graphics, x, y, arrowSize, dir, color);
        
        // Draw distance label
        String distStr = String.format("%.0fm", distance);
        Minecraft mc = Minecraft.getInstance();
        int labelWidth = mc.font.width(distStr);
        graphics.drawString(mc.font, distStr, x + arrowSize / 2 - labelWidth / 2, y + arrowSize + 2, color);
    }
    
    private void drawArrow(GuiGraphics graphics, int x, int y, int size, int direction, int color) {
        // Simple arrow drawing based on 8 directions
        int darkColor = darkenColor(color, 0.6f);
        
        // Background
        graphics.fill(x, y, x + size, y + size, darkColor);
        
        // Arrow symbol - draw based on direction
        int cx = x + size / 2;
        int cy = y + size / 2;
        int halfSize = size / 3;
        
        switch (direction) {
            case 0 -> { // Up
                graphics.fill(cx - 1, cy - halfSize, cx + 1, cy + halfSize, color);
                graphics.fill(cx - halfSize, cy - halfSize + 2, cx + halfSize, cy - halfSize + 4, color);
            }
            case 2 -> { // Right
                graphics.fill(cx - halfSize, cy - 1, cx + halfSize, cy + 1, color);
                graphics.fill(cx + halfSize - 4, cy - halfSize, cx + halfSize - 2, cy + halfSize, color);
            }
            case 4 -> { // Down
                graphics.fill(cx - 1, cy - halfSize, cx + 1, cy + halfSize, color);
                graphics.fill(cx - halfSize, cy + halfSize - 4, cx + halfSize, cy + halfSize - 2, color);
            }
            case 6 -> { // Left
                graphics.fill(cx - halfSize, cy - 1, cx + halfSize, cy + 1, color);
                graphics.fill(cx - halfSize + 2, cy - halfSize, cx - halfSize + 4, cy + halfSize, color);
            }
            default -> { // Diagonal or other
                graphics.fill(cx - halfSize, cy - 1, cx + halfSize, cy + 1, color);
                graphics.fill(cx - 1, cy - halfSize, cx + 1, cy + halfSize, color);
            }
        }
    }
    
    private int darkenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int)(((color >> 16) & 0xFF) * factor);
        int g = (int)(((color >> 8) & 0xFF) * factor);
        int b = (int)((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    // Public API
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public void addTarget(double x, double y, double z, int color, String label) {
        targets.add(new IndicatorTarget(x, y, z, color, label));
    }
    
    public void clearTargets() {
        targets.clear();
    }
    
    public record IndicatorTarget(double x, double y, double z, int color, String label) {}
}
