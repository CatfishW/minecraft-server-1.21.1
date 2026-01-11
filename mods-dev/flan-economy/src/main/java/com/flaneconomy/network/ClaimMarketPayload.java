package com.flaneconomy.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record ClaimMarketPayload(long balance, List<MarketEntry> entries) implements CustomPacketPayload {
    public static final Type<ClaimMarketPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("flan_economy", "claim_market"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimMarketPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ClaimMarketPayload::balance,
            MarketEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), ClaimMarketPayload::entries,
            ClaimMarketPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record MarketEntry(UUID claimId, String claimName, long price, String sellerName, String iconId) {
        public static final StreamCodec<RegistryFriendlyByteBuf, MarketEntry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, MarketEntry::claimId,
                ByteBufCodecs.STRING_UTF8, MarketEntry::claimName,
                ByteBufCodecs.VAR_LONG, MarketEntry::price,
                ByteBufCodecs.STRING_UTF8, MarketEntry::sellerName,
                ByteBufCodecs.STRING_UTF8, MarketEntry::iconId,
                MarketEntry::new
        );
    }
}
