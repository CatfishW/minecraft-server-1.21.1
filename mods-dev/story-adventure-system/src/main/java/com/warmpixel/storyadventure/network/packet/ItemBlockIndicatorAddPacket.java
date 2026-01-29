package com.warmpixel.storyadventure.network.packet;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet for adding an item/block indicator on the client.
 * Sent from server to client.
 */
public record ItemBlockIndicatorAddPacket(
    String id,
    double x,
    double y,
    double z,
    int color,
    String label,
    float circleRadius,
    boolean showArrow,
    boolean showCircle
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ItemBlockIndicatorAddPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "item_block_indicator_add"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemBlockIndicatorAddPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeUtf(packet.id);
            buf.writeDouble(packet.x);
            buf.writeDouble(packet.y);
            buf.writeDouble(packet.z);
            buf.writeInt(packet.color);
            buf.writeUtf(packet.label);
            buf.writeFloat(packet.circleRadius);
            buf.writeBoolean(packet.showArrow);
            buf.writeBoolean(packet.showCircle);
        },
        buf -> new ItemBlockIndicatorAddPacket(
            buf.readUtf(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readInt(),
            buf.readUtf(),
            buf.readFloat(),
            buf.readBoolean(),
            buf.readBoolean()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
    }
}
