package com.warmpixel.storyadventure.client.ui.admin.panels;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel for selecting a node to skip to in an instance.
 * Shows a scrollable list of available nodes.
 */
public class NodeSelectorPanel {
    
    private static final int COLOR_NEON_RED = 0xFFE50914;
    private static final int COLOR_TEXT_BODY = 0xFFCCCCCC;
    private static final int COLOR_TEXT_DIM = 0xFF666666;
    private static final int COLOR_BORDER = 0xFF330011;
    private static final int ENTRY_HEIGHT = 28;
    
    private final String title;
    private final List<NodeEntry> nodes = new ArrayList<>();
    private final Consumer<String> onSelect;
    private final Runnable onCancel;
    
    private int x, y, width, height;
    private int scrollOffset = 0;
    private int selectedIndex = -1;
    private boolean visible = false;
    
    public NodeSelectorPanel(String title, Consumer<String> onSelect, Runnable onCancel) {
        this.title = title;
        this.onSelect = onSelect;
        this.onCancel = onCancel;
    }
    
    public void addNode(String nodeId, String nodeType, String description) {
        nodes.add(new NodeEntry(nodeId, nodeType, description));
    }
    
    public void clearNodes() {
        nodes.clear();
        selectedIndex = -1;
    }
    
    public void show(int screenWidth, int screenHeight) {
        this.width = 400;
        this.height = 350;
        this.x = (screenWidth - width) / 2;
        this.y = (screenHeight - height) / 2;
        this.visible = true;
        this.scrollOffset = 0;
        this.selectedIndex = -1;
    }
    
    public void hide() {
        this.visible = false;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void render(GuiGraphics graphics, int mouseX, int mouseY, Font font) {
        if (!visible) return;
        
        // Dim background
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xC0000000);
        
        // Panel background
        graphics.fill(x, y, x + width, y + height, 0xF0101010);
        
        // Border
        graphics.fill(x, y, x + width, y + 2, COLOR_NEON_RED);
        graphics.fill(x, y + height - 2, x + width, y + height, COLOR_NEON_RED);
        graphics.fill(x, y, x + 2, y + height, COLOR_NEON_RED);
        graphics.fill(x + width - 2, y, x + width, y + height, COLOR_NEON_RED);
        
        // Corner accents
        int cs = 10;
        graphics.fill(x, y, x + cs, y + 3, COLOR_NEON_RED);
        graphics.fill(x, y, x + 3, y + cs, COLOR_NEON_RED);
        graphics.fill(x + width - cs, y, x + width, y + 3, COLOR_NEON_RED);
        graphics.fill(x + width - 3, y, x + width, y + cs, COLOR_NEON_RED);
        
        // Title
        graphics.drawCenteredString(font, title, x + width / 2, y + 12, COLOR_NEON_RED);
        
        // Divider
        graphics.fill(x + 10, y + 28, x + width - 10, y + 29, COLOR_BORDER);
        
        // List area
        int listY = y + 35;
        int listHeight = height - 85;
        int visibleCount = listHeight / ENTRY_HEIGHT;
        int visibleEnd = Math.min(scrollOffset + visibleCount, nodes.size());
        
        // List background
        graphics.fill(x + 10, listY, x + width - 10, listY + listHeight, 0xFF080808);
        
        // Entries
        int entryY = listY;
        for (int i = scrollOffset; i < visibleEnd; i++) {
            NodeEntry entry = nodes.get(i);
            boolean isSelected = i == selectedIndex;
            boolean isHovered = mouseX >= x + 10 && mouseX < x + width - 10 &&
                               mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT;
            
            int bgColor = isSelected ? 0xFF1A0808 : (isHovered ? 0xFF120606 : 0xFF080808);
            graphics.fill(x + 10, entryY, x + width - 10, entryY + ENTRY_HEIGHT - 1, bgColor);
            
            // Type color bar
            int typeColor = getTypeColor(entry.nodeType);
            graphics.fill(x + 10, entryY, x + 14, entryY + ENTRY_HEIGHT - 1, typeColor);
            
            // Node ID
            graphics.drawString(font, entry.nodeId, x + 20, entryY + 5, COLOR_TEXT_BODY);
            
            // Type badge
            String typeDisplay = getTypeDisplayName(entry.nodeType);
            int badgeX = x + 200;
            graphics.fill(badgeX, entryY + 3, badgeX + font.width(typeDisplay) + 8, entryY + 16, typeColor & 0x40FFFFFF);
            graphics.drawString(font, typeDisplay, badgeX + 4, entryY + 5, typeColor);
            
            // Description
            String desc = entry.description.length() > 20 ? entry.description.substring(0, 18) + ".." : entry.description;
            graphics.drawString(font, desc, x + 280, entryY + 5, COLOR_TEXT_DIM);
            
            entryY += ENTRY_HEIGHT;
        }
        
        // Empty state
        if (nodes.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.panels.no_nodes"), x + width / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        }
        
        // Scroll indicator
        if (nodes.size() > visibleCount) {
            int scrollBarHeight = listHeight * visibleCount / nodes.size();
            int scrollBarY = listY + (int)((float) scrollOffset / nodes.size() * listHeight);
            graphics.fill(x + width - 14, listY, x + width - 10, listY + listHeight, 0xFF222222);
            graphics.fill(x + width - 14, scrollBarY, x + width - 10, scrollBarY + scrollBarHeight, COLOR_NEON_RED);
        }
        
        // Buttons
        int buttonY = y + height - 40;
        int buttonWidth = 100;
        int buttonHeight = 28;
        int gap = 20;
        
        // Select button
        int selectX = x + width / 2 - buttonWidth - gap / 2;
        boolean selectHovered = mouseX >= selectX && mouseX < selectX + buttonWidth &&
                                 mouseY >= buttonY && mouseY < buttonY + buttonHeight;
        boolean selectEnabled = selectedIndex >= 0;
        int selectBg = !selectEnabled ? 0xFF1A1A1A : (selectHovered ? 0xFF114411 : 0xFF222222);
        graphics.fill(selectX, buttonY, selectX + buttonWidth, buttonY + buttonHeight, selectBg);
        graphics.fill(selectX, buttonY, selectX + buttonWidth, buttonY + 1, selectEnabled ? 0xFF44FF44 : COLOR_TEXT_DIM);
        graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.panels.select").getString(), selectX + buttonWidth / 2, buttonY + 9, 
            selectEnabled ? 0xFF44FF44 : COLOR_TEXT_DIM);
        
        // Cancel button
        int cancelX = x + width / 2 + gap / 2;
        boolean cancelHovered = mouseX >= cancelX && mouseX < cancelX + buttonWidth &&
                                 mouseY >= buttonY && mouseY < buttonY + buttonHeight;
        int cancelBg = cancelHovered ? 0xFF333333 : 0xFF222222;
        graphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + buttonHeight, cancelBg);
        graphics.fill(cancelX, buttonY, cancelX + buttonWidth, buttonY + 1, COLOR_TEXT_DIM);
        graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.admin.panels.cancel").getString(), cancelX + buttonWidth / 2, buttonY + 9, COLOR_TEXT_BODY);
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        
        int listY = y + 35;
        int listHeight = height - 85;
        
