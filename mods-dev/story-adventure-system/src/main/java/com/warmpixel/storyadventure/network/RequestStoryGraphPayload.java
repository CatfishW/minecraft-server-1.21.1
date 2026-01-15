package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for requesting a story's JSON data from the server.
 */
public record RequestStoryGraphPayload(String storyId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestStoryGraphPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "request_story_graph"));
    
    public static final StreamCodec<FriendlyByteBuf, RequestStoryGraphPayload> STREAM_CODEC = 
        StreamCodec.of(RequestStoryGraphPayload::write, RequestStoryGraphPayload::read);

    private static void write(FriendlyByteBuf buf, RequestStoryGraphPayload payload) {
        buf.writeUtf(payload.storyId);
    }
    
    private static RequestStoryGraphPayload read(FriendlyByteBuf buf) {
        return new RequestStoryGraphPayload(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
