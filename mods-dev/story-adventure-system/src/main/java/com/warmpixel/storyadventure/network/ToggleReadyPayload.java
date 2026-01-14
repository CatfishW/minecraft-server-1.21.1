package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload to toggle ready status in a lobby.
 */
public record ToggleReadyPayload(boolean ready) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<ToggleReadyPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "toggle_ready"));
    
    public static final StreamCodec<FriendlyByteBuf, ToggleReadyPayload> STREAM_CODEC = 
        StreamCodec.of(ToggleReadyPayload::write, ToggleReadyPayload::read);
    
    private static void write(FriendlyByteBuf buf, ToggleReadyPayload payload) {
        buf.writeBoolean(payload.ready);
    }
    
    private static ToggleReadyPayload read(FriendlyByteBuf buf) {
        return new ToggleReadyPayload(buf.readBoolean());
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
