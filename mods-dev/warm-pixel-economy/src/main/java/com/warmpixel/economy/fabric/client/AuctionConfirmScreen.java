package com.warmpixel.economy.fabric.client;

import com.warmpixel.economy.fabric.AuctionConfirmMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class AuctionConfirmScreen extends AbstractContainerScreen<AuctionConfirmMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public AuctionConfirmScreen(AuctionConfirmMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // Blit only the part of the generic chest texture that we need for 3 rows
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, 71);
        guiGraphics.blit(TEXTURE, leftPos, topPos + 71, 0, 126, imageWidth, 96);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
