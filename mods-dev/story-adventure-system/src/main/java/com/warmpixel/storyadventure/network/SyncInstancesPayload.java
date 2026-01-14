package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Payload to sync the list of active instances to administrative clients.
 */
public record SyncInstancesPayload(List<InstanceInfo> instances) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<SyncInstancesPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_instances"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncInstancesPayload> STREAM_CODEC = 
        StreamCodec.of(SyncInstancesPayload::write, SyncInstancesPayload::read);
    
    public record InstanceInfo(UUID id, String storyName, String node, String status, int playerCount, long elapsed) {}
    
    private static void write(FriendlyByteBuf buf, SyncInstancesPayload payload) {
        buf.writeInt(payload.instances.size());
        for (InstanceInfo info : payload.instances) {
            buf.writeUUID(info.id);
            buf.writeUtf(info.storyName);
            buf.writeUtf(info.node);
            buf.writeUtf(info.status);
            buf.writeInt(info.playerCount);
            buf.writeLong(info.elapsed);
        }
    }
    
    private static SyncInstancesPayload read(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<InstanceInfo> instances = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            instances.add(new InstanceInfo(
                buf.readUUID(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readLong()
            ));
        }
        return new SyncInstancesPayload(instances);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
