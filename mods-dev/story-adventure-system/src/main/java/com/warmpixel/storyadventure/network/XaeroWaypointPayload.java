package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for Xaero waypoint actions (START, ADD, REMOVE, END).
 */
public record XaeroWaypointPayload(Action action, String id, String name, double x, double y, double z, int color) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<XaeroWaypointPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "xaero_waypoint"));
    
    public static final StreamCodec<FriendlyByteBuf, XaeroWaypointPayload> STREAM_CODEC = 
        StreamCodec.of(XaeroWaypointPayload::write, XaeroWaypointPayload::read);

    public enum Action {
        START, ADD, REMOVE, END
    }

    private static void write(FriendlyByteBuf buf, XaeroWaypointPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeUtf(payload.id != null ? payload.id : "");
        buf.writeUtf(payload.name != null ? payload.name : "");
        buf.writeDouble(payload.x);
        buf.writeDouble(payload.y);
        buf.writeDouble(payload.z);
        buf.writeInt(payload.color);
    }
    
    private static XaeroWaypointPayload read(FriendlyByteBuf buf) {
        return new XaeroWaypointPayload(
            buf.readEnum(Action.class),
            buf.readUtf(),
            buf.readUtf(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static XaeroWaypointPayload start() {
        return new XaeroWaypointPayload(Action.START, "", "", 0, 0, 0, 0);
    }

    public static XaeroWaypointPayload add(String id, String name, double x, double y, double z, int color) {
        return new XaeroWaypointPayload(Action.ADD, id, name, x, y, z, color);
    }

    public static XaeroWaypointPayload remove(String id) {
        return new XaeroWaypointPayload(Action.REMOVE, id, "", 0, 0, 0, 0);
    }

    public static XaeroWaypointPayload end() {
        return new XaeroWaypointPayload(Action.END, "", "", 0, 0, 0, 0);
    }
}
