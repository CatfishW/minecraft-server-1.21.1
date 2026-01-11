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

import de.markusbordihn.easynpc.client.quest.ClientQuestManager;
import de.markusbordihn.easynpc.client.quest.ClientQuestManager.ClientQuestEntry;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public class QuestListScreen extends Screen {

  private QuestList questList;
  private ClientQuestEntry selectedQuest;
  private double descriptionScrollAmount = 0;
  private int maxDescriptionHeight = 0;
  private QuestButton cancelQuestButton;

  public QuestListScreen() {
    super(Component.translatable("gui.easy_npc.quest.adventure_log"));
  }

  @Override
  protected void init() {
    super.init();

    int panelWidth = Math.min(this.width - 40, 480);
    int panelX = (this.width - panelWidth) / 2;
    int listWidth = 160;

    // Create the quest list on the left
    this.questList = new QuestList(this.minecraft, listWidth, this.height - 80, 40, 30);
    this.questList.setX(panelX);
    this.addRenderableWidget(this.questList);

    // Initial selection
    List<ClientQuestEntry> quests = ClientQuestManager.getQuests();
    if (!quests.isEmpty() && selectedQuest == null) {
      for (ClientQuestEntry q : quests) {
        if (!q.completed) {
          selectedQuest = q;
          break;
        }
      }
    }
    
    this.setFocused(true);

    // Cancel Quest Button (initially positioned, visibility handled in render)
    this.cancelQuestButton = this.addRenderableWidget(
        new QuestButton(
            panelX + listWidth + 15,
            this.height - 65,
            100,
            20,
            Component.translatable("gui.easy_npc.quest.cancel_quest"),
            button -> {
              if (selectedQuest != null) {
                de.markusbordihn.easynpc.network.NetworkHandlerManager.sendMessageToServer(
                    new de.markusbordihn.easynpc.network.message.server.CancelQuestMessage(selectedQuest.id));
                ClientQuestManager.removeQuest(selectedQuest.id);
                selectedQuest = null;
                this.questList.refreshList();
              }
            },
            QuestButton.Type.NEGATIVE));

    // Close Button
    this.addRenderableWidget(
        new QuestButton(
            this.width / 2 - 50,
            this.height - 30,
            100,
            20,
            Component.translatable("gui.easy_npc.quest.close_log"),
            button -> this.onClose(),
            QuestButton.Type.NEGATIVE));
  }

  @Override
  public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    // Empty to disable vanilla blur and darkening pass
  }

  @Override
  public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    // Manually render a darkened background at the very beginning
    guiGraphics.fill(0, 0, this.width, this.height, 0xC0101010);

    int panelWidth = Math.min(this.width - 40, 480);
    int panelX = (this.width - panelWidth) / 2;
    int panelY = 5;
    int panelHeight = this.height - 10;

    // Main panel background
    guiGraphics.fill(panelX - 10, panelY, panelX + panelWidth + 10, panelY + panelHeight, 0xDD101010);
    guiGraphics.renderOutline(panelX - 10, panelY, panelWidth + 20, panelHeight, 0xFFAA0000);

    // Title
    guiGraphics.drawCenteredString(this.font, Component.translatable("gui.easy_npc.quest.adventure_log"), this.width / 2, 12, 0xFFD4AF37);
    guiGraphics.fill(this.width / 2 - 80, 25, this.width / 2 + 80, 26, 0xFFD4AF37);

    // Divider between list and details
    int dividerX = panelX + 160;
    guiGraphics.fill(dividerX, 40, dividerX + 1, panelHeight - 40, 0x44D4AF37);

    // Render details for selected quest
    if (selectedQuest != null) {
      renderQuestDetails(guiGraphics, dividerX + 15, 45, panelWidth - 180, panelHeight - 90, mouseX, mouseY);
    } else if (ClientQuestManager.getQuests().isEmpty()) {
      guiGraphics.drawCenteredString(this.font, Component.translatable("gui.easy_npc.quest.no_quests"), dividerX + (panelWidth - 160) / 2, panelHeight / 2, 0x888888);
    }

    // Update cancel button visibility
    if (cancelQuestButton != null) {
        cancelQuestButton.visible = selectedQuest != null;
    }

    super.render(guiGraphics, mouseX, mouseY, partialTick);
  }

  private void renderQuestDetails(GuiGraphics guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY) {
    // Header
    guiGraphics.drawString(font, "§6§n" + selectedQuest.title, x, y, 0xFFFFFF);
    
    // Progress bar
    int barY = y + 18;
    guiGraphics.fill(x, barY, x + width, barY + 6, 0xFF333333);
    float pct = selectedQuest.targetAmount > 0 ? (float)selectedQuest.progress / selectedQuest.targetAmount : 1.0f;
    guiGraphics.fill(x, barY, x + (int)(width * Math.min(pct, 1.0f)), barY + 6, 0xFF55FF55);
    
    String progressStr = Component.translatable("gui.easy_npc.quest.progress").getString() + ": §a" + selectedQuest.progress + " §7/ §e" + selectedQuest.targetAmount;
    guiGraphics.drawString(font, progressStr, x, barY + 10, 0xFFFFFF);

    // Description area with clipping and scrolling
    int descY = barY + 28;
    int descHeight = height - 30;
    
    guiGraphics.enableScissor(x, descY, x + width, descY + descHeight);
    
    List<FormattedCharSequence> lines = font.split(Component.literal(selectedQuest.description), width - 10);
    maxDescriptionHeight = lines.size() * 10;
    
    int currentY = descY - (int)descriptionScrollAmount;
    for (FormattedCharSequence line : lines) {
        if (currentY + 10 > descY && currentY < descY + descHeight) {
            guiGraphics.drawString(font, line, x, currentY, 0xCCCCCC);
        }
        currentY += 10;
    }
    
    guiGraphics.disableScissor();

    // Scrollbar if needed
    if (maxDescriptionHeight > descHeight) {
        int scrollbarX = x + width - 4;
        guiGraphics.fill(scrollbarX, descY, scrollbarX + 2, descY + descHeight, 0x22FFFFFF);
        
        int thumbHeight = (int)((float)descHeight / maxDescriptionHeight * descHeight);
        int thumbY = descY + (int)((float)descriptionScrollAmount / maxDescriptionHeight * descHeight);
        guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbHeight, 0xFFAA0000);
    }
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    if (selectedQuest != null && maxDescriptionHeight > (this.height - 130)) {
        descriptionScrollAmount = Mth.clamp(descriptionScrollAmount - scrollY * 15, 0, Math.max(0, maxDescriptionHeight - (this.height - 130)));
        return true;
    }
    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  class QuestList extends ObjectSelectionList<QuestEntry> {
    public QuestList(net.minecraft.client.Minecraft minecraft, int width, int height, int top, int itemHeight) {
      super(minecraft, width, height, top, itemHeight);
      this.setRenderHeader(false, 0);

      refreshList();
    }

    public void refreshList() {
      this.clearEntries();
      List<ClientQuestEntry> quests = ClientQuestManager.getQuests();
      for (ClientQuestEntry quest : quests) {
        if (!quest.completed) {
          this.addEntry(new QuestEntry(quest));
        }
      }
    }

    @Override
    protected int getScrollbarPosition() {
      return this.getX() + this.width - 6;
    }

    @Override
    public int getRowWidth() {
      return this.width - 10;
    }

    @Override
    protected void renderSelection(
        GuiGraphics guiGraphics, int unused1, int unused2, int unused3, int unused4, int unused5) {
      // Do not render selection.
    }

    @Override
    protected void renderListBackground(GuiGraphics guiGraphics) {
      // Empty to let screen background show through
    }
  }

  class QuestEntry extends ObjectSelectionList.Entry<QuestEntry> {
    private final ClientQuestEntry quest;

    public QuestEntry(ClientQuestEntry quest) {
      this.quest = quest;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
      boolean selected = selectedQuest != null && selectedQuest.id.equals(quest.id);
      
      int bgColor = selected ? 0x66AA0000 : (hovering ? 0x33FFFFFF : 0x11FFFFFF);
      guiGraphics.fill(left, top, left + width, top + height - 2, bgColor);
      
      if (selected) {
          guiGraphics.renderOutline(left, top, width, height - 2, 0xFFD4AF37);
      }

      String title = (selected ? "§e" : "§f") + quest.title;
      if (font.width(title) > width - 10) {
          title = font.plainSubstrByWidth(title, width - 20) + "...";
      }
      guiGraphics.drawString(font, title, left + 5, top + (height - 8) / 2, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
      selectedQuest = quest;
      descriptionScrollAmount = 0;
      return true;
    }

    @Override
    public Component getNarration() {
      return Component.literal(quest.title);
    }
  }
}
