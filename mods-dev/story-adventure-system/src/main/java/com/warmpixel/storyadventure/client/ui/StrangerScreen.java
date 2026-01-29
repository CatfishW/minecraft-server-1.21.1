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
    
    protected int guiLeft;
    protected int guiTop;
    protected int guiWidth;
    protected int guiHeight;
    
    protected StrangerScreen(Component title) {
        super(title);
        this.screenOpenTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();
        this.guiWidth = getWindowWidth();
        this.guiHeight = getWindowHeight();
        this.guiLeft = (this.width - this.guiWidth) / 2;
        this.guiTop = (this.height - this.guiHeight) / 2;
    }

    protected int getWindowWidth() {
        return Math.min(width - 40, 480);
    }

    protected int getWindowHeight() {
        return Math.min(height - 40, 320);
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Full screen background dimming
        graphics.fill(0, 0, width, height, 0x99000000);

        // Render themed window background
        renderStrangerBackground(graphics);
        
        // Render window frame
        renderWindowFrame(graphics);
        
        // Render title with glow
        renderTitle(graphics, mouseX, mouseY);
        
        // Render custom buttons
        for (StrangerButton button : strangerButtons) {
            button.render(graphics, mouseX, mouseY, partialTick);
        }
        
        // Render content (subclass implementation)
        renderContent(graphics, mouseX, mouseY, partialTick);
    }
    
    protected void renderStrangerBackground(GuiGraphics graphics) {
        // Draw the dark background inside the window
        graphics.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + guiHeight, COLOR_BG_DARK);
        
        // Clip decorative elements to window bounds
        graphics.enableScissor(guiLeft, guiTop, guiLeft + guiWidth, guiTop + guiHeight);
        
        // Render 80s Grid
        renderGrid(graphics);
        
        // Subtle neutral glow in window center
        int centerX = guiLeft + guiWidth / 2;
        int centerY = guiTop + guiHeight / 2;
        int glowRadius = Math.min(guiWidth, guiHeight) / 2;
        int glowColor = 0x0C1D2630;
        graphics.fill(centerX - glowRadius, centerY - glowRadius,
            centerX + glowRadius, centerY + glowRadius, glowColor);
            
        renderScanlines(graphics);
        
        graphics.disableScissor();
    }
    
    protected void renderWindowFrame(GuiGraphics graphics) {
        // Main window border (Neon Red)
        renderRectOutline(graphics, guiLeft, guiTop, guiWidth, guiHeight, COLOR_NEON_RED);
        
        // Corner accents
        int cs = 12;
        int thick = 2;
        // Top-Left
        graphics.fill(guiLeft - thick, guiTop - thick, guiLeft + cs, guiTop, COLOR_NEON_RED);
        graphics.fill(guiLeft - thick, guiTop - thick, guiLeft, guiTop + cs, COLOR_NEON_RED);
        // Top-Right
        graphics.fill(guiLeft + guiWidth - cs, guiTop - thick, guiLeft + guiWidth + thick, guiTop, COLOR_NEON_RED);
        graphics.fill(guiLeft + guiWidth, guiTop - thick, guiLeft + guiWidth + thick, guiTop + cs, COLOR_NEON_RED);
        // Bottom-Left
        graphics.fill(guiLeft - thick, guiTop + guiHeight, guiLeft + cs, guiTop + guiHeight + thick, COLOR_NEON_RED);
        graphics.fill(guiLeft - thick, guiTop + guiHeight - cs, guiLeft, guiTop + guiHeight + thick, COLOR_NEON_RED);
        // Bottom-Right
        graphics.fill(guiLeft + guiWidth - cs, guiTop + guiHeight, guiLeft + guiWidth + thick, guiTop + guiHeight + thick, COLOR_NEON_RED);
        graphics.fill(guiLeft + guiWidth, guiTop + guiHeight - cs, guiLeft + guiWidth + thick, guiTop + guiHeight + thick, COLOR_NEON_RED);

        // Subtle outer glow
        int outerGlow = 0x303BB6A6;
        renderRectOutline(graphics, guiLeft - 1, guiTop - 1, guiWidth + 2, guiHeight + 2, outerGlow);
    }
    
    protected void renderGrid(GuiGraphics graphics) {
        int gridSize = 40;
        int gridColor = 0x08FFFFFF;
        
        long time = System.currentTimeMillis();
        int offset = (int)((time / 50) % gridSize);
        
        // Vertical lines
        for (int x = guiLeft + (guiWidth % gridSize) / 2; x <= guiLeft + guiWidth; x += gridSize) {
            graphics.fill(x, guiTop, x + 1, guiTop + guiHeight, gridColor);
        }
        
        // Horizontal lines
        for (int y = guiTop - offset; y <= guiTop + guiHeight; y += gridSize) {
            if (y >= guiTop) graphics.fill(guiLeft, y, guiLeft + guiWidth, y + 1, gridColor);
        }
    }
    
    protected void renderScanlines(GuiGraphics graphics) {
        int scanColor = 0x03000000;
        for(int y = guiTop; y < guiTop + guiHeight; y += 4) {
            graphics.fill(guiLeft, y, guiLeft + guiWidth, y+1, scanColor);
        }
    }
    
    protected void renderTitle(GuiGraphics graphics, int mouseX, int mouseY) {
         // Default implementation just centers title in window
        renderCenteredTitle(graphics, title.getString());
    }
    
    protected void renderCenteredTitle(GuiGraphics graphics, String text) {
        // Pulsing glow effect
        float pulse = (float)(0.7 + 0.3 * Math.sin((System.currentTimeMillis() - screenOpenTime) / 500.0));
        int glowAlpha = (int)(40 * pulse);
        int glowColor = (glowAlpha << 24) | (COLOR_NEON_PINK & 0x00FFFFFF);
        
        int titleWidth = font.width(text);
        int titleX = guiLeft + (guiWidth - titleWidth) / 2;
        int titleY = guiTop + 15;
        
        // Draw glow behind text
        graphics.fill(titleX - 10, titleY - 4, titleX + titleWidth + 10, titleY + 14, glowColor);
        
        // Draw title text
        graphics.drawString(font, text, titleX, titleY, COLOR_TEXT_TITLE);
        
        // Draw underline accent
        int underlineY = titleY + 12;
        graphics.fill(titleX - 4, underlineY, titleX + titleWidth + 4, underlineY + 1, COLOR_NEON_RED);
    }

    protected boolean renderTopTab(GuiGraphics graphics, String text, int x, int y, boolean active, int mouseX, int mouseY) {
        int w = font.width(text) + 30;
        int h = 24;
        
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        
        // Tab shape
        int borderColor = active ? COLOR_NEON_RED : (hovered ? COLOR_NEON_PINK : COLOR_BORDER);
        int fillColor = active ? 0xFF151B22 : (hovered ? 0xFF0E1216 : 0x00000000); // Transparent if inactive
        
        graphics.fill(x, y, x + w, y + h, fillColor);
        renderRectOutline(graphics, x, y, w, h, borderColor);
        
        // Active Indicator (Bottom bar)
        if (active) {
            graphics.fill(x, y + h - 2, x + w, y + h, COLOR_NEON_RED);
        }
        
        int textColor = active ? COLOR_NEON_RED : (hovered ? 0xFFFFFFFF : COLOR_TEXT_DIM);
        graphics.drawCenteredString(font, text, x + w / 2, y + 8, textColor);
        
        return hovered;
    }
    
    protected void renderBorderFrame(GuiGraphics graphics) {
        // Intentionally no-op to avoid heavy corner framing.
    }
    
    /**
     * Override to render screen-specific content.
     */
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
    
    protected void renderRectOutline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color); // Top
        graphics.fill(x, y + h - 1, x + w, y + h, color); // Bottom
        graphics.fill(x, y, x + 1, y + h, color); // Left
        graphics.fill(x + w - 1, y, x + w, y + h, color); // Right
    }

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
