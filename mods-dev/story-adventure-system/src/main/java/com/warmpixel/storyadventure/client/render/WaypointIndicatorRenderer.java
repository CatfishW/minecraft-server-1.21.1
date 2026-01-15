package com.warmpixel.storyadventure.client.render;

import com.warmpixel.storyadventure.core.waypoint.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders waypoint indicators on the player's HUD.
 * Shows on-screen markers for nearby waypoints and off-screen arrows for distant ones.
 */
public class WaypointIndicatorRenderer implements HudRenderCallback {
    
    private static final int INDICATOR_SIZE = 16;
    private static final int MARGIN = 30;
    private static final double ON_SCREEN_MAX_DISTANCE = 100.0;
    
    private static WaypointIndicatorRenderer instance;
    private List<Waypoint> activeWaypoints = new ArrayList<>();
    private boolean enabled = false;
    
    public static void register() {
        instance = new WaypointIndicatorRenderer();
        HudRenderCallback.EVENT.register(instance);
    }
    
    public static WaypointIndicatorRenderer getInstance() {
        return instance;
    }
    
    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!enabled || activeWaypoints.isEmpty()) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        
        Vec3 playerPos = mc.player.position();
        float playerYaw = mc.player.getYRot();
        float playerPitch = mc.player.getXRot();
        
        Font font = mc.font;
        
        for (Waypoint waypoint : activeWaypoints) {
            Vec3 wpPos = waypoint.getPosition();
            double dx = wpPos.x - playerPos.x;
            double dy = wpPos.y - playerPos.y;
            double dz = wpPos.z - playerPos.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            
            // Calculate angle to waypoint
            double angleToTarget = Math.toDegrees(Math.atan2(-dx, dz));
            double relativeAngle = angleToTarget - playerYaw;
            
            // Normalize angle
            while (relativeAngle > 180) relativeAngle -= 360;
            while (relativeAngle < -180) relativeAngle += 360;
            
            // Check if roughly on-screen (within FOV)
            boolean onScreen = Math.abs(relativeAngle) < 50 && distance < ON_SCREEN_MAX_DISTANCE;
            
            if (onScreen) {
                // Render on-screen marker
                renderOnScreenMarker(graphics, font, waypoint, centerX, centerY, relativeAngle, dy, distance);
            } else {
                // Render off-screen arrow
                renderOffScreenArrow(graphics, font, waypoint, screenWidth, screenHeight, centerX, centerY, relativeAngle, distance);
            }
        }
    }
    
    private void renderOnScreenMarker(GuiGraphics graphics, Font font, Waypoint waypoint,
                                       int centerX, int centerY, double relativeAngle, double dy, double distance) {
        // Calculate screen position based on angle
        int offsetX = (int)(Math.sin(Math.toRadians(relativeAngle)) * 60);
        int offsetY = (int)(-dy * 2);
        
        int x = centerX + offsetX - INDICATOR_SIZE / 2;
        int y = centerY + offsetY - INDICATOR_SIZE / 2;
        
        int color = waypoint.getColor();
        
        // Draw icon background
        int bgColor = (0x80 << 24) | (color & 0x00FFFFFF);
        graphics.fill(x - 2, y - 2, x + INDICATOR_SIZE + 2, y + INDICATOR_SIZE + 2, bgColor);
        
        // Draw icon symbol
        String symbol = waypoint.getIcon().getSymbol();
        int symbolWidth = font.width(symbol);
        graphics.drawString(font, symbol, x + INDICATOR_SIZE / 2 - symbolWidth / 2, y + 4, color);
        
        // Draw label and distance
        if (waypoint.showsDistance()) {
            String info = String.format("%s (%.0fm)", waypoint.getLabel(), distance);
            int infoWidth = font.width(info);
            graphics.drawString(font, info, x + INDICATOR_SIZE / 2 - infoWidth / 2, y + INDICATOR_SIZE + 4, color);
        }
    }
    
    private void renderOffScreenArrow(GuiGraphics graphics, Font font, Waypoint waypoint,
                                       int screenWidth, int screenHeight, int centerX, int centerY,
                                       double relativeAngle, double distance) {
        int color = waypoint.getColor();
        
        // Calculate screen edge position
        double rad = Math.toRadians(relativeAngle);
        double projX = Math.sin(rad);
        double projY = -Math.cos(rad) * 0.3;
        
        double maxX = (screenWidth / 2.0) - MARGIN - INDICATOR_SIZE;
        double maxY = (screenHeight / 2.0) - MARGIN - INDICATOR_SIZE;
        
        double scale = Math.min(maxX / Math.max(0.001, Math.abs(projX)), 
                                maxY / Math.max(0.001, Math.abs(projY)));
        
        int x = (int)(centerX + projX * scale);
        int y = (int)(centerY + projY * scale);
        
        // Clamp to screen
        x = Math.max(MARGIN, Math.min(screenWidth - MARGIN - INDICATOR_SIZE, x));
        y = Math.max(MARGIN, Math.min(screenHeight - MARGIN - INDICATOR_SIZE, y));
        
        // Draw arrow with pulsing effect
        long pulse = System.currentTimeMillis() % 1000;
        float pulseAlpha = 0.7f + 0.3f * (float)Math.sin(pulse / 1000.0 * Math.PI * 2);
        int pulseColor = ((int)(pulseAlpha * 255) << 24) | (color & 0x00FFFFFF);
        
        // Draw glow
        graphics.fill(x - 3, y - 3, x + INDICATOR_SIZE + 3, y + INDICATOR_SIZE + 3, 
            (0x40 << 24) | (color & 0x00FFFFFF));
        
        // Draw arrow box
        graphics.fill(x, y, x + INDICATOR_SIZE, y + INDICATOR_SIZE, pulseColor);
        
        // Draw direction indicator
        String arrow = getDirectionArrow(relativeAngle);
        int arrowWidth = font.width(arrow);
        graphics.drawString(font, arrow, x + INDICATOR_SIZE / 2 - arrowWidth / 2, y + 4, 0xFFFFFFFF);
        
        // Draw distance below
        String distStr = String.format("%.0fm", distance);
        int distWidth = font.width(distStr);
        graphics.drawString(font, distStr, x + INDICATOR_SIZE / 2 - distWidth / 2, y + INDICATOR_SIZE + 2, color);
    }
    
    private String getDirectionArrow(double angle) {
        if (angle > -22.5 && angle <= 22.5) return "↑";
        if (angle > 22.5 && angle <= 67.5) return "↗";
        if (angle > 67.5 && angle <= 112.5) return "→";
        if (angle > 112.5 && angle <= 157.5) return "↘";
        if (angle > 157.5 || angle <= -157.5) return "↓";
        if (angle > -157.5 && angle <= -112.5) return "↙";
        if (angle > -112.5 && angle <= -67.5) return "←";
        if (angle > -67.5 && angle <= -22.5) return "↖";
        return "●";
    }
    
    // Public API
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public void setWaypoints(List<Waypoint> waypoints) {
        this.activeWaypoints = new ArrayList<>(waypoints);
    }
    
    public void addWaypoint(Waypoint waypoint) {
        activeWaypoints.add(waypoint);
    }
    
    public void removeWaypoint(String id) {
        activeWaypoints.removeIf(w -> w.getId().equals(id));
    }
    
    public void clear() {
        activeWaypoints.clear();
    }
}
