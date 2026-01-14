/*
 * Copyright 2023 Markus Bordihn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusbordihn.easynpc.client.screen.dialog;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.client.screen.Screen;
import de.markusbordihn.easynpc.client.screen.components.TextButton;
import de.markusbordihn.easynpc.data.action.ActionEventType;
import de.markusbordihn.easynpc.data.dialog.DialogButtonEntry;
import de.markusbordihn.easynpc.data.dialog.DialogDataEntry;
import de.markusbordihn.easynpc.data.dialog.DialogMetaData;
import de.markusbordihn.easynpc.data.dialog.DialogScreenLayout;
import de.markusbordihn.easynpc.data.dialog.DialogUtils;
import de.markusbordihn.easynpc.data.screen.AdditionalScreenData;
import de.markusbordihn.easynpc.llm.LLMDialogResponseManager;
import de.markusbordihn.easynpc.menu.dialog.DialogMenu;
import de.markusbordihn.easynpc.network.NetworkHandlerManager;
import de.markusbordihn.easynpc.network.NetworkMessageHandlerManager;
import de.markusbordihn.easynpc.network.components.TextComponent;
import de.markusbordihn.easynpc.network.message.server.LLMChatRequestMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class DialogScreen<T extends DialogMenu> extends Screen<T, AdditionalScreenData> {

  private static final int MAX_TOTAL_DIALOG_LINES = 100;
  private static final int PORTRAIT_SIZE = 30; // Smaller portrait size
  private static final int BAR_HEIGHT = 100;
  private static final int PORTRAIT_AREA_WIDTH = 55; // Width reserved for each portrait
  
  private static DialogScreenLayout dialogScreenLayout = DialogScreenLayout.UNKNOWN;
  protected final ArrayList<ModernButton> dialogButtons = new ArrayList<>();
  protected final Component dialogText;
  protected final DialogMetaData dialogMetaData;
  protected Component dialogComponent;
  protected int numberOfDialogLines = 1;
  
  // Streaming text optimization
  private List<FormattedCharSequence> cachedFullLines = Collections.emptyList();
  private List<Integer> lineCharacterCounts = new ArrayList<>();
  protected boolean isStreaming = false;
  protected String rawDialogText = "";
  protected long streamingStartTime = 0;
  protected int streamingTick = 0;

  // LLM Chat UI elements
  protected EditBox llmChatInput = null;
  protected Button llmSendButton = null;
  protected boolean llmEnabled = false;
  protected boolean waitingForLLMResponse = false;
  protected String lastLLMResponse = null;

  public DialogScreen(T menu, Inventory inventory, Component component) {
    super(menu, inventory, component, 280, 200);
    this.dialogText = this.getDialogText();
    this.dialogMetaData =
        new DialogMetaData(
            this.getEasyNPC().getLivingEntity(),
            minecraftInstance != null ? minecraftInstance.player : null,
            this.getAdditionalScreenData() != null
                ? this.getAdditionalScreenData().getScoreboardData()
                : null);
  }

  private static void setDialogScreenLayout(DialogScreenLayout dialogScreenLayout) {
    DialogScreen.dialogScreenLayout = dialogScreenLayout;
  }

  protected void renderDialog(GuiGraphics guiGraphics, float partialTicks) {
    if (this.cachedFullLines.isEmpty()) {
      return;
    }

    int dialogTopPosition = this.height - BAR_HEIGHT + 20;
    int dialogLeftPosition = PORTRAIT_AREA_WIDTH + 15; // Space after NPC portrait
    int dialogRightMargin = PORTRAIT_AREA_WIDTH + 15; // Space before player portrait
    int maxDialogWidth = this.width - dialogLeftPosition - dialogRightMargin;
    
    // Smooth character reveal based on time
    long time = System.currentTimeMillis();
    float elapsedSeconds = (time - this.streamingStartTime) / 1000.0f;
    float currentRevealed = isStreaming ? 
        Math.min(this.rawDialogText.length(), elapsedSeconds * 100.0f) : 
        this.rawDialogText.length();
    
    int remainingToDraw = (int) currentRevealed;
    int lineY = dialogTopPosition;

    // Push pose for text rendering on top
    guiGraphics.pose().pushPose();
    guiGraphics.pose().translate(0, 0, 500);

    for (int i = 0; i < this.cachedFullLines.size() && i < 6; i++) { // Limit to 6 visible lines
      if (remainingToDraw <= 0) break;
      
      FormattedCharSequence line = this.cachedFullLines.get(i);
      int lineLen = this.lineCharacterCounts.get(i);
      
      if (remainingToDraw >= lineLen) {
        guiGraphics.drawString(this.font, line, dialogLeftPosition, lineY, 0xFFFFFFFF, true);
        remainingToDraw -= lineLen;
      } else {
        guiGraphics.drawString(this.font, truncate(line, remainingToDraw), dialogLeftPosition, lineY, 0xFFFFFFFF, true);
        remainingToDraw = 0;
      }
      lineY += font.lineHeight + 2;
    }
    
    guiGraphics.pose().popPose();
  }

  private FormattedCharSequence truncate(FormattedCharSequence sequence, int length) {
    return (sink) -> {
      final int[] count = {0};
      return sequence.accept((index, style, codePoint) -> {
        if (count[0] >= length) return false;
        count[0]++;
        return sink.accept(index, style, codePoint);
      });
    };
  }

  private void setDialogText(DialogDataEntry dialogData) {
    if (dialogData == null) return;
    String text = dialogData.getDialogText(this.dialogMetaData);
    if (text == null || text.isBlank()) return;

    this.rawDialogText = text;
    this.dialogComponent = TextComponent.getText(text);
    this.isStreaming = true;
    this.streamingStartTime = System.currentTimeMillis();
    this.streamingTick = 0;

    // Calculate available width for text (between portraits, leaving room for buttons on right)
    int availableWidth = this.width - (PORTRAIT_AREA_WIDTH * 2) - 160;
    this.cachedFullLines = this.font.split(this.dialogComponent, availableWidth);
    this.lineCharacterCounts.clear();
    for (FormattedCharSequence line : this.cachedFullLines) {
      int[] len = {0};
      line.accept((idx, style, cp) -> { len[0]++; return true; });
      this.lineCharacterCounts.add(len[0]);
    }
    this.numberOfDialogLines = this.cachedFullLines.size();
  }

  @Override
  public void init() {
    this.clearWidgets();
    this.dialogButtons.clear();
    super.init();
    if (this.closeButton != null) this.closeButton.visible = false;
    
    setDialogScreenLayout(DialogUtils.getDialogScreenLayout(this.getDialogData(), this.font));
    this.setDialogText(this.getDialogData());
    
    if (this.hasDialogData() && this.getDialogData().getNumberOfDialogButtons() > 0) {
      for (DialogButtonEntry dialogButtonEntry : this.getDialogData().getDialogButtons()) {
        if (dialogButtonEntry == null) continue;
        addDialogButton(dialogButtonEntry);
      }
    }
    this.repositionButtons();
    
    if (this.getAdditionalScreenData() != null) {
      this.llmEnabled = this.getDialogDataSet() != null && this.getDialogDataSet().isLLMEnabled();
    }
    if (this.llmEnabled) initLLMChatUI();
  }

  private void addDialogButton(DialogButtonEntry dialogButtonEntry) {
    if (dialogButtonEntry == null) return;
    Component buttonName = dialogButtonEntry.getButtonName(28);
    ModernButton dialogButton = new ModernButton(0, 0, 120, 18, buttonName, onPress -> {
      if (this.getActionEventSet().hasActionEvent(ActionEventType.ON_BUTTON_CLICK)) {
        NetworkMessageHandlerManager.getServerHandler().executeActionEvent(this.getEasyNPCUUID(), ActionEventType.ON_BUTTON_CLICK);
      }
      if (dialogButtonEntry.hasActionData()) {
        NetworkMessageHandlerManager.getServerHandler().executeDialogButtonAction(this.getEasyNPCUUID(), this.getDialogUUID(), dialogButtonEntry.id());
      } else {
        this.onClose();
      }
    });
    this.dialogButtons.add(dialogButton);
  }

  private void repositionButtons() {
    int buttonWidth = 110;
    int buttonHeight = 18;
    int spacing = 4;
    
    // Position buttons on the right side of the screen to avoid overlapping text
    int xPos = this.width - PORTRAIT_AREA_WIDTH - buttonWidth - 30;
    
    // Position buttons from the bottom up
    int bottomY = this.height - 10 - buttonHeight;
    
    for (int i = 0; i < this.dialogButtons.size(); i++) {
      ModernButton btn = this.dialogButtons.get(i);
      btn.setWidth(buttonWidth);
      btn.setHeight(buttonHeight);
      btn.setX(xPos);
      btn.setY(bottomY - (i * (buttonHeight + spacing)));
      this.addRenderableWidget(btn);
    }
  }

  protected void initLLMChatUI() {
    int inputWidth = 160;
    int inputHeight = 18;
    int sendWidth = 45;
    int y = this.height - 8 - inputHeight;
    int x = (this.width / 2) - ((inputWidth + sendWidth + 5) / 2);

    this.llmChatInput = new EditBox(this.font, x, y, inputWidth, inputHeight, Component.literal("Chat..."));
    this.llmChatInput.setMaxLength(256);
    this.llmChatInput.setHint(Component.literal("Type message..."));
    this.llmChatInput.setBordered(true);
    this.addRenderableWidget(this.llmChatInput);

    this.llmSendButton = Button.builder(Component.literal("Send"), onPress -> sendLLMMessage())
        .bounds(x + inputWidth + 5, y, sendWidth, inputHeight)
        .build();
    this.addRenderableWidget(this.llmSendButton);
  }

  protected void sendLLMMessage() {
    if (this.llmChatInput == null || this.llmChatInput.getValue().trim().isEmpty()) return;
    String message = this.llmChatInput.getValue().trim();
    this.waitingForLLMResponse = true;
    updateDialogTextDirect("...");
    NetworkHandlerManager.sendMessageToServer(new LLMChatRequestMessage(this.getEasyNPCUUID(), message));
    this.llmChatInput.setValue("");
    this.llmChatInput.setFocused(true);
  }

  protected void updateDialogTextDirect(String text) {
    this.rawDialogText = text;
    this.dialogComponent = Component.literal(text);
    this.isStreaming = true;
    this.streamingStartTime = System.currentTimeMillis();
    this.streamingTick = 0;
    
    int availableWidth = this.width - (PORTRAIT_AREA_WIDTH * 2) - 160;
    this.cachedFullLines = this.font.split(this.dialogComponent, availableWidth);
    this.lineCharacterCounts.clear();
    for (FormattedCharSequence line : this.cachedFullLines) {
      int[] len = {0};
      line.accept((idx, style, cp) -> { len[0]++; return true; });
      this.lineCharacterCounts.add(len[0]);
    }
  }

  @Override
  public void updateTick() {
    super.updateTick();
    if (this.isStreaming) {
      this.streamingTick++;
      float elapsedSeconds = (System.currentTimeMillis() - this.streamingStartTime) / 1000.0f;
      if (elapsedSeconds * 100.0f >= this.rawDialogText.length()) {
        this.isStreaming = false;
      }
    }
  }

  @Override
  public void render(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
    if (this.getEasyNPC() == null) return;
    
    // LLM Handling
    if (this.llmEnabled && LLMDialogResponseManager.hasPendingResponse(this.getEasyNPCUUID())) {
      String streamedText = LLMDialogResponseManager.getStreamedText(this.getEasyNPCUUID(), 3);
      if (streamedText != null && !streamedText.equals(this.lastLLMResponse)) {
        this.lastLLMResponse = streamedText;
        updateDialogTextDirect(streamedText);
      }
      if (LLMDialogResponseManager.isStreamingComplete(this.getEasyNPCUUID())) {
        this.waitingForLLMResponse = false;
      }
    }
    
    // 1. Semi-transparent dark overlay
    guiGraphics.fill(0, 0, this.width, this.height, 0x88000000);

    // 2. Main dialog bar with gradient effect
    int barY = this.height - BAR_HEIGHT;
    
    // Gradient background for bar
    for (int i = 0; i < 8; i++) {
      int alpha = 0x10 + (i * 0x15);
      guiGraphics.fill(0, barY - 8 + i, this.width, barY - 7 + i, (alpha << 24));
    }
    
    // Main bar background
    guiGraphics.fill(0, barY, this.width, this.height, 0xEE1a1a1a);
    
    // Top border with accent
    guiGraphics.fill(0, barY, this.width, barY + 2, 0xFF00DDAA);
    guiGraphics.fill(0, barY + 2, this.width, barY + 3, 0xFF008866);

    // 3. Portrait frames
    renderPortraitFrames(guiGraphics);

    // 4. Speaker name tag
    renderSpeakerTag(guiGraphics, barY);

    // 5. GUI elements (buttons, input)
    super.render(guiGraphics, x, y, partialTicks);

    // 6. Portraits (smaller, cleaner)
    renderPortraits(guiGraphics, x, y, partialTicks);

    // 7. Dialog text (highest priority)
    renderDialog(guiGraphics, partialTicks);
    
    // 8. Streaming indicator
    if (this.isStreaming || this.waitingForLLMResponse) {
      renderStreamingIndicator(guiGraphics, partialTicks);
    }
  }

  private void renderPortraitFrames(GuiGraphics guiGraphics) {
    int barY = this.height - BAR_HEIGHT;
    int frameSize = PORTRAIT_AREA_WIDTH + 10;
    
    // NPC portrait frame (left)
    int npcFrameX = 5;
    int npcFrameY = barY + 5;
    guiGraphics.fill(npcFrameX, npcFrameY, npcFrameX + frameSize, this.height - 5, 0x66000000);
    guiGraphics.renderOutline(npcFrameX, npcFrameY, frameSize, this.height - 5 - npcFrameY, 0xFF00DDAA);
    
    // Player portrait frame (right)
    int playerFrameX = this.width - frameSize - 5;
    guiGraphics.fill(playerFrameX, npcFrameY, playerFrameX + frameSize, this.height - 5, 0x66000000);
    guiGraphics.renderOutline(playerFrameX, npcFrameY, frameSize, this.height - 5 - npcFrameY, 0xFF00DDAA);
  }

  private void renderSpeakerTag(GuiGraphics guiGraphics, int barY) {
    String speakerName = this.getEasyNPC().getLivingEntity().getName().getString();
    int tagPadding = 8;
    int tagWidth = this.font.width(speakerName) + tagPadding * 2;
    int tagHeight = 14;
    int tagX = PORTRAIT_AREA_WIDTH + 15;
    int tagY = barY - tagHeight + 2;
    
    // Tag background with gradient
    guiGraphics.fill(tagX, tagY, tagX + tagWidth, barY + 2, 0xEE1a1a1a);
    guiGraphics.fill(tagX, tagY, tagX + tagWidth, tagY + 1, 0xFF00DDAA);
    guiGraphics.fill(tagX, tagY, tagX + 1, barY + 2, 0xFF00DDAA);
    guiGraphics.fill(tagX + tagWidth - 1, tagY, tagX + tagWidth, barY + 2, 0xFF00DDAA);
    
    // Speaker name with accent color
    guiGraphics.drawString(this.font, speakerName, tagX + tagPadding, tagY + 3, 0xFF00FFCC, false);
  }

  private void renderStreamingIndicator(GuiGraphics guiGraphics, float partialTicks) {
    float time = System.currentTimeMillis() / 1000.0f * 5.0f;
    int dotCount = 3;
    int baseX = this.width - PORTRAIT_AREA_WIDTH - 20;
    int baseY = this.height - BAR_HEIGHT + 12;
    
    for (int i = 0; i < dotCount; i++) {
      float offset = Mth.sin(time + i * 0.5f) * 3;
      int alpha = (int) ((Mth.sin(time + i * 0.5f) + 1) * 0.5f * 200 + 55);
      int color = (alpha << 24) | 0x00DDAA;
      guiGraphics.fill(baseX + i * 8, baseY - (int) offset, baseX + i * 8 + 4, baseY - (int) offset + 4, color);
    }
  }

  private void renderPortraits(GuiGraphics guiGraphics, int x, int y, float pt) {
    long time = System.currentTimeMillis();
    float animTime = time / 1000.0f;
    int barY = this.height - BAR_HEIGHT;
    
    // NPC Portrait (left side, smaller)
    int npcX = 5 + (PORTRAIT_AREA_WIDTH + 10) / 2;
    int npcTop = barY + 8;
    int npcBottom = this.height - 8;
    
    // Subtle idle animation using time for smooth movement
    float npcHeadBob = Mth.sin(animTime * 2.0f) * 1.5f;
    float npcHeadTurn = isStreaming ? Mth.sin(animTime * 6.0f) * 5.0f : Mth.sin(animTime * 1.5f) * 2.0f;
    
    guiGraphics.pose().pushPose();
    guiGraphics.pose().translate(0, 0, 200);
    
    // Ensure 3D rendering state is correct for GUI
    RenderSystem.enableDepthTest();
    Lighting.setupForEntityInInventory();
    
    InventoryScreen.renderEntityInInventoryFollowsMouse(
        guiGraphics, 
        npcX - 25, npcTop, 
        npcX + 25, npcBottom,
        PORTRAIT_SIZE, 
        0.0F, 
        (float) npcX - (npcHeadTurn * 10.0f), 
        (float) (npcTop + npcBottom) / 2 + (npcHeadBob * 10.0f),
        this.getEasyNPC().getLivingEntity()
    );
    
    guiGraphics.pose().popPose();

    // Player Portrait (right side, smaller)
    if (this.minecraft != null && this.minecraft.player != null) {
      int playerX = this.width - 5 - (PORTRAIT_AREA_WIDTH + 10) / 2;
      int playerTop = barY + 8;
      int playerBottom = this.height - 8;
      
      // Check if hovering over buttons for reaction
      boolean isHoveringButton = false;
      for (ModernButton btn : this.dialogButtons) {
        if (btn.isHovered()) {
          isHoveringButton = true;
          break;
        }
      }
      
      float playerHeadBob = Mth.sin(animTime * 2.5f) * 1.2f;
      float playerHeadTurn = isHoveringButton ? Mth.sin(animTime * 8.0f) * 4.0f : Mth.sin(animTime * 1.2f) * 1.5f;
      
      guiGraphics.pose().pushPose();
      guiGraphics.pose().translate(0, 0, 200);
      
      InventoryScreen.renderEntityInInventoryFollowsMouse(
          guiGraphics, 
          playerX - 25, playerTop, 
          playerX + 25, playerBottom,
          PORTRAIT_SIZE, 
          0.0F, 
          (float) playerX - (playerHeadTurn * 10.0f), 
          (float) (playerTop + playerBottom) / 2 + (playerHeadBob * 10.0f),
          this.minecraft.player
      );
      
      guiGraphics.pose().popPose();
    }
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (this.isStreaming) {
      this.isStreaming = false;
      this.streamingStartTime = System.currentTimeMillis() - 100000;
      return true;
    }
    return super.mouseClicked(mouseX, mouseY, button);
  }

  @Override
  protected void renderLabels(GuiGraphics guiGraphics, int x, int y) {}
  
  @Override
  protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {}
  
  @Override
  public void renderBackground(GuiGraphics guiGraphics, int x, int y, float partialTicks) {}

  private class ModernButton extends TextButton {
    public ModernButton(int x, int y, int width, int height, Component label, OnPress onPress) {
      super(x, y, width, height, label, onPress);
    }
    
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
      boolean hovered = isHoveredOrFocused();
      
      // Background colors
      int bgColor = hovered ? 0xFFFFFFFF : 0xDD2a2a2a;
      int textColor = hovered ? 0xFF1a1a1a : 0xFFFFFFFF;
      int accentColor = 0xFF00DDAA;
      int borderColor = hovered ? accentColor : 0xFF444444;
      
      // Draw button background
      guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
      
      // Draw border
      guiGraphics.renderOutline(getX(), getY(), width, height, borderColor);
      
      // Draw accent line on left when hovered
      if (hovered) {
        guiGraphics.fill(getX(), getY(), getX() + 3, getY() + height, accentColor);
        // Subtle glow effect
        guiGraphics.fill(getX() + 3, getY(), getX() + 4, getY() + height, 0x6600DDAA);
      }
      
      // Draw centered text
      int textX = getX() + (hovered ? 4 : 0) + (width - (hovered ? 4 : 0)) / 2;
      int textY = getY() + (height - 8) / 2;
      guiGraphics.drawCenteredString(font, getMessage(), textX, textY, textColor);
      
      // Draw arrow indicator when hovered
      if (hovered) {
        guiGraphics.drawString(font, "▶", getX() + width - 12, textY, accentColor, false);
      }
    }
  }

  @Override
  public void onClose() {
    if (this.getActionEventSet().hasActionEvent(ActionEventType.ON_CLOSE_DIALOG)) {
      NetworkMessageHandlerManager.getServerHandler().executeActionEvent(this.getEasyNPCUUID(), ActionEventType.ON_CLOSE_DIALOG);
    }
    super.onClose();
  }

  @Override
  public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (this.llmEnabled && this.llmChatInput != null && this.llmChatInput.isFocused()) {
      if (keyCode == 257 || keyCode == 335) { // Enter or Numpad Enter
        sendLLMMessage();
        return true;
      }
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
  }
}