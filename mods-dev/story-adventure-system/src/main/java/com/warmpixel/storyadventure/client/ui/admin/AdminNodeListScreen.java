package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import com.warmpixel.storyadventure.core.graph.NodeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin UI for viewing and managing all nodes in a story.
 */
public class AdminNodeListScreen extends StrangerScreen {
    
    private static final int NODE_ENTRY_HEIGHT = 35;
    
    private final String storyId;
    private final String storyName;
    private final List<NodeInfo> nodes = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    
    // Filter
    private String filterType = "ALL";
    
    public AdminNodeListScreen(String storyId, String storyName) {
        super(Component.translatable("gui.storyadventure.admin.nodes.title", storyName));
        this.storyId = storyId;
        this.storyName = storyName;
    }
    
    public void addNode(String nodeId, String type, int edgeCount, String description) {
        nodes.add(new NodeInfo(nodeId, type, edgeCount, description));
    }
    
    public void clearNodes() {
        nodes.clear();
        selectedIndex = -1;
    }
    
    @Override
    protected int getWindowWidth() {
        return Math.min(width - 20, 750);
    }

    @Override
    protected int getWindowHeight() {
        return Math.min(height - 20, 480);
    }

    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 140;
        int buttonHeight = 24;
        int rightX = guiLeft + guiWidth - 170;
        int y = guiTop + 50;
        
        // Edit node button
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.edit"), this::editNode);
        y += buttonHeight + 8;
        
        // Test trigger button
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.trigger"), this::triggerNode);
        y += buttonHeight + 8;
        
        // View edges button
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.edges"), this::viewEdges);
        y += buttonHeight + 20;
        
        // Filter buttons
        addStrangerButton(rightX, y, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.filter_all"), () -> setFilter("ALL"));
        y += buttonHeight + 5;
        addStrangerButton(rightX, y, buttonWidth / 2 - 2, buttonHeight,
            Component.translatable("gui.storyadventure.node.type.dialogue"), () -> setFilter("DIALOGUE"));
        addStrangerButton(rightX + buttonWidth / 2 + 2, y, buttonWidth / 2 - 2, buttonHeight,
            Component.translatable("gui.storyadventure.node.type.task"), () -> setFilter("TASK"));
        y += buttonHeight + 5;
        addStrangerButton(rightX, y, buttonWidth / 2 - 2, buttonHeight,
            Component.translatable("gui.storyadventure.node.type.puzzle"), () -> setFilter("PUZZLE"));
        addStrangerButton(rightX + buttonWidth / 2 + 2, y, buttonWidth / 2 - 2, buttonHeight,
            Component.translatable("gui.storyadventure.node.type.combat"), () -> setFilter("COMBAT"));
        y += buttonHeight + 5;
        addStrangerButton(rightX, y, buttonWidth / 2 - 2, buttonHeight,
            Component.translatable("gui.storyadventure.node.type.cutscene"), () -> setFilter("CUTSCENE"));
        addStrangerButton(rightX + buttonWidth / 2 + 2, y, buttonWidth / 2 - 2, buttonHeight,
            Component.translatable("gui.storyadventure.node.type.checkpoint"), () -> setFilter("CHECKPOINT"));
        
        // Back button
        addStrangerButton(guiLeft + 30, guiTop + guiHeight - 40, 100, 24,
            Component.translatable("gui.storyadventure.admin.nodes.back"), this::goBack);
        
        // Close button
        addStrangerButton(guiLeft + (guiWidth - 120) / 2, guiTop + guiHeight - 40, 120, 24,
            Component.translatable("gui.storyadventure.admin.nodes.close"), this::onClose);
        
        // Load nodes
        loadNodes();
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Node list panel
        int listX = guiLeft + 30;
        int listY = guiTop + 35;
        int listWidth = guiWidth - 230;
        int listHeight = guiHeight - 85;
        
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xE0080808);
        drawPanelBorder(graphics, listX, listY, listWidth, listHeight);
        
