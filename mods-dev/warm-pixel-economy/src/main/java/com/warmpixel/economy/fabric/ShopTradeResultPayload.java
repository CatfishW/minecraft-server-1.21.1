package com.warmpixel.economy.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ShopTradeResultPayload(boolean success, String messageKey, List<String> messageArgs, long balance) implements CustomPacketPayload {
    public static final Type<ShopTradeResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(WarmPixelEconomyMod.MOD_ID, "trade_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopTradeResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                ByteBufCodecs.BOOL.encode(buf, value.success());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.messageKey());
                encodeArgs(buf, value.messageArgs());
                ByteBufCodecs.VAR_LONG.encode(buf, value.balance());
            },
            buf -> new ShopTradeResultPayload(
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    decodeArgs(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encodeArgs(RegistryFriendlyByteBuf buf, List<String> args) {
        if (args == null || args.isEmpty()) {
            ByteBufCodecs.VAR_INT.encode(buf, 0);
            return;
        }
        ByteBufCodecs.VAR_INT.encode(buf, args.size());
        for (String arg : args) {
            ByteBufCodecs.STRING_UTF8.encode(buf, arg);
        }
    }

    private static List<String> decodeArgs(RegistryFriendlyByteBuf buf) {
        int size = ByteBufCodecs.VAR_INT.decode(buf);
        if (size <= 0) {
            return List.of();
        }
        List<String> args = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            args.add(ByteBufCodecs.STRING_UTF8.decode(buf));
        }
        return args;
    }
}
