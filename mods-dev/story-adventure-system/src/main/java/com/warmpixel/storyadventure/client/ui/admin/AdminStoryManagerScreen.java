package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.warmpixel.storyadventure.network.AdminStoryActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

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
        super(Component.translatable("gui.storyadventure.admin.stories.title"));
    }
    
    public void addStory(String id, String name, int nodeCount, String version, boolean valid, String errorMsg) {
        stories.add(new StoryInfo(id, name, nodeCount, version, valid, errorMsg));
    }
    
    public void clearStories() {
        stories.clear();
        selectedIndex = -1;
    }
    
    @Override
    protected int getWindowWidth() {
        return Math.min(width - 20, 600);
    }

    @Override
    protected int getWindowHeight() {
        return Math.min(height - 20, 400);
    }

    @Override
    protected void init() {
        super.init();
        
        int sidebarX = guiLeft + guiWidth - 160;
        int buttonWidth = 140;
        int buttonHeight = 24;
        int y = guiTop + 50;
        
        // Action buttons - compact layout
        addStrangerButton(sidebarX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.reload_all"), this::reloadAll);
        y += buttonHeight + 4;
        
        addStrangerButton(sidebarX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.validate_selected"), this::validateSelected);
        y += buttonHeight + 4;
        
        addStrangerButton(sidebarX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.graph_editor"), this::openGraphEditor);
        y += buttonHeight + 12;
        
        // Locations Group
        addStrangerButton(sidebarX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.set_spawn"), this::setSpawnLocation);
        y += buttonHeight + 4;
        
        addStrangerButton(sidebarX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.set_return"), this::setReturnLocation);
        y += buttonHeight + 4;
        
        addStrangerButton(sidebarX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.tp_to_scene"), this::teleportToScene);
        y += buttonHeight + 12;
        
        addStrangerButton(sidebarX, y, buttonWidth, buttonHeight, Component.translatable("gui.storyadventure.admin.stories.create_template"), this::createTemplate);
        
        // Close button
        addStrangerButton(guiLeft + guiWidth / 2 - 60, guiTop + guiHeight - 35, 120, 24,
            Component.translatable("gui.storyadventure.admin.stories.close"), this::onClose);
            
        // Request fresh data - use direct payload to avoid re-open loop
        refreshStories();
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Story list
        int listX = guiLeft + 15;
        int listY = guiTop + 45;
        int listWidth = guiWidth - 190;
        int listHeight = guiHeight - 115;
        
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xE0080808);
        drawPanelBorder(graphics, listX, listY, listWidth, listHeight);
        
        // Headers
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stories.col.id"), listX + 10, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stories.col.name"), listX + 110, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stories.col.nodes"), listX + listWidth - 150, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stories.col.version"), listX + listWidth - 100, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stories.col.status"), listX + listWidth - 50, listY + 8, COLOR_NEON_RED);
        
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
            graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.stories.empty"), listX + listWidth / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        }
        
        // Selected story details
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            renderStoryDetails(graphics, stories.get(selectedIndex));
        }
        
        // Stats
        int validCount = (int) stories.stream().filter(s -> s.valid).count();
        Component stats = Component.translatable("gui.storyadventure.admin.stories.stats", 
            stories.size(), validCount, stories.size() - validCount);
        graphics.drawString(font, stats, guiLeft + 20, guiTop + guiHeight - 65, COLOR_TEXT_DIM);
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
        graphics.drawString(font, truncate(info.name, 20), x + 110, y + 8, COLOR_TEXT_BODY);
        
        // Node count
        graphics.drawString(font, String.valueOf(info.nodeCount), x + width - 140, y + 8, COLOR_TEXT_DIM);
        
        // Version
        graphics.drawString(font, info.version, x + width - 90, y + 8, COLOR_TEXT_DIM);
        
        // Status
        String statusText = info.valid ? Component.translatable("gui.storyadventure.admin.stories.status.valid").getString() 
                                       : Component.translatable("gui.storyadventure.admin.stories.status.error").getString();
        graphics.drawString(font, statusText, x + width - 40, y + 8, statusColor);
        
        // Error preview
        if (!info.valid && !info.errorMsg.isEmpty()) {
            graphics.drawString(font, truncate(info.errorMsg, 60), x + 10, y + 22, 0xFFAA4444);
        }
    }
    
    private void renderStoryDetails(GuiGraphics graphics, StoryInfo info) {
        int detailsY = guiTop + guiHeight - 110;
        
        if (!info.valid && !info.errorMsg.isEmpty()) {
            graphics.fill(guiLeft + 15, detailsY - 5, guiLeft + 15 + guiWidth - 190, detailsY + 25, 0xE01A0808);
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.stories.error_details"), guiLeft + 25, detailsY, 0xFFFF6666);
            graphics.drawString(font, info.errorMsg, guiLeft + 25, detailsY + 12, 0xFFAA4444);
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
        int listX = guiLeft + 15;
        int listY = guiTop + 45;
        int listWidth = guiWidth - 190;
        int listHeight = guiHeight - 115;
        
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
        int listHeight = guiHeight - 115;
        int visibleCount = (listHeight - 30) / STORY_ENTRY_HEIGHT;
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
        ClientPlayNetworking.send(new AdminStoryActionPayload(AdminStoryActionPayload.Action.RELOAD, ""));
        showMessage(Component.translatable("command.storyadventure.admin.stories.reloading").getString());
    }
    
    private void validateSelected() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            ClientPlayNetworking.send(new AdminStoryActionPayload(AdminStoryActionPayload.Action.VALIDATE, info.id));
            showMessage(Component.translatable("command.storyadventure.admin.stories.validating", info.id).getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }

    private void refreshStories() {
        ClientPlayNetworking.send(new AdminStoryActionPayload(AdminStoryActionPayload.Action.SYNC, ""));
    }
    
    private void viewStructure() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            // Open the node list screen for this story
            AdminNodeListScreen nodeListScreen = new AdminNodeListScreen(info.id, info.name);
            net.minecraft.client.Minecraft.getInstance().setScreen(nodeListScreen);
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void setSpawnLocation() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            sendCommand("storyadmin setlocation " + info.id + " spawn");
            showMessage(Component.translatable("command.storyadventure.admin.stories.spawn_set", info.id).getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void setReturnLocation() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            sendCommand("storyadmin setlocation " + info.id + " return");
            showMessage(Component.translatable("command.storyadventure.admin.stories.return_set", info.id).getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void teleportToScene() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            sendCommand("storyadmin tp " + info.id);
            showMessage(Component.translatable("command.storyadventure.admin.stories.tp_scene", info.id).getString());
            onClose();
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void openGraphEditor() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryInfo info = stories.get(selectedIndex);
            com.warmpixel.storyadventure.client.ui.admin.graph.StoryGraphScreen graphScreen = 
                new com.warmpixel.storyadventure.client.ui.admin.graph.StoryGraphScreen(info.id, info.name);
            net.minecraft.client.Minecraft.getInstance().setScreen(graphScreen);
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.stories.select_first").getString());
        }
    }
    
    private void createTemplate() {
        showMessage(Component.translatable("command.storyadventure.admin.stories.template_creating").getString());
        showMessage(Component.translatable("command.storyadventure.admin.stories.template_path", "config/storyadventure/stories/new_story.json").getString());
        showMessage(Component.translatable("command.storyadventure.admin.stories.template_hint").getString());
    }
    
    public record StoryInfo(String id, String name, int nodeCount, String version, boolean valid, String errorMsg) {}
}
