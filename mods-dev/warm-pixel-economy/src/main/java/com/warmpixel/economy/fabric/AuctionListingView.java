package com.warmpixel.economy.fabric;

import com.warmpixel.economy.core.AuctionListing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record AuctionListingView(
        String listingId,
        String registryId,
        String itemHash,
        String itemJson,
        int count,
        long startingPrice,
        Long buyoutPrice,
        Long highestBid,
        long expiresAt
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionListingView> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, value.listingId());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.registryId());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.itemHash());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.itemJson());
                ByteBufCodecs.VAR_INT.encode(buf, value.count());
                ByteBufCodecs.VAR_LONG.encode(buf, value.startingPrice());
                encodeOptionalLong(buf, value.buyoutPrice());
                encodeOptionalLong(buf, value.highestBid());
                ByteBufCodecs.VAR_LONG.encode(buf, value.expiresAt());
            },
            buf -> new AuctionListingView(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    decodeOptionalLong(buf),
                    decodeOptionalLong(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf)
            )
    );

    public static AuctionListingView from(AuctionListing listing) {
        return new AuctionListingView(
                listing.listingId(),
                listing.registryId(),
                listing.itemHash(),
                listing.itemJson(),
                listing.count(),
                listing.startingPrice(),
                listing.buyoutPrice(),
                listing.highestBid(),
                listing.expiresAt()
        );
    }

    private static void encodeOptionalLong(RegistryFriendlyByteBuf buf, Long value) {
        ByteBufCodecs.BOOL.encode(buf, value != null);
        if (value != null) {
            ByteBufCodecs.VAR_LONG.encode(buf, value);
        }
    }

    private static Long decodeOptionalLong(RegistryFriendlyByteBuf buf) {
        boolean has = ByteBufCodecs.BOOL.decode(buf);
        if (!has) {
            return null;
        }
        return ByteBufCodecs.VAR_LONG.decode(buf);
    }
}
