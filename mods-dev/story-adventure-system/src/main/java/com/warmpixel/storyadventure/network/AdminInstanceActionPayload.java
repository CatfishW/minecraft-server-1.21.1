package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Payload for administrative instance management actions.
 */
public record AdminInstanceActionPayload(Action action, UUID instanceId) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<AdminInstanceActionPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "admin_instance_action"));
    
    public static final StreamCodec<FriendlyByteBuf, AdminInstanceActionPayload> STREAM_CODEC = 
        StreamCodec.of(AdminInstanceActionPayload::write, AdminInstanceActionPayload::read);

    public enum Action {
        SYNC, TERMINATE, PAUSE, RESUME
    }

    private static void write(FriendlyByteBuf buf, AdminInstanceActionPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeBoolean(payload.instanceId != null);
        if (payload.instanceId != null) {
            buf.writeUUID(payload.instanceId);
        }
    }
    
    private static AdminInstanceActionPayload read(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID instanceId = buf.readBoolean() ? buf.readUUID() : null;
        return new AdminInstanceActionPayload(action, instanceId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
