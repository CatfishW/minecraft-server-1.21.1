package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Stranger Things themed story selection screen.
 * Lists available stories with neon styling.
 */
public class StrangerStoryListScreen extends StrangerScreen {
    
    private final List<StoryEntry> stories = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private static final int ENTRY_HEIGHT = 50;
    private static final int VISIBLE_ENTRIES = 5;
    
    public StrangerStoryListScreen() {
        super(Component.literal("选择故事"));
    }
    
    public void addStory(String id, String name, String description, int minPlayers, int maxPlayers, int estimatedMinutes) {
        stories.add(new StoryEntry(id, name, description, minPlayers, maxPlayers, estimatedMinutes));
    }

    public void clearStories() {
        stories.clear();
        selectedIndex = -1;
        scrollOffset = 0;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 150;
        int buttonHeight = 28;
        int bottomY = height - 50;
        
        // Start button
        addStrangerButton(width / 2 - buttonWidth - 10, bottomY, buttonWidth, buttonHeight,
            Component.literal("开始故事"), this::startSelectedStory);
        
        // Back button  
        addStrangerButton(width / 2 + 10, bottomY, buttonWidth, buttonHeight,
            Component.literal("返回"), this::onClose);
    }
    
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int listX = 50;
        int listY = 60;
        int listWidth = width - 100;
        int listHeight = ENTRY_HEIGHT * VISIBLE_ENTRIES;
        
        // Draw list background
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xE0101216);
        
        // Draw list border
        drawListBorder(graphics, listX, listY, listWidth, listHeight);
        
        // Draw entries
        if (stories.isEmpty()) {
             graphics.drawCenteredString(font, "加载中...", width / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        } else {
            int visibleEnd = Math.min(scrollOffset + VISIBLE_ENTRIES, stories.size());
            for (int i = scrollOffset; i < visibleEnd; i++) {
                int entryY = listY + (i - scrollOffset) * ENTRY_HEIGHT;
                renderStoryEntry(graphics, stories.get(i), listX + 4, entryY, listWidth - 8, i == selectedIndex, mouseX, mouseY);
            }
        }
        
        // Draw scroll indicators if needed
        if (scrollOffset > 0) {
            graphics.drawString(font, "▲", listX + listWidth / 2 - 4, listY - 12, COLOR_NEON_RED);
        }
        if (scrollOffset + VISIBLE_ENTRIES < stories.size()) {
            graphics.drawString(font, "▼", listX + listWidth / 2 - 4, listY + listHeight + 4, COLOR_NEON_RED);
        }
    }
    
    private void renderStoryEntry(GuiGraphics graphics, StoryEntry story, int x, int y, int width, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
        
        // Background
        int bgColor = selected ? 0xFF1B232C : (hovered ? 0xFF151B22 : 0xFF0E1218);
        graphics.fill(x, y, x + width, y + ENTRY_HEIGHT - 2, bgColor);
        
        // Border
        int borderColor = selected ? COLOR_NEON_RED : COLOR_BORDER;
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + ENTRY_HEIGHT - 3, x + width, y + ENTRY_HEIGHT - 2, borderColor);
        graphics.fill(x, y, x + 1, y + ENTRY_HEIGHT - 2, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + ENTRY_HEIGHT - 2, borderColor);
        
        // Title
        graphics.drawString(font, story.name, x + 8, y + 6, selected ? COLOR_NEON_RED : COLOR_TEXT_BODY);
        
        // Description (truncated)
        String desc = story.description;
        if (font.width(desc) > width - 16) {
            while (font.width(desc + "...") > width - 16 && desc.length() > 0) {
                desc = desc.substring(0, desc.length() - 1);
            }
            desc += "...";
        }
        graphics.drawString(font, desc, x + 8, y + 20, COLOR_TEXT_DIM);
        
        // Player count and duration
        String info = String.format("%d-%d人 | 约%d分钟", story.minPlayers, story.maxPlayers, story.estimatedMinutes);
        graphics.drawString(font, info, x + 8, y + 34, COLOR_TEXT_DIM);
    }
    
    private void drawListBorder(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check for story selection
        int listX = 50;
        int listY = 60;
        int listWidth = width - 100;
        
        if (mouseX >= listX && mouseX < listX + listWidth) {
            int relY = (int)mouseY - listY;
            if (relY >= 0 && relY < ENTRY_HEIGHT * VISIBLE_ENTRIES) {
                int clickedIndex = scrollOffset + relY / ENTRY_HEIGHT;
                if (clickedIndex < stories.size()) {
                    selectedIndex = clickedIndex;
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        if (vAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (vAmount < 0 && scrollOffset + VISIBLE_ENTRIES < stories.size()) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }
    
    private void startSelectedStory() {
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryEntry story = stories.get(selectedIndex);
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.warmpixel.storyadventure.network.StoryActionPayload(
                    com.warmpixel.storyadventure.network.StoryActionPayload.Action.SELECT_STORY,
                    story.id
                )
            );
            // Don't close immediately, wait for server to switch us to Lobby
        }
    }
    
    public record StoryEntry(String id, String name, String description, int minPlayers, int maxPlayers, int estimatedMinutes) {}
}
