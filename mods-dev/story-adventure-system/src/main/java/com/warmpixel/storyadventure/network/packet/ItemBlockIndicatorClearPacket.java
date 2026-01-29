package com.warmpixel.storyadventure.network.packet;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet for clearing all item/block indicators on the client.
 * Sent from server to client.
 */
public record ItemBlockIndicatorClearPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ItemBlockIndicatorClearPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "item_block_indicator_clear"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemBlockIndicatorClearPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {},
        buf -> new ItemBlockIndicatorClearPacket()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
    }
}
