package com.warmpixel.storyadventure.network;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload to instruct the client to open a specific UI screen.
 */
public record OpenUIPayload(String screenType, String extraData) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<OpenUIPayload> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(StoryAdventureMod.MOD_ID, "open_ui"));
    
    public static final StreamCodec<FriendlyByteBuf, OpenUIPayload> STREAM_CODEC = 
        StreamCodec.of(OpenUIPayload::write, OpenUIPayload::read);
    
    // Screen type constants
    public static final String SCREEN_STORIES = "stories";
    public static final String SCREEN_LOBBY = "lobby";
    public static final String SCREEN_DIALOGUE = "dialogue";
    public static final String SCREEN_PUZZLE = "puzzle";
    public static final String SCREEN_ADMIN_DASHBOARD = "admin_dashboard";
    public static final String SCREEN_ADMIN_INSTANCES = "admin_instances";
    public static final String SCREEN_ADMIN_STORIES = "admin_stories";
    public static final String SCREEN_HUD_SHOW = "hud_show";
    public static final String SCREEN_HUD_HIDE = "hud_hide";
    public static final String SCREEN_VICTORY = "victory";
    
    private static void write(FriendlyByteBuf buf, OpenUIPayload payload) {
        buf.writeUtf(payload.screenType);
        buf.writeUtf(payload.extraData);
    }
    
    private static OpenUIPayload read(FriendlyByteBuf buf) {
        return new OpenUIPayload(buf.readUtf(), buf.readUtf());
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
