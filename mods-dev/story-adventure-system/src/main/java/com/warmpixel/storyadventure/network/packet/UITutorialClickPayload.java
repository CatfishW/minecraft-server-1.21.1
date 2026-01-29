package com.warmpixel.storyadventure.network.packet;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server packet notifying that a tutorial element was clicked.
 */
public record UITutorialClickPayload(String tutorialId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UITutorialClickPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "ui_tutorial_click"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UITutorialClickPayload> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> buf.writeUtf(packet.tutorialId),
        buf -> new UITutorialClickPayload(buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(TYPE, STREAM_CODEC);
    }
}
