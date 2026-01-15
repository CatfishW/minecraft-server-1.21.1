package com.flaneconomy.client;

import com.flaneconomy.FlanEconomyMod;
import com.flaneconomy.network.BuyClaimBlocksPayload;
import com.flaneconomy.network.BuyClaimPayload;
import com.flaneconomy.network.ListClaimPayload;
import com.flaneconomy.network.RequestClaimMarketPayload;
import com.flaneconomy.network.RenameClaimPayload;
import com.flaneconomy.network.RequestPlayerClaimsPayload;
import com.flaneconomy.network.UnlistClaimPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

public class ClaimShopScreen extends Screen {
    private static final int FULL_PANEL_WIDTH = 380;
    private static final int MINI_PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 320;
    private static final int COLOR_GOLD = 0xFFE6C875;
    private static final int COLOR_TEXT = 0xFFDADADA;
    private static final int COLOR_SUBTEXT = 0xFF8A96A8;
    private static final int COLOR_ACCENT = 0xFF8FD0FF;
    private static final int COLOR_ERROR = 0xFFFF7070;

    private static final List<String> ICONS = Arrays.asList(
        "minecraft:grass_block", "minecraft:dirt", "minecraft:cobblestone", "minecraft:oak_log", 
        "minecraft:diamond_block", "minecraft:gold_block", "minecraft:iron_block", "minecraft:emerald_block",
        "minecraft:oak_sapling", "minecraft:poppy", "minecraft:dandelion", "flan_economy:land_deed"
    );

    private ClaimShopData data;
    private int currentPanelWidth = FULL_PANEL_WIDTH;
    private MarketButton buyClaimButton;
    private MarketButton sellClaimButton;
    private EditBox priceEdit;
    private EditBox nameEdit;
    private MarketButton renameButton;
    private MarketButton updateSaleButton;
    private int selectedIconIndex = 0;

    public ClaimShopScreen(ClaimShopData data) {
        super(Component.translatable("gui.flaneconomy.market.title"));
        this.data = data;
        this.currentPanelWidth = shouldShowClaimSection() ? FULL_PANEL_WIDTH : MINI_PANEL_WIDTH;
        this.selectedIconIndex = Math.max(0, ICONS.indexOf(data.iconId()));
    }

    private boolean shouldShowClaimSection() {
        return data.hasClaim() && (data.isOwner() || data.forSale());
    }

    public void setData(ClaimShopData data) {
        boolean wasShowing = shouldShowClaimSection();
        this.data = data;
        this.selectedIconIndex = Math.max(0, ICONS.indexOf(data.iconId()));
        
        boolean nowShowing = shouldShowClaimSection();
        if (wasShowing != nowShowing) {
            this.currentPanelWidth = nowShowing ? FULL_PANEL_WIDTH : MINI_PANEL_WIDTH;
            this.clearWidgets();
            this.init();
            return;
        }

        if (this.buyClaimButton != null) {
            this.buyClaimButton.active = data.forSale() && !data.isOwner();
        }
        if (this.sellClaimButton != null) {
            this.sellClaimButton.visible = data.isOwner() && data.hasClaim();
            this.sellClaimButton.setMessage(data.forSale() ? Component.translatable("gui.flaneconomy.market.unlist") : Component.translatable("gui.flaneconomy.market.list"));
        }
        if (this.priceEdit != null) {
            this.priceEdit.visible = data.isOwner() && data.hasClaim();
        }
        if (this.nameEdit != null) {
            this.nameEdit.visible = data.isOwner() && data.hasClaim();
            if (data.isOwner()) this.nameEdit.setValue(data.claimName());
        }
        if (this.renameButton != null) {
            this.renameButton.visible = data.isOwner() && data.hasClaim();
        }
        if (this.updateSaleButton != null) {
            this.updateSaleButton.visible = data.isOwner() && data.hasClaim() && data.forSale();
        }
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - currentPanelWidth) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        boolean showClaim = shouldShowClaimSection();

