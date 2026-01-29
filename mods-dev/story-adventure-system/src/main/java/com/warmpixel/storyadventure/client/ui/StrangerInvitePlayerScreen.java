package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import com.warmpixel.storyadventure.network.InvitePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Small overlay to input a player's name for invitation.
 */
public class StrangerInvitePlayerScreen extends StrangerScreen {
    
    private EditBox nameInput;
    private final StrangerScreen parent;
    
    public StrangerInvitePlayerScreen(StrangerScreen parent) {
        super(Component.literal("邀请玩家"));
        this.parent = parent;
    }
    
    @Override
    protected int getWindowWidth() {
        return 240;
    }

    @Override
    protected int getWindowHeight() {
        return 120;
    }

    @Override
    protected void init() {
        super.init();
        
        int boxWidth = 180;
        int boxHeight = 20;
        int x = guiLeft + (guiWidth - boxWidth) / 2;
        int y = guiTop + 45;
        
        nameInput = new EditBox(font, x, y, boxWidth, boxHeight, Component.literal("玩家名称"));
        nameInput.setMaxLength(16);
        nameInput.setFocused(true);
        addWidget(nameInput);
        
        addStrangerButton(x, y + 30, 85, 20, Component.literal("发送邀请"), this::sendInvite);
        addStrangerButton(x + 95, y + 30, 85, 20, Component.literal("取消"), () -> minecraft.setScreen(parent));
    }
    
    private void sendInvite() {
        String name = nameInput.getValue().trim();
        if (!name.isEmpty()) {
            ClientPlayNetworking.send(new InvitePayload(name, false, false));
            minecraft.setScreen(parent);
        }
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Window title and prompt
        graphics.drawCenteredString(font, "INVITE OPERATIVE", guiLeft + guiWidth / 2, guiTop + 15, COLOR_NEON_RED);
        graphics.drawCenteredString(font, "Enter player name", guiLeft + guiWidth / 2, guiTop + 30, COLOR_TEXT_DIM);
        
        nameInput.render(graphics, mouseX, mouseY, partialTick);
    }
}
