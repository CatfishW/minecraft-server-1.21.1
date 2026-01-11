package com.flaneconomy.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record BuyClaimPayload(UUID claimId) implements CustomPacketPayload {
    public static final Type<BuyClaimPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("flan_economy", "buy_claim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyClaimPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> UUIDUtil.STREAM_CODEC.encode(buf, value.claimId()),
            buf -> new BuyClaimPayload(UUIDUtil.STREAM_CODEC.decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
