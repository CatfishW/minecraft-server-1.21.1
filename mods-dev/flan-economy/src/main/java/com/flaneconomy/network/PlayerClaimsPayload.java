package com.flaneconomy.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record PlayerClaimsPayload(List<PlayerClaimEntry> claims) implements CustomPacketPayload {
    public static final Type<PlayerClaimsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("flan_economy", "player_claims"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerClaimsPayload> STREAM_CODEC = StreamCodec.composite(
            PlayerClaimEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), PlayerClaimsPayload::claims,
            PlayerClaimsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record PlayerClaimEntry(UUID claimId, String claimName, boolean forSale, long price, String iconId) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerClaimEntry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, PlayerClaimEntry::claimId,
                ByteBufCodecs.STRING_UTF8, PlayerClaimEntry::claimName,
                ByteBufCodecs.BOOL, PlayerClaimEntry::forSale,
                ByteBufCodecs.VAR_LONG, PlayerClaimEntry::price,
                ByteBufCodecs.STRING_UTF8, PlayerClaimEntry::iconId,
                PlayerClaimEntry::new
        );
    }
}
