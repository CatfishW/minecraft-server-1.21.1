package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin UI for managing all active story instances.
 * Allows monitoring, intervention, and debugging.
 */
public class AdminInstanceManagerScreen extends StrangerScreen {
    
    private static final int INSTANCE_ENTRY_HEIGHT = 60;
    private static final int SIDEBAR_WIDTH = 200;
    
    private List<InstanceInfo> instances = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    
    // Admin action buttons
    private StrangerButton pauseButton;
    private StrangerButton resumeButton;
    private StrangerButton skipNodeButton;
    private StrangerButton forceCompleteButton;
    private StrangerButton terminateButton;
    
    public AdminInstanceManagerScreen() {
        super(Component.literal("实例管理面板"));
    }
    
    public void clearInstances() {
        instances.clear();
        selectedIndex = -1;
    }
    
    public void addInstance(UUID instanceId, String storyName, String currentNode, 
                            String status, int playerCount, long elapsedMs) {
        instances.add(new InstanceInfo(instanceId, storyName, currentNode, status, playerCount, elapsedMs));
    }
    
    @Override
    protected void init() {
        super.init();
        
        int sidebarX = width - SIDEBAR_WIDTH - 20;
        int buttonY = 100;
        int buttonWidth = SIDEBAR_WIDTH - 20;
        int buttonHeight = 24;
        int gap = 6;
        
        // Pause button
        pauseButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.literal("⏸ 暂停实例"), this::pauseInstance);
        buttonY += buttonHeight + gap;
        
