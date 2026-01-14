package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for puzzle inputs.
 */
public record PuzzleInputPayload(String input) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<PuzzleInputPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "puzzle_input"));
    
    public static final StreamCodec<FriendlyByteBuf, PuzzleInputPayload> STREAM_CODEC = 
        StreamCodec.of(PuzzleInputPayload::write, PuzzleInputPayload::read);
    
    private static void write(FriendlyByteBuf buf, PuzzleInputPayload payload) {
        buf.writeUtf(payload.input);
    }
    
    private static PuzzleInputPayload read(FriendlyByteBuf buf) {
        return new PuzzleInputPayload(buf.readUtf());
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
