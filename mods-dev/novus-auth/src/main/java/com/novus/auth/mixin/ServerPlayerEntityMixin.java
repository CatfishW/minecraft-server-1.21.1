package com.novus.auth.mixin;

import com.novus.auth.NovusAuth;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin {
    private int novusAuth$reminderTick = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!NovusAuth.getInstance().getAuthManager().isAuthenticated(player.getUUID())) {
            // Block movement by freezing position via teleportation
            player.teleportTo(player.getX(), player.getY(), player.getZ());
            player.setDeltaMovement(0, 0, 0);

            // Send title reminder every 2 seconds (40 ticks)
            if (novusAuth$reminderTick++ % 40 == 0) {
                NovusAuth.getInstance().getAuthService().isRegistered(player.getUUID()).thenAccept(registered -> {
                    Component title = registered ? 
                        Component.translatable("novus_auth.gui.login.title") : 
                        Component.translatable("novus_auth.gui.register.title");
                    Component subtitle = registered ? 
                        Component.translatable("novus_auth.gui.login.prompt") : 
                        Component.translatable("novus_auth.gui.register.prompt");
                    
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
                });
            }
        }
    }
}
