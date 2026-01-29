package com.warmpixel.storyadventure.network.packet;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet for removing an item/block indicator on the client.
 * Sent from server to client.
 */
public record ItemBlockIndicatorRemovePacket(String id) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ItemBlockIndicatorRemovePacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "item_block_indicator_remove"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemBlockIndicatorRemovePacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ItemBlockIndicatorRemovePacket::id,
        ItemBlockIndicatorRemovePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
    }
}
