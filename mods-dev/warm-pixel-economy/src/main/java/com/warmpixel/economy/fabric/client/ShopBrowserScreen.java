package com.warmpixel.economy.fabric.client;

import com.warmpixel.economy.fabric.ShopMenu;
import com.warmpixel.economy.fabric.ShopOfferView;
import com.warmpixel.economy.fabric.WarmPixelEconomyMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public class ShopBrowserScreen extends AbstractContainerScreen<ShopMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WarmPixelEconomyMod.MOD_ID, "textures/gui/shop_browser.png");
    private int openTicks = 0;

    public ShopBrowserScreen(ShopMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.titleLabelX = 12;
        this.titleLabelY = 8;
        this.inventoryLabelX = 12;
        this.inventoryLabelY = 127;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void containerTick() {
        openTicks++;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Enhanced title with coin icon and warmer gold gradient
        String titlePrefix = "◎ ";
        Component title = Component.translatable("ui.warm_pixel_economy.shop.title");
        String fullTitle = titlePrefix + title.getString();
        // Draw title shadow for depth
        guiGraphics.drawString(font, fullTitle, titleLabelX + 1, titleLabelY + 1, 0x3D2A10, false);
        guiGraphics.drawString(font, fullTitle, titleLabelX, titleLabelY, 0xFFD966, false);
        
        // Enhanced page indicator with decorative background
        Component pageText = Component.translatable("ui.warm_pixel_economy.shop.page", menu.page() + 1);
        int pageX = imageWidth - font.width(pageText) - 14;
        // Draw badge background
        guiGraphics.fill(pageX - 4, titleLabelY - 2, imageWidth - 8, titleLabelY + 10, 0x44000000);
        guiGraphics.fill(pageX - 3, titleLabelY - 1, imageWidth - 9, titleLabelY + 9, 0x22FFFFFF);
        guiGraphics.drawString(font, pageText, pageX, titleLabelY, 0x7EB8E8, false);
        
        if (!menu.category().isBlank()) {
            Component categoryLabel = Component.literal("§7[§b" + menu.category() + "§7]");
            guiGraphics.drawString(font, categoryLabel, titleLabelX, titleLabelY + 12, 0xB7C0C8, false);
        }
        if (!menu.query().isBlank()) {
            Component searchLabel = Component.literal("§7🔍 §f" + menu.query());
            guiGraphics.drawString(font, searchLabel, titleLabelX, titleLabelY + 22, 0xB7C0C8, false);
        }
        
        // Styled hint at bottom
        guiGraphics.drawString(font, "§e⬅ Buy §7| §a➡ Sell", titleLabelX, imageHeight - 96, 0xD6C3A0, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderHoverHighlight(guiGraphics, partialTick);
        renderOfferOverlays(guiGraphics);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderOfferOverlays(GuiGraphics guiGraphics) {
        List<ShopOfferView> offers = menu.offerViews();
        int count = Math.min(offers.size(), ShopMenu.OFFER_SLOTS);
        for (int i = 0; i < count; i++) {
            ShopOfferView offer = offers.get(i);
            Slot slot = menu.slots.get(i);
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            
            // Simple B/S indicator at top-right corner
            String mode;
            int modeColor;
            if (!offer.buyEnabled()) {
                mode = "S";
                modeColor = 0x7AE89C;  // Green for sell-only
            } else if (!offer.sellEnabled()) {
                mode = "B";
                modeColor = 0x8FD0FF;  // Blue for buy-only
            } else {
                mode = "";  // Don't show indicator for both
                modeColor = 0;
            }
            if (!mode.isEmpty()) {
                guiGraphics.drawString(font, mode, x + 11, y, modeColor, true);
            }
        }
    }

    private void renderHoverHighlight(GuiGraphics guiGraphics, float partialTick) {
        if (hoveredSlot == null || hoveredSlot.index >= ShopMenu.OFFER_SLOTS) {
            return;
        }
        // Warm golden pulse effect instead of gray
        float sinVal = (float) Math.sin((openTicks + partialTick) / 5.0f);
        int goldR = (int) (200 + 55 * sinVal);
        int goldG = (int) (160 + 40 * sinVal);
        int goldB = (int) (60 + 20 * sinVal);
        int color = 0x55000000 | (goldR << 16) | (goldG << 8) | goldB;
        int x = leftPos + hoveredSlot.x;
        int y = topPos + hoveredSlot.y;
        // Draw outer glow border
        guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, 0x33FFD966);
        guiGraphics.fill(x, y, x + 16, y + 16, color);
    }
}
