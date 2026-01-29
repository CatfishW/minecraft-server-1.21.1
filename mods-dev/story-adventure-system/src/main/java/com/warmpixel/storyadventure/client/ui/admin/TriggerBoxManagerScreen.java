package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import com.warmpixel.storyadventure.core.waypoint.TriggerBox;
import com.warmpixel.storyadventure.item.AdminWandItem;
import com.warmpixel.storyadventure.network.AdminTriggerActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin UI for managing trigger boxes.
 * Shows list of boxes, allows editing properties and actions.
 */
public class TriggerBoxManagerScreen extends StrangerScreen {
    
    private static final int LIST_WIDTH = 220;
    private static final int ENTRY_HEIGHT = 24;
    
    private List<TriggerBoxEntry> boxes = new ArrayList<>();
    private int scrollOffset = 0;
    private TriggerBoxEntry selectedBox = null;
    
    // Edit fields
    private EditBox labelField;
    private EditBox linkedNodeField;
    private EditBox minXField, minYField, minZField;
    private EditBox maxXField, maxYField, maxZField;
    
    public TriggerBoxManagerScreen() {
        super(Component.translatable("gui.storyadventure.admin.triggers.title"));
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
        
        int rightPanelX = guiLeft + LIST_WIDTH + 30;
        int fieldWidth = 150;
        int fieldHeight = 20;
        int y = guiTop + 60;
        
        // Label field
        labelField = new EditBox(font, rightPanelX + 80, y, fieldWidth, fieldHeight, Component.translatable("gui.storyadventure.admin.triggers.label"));
        labelField.setMaxLength(64);
        addRenderableWidget(labelField);
        y += 28;
        
        // Linked node field  
        linkedNodeField = new EditBox(font, rightPanelX + 80, y, fieldWidth, fieldHeight, Component.translatable("gui.storyadventure.admin.triggers.linked_node"));
        linkedNodeField.setMaxLength(64);
        addRenderableWidget(linkedNodeField);
        y += 40;
        
        // Coordinate fields
        int coordWidth = 60;
        minXField = new EditBox(font, rightPanelX + 40, y, coordWidth, fieldHeight, Component.literal("X"));
        minYField = new EditBox(font, rightPanelX + 110, y, coordWidth, fieldHeight, Component.literal("Y"));
        minZField = new EditBox(font, rightPanelX + 180, y, coordWidth, fieldHeight, Component.literal("Z"));
        addRenderableWidget(minXField);
        addRenderableWidget(minYField);
        addRenderableWidget(minZField);
        y += 28;
        
        maxXField = new EditBox(font, rightPanelX + 40, y, coordWidth, fieldHeight, Component.literal("X"));
        maxYField = new EditBox(font, rightPanelX + 110, y, coordWidth, fieldHeight, Component.literal("Y"));
        maxZField = new EditBox(font, rightPanelX + 180, y, coordWidth, fieldHeight, Component.literal("Z"));
        addRenderableWidget(maxXField);
        addRenderableWidget(maxYField);
        addRenderableWidget(maxZField);
        y += 40;
        
        // Action buttons
        int btnWidth = 100;
        int btnHeight = 24;
        
        addStrangerButton(rightPanelX, y, btnWidth, btnHeight, 
            Component.translatable("gui.storyadventure.admin.triggers.save"), this::saveCurrentBox);
        addStrangerButton(rightPanelX + btnWidth + 10, y, btnWidth, btnHeight,
            Component.translatable("gui.storyadventure.admin.triggers.delete"), this::deleteCurrentBox);
        y += 35;
        
        addStrangerButton(rightPanelX, y, btnWidth, btnHeight,
            Component.translatable("gui.storyadventure.admin.triggers.teleport"), this::teleportToBox);
        addStrangerButton(rightPanelX + btnWidth + 10, y, btnWidth, btnHeight,
            Component.translatable("gui.storyadventure.admin.triggers.add_action"), this::addAction);
        
        // Bottom toolbar
        int toolbarY = guiTop + guiHeight - 40;
        addStrangerButton(guiLeft + 15, toolbarY, 100, 24, Component.translatable("gui.storyadventure.admin.triggers.refresh"), this::refreshList);
        addStrangerButton(guiLeft + 130, toolbarY, 120, 24, Component.translatable("gui.storyadventure.admin.triggers.new"), this::createNewBox);
        addStrangerButton(guiLeft + guiWidth - 110, toolbarY, 100, 24, Component.translatable("gui.storyadventure.admin.triggers.back"), this::goBack);
        
        // Load boxes
        refreshList();
        
        // Check for pending box from wand creation
        checkPendingBox();
    }
    
