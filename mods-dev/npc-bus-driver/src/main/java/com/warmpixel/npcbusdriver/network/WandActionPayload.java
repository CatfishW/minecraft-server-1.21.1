package com.warmpixel.npcbusdriver.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

public record WandActionPayload(int actionType, int index, String vehicleId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WandActionPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("npcbusdriver", "wand_action"));

    public static final StreamCodec<ByteBuf, WandActionPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeInt(payload.actionType);
            buf.writeInt(payload.index);
            writeString(buf, payload.vehicleId);
        },
        buf -> new WandActionPayload(buf.readInt(), buf.readInt(), readString(buf))
    );

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readInt();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