        // === STAT CARDS ===
        int cardWidth;
        int cardY = top + 35;
        if (showClaim) {
            cardWidth = (FULL_PANEL_WIDTH - 50) / 2;
            renderStatCardWidget(left + 20, cardY, cardWidth, 35, 
                Component.translatable("gui.flaneconomy.market.coins"), String.valueOf(data.balance()), COLOR_GOLD);
            renderStatCardWidget(left + FULL_PANEL_WIDTH - 20 - cardWidth, cardY, cardWidth, 35, 
                Component.translatable("gui.flaneconomy.market.blocks"), String.valueOf(data.claimBlocks()), COLOR_ACCENT);
        } else {
            cardWidth = (MINI_PANEL_WIDTH - 30) / 2;
            renderStatCardWidget(left + 10, cardY, cardWidth, 35, 
                Component.translatable("gui.flaneconomy.market.coins"), String.valueOf(data.balance()), COLOR_GOLD);
            renderStatCardWidget(left + MINI_PANEL_WIDTH - 10 - cardWidth, cardY, cardWidth, 35, 
                Component.translatable("gui.flaneconomy.market.blocks"), String.valueOf(data.claimBlocks()), COLOR_ACCENT);
        }

        // === LEFT COLUMN: Buy Claim Blocks ===
        int leftColX = showClaim ? left + 20 : left + (currentPanelWidth - 150) / 2;
        int btnY = top + 100;
        
        this.addRenderableWidget(new MarketButton(leftColX, btnY, 150, 22, 
            Component.translatable("gui.flaneconomy.market.buy_x_blocks", 100), 
            button -> buyClaimBlocks(100), MarketButton.Type.PRIMARY));
        this.addRenderableWidget(new MarketButton(leftColX, btnY + 28, 150, 22, 
            Component.translatable("gui.flaneconomy.market.buy_x_blocks", 500), 
            button -> buyClaimBlocks(500), MarketButton.Type.PRIMARY));
        this.addRenderableWidget(new MarketButton(leftColX, btnY + 56, 150, 22, 
            Component.translatable("gui.flaneconomy.market.buy_x_blocks", 1000), 
            button -> buyClaimBlocks(1000), MarketButton.Type.PRIMARY));

        // === RIGHT COLUMN: Claim Info & Actions ===
        if (showClaim) {
            int rightColX = left + 195;
            
            // Purchase Claim Button
            this.buyClaimButton = this.addRenderableWidget(new MarketButton(rightColX, top + 160, 160, 22, 
                Component.translatable("gui.flaneconomy.market.purchase_claim"), 
                button -> buyClaim(), MarketButton.Type.SUCCESS));
            this.buyClaimButton.active = data.forSale() && !data.isOwner();

            // === OWNER SECTION: Sell & Rename ===
            int ownerY = top + 190;
            
            this.priceEdit = this.addRenderableWidget(new EditBox(this.font, rightColX, ownerY, 80, 18, Component.literal("Price")));
            this.priceEdit.setValue(data.forSale() ? String.valueOf(data.salePrice()) : "1000");
            this.priceEdit.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
            this.priceEdit.visible = data.isOwner() && data.hasClaim();

            this.sellClaimButton = this.addRenderableWidget(new MarketButton(rightColX + 85, ownerY, 75, 18, 
                data.forSale() ? Component.translatable("gui.flaneconomy.market.unlist") : Component.translatable("gui.flaneconomy.market.list"), 
                button -> { if (data.forSale()) unlistClaim(); else listOrUpdateClaim(); }, MarketButton.Type.PRIMARY));
            this.sellClaimButton.visible = data.isOwner() && data.hasClaim();

            this.nameEdit = this.addRenderableWidget(new EditBox(this.font, rightColX, ownerY + 24, 110, 18, Component.literal("Name")));
            this.nameEdit.setValue(data.claimName());
            this.nameEdit.visible = data.isOwner() && data.hasClaim();

            this.renameButton = this.addRenderableWidget(new MarketButton(rightColX + 115, ownerY + 24, 45, 18, 
                Component.translatable("gui.flaneconomy.market.rename"), 
                button -> renameClaim(), MarketButton.Type.PRIMARY));
            this.renameButton.visible = data.isOwner() && data.hasClaim();

            // Icon Selection
            int iconY = ownerY + 48;
            if (data.isOwner() && data.hasClaim()) {
                this.addRenderableWidget(new MarketButton(rightColX, iconY, 20, 18, Component.literal("<"), 
                    button -> { selectedIconIndex = (selectedIconIndex - 1 + ICONS.size()) % ICONS.size(); }, MarketButton.Type.PRIMARY));
                this.addRenderableWidget(new MarketButton(rightColX + 140, iconY, 20, 18, Component.literal(">"), 
                    button -> { selectedIconIndex = (selectedIconIndex + 1) % ICONS.size(); }, MarketButton.Type.PRIMARY));
                
                this.updateSaleButton = this.addRenderableWidget(new MarketButton(rightColX + 25, iconY + 22, 110, 18, 
                    Component.translatable("gui.flaneconomy.market.update"), 
                    button -> listOrUpdateClaim(), MarketButton.Type.SUCCESS));
                this.updateSaleButton.visible = data.forSale();
            }
        }
        
