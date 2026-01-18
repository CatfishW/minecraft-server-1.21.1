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

package de.markusbordihn.easynpc.client.renderer;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class QuestOverlay {

  private static boolean enabled = false;
  private static boolean descriptionEnabled = false;
  private static final float UI_SCALE = 0.85f;
  private static final int MAX_VISIBLE_QUESTS = 8;
  private static final int MAX_DESCRIPTION_WIDTH = 190;
  private static final int PANEL_PADDING = 5;
  private static final int HEADER_GAP = 4;
  private static final int LINE_HEIGHT = 9;
  private static final int DESCRIPTION_LINE_HEIGHT = 8;
  private static final int PANEL_RADIUS = 0;
  private static final int PANEL_MARGIN = 8;
  private static final int HEADER_COLOR = 0xFFE3B041;
  private static final int TEXT_COLOR = 0xFFE6E6E6;
  private static final int MUTED_TEXT_COLOR = 0xFF9A9A9A;
  private static final int DESCRIPTION_COLOR = 0xFF7D7D7D;
  private static final int PANEL_BG = 0x7A0E0E0E;
  private static final int PANEL_SHADOW = 0x66101010;
  private static final int ACCENT_COLOR = 0xFFC8922B;

  private static final class RenderLine {
    private final net.minecraft.util.FormattedCharSequence text;
    private final int color;
    private final int indent;
    private final int height;

    private RenderLine(net.minecraft.util.FormattedCharSequence text, int color, int indent, int height) {
      this.text = text;
      this.color = color;
      this.indent = indent;
      this.height = height;
    }
  }

  private QuestOverlay() {}

  public static void register() {
    HudRenderCallback.EVENT.register(QuestOverlay::onHudRender);
    de.markusbordihn.easynpc.network.message.client.OpenQuestDialogMessage.OpenQuestDialogHandler.setHandler(
        quest -> {
            Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen != null) {
                mc.screen.onClose();
            }
            mc.setScreen(new de.markusbordihn.easynpc.client.screen.QuestDialogScreen(quest));
        }
    );
  }

  public static boolean toggleDescription() {
    descriptionEnabled = !descriptionEnabled;
    return descriptionEnabled;
  }

  public static boolean toggle() {
    enabled = !enabled;
    return enabled;
  }

  private static void onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
    List<de.markusbordihn.easynpc.client.quest.ClientQuestManager.ClientQuestEntry> activeQuests = de.markusbordihn.easynpc.client.quest.ClientQuestManager.getQuests();
    if (!enabled || activeQuests.isEmpty()) {
      return;
    }

    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null || minecraft.screen != null) {
      return;
    }

    Font font = minecraft.font;
    int x = Math.round(PANEL_MARGIN / UI_SCALE);
    int y = Math.round(PANEL_MARGIN / UI_SCALE);
    int maxTextWidth = 0;
    int totalHeight = 0;
    int shownQuests = 0;

    List<RenderLine> lines = new ArrayList<>();
    List<de.markusbordihn.easynpc.client.quest.ClientQuestManager.ClientQuestEntry> visibleQuests = new ArrayList<>();
    for (de.markusbordihn.easynpc.client.quest.ClientQuestManager.ClientQuestEntry quest : activeQuests) {
      if (quest.completed) {
        continue;
      }
      visibleQuests.add(quest);
    }
    if (visibleQuests.isEmpty()) {
      return;
    }

    String header = "Active Quests (" + visibleQuests.size() + ")";
    net.minecraft.util.FormattedCharSequence headerText = Component.literal(header).getVisualOrderText();
    lines.add(new RenderLine(headerText, HEADER_COLOR, 0, LINE_HEIGHT + HEADER_GAP));
    maxTextWidth = Math.max(maxTextWidth, font.width(headerText));
    totalHeight += LINE_HEIGHT + HEADER_GAP;

    for (de.markusbordihn.easynpc.client.quest.ClientQuestManager.ClientQuestEntry quest : visibleQuests) {
      if (shownQuests >= MAX_VISIBLE_QUESTS) {
        break;
      }
      String status = "(" + quest.progress + "/" + quest.targetAmount + ")";
      String rawTitle = quest.title != null ? quest.title : "Quest";
      String questLine = "- " + rawTitle + " " + status;
      String trimmedLine = trimToWidth(font, questLine, MAX_DESCRIPTION_WIDTH);
      net.minecraft.util.FormattedCharSequence questText = Component.literal(trimmedLine).getVisualOrderText();
      lines.add(new RenderLine(questText, TEXT_COLOR, 0, LINE_HEIGHT));
      maxTextWidth = Math.max(maxTextWidth, font.width(questText));
      totalHeight += LINE_HEIGHT;
      shownQuests += 1;

      if (descriptionEnabled && quest.description != null && !quest.description.isEmpty()) {
        for (net.minecraft.util.FormattedCharSequence line : font.split(Component.literal(quest.description), MAX_DESCRIPTION_WIDTH)) {
          lines.add(new RenderLine(line, DESCRIPTION_COLOR, 8, DESCRIPTION_LINE_HEIGHT));
          maxTextWidth = Math.max(maxTextWidth, font.width(line) + 8);
          totalHeight += DESCRIPTION_LINE_HEIGHT;
        }
        net.minecraft.util.FormattedCharSequence spacer = Component.literal("").getVisualOrderText();
        lines.add(new RenderLine(spacer, DESCRIPTION_COLOR, 0, 3));
        totalHeight += 3;
      }
    }

    int hiddenQuests = visibleQuests.size() - shownQuests;
    if (hiddenQuests > 0) {
      String moreLine = "+ " + hiddenQuests + " more quests";
      net.minecraft.util.FormattedCharSequence moreText = Component.literal(moreLine).getVisualOrderText();
      lines.add(new RenderLine(moreText, MUTED_TEXT_COLOR, 0, LINE_HEIGHT));
      maxTextWidth = Math.max(maxTextWidth, font.width(moreText));
      totalHeight += LINE_HEIGHT;
    }

    int panelWidth = maxTextWidth + (PANEL_PADDING * 2);
    int panelHeight = totalHeight + (PANEL_PADDING * 2);

    guiGraphics.pose().pushPose();
    guiGraphics.pose().scale(UI_SCALE, UI_SCALE, 1.0f);
    guiGraphics.fill(x - 1, y - 1, x + panelWidth + 1, y + panelHeight + 1, PANEL_SHADOW);
    guiGraphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BG);
    guiGraphics.fill(x + PANEL_RADIUS, y + PANEL_RADIUS, x + panelWidth - PANEL_RADIUS, y + 2, ACCENT_COLOR);

    int cursorY = y + PANEL_PADDING;
    for (int i = 0; i < lines.size(); i++) {
      RenderLine line = lines.get(i);
      guiGraphics.drawString(font, line.text, x + PANEL_PADDING + line.indent, cursorY, line.color);
      cursorY += line.height;
    }

    guiGraphics.pose().popPose();
  }

  private static String trimToWidth(Font font, String text, int maxWidth) {
    if (font.width(text) <= maxWidth) {
      return text;
    }
    String ellipsis = "...";
    int ellipsisWidth = font.width(ellipsis);
    String trimmed = font.plainSubstrByWidth(text, Math.max(0, maxWidth - ellipsisWidth));
    return trimmed + ellipsis;
  }

}
