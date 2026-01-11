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

import de.markusbordihn.easynpc.data.quest.QuestDataEntry;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class QuestDialogScreen extends Screen {

  private final QuestDataEntry quest;
  private int panelWidth = 280;
  private int panelHeight = 220;

  public QuestDialogScreen(QuestDataEntry quest) {
    super(Component.translatable("gui.easy_npc.quest.accept_quest"));
    this.quest = quest;
  }

  @Override
  protected void init() {
    super.init();
    this.setFocused(true);

    int x = (this.width - panelWidth) / 2;
    int y = (this.height - panelHeight) / 2;

    // Accept or Cancel Button
    boolean isCompleted = quest != null && de.markusbordihn.easynpc.client.quest.ClientQuestManager.isQuestCompleted(quest.getId());
    
    if (!isCompleted) {
        if (quest != null && de.markusbordihn.easynpc.client.quest.ClientQuestManager.hasQuest(quest.getId())) {
            this.addRenderableWidget(
                new QuestButton(
                    x + (panelWidth / 2) - 105,
                    y + panelHeight - 30,
                    100,
                    20,
                    Component.translatable("gui.easy_npc.quest.cancel_quest"),
                    button -> {
                        de.markusbordihn.easynpc.network.NetworkHandlerManager.sendMessageToServer(
                            new de.markusbordihn.easynpc.network.message.server.CancelQuestMessage(
                                quest.getId()));
                        de.markusbordihn.easynpc.client.quest.ClientQuestManager.removeQuest(quest.getId());
                        this.onClose();
                    },
                    QuestButton.Type.NEGATIVE));
        } else {
            this.addRenderableWidget(
                new QuestButton(
                    x + (panelWidth / 2) - 105,
                    y + panelHeight - 30,
                    100,
                    20,
                    Component.translatable("gui.easy_npc.quest.accept_quest"),
                    button -> {
                      if (quest != null && quest.getId() != null) {
                        de.markusbordihn.easynpc.network.NetworkHandlerManager.sendMessageToServer(
                            new de.markusbordihn.easynpc.network.message.server.AcceptQuestMessage(
                                quest.getId()));
                        de.markusbordihn.easynpc.client.quest.ClientQuestManager.addQuest(
                            quest.getId(),
                            quest.getTitle(),
                            quest.getDescription(),
                            0,
                            quest.getObjectiveAmount(),
                            false,
                            quest.getRewardXP(),
                            quest.getRewardItemID(),
                            quest.getRewardItemAmount());
                      }
                      this.onClose();
                    },
                    QuestButton.Type.POSITIVE));
        }
    } else {
        // Optional: Add a disabled button or just leave space. 
        // User asked to remove buttons. We will render text in render() method instead.
    }

    // Close Button
    this.addRenderableWidget(
        new QuestButton(
            x + (panelWidth / 2) + 5,
            y + panelHeight - 30,
            100,
            20,
            Component.translatable("gui.easy_npc.quest.close"),
            button -> this.onClose(),
            QuestButton.Type.NEGATIVE));
  }

  @Override
  public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    // Empty to disable vanilla blur
  }

  @Override
  public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    guiGraphics.fill(0, 0, this.width, this.height, 0xC0101010);
    
    int x = (this.width - panelWidth) / 2;
    int y = (this.height - panelHeight) / 2;
    
    // Background
    guiGraphics.fill(x - 5, y - 5, x + panelWidth + 5, y + panelHeight + 5, 0xEE101010);
    guiGraphics.renderOutline(x - 5, y - 5, panelWidth + 10, panelHeight + 10, 0xFFAA0000);
    
    if (quest != null) {
        // Title
        guiGraphics.drawCenteredString(this.font, "§6§l" + quest.getTitle(), this.width / 2, y + 10, 0xFFFFFF);
        
        // Description wrap calculation
        List<FormattedCharSequence> lines = this.font.split(Component.literal(quest.getDescription()), panelWidth - 20);
        int currentY = y + 30;
        int maxLines = (panelHeight - 80) / 10;
        
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            guiGraphics.drawString(this.font, lines.get(i), x + 10, currentY, 0xCCCCCC);
            currentY += 10;
        }
        
        if (lines.size() > maxLines) {
            guiGraphics.drawString(this.font, "§8...", x + panelWidth - 20, currentY - 10, 0xFFFFFF);
        }

        // Objective (Dynamic position based on description height)
        // Ensure objective has some space from description or is at fixed bottom-ish area
        int objectiveY = y + panelHeight - 50; 
        String objectiveLabel = Component.translatable("gui.easy_npc.quest.objective").getString();
        String objectiveText = "§7" + objectiveLabel + ": §f" + quest.getObjectiveType() + " §e" + quest.getObjectiveTarget() + " §7x§6" + quest.getObjectiveAmount();
        guiGraphics.drawCenteredString(this.font, objectiveText, this.width / 2, objectiveY, 0xFFFFFF);
        
        if (de.markusbordihn.easynpc.client.quest.ClientQuestManager.isQuestCompleted(quest.getId())) {
             guiGraphics.drawCenteredString(this.font, Component.translatable("gui.easy_npc.quest.completed_title").append("!"), x + (panelWidth / 2) - 55, y + panelHeight - 25, 0x55FF55);
        }

    } else {
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.easy_npc.quest.no_quest_available"), this.width / 2, y + panelHeight / 2 - 10, 0xFF5555);
    }

    super.render(guiGraphics, mouseX, mouseY, partialTick);
  }
}
