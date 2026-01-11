package com.flaneconomy.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ListClaimPayload(UUID claimId, long price, String iconId) implements CustomPacketPayload {
    public static final Type<ListClaimPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("flan_economy", "list_claim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ListClaimPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ListClaimPayload::claimId,
            ByteBufCodecs.VAR_LONG, ListClaimPayload::price,
            ByteBufCodecs.STRING_UTF8, ListClaimPayload::iconId,
            ListClaimPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
