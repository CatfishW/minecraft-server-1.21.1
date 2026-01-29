package com.warmpixel.storyadventure.network.packet;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet for displaying UI tutorial highlights on the client.
 * Supports highlighting specific UI elements like hotbar slots, inventory slots,
 * or custom screen regions with animated guides.
 * 
 * Sent from server to client.
 */
public record UITutorialPacket(
    String action,           // "show", "hide", "clear"
    String id,               // Unique identifier for this tutorial
    String elementType,      // "hotbar", "inventory", "screen_region", "key_hint"
    int elementIndex,        // For hotbar/inventory: slot index
    int screenX,             // For screen_region: X position (percentage 0-100)
    int screenY,             // For screen_region: Y position (percentage 0-100)
    int width,               // Width of highlight region
    int height,              // Height of highlight region
    String message,          // Tutorial message to display
    String keyHint,          // Key hint text (e.g., "R", "Space", "鼠标左键")
    int color,               // Highlight color (ARGB)
    boolean showArrow,       // Show animated arrow pointing to element
    boolean showPulse,       // Show pulsing glow effect
    boolean showClickHint,   // Show click animation
    int durationTicks,       // Duration in ticks (0 = until manually hidden)
    boolean requireClick     // Whether this tutorial requires a click to dismiss/proceed
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UITutorialPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "ui_tutorial"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UITutorialPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeUtf(packet.action);
            buf.writeUtf(packet.id);
            buf.writeUtf(packet.elementType);
            buf.writeInt(packet.elementIndex);
            buf.writeInt(packet.screenX);
            buf.writeInt(packet.screenY);
            buf.writeInt(packet.width);
            buf.writeInt(packet.height);
            buf.writeUtf(packet.message);
            buf.writeUtf(packet.keyHint);
            buf.writeInt(packet.color);
            buf.writeBoolean(packet.showArrow);
            buf.writeBoolean(packet.showPulse);
            buf.writeBoolean(packet.showClickHint);
            buf.writeInt(packet.durationTicks);
            buf.writeBoolean(packet.requireClick);
        },
        buf -> new UITutorialPacket(
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readInt(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readInt(),
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
