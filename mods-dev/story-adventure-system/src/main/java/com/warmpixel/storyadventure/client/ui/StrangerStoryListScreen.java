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
    private boolean storyListRequested = false;
    
    @Override
    protected int getWindowWidth() {
        return Math.min(width - 20, 720);
    }

    @Override
    protected int getWindowHeight() {
        return Math.min(height - 20, 460);
    }
    
    // Dynamic layout methods - these calculate based on current window dimensions
    private int getEntryHeight() {
        int baseHeight = (int)(guiHeight * 0.08);
        return Math.max(30, Math.min(48, baseHeight));
    }
    
    private int getHeaderHeight() {
        return Math.max(30, Math.min(50, (int)(guiHeight * 0.07)));
    }
    
    private int getFooterHeight() {
        return Math.max(40, Math.min(65, (int)(guiHeight * 0.09)));
    }
    
    private int getMargin() {
        return Math.max(10, Math.min(20, (int)(guiWidth * 0.02)));
    }
    
    public StrangerStoryListScreen() {
        super(Component.translatable("gui.storyadventure.title.select_story"));
    }
    
    public void addStory(String id, String name, String description, int minPlayers, int maxPlayers, int estimatedMinutes, String cover) {
        stories.add(new StoryEntry(id, name, description, minPlayers, maxPlayers, estimatedMinutes, cover));
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
        
        int footerHeight = getFooterHeight();
        int margin = getMargin();
        
        int buttonWidth = Math.max(80, Math.min(130, (int)(guiWidth * 0.18)));
        int buttonHeight = Math.max(24, Math.min(32, (int)(guiHeight * 0.06)));
        
        int buttonsY = guiTop + guiHeight - footerHeight / 2 - buttonHeight / 2 + 5;
        
        int leftPanelWidth = (int)(guiWidth * 0.38);
        int rightPanelStart = guiLeft + leftPanelWidth + margin;
        int rightPanelWidth = guiWidth - leftPanelWidth - margin * 2;
        
        int buttonGap = Math.max(6, (int)(guiWidth * 0.012));
        int buttonsTotalWidth = buttonWidth * 2 + buttonGap;
        int buttonsStartX = rightPanelStart + (rightPanelWidth - buttonsTotalWidth) / 2;
        
        // Back button
        addStrangerButton(buttonsStartX, buttonsY, buttonWidth, buttonHeight,
            Component.translatable("gui.storyadventure.button.back"), this::onClose);

        // Start button
        StrangerButton startBtn = addStrangerButton(buttonsStartX + buttonWidth + buttonGap, buttonsY, buttonWidth, buttonHeight,
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
    protected void renderTitle(GuiGraphics graphics, int mouseX, int mouseY) {
        // Don't render default title - we draw our own in the panel header
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int ENTRY_HEIGHT = getEntryHeight();
        int HEADER_HEIGHT = getHeaderHeight();
        int FOOTER_HEIGHT = getFooterHeight();
        int margin = getMargin();
        
        // === Header area with title ===
        graphics.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + HEADER_HEIGHT, 0xFF0C0E12);
        graphics.fill(guiLeft, guiTop + HEADER_HEIGHT - 1, guiLeft + guiWidth, guiTop + HEADER_HEIGHT, COLOR_BORDER);
        
        // Title in header
        String titleText = Component.translatable("gui.storyadventure.title.select_story").getString();
        graphics.drawCenteredString(font, titleText, guiLeft + guiWidth / 2, guiTop + (HEADER_HEIGHT - 8) / 2, COLOR_TEXT_TITLE);
        
        // === Left Panel: Story List ===
        int leftPanelWidth = (int)(guiWidth * 0.38);
        int listX = guiLeft + margin;
        int listY = guiTop + HEADER_HEIGHT + 8;
        int listWidth = leftPanelWidth - margin;
        int listHeight = guiHeight - HEADER_HEIGHT - FOOTER_HEIGHT - 16;
        
        int visibleEntries = listHeight / ENTRY_HEIGHT;
        
        // Background for List Area
        graphics.fill(listX - 4, listY - 4, listX + listWidth + 4, listY + listHeight + 4, 0x30000000);
        renderRectOutline(graphics, listX - 4, listY - 4, listWidth + 8, listHeight + 8, COLOR_BORDER);

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
        
        // === Right Panel: Details ===
        int rightPanelStart = guiLeft + leftPanelWidth + margin;
        int rightPanelWidth = guiWidth - leftPanelWidth - margin * 2;
        int contentY = guiTop + HEADER_HEIGHT + 8;
        int contentBottom = guiTop + guiHeight - FOOTER_HEIGHT - 8;
        
        if (selectedIndex >= 0 && selectedIndex < stories.size()) {
            StoryEntry story = stories.get(selectedIndex);
            
            // 1. Thumbnail / Preview Image (16:9 Aspect Ratio)
            int thumbHeight = (int)(rightPanelWidth * 0.5625);
            // Limit height to fit within panel
            int maxThumbH = (int)((contentBottom - contentY) * 0.55);
            if (thumbHeight > maxThumbH) thumbHeight = maxThumbH;
            
            // Draw Thumbnail
            renderThumbnail(graphics, story, rightPanelStart, contentY, rightPanelWidth, thumbHeight);
            
            int textY = contentY + thumbHeight + 10;
            
            // 2. Title
            graphics.pose().pushPose();
            graphics.pose().translate(rightPanelStart, textY, 0);
            graphics.pose().scale(1.2f, 1.2f, 1.0f);
            graphics.drawString(font, story.name, 0, 0, COLOR_NEON_RED);
            graphics.pose().popPose();
            textY += 18;
            
            // 3. Stats Row
            String statsLabel = String.format("👥 %d-%d %s   ⏱ ~%d %s", 
                story.minPlayers, story.maxPlayers, Component.translatable("gui.storyadventure.label.people").getString(),
                story.estimatedMinutes, Component.translatable("gui.storyadventure.admin.stats.time.minutes").getString());
            graphics.drawString(font, statsLabel, rightPanelStart, textY, COLOR_NEON_PINK);
            textY += 12;
            
            // Divider
            graphics.fill(rightPanelStart, textY, rightPanelStart + rightPanelWidth, textY + 1, COLOR_BORDER);
            textY += 8;
            
            // 4. Description (clamp to content bottom)
            String desc = story.description;
            List<net.minecraft.util.FormattedCharSequence> wrappedDesc = font.split(Component.literal(desc), rightPanelWidth);
            for (net.minecraft.util.FormattedCharSequence line : wrappedDesc) {
                // Stop if getting too close to footer
                if (textY > contentBottom - 10) break; 
                graphics.drawString(font, line, rightPanelStart, textY, COLOR_TEXT_BODY);
                textY += 10;
            }
            
        } else {
            // Empty State
            int centerY = contentY + (contentBottom - contentY) / 2;
            graphics.drawCenteredString(font, Component.translatable("gui.storyadventure.select_prompt"), rightPanelStart + rightPanelWidth / 2, centerY, COLOR_TEXT_DIM);
        }
    }
    
    private void renderStoryEntry(GuiGraphics graphics, StoryEntry story, int x, int y, int entryWidth, boolean selected, int mouseX, int mouseY) {
        int entryHeight = getEntryHeight();
        boolean hovered = mouseX >= x && mouseX < x + entryWidth && mouseY >= y && mouseY < y + entryHeight;
        
        // Background
        int bgColor = selected ? 0xFF1B232C : (hovered ? 0xFF151B22 : 0x00000000); // Transparent if not interacting
        graphics.fill(x, y, x + entryWidth, y + entryHeight - 4, bgColor);
        
        // Active Indicator (Left Bar)
        if (selected) {
            graphics.fill(x, y, x + 3, y + entryHeight - 4, COLOR_NEON_RED);
             // Glow effect gradient
             graphics.fillGradient(x + 3, y, x + entryWidth, y + entryHeight - 4, 0x203BB6A6, 0x003BB6A6);
        } else if (hovered) {
             graphics.fill(x, y, x + 2, y + entryHeight - 4, COLOR_BORDER);
        }
        
        // Title & Mini Info - adjust vertical positions based on entry height
        int titleY = y + Math.max(4, (entryHeight - 28) / 2);
        int metaY = titleY + 14;
        
        int textColor = selected ? COLOR_NEON_RED : (hovered ? 0xFFFFFFFF : COLOR_TEXT_BODY);
        graphics.drawString(font, story.name, x + 10, titleY, textColor);
        
        // Mini Info
        String meta = String.format("%d-%d %s", story.minPlayers, story.maxPlayers, Component.translatable("gui.storyadventure.label.people").getString());
        graphics.drawString(font, meta, x + 10, metaY, COLOR_TEXT_DIM);
    }
    
    private void renderThumbnail(GuiGraphics graphics, StoryEntry story, int x, int y, int w, int h) {
        // Draw the static image
        ResourceLocation tex = COVER_IMAGE;
        if (story.cover != null && !story.cover.isEmpty()) {
            tex = ResourceLocation.fromNamespaceAndPath("storyadventure", "covers/" + story.cover);
        }
        
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, tex);
        graphics.blit(tex, x, y, w, h, 0, 0, w, h, w, h);
        
        // Inner border
        renderRectOutline(graphics, x, y, w, h, COLOR_BORDER);
        
        // "Scanner" line animation (optional, kept for cool factor)
        float scanPhase = (System.currentTimeMillis() % 3000) / 3000.0f;
        int scanY = y + (int)(h * scanPhase);
        graphics.fill(x, scanY, x + w, scanY + 1, 0x403BB6A6);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int ENTRY_HEIGHT = getEntryHeight();
        int HEADER_HEIGHT = getHeaderHeight();
        int FOOTER_HEIGHT = getFooterHeight();
        int margin = getMargin();
        
        int leftPanelWidth = (int)(guiWidth * 0.38);
        int listX = guiLeft + margin;
        int listY = guiTop + HEADER_HEIGHT + 8;
        int listWidth = leftPanelWidth - margin;
        int listHeight = guiHeight - HEADER_HEIGHT - FOOTER_HEIGHT - 16;

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
        int ENTRY_HEIGHT = getEntryHeight();
        int HEADER_HEIGHT = getHeaderHeight();
        int FOOTER_HEIGHT = getFooterHeight();
        
        int listHeight = guiHeight - HEADER_HEIGHT - FOOTER_HEIGHT - 16;
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
    
    public record StoryEntry(String id, String name, String description, int minPlayers, int maxPlayers, int estimatedMinutes, String cover) {}
}
