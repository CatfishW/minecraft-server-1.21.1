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

import de.markusbordihn.easynpc.client.SpawnTimerData;
import de.markusbordihn.easynpc.client.SpawnTimerData.SpawnTimerInfo;
import java.util.List;
import de.markusbordihn.easynpc.network.NetworkHandlerManager;
import de.markusbordihn.easynpc.network.message.server.StopSpawnTaskMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class SpawnTimerScreen extends Screen {

  private static final int PANEL_MAX_WIDTH = 360;
  private static final int PANEL_MAX_HEIGHT = 300;
  private static final int PANEL_PADDING = 12;
  private static final int HEADER_HEIGHT = 28;
  private static final int FOOTER_HEIGHT = 12;
  private static final int ENTRY_HEIGHT = 22;
  private static final int ENTRY_SPACING = 6;

  private double scrollAmount = 0;
  private int maxScroll = 0;

  public SpawnTimerScreen() {
    super(Component.literal("Spawn Timers"));
  }

  @Override
  public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    // Empty to disable vanilla blur and darkening pass.
  }

  @Override
  public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    guiGraphics.fill(0, 0, this.width, this.height, 0xAA050505);

    int panelWidth = Math.min(this.width - 40, PANEL_MAX_WIDTH);
    int panelHeight = Math.min(this.height - 40, PANEL_MAX_HEIGHT);
    int panelX = (this.width - panelWidth) / 2;
    int panelY = (this.height - panelHeight) / 2;

    guiGraphics.fillGradient(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xEE1A1E2E, 0xEE121622);
    guiGraphics.renderOutline(panelX, panelY, panelWidth, panelHeight, 0x44D4AF37);

    guiGraphics.drawString(this.font, this.title, panelX + PANEL_PADDING, panelY + 10, 0xFFD4AF37, true);
    guiGraphics.fill(panelX + PANEL_PADDING, panelY + 22, panelX + panelWidth - PANEL_PADDING, panelY + 23, 0x66D4AF37);

    int listX = panelX + PANEL_PADDING;
    int listY = panelY + HEADER_HEIGHT;
    int listWidth = panelWidth - (PANEL_PADDING * 2);
    int listHeight = panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT;

    List<SpawnTimerInfo> timers = SpawnTimerData.getTimers();
    int contentHeight = timers.isEmpty()
        ? 0
        : timers.size() * (ENTRY_HEIGHT + ENTRY_SPACING) - ENTRY_SPACING;
    maxScroll = Math.max(0, contentHeight - listHeight);
    scrollAmount = Mth.clamp(scrollAmount, 0, maxScroll);

    if (timers.isEmpty()) {
      guiGraphics.drawCenteredString(this.font, Component.literal("No active spawn timers."), panelX + panelWidth / 2, listY + listHeight / 2 - 4, 0x99FFFFFF);
    } else {
      guiGraphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
      int currentY = listY - (int) scrollAmount;

      for (SpawnTimerInfo timer : timers) {
        if (currentY + ENTRY_HEIGHT >= listY && currentY <= listY + listHeight) {
          renderEntry(guiGraphics, listX, currentY, listWidth, timer, mouseX, mouseY);
        }
        currentY += ENTRY_HEIGHT + ENTRY_SPACING;
      }

      guiGraphics.disableScissor();
    }

    if (maxScroll > 0) {
      int trackX = listX + listWidth - 3;
      guiGraphics.fill(trackX, listY, trackX + 2, listY + listHeight, 0x33FFFFFF);
      int thumbHeight = Math.max(12, (int) ((float) listHeight / contentHeight * listHeight));
      int thumbY = listY + (int) ((scrollAmount / maxScroll) * (listHeight - thumbHeight));
      guiGraphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFAA0000);
    }

    super.render(guiGraphics, mouseX, mouseY, partialTick);
  }

  private void renderEntry(GuiGraphics guiGraphics, int x, int y, int width, SpawnTimerInfo timer, int mouseX, int mouseY) {
    guiGraphics.fill(x, y, x + width, y + ENTRY_HEIGHT, 0x33000000);

    float secondsRemaining = timer.ticksRemaining / 20.0f;
    float totalSeconds = timer.totalTicks / 20.0f;
    float progress = timer.totalTicks > 0 ? 1.0f - (float) timer.ticksRemaining / timer.totalTicks : 1.0f;
    progress = Mth.clamp(progress, 0.0f, 1.0f);

    int textY = y + 4;
    int nameX = x + 6;
    if (timer.isGroupSpawn) {
      guiGraphics.drawString(this.font, "[G]", nameX, textY, 0xFFFF7777, false);
      nameX += this.font.width("[G] ");
    }

    String name = timer.templateName;
    guiGraphics.drawString(this.font, name, nameX, textY, 0xFFE6E6E6, false);

    String timeText = String.format("%.1fs / %.1fs", secondsRemaining, totalSeconds);
    int timeColor = timer.ticksRemaining < 100 ? 0xFFFF7777 : 0xFFDDDDDD;
    guiGraphics.drawString(this.font, timeText, x + width - 6 - this.font.width(timeText), textY, timeColor, false);

    int barX = x + 6;
    int barY = y + ENTRY_HEIGHT - 5;
    int barWidth = width - 12;
    
    // Admin stop button
    boolean isAdmin = Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2);
    if (isAdmin) {
      int stopButtonWidth = 20;
      int stopButtonX = x + width - 6 - this.font.width(timeText) - 5 - stopButtonWidth;
      int stopButtonY = y + 2;
      int stopButtonHeight = 14;
      
      // Render button background
      boolean hovered = mouseX >= stopButtonX && mouseX < stopButtonX + stopButtonWidth &&
                         mouseY >= stopButtonY && mouseY < stopButtonY + stopButtonHeight;
      
      guiGraphics.fill(stopButtonX, stopButtonY, stopButtonX + stopButtonWidth, stopButtonY + stopButtonHeight, hovered ? 0xFFAA0000 : 0xFF770000);
      guiGraphics.drawCenteredString(this.font, "X", stopButtonX + stopButtonWidth / 2, stopButtonY + 3, 0xFFFFFFFF);
      
      barWidth -= (stopButtonWidth + 5);
    }

    guiGraphics.fill(barX, barY, barX + barWidth, barY + 3, 0xFF2A2A2A);
    guiGraphics.fill(barX, barY, barX + (int) (barWidth * progress), barY + 3, timer.isGroupSpawn ? 0xFFFF5555 : 0xFF55FF55);
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (button == 0) {
      boolean isAdmin = Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2);
      if (isAdmin) {
        int panelWidth = Math.min(this.width - 40, PANEL_MAX_WIDTH);
        int panelHeight = Math.min(this.height - 40, PANEL_MAX_HEIGHT);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int listX = panelX + PANEL_PADDING;
        int listY = panelY + HEADER_HEIGHT;
        int listWidth = panelWidth - (PANEL_PADDING * 2);
        int listHeight = panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT;

        if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
          List<SpawnTimerInfo> timers = SpawnTimerData.getTimers();
          int currentY = listY - (int) scrollAmount;

          for (SpawnTimerInfo timer : timers) {
            if (currentY + ENTRY_HEIGHT >= listY && currentY <= listY + listHeight) {
              String timeText = String.format("%.1fs / %.1fs", timer.ticksRemaining / 20.0f, timer.totalTicks / 20.0f);
              int stopButtonWidth = 20;
              int stopButtonX = listX + listWidth - 6 - this.font.width(timeText) - 5 - stopButtonWidth;
              int stopButtonY = currentY + 2;
              int stopButtonHeight = 14;

              if (mouseX >= stopButtonX && mouseX < stopButtonX + stopButtonWidth && mouseY >= stopButtonY && mouseY < stopButtonY + stopButtonHeight) {
                NetworkHandlerManager.sendMessageToServer(new StopSpawnTaskMessage(timer.templateName));
                return true;
              }
            }
            currentY += ENTRY_HEIGHT + ENTRY_SPACING;
          }
        }
      }
    }
    return super.mouseClicked(mouseX, mouseY, button);
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    int panelWidth = Math.min(this.width - 40, PANEL_MAX_WIDTH);
    int panelHeight = Math.min(this.height - 40, PANEL_MAX_HEIGHT);
    int panelX = (this.width - panelWidth) / 2;
    int panelY = (this.height - panelHeight) / 2;

    int listX = panelX + PANEL_PADDING;
    int listY = panelY + HEADER_HEIGHT;
    int listWidth = panelWidth - (PANEL_PADDING * 2);
    int listHeight = panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT;

    if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
      List<SpawnTimerInfo> timers = SpawnTimerData.getTimers();
      int contentHeight = timers.isEmpty()
          ? 0
          : timers.size() * (ENTRY_HEIGHT + ENTRY_SPACING) - ENTRY_SPACING;
      maxScroll = Math.max(0, contentHeight - listHeight);
      if (maxScroll > 0) {
        scrollAmount = Mth.clamp(scrollAmount - scrollY * 12, 0, maxScroll);
        return true;
      }
    }

    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
