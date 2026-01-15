package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload for syncing trigger boxes to clients (for gizmo rendering).
 */
public record SyncTriggerBoxesPayload(List<TriggerBoxData> boxes) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncTriggerBoxesPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_trigger_boxes"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncTriggerBoxesPayload> STREAM_CODEC = 
        StreamCodec.of(SyncTriggerBoxesPayload::write, SyncTriggerBoxesPayload::read);

    private static void write(FriendlyByteBuf buf, SyncTriggerBoxesPayload payload) {
        buf.writeVarInt(payload.boxes.size());
        for (TriggerBoxData box : payload.boxes) {
            buf.writeUtf(box.id);
            buf.writeUtf(box.label);
            buf.writeDouble(box.minX);
            buf.writeDouble(box.minY);
            buf.writeDouble(box.minZ);
            buf.writeDouble(box.maxX);
            buf.writeDouble(box.maxY);
            buf.writeDouble(box.maxZ);
            buf.writeBoolean(box.hasPlayersInside);
        }
    }
    
    private static SyncTriggerBoxesPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<TriggerBoxData> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new TriggerBoxData(
                buf.readUtf(), buf.readUtf(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readBoolean()
            ));
        }
        return new SyncTriggerBoxesPayload(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public record TriggerBoxData(String id, String label,
                                  double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ,
                                  boolean hasPlayersInside) {}
}
