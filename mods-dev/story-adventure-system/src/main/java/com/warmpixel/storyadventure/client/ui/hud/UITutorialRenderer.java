package com.warmpixel.storyadventure.client.ui.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import com.warmpixel.storyadventure.mixin.AbstractContainerScreenAccessor;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders UI tutorial highlights and guides on the HUD.
 * Features:
 * - Spotlight/dimming effect around highlighted elements
 * - Animated arrows pointing to elements
 * - Pulsing glow effects
 * - Key hint overlays with animated click indicators
 * - Message tooltips with modern styling
 * 
 * Used by the story adventure system to guide players through tutorials.
 */
public class UITutorialRenderer implements HudRenderCallback {
    
    // Textures for tutorial elements
    private static final ResourceLocation SPOTLIGHT_TEXTURE = 
        ResourceLocation.fromNamespaceAndPath("storyadventure", "textures/gui/tutorial/spotlight.png");
    private static final ResourceLocation ARROW_TEXTURE = 
        ResourceLocation.fromNamespaceAndPath("storyadventure", "textures/gui/tutorial/arrow.png");
    private static final ResourceLocation CLICK_TEXTURE = 
        ResourceLocation.fromNamespaceAndPath("storyadventure", "textures/gui/tutorial/click_hint.png");
    private static final ResourceLocation KEY_BG_TEXTURE = 
        ResourceLocation.fromNamespaceAndPath("storyadventure", "textures/gui/tutorial/key_bg.png");
    
    // Colors
    private static final int COLOR_OVERLAY = 0xCC000000;  // Semi-transparent dark overlay
    private static final int COLOR_GLOW_DEFAULT = 0xFF00FFFF;
    private static final int COLOR_TEXT_BG = 0xE6101820;
    private static final int COLOR_TEXT_SHADOW = 0xFF000000;
    
    // Animation timing
    private static final float PULSE_SPEED = 3.0f;
    private static final float ARROW_BOB_SPEED = 4.0f;
    private static final float ARROW_BOB_AMOUNT = 4.0f;
    private static final float GLOW_SPEED = 2.0f;
    
    // Active tutorials
    private static final Map<String, TutorialEntry> activeTutorials = new ConcurrentHashMap<>();
    private static UITutorialRenderer instance;
    
    /**
     * Represents a single tutorial highlight.
     */
    public static class TutorialEntry {
        public final String id;
        public final String elementType;
        public final int elementIndex;
        public final int screenX;    // Percentage 0-100
        public final int screenY;    // Percentage 0-100
        public final int width;
        public final int height;
        public final String message;
        public final String keyHint;
        public final int color;
        public final boolean showArrow;
        public final boolean showPulse;
        public final boolean showClickHint;
        public final int durationTicks;
        public final boolean requireClick;
        public final long createdTime;
        public float fadeIn = 0f;
        
        public TutorialEntry(String id, String elementType, int elementIndex, 
                            int screenX, int screenY, int width, int height,
                            String message, String keyHint, int color,
                            boolean showArrow, boolean showPulse, boolean showClickHint,
                            int durationTicks, boolean requireClick) {
            this.id = id;
            this.elementType = elementType;
            this.elementIndex = elementIndex;
            this.screenX = screenX;
            this.screenY = screenY;
            this.width = width;
            this.height = height;
            this.message = message;
            this.keyHint = keyHint;
            this.color = color;
            this.showArrow = showArrow;
            this.showPulse = showPulse;
            this.showClickHint = showClickHint;
            this.durationTicks = durationTicks;
            this.requireClick = requireClick;
            this.createdTime = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            if (requireClick) return false; // Doesn't expire if click is required
            if (durationTicks <= 0) return false;
            long elapsed = System.currentTimeMillis() - createdTime;
            return elapsed > (durationTicks * 50L); // Convert ticks to ms
        }
        
        public float getAge() {
            return (System.currentTimeMillis() - createdTime) / 1000.0f;
        }
    }
    
