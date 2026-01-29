package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerButton;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin UI for editing individual node configuration.
 * Shows type-specific fields based on node type.
 */
public class AdminNodeEditorScreen extends StrangerScreen {
    
    private final String storyId;
    private final String nodeId;
    private final String nodeType;
    
    // Common fields
    private EditBox titleField;
    private EditBox descriptionField;
    
    // Type-specific fields (stored as key-value for simplicity)
    private final List<EditBox> dataFields = new ArrayList<>();
    private final List<String> dataFieldLabels = new ArrayList<>();
    
    // Scrolling for fields
    private int scrollOffset = 0;
    
    public AdminNodeEditorScreen(String storyId, String nodeId, String nodeType) {
        super(Component.translatable("gui.storyadventure.admin.nodes.edit_title", nodeId));
        this.storyId = storyId;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
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
        
        int fieldX = guiLeft + 60;
        int fieldWidth = guiWidth - 250;
        int y = guiTop + 70;
        
        // Initialize type-specific fields
        initTypeFields(fieldX, y, fieldWidth);
        
        // Action buttons on right side
        int buttonWidth = 140;
        int buttonHeight = 24;
        int rightX = guiLeft + guiWidth - 170;
        int buttonY = guiTop + 80;
        
        // Save button
        addStrangerButton(rightX, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.save"), this::saveChanges);
        buttonY += buttonHeight + 8;
        
        // Test trigger button
        addStrangerButton(rightX, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.test_trigger"), this::testTrigger);
        buttonY += buttonHeight + 8;
        
        // Reset to default
        addStrangerButton(rightX, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.reset"), this::resetToDefault);
        buttonY += buttonHeight + 20;
        
        // View JSON
        addStrangerButton(rightX, buttonY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.admin.nodes.view_json"), this::viewJson);
        
        // Navigation buttons
        addStrangerButton(guiLeft + 30, guiTop + guiHeight - 40, 100, 24,
            Component.translatable("gui.storyadventure.admin.nodes.back"), this::goBack);
        
        addStrangerButton(guiLeft + (guiWidth - 120) / 2, guiTop + guiHeight - 40, 120, 24,
            Component.translatable("gui.storyadventure.admin.nodes.cancel"), this::onClose);
    }
    
    private void initTypeFields(int x, int y, int fieldWidth) {
        int fieldHeight = 20;
        int gap = 30;
        
        switch (nodeType.toUpperCase()) {
            case "CUTSCENE" -> {
                addDataField("duration_ticks", Component.translatable("gui.storyadventure.admin.nodes.field.duration").getString(), "200", x, y, fieldWidth);
                y += gap;
                addDataField("title", Component.translatable("gui.storyadventure.admin.nodes.field.title").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("subtitle", Component.translatable("gui.storyadventure.admin.nodes.field.subtitle").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("message", Component.translatable("gui.storyadventure.admin.nodes.field.message").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("fade_in", Component.translatable("gui.storyadventure.admin.nodes.field.fade_in").getString(), "true", x, y, fieldWidth);
            }
            case "DIALOGUE" -> {
                addDataField("npc_template", Component.translatable("gui.storyadventure.admin.nodes.field.npc_template").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("npc_name", Component.translatable("gui.storyadventure.admin.nodes.field.npc_name").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("dialog_set", Component.translatable("gui.storyadventure.admin.nodes.field.dialog_set").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("lines", Component.translatable("gui.storyadventure.admin.nodes.field.lines").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("vote_required", Component.translatable("gui.storyadventure.admin.nodes.field.vote_required").getString(), "false", x, y, fieldWidth);
                y += gap;
                addDataField("vote_id", Component.translatable("gui.storyadventure.admin.nodes.field.vote_id").getString(), "", x, y, fieldWidth);
            }
            case "TASK" -> {
                addDataField("task_type", Component.translatable("gui.storyadventure.admin.nodes.field.task_type").getString(), "FETCH", x, y, fieldWidth);
                y += gap;
                addDataField("title", Component.translatable("gui.storyadventure.admin.nodes.field.title").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("description", Component.translatable("gui.storyadventure.admin.nodes.field.description").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("time_limit_seconds", Component.translatable("gui.storyadventure.admin.nodes.field.time_limit").getString(), "0", x, y, fieldWidth);
                y += gap;
                addDataField("stealth_required", Component.translatable("gui.storyadventure.admin.nodes.field.stealth").getString(), "false", x, y, fieldWidth);
            }
            case "PUZZLE" -> {
                addDataField("puzzle_type", Component.translatable("gui.storyadventure.admin.nodes.field.puzzle_type").getString(), "CODE_LOCK", x, y, fieldWidth);
                y += gap;
                addDataField("title", Component.translatable("gui.storyadventure.admin.nodes.field.title").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("description", Component.translatable("gui.storyadventure.admin.nodes.field.description").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("solution", Component.translatable("gui.storyadventure.admin.nodes.field.solution").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("max_attempts", Component.translatable("gui.storyadventure.admin.nodes.field.max_attempts").getString(), "5", x, y, fieldWidth);
                y += gap;
                addDataField("hints", Component.translatable("gui.storyadventure.admin.nodes.field.hints").getString(), "", x, y, fieldWidth);
            }
            case "COMBAT" -> {
                addDataField("combat_type", Component.translatable("gui.storyadventure.admin.nodes.field.combat_type").getString(), "BOSS", x, y, fieldWidth);
                y += gap;
                addDataField("title", Component.translatable("gui.storyadventure.admin.nodes.field.title").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("description", Component.translatable("gui.storyadventure.admin.nodes.field.description").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("enemies", Component.translatable("gui.storyadventure.admin.nodes.field.enemies").getString(), "", x, y, fieldWidth);
                y += gap;
                addDataField("escape_available", Component.translatable("gui.storyadventure.admin.nodes.field.escape_available").getString(), "false", x, y, fieldWidth);
                y += gap;
                addDataField("arena_radius", Component.translatable("gui.storyadventure.admin.nodes.field.arena_radius").getString(), "20", x, y, fieldWidth);
            }
            case "CHECKPOINT" -> {
                addDataField("rewind_anchor", Component.translatable("gui.storyadventure.admin.nodes.field.rewind_anchor").getString(), "true", x, y, fieldWidth);
                y += gap;
                addDataField("save_inventory", Component.translatable("gui.storyadventure.admin.nodes.field.save_inventory").getString(), "true", x, y, fieldWidth);
                y += gap;
                addDataField("message", Component.translatable("gui.storyadventure.admin.nodes.field.message").getString(), "", x, y, fieldWidth);
            }
            default -> {
                addDataField("data", Component.translatable("gui.storyadventure.admin.nodes.field.generic_data").getString(), "{}", x, y, fieldWidth);
            }
        }
    }
    
    private void addDataField(String key, String label, String defaultValue, int x, int y, int fieldWidth) {
        EditBox field = new EditBox(font, x, y, fieldWidth, 18, Component.literal(label));
        field.setMaxLength(500);
        field.setValue(defaultValue);
        field.setTextColor(0xFFCCCCCC);
        addRenderableWidget(field);
        dataFields.add(field);
        dataFieldLabels.add(label);
    }
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int headerY = guiTop + 35;
        
        // Type badge
        int typeColor = getTypeColor(nodeType);
        String typeDisplay = getTypeDisplayName(nodeType);
        int badgeWidth = font.width(typeDisplay) + 12;
        
        graphics.fill(guiLeft + 30, headerY, guiLeft + 30 + badgeWidth, headerY + 18, typeColor & 0x60FFFFFF);
        graphics.fill(guiLeft + 30, headerY, guiLeft + 32, headerY + 18, typeColor);
        graphics.drawString(font, typeDisplay, guiLeft + 36, headerY + 5, typeColor);
        
        // Node ID
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.node_label", nodeId), guiLeft + 40 + badgeWidth, headerY + 5, COLOR_TEXT_BODY);
        
        // Field labels
        int labelX = guiLeft + 30;
        int fieldY = guiTop + 70;
        int gap = 30;
        
        for (int i = 0; i < dataFieldLabels.size(); i++) {
            graphics.drawString(font, dataFieldLabels.get(i), labelX, fieldY + 4, COLOR_TEXT_DIM);
            fieldY += gap;
        }
        
        // Sidebar info panel
        int sidebarX = guiLeft + guiWidth - 170;
        int sidebarY = guiTop + 230;
        int sidebarWidth = 150;
        int sidebarHeight = 120;
        
        graphics.fill(sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + sidebarHeight, 0xE0080808);
        drawPanelBorder(graphics, sidebarX, sidebarY, sidebarWidth, sidebarHeight);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.info_title"), sidebarX + 10, sidebarY + 8, COLOR_NEON_RED);
        graphics.fill(sidebarX + 5, sidebarY + 20, sidebarX + sidebarWidth - 5, sidebarY + 21, COLOR_BORDER);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.nodes.info_type", typeDisplay), sidebarX + 10, sidebarY + 28, COLOR_TEXT_BODY);
    }
    
    private int getTypeColor(String type) {
        return switch (type.toUpperCase()) {
            case "DIALOGUE" -> 0xFF4488FF;
            case "TASK" -> 0xFF44FF44;
            case "PUZZLE" -> 0xFFFF8844;
            case "COMBAT" -> 0xFFFF4444;
            case "CUTSCENE" -> 0xFFCC44FF;
            case "CHECKPOINT" -> 0xFFFFCC44;
            default -> COLOR_TEXT_DIM;
        };
    }
    
    private String getTypeDisplayName(String type) {
        return switch (type.toUpperCase()) {
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
    
    private void saveChanges() {
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < dataFields.size(); i++) {
            if (i > 0) data.append(",");
            data.append(dataFieldLabels.get(i)).append("=").append(dataFields.get(i).getValue());
        }
        
        sendCommand("storyadmin updatenode " + storyId + " " + nodeId + " " + data);
        showMessage(Component.translatable("command.storyadventure.admin.nodes.saved").getString());
        showMessage(Component.translatable("command.storyadventure.admin.nodes.reload_hint").getString());
    }
    
    private void testTrigger() {
        sendCommand("storyadmin trigger " + storyId + " " + nodeId);
        showMessage(Component.translatable("command.storyadventure.admin.nodes.testing", nodeId).getString());
    }
    
    private void resetToDefault() {
        for (EditBox field : dataFields) {
            field.setValue("");
        }
        showMessage(Component.translatable("command.storyadventure.admin.nodes.reset_done").getString());
    }
    
    private void viewJson() {
        showMessage(Component.translatable("command.storyadventure.admin.nodes.json_title").getString());
        showMessage(Component.translatable("gui.storyadventure.admin.nodes.node_label", nodeId).getString());
        showMessage(Component.translatable("gui.storyadventure.admin.nodes.info_type", nodeType).getString());
        showMessage(Component.translatable("command.storyadventure.admin.nodes.json_hint").getString());
        showMessage("config/storyadventure/stories/" + storyId + ".json");
    }
    
    private void goBack() {
        Minecraft.getInstance().setScreen(new AdminNodeListScreen(storyId, storyId));
    }
}
