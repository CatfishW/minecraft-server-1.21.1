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
    protected void init() {
        super.init();
        
        int boxWidth = 200;
        int boxHeight = 20;
        int x = width / 2 - boxWidth / 2;
        int y = height / 2 - 20;
        
        nameInput = new EditBox(font, x, y, boxWidth, boxHeight, Component.literal("玩家名称"));
        nameInput.setMaxLength(16);
        nameInput.setFocused(true);
        addWidget(nameInput);
        
        addStrangerButton(x, y + 30, 95, 20, Component.literal("发送邀请"), this::sendInvite);
        addStrangerButton(x + 105, y + 30, 95, 20, Component.literal("取消"), () -> minecraft.setScreen(parent));
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
        int panelW = 240;
        int panelH = 120;
        int panelX = width / 2 - panelW / 2;
        int panelY = height / 2 - panelH / 2;
        
        // PAnel Background
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101216);
        renderRectOutline(graphics, panelX, panelY, panelW, panelH, COLOR_BORDER);
        
        // Title
        graphics.drawCenteredString(font, "INVITE OPERATIVE", width / 2, panelY + 15, COLOR_NEON_RED);
        graphics.drawCenteredString(font, "Enter player name to send invitation", width / 2, panelY + 30, COLOR_TEXT_DIM);
        
        nameInput.render(graphics, mouseX, mouseY, partialTick);
    }
}
