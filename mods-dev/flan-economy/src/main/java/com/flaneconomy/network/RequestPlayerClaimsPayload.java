package com.flaneconomy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestPlayerClaimsPayload() implements CustomPacketPayload {
    public static final Type<RequestPlayerClaimsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("flan_economy", "request_player_claims"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPlayerClaimsPayload> STREAM_CODEC = StreamCodec.unit(new RequestPlayerClaimsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
