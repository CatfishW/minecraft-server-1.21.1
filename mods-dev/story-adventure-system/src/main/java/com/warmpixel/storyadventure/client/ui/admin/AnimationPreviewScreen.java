package com.warmpixel.storyadventure.client.ui.admin;

import com.warmpixel.storyadventure.client.animation.AnimationManager;
import com.warmpixel.storyadventure.client.ui.StrangerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnimationPreviewScreen extends StrangerScreen {

    private final Entity targetEntity;
    private final List<ResourceLocation> animationList;
    private ResourceLocation selectedAnimation;
    private int scrollOffset = 0;
    private static final int LIST_WIDTH = 250;
    private static final int ENTRY_HEIGHT = 20;

    public AnimationPreviewScreen(Entity targetEntity) {
        super(Component.literal("Animation Preview: " + targetEntity.getDisplayName().getString()));
        this.targetEntity = targetEntity;
        this.animationList = new ArrayList<>(AnimationManager.getInstance().getAnimationIds());
        Collections.sort(this.animationList, (a, b) -> a.toString().compareTo(b.toString()));
    }

    @Override
    protected int getWindowWidth() {
        return Math.min(width - 20, 500);
    }

    @Override
    protected int getWindowHeight() {
        return Math.min(height - 20, 360);
    }

    @Override
    protected void init() {
        super.init();

        int rightPanelX = guiLeft + LIST_WIDTH + 30;
        int btnWidth = 100;
        int btnHeight = 24;
        int y = guiTop + 60;

        addStrangerButton(rightPanelX, y, btnWidth, btnHeight,
            Component.literal("Play Animation"), this::playSelected);
        
        y += 35;
        addStrangerButton(rightPanelX, y, btnWidth, btnHeight,
            Component.literal("Stop Animation"), this::stopAnimation);

        y += 35;
        addStrangerButton(rightPanelX, y, btnWidth, btnHeight,
            Component.literal("Refresh Animations"), this::refreshAnimations);

        y += 35;
        addStrangerButton(rightPanelX, y, btnWidth, btnHeight,
            Component.translatable("gui.storyadventure.admin.dashboard.close"), this::onClose);
    }

    private void playSelected() {
        if (selectedAnimation != null && targetEntity != null && targetEntity.isAlive()) {
            // Use the full ID including namespace (e.g. storyadventure:wave)
            // But verify if AnimationManager expects "animations/name.json" relative path or resource location?
            // CinematicCameraController reconstructs it.
            // AnimationManager.startAnimation takes a string and tries to parse it as ResourceLocation.
            // The keys in AnimationManager ARE ResourceLocations (e.g. storyadventure:wave).
            AnimationManager.getInstance().startAnimation(targetEntity, selectedAnimation.toString());
        }
    }

    private void stopAnimation() {
        if (targetEntity != null) {
            AnimationManager.getInstance().stopAnimation(targetEntity);
        }
    }

    private void refreshAnimations() {
        if (minecraft != null && minecraft.getResourceManager() != null) {
            AnimationManager.getInstance().load(minecraft.getResourceManager());
            this.animationList.clear();
            this.animationList.addAll(AnimationManager.getInstance().getAnimationIds());
            Collections.sort(this.animationList, (a, b) -> a.toString().compareTo(b.toString()));
            this.scrollOffset = 0;
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // List Background
        int listX = guiLeft + 15;
        int panelY = guiTop + 45;
        int panelHeight = guiHeight - 65;
        
        graphics.fill(listX, panelY, listX + LIST_WIDTH, panelY + panelHeight, 0xE0080808);
        drawPanelBorder(graphics, listX, panelY, LIST_WIDTH, panelHeight);
        
        graphics.drawString(font, Component.literal("Available Animations (" + animationList.size() + ")"), listX + 5, panelY + 5, COLOR_NEON_RED);
        graphics.fill(listX + 5, panelY + 19, listX + LIST_WIDTH - 5, panelY + 20, COLOR_BORDER);
        
        // Render List
        int listY = panelY + 25;
        int visibleCount = (panelHeight - 25) / ENTRY_HEIGHT;
        int visibleEnd = Math.min(scrollOffset + visibleCount, animationList.size());
        
        for (int i = scrollOffset; i < visibleEnd; i++) {
            ResourceLocation animId = animationList.get(i);
            boolean selected = animId.equals(selectedAnimation);
            boolean hovered = mouseX >= listX + 5 && mouseX < listX + LIST_WIDTH - 5 && 
                              mouseY >= listY && mouseY < listY + ENTRY_HEIGHT - 2;
            
            int bgColor = selected ? 0xFF331111 : (hovered ? 0xFF1A0808 : 0x00000000);
            if (bgColor != 0) {
                graphics.fill(listX + 5, listY, listX + LIST_WIDTH - 5, listY + ENTRY_HEIGHT - 2, bgColor);
            }
            
            graphics.drawString(font, animId.toString(), listX + 10, listY + 6, selected ? COLOR_NEON_RED : COLOR_TEXT_BODY);
            listY += ENTRY_HEIGHT;
        }
        
        // Selected Info
        int rightX = listX + LIST_WIDTH + 10;
        if (selectedAnimation != null) {
            graphics.drawString(font, Component.literal("Selected:"), rightX, guiTop + 180, COLOR_TEXT_DIM);
            graphics.drawString(font, selectedAnimation.toString(), rightX, guiTop + 195, COLOR_NEON_RED);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle list clicks
        int listX = guiLeft + 15;
        int panelY = guiTop + 45;
        int listY = panelY + 25;
        int panelHeight = guiHeight - 65;
        
        if (mouseX >= listX + 5 && mouseX < listX + LIST_WIDTH - 5 && mouseY >= listY) {
            int visibleCount = (panelHeight - 25) / ENTRY_HEIGHT;
            int visibleEnd = Math.min(scrollOffset + visibleCount, animationList.size());

            for (int i = scrollOffset; i < visibleEnd; i++) {
                if (mouseY >= listY && mouseY < listY + ENTRY_HEIGHT - 2) {
                    selectedAnimation = animationList.get(i);
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
            scrollOffset = Math.max(0, Math.min(animationList.size() - 5, scrollOffset - (int) vAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
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
}
