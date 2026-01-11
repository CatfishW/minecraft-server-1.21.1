package com.flaneconomy.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record UnlistClaimPayload(UUID claimId) implements CustomPacketPayload {
    public static final Type<UnlistClaimPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("flan_economy", "unlist_claim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnlistClaimPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, UnlistClaimPayload::claimId,
            UnlistClaimPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
