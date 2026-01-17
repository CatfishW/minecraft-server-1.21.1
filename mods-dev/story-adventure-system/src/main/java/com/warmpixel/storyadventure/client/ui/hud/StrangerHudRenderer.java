package com.warmpixel.storyadventure.client.ui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Stranger Things themed HUD overlay for story objectives, clues, and timers.
 * Displays in top-left corner with neon styling.
 */
public class StrangerHudRenderer implements HudRenderCallback {
    
    private static final int HUD_X = 12;
    private static final int HUD_Y = 24;
    private static final int HUD_MIN_WIDTH = 210;
    private static final int HUD_MAX_WIDTH = 320;
    private static final int HUD_PADDING_X = 10;
    private static final int HUD_PADDING_Y = 8;
    
    // Colors
    private static final int COLOR_ACCENT = 0xFF4CC9A6;
    private static final int COLOR_BG = 0xCC0B0E12;
    private static final int COLOR_BORDER = 0xFF1E2630;
    private static final int COLOR_TEXT = 0xFFE6E6E6;
    private static final int COLOR_TEXT_DIM = 0xFF9AA4AD;
    private static final int COLOR_OBJECTIVE_ACTIVE = 0xFFFFD166;
    private static final int COLOR_OBJECTIVE_DONE = 0xFF6EE7A6;
    private static final int COLOR_TIMER_URGENT = 0xFFFF6B6B;
    
    // State (would be synced from server)
    private boolean visible = false;
    private String storyTitle = "";
    private String chapterName = "";
    private List<ObjectiveEntry> objectives = new ArrayList<>();
    private List<String> clues = new ArrayList<>();
    private long timerEndTime = 0;
    private boolean timerActive = false;
    private int remainingLives = 0;
    private int maxLives = 0;
    private boolean showLives = false;
    
    private static StrangerHudRenderer instance;
    
    public static void register() {
        instance = new StrangerHudRenderer();
        HudRenderCallback.EVENT.register(instance);
    }
    
