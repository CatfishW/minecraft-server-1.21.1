package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload for syncing active waypoints to clients in a story instance.
 */
public record SyncWaypointsPayload(List<WaypointData> waypoints) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncWaypointsPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_waypoints"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncWaypointsPayload> STREAM_CODEC = 
        StreamCodec.of(SyncWaypointsPayload::write, SyncWaypointsPayload::read);

    private static void write(FriendlyByteBuf buf, SyncWaypointsPayload payload) {
        buf.writeVarInt(payload.waypoints.size());
        for (WaypointData wp : payload.waypoints) {
            buf.writeUtf(wp.id);
            buf.writeUtf(wp.label);
            buf.writeDouble(wp.x);
            buf.writeDouble(wp.y);
            buf.writeDouble(wp.z);
            buf.writeUtf(wp.icon);
            buf.writeInt(wp.color);
            buf.writeBoolean(wp.showDistance);
        }
    }
    
    private static SyncWaypointsPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<WaypointData> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new WaypointData(
                buf.readUtf(), buf.readUtf(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readUtf(), buf.readInt(), buf.readBoolean()
            ));
        }
        return new SyncWaypointsPayload(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public record WaypointData(String id, String label, double x, double y, double z, 
                                String icon, int color, boolean showDistance) {}
}
