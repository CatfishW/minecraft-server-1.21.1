package com.flaneconomy.client;

import com.flaneconomy.network.BuyClaimPayload;
import com.flaneconomy.network.ClaimMarketPayload;
import com.flaneconomy.network.TeleportToClaimPayload;
import com.flaneconomy.network.RequestClaimMarketPayload;
import net.minecraft.client.gui.components.Tooltip;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ClaimMarketScreen extends Screen {
    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 300;
    private static final int COLOR_GOLD = 0xFFE6C875;
    private static final int COLOR_SUBTEXT = 0xFF8A96A8;

    private ClaimMarketPayload data;
    private MarketList list;

    public ClaimMarketScreen(ClaimMarketPayload data) {
        super(Component.translatable("gui.flaneconomy.market.global_title"));
        this.data = data;
    }

    public void updateData(ClaimMarketPayload data) {
        this.data = data;
        if (this.list != null) {
            this.list.updateEntries(data.entries());
        }
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        // Market List
        int listTop = top + 75;
        int listHeight = PANEL_HEIGHT - 120;
        this.list = new MarketList(this.minecraft, PANEL_WIDTH - 40, listHeight, listTop, 28);
        this.list.setX(left + 20);
        this.addRenderableWidget(this.list);
        this.list.updateEntries(data.entries());

        // Close Button
        this.addRenderableWidget(new MarketButton(left + PANEL_WIDTH / 2 - 50, top + PANEL_HEIGHT - 38, 100, 22, 
            Component.translatable("gui.flaneconomy.market.close"), 
            button -> this.onClose(), MarketButton.Type.DANGER));
        
        // Refresh Button
        this.addRenderableWidget(new MarketButton(left + PANEL_WIDTH - 85, top + 15, 65, 20, 
            Component.translatable("gui.flaneconomy.market.refresh"), 
            button -> ClientPlayNetworking.send(new RequestClaimMarketPayload()), MarketButton.Type.PRIMARY));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Disable default blur
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        graphics.fill(0, 0, this.width, this.height, 0xAA050505);
        graphics.fillGradient(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xEE1A1E2E, 0xEE121622);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0x44D4AF37);

        // Title
        graphics.drawString(this.font, this.title, left + 20, top + 18, COLOR_GOLD, true);

        // Balance Card
        renderStatCard(graphics, left + 20, top + 40, PANEL_WIDTH - 40, 28, 
            Component.translatable("gui.flaneconomy.market.coins").getString(), 
            String.valueOf(data.balance()), COLOR_GOLD);

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderStatCard(GuiGraphics graphics, int x, int y, int w, int h, String label, String value, int color) {
        graphics.fill(x, y, x + w, y + h, 0x33000000);
        graphics.renderOutline(x, y, w, h, color & 0x44FFFFFF);
        graphics.drawString(this.font, label + ": ", x + 8, y + 10, COLOR_SUBTEXT, false);
        int labelWidth = this.font.width(label + ": ");
        graphics.drawString(this.font, value, x + 8 + labelWidth, y + 10, color, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class MarketList extends ObjectSelectionList<MarketList.Entry> {
        public MarketList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        public void updateEntries(List<ClaimMarketPayload.MarketEntry> entries) {
            this.clearEntries();
            for (ClaimMarketPayload.MarketEntry entry : entries) {
                this.addEntry(new Entry(entry));
            }
        }
        
        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            // Background for the list
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x22000000);
            graphics.renderOutline(getX(), getY(), width, height, 0x22888888);
            super.renderWidget(graphics, mouseX, mouseY, delta);
        }

        private class Entry extends ObjectSelectionList.Entry<Entry> {
            private final ClaimMarketPayload.MarketEntry entry;
            private final MarketButton buyButton;
            private final MarketButton tpButton;

            public Entry(ClaimMarketPayload.MarketEntry entry) {
                this.entry = entry;
                long fee = (long) (entry.price() * 0.1);
                this.tpButton = new MarketButton(0, 0, 30, 18, 
                    Component.literal("TP"), 
                    button -> ClientPlayNetworking.send(new TeleportToClaimPayload(entry.claimId())), 
                    MarketButton.Type.PRIMARY);
                this.tpButton.setTooltip(Tooltip.create(Component.translatable("gui.flaneconomy.market.tp_warning", fee)));
                
                this.buyButton = new MarketButton(0, 0, 45, 18, 
                    Component.translatable("gui.flaneconomy.market.buy"), 
                    button -> ClientPlayNetworking.send(new BuyClaimPayload(entry.claimId())), 
                    MarketButton.Type.SUCCESS);
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
                // Background on hover
                if (hovered) {
                    graphics.fill(left, top, left + width, top + height - 2, 0x22FFFFFF);
                }
                
                // Icon
                ItemStack iconStack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.iconId())));
                graphics.renderFakeItem(iconStack, left + 4, top + 4);

                // Claim name (bold/bright)
                String name = entry.claimName().isEmpty() ? "Unnamed Claim" : entry.claimName();
                graphics.drawString(Minecraft.getInstance().font, name, left + 26, top + 4, 0xFFFFFF);
                
                // Seller name (subtext)
                graphics.drawString(Minecraft.getInstance().font, entry.sellerName(), left + 26, top + 14, 0x888888);
                
                // Price (gold, right-aligned before button)
                // Price (gold, right-aligned before buttons)
                String priceStr = String.valueOf(entry.price());
                int priceWidth = Minecraft.getInstance().font.width(priceStr);
                graphics.drawString(Minecraft.getInstance().font, priceStr, left + width - 95 - priceWidth, top + 9, COLOR_GOLD);

                // TP button
                tpButton.setX(left + width - 85);
                tpButton.setY(top + 4);
                tpButton.render(graphics, mouseX, mouseY, delta);

                // Buy button
                buyButton.setX(left + width - 50);
                buyButton.setY(top + 4);
                buyButton.render(graphics, mouseX, mouseY, delta);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                return tpButton.mouseClicked(mouseX, mouseY, button) || buyButton.mouseClicked(mouseX, mouseY, button);
            }

            @Override
            public Component getNarration() {
                return Component.literal(entry.claimName());
            }
        }
    }
}
