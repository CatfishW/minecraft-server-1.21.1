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
    
    private static final int HUD_X = 10;
    private static final int HUD_Y = 40;
    private static final int HUD_WIDTH = 200;
    
    // Colors
    private static final int COLOR_NEON_RED = 0xFFE50914;
    private static final int COLOR_BG = 0xC0080808;
    private static final int COLOR_BORDER = 0xFF330011;
    private static final int COLOR_TEXT = 0xFFCCCCCC;
    private static final int COLOR_TEXT_DIM = 0xFF666666;
    private static final int COLOR_OBJECTIVE_ACTIVE = 0xFFFFCC00;
    private static final int COLOR_OBJECTIVE_DONE = 0xFF44FF44;
    private static final int COLOR_TIMER_URGENT = 0xFFFF4444;
    
    // State (would be synced from server)
    private boolean visible = false;
    private String storyTitle = "";
    private String chapterName = "";
    private List<ObjectiveEntry> objectives = new ArrayList<>();
    private List<String> clues = new ArrayList<>();
    private long timerEndTime = 0;
    private boolean timerActive = false;
    
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
        
        int y = HUD_Y;
        
        // Calculate total height needed
        int totalHeight = 40 + objectives.size() * 14 + (clues.isEmpty() ? 0 : 20 + clues.size() * 12);
        if (timerActive) totalHeight += 20;
        
        // Draw background panel
        graphics.fill(HUD_X, y, HUD_X + HUD_WIDTH, y + totalHeight, COLOR_BG);
        drawBorder(graphics, HUD_X, y, HUD_WIDTH, totalHeight);
        
        y += 4;
        
        // Draw story title
        graphics.drawString(font, "【" + storyTitle + "】", HUD_X + 6, y, COLOR_NEON_RED);
        y += 12;
        
        // Draw chapter name
        graphics.drawString(font, chapterName, HUD_X + 6, y, COLOR_TEXT_DIM);
        y += 14;
        
        // Draw separator
        graphics.fill(HUD_X + 6, y, HUD_X + HUD_WIDTH - 6, y + 1, COLOR_BORDER);
        y += 6;
        
        // Draw objectives
        for (ObjectiveEntry obj : objectives) {
            String prefix = obj.complete ? "✓ " : "◉ ";
            int color = obj.complete ? COLOR_OBJECTIVE_DONE : (obj.current ? COLOR_OBJECTIVE_ACTIVE : COLOR_TEXT);
            graphics.drawString(font, prefix + obj.text, HUD_X + 6, y, color);
            y += 12;
        }
        
        // Draw timer if active
        if (timerActive) {
            y += 4;
            long remaining = Math.max(0, timerEndTime - System.currentTimeMillis());
            int seconds = (int)(remaining / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            
            String timeStr = String.format("⏱ %02d:%02d", minutes, seconds);
            int timerColor = remaining < 30000 ? COLOR_TIMER_URGENT : COLOR_NEON_RED;
            
            // Pulse effect when urgent
            if (remaining < 30000 && (System.currentTimeMillis() / 500) % 2 == 0) {
                timerColor = 0xFFFFFFFF;
            }
            
            graphics.drawString(font, timeStr, HUD_X + 6, y, timerColor);
            y += 14;
        }
        
        // Draw clues section
        if (!clues.isEmpty()) {
            y += 4;
            graphics.fill(HUD_X + 6, y, HUD_X + HUD_WIDTH - 6, y + 1, COLOR_BORDER);
            y += 4;
            graphics.drawString(font, "已发现线索:", HUD_X + 6, y, COLOR_TEXT_DIM);
            y += 12;
            
            for (String clue : clues) {
                graphics.drawString(font, "📋 " + clue, HUD_X + 10, y, COLOR_TEXT);
                y += 10;
            }
        }
        
        // Draw WarmPixel branding at bottom
        String branding = "WarmPixel原创";
        int brandingWidth = font.width(branding);
        graphics.drawString(font, branding, HUD_X + HUD_WIDTH - brandingWidth - 6, 
            HUD_Y + totalHeight - 12, 0x80666666);
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
        
        // Neon corner accents
        int cs = 6;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
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
        visible = false;
    }
    
    public record ObjectiveEntry(String text, boolean complete, boolean current) {}
}
