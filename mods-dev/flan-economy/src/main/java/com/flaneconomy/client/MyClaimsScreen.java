package com.flaneconomy.client;

import com.flaneconomy.network.PlayerClaimsPayload;
import com.flaneconomy.network.RequestPlayerClaimsPayload;
import com.flaneconomy.network.TeleportToClaimPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class MyClaimsScreen extends Screen {
    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 300;
    private static final int COLOR_GOLD = 0xFFE6C875;
    private static final int COLOR_SUBTEXT = 0xFF8A96A8;
    private static final int COLOR_ACCENT = 0xFF8FD0FF;

    private PlayerClaimsPayload data;
    private ClaimList list;

    public MyClaimsScreen(PlayerClaimsPayload data) {
        super(Component.translatable("gui.flaneconomy.market.my_claims_title"));
        this.data = data;
    }

    public void updateData(PlayerClaimsPayload data) {
        this.data = data;
        if (this.list != null) {
            this.list.updateEntries(data.claims());
        }
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        // Claim List
        int listTop = top + 50;
        int listHeight = PANEL_HEIGHT - 90;
        this.list = new ClaimList(this.minecraft, PANEL_WIDTH - 40, listHeight, listTop, 32);
        this.list.setX(left + 20);
        this.addRenderableWidget(this.list);
        this.list.updateEntries(data.claims());

        // Close Button
        this.addRenderableWidget(new MarketButton(left + PANEL_WIDTH / 2 - 40, top + PANEL_HEIGHT - 35, 80, 22, 
            Component.translatable("gui.flaneconomy.market.close"), 
            button -> this.onClose(), MarketButton.Type.DANGER));
        
        // Refresh Button
        this.addRenderableWidget(new MarketButton(left + PANEL_WIDTH - 85, top + 15, 65, 20, 
            Component.translatable("gui.flaneconomy.market.refresh"), 
            button -> ClientPlayNetworking.send(new RequestPlayerClaimsPayload()), MarketButton.Type.PRIMARY));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        graphics.fill(0, 0, this.width, this.height, 0xAA050505);
        graphics.fillGradient(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xEE1A1E2E, 0xEE121622);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0x44D4AF37);

        // Title
        graphics.drawString(this.font, this.title, left + 20, top + 18, COLOR_ACCENT, true);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class ClaimList extends ObjectSelectionList<ClaimList.Entry> {
        public ClaimList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        public void updateEntries(List<PlayerClaimsPayload.PlayerClaimEntry> entries) {
            this.clearEntries();
            for (PlayerClaimsPayload.PlayerClaimEntry entry : entries) {
                this.addEntry(new Entry(entry));
            }
        }
        
        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(getX(), getY(), getX() + width, getY() + height, 0x22000000);
            graphics.renderOutline(getX(), getY(), width, height, 0x22888888);
            super.renderWidget(graphics, mouseX, mouseY, delta);
        }

        private class Entry extends ObjectSelectionList.Entry<Entry> {
            private final PlayerClaimsPayload.PlayerClaimEntry entry;
            private final MarketButton tpButton;

            public Entry(PlayerClaimsPayload.PlayerClaimEntry entry) {
                this.entry = entry;
                this.tpButton = new MarketButton(0, 0, 40, 18, 
                    Component.literal("TP"), 
                    button -> ClientPlayNetworking.send(new TeleportToClaimPayload(entry.claimId())), 
                    MarketButton.Type.PRIMARY);
                this.tpButton.setTooltip(Tooltip.create(Component.translatable("gui.flaneconomy.market.tp_own_warning", 888)));
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
                if (hovered) {
                    graphics.fill(left, top, left + width, top + height - 2, 0x22FFFFFF);
                }
                
                // Icon
                ItemStack iconStack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.iconId())));
                graphics.renderFakeItem(iconStack, left + 4, top + 8);

                // Claim name
                String name = entry.claimName();
                graphics.drawString(Minecraft.getInstance().font, name, left + 26, top + 6, 0xFFFFFF);
                
                // Status
                String status = entry.forSale() ? "Selling: " + entry.price() : "Private";
                int statusColor = entry.forSale() ? COLOR_GOLD : 0x888888;
                graphics.drawString(Minecraft.getInstance().font, status, left + 26, top + 18, statusColor);

                // TP button
                tpButton.setX(left + width - 50);
                tpButton.setY(top + 7);
                tpButton.render(graphics, mouseX, mouseY, delta);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                return tpButton.mouseClicked(mouseX, mouseY, button);
            }

            @Override
            public Component getNarration() {
                return Component.literal(entry.claimName());
            }
        }
    }
}
