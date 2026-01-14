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
        super(Component.literal("管理员控制台"));
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 200;
        int buttonHeight = 32;
        int centerX = width / 2 - buttonWidth / 2;
        int startY = height / 2 - 80;
        int gap = 10;
        
        // Instance Manager
        addStrangerButton(centerX, startY, buttonWidth, buttonHeight,
            Component.literal("📋 实例管理"), this::openInstanceManager);
        
        // Story Manager
        addStrangerButton(centerX, startY + buttonHeight + gap, buttonWidth, buttonHeight,
            Component.literal("📚 故事管理"), this::openStoryManager);
        
        // Player Manager (placeholder)
        addStrangerButton(centerX, startY + (buttonHeight + gap) * 2, buttonWidth, buttonHeight,
            Component.literal("👥 玩家管理"), this::openPlayerManager);
        
        // System Stats (placeholder)
        addStrangerButton(centerX, startY + (buttonHeight + gap) * 3, buttonWidth, buttonHeight,
            Component.literal("📊 系统状态"), this::openSystemStats);
        
        // Close
        addStrangerButton(width / 2 - 60, height - 50, 120, 28,
            Component.literal("关闭"), this::onClose);
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
        
        graphics.drawString(font, "快速统计", panelX + 10, panelY + 8, COLOR_NEON_RED);
        graphics.fill(panelX + 5, panelY + 20, panelX + panelWidth - 5, panelY + 21, COLOR_BORDER);
        
        // Placeholder stats
        graphics.drawString(font, "活动实例: 0", panelX + 10, panelY + 28, COLOR_TEXT_BODY);
        graphics.drawString(font, "在线玩家: 0", panelX + 10, panelY + 42, COLOR_TEXT_BODY);
        graphics.drawString(font, "加载故事: 1", panelX + 10, panelY + 56, COLOR_TEXT_BODY);
        graphics.drawString(font, "服务器: 正常", panelX + 10, panelY + 70, 0xFF44FF44);
        
        // Right panel - recent activity
        int rightPanelX = width - 180;
        graphics.fill(rightPanelX, panelY, rightPanelX + panelWidth, panelY + panelHeight, 0xE0080808);
        drawPanelBorder(graphics, rightPanelX, panelY, panelWidth, panelHeight);
        
        graphics.drawString(font, "最近活动", rightPanelX + 10, panelY + 8, COLOR_NEON_RED);
        graphics.fill(rightPanelX + 5, panelY + 20, rightPanelX + panelWidth - 5, panelY + 21, COLOR_BORDER);
        
        graphics.drawString(font, "暂无活动", rightPanelX + 10, panelY + 35, COLOR_TEXT_DIM);
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
        AdminInstanceManagerScreen screen = new AdminInstanceManagerScreen();
        for (var info : com.warmpixel.storyadventure.network.ClientNetworkHandler.getLastSyncedInstances()) {
            screen.addInstance(info.id(), info.storyName(), info.node(), info.status(), info.playerCount(), info.elapsed());
        }
        Minecraft.getInstance().setScreen(screen);
    }
    
    private void openStoryManager() {
        AdminStoryManagerScreen screen = new AdminStoryManagerScreen();
        screen.addStory("stranger_things_hawkins", "怪奇物语：霍金斯事件", 20, "1.0.0", true, "");
        screen.addStory("example_story", "示例故事", 5, "1.0.0", true, "");
        Minecraft.getInstance().setScreen(screen);
    }
    
    private void openPlayerManager() {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§e玩家管理界面正在开发中..."));
        }
    }
    
    private void openSystemStats() {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§6=== 系统状态 ===\n§7内存: §f正常\n§7TPS: §a20.0\n§7活动实例: §f" + 
                com.warmpixel.storyadventure.network.ClientNetworkHandler.getLastSyncedInstances().size()));
        }
    }
}
