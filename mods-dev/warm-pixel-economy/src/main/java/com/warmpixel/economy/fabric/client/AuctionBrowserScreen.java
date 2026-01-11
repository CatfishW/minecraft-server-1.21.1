package com.warmpixel.economy.fabric.client;

import com.warmpixel.economy.fabric.AuctionListingView;
import com.warmpixel.economy.fabric.AuctionMenu;
import com.warmpixel.economy.fabric.WarmPixelEconomyMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public class AuctionBrowserScreen extends AbstractContainerScreen<AuctionMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WarmPixelEconomyMod.MOD_ID, "textures/gui/auction_browser.png");
    private int openTicks = 0;

    public AuctionBrowserScreen(AuctionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.titleLabelX = 12;
        this.titleLabelY = 8;
        this.inventoryLabelX = 12;
        this.inventoryLabelY = 127;
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
        Component title = Component.translatable("ui.warm_pixel_economy.auction.title");
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0xB9E3FF, false);
        Component pageText = Component.translatable("ui.warm_pixel_economy.auction.page", menu.page() + 1);
        guiGraphics.drawString(font, pageText, imageWidth - font.width(pageText) - 12, titleLabelY, 0xE8C37F, false);
        if (!menu.query().isBlank()) {
            guiGraphics.drawString(font, Component.translatable("ui.warm_pixel_economy.auction.search", menu.query()), titleLabelX, titleLabelY + 12, 0xB7C0C8, false);
        }
        guiGraphics.drawString(font, Component.translatable("ui.warm_pixel_economy.auction.hint"), titleLabelX, imageHeight - 96, 0xD6C3A0, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderHoverHighlight(guiGraphics, partialTick);
        renderListingOverlays(guiGraphics);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderListingOverlays(GuiGraphics guiGraphics) {
        List<AuctionListingView> listings = menu.listingViews();
        int count = Math.min(listings.size(), AuctionMenu.LISTING_SLOTS);
        for (int i = 0; i < count; i++) {
            AuctionListingView listing = listings.get(i);
            Slot slot = menu.slots.get(i);
            int x = leftPos + slot.x + 1;
            int y = topPos + slot.y + 12;
            long current = listing.highestBid() == null ? listing.startingPrice() : listing.highestBid();
            String price = String.valueOf(current);
            guiGraphics.drawString(font, price, x, y, 0xE8C37F, true);
            if (listing.buyoutPrice() != null) {
                guiGraphics.drawString(font, "BO", x + 10, y - 9, 0xB9E3FF, false);
            } else {
                guiGraphics.drawString(font, "BID", x + 6, y - 9, 0xB7C0C8, false);
            }
        }
    }

    private void renderHoverHighlight(GuiGraphics guiGraphics, float partialTick) {
        if (hoveredSlot == null || hoveredSlot.index >= AuctionMenu.LISTING_SLOTS) {
            return;
        }
        int pulse = (int) (60 + 60 * Math.sin((openTicks + partialTick) / 6.0f));
        int color = 0x66000000 | (pulse << 8) | 0xFF;
        int x = leftPos + hoveredSlot.x;
        int y = topPos + hoveredSlot.y;
        guiGraphics.fill(x, y, x + 16, y + 16, color);
    }
}