    public static void register() {
        instance = new UITutorialRenderer();
        HudRenderCallback.EVENT.register(instance);
        
        // Register screen render callback to show tutorials over menus
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> {
                if (instance != null) {
                    instance.onScreenRender(graphics, tickDelta);
                }
            });
            
            // Handle clicks on tutorials
            ScreenMouseEvents.afterMouseClick(screen).register((s, mouseX, mouseY, button) -> {
                if (instance != null && button == 0) { // Left click
                    instance.onMouseClick(mouseX, mouseY);
                }
            });
        });
        
        StoryAdventureMod.LOGGER.info("[UITutorialRenderer] Registered HUD and Screen render callbacks");
    }
    
    public static UITutorialRenderer getInstance() {
        return instance;
    }
    
    // ==================== Public API ====================
    
    public static void showTutorial(TutorialEntry entry) {
        activeTutorials.put(entry.id, entry);
        StoryAdventureMod.LOGGER.debug("[UITutorialRenderer] Added tutorial: {} ({})", entry.id, entry.elementType);
    }
    
    public static void hideTutorial(String id) {
        activeTutorials.remove(id);
        StoryAdventureMod.LOGGER.debug("[UITutorialRenderer] Removed tutorial: {}", id);
    }
    
    public static void clearTutorials() {
        activeTutorials.clear();
        StoryAdventureMod.LOGGER.debug("[UITutorialRenderer] Cleared all tutorials");
    }
    
    public static boolean hasTutorials() {
        return !activeTutorials.isEmpty();
    }
    
    // ==================== Rendering ====================
    
    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (activeTutorials.isEmpty()) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        // Skip if any screen is open (except chat)
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        
        renderAll(graphics, deltaTracker.getGameTimeDeltaPartialTick(true));
    }
    
    public void onScreenRender(GuiGraphics graphics, float tickDelta) {
        if (activeTutorials.isEmpty()) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen == null) return;
        
        renderAll(graphics, tickDelta);
    }
    
    private void renderAll(GuiGraphics graphics, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float time = (mc.level.getGameTime() + tickDelta) / 20.0f;
        
        // Remove expired tutorials
        activeTutorials.entrySet().removeIf(e -> e.getValue().isExpired());
        
        graphics.pose().pushPose();
        
        // Ensure standard GUI shader and blend state
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        
        // Use a more standard Z layer for HUD overlays
        graphics.pose().translate(0, 0, 200);
        
        for (TutorialEntry entry : activeTutorials.values()) {
            // Update fade in
            entry.fadeIn = Math.min(1.0f, entry.fadeIn + 0.05f);
            renderTutorial(graphics, font, entry, screenWidth, screenHeight, time);
        }
        
        // Restore states
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.pose().popPose();
    }
    
    /**
     * Handles mouse clicks to detect if a tutorial element was clicked.
     */
    public void onMouseClick(double mouseX, double mouseY) {
        if (activeTutorials.isEmpty()) return;
        
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        for (TutorialEntry entry : activeTutorials.values()) {
            if (!entry.requireClick) continue;
            
            int[] bounds = calculateElementBounds(entry, screenWidth, screenHeight, mc.screen);
            int x = bounds[0];
            int y = bounds[1];
            int w = bounds[2];
            int h = bounds[3];
            
            // Add some padding for easier clicking
            int pad = 4;
            if (mouseX >= x - pad && mouseX <= x + w + pad && 
                mouseY >= y - pad && mouseY <= y + h + pad) {
                
                // Element clicked!
                StoryAdventureMod.LOGGER.info("[UITutorialRenderer] Tutorial element clicked: {}", entry.id);
                
                // Send feedback to server
                sendClickFeedback(entry.id);
                
                // Hide locally
                activeTutorials.remove(entry.id);
            }
        }
    }
    
    private void sendClickFeedback(String tutorialId) {
        // We'll implement the packet sending in ClientNetworkHandler or here
        // For now, let's use a static method to be added to ClientNetworkHandler
        com.warmpixel.storyadventure.network.ClientNetworkHandler.sendTutorialClick(tutorialId);
    }
    
    private void renderTutorial(GuiGraphics graphics, Font font, TutorialEntry entry,
                               int screenWidth, int screenHeight, float time) {
        // Calculate actual screen position based on element type
        int[] bounds = calculateElementBounds(entry, screenWidth, screenHeight, Minecraft.getInstance().screen);
        int x = bounds[0];
        int y = bounds[1];
        int w = bounds[2];
        int h = bounds[3];
        
        float alpha = entry.fadeIn;
        int baseColor = entry.color | 0xFF000000;
        
        // Render spotlight/highlight effect
        if (entry.showPulse) {
            renderPulsingHighlight(graphics, x, y, w, h, baseColor, time, alpha);
        } else {
            renderStaticHighlight(graphics, x, y, w, h, baseColor, alpha);
        }
        
        // Render arrow pointing to element
        if (entry.showArrow) {
            renderArrow(graphics, x + w / 2, y - 8, baseColor, time, alpha);
        }
        
        // Render click hint animation
        if (entry.showClickHint) {
            renderClickHint(graphics, x + w / 2, y + h / 2, time, alpha);
        }
        
        // Render key hint bubble
        if (entry.keyHint != null && !entry.keyHint.isEmpty()) {
            renderKeyHint(graphics, font, x + w + 12, y + h / 2, entry.keyHint, baseColor, time, alpha);
        }
        
        // Render message tooltip
        if (entry.message != null && !entry.message.isEmpty()) {
            renderMessage(graphics, font, x + w / 2, y + h + 16, entry.message, baseColor, alpha, screenWidth);
        }
        
        // Reset color and shader state after each tutorial element
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    /**
     * Calculate the screen bounds for a tutorial element.
     */
    private int[] calculateElementBounds(TutorialEntry entry, int screenWidth, int screenHeight, Screen currentScreen) {
        int x, y, w, h;
        
        switch (entry.elementType.toLowerCase()) {
            case "hotbar" -> {
                // Hotbar slot positions (centered at bottom)
                int hotbarWidth = 182;
                int slotSize = 20;
                int hotbarX = (screenWidth - hotbarWidth) / 2;
                int hotbarY = screenHeight - 22;
                
                int slot = Mth.clamp(entry.elementIndex, 0, 8);
                x = hotbarX + slot * 20 + 3;
                y = hotbarY + 3;
                w = 16;
                h = 16;
            }
            case "crosshair" -> {
                // Center of screen
                x = screenWidth / 2 - 8;
                y = screenHeight / 2 - 8;
                w = 16;
                h = 16;
            }
            case "health_bar" -> {
                // Health bar position (left of center, bottom)
                x = screenWidth / 2 - 91;
                y = screenHeight - 39;
                w = 81;
                h = 9;
            }
            case "armor_bar" -> {
                // Armor bar position
                x = screenWidth / 2 - 91;
                y = screenHeight - 49;
                w = 81;
                h = 9;
            }
            case "experience_bar" -> {
                // Experience bar position
                x = screenWidth / 2 - 91;
                y = screenHeight - 29;
                w = 182;
                h = 5;
            }
            case "screen_region" -> {
                // Custom percentage-based position
                x = (int)(screenWidth * entry.screenX / 100.0f);
                y = (int)(screenHeight * entry.screenY / 100.0f);
                w = entry.width > 0 ? (int)(screenWidth * entry.width / 100.0f) : 32;
                h = entry.height > 0 ? (int)(screenHeight * entry.height / 100.0f) : 32;
            }
            case "key_hint" -> {
                // Floating position for key hints
                x = (int)(screenWidth * entry.screenX / 100.0f);
                y = (int)(screenHeight * entry.screenY / 100.0f);
                w = 48;
                h = 48;
            }
            case "inventory" -> {
                // Inventory slot (when open)
                if (currentScreen instanceof AbstractContainerScreen<?> container) {
                    int slotIdx = entry.elementIndex;
                    if (slotIdx >= 0 && slotIdx < container.getMenu().slots.size()) {
                        var slot = container.getMenu().slots.get(slotIdx);
                        x = ((AbstractContainerScreenAccessor)container).getGuiLeft() + slot.x;
                        y = ((AbstractContainerScreenAccessor)container).getGuiTop() + slot.y;
                        w = 16;
                        h = 16;
                    } else {
                        x = screenWidth / 2; y = screenHeight / 2; w = 16; h = 16;
                    }
                } else {
                    x = screenWidth / 2; y = screenHeight / 2; w = 16; h = 16;
                }
            }
            case "ftb_quests_inventory_button" -> {
                // FTB Library sidebar buttons are rendered by SidebarGroupGuiButton
                // The buttons use a 17px grid (16px icon + 1px spacing)
                // By default, sidebar is at TOP_LEFT corner (xRenderStart=0, yRenderStart=0)
                // The 'quests' button is typically at grid position (0,0)
                // Button position formula: x = xRenderStart + gridX * 17 + 1
                //                           y = yRenderStart + gridY * 17 + 1
                // For grid (0,0) at TOP_LEFT: x = 0 + 0 * 17 + 1 = 1, y = 0 + 0 * 17 + 1 = 1
                
                // Try to find the SidebarGroupGuiButton widget
                int[] sidebarBounds = findWidgetBounds(currentScreen, widget -> 
                    widget.getClass().getName().contains("SidebarGroupGuiButton"));
                
                if (sidebarBounds != null) {
                    // Found the sidebar container - first button at grid (0,0) is at +1 offset
                    x = sidebarBounds[0] + 1;
                    y = sidebarBounds[1] + 1;
                    w = 16;
                    h = 16;
                } else if (currentScreen instanceof AbstractContainerScreen<?>) {
                    // FTB Library sidebar buttons are rendered as an overlay on container screens
                    // They are positioned based on the screen size, not the GUI bounds
                    // Default position (TOP_LEFT) means grid starts at (0, 0)
                    // First button (quests) at grid (0,0): x = 0 + 0*17 + 1 = 1, y = 0 + 0*17 + 1 = 1
                    x = 1;
                    y = 1;
                    w = 16;
                    h = 16;
                } else {
                    // Ultimate fallback - use screen top-left area
                    x = 1; y = 1; w = 16; h = 16;
                }
            }
            case "ftb_quests_claim_all" -> {
                // FTB Quests screen is FULLSCREEN and uses FTB Library's panel system
                // OtherButtonsPanelTop is positioned at (screenWidth - 20, 1)
                // CollectRewardsButton is the first button in the panel (20x18 each)
                // The panel aligns buttons vertically, so claim all is at top
                
                // Check if we're on the FTB Quest screen by class name
                boolean isQuestScreen = currentScreen != null && 
                    currentScreen.getClass().getName().contains("QuestScreen");
                
                if (isQuestScreen) {
                    // CollectRewardsButton position in OtherButtonsPanelTop
                    // Panel is at (screenWidth - 20, 1), button is 20x18
                    x = screenWidth - 20;
                    y = 1;
                    w = 20;
                    h = 18;
                } else {
                    // Fallback for unknown screens
                    x = screenWidth - 24;
                    y = 4;
                    w = 20;
                    h = 18;
                }
            }
            case "ftb_quests_chapters" -> {
                // FTB Quests screen is FULLSCREEN
                // ChapterPanel is on the left side, starts at x=0 when expanded
                // It contains ModpackButton + ChapterButtons
                // ChapterPanel width is typically 100-150px, height = screenHeight
                
                // Check if we're on the FTB Quest screen
                boolean isQuestScreen = currentScreen != null && 
                    currentScreen.getClass().getName().contains("QuestScreen");
                
                if (isQuestScreen) {
                    // ChapterPanel starts at left edge
                    // Typical width is around 100-120px when expanded
                    // We target the chapter buttons area (skip ModpackButton at top)
                    x = 20; // After expand button (20px wide)
                    y = 25; // Below ModpackButton header
                    w = 100;
                    h = Math.min(screenHeight - 50, 200); // Cap height for visibility
                } else {
                    // Fallback
                    x = 20;
                    y = 30;
                    w = 80;
                    h = 150;
                }
            }
            case "ftb_quests_chapter_expand" -> {
                // The expand/collapse arrow button on the left edge of FTB Quests screen
                // This is the small arrow that expands the chapter panel
                // It's positioned at the left edge, centered vertically
                
                boolean isQuestScreen = currentScreen != null && 
                    currentScreen.getClass().getName().contains("QuestScreen");
                
                if (isQuestScreen) {
                    // The expand button is a small arrow on the left side
                    // Position: left edge, vertically centered in the chapter area
                    x = 2;  // Left edge with small padding
                    y = 25; // Near the top where expand arrow is
                    w = 16; // Small button
                    h = 20;
                } else {
                    x = 2;
                    y = 25;
                    w = 16;
                    h = 20;
                }
            }
            default -> {
                // Default: use percentage positions
                x = (int)(screenWidth * entry.screenX / 100.0f);
                y = (int)(screenHeight * entry.screenY / 100.0f);
                w = 32;
                h = 32;
            }
        }
        
        return new int[] { x, y, w, h };
    }

    private int[] findWidgetBounds(Screen screen, Predicate<AbstractWidget> criteria) {
        if (screen == null) return null;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && criteria.test(widget)) {
                return new int[] { widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight() };
            }
        }
        // Also check renderables if accessible? For now children() covers interactive widgets.
        return null;
    }
    
    /**
     * Render a pulsing highlight around an element.
     */
    private void renderPulsingHighlight(GuiGraphics graphics, int x, int y, int w, int h,
                                        int color, float time, float alpha) {
        // Extract color components
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        // Pulse effect
        float pulse = 0.5f + 0.5f * (float)Math.sin(time * PULSE_SPEED);
        int pulseAlpha = (int)(180 * alpha * pulse);
        
        // Outer glow (larger, more transparent)
        int glowPad = (int)(8 + 4 * pulse);
        int glowColor = (((int)(pulseAlpha * 0.4f)) << 24) | (r << 16) | (g << 8) | b;
        drawRoundedRect(graphics, x - glowPad, y - glowPad, w + glowPad * 2, h + glowPad * 2, 
                       glowColor, 6);
        
        // Main highlight border
        int borderAlpha = (int)(220 * alpha);
        int borderColor = (borderAlpha << 24) | (r << 16) | (g << 8) | b;
        drawBorder(graphics, x - 2, y - 2, w + 4, h + 4, borderColor, 2);
        
        // Inner bright edge
        int innerAlpha = (int)(120 * alpha * pulse);
        int innerColor = (innerAlpha << 24) | 0xFFFFFF;
        drawBorder(graphics, x - 1, y - 1, w + 2, h + 2, innerColor, 1);
        
        // Corner accents
        drawCornerAccents(graphics, x - 4, y - 4, w + 8, h + 8, borderColor, 8);
    }
    
    /**
     * Render a static highlight (no animation).
     */
    private void renderStaticHighlight(GuiGraphics graphics, int x, int y, int w, int h,
                                       int color, float alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        int borderAlpha = (int)(200 * alpha);
        int borderColor = (borderAlpha << 24) | (r << 16) | (g << 8) | b;
        
        drawBorder(graphics, x - 2, y - 2, w + 4, h + 4, borderColor, 2);
        drawCornerAccents(graphics, x - 3, y - 3, w + 6, h + 6, borderColor, 6);
    }
    
    /**
     * Render an animated arrow pointing down.
     */
    private void renderArrow(GuiGraphics graphics, int x, int y, int color, float time, float alpha) {
        // Bob animation
        float bob = (float)Math.sin(time * ARROW_BOB_SPEED) * ARROW_BOB_AMOUNT;
        int arrowY = (int)(y - 20 + bob);
        // Use blit for the arrow instead of manual triangle drawing
        // ARROW_TEXTURE is 128x128 PNG (converted from JPEG)
        int arrowSize = 32; // Slightly larger for better visibility
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, ARROW_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r / 255.0f, g / 255.0f, b / 255.0f, alpha);
        
        // blit(texture, x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight)
        graphics.blit(ARROW_TEXTURE, x - arrowSize/2, arrowY, arrowSize, arrowSize, 0.0f, 0.0f, 128, 128, 128, 128);
        
        // Render a glow layer with the same texture
        float pulse = 0.5f + 0.5f * (float)Math.sin(time * 3.0f);
        float glowAlpha = 0.4f * alpha * pulse;
        RenderSystem.setShaderColor(r / 255.0f, g / 255.0f, b / 255.0f, glowAlpha);
        int glowSize = (int)(arrowSize * 1.4f);
        graphics.blit(ARROW_TEXTURE, x - glowSize/2, arrowY - (glowSize-arrowSize)/2, glowSize, glowSize, 0.0f, 0.0f, 128, 128, 128, 128);
        
        // Reset color
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    /**
     * Render a click hint animation (hand/cursor icon).
     */
    private void renderClickHint(GuiGraphics graphics, int x, int y, float time, float alpha) {
        // Click animation: scale and pulse
        float clickPhase = (time * 2.5f) % 1.0f;
        float scale = 0.8f + 0.4f * (1.0f - clickPhase);
        float clickAlpha = alpha * (1.0f - clickPhase);
        
        int size = (int)(32 * scale);
        
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, CLICK_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, clickAlpha);
        
        graphics.blit(CLICK_TEXTURE, x - size/2, y - size/2, size, size, 0.0f, 0.0f, 128, 128, 128, 128);
        
        // Draw main indicator (static or slower pulse)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha * 0.8f);
        int mainSize = 24;
        graphics.blit(CLICK_TEXTURE, x - mainSize/2, y - mainSize/2, mainSize, mainSize, 0.0f, 0.0f, 128, 128, 128, 128);
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    /**
     * Render a key hint bubble (e.g., "R", "Space").
     */
    private void renderKeyHint(GuiGraphics graphics, Font font, int x, int y, 
                               String keyHint, int color, float time, float alpha) {
        // Calculate dimensions
        int textWidth = font.width(keyHint);
        int bubbleWidth = Math.max(textWidth + 16, 28);
        int bubbleHeight = 24;
        
        // Pulse effect
        float pulse = 0.9f + 0.1f * (float)Math.sin(time * GLOW_SPEED);
        
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        // Background texture
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, KEY_BG_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r / 255.0f, g / 255.0f, b / 255.0f, alpha);
        
        graphics.blit(KEY_BG_TEXTURE, x, y - bubbleHeight/2, bubbleWidth, bubbleHeight, 0.0f, 0.0f, 128, 128, 128, 128);
        
        // Key text
        int textAlpha = (int)(255 * alpha);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        graphics.drawString(font, keyHint, x + (bubbleWidth - textWidth) / 2, y - 4, textColor, false);
        
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    /**
     * Render a message tooltip below the element.
     */
    private void renderMessage(GuiGraphics graphics, Font font, int x, int y, 
                               String message, int color, float alpha, int screenWidth) {
        // Calculate dimensions
        int textWidth = font.width(message);
        int padding = 10;
        int bubbleWidth = textWidth + padding * 2;
        int bubbleHeight = 18;
        
        // Center the bubble
        int bubbleX = x - bubbleWidth / 2;
        
        // Clamp to screen bounds
        bubbleX = Mth.clamp(bubbleX, 4, screenWidth - bubbleWidth - 4);
        
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        // Background with gradient
        int bgAlpha = (int)(230 * alpha);
        int bgColor = (bgAlpha << 24) | 0x151520;
        drawRoundedRect(graphics, bubbleX, y, bubbleWidth, bubbleHeight, bgColor, 4);
        
        // Top accent line
        int accentAlpha = (int)(255 * alpha);
        int accentColor = (accentAlpha << 24) | (r << 16) | (g << 8) | b;
        graphics.fill(bubbleX + 4, y, bubbleX + bubbleWidth - 4, y + 2, accentColor);
        
        // Message text
        int textColor = (accentAlpha << 24) | 0xE8E8F0;
        graphics.drawString(font, message, bubbleX + padding, y + 5, textColor, false);
    }
    
    // ==================== Drawing Helpers ====================
    
    private void drawRoundedRect(GuiGraphics graphics, int x, int y, int w, int h, int color, int radius) {
        // Explicitly set shader for fill calls to prevent leakage with shaders
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        graphics.fill(x, y, x + w, y + h, color);
    }
    
    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color, int thickness) {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        // Top
        graphics.fill(x, y, x + w, y + thickness, color);
        // Bottom
        graphics.fill(x, y + h - thickness, x + w, y + h, color);
        // Left
        graphics.fill(x, y, x + thickness, y + h, color);
        // Right
        graphics.fill(x + w - thickness, y, x + w, y + h, color);
    }
    
    private void drawCornerAccents(GuiGraphics graphics, int x, int y, int w, int h, int color, int length) {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        // Top-left corner
        graphics.fill(x, y, x + length, y + 2, color);
        graphics.fill(x, y, x + 2, y + length, color);
        
        // Top-right corner
        graphics.fill(x + w - length, y, x + w, y + 2, color);
        graphics.fill(x + w - 2, y, x + w, y + length, color);
        
        // Bottom-left corner
        graphics.fill(x, y + h - 2, x + length, y + h, color);
        graphics.fill(x, y + h - length, x + 2, y + h, color);
        
        // Bottom-right corner
        graphics.fill(x + w - length, y + h - 2, x + w, y + h, color);
        graphics.fill(x + w - 2, y + h - length, x + w, y + h, color);
    }
    
    private void drawTriangle(GuiGraphics graphics, int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        // For simplicity, draw as a filled polygon approximation
        // In a real implementation, this would use tessellator
        int minX = Math.min(x1, Math.min(x2, x3));
        int maxX = Math.max(x1, Math.max(x2, x3));
        int minY = Math.min(y1, Math.min(y2, y3));
        int maxY = Math.max(y1, Math.max(y2, y3));
        
        // Safety: Clamp to screen bounds to prevent massive artifacts
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            minX = Math.max(0, minX);
            maxX = Math.min(screenWidth, maxX);
            minY = Math.max(0, minY);
            maxY = Math.min(screenHeight, maxY);
        }
        
        if (minX >= maxX || minY >= maxY) return;
        
        // Simple filled triangle approximation using scanlines
        for (int y = minY; y <= maxY; y++) {
            int startX = maxX;
            int endX = minX;
            
            // Check intersection with each edge
            int[] edges = new int[] {
                x1, y1, x2, y2,
                x2, y2, x3, y3,
                x3, y3, x1, y1
            };
            
            for (int i = 0; i < 3; i++) {
                int ex1 = edges[i * 4];
                int ey1 = edges[i * 4 + 1];
                int ex2 = edges[i * 4 + 2];
                int ey2 = edges[i * 4 + 3];
                
                if ((ey1 <= y && ey2 > y) || (ey2 <= y && ey1 > y)) {
                    // Safety check for divide by zero (though implied by condition)
                    int dy = ey2 - ey1;
                    if (dy != 0) {
                        int xInt = ex1 + (y - ey1) * (ex2 - ex1) / dy;
                        startX = Math.min(startX, xInt);
                        endX = Math.max(endX, xInt);
                    }
                }
            }
            
            if (startX <= endX) {
                graphics.fill(startX, y, endX + 1, y + 1, color);
            }
        }
    }
}