    private void checkPendingBox() {
        if (minecraft == null || minecraft.player == null) return;
        
        var pending = com.warmpixel.storyadventure.item.AdminWandItem.PendingTriggerBoxes.remove(minecraft.player.getUUID());
        if (pending != null) {
            // Add the pending box to our list
            TriggerBox box = new TriggerBox(pending.id(), new AABB(pending.corner1(), pending.corner2()));
            box.setLabel(Component.translatable("gui.storyadventure.admin.triggers.new_trigger").getString());
            boxes.add(new TriggerBoxEntry(box));
            selectBox(boxes.get(boxes.size() - 1));
            
            // Request server to save it
            requestSaveBox(box);
        }
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Left panel - box list
        int leftPanelX = guiLeft + 15;
        int panelY = guiTop + 45;
        int panelHeight = guiHeight - 95;
        
        graphics.fill(leftPanelX, panelY, leftPanelX + LIST_WIDTH, panelY + panelHeight, 0xE0080808);
        drawPanelBorder(graphics, leftPanelX, panelY, LIST_WIDTH, panelHeight);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.triggers.list_title", boxes.size()), leftPanelX + 5, panelY + 5, COLOR_NEON_RED);
        graphics.fill(leftPanelX + 5, panelY + 19, leftPanelX + LIST_WIDTH - 5, panelY + 20, COLOR_BORDER);
        
        // Render box entries
        int listY = panelY + 25;
        int visibleCount = (panelHeight - 30) / ENTRY_HEIGHT;
        int visibleEnd = Math.min(scrollOffset + visibleCount, boxes.size());
        
        for (int i = scrollOffset; i < visibleEnd; i++) {
            TriggerBoxEntry entry = boxes.get(i);
            boolean selected = entry == selectedBox;
            boolean hovered = mouseX >= leftPanelX + 5 && mouseX < leftPanelX + LIST_WIDTH - 5 && 
                              mouseY >= listY && mouseY < listY + ENTRY_HEIGHT - 2;
            
            int bgColor = selected ? 0xFF331111 : (hovered ? 0xFF1A0808 : 0x00000000);
            if (bgColor != 0) {
                graphics.fill(leftPanelX + 5, listY, leftPanelX + LIST_WIDTH - 5, listY + ENTRY_HEIGHT - 2, bgColor);
            }
            
            String label = entry.box.getLabel();
            if (label.length() > 20) label = label.substring(0, 18) + "...";
            graphics.drawString(font, label, leftPanelX + 10, listY + 4, selected ? COLOR_NEON_RED : COLOR_TEXT_BODY);
            graphics.drawString(font, entry.box.getId(), leftPanelX + 10, listY + 13, COLOR_TEXT_DIM);
            
            listY += ENTRY_HEIGHT;
        }
        
        // Right panel - editor
        int rightX = leftPanelX + LIST_WIDTH + 10;
        int rightWidth = guiLeft + guiWidth - rightX - 15;
        graphics.fill(rightX, panelY, rightX + rightWidth, panelY + panelHeight, 0xE0080808);
        drawPanelBorder(graphics, rightX, panelY, rightWidth, panelHeight);
        
        graphics.drawString(font, Component.translatable("gui.storyadventure.admin.triggers.properties"), rightX + 5, panelY + 5, COLOR_NEON_RED);
        graphics.fill(rightX + 5, panelY + 19, rightX + rightWidth - 5, panelY + 20, COLOR_BORDER);
        
        if (selectedBox != null) {
            int labelY = panelY + 20;
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.triggers.label"), rightX + 10, labelY, COLOR_TEXT_BODY);
            labelY += 28;
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.triggers.linked_node"), rightX + 10, labelY, COLOR_TEXT_BODY);
            labelY += 40;
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.triggers.min_pos"), rightX + 10, labelY, COLOR_TEXT_BODY);
            labelY += 28;
            graphics.drawString(font, Component.translatable("gui.storyadventure.admin.triggers.max_pos"), rightX + 10, labelY, COLOR_TEXT_BODY);
        } else {
            graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.triggers.empty_selection"), rightX + rightWidth / 2, panelY + panelHeight / 2, COLOR_TEXT_DIM);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check list clicks
        int leftPanelX = guiLeft + 15;
        int panelY = guiTop + 45;
        int listY = panelY + 25;
        int panelHeight = guiHeight - 95;
        
        if (mouseX >= leftPanelX + 5 && mouseX < leftPanelX + LIST_WIDTH - 5 && mouseY >= listY) {
            int visibleCount = (panelHeight - 30) / ENTRY_HEIGHT;
            int visibleEnd = Math.min(scrollOffset + visibleCount, boxes.size());
            
            for (int i = scrollOffset; i < visibleEnd; i++) {
                if (mouseY >= listY && mouseY < listY + ENTRY_HEIGHT - 2) {
                    selectBox(boxes.get(i));
                    return true;
                }
                listY += ENTRY_HEIGHT;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (mouseX < guiLeft + LIST_WIDTH + 15) {
            scrollOffset = Math.max(0, Math.min(boxes.size() - 5, scrollOffset - (int) vAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }
    
    private void selectBox(TriggerBoxEntry entry) {
        selectedBox = entry;
        if (entry != null) {
            TriggerBox box = entry.box;
            labelField.setValue(box.getLabel());
            linkedNodeField.setValue(box.getLinkedNodeId() != null ? box.getLinkedNodeId() : "");
            
            AABB bounds = box.getBounds();
            minXField.setValue(String.format("%.1f", bounds.minX));
            minYField.setValue(String.format("%.1f", bounds.minY));
            minZField.setValue(String.format("%.1f", bounds.minZ));
            maxXField.setValue(String.format("%.1f", bounds.maxX));
            maxYField.setValue(String.format("%.1f", bounds.maxY));
            maxZField.setValue(String.format("%.1f", bounds.maxZ));
        }
    }
    
    private void saveCurrentBox() {
        if (selectedBox == null) return;
        
        try {
            TriggerBox box = selectedBox.box;
            box.setLabel(labelField.getValue());
            box.setLinkedNodeId(linkedNodeField.getValue().isEmpty() ? null : linkedNodeField.getValue());
            
            double minX = Double.parseDouble(minXField.getValue());
            double minY = Double.parseDouble(minYField.getValue());
            double minZ = Double.parseDouble(minZField.getValue());
            double maxX = Double.parseDouble(maxXField.getValue());
            double maxY = Double.parseDouble(maxYField.getValue());
            double maxZ = Double.parseDouble(maxZField.getValue());
            box.setBounds(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
            
            requestSaveBox(box);
            showMessage(Component.translatable("command.storyadventure.admin.triggers.saved").getString());
        } catch (NumberFormatException e) {
            showMessage(Component.translatable("command.storyadventure.admin.triggers.error_pos").getString());
        }
    }
    
    private void deleteCurrentBox() {
        if (selectedBox == null) return;
        
        String id = selectedBox.box.getId();
        requestDeleteBox(id);
        boxes.remove(selectedBox);
        selectedBox = null;
        showMessage(Component.translatable("command.storyadventure.admin.triggers.deleted").getString());
    }
    
    private void teleportToBox() {
        if (selectedBox == null || minecraft == null || minecraft.player == null) return;
        
        AABB bounds = selectedBox.box.getBounds();
        double x = (bounds.minX + bounds.maxX) / 2;
        double y = bounds.minY;
        double z = (bounds.minZ + bounds.maxZ) / 2;
        
        // Send teleport command
        if (minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand(String.format("tp @s %.1f %.1f %.1f", x, y, z));
        }
    }
    
    private void addAction() {
        if (selectedBox == null) return;
        showMessage(Component.translatable("command.storyadventure.admin.triggers.action_editor_dev").getString());
    }
    
    private void refreshList() {
        // Request box list from server
        requestBoxList();
    }
    
    private void createNewBox() {
        showMessage(Component.translatable("command.storyadventure.admin.triggers.create_wand_hint").getString());
        goBack();
    }
    
    private void goBack() {
        Minecraft.getInstance().setScreen(new AdminDashboardScreen());
    }
    
    // Network request stubs - will be implemented
    private void requestBoxList() {
        if (minecraft != null) {
            ClientPlayNetworking.send(new AdminTriggerActionPayload(
                AdminTriggerActionPayload.Action.LIST, "", "", 0, 0, 0, 0, 0, 0, ""
            ));
        }
    }
    
    private void requestSaveBox(TriggerBox box) {
        if (minecraft != null) {
            var bounds = box.getBounds();
            ClientPlayNetworking.send(new AdminTriggerActionPayload(
                AdminTriggerActionPayload.Action.SAVE,
                box.getId(),
                box.getLabel(),
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                box.getLinkedNodeId() != null ? box.getLinkedNodeId() : ""
            ));
        }
    }
    
    private void requestDeleteBox(String id) {
        if (minecraft != null) {
            ClientPlayNetworking.send(new AdminTriggerActionPayload(
                AdminTriggerActionPayload.Action.DELETE, id, "", 0, 0, 0, 0, 0, 0, ""
            ));
        }
    }
    
    public void addBoxFromSync(String id, String label, double minX, double minY, double minZ, 
                                double maxX, double maxY, double maxZ) {
        TriggerBox box = new TriggerBox(id, new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        box.setLabel(label);
        boxes.add(new TriggerBoxEntry(box));
    }
    
    public void clearBoxes() {
        boxes.clear();
        selectedBox = null;
    }
    
    private void showMessage(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(message));
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
    
    private record TriggerBoxEntry(TriggerBox box) {}
}
