package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Main admin dashboard with quick access to all admin functions.
 */
public class AdminDashboardScreen extends StrangerScreen {
    
    public AdminDashboardScreen() {
        super(Component.translatable("gui.storyadventure.admin.dashboard.title"));
    }
    
    @Override
    protected int getWindowWidth() {
        return Math.min(width - 20, 500);
    }

    @Override
    protected int getWindowHeight() {
        return Math.min(height - 20, 360);
    }

    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 180;
        int buttonHeight = 28;
        int centerX = guiLeft + (guiWidth - buttonWidth) / 2;
        int startY = guiTop + 60;
        int gap = 6;
        
        // Instance Manager
        addStrangerButton(centerX, startY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.dashboard.instances"), this::openInstanceManager);
        
        // Story Manager
        addStrangerButton(centerX, startY + buttonHeight + gap, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.dashboard.stories"), this::openStoryManager);
        
        // Trigger Manager
        addStrangerButton(centerX, startY + (buttonHeight + gap) * 2, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.dashboard.triggers"), this::openTriggerManager);
        
        // Player Manager
        addStrangerButton(centerX, startY + (buttonHeight + gap) * 3, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.dashboard.players"), this::openPlayerManager);
        
        // System Stats
        addStrangerButton(centerX, startY + (buttonHeight + gap) * 4, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.dashboard.stats"), this::openSystemStats);
        
        // Close
        addStrangerButton(guiLeft + (guiWidth - 100) / 2, guiTop + guiHeight - 35, 100, 24,
            Component.translatable("gui.storyadventure.admin.dashboard.close"), this::onClose);
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Quick stats panel (Left side of buttons)
        int panelX = guiLeft + 15;
        int panelY = guiTop + 60;
        int panelWidth = (guiWidth - 180 - 60) / 2; // Split remaining space
        if (panelWidth < 80) panelWidth = 0; // Hide if too small
        
        if (panelWidth > 0) {
            int panelHeight = 85;
            graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0080808);
            drawPanelBorder(graphics, panelX, panelY, panelWidth, panelHeight);
            
            graphics.drawString(font, "STATS", panelX + 5, panelY + 5, COLOR_NEON_RED);
            graphics.drawString(font, "ACT: 0", panelX + 5, panelY + 20, COLOR_TEXT_BODY);
            graphics.drawString(font, "PLR: 0", panelX + 5, panelY + 34, COLOR_TEXT_BODY);
        }
        
        // Right panel - recent activity
        int rightPanelX = guiLeft + guiWidth - panelWidth - 15;
        if (panelWidth > 0) {
            int panelHeight = 85;
            graphics.fill(rightPanelX, panelY, rightPanelX + panelWidth, panelY + panelHeight, 0xE0080808);
            drawPanelBorder(graphics, rightPanelX, panelY, panelWidth, panelHeight);
            
            graphics.drawString(font, "LOGS", rightPanelX + 5, panelY + 5, COLOR_NEON_RED);
            graphics.drawString(font, "None", rightPanelX + 5, panelY + 20, COLOR_TEXT_DIM);
        }
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        int cs = 6;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
    }
    
    private void openInstanceManager() {
        sendCommand("storyadminui instances");
    }
    
    private void openStoryManager() {
        sendCommand("storyadminui stories");
    }
    
    private void openPlayerManager() {
        // We'll keep this one for now if there's no server command yet, or add it
        AdminPlayerManagerScreen screen = new AdminPlayerManagerScreen();
        // Populate with some placeholder data - in real implementation this comes from network sync
        screen.addPlayer(java.util.UUID.randomUUID(), "测试玩家1", "stranger_things_hawkins", "meet_joyce", true);
        screen.addPlayer(java.util.UUID.randomUUID(), "测试玩家2", "stranger_things_hawkins", "meet_joyce", false);
        Minecraft.getInstance().setScreen(screen);
    }
    
    private void openSystemStats() {
        AdminSystemStatsScreen screen = new AdminSystemStatsScreen();
        Minecraft.getInstance().setScreen(screen);
    }
    
    private void openTriggerManager() {
        Minecraft.getInstance().setScreen(new TriggerBoxManagerScreen());
    }

    private void sendCommand(String command) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }
}
