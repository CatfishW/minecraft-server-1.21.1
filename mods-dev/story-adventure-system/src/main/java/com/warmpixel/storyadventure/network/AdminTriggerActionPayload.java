package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for admin trigger box actions (save, delete).
 */
public record AdminTriggerActionPayload(Action action, String id, String label, 
                                        double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ,
                                        String linkedNodeId) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<AdminTriggerActionPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "admin_trigger_action"));
    
    public static final StreamCodec<FriendlyByteBuf, AdminTriggerActionPayload> STREAM_CODEC = 
        StreamCodec.of(AdminTriggerActionPayload::write, AdminTriggerActionPayload::read);

    public enum Action {
        SAVE, DELETE, LIST
    }

    private static void write(FriendlyByteBuf buf, AdminTriggerActionPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeUtf(payload.id);
        buf.writeUtf(payload.label != null ? payload.label : "");
        buf.writeDouble(payload.minX);
        buf.writeDouble(payload.minY);
        buf.writeDouble(payload.minZ);
        buf.writeDouble(payload.maxX);
        buf.writeDouble(payload.maxY);
        buf.writeDouble(payload.maxZ);
        buf.writeUtf(payload.linkedNodeId != null ? payload.linkedNodeId : "");
    }
    
    private static AdminTriggerActionPayload read(FriendlyByteBuf buf) {
        return new AdminTriggerActionPayload(
            buf.readEnum(Action.class),
            buf.readUtf(),
            buf.readUtf(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readUtf()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
