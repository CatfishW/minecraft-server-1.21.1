package com.warmpixel.economy.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShopTradeActionPayload(String offerId, TradeMode mode, int units) implements CustomPacketPayload {
    public static final Type<ShopTradeActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WarmPixelEconomyMod.MOD_ID, "trade_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopTradeActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, value.offerId());
                ByteBufCodecs.VAR_INT.encode(buf, value.mode().ordinal());
                ByteBufCodecs.VAR_INT.encode(buf, value.units());
            },
            buf -> new ShopTradeActionPayload(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    TradeMode.fromOrdinal(ByteBufCodecs.VAR_INT.decode(buf)),
                    ByteBufCodecs.VAR_INT.decode(buf)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
