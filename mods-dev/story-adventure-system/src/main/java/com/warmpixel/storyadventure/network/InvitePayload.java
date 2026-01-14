package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for party invitations.
 * C2S: Send invite to player name.
 * S2C: Receive invite from inviter name.
 */
public record InvitePayload(String name, boolean isResponse, boolean accept) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<InvitePayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "invite"));
    
    public static final StreamCodec<FriendlyByteBuf, InvitePayload> STREAM_CODEC = 
        StreamCodec.of(InvitePayload::write, InvitePayload::read);
    
    private static void write(FriendlyByteBuf buf, InvitePayload payload) {
        buf.writeUtf(payload.name);
        buf.writeBoolean(payload.isResponse);
        buf.writeBoolean(payload.accept);
    }
    
    private static InvitePayload read(FriendlyByteBuf buf) {
        return new InvitePayload(buf.readUtf(), buf.readBoolean(), buf.readBoolean());
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
