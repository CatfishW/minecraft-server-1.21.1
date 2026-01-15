package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for syncing a full story JSON to the client for the graph editor.
 */
public record SyncStoryGraphPayload(String storyId, String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncStoryGraphPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_story_graph"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncStoryGraphPayload> STREAM_CODEC = 
        StreamCodec.of(SyncStoryGraphPayload::write, SyncStoryGraphPayload::read);

    private static void write(FriendlyByteBuf buf, SyncStoryGraphPayload payload) {
        buf.writeUtf(payload.storyId);
        buf.writeUtf(payload.json);
    }
    
    private static SyncStoryGraphPayload read(FriendlyByteBuf buf) {
        return new SyncStoryGraphPayload(buf.readUtf(), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
