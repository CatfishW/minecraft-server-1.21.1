package com.warmpixel.storyadventure.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom Stranger Things themed button - no vanilla styling.
 * Neon red glow effect with dark background, 80s synth-wave aesthetic.
 */
public class StrangerButton extends AbstractWidget {
    // Color scheme (Stranger Things neon red theme)
    private static final int COLOR_NEON_RED = 0xFF3BB6A6;       // Accent teal
    private static final int COLOR_NEON_GLOW = 0x663BB6A6;      // Soft glow
    private static final int COLOR_DARK_BG = 0xFF0E1218;        // Neutral dark
    private static final int COLOR_HOVER_BG = 0xFF151B22;       // Slight lift on hover
    private static final int COLOR_TEXT = 0xFFE6EEF3;           // Light text
    private static final int COLOR_TEXT_HOVER = 0xFFFFFFFF;     // White on hover
    private static final int COLOR_BORDER = 0xFF1D252E;         // Subtle border
    
    private final Runnable onPress;
    private boolean glowPulse = true;
    private long creationTime;
    
    public StrangerButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.creationTime = System.currentTimeMillis();
    }
    
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered();
        
        // Calculate pulsing glow effect
        float pulsePhase = ((System.currentTimeMillis() - creationTime) % 2000) / 2000f;
        float glowIntensity = glowPulse ? (float)(0.5 + 0.5 * Math.sin(pulsePhase * Math.PI * 2)) : 1f;
        
        // Draw outer glow (subtle)
        if (hovered || glowPulse) {
            int glowAlpha = (int)(36 * glowIntensity);
            int glowColor = (glowAlpha << 24) | (COLOR_NEON_GLOW & 0x00FFFFFF);
            graphics.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, glowColor);
        }
        
        // Draw background
        int bgColor = hovered ? COLOR_HOVER_BG : COLOR_DARK_BG;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
        
        // Draw border
        int borderColor = hovered ? COLOR_NEON_RED : COLOR_BORDER;
        
        // Top border
        graphics.fill(getX(), getY(), getX() + width, getY() + 1, borderColor);
        // Bottom border
        graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor);
        // Left border
        graphics.fill(getX(), getY(), getX() + 1, getY() + height, borderColor);
        // Right border
        graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor);
        
        // Draw top accent strip
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + 3, borderColor);
        
        // Draw text centered
        int textColor = hovered ? COLOR_TEXT_HOVER : COLOR_TEXT;
        graphics.drawCenteredString(
            net.minecraft.client.Minecraft.getInstance().font,
            getMessage(),
            getX() + width / 2,
            getY() + (height - 8) / 2,
            textColor
        );
    }
    
    @Override
    public void onClick(double mouseX, double mouseY) {
        if (onPress != null) {
            onPress.run();
        }
    }
    
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
    
    public StrangerButton setGlowPulse(boolean pulse) {
        this.glowPulse = pulse;
        return this;
    }
}