        // Resume button
        resumeButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.literal("▶ 恢复实例"), this::resumeInstance);
        buttonY += buttonHeight + gap;
        
        // Skip node button
        skipNodeButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.literal("⏭ 跳过节点"), this::skipNode);
        buttonY += buttonHeight + gap;
        
        // Force complete button
        forceCompleteButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.literal("✓ 强制完成"), this::forceComplete);
        buttonY += buttonHeight + gap;
        
        // Terminate button
        terminateButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.literal("✕ 终止实例"), this::terminateInstance);
        buttonY += buttonHeight + gap * 3;
        
        // Refresh button
        addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.literal("🔄 刷新列表"), this::refreshList);
        
        // Close button at bottom
        addStrangerButton(width / 2 - 60, height - 45, 120, 28,
            Component.literal("关闭"), this::onClose);
        
        updateButtonStates();
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Main list area
        int listX = 30;
        int listY = 50;
        int listWidth = width - SIDEBAR_WIDTH - 70;
        int listHeight = height - 110;
        
        // List background
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xE0080808);
        drawPanelBorder(graphics, listX, listY, listWidth, listHeight);
        
        // Column headers
        graphics.drawString(font, "故事", listX + 10, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, "当前节点", listX + 150, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, "状态", listX + 300, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, "玩家", listX + 380, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, "时长", listX + 430, listY + 8, COLOR_NEON_RED);
        
        graphics.fill(listX + 5, listY + 22, listX + listWidth - 5, listY + 23, COLOR_BORDER);
        
        // Instance entries
        int y = listY + 28;
        int visibleCount = (listHeight - 30) / INSTANCE_ENTRY_HEIGHT;
        int visibleEnd = Math.min(scrollOffset + visibleCount, instances.size());
        
        for (int i = scrollOffset; i < visibleEnd; i++) {
            renderInstanceEntry(graphics, instances.get(i), listX + 5, y, listWidth - 10, 
                i == selectedIndex, mouseX, mouseY);
            y += INSTANCE_ENTRY_HEIGHT;
        }
        
        // Empty state
        if (instances.isEmpty()) {
            graphics.drawCenteredString(font, "没有活动的实例", listX + listWidth / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        }
        
        // Sidebar - selected instance details
        renderSidebar(graphics);
        
        // Stats bar at bottom
        renderStatsBar(graphics);
    }
    
    private void renderInstanceEntry(GuiGraphics graphics, InstanceInfo info, 
                                      int x, int y, int width, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + INSTANCE_ENTRY_HEIGHT - 2;
        
        // Background
        int bgColor = selected ? 0xFF1A0808 : (hovered ? 0xFF100505 : 0xFF0A0A0A);
        graphics.fill(x, y, x + width, y + INSTANCE_ENTRY_HEIGHT - 2, bgColor);
        
        // Left accent bar
        int accentColor = getStatusColor(info.status);
        graphics.fill(x, y, x + 3, y + INSTANCE_ENTRY_HEIGHT - 2, accentColor);
        
        // Story name
        graphics.drawString(font, truncate(info.storyName, 18), x + 10, y + 8, COLOR_TEXT_BODY);
        
        // Instance ID (dimmed)
        String shortId = info.id.toString().substring(0, 8);
        graphics.drawString(font, shortId, x + 10, y + 22, COLOR_TEXT_DIM);
        
        // Current node
        graphics.drawString(font, truncate(info.currentNode, 18), x + 150, y + 8, COLOR_TEXT_BODY);
        
        // Status with color
        graphics.drawString(font, getStatusText(info.status), x + 300, y + 8, accentColor);
        
        // Player count
        graphics.drawString(font, String.valueOf(info.playerCount), x + 385, y + 8, COLOR_TEXT_BODY);
        
        // Elapsed time
        String timeStr = formatDuration(info.elapsedMs);
        graphics.drawString(font, timeStr, x + 430, y + 8, COLOR_TEXT_DIM);
        
        // Progress bar placeholder
        int progressWidth = width - 500;
        if (progressWidth > 50) {
            graphics.fill(x + 480, y + 10, x + 480 + progressWidth, y + 14, 0xFF222222);
            int filledWidth = (int)(progressWidth * 0.35); // Placeholder progress
            graphics.fill(x + 480, y + 10, x + 480 + filledWidth, y + 14, accentColor);
        }
    }
    
    private void renderSidebar(GuiGraphics graphics) {
        int sidebarX = width - SIDEBAR_WIDTH - 20;
        int sidebarY = 50;
        
        // Sidebar background
        graphics.fill(sidebarX, sidebarY, sidebarX + SIDEBAR_WIDTH, height - 60, 0xE0080808);
        drawPanelBorder(graphics, sidebarX, sidebarY, SIDEBAR_WIDTH, height - 110);
        
        // Title
        graphics.drawString(font, "管理操作", sidebarX + 10, sidebarY + 8, COLOR_NEON_RED);
        graphics.fill(sidebarX + 5, sidebarY + 22, sidebarX + SIDEBAR_WIDTH - 5, sidebarY + 23, COLOR_BORDER);
        
        // Selected instance info
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            int y = sidebarY + 30;
            
            graphics.drawString(font, "选中:", sidebarX + 10, y, COLOR_TEXT_DIM);
            y += 12;
            graphics.drawString(font, info.storyName, sidebarX + 10, y, COLOR_TEXT_BODY);
            y += 14;
            graphics.drawString(font, "ID: " + info.id.toString().substring(0, 8), sidebarX + 10, y, COLOR_TEXT_DIM);
        } else {
            graphics.drawString(font, "未选中实例", sidebarX + 10, sidebarY + 35, COLOR_TEXT_DIM);
        }
    }
    
    private void renderStatsBar(GuiGraphics graphics) {
        int barY = height - 55;
        
        // Stats background
        graphics.fill(30, barY, width - 30, barY + 20, 0xC0080808);
        
        // Stats
        String stats = String.format("活动实例: %d | 总玩家: %d | 服务器负载: 正常",
            instances.size(),
            instances.stream().mapToInt(i -> i.playerCount).sum());
        
        graphics.drawString(font, stats, 40, barY + 6, COLOR_TEXT_DIM);
    }
    
    private void drawPanelBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
        
        int cs = 8;
        graphics.fill(x, y, x + cs, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + cs, COLOR_NEON_RED);
        graphics.fill(x + w - cs, y, x + w, y + 2, COLOR_NEON_RED);
        graphics.fill(x + w - 2, y, x + w, y + cs, COLOR_NEON_RED);
    }
    
    private int getStatusColor(String status) {
        return switch (status.toUpperCase()) {
            case "RUNNING" -> 0xFF44FF44;
            case "PAUSED" -> 0xFFFFCC00;
            case "COMPLETED" -> 0xFF4488FF;
            case "FAILED" -> 0xFFFF4444;
            default -> COLOR_TEXT_DIM;
        };
    }
    
    private String getStatusText(String status) {
        return switch (status.toUpperCase()) {
            case "RUNNING" -> "运行中";
            case "PAUSED" -> "已暂停";
            case "COMPLETED" -> "已完成";
            case "FAILED" -> "已失败";
            case "CREATED" -> "已创建";
            default -> status;
        };
    }
    
    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen - 2) + ".." : text;
    }
    
    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check for instance selection
        int listX = 30;
        int listY = 50;
        int listWidth = width - SIDEBAR_WIDTH - 70;
        int listHeight = height - 110;
        
        if (mouseX >= listX && mouseX < listX + listWidth && 
            mouseY >= listY + 28 && mouseY < listY + listHeight) {
            int relY = (int)mouseY - listY - 28;
            int clickedIndex = scrollOffset + relY / INSTANCE_ENTRY_HEIGHT;
            if (clickedIndex < instances.size()) {
                selectedIndex = clickedIndex;
                updateButtonStates();
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        int visibleCount = (height - 110 - 30) / INSTANCE_ENTRY_HEIGHT;
        if (vAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (vAmount < 0 && scrollOffset + visibleCount < instances.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }
    
    private void updateButtonStates() {
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < instances.size();
        
        if (pauseButton != null) pauseButton.active = hasSelection;
        if (resumeButton != null) resumeButton.active = hasSelection;
        if (skipNodeButton != null) skipNodeButton.active = hasSelection;
        if (forceCompleteButton != null) forceCompleteButton.active = hasSelection;
        if (terminateButton != null) terminateButton.active = hasSelection;
    }
    
    private void sendCommand(String command) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }
    
    private void showMessage(String message) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
    
    // Admin action methods
    private void pauseInstance() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            String shortId = info.id.toString().substring(0, 8);
            sendCommand("storyadmin pause " + shortId);
            showMessage("§e正在暂停实例 " + shortId + "...");
            info = new InstanceInfo(info.id, info.storyName, info.currentNode, "PAUSED", info.playerCount, info.elapsedMs);
            instances.set(selectedIndex, info);
        }
    }
    
    private void resumeInstance() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            String shortId = info.id.toString().substring(0, 8);
            sendCommand("storyadmin resume " + shortId);
            showMessage("§a正在恢复实例 " + shortId + "...");
            info = new InstanceInfo(info.id, info.storyName, info.currentNode, "RUNNING", info.playerCount, info.elapsedMs);
            instances.set(selectedIndex, info);
        }
    }
    
    private void skipNode() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            String shortId = info.id.toString().substring(0, 8);
            // Open a simple input dialog or show command hint
            showMessage("§e输入命令跳转节点：§f/storyadmin skip " + shortId + " <节点ID>");
            showMessage("§7提示：使用 /storyadmin debug " + shortId + " 查看可用节点");
        }
    }
    
    private void forceComplete() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            String shortId = info.id.toString().substring(0, 8);
            sendCommand("storyadmin complete " + shortId + " success");
            showMessage("§a已强制完成实例 " + shortId);
        }
    }
    
    private void terminateInstance() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            String shortId = info.id.toString().substring(0, 8);
            sendCommand("storyadmin terminate " + shortId);
            showMessage("§c已终止实例 " + shortId);
            instances.remove(selectedIndex);
            selectedIndex = -1;
            updateButtonStates();
        }
    }
    
    private void refreshList() {
        sendCommand("storyadmin instances");
        showMessage("§e正在刷新实例列表...");
        // In a full implementation, this would trigger a network request
        // and the server would send back the instance list
    }
    
    public record InstanceInfo(UUID id, String storyName, String currentNode, 
                                String status, int playerCount, long elapsedMs) {}
}
