package com.warmpixel.economy.fabric.client;

import com.warmpixel.economy.fabric.ItemKeyFactory;
import com.warmpixel.economy.fabric.ShopTradeActionPayload;
import com.warmpixel.economy.fabric.ShopTradeMenu;
import com.warmpixel.economy.fabric.ShopTradeResultPayload;
import com.warmpixel.economy.fabric.TradeMode;
import com.warmpixel.economy.fabric.WarmPixelEconomyMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ShopTradeScreen extends AbstractContainerScreen<ShopTradeMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(WarmPixelEconomyMod.MOD_ID, "textures/gui/shop_trade.png");
    private static final int SLIDER_WIDTH = 116;
    private static final int SLIDER_HEIGHT = 6;

    private int openTicks = 0;
    private int units = 1;
    private int maxUnits = 1;
    private long balance;
    private boolean dragging = false;
    private boolean pending = false;
    private Component statusMessage = Component.empty();
    private boolean statusSuccess = true;
    private ItemStack cachedStack = ItemStack.EMPTY;

    public ShopTradeScreen(ShopTradeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 12;
        this.titleLabelY = 8;
        this.balance = menu.balance();
        updateLimits();
    }

    @Override
    protected void containerTick() {
        openTicks++;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);

        ItemStack displayStack = getOfferStack();
        if (!displayStack.isEmpty()) {
            int x = leftPos + 80;
            int y = topPos + 32;
            guiGraphics.renderItem(displayStack, x, y);
            guiGraphics.renderItemDecorations(font, displayStack, x, y);
        }

        drawSlider(guiGraphics, partialTick);
        drawButtons(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Enhanced header with coin icon and shadow
        String prefix = menu.mode() == TradeMode.BUY ? "◎ " : "💰 ";
        Component header = menu.mode() == TradeMode.BUY
                ? Component.translatable("ui.warm_pixel_economy.trade.title.buy")
                : Component.translatable("ui.warm_pixel_economy.trade.title.sell");
        String fullHeader = prefix + header.getString();
        guiGraphics.drawString(font, fullHeader, titleLabelX + 1, titleLabelY + 1, 0x3D2A10, false);
        guiGraphics.drawString(font, fullHeader, titleLabelX, titleLabelY, 0xFFD966, false);
        
        // Balance with styled coin icon
        String balanceStr = "◎ " + balance;
        guiGraphics.drawString(font, balanceStr, titleLabelX, titleLabelY + 14, 0x7EB8E8, false);
        
        // Decorative divider line
        guiGraphics.fill(titleLabelX, titleLabelY + 26, imageWidth - titleLabelX, titleLabelY + 27, 0x33FFFFFF);
        guiGraphics.fill(titleLabelX, titleLabelY + 27, imageWidth - titleLabelX, titleLabelY + 28, 0x22000000);

        long unitPrice = menu.mode() == TradeMode.SELL 
                ? Math.max(1, (long) (menu.offer().price() * 0.1)) 
                : menu.offer().price();
        int perOffer = Math.max(1, menu.offer().count());
        long baseTotal = unitPrice * units;
        long tax = Math.round(baseTotal * menu.taxRate());
        long total = menu.mode() == TradeMode.SELL ? baseTotal - tax : baseTotal + tax;

        // Unit price and tax line with icons
        Component unitTax = Component.translatable("ui.warm_pixel_economy.trade.unit_tax", unitPrice, tax).withStyle(s -> s.withColor(0xB7C0C8));
        guiGraphics.drawString(font, unitTax, titleLabelX, 70, 0xB7C0C8, false);
        
        // Total/Payout line with prominent styling
        Component totalLabel = menu.mode() == TradeMode.SELL 
                ? Component.translatable("ui.warm_pixel_economy.trade.payout", "◎" + total).withStyle(s -> s.withColor(0x87E0A0))
                : Component.translatable("ui.warm_pixel_economy.trade.total", "◎" + total).withStyle(s -> s.withColor(0xFFD966));
        guiGraphics.drawString(font, totalLabel, titleLabelX, 82, 0xFFD966, false);

        // Warning messages with icons
        if (menu.mode() == TradeMode.BUY && balance < requiredTotal()) {
            guiGraphics.drawString(font, Component.translatable("ui.warm_pixel_economy.trade.insufficient_funds").withStyle(s -> s.withColor(0xE07A7A)), titleLabelX, 96, 0xE07A7A, false);
        } else if (menu.mode() == TradeMode.BUY && !menu.offer().infiniteStock() && menu.offer().stock() < menu.offer().count() * units) {
            guiGraphics.drawString(font, Component.translatable("ui.warm_pixel_economy.trade.out_of_stock").withStyle(s -> s.withColor(0xE07A7A)), titleLabelX, 96, 0xE07A7A, false);
        }

        // Status message with icons
        if (statusMessage != null && !statusMessage.getString().isBlank()) {
            guiGraphics.drawString(font, statusMessage, titleLabelX, imageHeight - 16, statusSuccess ? 0x87E0A0 : 0xE07A7A, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleButtons(mouseX, mouseY)) {
            return true;
        }
        if (isOnSlider(mouseX, mouseY)) {
            dragging = true;
            updateSlider(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            updateSlider(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public void handleResult(ShopTradeResultPayload payload) {
        pending = false;
        statusSuccess = payload.success();
        if (payload.messageKey() == null || payload.messageKey().isBlank()) {
            statusMessage = Component.empty();
        } else {
            statusMessage = Component.translatable(payload.messageKey(), payload.messageArgs().toArray());
        }
        balance = payload.balance();
        updateLimits();
    }

    private void updateLimits() {
        ItemStack stack = getOfferStack();
        int perOffer = Math.max(1, menu.offer().count());
        int maxStackUnits = Math.max(1, stack.isEmpty() ? 1 : stack.getMaxStackSize() / perOffer);
        int maxByStock = maxStackUnits;
        if (!menu.offer().infiniteStock()) {
            maxByStock = Math.max(1, menu.offer().stock() / perOffer);
            maxByStock = Math.min(maxByStock, maxStackUnits);
        }
        int maxByBalance = maxByStock;
        if (menu.mode() == TradeMode.BUY) {
            long unitPrice = menu.offer().price();
            long unitTotal = unitPrice + Math.round(unitPrice * menu.taxRate());
            if (unitTotal > 0) {
                maxByBalance = (int) Math.max(1, Math.min(maxByStock, balance / unitTotal));
            }
        } else if (menu.mode() == TradeMode.SELL) {
            // No balance limit for selling, but we could add inventory limit here if needed.
            maxByBalance = maxByStock;
        }
        maxUnits = Math.max(1, maxByBalance);
        units = Mth.clamp(units, 1, maxUnits);
    }

    private ItemStack getOfferStack() {
        if (!cachedStack.isEmpty()) {
            return cachedStack;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return ItemStack.EMPTY;
        }
        cachedStack = ItemKeyFactory.stackFromSnbt(menu.offer().itemJson(), menu.offer().count(), client.level.registryAccess());
        return cachedStack;
    }

    private void drawSlider(GuiGraphics guiGraphics, float partialTick) {
        int sliderX = leftPos + 30;
        int sliderY = topPos + 120;
        
        // Enhanced slider track with border
        guiGraphics.fill(sliderX - 1, sliderY - 1, sliderX + SLIDER_WIDTH + 1, sliderY + SLIDER_HEIGHT + 1, 0xFF1A1E23);
        guiGraphics.fill(sliderX, sliderY, sliderX + SLIDER_WIDTH, sliderY + SLIDER_HEIGHT, 0xFF2A2E33);
        
        // Filled track portion (progress indicator)
        float progress = maxUnits <= 1 ? 0.0f : (float) (units - 1) / (float) (maxUnits - 1);
        int fillWidth = (int) (SLIDER_WIDTH * progress);
        int fillColor = menu.mode() == TradeMode.BUY ? 0xFF3A5A7A : 0xFF4A6B4A;
        if (fillWidth > 0) {
            guiGraphics.fill(sliderX, sliderY, sliderX + fillWidth, sliderY + SLIDER_HEIGHT, fillColor);
        }
        
        // Enhanced knob with glow effect
        int knobX = sliderX + (int) ((SLIDER_WIDTH - 10) * progress);
        float pulse = (float) Math.sin((openTicks + partialTick) / 4.0f);
        int glowAlpha = (int) (30 + 20 * pulse);
        int knobGlow = (glowAlpha << 24) | 0xFFD966;
        // Outer glow
        guiGraphics.fill(knobX - 2, sliderY - 5, knobX + 12, sliderY + SLIDER_HEIGHT + 5, knobGlow);
        // Knob border
        guiGraphics.fill(knobX - 1, sliderY - 4, knobX + 11, sliderY + SLIDER_HEIGHT + 4, 0xFF1A1E23);
        // Knob body with gradient simulation
        guiGraphics.fill(knobX, sliderY - 3, knobX + 10, sliderY + SLIDER_HEIGHT + 3, 0xFF6A7A8A);
        guiGraphics.fill(knobX + 1, sliderY - 2, knobX + 9, sliderY + SLIDER_HEIGHT + 2, 0xFF8A9AAA);
        // Highlight line
        guiGraphics.fill(knobX + 2, sliderY - 1, knobX + 8, sliderY, 0xAAFFFFFF);

        // Quantity display - cleaner format
        String amount = "x" + units;
        guiGraphics.drawString(font, amount, sliderX + SLIDER_WIDTH + 8, sliderY - 2, 0xFFD966, false);
    }

    private void drawButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Wider buttons with better spacing - centered in the GUI
        int buttonWidth = 64;
        int buttonHeight = 18;
        int spacing = 8;
        int totalWidth = buttonWidth * 2 + spacing;
        int startX = leftPos + (imageWidth - totalWidth) / 2;
        
        int confirmX = startX;
        int confirmY = topPos + 140;
        int cancelX = startX + buttonWidth + spacing;
        int cancelY = topPos + 140;

        boolean canAfford = menu.mode() == TradeMode.SELL || balance >= requiredTotal();
        boolean hasStock = menu.mode() == TradeMode.SELL || menu.offer().infiniteStock() || menu.offer().stock() >= menu.offer().count() * units;
        boolean canTrade = canAfford && hasStock;
        
        boolean hoverConfirm = isWithin(mouseX, mouseY, confirmX, confirmY, buttonWidth, buttonHeight);
        boolean hoverCancel = isWithin(mouseX, mouseY, cancelX, cancelY, buttonWidth, buttonHeight);

        // Confirm button
        int confirmColorOuter = pending || !canTrade ? 0xFF2A2E34 : (hoverConfirm ? 0xFF3A7B5E : 0xFF2A5B3E);
        int confirmColorInner = pending || !canTrade ? 0xFF3A3E44 : (hoverConfirm ? 0xFF4A9B7E : 0xFF3A7B5E);
        Component confirmLabel = pending ? Component.literal("...") : Component.translatable("ui.warm_pixel_economy.trade.confirm");
        drawEnhancedButton(guiGraphics, confirmX, confirmY, buttonWidth, buttonHeight, confirmColorOuter, confirmColorInner, confirmLabel);
        
        // Cancel button
        int cancelColorOuter = hoverCancel ? 0xFF7B3A3A : 0xFF5B2A2A;
        int cancelColorInner = hoverCancel ? 0xFF9B5A5A : 0xFF7B3A3A;
        drawEnhancedButton(guiGraphics, cancelX, cancelY, buttonWidth, buttonHeight, cancelColorOuter, cancelColorInner, Component.translatable("ui.warm_pixel_economy.trade.cancel"));

        // Quick selection buttons
        int quickY = topPos + 54;
        boolean hover1 = isWithin(mouseX, mouseY, leftPos + 24, quickY, 24, 14);
        boolean hover16 = isWithin(mouseX, mouseY, leftPos + 52, quickY, 24, 14);
        boolean hover32 = isWithin(mouseX, mouseY, leftPos + 80, quickY, 24, 14);
        boolean hoverStack = isWithin(mouseX, mouseY, leftPos + 108, quickY, 34, 14);
        
        drawEnhancedButton(guiGraphics, leftPos + 24, quickY, 24, 14, 
            hover1 ? 0xFF3A5A7A : 0xFF2A3A4A, hover1 ? 0xFF4A6A8A : 0xFF3A4A5A, Component.literal("x1"));
        drawEnhancedButton(guiGraphics, leftPos + 52, quickY, 24, 14, 
            hover16 ? 0xFF3A5A7A : 0xFF2A3A4A, hover16 ? 0xFF4A6A8A : 0xFF3A4A5A, Component.literal("x16"));
        drawEnhancedButton(guiGraphics, leftPos + 80, quickY, 24, 14, 
            hover32 ? 0xFF3A5A7A : 0xFF2A3A4A, hover32 ? 0xFF4A6A8A : 0xFF3A4A5A, Component.literal("x32"));
        drawEnhancedButton(guiGraphics, leftPos + 108, quickY, 34, 14, 
            hoverStack ? 0xFF3A5A7A : 0xFF2A3A4A, hoverStack ? 0xFF4A6A8A : 0xFF3A4A5A, Component.translatable("ui.warm_pixel_economy.trade.stack"));
    }

    private void drawEnhancedButton(GuiGraphics guiGraphics, int x, int y, int width, int height, int outerColor, int innerColor, Component label) {
        // Border/outer
        guiGraphics.fill(x, y, x + width, y + height, outerColor);
        // Inner gradient simulation
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, innerColor);
        // Top highlight
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x22FFFFFF);
        // Bottom shadow
        guiGraphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0x22000000);
        // Label
        int textX = x + (width - font.width(label)) / 2;
        int textY = y + (height - 8) / 2;
        guiGraphics.drawString(font, label, textX, textY, 0xF2E8D8, false);
    }

    @Override
    public void removed() {
        super.removed();
        if (minecraft != null && minecraft.player != null) {
            String cmd = "shop " + menu.page() + (menu.category().isEmpty() ? "" : " " + menu.category());
            minecraft.player.connection.sendCommand(cmd);
        }
    }

    private boolean handleButtons(double mouseX, double mouseY) {
        // Match the layout from drawButtons
        int buttonWidth = 64;
        int buttonHeight = 18;
        int spacing = 8;
        int totalWidth = buttonWidth * 2 + spacing;
        int startX = leftPos + (imageWidth - totalWidth) / 2;
        
        int confirmX = startX;
        int confirmY = topPos + 140;
        int cancelX = startX + buttonWidth + spacing;
        int cancelY = topPos + 140;
        
        if (isWithin(mouseX, mouseY, confirmX, confirmY, buttonWidth, buttonHeight)) {
            boolean canAfford = menu.mode() == TradeMode.SELL || balance >= requiredTotal();
            boolean hasStock = menu.mode() == TradeMode.SELL || menu.offer().infiniteStock() || menu.offer().stock() >= menu.offer().count() * units;
            if (!pending && canAfford && hasStock) {
                pending = true;
                statusMessage = Component.translatable("ui.warm_pixel_economy.trade.processing");
                statusSuccess = true;
                ClientPlayNetworking.send(new ShopTradeActionPayload(menu.offer().offerId(), menu.mode(), units));
            }
            return true;
        }
        if (isWithin(mouseX, mouseY, cancelX, cancelY, buttonWidth, buttonHeight)) {
            onClose();
            return true;
        }
        int quickY = topPos + 54;
        if (isWithin(mouseX, mouseY, leftPos + 24, quickY, 24, 14)) {
            units = 1;
            return true;
        }
        if (isWithin(mouseX, mouseY, leftPos + 52, quickY, 24, 14)) {
            int perOffer = Math.max(1, menu.offer().count());
            units = Math.min(maxUnits, Math.max(1, 16 / perOffer));
            return true;
        }
        if (isWithin(mouseX, mouseY, leftPos + 80, quickY, 24, 14)) {
            int perOffer = Math.max(1, menu.offer().count());
            units = Math.min(maxUnits, Math.max(1, 32 / perOffer));
            return true;
        }
        if (isWithin(mouseX, mouseY, leftPos + 108, quickY, 34, 14)) {
            units = maxUnits;
            return true;
        }
        return false;
    }

    private boolean isOnSlider(double mouseX, double mouseY) {
        int sliderX = leftPos + 30;
        int sliderY = topPos + 120;
        return isWithin(mouseX, mouseY, sliderX, sliderY - 4, SLIDER_WIDTH, SLIDER_HEIGHT + 8);
    }

    private void updateSlider(double mouseX) {
        int sliderX = leftPos + 30;
        float percent = (float) (mouseX - sliderX) / (float) (SLIDER_WIDTH - 8);
        percent = Mth.clamp(percent, 0.0f, 1.0f);
        int computed = 1 + Math.round(percent * (maxUnits - 1));
        units = Mth.clamp(computed, 1, maxUnits);
    }

    private boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private long requiredTotal() {
        long unitPrice = menu.mode() == TradeMode.SELL 
                ? Math.max(1, (long) (menu.offer().price() * 0.1)) 
                : menu.offer().price();
        long baseTotal = unitPrice * units;
        long tax = Math.round(baseTotal * menu.taxRate());
        return menu.mode() == TradeMode.SELL ? baseTotal - tax : baseTotal + tax;
    }
}
