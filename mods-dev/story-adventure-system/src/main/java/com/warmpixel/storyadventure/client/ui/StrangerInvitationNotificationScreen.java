package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.warmpixel.storyadventure.network.InvitePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Screen that pops up when you receive an invitation.
 */
public class StrangerInvitationNotificationScreen extends StrangerScreen {
    
    private final String inviterName;
    
    public StrangerInvitationNotificationScreen(String inviterName) {
        super(Component.literal("收到邀请"));
        this.inviterName = inviterName;
    }
    
    @Override
    protected int getWindowWidth() {
        return 240;
    }

    @Override
    protected int getWindowHeight() {
        return 100;
    }

    @Override
    protected void init() {
        super.init();
        
        int x = guiLeft + guiWidth / 2;
        int y = guiTop + guiHeight / 2;
        
        addStrangerButton(x - 105, y + 10, 100, 24, Component.literal("✔ 接受邀请"), this::accept);
        addStrangerButton(x + 5, y + 10, 100, 24, Component.literal("✕ 拒绝"), this::decline);
    }
    
    private void accept() {
        ClientPlayNetworking.send(new InvitePayload(inviterName, true, true));
        onClose();
    }
    
    private void decline() {
        ClientPlayNetworking.send(new InvitePayload(inviterName, true, false));
        onClose();
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(font, "队伍邀请", guiLeft + guiWidth / 2, guiTop + 15, COLOR_TEXT_TITLE);
        graphics.drawCenteredString(font, "玩家 " + inviterName, guiLeft + guiWidth / 2, guiTop + 40, COLOR_TEXT_BODY);
        graphics.drawCenteredString(font, "邀请你加入他的队伍", guiLeft + guiWidth / 2, guiTop + 52, COLOR_TEXT_BODY);
    }
}
