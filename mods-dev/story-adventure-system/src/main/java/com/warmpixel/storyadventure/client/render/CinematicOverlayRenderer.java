package com.warmpixel.storyadventure.client.render;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders cinematic overlay effects during cutscenes.
 * Includes letterbox bars, fade effects, skip prompt, and progress bar.
 */
public class CinematicOverlayRenderer implements HudRenderCallback {
    
    private static CinematicOverlayRenderer instance;
    
    // Letterbox settings
    private static final float LETTERBOX_HEIGHT_RATIO = 0.12f; // 12% of screen height for each bar
    private static final int LETTERBOX_COLOR = 0xFF000000; // Pure black
    
    // Fade settings
    private static final int FADE_COLOR_BASE = 0x000000; // Black fade
    
    // Skip prompt
    private static final String SKIP_PROMPT = "Press [ESC] to skip";
    private static final int SKIP_PROMPT_COLOR = 0xAAFFFFFF;
    
    public static void register() {
        instance = new CinematicOverlayRenderer();
        // HudRenderCallback.EVENT.register(instance); // Disabled to prevent double rendering with Mixin
    }
    
    public static CinematicOverlayRenderer getInstance() {
        return instance;
    }
    
    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        CinematicCameraController controller = CinematicCameraController.getInstance();
        
        if (!controller.isActive()) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        
        // Update controller tick
        controller.renderTick(partialTicks);
        
        // Render fade effect (behind letterbox)
        if (controller.isFadeEnabled()) {
            renderFade(graphics, screenWidth, screenHeight, controller.getFadeProgress());
        }
        
        // Render letterbox bars
        if (controller.isLetterboxEnabled()) {
            renderLetterbox(graphics, screenWidth, screenHeight, controller.getLetterboxProgress());
        }
        
        // Render skip prompt
        if (controller.isSkippable()) {
            renderSkipPrompt(graphics, mc.font, screenWidth, screenHeight, controller.getLetterboxProgress());
        }
        
        // Render subtitles
        String subtitle = controller.getCurrentSubtitle();
        if (subtitle != null && !subtitle.isEmpty()) {
            renderSubtitle(graphics, mc.font, screenWidth, screenHeight, subtitle, controller.getLetterboxProgress());
        }
        
        // Render progress bar (optional, subtle)
        renderProgressBar(graphics, screenWidth, screenHeight, controller.getProgress(), controller.getLetterboxProgress());
    }
    
    /**
     * Render letterbox bars at top and bottom of screen.
     */
    private void renderLetterbox(GuiGraphics graphics, int screenWidth, int screenHeight, float progress) {
        if (progress <= 0) return;
        
        int barHeight = (int) (screenHeight * LETTERBOX_HEIGHT_RATIO * progress);
        
        // Top bar
        graphics.fill(0, 0, screenWidth, barHeight, LETTERBOX_COLOR);
        
        // Bottom bar
        graphics.fill(0, screenHeight - barHeight, screenWidth, screenHeight, LETTERBOX_COLOR);
    }
    
    /**
     * Render screen fade effect.
     */
    private void renderFade(GuiGraphics graphics, int screenWidth, int screenHeight, float progress) {
        if (progress <= 0) return;
        
        int alpha = (int) (progress * 255);
        int fadeColor = (alpha << 24) | FADE_COLOR_BASE;
        
        graphics.fill(0, 0, screenWidth, screenHeight, fadeColor);
    }
    
    /**
     * Render skip prompt in the letterbox area.
     */
    private void renderSkipPrompt(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, float letterboxProgress) {
        if (letterboxProgress < 0.5f) return; // Only show when letterbox is mostly visible
        
        // Pulse animation
        long time = System.currentTimeMillis();
        float pulse = 0.6f + 0.4f * (float) Math.sin(time / 500.0);
        int alpha = (int) (pulse * 170);
        int color = (alpha << 24) | 0xFFFFFF;
        
        int barHeight = (int) (screenHeight * LETTERBOX_HEIGHT_RATIO);
        int textWidth = font.width(SKIP_PROMPT);
        int x = screenWidth - textWidth - 20;
        int y = screenHeight - barHeight / 2 - 4;
        
        graphics.drawString(font, SKIP_PROMPT, x, y, color, false);
    }
    
    /**
     * Render a subtle progress bar at the bottom of the letterbox.
     */
    private void renderProgressBar(GuiGraphics graphics, int screenWidth, int screenHeight, 
                                    float progress, float letterboxProgress) {
        if (letterboxProgress < 0.8f) return;
        
        int barHeight = (int) (screenHeight * LETTERBOX_HEIGHT_RATIO);
        int progressBarHeight = 2;
        int progressBarY = screenHeight - barHeight + 2;
        
        // Background
        int bgAlpha = (int) (100 * letterboxProgress);
        graphics.fill(0, progressBarY, screenWidth, progressBarY + progressBarHeight, 
            (bgAlpha << 24) | 0x333333);
        
        // Progress fill
        int fillWidth = (int) (screenWidth * progress);
        int fillAlpha = (int) (200 * letterboxProgress);
        graphics.fill(0, progressBarY, fillWidth, progressBarY + progressBarHeight, 
            (fillAlpha << 24) | 0xFFFFFF);
    }

    /**
     * Render a subtitle at the bottom of the screen.
     * Always renders with full alpha for visibility.
     */
    private void renderSubtitle(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, 
                                 String text, float letterboxProgress) {
        if (text == null || text.isEmpty()) return;

        CinematicCameraController controller = CinematicCameraController.getInstance();
        int displayedChars = controller.getSubtitleDisplayedCharacters();
        String displayText = text.substring(0, Math.min(displayedChars, text.length()));

        // Movie-style positioning: at the bottom
        // Calculate position based on whether letterbox is active
        int letterboxHeight = (int)(screenHeight * LETTERBOX_HEIGHT_RATIO * letterboxProgress);
        
        // Wrap text to fit screen width (with padding)
        int maxWidth = screenWidth - 80;
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = font.split(net.minecraft.network.chat.Component.literal(displayText), maxWidth);
        
        int totalHeight = lines.size() * (font.lineHeight + 2);
        int y = screenHeight - letterboxHeight - totalHeight - 20;
        
        // If no letterbox, position near bottom but not cut off
        if (letterboxProgress < 0.1f) {
            y = screenHeight - totalHeight - 40;
        }

        for (int i = 0; i < lines.size(); i++) {
            net.minecraft.util.FormattedCharSequence line = lines.get(i);
            int textWidth = font.width(line);
            int x = (screenWidth - textWidth) / 2;
            int lineY = y + i * (font.lineHeight + 2);

            // Draw background shadow for better readability
            graphics.fill(x - 4, lineY - 1, x + textWidth + 4, lineY + font.lineHeight + 1, 0x99000000);
            
            // Draw text with white color (full alpha)
            graphics.drawString(font, line, x, lineY, 0xFFFFFFFF, true);
        }
    }
}
