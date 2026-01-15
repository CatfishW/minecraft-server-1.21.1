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
    protected void init() {
        super.init();
        
        int buttonWidth = 200;
        int buttonHeight = 32;
        int centerX = width / 2 - buttonWidth / 2;
        int startY = height / 2 - 100;
        int gap = 8;
        
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
        addStrangerButton(width / 2 - 60, height - 50, 120, 28,
            Component.translatable("gui.storyadventure.admin.dashboard.close"), this::onClose);
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Quick stats panel
        int panelX = 30;
        int panelY = 50;
        int panelWidth = 150;
        int panelHeight = 100;
        
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0080808);
        drawPanelBorder(graphics, panelX, panelY, panelWidth, panelHeight);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.quick_stats"), panelX + 10, panelY + 8, COLOR_NEON_RED);
        graphics.fill(panelX + 5, panelY + 20, panelX + panelWidth - 5, panelY + 21, COLOR_BORDER);
        
        // Placeholder stats
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.active_instances", 0), panelX + 10, panelY + 28, COLOR_TEXT_BODY);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.online_players", 0), panelX + 10, panelY + 42, COLOR_TEXT_BODY);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.loaded_stories", 1), panelX + 10, panelY + 56, COLOR_TEXT_BODY);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.server_status", 
            Component.translatable("gui.storyadventure.admin.dashboard.status_ok").getString()), panelX + 10, panelY + 70, 0xFF44FF44);
        
        // Right panel - recent activity
        int rightPanelX = width - 180;
        graphics.fill(rightPanelX, panelY, rightPanelX + panelWidth, panelY + panelHeight, 0xE0080808);
        drawPanelBorder(graphics, rightPanelX, panelY, panelWidth, panelHeight);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.recent_activity"), rightPanelX + 10, panelY + 8, COLOR_NEON_RED);
        graphics.fill(rightPanelX + 5, panelY + 20, rightPanelX + panelWidth - 5, panelY + 21, COLOR_BORDER);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.dashboard.no_activity"), rightPanelX + 10, panelY + 35, COLOR_TEXT_DIM);
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
