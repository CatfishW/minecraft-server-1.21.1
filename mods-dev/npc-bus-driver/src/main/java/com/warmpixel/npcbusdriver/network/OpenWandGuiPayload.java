package com.warmpixel.npcbusdriver.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record OpenWandGuiPayload(List<BlockPos> points) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenWandGuiPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("npcbusdriver", "open_wand_gui"));

    public static final StreamCodec<ByteBuf, OpenWandGuiPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeInt(payload.points.size());
            for (BlockPos pos : payload.points) {
                buf.writeLong(pos.asLong());
            }
        },
        buf -> {
            int size = buf.readInt();
            List<BlockPos> points = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                points.add(BlockPos.of(buf.readLong()));
            }
            return new OpenWandGuiPayload(points);
        }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
