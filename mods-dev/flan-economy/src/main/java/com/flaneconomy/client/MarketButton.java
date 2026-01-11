package com.flaneconomy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class MarketButton extends Button {
    public enum Type { PRIMARY, SUCCESS, DANGER }
    private final Type type;

    public MarketButton(int x, int y, int width, int height, Component message, OnPress onPress, Type type) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.type = type;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!this.visible) return;

        boolean hovered = this.isHovered();
        int x = this.getX();
        int y = this.getY();

        int bgColor, borderColor, textColor;
        switch (type) {
            case SUCCESS -> {
                bgColor = hovered ? 0xEE1E4D2B : (active ? 0xBB14361E : 0x44222222);
                borderColor = hovered ? 0xFF3CC15E : (active ? 0xAA2B8E46 : 0x22FFFFFF);
                textColor = active ? (hovered ? 0xFFFFFF : 0xFFB0FFB0) : 0xFF666666;
            }
            case DANGER -> {
                bgColor = hovered ? 0xEE5D1E1E : 0xBB411414;
                borderColor = hovered ? 0xFFFF4C4C : 0xAA8E2B2B;
                textColor = hovered ? 0xFFFFFF : 0xFFFFB0B0;
            }
            default -> {
                bgColor = hovered ? 0xEE2A344D : 0xBB1A2131;
                borderColor = hovered ? 0xFF8FD0FF : 0xAA5D8FB3;
                textColor = hovered ? 0xFFFFFF : 0xFFD0E0FF;
            }
        }

        // Draw shadow
        if (hovered && active) {
            graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, borderColor & 0x66FFFFFF);
        }

        graphics.fill(x, y, x + width, y + height, bgColor);
        graphics.renderOutline(x, y, width, height, borderColor);
        
        // Gloss
        if (active) graphics.fill(x, y, x + width, y + 1, 0x22FFFFFF);

        int textX = x + (width - Minecraft.getInstance().font.width(getMessage())) / 2;
        int textY = y + (height - 8) / 2;
        graphics.drawString(Minecraft.getInstance().font, getMessage(), textX, textY, textColor, active);
    }
}
