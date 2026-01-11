package com.warmpixel.economy.fabric;

import com.warmpixel.economy.core.ShopOffer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ShopOfferView(
        String offerId,
        String registryId,
        String itemHash,
        String itemJson,
        int count,
        long price,
        int stock,
        boolean infiniteStock,
        boolean buyEnabled,
        boolean sellEnabled,
        String category
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopOfferView> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, value.offerId());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.registryId());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.itemHash());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.itemJson());
                ByteBufCodecs.VAR_INT.encode(buf, value.count());
                ByteBufCodecs.VAR_LONG.encode(buf, value.price());
                ByteBufCodecs.VAR_INT.encode(buf, value.stock());
                ByteBufCodecs.BOOL.encode(buf, value.infiniteStock());
                ByteBufCodecs.BOOL.encode(buf, value.buyEnabled());
                ByteBufCodecs.BOOL.encode(buf, value.sellEnabled());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.category() == null ? "" : value.category());
            },
            buf -> new ShopOfferView(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
            )
    );

    public static ShopOfferView from(ShopOffer offer) {
        return new ShopOfferView(
                offer.offerId(),
                offer.registryId(),
                offer.itemHash(),
                offer.itemJson(),
                offer.count(),
                offer.price(),
                offer.stock(),
                offer.infiniteStock(),
                offer.buyEnabled(),
                offer.sellEnabled(),
                offer.category()
        );
    }
}
