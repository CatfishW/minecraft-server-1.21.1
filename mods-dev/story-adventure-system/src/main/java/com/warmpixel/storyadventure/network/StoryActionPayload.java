package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for story-related actions (Select, Start, Leave).
 */
public record StoryActionPayload(Action action, String data) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<StoryActionPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "story_action"));
    
    public static final StreamCodec<FriendlyByteBuf, StoryActionPayload> STREAM_CODEC = 
        StreamCodec.of(StoryActionPayload::write, StoryActionPayload::read);
    
    public enum Action {
        SELECT_STORY,
        START_ADVENTURE,
        LEAVE_PARTY,
        DISBAND_PARTY
    }
    
    private static void write(FriendlyByteBuf buf, StoryActionPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeUtf(payload.data);
    }
    
    private static StoryActionPayload read(FriendlyByteBuf buf) {
        return new StoryActionPayload(buf.readEnum(Action.class), buf.readUtf());
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
