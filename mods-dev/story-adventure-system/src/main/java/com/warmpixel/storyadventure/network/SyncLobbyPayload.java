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
 * Payload to sync lobby member status to all clients in the lobby.
 */
public record SyncLobbyPayload(List<MemberInfo> members) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<SyncLobbyPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "sync_lobby"));
    
    public static final StreamCodec<FriendlyByteBuf, SyncLobbyPayload> STREAM_CODEC = 
        StreamCodec.of(SyncLobbyPayload::write, SyncLobbyPayload::read);
    
    public record MemberInfo(UUID id, String name, boolean ready, boolean isLeader) {}
    
    private static void write(FriendlyByteBuf buf, SyncLobbyPayload payload) {
        buf.writeInt(payload.members.size());
        for (MemberInfo info : payload.members) {
            buf.writeUUID(info.id);
            buf.writeUtf(info.name);
            buf.writeBoolean(info.ready);
            buf.writeBoolean(info.isLeader);
        }
    }
    
    private static SyncLobbyPayload read(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<MemberInfo> members = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            members.add(new MemberInfo(buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readBoolean()));
        }
        return new SyncLobbyPayload(members);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
