package com.flaneconomy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestClaimMarketPayload() implements CustomPacketPayload {
    public static final Type<RequestClaimMarketPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("flan_economy", "request_claim_market"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestClaimMarketPayload> STREAM_CODEC = StreamCodec.unit(new RequestClaimMarketPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
