package com.warmpixel.storyadventure.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.warmpixel.storyadventure.client.cinematic.CinematicCameraController;
import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide HUD elements during cinematic cutscenes
 * and render letterbox/fade effects.
 */
@Mixin(Gui.class)
public class CinematicGuiMixin {
    
    @Shadow
    @Final
    private Minecraft minecraft;
    
    /**
     * Hide the main HUD group (hotbar, health, exp, etc.) during cutscenes.
     */
    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideHudGroup(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
    
    /**
     * Hide the crosshair during cutscenes.
     */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideExperienceBar(GuiGraphics graphics, int x, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderJumpMeter", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideJumpMeter(net.minecraft.world.entity.PlayerRideableJumping playerRideableJumping, GuiGraphics graphics, int x, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideVehicleHealth(GuiGraphics graphics, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideEffects(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideItemHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void storyadventure$hideSelectedItemName(GuiGraphics graphics, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            ci.cancel();
        }
    }
    

    /**
     * Render letterbox and fade effects after all other GUI elements.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void storyadventure$renderCinematicOverlay(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CinematicCameraController.getInstance().isActive()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400.0f); // Higher Z to ensure it's on top
            
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            
            com.warmpixel.storyadventure.client.render.CinematicOverlayRenderer.getInstance().onHudRender(graphics, deltaTracker);
            
            RenderSystem.disableBlend();
            graphics.pose().popPose();
        }
    }
}
