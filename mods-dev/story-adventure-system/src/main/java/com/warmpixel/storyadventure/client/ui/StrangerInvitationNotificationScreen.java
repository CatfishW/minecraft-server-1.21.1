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
    protected void init() {
        super.init();
        
        int x = width / 2;
        int y = height / 2;
        
        addStrangerButton(x - 105, y + 20, 100, 24, Component.literal("✔ 接受邀请"), this::accept);
        addStrangerButton(x + 5, y + 20, 100, 24, Component.literal("✕ 拒绝"), this::decline);
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
        int rectW = 240;
        int rectH = 100;
        int rectX = width / 2 - rectW / 2;
        int rectY = height / 2 - rectH / 2;
        
        graphics.fill(rectX, rectY, rectX + rectW, rectY + rectH, 0xF0080808);
        drawPanelBorder(graphics, rectX, rectY, rectW, rectH);
        
        graphics.drawCenteredString(font, "队伍邀请", width / 2, rectY + 15, COLOR_NEON_RED);
        graphics.drawCenteredString(font, "玩家 " + inviterName + " 邀请你加入他的队伍", width / 2, rectY + 40, 0xFFFFFFFF);
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
    }
}
