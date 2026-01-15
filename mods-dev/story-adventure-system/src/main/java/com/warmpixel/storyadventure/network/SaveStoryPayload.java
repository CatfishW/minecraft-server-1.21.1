package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for saving a story's JSON data back to the server.
 */
public record SaveStoryPayload(String storyId, String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SaveStoryPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "save_story"));
    
    public static final StreamCodec<FriendlyByteBuf, SaveStoryPayload> STREAM_CODEC = 
        StreamCodec.of(SaveStoryPayload::write, SaveStoryPayload::read);

    private static void write(FriendlyByteBuf buf, SaveStoryPayload payload) {
        buf.writeUtf(payload.storyId);
        buf.writeUtf(payload.json);
    }
    
    private static SaveStoryPayload read(FriendlyByteBuf buf) {
        return new SaveStoryPayload(buf.readUtf(), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
