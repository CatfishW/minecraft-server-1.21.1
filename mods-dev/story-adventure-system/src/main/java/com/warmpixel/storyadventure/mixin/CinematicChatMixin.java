package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide chat during cinematic cutscenes.
 */
@Mixin(ChatComponent.class)
public class CinematicChatMixin {
    
    /**
     * Hide chat messages during cutscenes.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideChat(GuiGraphics graphics, int tickCount, int mouseX, int mouseY, 
                                          boolean focused, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
}