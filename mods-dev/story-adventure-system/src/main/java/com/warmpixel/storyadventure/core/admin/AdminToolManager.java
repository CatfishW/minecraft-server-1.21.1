package com.warmpixel.storyadventure.core.admin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manages admin tools, such as the interaction recorder.
 */
public class AdminToolManager {

    private static final Set<UUID> recordingAdmins = new HashSet<>();
    
    public static void setRecording(ServerPlayer player, boolean recording) {
        if (recording) {
            recordingAdmins.add(player.getUUID());
            player.sendSystemMessage(Component.literal("§a[StoryAdmin] Interaction recording ON."));
            player.sendSystemMessage(Component.literal("§7Interact with blocks (Left/Right click) to generate JSON."));
        } else {
            recordingAdmins.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("§e[StoryAdmin] Interaction recording OFF."));
        }
    }
    
    public static boolean isRecording(ServerPlayer player) {
        return recordingAdmins.contains(player.getUUID());
    }
    
    public static void recordInteraction(ServerPlayer player, String type, BlockPos pos) {
        // Generate JSON snippet
        String json = String.format(
            "{\n  \"type\": \"%s\",\n  \"x\": %d, \"y\": %d, \"z\": %d,\n  \"count\": 1,\n  \"feedback_msg\": \"Completed!\"\n}",
            type, pos.getX(), pos.getY(), pos.getZ()
        );
        
        Component message = Component.literal("§b[Recorded] §f" + type + " at " + pos.toShortString())
            .append(Component.literal(" §e[COPY]")
                .withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, json))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, 
                        Component.literal("Click to copy JSON")
                    ))
                ));
                
        player.sendSystemMessage(message);
    }
}