        // === BOTTOM: Navigation Buttons ===
        if (showClaim) {
            this.addRenderableWidget(new MarketButton(left + 20, top + PANEL_HEIGHT - 35, 110, 22, 
                Component.translatable("gui.flaneconomy.market.global_market"), 
                button -> ClientPlayNetworking.send(new RequestClaimMarketPayload()), MarketButton.Type.PRIMARY));

            this.addRenderableWidget(new MarketButton(left + 135, top + PANEL_HEIGHT - 35, 110, 22, 
                Component.translatable("gui.flaneconomy.market.my_claims"), 
                button -> ClientPlayNetworking.send(new RequestPlayerClaimsPayload()), MarketButton.Type.PRIMARY));

            this.addRenderableWidget(new MarketButton(left + FULL_PANEL_WIDTH - 100, top + PANEL_HEIGHT - 35, 80, 22, 
                Component.translatable("gui.flaneconomy.market.close"), 
                button -> this.onClose(), MarketButton.Type.DANGER));
        } else {
            // In mini mode, we stack or rearrange navigation
            int bottomY = top + 210;
            this.addRenderableWidget(new MarketButton(left + 20, bottomY, 160, 22, 
                Component.translatable("gui.flaneconomy.market.global_market"), 
                button -> ClientPlayNetworking.send(new RequestClaimMarketPayload()), MarketButton.Type.PRIMARY));
            
            this.addRenderableWidget(new MarketButton(left + 20, bottomY + 28, 160, 22, 
                Component.translatable("gui.flaneconomy.market.my_claims"), 
                button -> ClientPlayNetworking.send(new RequestPlayerClaimsPayload()), MarketButton.Type.PRIMARY));

            this.addRenderableWidget(new MarketButton(left + 50, top + PANEL_HEIGHT - 35, 100, 22, 
                Component.translatable("gui.flaneconomy.market.close"), 
                button -> this.onClose(), MarketButton.Type.DANGER));
        }
    }

    private void renderStatCardWidget(int x, int y, int w, int h, Component label, String value, int color) {
        // Dummy method to allow rendering through widgets if needed, 
        // but for now we just keep the rendering in render() method using values calculated in init.
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int left = (this.width - currentPanelWidth) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        boolean showClaim = shouldShowClaimSection();

        // Dark background layer
        graphics.fill(0, 0, this.width, this.height, 0xAA050505);
        
        // Main Panel
        graphics.fillGradient(left, top, left + currentPanelWidth, top + PANEL_HEIGHT, 0xEE1A1E2E, 0xEE121622);
        graphics.renderOutline(left, top, currentPanelWidth, PANEL_HEIGHT, 0x44D4AF37);
        
        // Header Title
        graphics.drawString(this.font, this.title, left + 20, top + 15, COLOR_GOLD, true);

        // === Stat Cards Rendering ===
        int cardY = top + 35;
        if (showClaim) {
            int cardWidth = (FULL_PANEL_WIDTH - 50) / 2;
            renderStatCard(graphics, left + 20, cardY, cardWidth, 35, 
                Component.translatable("gui.flaneconomy.market.coins").getString(), 
                String.valueOf(data.balance()), COLOR_GOLD);
            renderStatCard(graphics, left + FULL_PANEL_WIDTH - 20 - cardWidth, cardY, cardWidth, 35, 
                Component.translatable("gui.flaneconomy.market.blocks").getString(), 
                String.valueOf(data.claimBlocks()), COLOR_ACCENT);
        } else {
            int cardWidth = (MINI_PANEL_WIDTH - 30) / 2;
            renderStatCard(graphics, left + 10, cardY, cardWidth, 35, 
                Component.translatable("gui.flaneconomy.market.coins").getString(), 
                String.valueOf(data.balance()), COLOR_GOLD);
            renderStatCard(graphics, left + MINI_PANEL_WIDTH - 10 - cardWidth, cardY, cardWidth, 35, 
                Component.translatable("gui.flaneconomy.market.blocks").getString(), 
                String.valueOf(data.claimBlocks()), COLOR_ACCENT);
        }

        // Section Labels
        int leftColX = showClaim ? left + 20 : left + (currentPanelWidth - 150) / 2;
        graphics.drawString(this.font, Component.translatable("gui.flaneconomy.market.buy_blocks"), leftColX, top + 82, COLOR_SUBTEXT, false);

        if (showClaim) {
            int rightColX = left + 195;
            graphics.drawString(this.font, Component.translatable("gui.flaneconomy.market.current_claim"), rightColX, top + 82, COLOR_SUBTEXT, false);

            // Rate info
            graphics.drawString(this.font, Component.translatable("gui.flaneconomy.market.rate", FlanEconomyMod.CLAIM_BLOCK_PRICE), leftColX, top + 185, 0x66FFFFFF, false);

            // Claim Details Box
            int claimBoxY = top + 95;
            int claimBoxHeight = 60;
            
            graphics.fill(rightColX, claimBoxY, rightColX + 160, claimBoxY + claimBoxHeight, 0x33D4AF37);
            graphics.renderOutline(rightColX, claimBoxY, 160, claimBoxHeight, 0x44D4AF37);
            
            String name = data.claimName().isEmpty() ? "Unnamed Claim" : data.claimName();
            graphics.drawCenteredString(this.font, name, rightColX + 80, claimBoxY + 8, COLOR_TEXT);
            
            if (data.forSale()) {
                graphics.drawCenteredString(this.font, Component.translatable("gui.flaneconomy.market.price", data.salePrice()), rightColX + 80, claimBoxY + 24, COLOR_GOLD);
                graphics.drawCenteredString(this.font, Component.translatable("gui.flaneconomy.market.seller", data.sellerName()), rightColX + 80, claimBoxY + 40, COLOR_SUBTEXT);
                
                ItemStack iconStack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.iconId())));
                graphics.renderFakeItem(iconStack, rightColX + 140, claimBoxY + 4);
            } else {
                graphics.drawCenteredString(this.font, Component.translatable("gui.flaneconomy.market.not_for_sale"), rightColX + 80, claimBoxY + 30, COLOR_ERROR);
            }

            // Preview Selected Icon for owner
            if (data.isOwner() && data.hasClaim()) {
                graphics.drawString(this.font, Component.literal("Icon:"), rightColX + 25, top + 243, COLOR_SUBTEXT, false);
                ItemStack iconStack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(ICONS.get(selectedIconIndex))));
                graphics.renderFakeItem(iconStack, rightColX + 60, top + 238);
            }
        } else {
            // In mini mode, we can still show the rate info
            graphics.drawString(this.font, Component.translatable("gui.flaneconomy.market.rate", FlanEconomyMod.CLAIM_BLOCK_PRICE), leftColX, top + 185, 0x66FFFFFF, false);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderStatCard(GuiGraphics graphics, int x, int y, int w, int h, String label, String value, int color) {
        graphics.fill(x, y, x + w, y + h, 0x33000000);
        graphics.renderOutline(x, y, w, h, color & 0x44FFFFFF);
        graphics.drawString(this.font, label, x + 8, y + 6, COLOR_SUBTEXT, false);
        graphics.drawString(this.font, value, x + 8, y + 18, color, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void buyClaimBlocks(int amount) {
        ClientPlayNetworking.send(new BuyClaimBlocksPayload(amount));
    }

    private void buyClaim() {
        if (!data.forSale() || data.claimId() == null) {
            return;
        }
        ClientPlayNetworking.send(new BuyClaimPayload(data.claimId()));
    }

    private void listOrUpdateClaim() {
        if (data.claimId() == null) return;
        long price = 1000;
        try {
            price = Long.parseLong(priceEdit.getValue());
        } catch (NumberFormatException ignored) {}
        String iconId = ICONS.get(selectedIconIndex);
        ClientPlayNetworking.send(new ListClaimPayload(data.claimId(), price, iconId));
    }

    private void unlistClaim() {
        if (data.claimId() == null) return;
        ClientPlayNetworking.send(new UnlistClaimPayload(data.claimId()));
    }

    private void renameClaim() {
        if (data.claimId() == null || nameEdit.getValue().isEmpty()) return;
        ClientPlayNetworking.send(new RenameClaimPayload(data.claimId(), nameEdit.getValue()));
    }
}

