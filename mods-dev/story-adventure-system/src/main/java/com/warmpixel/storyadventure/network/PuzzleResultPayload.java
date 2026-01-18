package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload to notify clients of puzzle results.
 */
public record PuzzleResultPayload(boolean success) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PuzzleResultPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "puzzle_result"));

    public static final StreamCodec<FriendlyByteBuf, PuzzleResultPayload> STREAM_CODEC =
        StreamCodec.of(PuzzleResultPayload::write, PuzzleResultPayload::read);

    private static void write(FriendlyByteBuf buf, PuzzleResultPayload payload) {
        buf.writeBoolean(payload.success);
    }

    private static PuzzleResultPayload read(FriendlyByteBuf buf) {
        return new PuzzleResultPayload(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
