package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for administrative story management actions.
 * Used to request data sync, reload stories, or validate them.
 */
public record AdminStoryActionPayload(Action action, String storyId) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<AdminStoryActionPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "admin_story_action"));
    
    public static final StreamCodec<FriendlyByteBuf, AdminStoryActionPayload> STREAM_CODEC = 
        StreamCodec.of(AdminStoryActionPayload::write, AdminStoryActionPayload::read);

    public enum Action {
        SYNC, RELOAD, VALIDATE, SET_SPAWN, SET_RETURN, TP_TO_SCENE, CREATE_TEMPLATE
    }

    private static void write(FriendlyByteBuf buf, AdminStoryActionPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeUtf(payload.storyId != null ? payload.storyId : "");
    }
    
    private static AdminStoryActionPayload read(FriendlyByteBuf buf) {
        return new AdminStoryActionPayload(
            buf.readEnum(Action.class),
            buf.readUtf()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
