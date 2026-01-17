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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 商店交易界面 - 完全重构版本
 */
public class ShopTradeScreen extends AbstractContainerScreen<ShopTradeMenu> {
    
    // 布局常量
    private static final int GUI_WIDTH = 200;
    private static final int GUI_HEIGHT = 260; // Increased to 260
    private static final int PADDING = 12;
    private static final int SLIDER_WIDTH = 140;
    private static final int SLIDER_HEIGHT = 8;
    
    // 状态
    private int units = 0;
    private int maxUnits = 0;
    private long balance;
    private int inventoryCount = 0;
    private boolean dragging = false;
    private boolean pending = false;
    private int tickCounter = 0;
    
    // UI组件
    private EditBox quantityBox;
    private ItemStack displayStack = ItemStack.EMPTY;
    
    // 消息
    private Component statusMessage = Component.empty();
    private boolean statusSuccess = true;
    private int statusTicks = 0;

    public ShopTradeScreen(ShopTradeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.balance = menu.balance();
    }

    @Override
    protected void init() {
        super.init();
        
        // 初始化显示物品
        refreshDisplayStack();
        
        // 初始化状态
        this.units = 0;
        refreshInventoryCount();
        updateMaxUnits();
        
        // 数量输入框
        int boxX = leftPos + (imageWidth - 80) / 2;
        int boxY = topPos + 160; // Moved down to 160
        quantityBox = new EditBox(font, boxX, boxY, 80, 18, Component.literal("0"));
        quantityBox.setMaxLength(4);
        quantityBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        quantityBox.setValue(String.valueOf(units));
        quantityBox.setResponder(this::onQuantityChanged);
        addRenderableWidget(quantityBox);
    }

    @Override
    protected void containerTick() {
        tickCounter++;
        
        // 每10 tick刷新一次背包数量
        if (tickCounter % 10 == 0) {
            refreshInventoryCount();
            updateMaxUnits();
        }
        
        // 状态消息淡出
        if (statusTicks > 0) {
            statusTicks--;
            if (statusTicks == 0) {
                statusMessage = Component.empty();
            }
        }
    }

    // ==================== 渲染 ====================

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 主面板
        int x = leftPos;
        int y = topPos;
        
