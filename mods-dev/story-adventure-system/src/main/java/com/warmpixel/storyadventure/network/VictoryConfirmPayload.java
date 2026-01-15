package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server payload sent when a player confirms the victory screen
 * or when the countdown timer expires.
 */
public record VictoryConfirmPayload() implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<VictoryConfirmPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "victory_confirm"));
    
    public static final StreamCodec<FriendlyByteBuf, VictoryConfirmPayload> STREAM_CODEC = 
        StreamCodec.of(VictoryConfirmPayload::write, VictoryConfirmPayload::read);
    
    private static void write(FriendlyByteBuf buf, VictoryConfirmPayload payload) {
        // No data needed - server knows player's instance from their UUID
    }
    
    private static VictoryConfirmPayload read(FriendlyByteBuf buf) {
        return new VictoryConfirmPayload();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
