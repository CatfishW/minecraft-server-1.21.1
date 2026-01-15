package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import com.warmpixel.storyadventure.client.ui.admin.panels.ConfirmationPanel;
import com.warmpixel.storyadventure.client.ui.admin.panels.NodeSelectorPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.warmpixel.storyadventure.network.AdminInstanceActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

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
    
    // Modal panels
    private ConfirmationPanel terminatePanel;
    private ConfirmationPanel forceCompletePanel;
    private NodeSelectorPanel skipNodePanel;
    
    public AdminInstanceManagerScreen() {
        super(Component.translatable("gui.storyadventure.admin.instances.title"));
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
            Component.translatable("gui.storyadventure.admin.instances.pause"), this::pauseInstance);
        buttonY += buttonHeight + gap;
        
        // Resume button
        resumeButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.instances.resume"), this::resumeInstance);
        buttonY += buttonHeight + gap;
        
        // Skip node button
        skipNodeButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.instances.skip"), this::skipNode);
        buttonY += buttonHeight + gap;
        
        // Force complete button
        forceCompleteButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.instances.complete"), this::forceComplete);
        buttonY += buttonHeight + gap;
        
        // Terminate button
        terminateButton = addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.instances.terminate"), this::terminateInstance);
        buttonY += buttonHeight + gap * 3;
        
        // Refresh button
        addStrangerButton(sidebarX + 10, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.instances.refresh"), this::refreshList);
        
        // Close button at bottom
        addStrangerButton(width / 2 - 60, height - 45, 120, 28,
            Component.translatable("gui.storyadventure.admin.instances.close"), this::onClose);
        
        updateButtonStates();
        
        // Auto-refresh list on open
        refreshList();
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
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.col.story"), listX + 10, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.col.node"), listX + 150, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.col.status"), listX + 300, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.col.players"), listX + 380, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.col.time"), listX + 430, listY + 8, COLOR_NEON_RED);
        
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
            graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.instances.empty"), listX + listWidth / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        }
        
        // Sidebar - selected instance details
        renderSidebar(graphics);
        
        // Stats bar at bottom
        renderStatsBar(graphics);
        
        // Render modal panels on top
        if (terminatePanel != null && terminatePanel.isVisible()) {
            terminatePanel.render(graphics, mouseX, mouseY, font);
        }
        if (forceCompletePanel != null && forceCompletePanel.isVisible()) {
            forceCompletePanel.render(graphics, mouseX, mouseY, font);
        }
        if (skipNodePanel != null && skipNodePanel.isVisible()) {
            skipNodePanel.render(graphics, mouseX, mouseY, font);
        }
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
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.actions"), sidebarX + 10, sidebarY + 8, COLOR_NEON_RED);
        graphics.fill(sidebarX + 5, sidebarY + 22, sidebarX + SIDEBAR_WIDTH - 5, sidebarY + 23, COLOR_BORDER);
        
        // Selected instance info
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            int y = sidebarY + 30;
            
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.selected"), sidebarX + 10, y, COLOR_TEXT_DIM);
            y += 12;
            graphics.drawString(font, info.storyName, sidebarX + 10, y, COLOR_TEXT_BODY);
            y += 14;
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.id", info.id.toString().substring(0, 8)), sidebarX + 10, y, COLOR_TEXT_DIM);
        } else {
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.instances.none_selected"), sidebarX + 10, sidebarY + 35, COLOR_TEXT_DIM);
        }
    }
    
    private void renderStatsBar(GuiGraphics graphics) {
        int barY = height - 55;
        
        // Stats background
        graphics.fill(30, barY, width - 30, barY + 20, 0xC0080808);
        
        // Stats
        Component stats = Component.translatable("gui.storyadventure.admin.instances.stats_bar",
            instances.size(),
            instances.stream().mapToInt(i -> i.playerCount).sum(),
            Component.translatable("gui.storyadventure.admin.dashboard.status_ok").getString());
        
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
            case "RUNNING" -> Component.translatable("gui.storyadventure.admin.instances.status.running").getString();
            case "PAUSED" -> Component.translatable("gui.storyadventure.admin.instances.status.paused").getString();
            case "COMPLETED" -> Component.translatable("gui.storyadventure.admin.instances.status.completed").getString();
            case "FAILED" -> Component.translatable("gui.storyadventure.admin.instances.status.failed").getString();
            case "CREATED" -> Component.translatable("gui.storyadventure.admin.instances.status.created").getString();
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
        // Handle modal panels first
        if (terminatePanel != null && terminatePanel.isVisible()) {
            return terminatePanel.mouseClicked(mouseX, mouseY, button);
        }
        if (forceCompletePanel != null && forceCompletePanel.isVisible()) {
            return forceCompletePanel.mouseClicked(mouseX, mouseY, button);
        }
        if (skipNodePanel != null && skipNodePanel.isVisible()) {
            return skipNodePanel.mouseClicked(mouseX, mouseY, button);
        }
        
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
        // Handle modal panel scrolling
        if (skipNodePanel != null && skipNodePanel.isVisible()) {
            return skipNodePanel.mouseScrolled(mouseX, mouseY, vAmount);
        }
        
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
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle modal panel key events
        if (terminatePanel != null && terminatePanel.isVisible()) {
            return terminatePanel.keyPressed(keyCode, scanCode, modifiers);
        }
        if (forceCompletePanel != null && forceCompletePanel.isVisible()) {
            return forceCompletePanel.keyPressed(keyCode, scanCode, modifiers);
        }
        if (skipNodePanel != null && skipNodePanel.isVisible()) {
            return skipNodePanel.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        // Handle modal panel char input
        if (terminatePanel != null && terminatePanel.isVisible()) {
            return terminatePanel.charTyped(chr, modifiers);
        }
        if (forceCompletePanel != null && forceCompletePanel.isVisible()) {
            return forceCompletePanel.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
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
            ClientPlayNetworking.send(new AdminInstanceActionPayload(AdminInstanceActionPayload.Action.PAUSE, info.id));
            showMessage(Component.translatable("command.storyadventure.admin.instances.pausing", info.id.toString().substring(0, 8)).getString());
            info = new InstanceInfo(info.id, info.storyName, info.currentNode, "PAUSED", info.playerCount, info.elapsedMs);
            instances.set(selectedIndex, info);
        }
    }
    
    private void resumeInstance() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            ClientPlayNetworking.send(new AdminInstanceActionPayload(AdminInstanceActionPayload.Action.RESUME, info.id));
            showMessage(Component.translatable("command.storyadventure.admin.instances.resuming", info.id.toString().substring(0, 8)).getString());
            info = new InstanceInfo(info.id, info.storyName, info.currentNode, "RUNNING", info.playerCount, info.elapsedMs);
            instances.set(selectedIndex, info);
        }
    }
    
    private void skipNode() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            String shortId = info.id.toString().substring(0, 8);
            
            // Show node selector panel
            skipNodePanel = new NodeSelectorPanel(Component.translatable("gui.storyadventure.admin.instances.select_target_node").getString(), 
                nodeId -> {
                    sendCommand("storyadmin skip " + shortId + " " + nodeId);
                    showMessage(Component.translatable("command.storyadventure.admin.instances.skipping", nodeId).getString());
                },
                () -> {} // Cancel - do nothing
            );
            
            // Add placeholder nodes - in real implementation these come from server
            skipNodePanel.addNode("intro_cutscene", "CUTSCENE", "开场动画");
            skipNodePanel.addNode("meet_joyce", "DIALOGUE", "遇见乔伊斯");
            skipNodePanel.addNode("accept_mission", "CHECKPOINT", "接受任务");
            skipNodePanel.addNode("investigate_house", "TASK", "调查房屋");
            skipNodePanel.addNode("find_first_clue", "CUTSCENE", "发现线索");
            skipNodePanel.addNode("first_demogorgon_encounter", "COMBAT", "初次遭遇");
            skipNodePanel.addNode("lab_puzzle", "PUZZLE", "实验室密码");
            skipNodePanel.addNode("good_ending", "CUTSCENE", "好结局");
            
            skipNodePanel.show(width, height);
        }
    }
    
    private void forceComplete() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            String shortId = info.id.toString().substring(0, 8);
            
            // Show completion options panel
            forceCompletePanel = ConfirmationPanel.builder(Component.translatable("gui.storyadventure.admin.instances.confirm_complete_title").getString())
                .description(Component.translatable("gui.storyadventure.admin.instances.confirm_complete_desc").getString())
                .withInput(Component.translatable("gui.storyadventure.admin.instances.confirm_complete_input").getString())
                .onConfirm(result -> {
                    String outcome = result.isEmpty() ? "success" : result;
                    sendCommand("storyadmin complete " + shortId + " " + outcome);
                    showMessage(Component.translatable("command.storyadventure.admin.instances.forced_complete", shortId, outcome).getString());
                })
                .onCancel(() -> {})
                .build();
            
            forceCompletePanel.show(width, height, font);
        }
    }
    
    private void terminateInstance() {
        if (selectedIndex >= 0 && selectedIndex < instances.size()) {
            InstanceInfo info = instances.get(selectedIndex);
            String shortId = info.id.toString().substring(0, 8);
            
            // Show dangerous confirmation panel
            terminatePanel = ConfirmationPanel.builder(Component.translatable("gui.storyadventure.admin.instances.confirm_terminate_title").getString())
                .description(Component.translatable("gui.storyadventure.admin.instances.confirm_terminate_desc", shortId).getString())
                .dangerous()
                .withInput(Component.translatable("gui.storyadventure.admin.instances.confirm_terminate_input").getString())
                .onConfirm(reason -> {
                    ClientPlayNetworking.send(new AdminInstanceActionPayload(AdminInstanceActionPayload.Action.TERMINATE, info.id));
                    showMessage(Component.translatable("command.storyadventure.admin.instances.terminated", shortId).getString());
                    
                    // Remove from local list immediately for responsiveness
                    instances.remove(selectedIndex);
                    selectedIndex = -1;
                    updateButtonStates();
                    
                    // Trigger a full refresh to sync with server
                    // Delay slightly to let server process
                    net.minecraft.client.Minecraft.getInstance().tell(this::refreshList);
                })
                .onCancel(() -> {})
                .build();
            
            terminatePanel.show(width, height, font);
        }
    }
    
    private void refreshList() {
        // Clear current list first
        instances.clear();
        selectedIndex = -1;
        updateButtonStates();
        
        showMessage(Component.translatable("command.storyadventure.admin.instances.listing").getString());
        
        // Request direct sync from server
        ClientPlayNetworking.send(new AdminInstanceActionPayload(AdminInstanceActionPayload.Action.SYNC, null));
    }
    
    /**
     * Called by network handler when sync data arrives.
     */
    public void onSyncReceived() {
        updateButtonStates();
        if (instances.isEmpty()) {
            showMessage(Component.translatable("command.storyadventure.admin.instances.sync_none").getString());
        } else {
            showMessage(Component.translatable("command.storyadventure.admin.instances.synced", instances.size()).getString());
        }
    }
    
    public record InstanceInfo(UUID id, String storyName, String currentNode, 
                                String status, int playerCount, long elapsedMs) {}
}
