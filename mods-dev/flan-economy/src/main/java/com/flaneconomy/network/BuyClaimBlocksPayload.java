package com.flaneconomy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BuyClaimBlocksPayload(int amount) implements CustomPacketPayload {
    public static final Type<BuyClaimBlocksPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("flan_economy", "buy_claim_blocks"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyClaimBlocksPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> ByteBufCodecs.VAR_INT.encode(buf, value.amount()),
            buf -> new BuyClaimBlocksPayload(ByteBufCodecs.VAR_INT.decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
