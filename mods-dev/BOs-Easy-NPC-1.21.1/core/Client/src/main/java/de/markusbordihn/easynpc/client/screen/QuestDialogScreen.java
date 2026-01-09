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

import de.markusbordihn.easynpc.client.renderer.QuestOverlay;
import de.markusbordihn.easynpc.data.quest.QuestDataEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class QuestDialogScreen extends Screen {

  private final QuestDataEntry quest;

  public QuestDialogScreen(QuestDataEntry quest) {
    super(Component.literal("Quest Dialog"));
    this.quest = quest;
  }

  @Override
  protected void init() {
    super.init();
    this.setFocused(true);

    int x = (this.width - 200) / 2;
    int y = (this.height - 160) / 2;

    // Accept Button
    this.addRenderableWidget(
        Button.builder(Component.literal("Accept Quest"), button -> {
          if (quest != null && quest.getId() != null) {
              de.markusbordihn.easynpc.network.NetworkHandlerManager.sendMessageToServer(
                  new de.markusbordihn.easynpc.network.message.server.AcceptQuestMessage(quest.getId()));
              de.markusbordihn.easynpc.client.quest.ClientQuestManager.addQuest(quest.getId(), quest.getTitle(), quest.getDescription(), 0, quest.getObjectiveAmount(), false);
          }
          this.onClose();
        })
        .bounds(x + 50, y + 120, 100, 20)
        .build());
        
    // Close Button
    this.addRenderableWidget(
        Button.builder(Component.literal("Close"), button -> this.onClose())
        .bounds(x + 50, y + 145, 100, 20)
        .build());
  }

  @Override
  public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
    
    int x = (this.width - 200) / 2;
    int y = (this.height - 160) / 2;
    
    // Background (Opaque and large enough)
    guiGraphics.fill(x - 5, y - 5, x + 205, y + 175, 0xFF101010);
    guiGraphics.renderOutline(x - 5, y - 5, 210, 180, 0xFFAA0000);
    
    // Title
    if (quest != null) {
        guiGraphics.drawCenteredString(this.font, quest.getTitle(), this.width / 2, y + 10, 0xFFAA00);
        
        // Description
        guiGraphics.drawWordWrap(this.font, Component.literal(quest.getDescription()), x + 10, y + 30, 180, 0xFFFFFF);
        
        // Objective
        String objective = "Objective: " + quest.getObjectiveType() + " " + quest.getObjectiveTarget() + " x" + quest.getObjectiveAmount();
        guiGraphics.drawString(this.font, objective, x + 10, y + 100, 0xAAAAAA);
    } else {
        guiGraphics.drawCenteredString(this.font, "No Quest Available", this.width / 2, y + 80, 0xFF5555);
    }

    super.render(guiGraphics, mouseX, mouseY, partialTick);
  }
}
