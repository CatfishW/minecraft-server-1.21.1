package com.flaneconomy.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record TeleportToClaimPayload(UUID claimId) implements CustomPacketPayload {
    public static final Type<TeleportToClaimPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("flan_economy", "teleport_to_claim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportToClaimPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TeleportToClaimPayload::claimId,
            TeleportToClaimPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