    public static StrangerHudRenderer getInstance() {
        return instance;
    }
    
    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!visible) return;
        
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        graphics.pose().pushPose();
        
        // Scale down the UI to 1.5x bigger than before
        float scale = 0.6f;
        int panelWidth = calculatePanelWidth(font, screenWidth, scale);
        int startX = calculatePanelX(panelWidth, screenWidth, scale);
        // Calculate total height needed
        int totalHeight = HUD_PADDING_Y * 2 + 28 + objectives.size() * 13 + (clues.isEmpty() ? 0 : 18 + clues.size() * 11);
        if (timerActive) totalHeight += 20;
        if (showLives) totalHeight += 18;
        
        int startY = calculatePanelY(totalHeight, screenHeight, scale);
        graphics.pose().translate(startX, startY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-startX, -startY, 0);
        
        int y = startY;
        
        // Draw background panel with soft shadow
        graphics.fill(startX + 2, y + 2, startX + panelWidth + 2, y + totalHeight + 2, 0x55000000);
        graphics.fill(startX, y, startX + panelWidth, y + totalHeight, COLOR_BG);
        drawBorder(graphics, startX, y, panelWidth, totalHeight);
        
        y += HUD_PADDING_Y;
        
        // Draw story title
        graphics.drawString(font, "【" + storyTitle + "】", startX + HUD_PADDING_X, y, COLOR_ACCENT);
        y += 12;
        
        // Draw chapter name
        graphics.drawString(font, chapterName, startX + HUD_PADDING_X, y, COLOR_TEXT_DIM);
        y += 12;
        
        // Draw separator
        graphics.fill(startX + HUD_PADDING_X, y, startX + panelWidth - HUD_PADDING_X, y + 1, COLOR_BORDER);
        y += 6;
        
        // Draw objectives
        for (ObjectiveEntry obj : objectives) {
            String prefix = obj.complete ? "✓ " : "◉ ";
            int color = obj.complete ? COLOR_OBJECTIVE_DONE : (obj.current ? COLOR_OBJECTIVE_ACTIVE : COLOR_TEXT);
            graphics.drawString(font, prefix + obj.text, startX + HUD_PADDING_X, y, color);
            y += 11;
        }
        
        // Draw timer if active
        if (timerActive) {
            y += 4;
            long remaining = Math.max(0, timerEndTime - System.currentTimeMillis());
            int seconds = (int)(remaining / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            
            String timeStr = String.format("⏱ %02d:%02d", minutes, seconds);
            int timerColor = remaining < 30000 ? COLOR_TIMER_URGENT : COLOR_ACCENT;
            
            // Pulse effect when urgent
            if (remaining < 30000 && (System.currentTimeMillis() / 500) % 2 == 0) {
                timerColor = 0xFFFFFFFF;
            }
            
            graphics.drawString(font, timeStr, startX + HUD_PADDING_X, y, timerColor);
            y += 14;
        }
        
        // Draw lives display
        if (showLives && maxLives > 0) {
            y += 4;
            graphics.fill(startX + HUD_PADDING_X, y, startX + panelWidth - HUD_PADDING_X, y + 1, COLOR_BORDER);
            y += 4;
            
            // Build lives string with hearts
            StringBuilder livesStr = new StringBuilder();
            livesStr.append("❤ 生命值: ");
            livesStr.append(remainingLives).append(" / ").append(maxLives);
            
            // Color based on remaining lives percentage
            float lifePercent = (float) remainingLives / maxLives;
            int livesColor;
            if (lifePercent > 0.5f) {
                livesColor = 0xFF44FF44; // Green
            } else if (lifePercent > 0.25f) {
                livesColor = 0xFFFFCC00; // Yellow
            } else {
                livesColor = 0xFFFF4444; // Red
                // Pulse when critical
                if ((System.currentTimeMillis() / 500) % 2 == 0) {
                    livesColor = 0xFFFFFFFF;
                }
            }
            
            graphics.drawString(font, livesStr.toString(), startX + HUD_PADDING_X, y, livesColor);
            y += 14;
        }
        
        // Draw clues section
        if (!clues.isEmpty()) {
            y += 4;
            graphics.fill(startX + HUD_PADDING_X, y, startX + panelWidth - HUD_PADDING_X, y + 1, COLOR_BORDER);
            y += 4;
            graphics.drawString(font, "已发现线索:", startX + HUD_PADDING_X, y, COLOR_TEXT_DIM);
            y += 12;
            
            for (String clue : clues) {
                graphics.drawString(font, "• " + clue, startX + HUD_PADDING_X + 4, y, COLOR_TEXT);
                y += 11;
            }
        }
        
        // Draw WarmPixel branding at bottom
        String branding = "WarmPixel原创";
        int brandingWidth = font.width(branding);
        graphics.drawString(font, branding, startX + panelWidth - brandingWidth - HUD_PADDING_X, 
            startY + totalHeight - 12, 0x80666666);

        graphics.pose().popPose();
    }
    
    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        // Top
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        // Bottom
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        // Left
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        // Right
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        // Accent strip
        graphics.fill(x + 1, y + 1, x + w - 1, y + 3, COLOR_ACCENT);
    }
    
    private int calculatePanelWidth(Font font, int screenWidth, float scale) {
        int maxTextWidth = 0;
        maxTextWidth = Math.max(maxTextWidth, font.width("【" + storyTitle + "】"));
        maxTextWidth = Math.max(maxTextWidth, font.width(chapterName));
        
        for (ObjectiveEntry obj : objectives) {
            String prefix = obj.complete ? "✓ " : "◉ ";
            maxTextWidth = Math.max(maxTextWidth, font.width(prefix + obj.text));
        }
        
        if (timerActive) {
            maxTextWidth = Math.max(maxTextWidth, font.width("⏱ 00:00"));
        }
        
        if (showLives) {
            String livesText = "❤ 生命值: " + remainingLives + " / " + maxLives;
            maxTextWidth = Math.max(maxTextWidth, font.width(livesText));
        }
        
        if (!clues.isEmpty()) {
            maxTextWidth = Math.max(maxTextWidth, font.width("已发现线索:"));
            for (String clue : clues) {
                maxTextWidth = Math.max(maxTextWidth, font.width("• " + clue));
            }
        }
        
        int desiredWidth = maxTextWidth + HUD_PADDING_X * 2;
        int maxAllowed = Math.max(160, (int) ((screenWidth - HUD_X * 2) / scale));
        int minAllowed = Math.min(HUD_MIN_WIDTH, maxAllowed);
        
        int targetWidth = Math.min(HUD_MAX_WIDTH, desiredWidth);
        return Math.max(minAllowed, Math.min(targetWidth, maxAllowed));
    }
    
    private int calculatePanelX(int panelWidth, int screenWidth, float scale) {
        int scaledRightMargin = (int) (HUD_X / scale);
        return Math.max(HUD_X, screenWidth - panelWidth - scaledRightMargin);
    }

    private int calculatePanelY(int totalHeight, int screenHeight, float scale) {
        return HUD_Y;
    }
    
    // Public API for updating HUD state
    public void show(String title, String chapter) {
        this.visible = true;
        this.storyTitle = title;
        this.chapterName = chapter;
    }
    
    public void hide() {
        this.visible = false;
    }
    
    public void setObjectives(List<ObjectiveEntry> objectives) {
        this.objectives = new ArrayList<>(objectives);
    }
    
    public void addClue(String clue) {
        if (!clues.contains(clue)) {
            clues.add(clue);
        }
    }
    
    public void startTimer(long durationMs) {
        this.timerActive = true;
        this.timerEndTime = System.currentTimeMillis() + durationMs;
    }
    
    public void stopTimer() {
        this.timerActive = false;
    }
    
    public void reset() {
        objectives.clear();
        clues.clear();
        timerActive = false;
        showLives = false;
        remainingLives = 0;
        maxLives = 0;
        visible = false;
    }
    
    public void setLives(int remaining, int max) {
        this.remainingLives = remaining;
        this.maxLives = max;
        this.showLives = max > 0;
    }
    
    public record ObjectiveEntry(String text, boolean complete, boolean current) {}
}
