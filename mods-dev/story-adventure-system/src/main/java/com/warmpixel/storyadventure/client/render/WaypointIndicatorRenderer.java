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
    
    private static final int BASE_INDICATOR_SIZE = 12;
    private static final int MIN_INDICATOR_SIZE = 8;
    private static final int MAX_INDICATOR_SIZE = 20;
    private static final int MARGIN = 25;
    
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
        // Disabled HUD indicators as requested, using 3D world indicators instead
        if (true) return;
        
        if (!enabled || activeWaypoints.isEmpty()) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameRenderer == null) return;
        
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;
        
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        
        Font font = mc.font;
        
        // Get camera rotation (Minecraft conventions)
        // YRot (yaw): 0 = South (+Z), 90 = West (-X), increases counterclockwise from above
        // XRot (pitch): positive = looking down, negative = looking up
        float camYaw = camera.getYRot();
        float camPitch = camera.getXRot();
        
        double yawRad = Math.toRadians(camYaw);
        double pitchRad = Math.toRadians(camPitch);
        
        // Precompute trig values
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        double sinPitch = Math.sin(pitchRad);
        double cosPitch = Math.cos(pitchRad);
        
        // Build camera basis vectors using Minecraft's coordinate system
        // Forward: unit vector in the direction the camera is looking
        double forwardX = -sinYaw * cosPitch;
        double forwardY = -sinPitch;
        double forwardZ = cosYaw * cosPitch;
        
        // Right: unit vector pointing to the right of the camera (always horizontal)
        double rightX = cosYaw;
        double rightY = 0;
        double rightZ = sinYaw;
        
        // Up: perpendicular to forward and right, computed via cross product
        // For correct orientation: up = right × forward (not forward × right)
        double upX = rightY * forwardZ - rightZ * forwardY;
        double upY = rightZ * forwardX - rightX * forwardZ;
        double upZ = rightX * forwardY - rightY * forwardX;
        
        // Normalize up vector (should already be unit length, but ensure it)
        double upLen = Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        if (upLen > 0.0001) {
            upX /= upLen;
            upY /= upLen;
            upZ /= upLen;
        }
        
        // Get actual rendered FOV (accounts for sprinting, effects, etc.) via Mixin Accessor
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        double fovY = ((com.warmpixel.storyadventure.mixin.GameRendererAccessor)mc.gameRenderer).invokeGetFov(camera, partialTicks, true);
        double fovYRad = Math.toRadians(fovY);
        double aspectRatio = (double) screenWidth / screenHeight;
        
        double tanHalfFovY = Math.tan(fovYRad / 2.0);
        double tanHalfFovX = tanHalfFovY * aspectRatio;
        
        for (Waypoint waypoint : activeWaypoints) {
            // Offset waypoint position slightly above ground for visibility
            Vec3 wpPos = waypoint.getPosition().add(0, 1.5, 0);
            
            // Vector from camera to waypoint
            double dx = wpPos.x - cameraPos.x;
            double dy = wpPos.y - cameraPos.y;
            double dz = wpPos.z - cameraPos.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            
            if (distance < 0.5) continue;
            
            // Transform waypoint position to camera space using dot products
            // camZ: depth (positive = in front of camera)
            // camX: horizontal offset (positive = to the right)
            // camY: vertical offset (positive = above, appears higher on screen)
            double camZ = dx * forwardX + dy * forwardY + dz * forwardZ;
            double camX = dx * rightX + dy * rightY + dz * rightZ;
            double camY = dx * upX + dy * upY + dz * upZ;
            
            boolean inFront = camZ > 0.1;
            
            double screenX, screenY;
            
            if (inFront) {
                // Standard perspective projection for points in front of camera
                // Maps camera space to normalized device coordinates, then to screen space
                double ndcX = camX / (camZ * tanHalfFovX);
                double ndcY = camY / (camZ * tanHalfFovY);
                
                screenX = centerX + ndcX * centerX;
                screenY = centerY - ndcY * centerY; // Subtract because screen Y increases downward
            } else {
                // Point is behind or at camera - project to screen edge
                // Use the horizontal (camX) and vertical (camY) components to determine direction
                double camXYLen = Math.sqrt(camX * camX + camY * camY);
                
                if (camXYLen > 0.001) {
                    // Normalize direction in camera XY plane
                    double normX = camX / camXYLen;
                    double normY = camY / camXYLen;
                    
                    // Project far beyond screen in this direction
                    // The direction is correct: if waypoint is behind-right, 
                    // player needs to turn right to face it
                    screenX = centerX + normX * screenWidth * 2;
                    screenY = centerY - normY * screenHeight * 2;
                } else {
                    // Directly behind - show at bottom center
                    screenX = centerX;
                    screenY = screenHeight * 2;
                }
            }
            
            // Determine if waypoint is visible on screen (with margin for indicator size)
            boolean onScreen = inFront && 
                              screenX >= MARGIN && screenX <= screenWidth - MARGIN && 
                              screenY >= MARGIN && screenY <= screenHeight - MARGIN;
            
            if (onScreen) {
                renderOnScreenMarker(graphics, font, waypoint, (int)screenX, (int)screenY, distance);
            } else {
                // Calculate position at screen edge for off-screen indicator
                double dirX = screenX - centerX;
                double dirY = screenY - centerY;
                
                int edgeX, edgeY;
                
                if (Math.abs(dirX) < 0.001 && Math.abs(dirY) < 0.001) {
                    // Edge case: direction is zero (shouldn't happen often)
                    edgeX = (int)centerX;
                    edgeY = screenHeight - MARGIN;
                } else {
                    // Find where the line from center to projected position intersects screen boundary
                    double scale = calculateBoundaryScale(dirX, dirY, centerX, centerY, 
                                                          screenWidth, screenHeight, MARGIN);
                    
                    edgeX = (int)(centerX + dirX * scale);
                    edgeY = (int)(centerY + dirY * scale);
                    
                    // Ensure we stay within the valid screen area
                    edgeX = Math.max(MARGIN, Math.min(screenWidth - MARGIN, edgeX));
                    edgeY = Math.max(MARGIN, Math.min(screenHeight - MARGIN, edgeY));
                }
                
                renderOffScreenArrow(graphics, font, waypoint, screenWidth, screenHeight, 
                                    edgeX, edgeY, centerX, centerY, distance);
            }
        }
    }
    
    /**
     * Calculates the scale factor to reach the screen boundary from center.
     * Returns the smallest positive scale that places the point on the boundary.
     */
    private double calculateBoundaryScale(double dirX, double dirY, double centerX, double centerY,
                                          int screenWidth, int screenHeight, int margin) {
        double scale = Double.MAX_VALUE;
        
        // Calculate scale for each boundary
        if (dirX > 0.001) {
            // Right boundary
            double s = (screenWidth - margin - centerX) / dirX;
            if (s > 0 && s < scale) scale = s;
        } else if (dirX < -0.001) {
            // Left boundary
            double s = (margin - centerX) / dirX;
            if (s > 0 && s < scale) scale = s;
        }
        
        if (dirY > 0.001) {
            // Bottom boundary
            double s = (screenHeight - margin - centerY) / dirY;
            if (s > 0 && s < scale) scale = s;
        } else if (dirY < -0.001) {
            // Top boundary
            double s = (margin - centerY) / dirY;
            if (s > 0 && s < scale) scale = s;
        }
        
        return (scale == Double.MAX_VALUE || scale <= 0) ? 1.0 : scale;
    }
    
    private void renderOnScreenMarker(GuiGraphics graphics, Font font, Waypoint waypoint,
                                       int screenX, int screenY, double distance) {
        // Calculate distance-based size (bigger when closer)
        double sizeMultiplier = Math.max(0.5, Math.min(2.0, 50.0 / Math.max(10, distance)));
        int baseSize = (int)(BASE_INDICATOR_SIZE * sizeMultiplier);
        baseSize = Math.max(MIN_INDICATOR_SIZE, Math.min(MAX_INDICATOR_SIZE, baseSize));
        
        // Smooth animation using time-based easing
        long time = System.currentTimeMillis();
        float bobAmount = (float) Math.sin(time / 400.0) * 2.0f;
        float pulseScale = 0.95f + 0.1f * (float) Math.sin(time / 300.0);
        
        int size = (int)(baseSize * pulseScale);
        int x = screenX - size / 2;
        int y = screenY - size / 2 + (int)bobAmount;
        
        int color = waypoint.getColor();
        
        // Draw outer glow
        int glowAlpha = (int)(40 + 20 * Math.sin(time / 500.0));
        graphics.fill(x - 3, y - 3, x + size + 3, y + size + 3, (glowAlpha << 24) | (color & 0x00FFFFFF));
        
        // Draw icon background
        int bgAlpha = (int)(220 + 30 * Math.sin(time / 400.0));
        graphics.fill(x, y, x + size, y + size, (bgAlpha << 24) | (color & 0x00FFFFFF));
        
        // Draw icon symbol
        String symbol = "◉";
        int symbolWidth = font.width(symbol);
        graphics.drawString(font, symbol, x + size / 2 - symbolWidth / 2, y + size / 2 - 4, 0xFFFFFFFF);
        
        // Draw label and distance
        if (waypoint.showsDistance()) {
            String info = waypoint.getLabel() + " " + (int)distance + "m";
            int infoWidth = font.width(info);
            int labelX = x + size / 2 - infoWidth / 2;
            int labelY = y + size + 4;
            
            // Background for better readability
            graphics.fill(labelX - 3, labelY - 1, labelX + infoWidth + 3, labelY + 10, 0xC0000000);
            graphics.drawString(font, info, labelX, labelY, color);
        }
    }
    
    private void renderOffScreenArrow(GuiGraphics graphics, Font font, Waypoint waypoint,
                                       int screenWidth, int screenHeight, 
                                       int edgeX, int edgeY, 
                                       float centerX, float centerY, double distance) {
        int color = waypoint.getColor();
        int size = BASE_INDICATOR_SIZE;
        
        // Adjust position to center the indicator on the edge point
        int x = edgeX - size / 2;
        int y = edgeY - size / 2;
        
        // Ensure indicator stays within screen bounds
        x = Math.max(MARGIN, Math.min(screenWidth - MARGIN - size, x));
        y = Math.max(MARGIN, Math.min(screenHeight - MARGIN - size, y));
        
        // Pulsing animation
        long time = System.currentTimeMillis();
        float pulseAlpha = 0.7f + 0.3f * (float)Math.sin(time / 300.0 * Math.PI);
        int pulseColor = ((int)(pulseAlpha * 255) << 24) | (color & 0x00FFFFFF);
        
        // Draw glow effect
        int glowAlpha = (int)(40 + 20 * Math.sin(time / 400.0));
        graphics.fill(x - 3, y - 3, x + size + 3, y + size + 3, (glowAlpha << 24) | (color & 0x00FFFFFF));
        
        // Draw indicator background
        graphics.fill(x, y, x + size, y + size, pulseColor);
        
        // Calculate arrow direction from screen center to edge position
        float dirX = edgeX - centerX;
        float dirY = edgeY - centerY;
        
        // Calculate angle for arrow selection
        // atan2 with (dirX, -dirY) because:
        // - We want 0° to be "up" on screen (negative Y direction)
        // - dirX positive = right side of screen
        // - dirY positive = bottom of screen (but we want up = 0°, so negate)
        double angle = Math.toDegrees(Math.atan2(dirX, -dirY));
        
        // Get appropriate directional arrow
        String arrow = getDirectionArrow(angle);
        
        int arrowWidth = font.width(arrow);
        graphics.drawString(font, arrow, x + size / 2 - arrowWidth / 2, y + size / 2 - 4, 0xFFFFFFFF);
        
        // Draw distance label
        String distStr = (int)distance + "m";
        int distWidth = font.width(distStr);
        
        // Position label based on edge location
        int labelX = x + size / 2 - distWidth / 2;
        int labelY;
        
        // Determine vertical position based on where the indicator is
        if (edgeY <= MARGIN + size) {
            // At top edge - put label below indicator
            labelY = y + size + 2;
        } else if (edgeY >= screenHeight - MARGIN - size) {
            // At bottom edge - put label above indicator
            labelY = y - 12;
        } else {
            // On side edge - put label below
            labelY = y + size + 2;
        }
        
        // Clamp label position to screen
        labelX = Math.max(2, Math.min(screenWidth - distWidth - 2, labelX));
        labelY = Math.max(2, Math.min(screenHeight - 12, labelY));
        
        // Draw label background and text
        graphics.fill(labelX - 2, labelY - 1, labelX + distWidth + 2, labelY + 10, 0xA0000000);
        graphics.drawString(font, distStr, labelX, labelY, color);
    }
    
    /**
     * Returns an arrow character pointing in the given direction.
     * @param angle Direction in degrees where 0° = up, 90° = right, etc.
     */
    private String getDirectionArrow(double angle) {
        // Normalize angle to -180 to 180 range
        while (angle > 180) angle -= 360;
        while (angle <= -180) angle += 360;
        
        // Map angle to 8-directional arrows
        // Each direction covers a 45° arc centered on that direction
        if (angle > -22.5 && angle <= 22.5) return "↑";      // Up
        if (angle > 22.5 && angle <= 67.5) return "↗";       // Up-Right
        if (angle > 67.5 && angle <= 112.5) return "→";      // Right
        if (angle > 112.5 && angle <= 157.5) return "↘";     // Down-Right
        if (angle > 157.5 || angle <= -157.5) return "↓";    // Down
        if (angle > -157.5 && angle <= -112.5) return "↙";   // Down-Left
        if (angle > -112.5 && angle <= -67.5) return "←";    // Left
        if (angle > -67.5 && angle <= -22.5) return "↖";     // Up-Left
        
        return "●"; // Fallback
    }
    
    // ==================== Public API ====================
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return this.enabled;
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
    
    public List<Waypoint> getActiveWaypoints() {
        return new ArrayList<>(activeWaypoints);
    }
}