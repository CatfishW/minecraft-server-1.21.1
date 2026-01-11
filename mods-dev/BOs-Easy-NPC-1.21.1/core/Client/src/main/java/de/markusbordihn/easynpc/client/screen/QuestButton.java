/*
 * Copyright 2024 Markus Bordihn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusbordihn.easynpc.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class QuestButton extends Button {

  public enum Type {
    NEUTRAL,
    POSITIVE,
    NEGATIVE
  }

  private final Type type;

  public QuestButton(int x, int y, int width, int height, Component message, OnPress onPress) {
    this(x, y, width, height, message, onPress, Type.NEUTRAL);
  }

  public QuestButton(
      int x, int y, int width, int height, Component message, OnPress onPress, Type type) {
    super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    this.type = type;
  }

  @Override
  protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    if (!this.visible) {
      return;
    }

    boolean hovered = this.isHoveredOrFocused();
    int x = this.getX();
    int y = this.getY();

    // Determine colors based on type and hover state
    int bgColor;
    int borderColor;
    int textColor;

    switch (this.type) {
      case POSITIVE:
        bgColor = hovered ? 0xDD004400 : 0xAA003300;
        borderColor = hovered ? 0xFF00FF00 : 0xAA00AA00;
        textColor = hovered ? 0xFFFFFF : 0xAAFFAA;
        break;
      case NEGATIVE:
        bgColor = hovered ? 0xDD440000 : 0xAA330000;
        borderColor = hovered ? 0xFFFF0000 : 0xAAAA0000;
        textColor = hovered ? 0xFFFFFF : 0xFFAAAA;
        break;
      default:
      case NEUTRAL:
        bgColor = hovered ? 0xDD3D2A10 : 0xAA1A1A1A;
        borderColor = hovered ? 0xFFD4AF37 : 0x88D4AF37;
        textColor = hovered ? 0xFFFFFF : 0xDDDDDD;
        break;
    }

    // Draw main button body with gradient or glow
    guiGraphics.fill(x, y, x + this.width, y + this.height, bgColor);
    
    // Gloss effect at top
    guiGraphics.fill(x, y, x + this.width, y + 2, 0x22FFFFFF);

    // Decorative corners or shadow
    if (hovered) {
        // Outer glow
        guiGraphics.renderOutline(x - 1, y - 1, this.width + 2, this.height + 2, borderColor & 0x44FFFFFF);
    }

    // Main border
    guiGraphics.renderOutline(x, y, this.width, this.height, borderColor);

    // Text with shadow
    int titleX = x + (this.width - Minecraft.getInstance().font.width(this.getMessage())) / 2;
    int titleY = y + (this.height - 8) / 2;
    guiGraphics.drawString(Minecraft.getInstance().font, this.getMessage(), titleX, titleY, textColor, true);
  }
}
