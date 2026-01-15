package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload to sync detailed story information to admin clients.
 */
public record SyncAdminStoriesPayload(List<AdminStoryInfo> stories) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<SyncAdminStoriesPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_admin_stories"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncAdminStoriesPayload> STREAM_CODEC = 
        StreamCodec.of(SyncAdminStoriesPayload::write, SyncAdminStoriesPayload::read);
    
    public record AdminStoryInfo(String id, String name, int nodeCount, String version, boolean valid, String errorMsg) {}
    
    private static void write(FriendlyByteBuf buf, SyncAdminStoriesPayload payload) {
        buf.writeInt(payload.stories.size());
        for (AdminStoryInfo story : payload.stories) {
            buf.writeUtf(story.id);
            buf.writeUtf(story.name);
            buf.writeInt(story.nodeCount);
            buf.writeUtf(story.version);
            buf.writeBoolean(story.valid);
            buf.writeUtf(story.errorMsg);
        }
    }
    
    private static SyncAdminStoriesPayload read(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<AdminStoryInfo> stories = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stories.add(new AdminStoryInfo(
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readUtf()
            ));
        }
        return new SyncAdminStoriesPayload(stories);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
