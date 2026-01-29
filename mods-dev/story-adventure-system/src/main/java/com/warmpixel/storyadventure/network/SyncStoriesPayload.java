package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload to sync available stories to the client.
 */
public record SyncStoriesPayload(List<StorySummary> stories) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<SyncStoriesPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_stories"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncStoriesPayload> STREAM_CODEC = 
        StreamCodec.of(SyncStoriesPayload::write, SyncStoriesPayload::read);
    
    public record StorySummary(String id, String name, String description, int minPlayers, int maxPlayers, int estimatedMinutes, String cover) {}
    
    private static void write(FriendlyByteBuf buf, SyncStoriesPayload payload) {
        buf.writeInt(payload.stories.size());
        for (StorySummary story : payload.stories) {
            buf.writeUtf(story.id);
            buf.writeUtf(story.name);
            buf.writeUtf(story.description);
            buf.writeInt(story.minPlayers);
            buf.writeInt(story.maxPlayers);
            buf.writeInt(story.estimatedMinutes);
            buf.writeUtf(story.cover != null ? story.cover : "");
        }
    }
    
    private static SyncStoriesPayload read(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<StorySummary> stories = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stories.add(new StorySummary(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf()
            ));
        }
        return new SyncStoriesPayload(stories);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
