package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for dialogue choices.
 */
public record DialogueChoicePayload(String choiceId) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<DialogueChoicePayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "dialogue_choice"));
    
    public static final StreamCodec<FriendlyByteBuf, DialogueChoicePayload> STREAM_CODEC = 
        StreamCodec.of(DialogueChoicePayload::write, DialogueChoicePayload::read);
    
    private static void write(FriendlyByteBuf buf, DialogueChoicePayload payload) {
        buf.writeUtf(payload.choiceId);
    }
    
    private static DialogueChoicePayload read(FriendlyByteBuf buf) {
        return new DialogueChoicePayload(buf.readUtf());
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
