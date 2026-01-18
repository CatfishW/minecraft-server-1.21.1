package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class InstanceCommandProtectionMixin {

    @Shadow public ServerPlayer player;

    private static final List<String> ALLOWED_COMMANDS = Arrays.asList(
        "story", "storyui", "msg", "tell", "w", "say", "me", "teammsg", "tm"
    );

    @Inject(method = "handleChatCommand", at = @At("HEAD"), cancellable = true)
    private void storyadventure$onCommandExecution(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        // Skip if player is OP (permission level 2+)
        if (this.player.hasPermissions(2)) {
            return;
        }

        // Check if player is in an active instance
        if (StoryAdventureMod.getInstance().getInstanceManager().isPlayerInInstance(this.player.getUUID())) {
            String fullCommand = packet.command();
            String rootCommand = fullCommand.split(" ")[0].toLowerCase();

            // Check allowlist
            if (!ALLOWED_COMMANDS.contains(rootCommand)) {
                ci.cancel();
                this.player.sendSystemMessage(Component.literal("§c在故事实例中无法使用此命令！"));
            }
        }
    }
}
