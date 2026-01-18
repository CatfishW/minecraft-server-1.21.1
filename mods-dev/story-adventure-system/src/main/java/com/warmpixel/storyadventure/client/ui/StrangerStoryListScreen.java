package com.warmpixel.storyadventure.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

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
    private static final int ENTRY_HEIGHT = 44; // Slightly tighter list
    private boolean storyListRequested = false;
    
    // Layout Constants
    private static final int HEADER_HEIGHT = 50;
    private static final int FOOTER_HEIGHT = 60;
    
    public StrangerStoryListScreen() {
        super(Component.translatable("gui.storyadventure.title.select_story"));
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

        if (!storyListRequested && stories.isEmpty()) {
            requestStoryListRefresh();
        }
        
        int buttonWidth = 160;
        int buttonHeight = 32;
        int bottomY = height - 44;
        
        // Right-aligned buttons in the footer area
        int rightPanelStart = (int)(width * 0.35) + 20;
        int rightPanelWidth = width - rightPanelStart - 20;
        
        // Center the buttons within the right panel area
        int buttonsTotalWidth = buttonWidth * 2 + 20;
        int buttonsStartX = rightPanelStart + (rightPanelWidth - buttonsTotalWidth) / 2;
        
        // Back button (left)
        addStrangerButton(buttonsStartX, bottomY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.button.back"), this::onClose);

        // Start button (right) - Only enabled if selected, but we render it always and check in callback
        StrangerButton startBtn = addStrangerButton(buttonsStartX + buttonWidth + 20, bottomY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.button.start_story"), this::startSelectedStory);
        startBtn.setGlowPulse(true);
    }

    private void requestStoryListRefresh() {
        storyListRequested = true;
        ClientPlayNetworking.send(
            new com.warmpixel.storyadventure.network.StoryActionPayload(
                com.warmpixel.storyadventure.network.StoryActionPayload.Action.REQUEST_STORY_LIST,
                ""
            )
        );
    }
    
    private static final ResourceLocation COVER_IMAGE = ResourceLocation.fromNamespaceAndPath("storyadventure", "textures/gui/story_cover_placeholder.png");

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int leftPanelWidth = (int)(width * 0.35);
        int listX = 20;
        int listY = HEADER_HEIGHT + 10;
        int listWidth = leftPanelWidth - 20;
        int listHeight = height - HEADER_HEIGHT - FOOTER_HEIGHT - 20;
        
        int visibleEntries = listHeight / ENTRY_HEIGHT;
        
        // --- Left Panel: Story List ---
        
        // Background for List Area
        graphics.fill(listX - 10, listY - 10, listX + listWidth + 10, listY + listHeight + 10, 0x40000000);
        renderRectOutline(graphics, listX - 10, listY - 10, listWidth + 20, listHeight + 20, COLOR_BORDER);

        if (stories.isEmpty()) {
             graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.loading"), listX + listWidth / 2, listY + listHeight / 2, COLOR_TEXT_DIM);
        } else {
            int visibleEnd = Math.min(scrollOffset + visibleEntries, stories.size());
            for (int i = scrollOffset; i < visibleEnd; i++) {
                int entryY = listY + (i - scrollOffset) * ENTRY_HEIGHT;
                renderStoryEntry(graphics, stories.get(i), listX, entryY, listWidth, i == selectedIndex, mouseX, mouseY);
            }
            
            // Scroll Indicators
            if (scrollOffset > 0) {
                 graphics.drawCenteredString(font, "▲", listX + listWidth / 2, listY - 8, COLOR_NEON_RED);
            }
            if (scrollOffset + visibleEntries < stories.size()) {
                 graphics.drawCenteredString(font, "▼", listX + listWidth / 2, listY + listHeight, COLOR_NEON_RED);
            }
        }
        
        // --- Right Panel: Details ---
        
        int rightPanelStart = leftPanelWidth + 20;
        int rightPanelWidth = width - rightPanelStart - 40;
        int contentY = HEADER_HEIGHT + 10;
        
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryEntry story = stories.get(selectedIndex);
            
            // 1. Thumbnail / Preview Image (16:9 Aspect Ratio)
            int thumbHeight = (int)(rightPanelWidth * 0.5625);
            // Limit height if screen is short
            int maxThumbH = (int)(height * 0.45);
            if (thumbHeight > maxThumbH) thumbHeight = maxThumbH;
            
            // Draw Thumbnail
            renderThumbnail(graphics, story, rightPanelStart, contentY, rightPanelWidth, thumbHeight);
            
            int textY = contentY + thumbHeight + 15;
            
            // 2. Title
            graphics.pose().pushPose();
            graphics.pose().translate(rightPanelStart, textY, 0);
            graphics.pose().scale(1.3f, 1.3f, 1.0f);
            graphics.drawString(font, story.name, 0, 0, COLOR_NEON_RED);
            graphics.pose().popPose();
            textY += 20;
            
            // 3. Stats Row
            String stats = String.format("👥 %d-%d Players   ⏱ ~%d Mins", story.minPlayers, story.maxPlayers, story.estimatedMinutes);
            graphics.drawString(font, stats, rightPanelStart, textY, COLOR_NEON_PINK);
            textY += 15;
            
            // Divider
            graphics.fill(rightPanelStart, textY, rightPanelStart + rightPanelWidth, textY + 1, COLOR_BORDER);
            textY += 10;
            
            // 4. Description (Scrollable-ish, or just clamp)
            String desc = story.description;
            List<net.minecraft.util.FormattedCharSequence> wrappedDesc = font.split(Component.literal(desc), rightPanelWidth);
            for (net.minecraft.util.FormattedCharSequence line : wrappedDesc) {
                // Stop if getting too close to buttons
                if (textY > height - FOOTER_HEIGHT - 10) break; 
                graphics.drawString(font, line, rightPanelStart, textY, COLOR_TEXT_BODY);
                textY += 11;
            }
            
        } else {
            // Empty State
            graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.select_prompt"), rightPanelStart + rightPanelWidth / 2, height / 2, COLOR_TEXT_DIM);
        }
    }
    
    private void renderStoryEntry(GuiGraphics graphics, StoryEntry story, int x, int y, int width, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
        
        // Background
        int bgColor = selected ? 0xFF1B232C : (hovered ? 0xFF151B22 : 0x00000000); // Transparent if not interacting
        graphics.fill(x, y, x + width, y + ENTRY_HEIGHT - 4, bgColor);
        
        // Active Indicator (Left Bar)
        if (selected) {
            graphics.fill(x, y, x + 3, y + ENTRY_HEIGHT - 4, COLOR_NEON_RED);
             // Glow effect gradient
             graphics.fillGradient(x + 3, y, x + width, y + ENTRY_HEIGHT - 4, 0x203BB6A6, 0x003BB6A6);
        } else if (hovered) {
             graphics.fill(x, y, x + 2, y + ENTRY_HEIGHT - 4, COLOR_BORDER);
        }
        
        // Title
        int textColor = selected ? COLOR_NEON_RED : (hovered ? 0xFFFFFFFF : COLOR_TEXT_BODY);
        graphics.drawString(font, story.name, x + 10, y + 8, textColor);
        
        // Mini Info
        String meta = String.format("%d-%d %s", story.minPlayers, story.maxPlayers, Component.translatable("gui.storyadventure.label.people").getString());
        graphics.drawString(font, meta, x + 10, y + 22, COLOR_TEXT_DIM);
    }
    
    // ... existing helpers ...

    private void renderThumbnail(GuiGraphics graphics, StoryEntry story, int x, int y, int w, int h) {
        // Draw the static image
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, COVER_IMAGE);
        graphics.blit(COVER_IMAGE, x, y, w, h, 0, 0, w, h, w, h);
        
        // Inner border
        renderRectOutline(graphics, x, y, w, h, COLOR_BORDER);
        
        // "Scanner" line animation (optional, kept for cool factor)
        float scanPhase = (System.currentTimeMillis() % 3000) / 3000.0f;
        int scanY = y + (int)(h * scanPhase);
        graphics.fill(x, scanY, x + w, scanY + 1, 0x403BB6A6);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int leftPanelWidth = (int)(width * 0.35);
        int listX = 20;
        int listY = HEADER_HEIGHT + 10;
        int listWidth = leftPanelWidth - 20;
        int listHeight = height - HEADER_HEIGHT - FOOTER_HEIGHT - 20;

        if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= listY && mouseY < listY + listHeight) {
             int relY = (int)mouseY - listY;
             int clickedIndex = scrollOffset + relY / ENTRY_HEIGHT;
             if (clickedIndex >= 0 && clickedIndex < stories.size()) {
                 selectedIndex = clickedIndex;
                 return true;
             }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        int listHeight = height - HEADER_HEIGHT - FOOTER_HEIGHT - 20;
        int visibleEntries = listHeight / ENTRY_HEIGHT;
        
        if (vAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (vAmount < 0 && scrollOffset + visibleEntries < stories.size()) {
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
        }
    }
    
    public record StoryEntry(String id, String name, String description, int minPlayers, int maxPlayers, int estimatedMinutes) {}
}