        // 外边框
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF1A1D21);
        // 内部背景
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFF2D3139);
        
        // 顶部装饰条
        int headerColor = menu.mode() == TradeMode.BUY ? 0xFF2A5A8A : 0xFF4A8A5A;
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + 28, headerColor);
        
        // 渲染各个部分
        renderHeader(graphics, x, y);
        renderItemDisplay(graphics, x, y);
        renderPriceInfo(graphics, x, y);
        renderQuickButtons(graphics, x, y, mouseX, mouseY);
        renderSlider(graphics, x, y);
        renderActionButtons(graphics, x, y, mouseX, mouseY);
        renderStatus(graphics, x, y);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 不渲染默认标签
    }

    private void renderHeader(GuiGraphics graphics, int x, int y) {
        // 标题
        Component title = menu.mode() == TradeMode.BUY
                ? Component.translatable("ui.warm_pixel_economy.trade.title.buy")
                : Component.translatable("ui.warm_pixel_economy.trade.title.sell");
        
        String icon = menu.mode() == TradeMode.BUY ? ">> " : "<< ";
        String titleText = icon + title.getString();
        
        int titleX = x + (imageWidth - font.width(titleText)) / 2;
        graphics.drawString(font, titleText, titleX, y + 10, 0xFFFFFF, true);
    }

    private void renderItemDisplay(GuiGraphics graphics, int x, int y) {
        int itemAreaY = y + 25; // Moved up from 35
        
        // 物品背景框
        int boxX = x + (imageWidth - 24) / 2 - 4;
        graphics.fill(boxX, itemAreaY, boxX + 32, itemAreaY + 32, 0xFF1A1D21);
        graphics.fill(boxX + 1, itemAreaY + 1, boxX + 31, itemAreaY + 31, 0xFF3D4148);
        
        // 物品图标
        if (!displayStack.isEmpty()) {
            int itemX = x + (imageWidth - 16) / 2;
            graphics.renderItem(displayStack, itemX, itemAreaY + 8);
            
            // 显示数量
            int count = menu.offer().count();
            if (count > 1) {
                String countText = "x" + count;
                graphics.renderItemDecorations(font, displayStack, itemX, itemAreaY + 8, countText);
            }
        }
        
        // 物品名称
        if (!displayStack.isEmpty()) {
            Component itemName = displayStack.getHoverName();
            int nameX = x + (imageWidth - font.width(itemName)) / 2;
            graphics.drawString(font, itemName, nameX, itemAreaY + 36, 0xCCCCCC, false);
        }
    }

    private void renderPriceInfo(GuiGraphics graphics, int x, int y) {
        int infoY = y + 72; // Moved up from 82
        int leftX = x + PADDING;
        
        long unitPrice = calculateUnitPrice();
        long total = calculateTotal();
        long tax = calculateTax();
        
        // 分隔线
        graphics.fill(x + PADDING, infoY - 4, x + imageWidth - PADDING, infoY - 3, 0x44FFFFFF);
        
        // 余额
        String balanceLabel = "Balance: " + formatNumber(balance);
        graphics.drawString(font, balanceLabel, leftX, infoY, 0x7EB8E8, false);
        
        // 背包数量（仅出售模式）- Right Aligned on same line
        if (menu.mode() == TradeMode.SELL) {
            String invLabel = Component.translatable("ui.warm_pixel_economy.trade.inventory", inventoryCount).getString();
            int invLabelX = x + imageWidth - PADDING - font.width(invLabel);
            graphics.drawString(font, invLabel, invLabelX, infoY, 0xAAAAAA, false);
        }
        
        // 单价和税费
        infoY += 12; // Reduced spacing
        Component priceInfo = Component.translatable("ui.warm_pixel_economy.trade.unit_tax", 
                formatNumber(unitPrice), formatNumber(tax));
        graphics.drawString(font, priceInfo, leftX, infoY, 0xB0B0B0, false);
        
        // 总计/收款
        infoY += 12; // Reduced spacing
        if (menu.mode() == TradeMode.BUY) {
            Component totalLabel = Component.translatable("ui.warm_pixel_economy.trade.total", formatNumber(total));
            graphics.drawString(font, totalLabel, leftX, infoY, 0xFFD966, false);
        } else {
            Component payoutLabel = Component.translatable("ui.warm_pixel_economy.trade.payout", formatNumber(total));
            graphics.drawString(font, payoutLabel, leftX, infoY, 0x87E0A0, false);
        }
        
        // 警告信息
        infoY += 12; // Reduced spacing
        TradeValidation validation = validateTrade();
        if (!validation.valid && units > 0) {
            graphics.drawString(font, validation.message, leftX, infoY, 0xE07A7A, false);
        }
    }

    private void renderQuickButtons(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int buttonY = y + 120; // Adjusted Y
        int buttonWidth = 36;
        int buttonHeight = 14;
        int spacing = 4;
        int totalWidth = buttonWidth * 4 + spacing * 3;
        int startX = x + (imageWidth - totalWidth) / 2;
        
        // x1, x16, x32, 整组
        String[] labels = {"x1", "x16", "x32", 
                Component.translatable("ui.warm_pixel_economy.trade.stack").getString()};
        
        for (int i = 0; i < 4; i++) {
            int btnX = startX + i * (buttonWidth + spacing);
            boolean hover = isWithin(mouseX, mouseY, btnX, buttonY, buttonWidth, buttonHeight);
            renderButton(graphics, btnX, buttonY, buttonWidth, buttonHeight, labels[i], hover, true);
        }
        
        // 出售全部按钮（仅出售模式）
        if (menu.mode() == TradeMode.SELL) {
            int sellAllY = buttonY + 16; // Moved BELOW quick buttons
            int sellAllWidth = totalWidth;
            boolean hover = isWithin(mouseX, mouseY, startX, sellAllY, sellAllWidth, buttonHeight);
            String sellAllText = Component.translatable("ui.warm_pixel_economy.trade.sell_all").getString();
            renderButton(graphics, startX, sellAllY, sellAllWidth, buttonHeight, sellAllText, hover, true);
        }
    }

    private void renderSlider(GuiGraphics graphics, int x, int y) {
        int sliderX = x + (imageWidth - SLIDER_WIDTH) / 2;
        int sliderY = y + 195; // Moved down to 195 // Moved down to 185
        
        // 标签
        String qtyLabel = "x" + units + " / " + maxUnits;
        int labelX = x + (imageWidth - font.width(qtyLabel)) / 2;
        graphics.drawString(font, qtyLabel, labelX, sliderY - 12, 0xCCCCCC, false);
        
        // 轨道背景
        graphics.fill(sliderX - 1, sliderY - 1, sliderX + SLIDER_WIDTH + 1, sliderY + SLIDER_HEIGHT + 1, 0xFF1A1D21);
        graphics.fill(sliderX, sliderY, sliderX + SLIDER_WIDTH, sliderY + SLIDER_HEIGHT, 0xFF3D4148);
        
        // 填充进度
        float progress = maxUnits > 0 ? (float) units / maxUnits : 0;
        int fillWidth = (int) (SLIDER_WIDTH * progress);
        int fillColor = menu.mode() == TradeMode.BUY ? 0xFF3A7AAA : 0xFF5AAA7A;
        if (fillWidth > 0) {
            graphics.fill(sliderX, sliderY, sliderX + fillWidth, sliderY + SLIDER_HEIGHT, fillColor);
        }
        
        // 滑块手柄
        int handleWidth = 12;
        int handleX = sliderX + (int) ((SLIDER_WIDTH - handleWidth) * progress);
        graphics.fill(handleX, sliderY - 2, handleX + handleWidth, sliderY + SLIDER_HEIGHT + 2, 0xFF8A9AAA);
        graphics.fill(handleX + 1, sliderY - 1, handleX + handleWidth - 1, sliderY + SLIDER_HEIGHT + 1, 0xFFAABACA);
    }

    private void renderActionButtons(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int buttonWidth = 70;
        int buttonHeight = 20;
        int spacing = 16;
        int buttonY = y + 225; // Moved down to 225
        int startX = x + (imageWidth - buttonWidth * 2 - spacing) / 2;
        
        TradeValidation validation = validateTrade();
        boolean canConfirm = validation.valid && units > 0 && !pending;
        
        // 确认按钮
        int confirmX = startX;
        boolean hoverConfirm = isWithin(mouseX, mouseY, confirmX, buttonY, buttonWidth, buttonHeight);
        String confirmText = pending ? "..." : Component.translatable("ui.warm_pixel_economy.trade.confirm").getString();
        renderActionButton(graphics, confirmX, buttonY, buttonWidth, buttonHeight, confirmText, 
                hoverConfirm, canConfirm, true);
        
        // 取消按钮
        int cancelX = startX + buttonWidth + spacing;
        boolean hoverCancel = isWithin(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);
        String cancelText = Component.translatable("ui.warm_pixel_economy.trade.cancel").getString();
        renderActionButton(graphics, cancelX, buttonY, buttonWidth, buttonHeight, cancelText, 
                hoverCancel, true, false);
    }

    private void renderStatus(GuiGraphics graphics, int x, int y) {
        if (statusMessage != null && !statusMessage.getString().isEmpty()) {
            int statusY = y + imageHeight - 12;
            int statusX = x + (imageWidth - font.width(statusMessage)) / 2;
            int color = statusSuccess ? 0x87E0A0 : 0xE07A7A;
            graphics.drawString(font, statusMessage, statusX, statusY, color, false);
        }
    }

    private void renderButton(GuiGraphics graphics, int x, int y, int w, int h, String text, 
            boolean hover, boolean enabled) {
        int bgColor = enabled ? (hover ? 0xFF4A5A6A : 0xFF3A4A5A) : 0xFF2A2A2A;
        int borderColor = enabled ? 0xFF5A6A7A : 0xFF3A3A3A;
        
        graphics.fill(x, y, x + w, y + h, borderColor);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);
        
        int textColor = enabled ? 0xE0E0E0 : 0x808080;
        int textX = x + (w - font.width(text)) / 2;
        int textY = y + (h - 8) / 2;
        graphics.drawString(font, text, textX, textY, textColor, false);
    }

    private void renderActionButton(GuiGraphics graphics, int x, int y, int w, int h, String text,
            boolean hover, boolean enabled, boolean isConfirm) {
        int bgColor, borderColor;
        
        if (!enabled) {
            bgColor = 0xFF2A2E34;
            borderColor = 0xFF3A3E44;
        } else if (isConfirm) {
            bgColor = hover ? 0xFF3A8B5E : 0xFF2A6B4E;
            borderColor = hover ? 0xFF4AAB7E : 0xFF3A8B5E;
        } else {
            bgColor = hover ? 0xFF8B3A3A : 0xFF6B2A2A;
            borderColor = hover ? 0xFFAB5A5A : 0xFF8B3A3A;
        }
        
        graphics.fill(x, y, x + w, y + h, borderColor);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);
        
        // 高光
        graphics.fill(x + 1, y + 1, x + w - 1, y + 3, 0x22FFFFFF);
        
        int textColor = enabled ? 0xFFFFFF : 0x808080;
        int textX = x + (w - font.width(text)) / 2;
        int textY = y + (h - 8) / 2;
        graphics.drawString(font, text, textX, textY, textColor, false);
    }

    // ==================== 事件处理 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 快速选择按钮
        if (handleQuickButtons(mouseX, mouseY)) {
            return true;
        }
        
        // 主按钮
        if (handleActionButtons(mouseX, mouseY)) {
            return true;
        }
        
        // 滑块
        if (isOnSlider(mouseX, mouseY)) {
            dragging = true;
            updateSliderFromMouse(mouseX);
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            updateSliderFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleQuickButtons(double mouseX, double mouseY) {
        int y = topPos + 120; // Matches renderQuickButtons Y
        int buttonWidth = 36;
        int buttonHeight = 14;
        int spacing = 4;
        int totalWidth = buttonWidth * 4 + spacing * 3;
        int startX = leftPos + (imageWidth - totalWidth) / 2;
        
        // x1
        if (isWithin(mouseX, mouseY, startX, y, buttonWidth, buttonHeight)) {
            setUnits(1);
            return true;
        }
        // x16
        if (isWithin(mouseX, mouseY, startX + buttonWidth + spacing, y, buttonWidth, buttonHeight)) {
            setUnits(16);
            return true;
        }
        // x32
        if (isWithin(mouseX, mouseY, startX + (buttonWidth + spacing) * 2, y, buttonWidth, buttonHeight)) {
            setUnits(32);
            return true;
        }
        // 整组
        if (isWithin(mouseX, mouseY, startX + (buttonWidth + spacing) * 3, y, buttonWidth, buttonHeight)) {
            int perOffer = Math.max(1, menu.offer().count());
            setUnits(64 / perOffer);
            return true;
        }
        
        // 出售全部
        if (menu.mode() == TradeMode.SELL) {
            int sellAllY = y + 16; // Matches renderQuickButtons SellAll Y
            if (isWithin(mouseX, mouseY, startX, sellAllY, totalWidth, buttonHeight)) {
                setUnits(maxUnits);
                return true;
            }
        }
        
        return false;
    }

    private boolean handleActionButtons(double mouseX, double mouseY) {
        int buttonWidth = 70;
        int buttonHeight = 20;
        int spacing = 16;
        int buttonY = topPos + 225; // Match renderActionButtons
        int startX = leftPos + (imageWidth - buttonWidth * 2 - spacing) / 2;
        
        // 确认
        if (isWithin(mouseX, mouseY, startX, buttonY, buttonWidth, buttonHeight)) {
            attemptTrade();
            return true;
        }
        
        // 取消
        if (isWithin(mouseX, mouseY, startX + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight)) {
            onClose();
            return true;
        }
        
        return false;
    }

    private void onQuantityChanged(String text) {
        if (text.isEmpty()) {
            units = 0;
        } else {
            try {
                int value = Integer.parseInt(text);
                units = Mth.clamp(value, 0, maxUnits);
                if (value > maxUnits && quantityBox != null) {
                    quantityBox.setValue(String.valueOf(units));
                }
            } catch (NumberFormatException e) {
                units = 0;
            }
        }
    }

    private void updateSliderFromMouse(double mouseX) {
        int sliderX = leftPos + (imageWidth - SLIDER_WIDTH) / 2;
        float progress = (float) (mouseX - sliderX) / SLIDER_WIDTH;
        progress = Mth.clamp(progress, 0, 1);
        setUnits(Math.round(progress * maxUnits));
    }

    private boolean isOnSlider(double mouseX, double mouseY) {
        int sliderX = leftPos + (imageWidth - SLIDER_WIDTH) / 2;
        int sliderY = topPos + 195; // Match renderSlider
        return isWithin(mouseX, mouseY, sliderX - 5, sliderY - 5, SLIDER_WIDTH + 10, SLIDER_HEIGHT + 10);
    }

    // ==================== 交易逻辑 ====================

    private void attemptTrade() {
        if (pending) {
            return;
        }
        
        // 刷新并验证
        refreshInventoryCount();
        updateMaxUnits();
        
        TradeValidation validation = validateTrade();
        if (!validation.valid) {
            showStatus(validation.message, false);
            return;
        }
        
        if (units <= 0) {
            showStatus(Component.translatable("message.warm_pixel_economy.quantity_positive"), false);
            return;
        }
        
        // 发送交易请求
        pending = true;
        showStatus(Component.translatable("ui.warm_pixel_economy.trade.processing"), true);
        ClientPlayNetworking.send(new ShopTradeActionPayload(menu.offer().offerId(), menu.mode(), units));
    }

    public void handleResult(ShopTradeResultPayload payload) {
        pending = false;
        
        if (payload.messageKey() != null && !payload.messageKey().isBlank()) {
            showStatus(Component.translatable(payload.messageKey(), payload.messageArgs().toArray()), 
                    payload.success());
        }
        
        balance = payload.balance();
        
        // 刷新状态
        refreshInventoryCount();
        updateMaxUnits();
        
        // 交易成功后重置数量
        if (payload.success()) {
            setUnits(0);
        }
    }

    private TradeValidation validateTrade() {
        if (units <= 0) {
            return new TradeValidation(true, Component.empty()); // 0数量时不显示错误
        }
        
        int perOffer = Math.max(1, menu.offer().count());
        int requiredItems = perOffer * units;
        long requiredMoney = calculateTotal();
        
        if (menu.mode() == TradeMode.BUY) {
            // 购买验证
            if (balance < requiredMoney) {
                return new TradeValidation(false, 
                        Component.translatable("ui.warm_pixel_economy.trade.insufficient_funds"));
            }
            if (!menu.offer().infiniteStock() && menu.offer().stock() < requiredItems) {
                return new TradeValidation(false, 
                        Component.translatable("ui.warm_pixel_economy.trade.out_of_stock"));
            }
        } else {
            // 出售验证 - 关键修复
            if (inventoryCount < requiredItems) {
                return new TradeValidation(false, 
                        Component.translatable("ui.warm_pixel_economy.trade.missing_items"));
            }
        }
        
        return new TradeValidation(true, Component.empty());
    }

    // ==================== 辅助方法 ====================

    private void refreshDisplayStack() {
        if (minecraft != null && minecraft.level != null) {
            displayStack = ItemKeyFactory.stackFromSnbt(
                    menu.offer().itemJson(),
                    menu.offer().count(),
                    minecraft.level.registryAccess()
            );
        }
    }

    private void refreshInventoryCount() {
        inventoryCount = 0;
        
        if (minecraft == null || minecraft.player == null || displayStack.isEmpty()) {
            return;
        }
        
        for (ItemStack stack : minecraft.player.getInventory().items) {
            if (!stack.isEmpty() && isSameItem(stack, displayStack)) {
                inventoryCount += stack.getCount();
            }
        }
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.getItem() == b.getItem() && ItemStack.isSameItemSameComponents(a, b);
    }

    private void updateMaxUnits() {
        int perOffer = Math.max(1, menu.offer().count());
        int absoluteMax = 999;
        
        if (menu.mode() == TradeMode.BUY) {
            // 购买模式：受库存和余额限制
            int maxByStock = absoluteMax;
            if (!menu.offer().infiniteStock()) {
                maxByStock = menu.offer().stock() / perOffer;
            }
            
            long unitTotal = calculateUnitPrice() + Math.round(calculateUnitPrice() * menu.taxRate());
            int maxByBalance = unitTotal > 0 ? (int) (balance / unitTotal) : absoluteMax;
            
            // 计算背包空间限制
            int availableSpace = calculateAvailableSpace();
            int maxBySpace = availableSpace / perOffer;
            
            maxUnits = Math.max(0, Math.min(Math.min(maxByStock, maxByBalance), maxBySpace));
        } else {
            // 出售模式：受背包物品数量限制
            maxUnits = Math.max(0, inventoryCount / perOffer);
        }
        
        maxUnits = Math.min(maxUnits, absoluteMax);
        units = Mth.clamp(units, 0, maxUnits);
        
        if (quantityBox != null && !quantityBox.isFocused()) {
            quantityBox.setValue(String.valueOf(units));
        }
    }

    private void setUnits(int value) {
        units = Mth.clamp(value, 0, maxUnits);
        if (quantityBox != null) {
            quantityBox.setValue(String.valueOf(units));
        }
    }

    private long calculateUnitPrice() {
        if (menu.mode() == TradeMode.SELL) {
            return Math.max(1, (long) (menu.offer().price() * menu.sellRatio()));
        }
        return menu.offer().price();
    }

    private int calculateAvailableSpace() {
        if (minecraft == null || minecraft.player == null || displayStack.isEmpty()) {
            return 0;
        }

        Inventory inventory = minecraft.player.getInventory();
        int totalSpace = 0;
        int maxStackSize = Math.min(displayStack.getMaxStackSize(), inventory.getMaxStackSize());

        // 检查主背包 (0-35)
        for (int i = 0; i < 36; i++) {
            ItemStack slotStack = inventory.getItem(i);
            
            if (slotStack.isEmpty()) {
                totalSpace += maxStackSize;
            } else if (isSameItem(slotStack, displayStack)) {
                int space = maxStackSize - slotStack.getCount();
                if (space > 0) {
                    totalSpace += space;
                }
            }
        }
        
        return totalSpace;
    }

    private long calculateTax() {
        long baseTotal = calculateUnitPrice() * units;
        return Math.round(baseTotal * menu.taxRate());
    }

    private long calculateTotal() {
        long baseTotal = calculateUnitPrice() * units;
        long tax = calculateTax();
        return menu.mode() == TradeMode.SELL ? baseTotal - tax : baseTotal + tax;
    }

    private void showStatus(Component message, boolean success) {
        statusMessage = message;
        statusSuccess = success;
        statusTicks = 100; // 5秒后淡出
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    private boolean isWithin(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void removed() {
        super.removed();
        if (minecraft != null && minecraft.player != null) {
            String cmd = "shop " + menu.page() + (menu.category().isEmpty() ? "" : " " + menu.category());
            minecraft.player.connection.sendCommand(cmd);
        }
    }

    // ==================== 内部类 ====================

    private static class TradeValidation {
        final boolean valid;
        final Component message;
        
        TradeValidation(boolean valid, Component message) {
            this.valid = valid;
            this.message = message;
        }
    }
}