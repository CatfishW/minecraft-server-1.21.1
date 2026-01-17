package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Base screen for all Stranger Things themed story screens.
 * Dark background with neon accents, no vanilla UI elements.
 */
public abstract class StrangerScreen extends Screen {
    // Stranger Things color palette
    protected static final int COLOR_BG_DARK = 0xF0080A0E;       // Neutral dark base
    protected static final int COLOR_BG_GRADIENT = 0xF0101318;   // Subtle cool tint
    protected static final int COLOR_NEON_RED = 0xFF3BB6A6;      // Accent teal
    protected static final int COLOR_NEON_PINK = 0xFF6BE0CF;     // Soft accent
    protected static final int COLOR_TEXT_TITLE = 0xFFE6EEF3;    // Light title text
    protected static final int COLOR_TEXT_BODY = 0xFFCBD3DA;     // Light gray body
    protected static final int COLOR_TEXT_DIM = 0xFF8A949C;      // Dimmed text
    protected static final int COLOR_BORDER = 0xFF1D252E;        // Subtle border
    protected static final int COLOR_SCANLINE = 0x04FFFFFF;      // Very subtle scanline
    
    protected final List<StrangerButton> strangerButtons = new ArrayList<>();
    protected long screenOpenTime;
    
    protected StrangerScreen(Component title) {
        super(title);
        this.screenOpenTime = System.currentTimeMillis();
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render dark background with vignette effect
        renderStrangerBackground(graphics);
        
        // Render title with glow
        renderTitle(graphics);
        
        // Render custom buttons
        for (StrangerButton button : strangerButtons) {
            button.render(graphics, mouseX, mouseY, partialTick);
        }
        
        // Render content (subclass implementation)
        renderContent(graphics, mouseX, mouseY, partialTick);
        
    }
    
    protected void renderStrangerBackground(GuiGraphics graphics) {
        // Full screen dark gradient
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Base dark fill
        graphics.fill(0, 0, width, height, COLOR_BG_DARK);
        
        // Radial vignette effect (darker at edges)
        for (int layer = 0; layer < 5; layer++) {
            int alpha = 20 + layer * 8;
            int vignetteColor = (alpha << 24);
            int inset = layer * 40;
            
            // Top
            graphics.fill(0, 0, width, inset, vignetteColor);
            // Bottom
            graphics.fill(0, height - inset, width, height, vignetteColor);
            // Left
            graphics.fill(0, 0, inset, height, vignetteColor);
            // Right
            graphics.fill(width - inset, 0, width, height, vignetteColor);
        }
        
        // Subtle neutral glow in center
        int glowRadius = 120;
        int glowColor = 0x0C1D2630;
        graphics.fill(centerX - glowRadius, centerY - glowRadius,
            centerX + glowRadius, centerY + glowRadius, glowColor);
    }
    
    protected void renderScanlines(GuiGraphics graphics) {
        // Intentionally no-op to keep the UI clean and modern.
    }
    
    protected void renderTitle(GuiGraphics graphics) {
        // Pulsing glow effect
        float pulse = (float)(0.7 + 0.3 * Math.sin((System.currentTimeMillis() - screenOpenTime) / 500.0));
        int glowAlpha = (int)(40 * pulse);
        int glowColor = (glowAlpha << 24) | (COLOR_NEON_PINK & 0x00FFFFFF);
        
        String titleText = title.getString();
        int titleWidth = font.width(titleText);
        int titleX = (width - titleWidth) / 2;
        int titleY = 20;
        
        // Draw glow behind text
        graphics.fill(titleX - 10, titleY - 4, titleX + titleWidth + 10, titleY + 14, glowColor);
        
        // Draw title text
        graphics.drawString(font, title, titleX, titleY, COLOR_TEXT_TITLE);
        
        // Draw underline accent
        int underlineY = titleY + 12;
        graphics.fill(titleX - 4, underlineY, titleX + titleWidth + 4, underlineY + 1, COLOR_NEON_RED);
    }
    
    protected void renderBorderFrame(GuiGraphics graphics) {
        // Intentionally no-op to avoid heavy corner framing.
    }
    
    /**
     * Override to render screen-specific content.
     */
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
    
    protected StrangerButton addStrangerButton(int x, int y, int width, int height, 
                                                Component text, Runnable onPress) {
        StrangerButton button = new StrangerButton(x, y, width, height, text, onPress);
        strangerButtons.add(button);
        addRenderableWidget(button);
        return button;
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