        // Headers
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.col.id"), listX + 10, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.col.type"), listX + 180, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.col.edges"), listX + 270, listY + 8, COLOR_NEON_RED);
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.col.desc"), listX + 320, listY + 8, COLOR_NEON_RED);
        
        graphics.fill(listX + 5, listY + 22, listX + listWidth - 5, listY + 23, COLOR_BORDER);
        
        // Filtered nodes
        List<NodeInfo> filteredNodes = getFilteredNodes();
        
        // Node entries
        int y = listY + 28;
        int visibleCount = (listHeight - 30) / NODE_ENTRY_HEIGHT;
        int visibleEnd = Math.min(scrollOffset + visibleCount, filteredNodes.size());
        
        for (int i = scrollOffset; i < visibleEnd; i++) {
            renderNodeEntry(graphics, filteredNodes.get(i), listX + 5, y, listWidth - 10, 
                i == selectedIndex, mouseX, mouseY);
            y += NODE_ENTRY_HEIGHT;
        }
        
        if (filteredNodes.isEmpty()) {
            Component emptyMsg = filterType.equals("ALL") ? 
                Component.translatable("gui.storyadventure.admin.nodes.empty") : 
                Component.translatable("gui.storyadventure.admin.nodes.empty_filtered", getTypeDisplayName(filterType));
            graphics.drawCenteredString(font, emptyMsg, listX + listWidth / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        }
        
        // Stats bar
        graphics.fill(guiLeft + 30, guiTop + guiHeight - 65, guiLeft + guiWidth - 30, guiTop + guiHeight - 50, 0xC0080808);
        Component stats = Component.translatable("gui.storyadventure.admin.nodes.stats",
            storyId, nodes.size(), filteredNodes.size(), getTypeDisplayName(filterType));
        graphics.drawString(font, stats, guiLeft + 40, guiTop + guiHeight - 62, COLOR_TEXT_DIM);
    }
    
    private void renderNodeEntry(GuiGraphics graphics, NodeInfo info, 
                                  int x, int y, int width, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + NODE_ENTRY_HEIGHT - 2;
        
        int bgColor = selected ? 0xFF1A0808 : (hovered ? 0xFF100505 : 0xFF0A0A0A);
        graphics.fill(x, y, x + width, y + NODE_ENTRY_HEIGHT - 2, bgColor);
        
        // Type color indicator
        int typeColor = getTypeColor(info.type);
        graphics.fill(x, y, x + 3, y + NODE_ENTRY_HEIGHT - 2, typeColor);
        
        // Node ID
        graphics.drawString(font, truncate(info.nodeId, 24), x + 10, y + 10, COLOR_TEXT_BODY);
        
        // Type badge
        String typeDisplay = getTypeDisplayName(info.type);
        graphics.fill(x + 175, y + 6, x + 175 + font.width(typeDisplay) + 10, y + 20, typeColor & 0x40FFFFFF);
        graphics.drawString(font, typeDisplay, x + 180, y + 10, typeColor);
        
        // Edge count
        graphics.drawString(font, String.valueOf(info.edgeCount), x + 280, y + 10, COLOR_TEXT_DIM);
        
        // Description
        graphics.drawString(font, truncate(info.description, 30), x + 320, y + 10, COLOR_TEXT_DIM);
    }
    
    private List<NodeInfo> getFilteredNodes() {
        if (filterType.equals("ALL")) {
            return nodes;
        }
        return nodes.stream()
            .filter(n -> n.type.equalsIgnoreCase(filterType))
            .toList();
    }
    
    private void setFilter(String type) {
        this.filterType = type;
        this.selectedIndex = -1;
        this.scrollOffset = 0;
    }
    
    private int getTypeColor(String type) {
        return switch (type.toUpperCase()) {
            case "DIALOGUE" -> 0xFF4488FF;  // Blue
            case "TASK" -> 0xFF44FF44;      // Green
            case "PUZZLE" -> 0xFFFF8844;    // Orange
            case "COMBAT" -> 0xFFFF4444;    // Red
            case "CUTSCENE" -> 0xFFCC44FF;  // Purple
            case "CHECKPOINT" -> 0xFFFFCC44; // Yellow
            default -> COLOR_TEXT_DIM;
        };
    }
    
    private String getTypeDisplayName(String type) {
        return switch (type.toUpperCase()) {
            case "ALL" -> Component.translatable("gui.storyadventure.admin.nodes.filter.all").getString();
            case "DIALOGUE" -> Component.translatable("gui.storyadventure.node.type.dialogue").getString();
            case "TASK" -> Component.translatable("gui.storyadventure.node.type.task").getString();
            case "PUZZLE" -> Component.translatable("gui.storyadventure.node.type.puzzle").getString();
            case "COMBAT" -> Component.translatable("gui.storyadventure.node.type.combat").getString();
            case "CUTSCENE" -> Component.translatable("gui.storyadventure.node.type.cutscene").getString();
            case "CHECKPOINT" -> Component.translatable("gui.storyadventure.node.type.checkpoint").getString();
            default -> type;
        };
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
    
    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen - 2) + ".." : text;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = guiLeft + 30;
        int listY = guiTop + 35;
        int listWidth = guiWidth - 230;
        int listHeight = guiHeight - 85;
        
        if (mouseX >= listX && mouseX < listX + listWidth && 
            mouseY >= listY + 28 && mouseY < listY + listHeight) {
            List<NodeInfo> filtered = getFilteredNodes();
            int relY = (int)mouseY - listY - 28;
            int clickedIndex = scrollOffset + relY / NODE_ENTRY_HEIGHT;
            if (clickedIndex < filtered.size()) {
                selectedIndex = clickedIndex;
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        List<NodeInfo> filtered = getFilteredNodes();
        int visibleCount = (guiHeight - 85 - 30) / NODE_ENTRY_HEIGHT;
        if (vAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (vAmount < 0 && scrollOffset + visibleCount < filtered.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }
    
    private void sendCommand(String command) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }
    
    private void showMessage(String message) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
    
    private void showTranslatable(String key, Object... args) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.translatable(key, args));
        }
    }
    
    private void loadNodes() {
        // Request nodes from server
        sendCommand("storyadmin nodes " + storyId);
        
        // For now, load some placeholder data based on story
        // In real implementation, this would come from network sync
        clearNodes();
        
        // Placeholder nodes for demonstration
        addNode("intro_cutscene", "CUTSCENE", 1, "开场过场动画");
        addNode("meet_joyce", "DIALOGUE", 2, "遇见乔伊斯");
        addNode("accept_mission", "CHECKPOINT", 1, "接受任务存档点");
        addNode("investigate_house", "TASK", 2, "调查房屋任务");
        addNode("find_first_clue", "CUTSCENE", 1, "发现线索");
        addNode("first_demogorgon_encounter", "COMBAT", 3, "第一次遭遇战斗");
        addNode("lab_puzzle", "PUZZLE", 2, "实验室密码谜题");
        addNode("gather_supplies", "TASK", 1, "收集物资");
        addNode("final_battle_prep", "CHECKPOINT", 1, "最终战斗准备");
        addNode("final_battle_vote", "DIALOGUE", 2, "投票选择策略");
        addNode("final_battle_direct", "COMBAT", 2, "正面突击战斗");
        addNode("good_ending", "CUTSCENE", 0, "好结局");
        addNode("bad_ending_defeated", "CUTSCENE", 0, "坏结局-失败");
    }
    
    private void editNode() {
        List<NodeInfo> filtered = getFilteredNodes();
        if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
            NodeInfo info = filtered.get(selectedIndex);
            Minecraft.getInstance().setScreen(new AdminNodeEditorScreen(storyId, info.nodeId, info.type));
        } else {
            showTranslatable("command.storyadventure.admin.stories.select_first");
        }
    }
    
    private void triggerNode() {
        List<NodeInfo> filtered = getFilteredNodes();
        if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
            NodeInfo info = filtered.get(selectedIndex);
            sendCommand("storyadmin trigger " + storyId + " " + info.nodeId);
            showTranslatable("command.storyadventure.admin.nodes.triggering", info.nodeId);
        } else {
            showTranslatable("command.storyadventure.admin.stories.select_first");
        }
    }
    
    private void viewEdges() {
        List<NodeInfo> filtered = getFilteredNodes();
        if (selectedIndex >= 0 && selectedIndex < filtered.size()) {
            NodeInfo info = filtered.get(selectedIndex);
            showTranslatable("gui.storyadventure.admin.nodes.edges_title", info.nodeId);
            showTranslatable("gui.storyadventure.admin.nodes.edges_count", info.edgeCount);
            showTranslatable("gui.storyadventure.admin.nodes.edges_hint");
        } else {
            showTranslatable("command.storyadventure.admin.stories.select_first");
        }
    }
    
    private void goBack() {
        AdminStoryManagerScreen screen = new AdminStoryManagerScreen();
        screen.addStory(storyId, storyName, nodes.size(), "1.0.0", true, "");
        Minecraft.getInstance().setScreen(screen);
    }
    
    public record NodeInfo(String nodeId, String type, int edgeCount, String description) {}
}
