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
    protected static final int COLOR_BG_DARK = 0xF0050505;       // Near black with transparency
    protected static final int COLOR_BG_GRADIENT = 0xF0100808;   // Dark red tint
    protected static final int COLOR_NEON_RED = 0xFFE50914;      // Netflix/Stranger Things red
    protected static final int COLOR_NEON_PINK = 0xFFFF3366;     // Neon pink accent
    protected static final int COLOR_TEXT_TITLE = 0xFFE50914;    // Red title text
    protected static final int COLOR_TEXT_BODY = 0xFFCCCCCC;     // Light gray body
    protected static final int COLOR_TEXT_DIM = 0xFF666666;      // Dimmed text
    protected static final int COLOR_BORDER = 0xFF330011;        // Dark red border
    protected static final int COLOR_SCANLINE = 0x08FFFFFF;      // CRT scanline effect
    
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
        
        // Render CRT scanline effect
        renderScanlines(graphics);
        
        // Render title with glow
        renderTitle(graphics);
        
        // Render custom buttons
        for (StrangerButton button : strangerButtons) {
            button.render(graphics, mouseX, mouseY, partialTick);
        }
        
        // Render content (subclass implementation)
        renderContent(graphics, mouseX, mouseY, partialTick);
        
        // Render border frame
        renderBorderFrame(graphics);
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
        
        // Subtle red glow in center
        int glowRadius = 100;
        int glowColor = 0x10E50914;
        graphics.fill(centerX - glowRadius, centerY - glowRadius, 
                     centerX + glowRadius, centerY + glowRadius, glowColor);
    }
    
    protected void renderScanlines(GuiGraphics graphics) {
        // CRT monitor scanline effect for 80s aesthetic
        for (int y = 0; y < height; y += 2) {
            graphics.fill(0, y, width, y + 1, COLOR_SCANLINE);
        }
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
        graphics.fill(titleX - 5, underlineY, titleX + titleWidth + 5, underlineY + 1, COLOR_NEON_RED);
    }
    
    protected void renderBorderFrame(GuiGraphics graphics) {
        int margin = 8;
        int cornerSize = 20;
        
        // Corner accents (Stranger Things style)
        // Top-left
        graphics.fill(margin, margin, margin + cornerSize, margin + 2, COLOR_NEON_RED);
        graphics.fill(margin, margin, margin + 2, margin + cornerSize, COLOR_NEON_RED);
        
        // Top-right
        graphics.fill(width - margin - cornerSize, margin, width - margin, margin + 2, COLOR_NEON_RED);
        graphics.fill(width - margin - 2, margin, width - margin, margin + cornerSize, COLOR_NEON_RED);
        
        // Bottom-left
        graphics.fill(margin, height - margin - 2, margin + cornerSize, height - margin, COLOR_NEON_RED);
        graphics.fill(margin, height - margin - cornerSize, margin + 2, height - margin, COLOR_NEON_RED);
        
        // Bottom-right
        graphics.fill(width - margin - cornerSize, height - margin - 2, width - margin, height - margin, COLOR_NEON_RED);
        graphics.fill(width - margin - 2, height - margin - cornerSize, width - margin, height - margin, COLOR_NEON_RED);
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
