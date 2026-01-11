package com.flaneconomy.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RenameClaimPayload(UUID claimId, String name) implements CustomPacketPayload {
    public static final Type<RenameClaimPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("flan_economy", "rename_claim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameClaimPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RenameClaimPayload::claimId,
            ByteBufCodecs.STRING_UTF8, RenameClaimPayload::name,
            RenameClaimPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
