package com.flaneconomy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public record ClaimShopPayload(
        long balance,
        int claimBlocks,
        boolean hasClaim,
        UUID claimId,
        String claimName,
        boolean forSale,
        long salePrice,
        String sellerName,
        boolean isOwner,
        String iconId
) implements CustomPacketPayload {
    public static final Type<ClaimShopPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("flan_economy", "claim_shop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimShopPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                ByteBufCodecs.VAR_LONG.encode(buf, value.balance());
                ByteBufCodecs.VAR_INT.encode(buf, value.claimBlocks());
                ByteBufCodecs.BOOL.encode(buf, value.hasClaim());
                if (value.hasClaim()) {
                    UUIDUtil.STREAM_CODEC.encode(buf, value.claimId());
                    ByteBufCodecs.STRING_UTF8.encode(buf, value.claimName());
                    ByteBufCodecs.BOOL.encode(buf, value.forSale());
                    if (value.forSale()) {
                        ByteBufCodecs.VAR_LONG.encode(buf, value.salePrice());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.sellerName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, value.iconId());
                    }
                    ByteBufCodecs.BOOL.encode(buf, value.isOwner());
                }
            },
            buf -> {
                long balance = ByteBufCodecs.VAR_LONG.decode(buf);
                int claimBlocks = ByteBufCodecs.VAR_INT.decode(buf);
                boolean hasClaim = ByteBufCodecs.BOOL.decode(buf);
                if (!hasClaim) {
                    return new ClaimShopPayload(balance, claimBlocks, false, null, "", false, 0L, "", false, "minecraft:grass_block");
                }
                UUID claimId = UUIDUtil.STREAM_CODEC.decode(buf);
                String claimName = ByteBufCodecs.STRING_UTF8.decode(buf);
                boolean forSale = ByteBufCodecs.BOOL.decode(buf);
                long salePrice = 0L;
                String sellerName = "";
                String iconId = "minecraft:grass_block";
                if (forSale) {
                    salePrice = ByteBufCodecs.VAR_LONG.decode(buf);
                    sellerName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    iconId = ByteBufCodecs.STRING_UTF8.decode(buf);
                }
                boolean isOwner = ByteBufCodecs.BOOL.decode(buf);
                return new ClaimShopPayload(balance, claimBlocks, true, claimId, claimName, forSale, salePrice, sellerName, isOwner, iconId);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
