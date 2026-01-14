package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin UI for managing story definitions - reload, validate, view structure.
 */
public class AdminStoryManagerScreen extends StrangerScreen {
    
    private static final int STORY_ENTRY_HEIGHT = 45;
    
    private List<StoryInfo> stories = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    
    public AdminStoryManagerScreen() {
        super(Component.literal("故事管理"));
    }
    
    public void addStory(String id, String name, int nodeCount, String version, boolean valid, String errorMsg) {
        stories.add(new StoryInfo(id, name, nodeCount, version, valid, errorMsg));
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 140;
        int buttonHeight = 26;
        int rightX = width - 170;
        int y = 80;
        
        // Reload all button
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.literal("🔄 重载全部"), this::reloadAll);
        y += buttonHeight + 8;
        
        // Validate button
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.literal("✓ 验证选中"), this::validateSelected);
        y += buttonHeight + 8;
        
        // View structure button
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.literal("📊 查看节点"), this::viewStructure);
        y += buttonHeight + 8;
        
        // Set spawn location
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.literal("📍 设置出生点"), this::setSpawnLocation);
        y += buttonHeight + 8;
        
        // Set return location
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.literal("🏠 设置返回点"), this::setReturnLocation);
        y += buttonHeight + 8;
        
        // Teleport to scene
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.literal("✈ 传送到场景"), this::teleportToScene);
        y += buttonHeight + 20;
        
        // Create new button
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.literal("+ 创建模板"), this::createTemplate);
        
        // Close button
        addStrangerButton(width / 2 - 60, height - 45, 120, 28,
            Component.literal("关闭"), this::onClose);
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Story list
        int listX = 30;
        int listY = 50;
        int listWidth = width - 230;
        int listHeight = height - 110;
        
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xE0080808);
        drawPanelBorder(graphics, listX, listY, listWidth, listHeight);
        
        // Headers
        graphics.drawString(font, "ID", listX + 10, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, "名称", listX + 150, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, "节点数", listX + 320, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, "版本", listX + 380, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, "状态", listX + 440, listY + 8, COLOR_NEON_RED);
        
        graphics.fill(listX + 5, listY + 22, listX + listWidth - 5, listY + 23, COLOR_BORDER);
        
        // Story entries
        int y = listY + 28;
        int visibleCount = (listHeight - 30) / STORY_ENTRY_HEIGHT;
        int visibleEnd = Math.min(scrollOffset + visibleCount, stories.size());
        
        for (int i = scrollOffset; i < visibleEnd; i++) {
            renderStoryEntry(graphics, stories.get(i), listX + 5, y, listWidth - 10, 
                i == selectedIndex, mouseX, mouseY);
            y += STORY_ENTRY_HEIGHT;
        }
        
        if (stories.isEmpty()) {
            graphics.drawCenteredString(font, "没有加载的故事", listX + listWidth / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        }
        
        // Selected story details
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            renderStoryDetails(graphics, stories.get(selectedIndex));
        }
        
        // Stats
        int validCount = (int) stories.stream().filter(s -> s.valid).count();
        String stats = String.format("总计: %d 个故事 | 有效: %d | 错误: %d", 
            stories.size(), validCount, stories.size() - validCount);
        graphics.drawString(font, stats, 40, height - 70, COLOR_TEXT_DIM);
    }
    
    private void renderStoryEntry(GuiGraphics graphics, StoryInfo info, 
                                   int x, int y, int width, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + STORY_ENTRY_HEIGHT - 2;
        
        int bgColor = selected ? 0xFF1A0808 : (hovered ? 0xFF100505 : 0xFF0A0A0A);
        graphics.fill(x, y, x + width, y + STORY_ENTRY_HEIGHT - 2, bgColor);
        
        // Status indicator
        int statusColor = info.valid ? 0xFF44FF44 : 0xFFFF4444;
        graphics.fill(x, y, x + 3, y + STORY_ENTRY_HEIGHT - 2, statusColor);
        
        // ID
        graphics.drawString(font, truncate(info.id, 18), x + 10, y + 8, COLOR_TEXT_BODY);
        
        // Name
        graphics.drawString(font, truncate(info.name, 20), x + 150, y + 8, COLOR_TEXT_BODY);
        
        // Node count
        graphics.drawString(font, String.valueOf(info.nodeCount), x + 325, y + 8, COLOR_TEXT_DIM);
        
        // Version
        graphics.drawString(font, info.version, x + 380, y + 8, COLOR_TEXT_DIM);
        
        // Status
        String statusText = info.valid ? "✓ 有效" : "✕ 错误";
        graphics.drawString(font, statusText, x + 440, y + 8, statusColor);
        
        // Error preview
        if (!info.valid && !info.errorMsg.isEmpty()) {
            graphics.drawString(font, truncate(info.errorMsg, 60), x + 10, y + 22, 0xFFAA4444);
        }
    }
    
    private void renderStoryDetails(GuiGraphics graphics, StoryInfo info) {
        int detailsY = height - 100;
        
        if (!info.valid && !info.errorMsg.isEmpty()) {
            graphics.fill(30, detailsY - 5, width - 30, detailsY + 25, 0xE01A0808);
            graphics.drawString(font, "⚠ 错误详情:", 40, detailsY, 0xFFFF6666);
            graphics.drawString(font, info.errorMsg, 40, detailsY + 12, 0xFFAA4444);
        }
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
    
    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen - 2) + ".." : text;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = 30;
        int listY = 50;
        int listWidth = width - 230;
        int listHeight = height - 110;
        
        if (mouseX >= listX && mouseX < listX + listWidth && 
            mouseY >= listY + 28 && mouseY < listY + listHeight) {
            int relY = (int)mouseY - listY - 28;
            int clickedIndex = scrollOffset + relY / STORY_ENTRY_HEIGHT;
            if (clickedIndex < stories.size()) {
                selectedIndex = clickedIndex;
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        int visibleCount = (height - 110 - 30) / STORY_ENTRY_HEIGHT;
        if (vAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (vAmount < 0 && scrollOffset + visibleCount < stories.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
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
    
    private void reloadAll() {
        sendCommand("storyadmin reload");
        showMessage("§a正在重新加载所有故事...");
    }
    
    private void validateSelected() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            sendCommand("storyadmin reload");
            showMessage("§e正在验证故事 '" + info.id + "'...");
        } else {
            showMessage("§c请先选择一个故事");
        }
    }
    
    private void viewStructure() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            showMessage("§6=== 故事结构: " + info.name + " ===");
            showMessage("§7ID: §f" + info.id);
            showMessage("§7节点数: §f" + info.nodeCount);
            showMessage("§7版本: §f" + info.version);
            showMessage("§7使用命令 §f/storyadmin debug <instance_id> §7查看运行中实例的详细节点信息");
        } else {
            showMessage("§c请先选择一个故事");
        }
    }
    
    private void setSpawnLocation() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            sendCommand("storyadmin setlocation " + info.id + " spawn");
            showMessage("§a已将当前位置设为故事 '" + info.id + "' 的出生点");
        } else {
            showMessage("§c请先选择一个故事");
        }
    }
    
    private void setReturnLocation() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            sendCommand("storyadmin setlocation " + info.id + " return");
            showMessage("§a已将当前位置设为故事 '" + info.id + "' 的返回点");
        } else {
            showMessage("§c请先选择一个故事");
        }
    }
    
    private void teleportToScene() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            sendCommand("storyadmin tp " + info.id);
            showMessage("§a正在传送到故事 '" + info.id + "' 的场景...");
            onClose();
        } else {
            showMessage("§c请先选择一个故事");
        }
    }
    
    private void createTemplate() {
        showMessage("§e正在创建新故事模板...");
        showMessage("§7新模板将保存到: §fconfig/storyadventure/stories/new_story.json");
        showMessage("§7请编辑该文件后使用 §f/storyadmin reload §7重新加载");
    }
    
    public record StoryInfo(String id, String name, int nodeCount, String version, boolean valid, String errorMsg) {}
}
