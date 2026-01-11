package com.warmpixel.economy.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class ShopTradeMenu extends AbstractContainerMenu {
    private final ShopOfferView offer;
    private final TradeMode mode;
    private final long balance;
    private final double taxRate;
    private final String category;
    private final String query;
    private final int page;

    public ShopTradeMenu(int syncId, Inventory playerInventory, Data data) {
        this(syncId, data.offer(), data.mode(), data.balance(), data.taxRate(), data.category(), data.query(), data.page());
    }

    public ShopTradeMenu(int syncId, ShopOfferView offer, TradeMode mode, long balance, double taxRate,
                        String category, String query, int page) {
        super(ShopScreenHandlers.SHOP_TRADE, syncId);
        this.offer = offer;
        this.mode = mode;
        this.balance = balance;
        this.taxRate = taxRate;
        this.category = category;
        this.query = query;
        this.page = page;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public ShopOfferView offer() {
        return offer;
    }

    public TradeMode mode() {
        return mode;
    }

    public long balance() {
        return balance;
    }

    public double taxRate() {
        return taxRate;
    }

    public String category() {
        return category;
    }

    public String query() {
        return query;
    }

    public int page() {
        return page;
    }

    public Component title() {
        return mode == TradeMode.BUY
                ? Component.translatable("ui.warm_pixel_economy.trade.title.buy")
                : Component.translatable("ui.warm_pixel_economy.trade.title.sell");
    }

    public record Data(ShopOfferView offer, TradeMode mode, long balance, double taxRate,
                       String category, String query, int page) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    ShopOfferView.STREAM_CODEC.encode(buf, value.offer());
                    ByteBufCodecs.VAR_INT.encode(buf, value.mode().ordinal());
                    ByteBufCodecs.VAR_LONG.encode(buf, value.balance());
                    ByteBufCodecs.DOUBLE.encode(buf, value.taxRate());
                    ByteBufCodecs.STRING_UTF8.encode(buf, value.category());
                    ByteBufCodecs.STRING_UTF8.encode(buf, value.query());
                    ByteBufCodecs.VAR_INT.encode(buf, value.page());
                },
                buf -> new Data(
                        ShopOfferView.STREAM_CODEC.decode(buf),
                        TradeMode.fromOrdinal(ByteBufCodecs.VAR_INT.decode(buf)),
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.DOUBLE.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf)
                )
        );
    }
}
