package com.novus.auth.mixin;

import com.novus.auth.NovusAuth;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerPlayNetworkHandlerMixin {
    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void onChatMessage(ServerboundChatPacket packet, CallbackInfo ci) {
        if (!NovusAuth.getInstance().getAuthManager().isAuthenticated(((ServerGamePacketListenerImpl) (Object) this).player.getUUID())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleChatCommand", at = @At("HEAD"), cancellable = true)
    private void onCommandExecution(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        if (!NovusAuth.getInstance().getAuthManager().isAuthenticated(((ServerGamePacketListenerImpl) (Object) this).player.getUUID())) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void onPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (!NovusAuth.getInstance().getAuthManager().isAuthenticated(((ServerGamePacketListenerImpl) (Object) this).player.getUUID())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void onPlayerInteractBlock(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (!NovusAuth.getInstance().getAuthManager().isAuthenticated(((ServerGamePacketListenerImpl) (Object) this).player.getUUID())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void onPlayerInteractItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (!NovusAuth.getInstance().getAuthManager().isAuthenticated(((ServerGamePacketListenerImpl) (Object) this).player.getUUID())) {
            ci.cancel();
        }
    }
}
