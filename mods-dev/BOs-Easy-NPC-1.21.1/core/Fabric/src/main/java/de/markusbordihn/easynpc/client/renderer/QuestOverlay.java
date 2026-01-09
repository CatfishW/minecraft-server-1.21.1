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

  private static boolean enabled = true;
  private static boolean descriptionEnabled = false;

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
    int x = 10;
    int y = 10;

    String header = "§6Active Quests";
    guiGraphics.drawString(font, header, x, y, 0xFFFFFF);
    y += 12;

    for (de.markusbordihn.easynpc.client.quest.ClientQuestManager.ClientQuestEntry quest : activeQuests) {
        if (quest.completed) {
            continue;
        }
        String status = "§7(" + quest.progress + "/" + quest.targetAmount + ")";
        String text = "§f- " + quest.title + " " + status;
        guiGraphics.drawString(font, text, x, y, 0xFFFFFF);
        y += 10;
        
        if (descriptionEnabled && quest.description != null && !quest.description.isEmpty()) {
                for (net.minecraft.util.FormattedCharSequence line : font.split(Component.literal("§8" + quest.description), 200)) {
                    guiGraphics.drawString(font, line, x + 10, y, 0xBBBBBB);
                    y += 9;
                }
                y += 2;
        }
    }
  }

}