        // Check list clicks
        if (mouseX >= x + 10 && mouseX < x + width - 10 && mouseY >= listY && mouseY < listY + listHeight) {
            int relY = (int) mouseY - listY;
            int clickedIndex = scrollOffset + relY / ENTRY_HEIGHT;
            if (clickedIndex < nodes.size()) {
                selectedIndex = clickedIndex;
                return true;
            }
        }
        
        int buttonY = y + height - 40;
        int buttonWidth = 100;
        int buttonHeight = 28;
        int gap = 20;
        
        // Select button
        int selectX = x + width / 2 - buttonWidth - gap / 2;
        if (selectedIndex >= 0 && mouseX >= selectX && mouseX < selectX + buttonWidth &&
            mouseY >= buttonY && mouseY < buttonY + buttonHeight) {
            onSelect.accept(nodes.get(selectedIndex).nodeId);
            hide();
            return true;
        }
        
        // Cancel button
        int cancelX = x + width / 2 + gap / 2;
        if (mouseX >= cancelX && mouseX < cancelX + buttonWidth &&
            mouseY >= buttonY && mouseY < buttonY + buttonHeight) {
            onCancel.run();
            hide();
            return true;
        }
        
        // Click outside to cancel
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            onCancel.run();
            hide();
            return true;
        }
        
        return true;
    }
    
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        
        int listHeight = height - 85;
        int visibleCount = listHeight / ENTRY_HEIGHT;
        
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (delta < 0 && scrollOffset + visibleCount < nodes.size()) {
            scrollOffset++;
            return true;
        }
        return false;
    }
    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        
        // Escape to cancel
        if (keyCode == 256) {
            onCancel.run();
            hide();
            return true;
        }
        
        // Enter to confirm
        if (keyCode == 257 && selectedIndex >= 0) {
            onSelect.accept(nodes.get(selectedIndex).nodeId);
            hide();
            return true;
        }
        
        // Arrow keys for navigation
        if (keyCode == 264 && selectedIndex < nodes.size() - 1) { // Down
            selectedIndex++;
            int listHeight = height - 85;
            int visibleCount = listHeight / ENTRY_HEIGHT;
            if (selectedIndex >= scrollOffset + visibleCount) {
                scrollOffset++;
            }
            return true;
        }
        if (keyCode == 265 && selectedIndex > 0) { // Up
            selectedIndex--;
            if (selectedIndex < scrollOffset) {
                scrollOffset--;
            }
            return true;
        }
        
        return false;
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
    
    public record NodeEntry(String nodeId, String nodeType, String description) {}
}
