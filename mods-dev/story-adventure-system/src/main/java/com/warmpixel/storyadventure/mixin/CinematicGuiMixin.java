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
        CinematicCameraController controller = CinematicCameraController.getInstance();
        
        if (!controller.isActive()) {
            return;
        }

        // Update interpolation state for the current frame
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        controller.renderTick(partialTicks);
        
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // Push pose to ensure high Z-index so it covers everything
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400.0f); // Higher Z to ensure it's on top of other HUD elements
        
        // Ensure blending is enabled for transparency (fades)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        // Render letterbox effect
        if (controller.isLetterboxEnabled() && controller.getLetterboxProgress() > 0f) {
            storyadventure$renderLetterbox(graphics, screenWidth, screenHeight, controller.getLetterboxProgress());
        }
        
        // Render fade effect
        if (controller.isFadeEnabled() && controller.getFadeProgress() > 0f) {
            storyadventure$renderFade(graphics, screenWidth, screenHeight, controller.getFadeProgress());
        }
        
        // Render skip hint if skippable
        if (controller.isSkippable() && controller.getProgress() > 0.05f) {
            storyadventure$renderSkipHint(graphics, screenWidth, screenHeight);
        }

        RenderSystem.disableBlend(); 
        graphics.pose().popPose();
    }
    
    @Unique
    private void storyadventure$renderLetterbox(GuiGraphics graphics, int width, int height, float progress) {
        // Standard cinematic ratio is 2.35:1, which means ~12.5% black bars on top/bottom
        // The progress is already smoothed in the controller, no need for extra smoothstep here
        int barHeight = (int) (height * 0.12f * progress);
        
        if (barHeight > 0) {
            // Top bar
            graphics.fill(0, -1, width, barHeight, 0xFF000000); // -1 to avoid gaps
            // Bottom bar
            graphics.fill(0, height - barHeight, width, height + 1, 0xFF000000); // +1 to avoid gaps
        }
    }
    
    @Unique
    private void storyadventure$renderFade(GuiGraphics graphics, int width, int height, float progress) {
        // Fade to/from black overlay
        int alpha = (int) (progress * 255f);
        alpha = Math.max(0, Math.min(255, alpha));
        
        if (alpha > 0) {
            int color = (alpha << 24); // Black with variable alpha
            graphics.fill(0, 0, width, height, color);
        }
    }
    
    @Unique
    private void storyadventure$renderSkipHint(GuiGraphics graphics, int width, int height) {
        // Don't render skip hint if a screen is open (dialogue, menu, etc.)
        if (minecraft.screen != null) return;
        
        CinematicCameraController controller = CinematicCameraController.getInstance();
        
        // Fade in the skip hint after 10% progress
        float hintAlpha = Math.min(1f, (controller.getProgress() - 0.1f) * 5f);
        
        // Pulse the hint
        float pulse = (float) (0.5f + 0.5f * Math.sin(System.currentTimeMillis() / 500.0));
        hintAlpha *= (0.3f + 0.2f * pulse);
        
        if (hintAlpha > 0.05f) {
            int alpha = (int) (hintAlpha * 255f);
            int textColor = 0xFFFFFF | (alpha << 24);
            
            String skipText = "Press ESC to skip";
            int textWidth = minecraft.font.width(skipText);
            int x = (width - textWidth) / 2;
            int y = height - 30;
            
            // Account for letterbox
            if (controller.isLetterboxEnabled()) {
                int barHeight = (int) (height * 0.125f * controller.getLetterboxProgress());
                y = height - barHeight - 15;
            }
            
            // Draw with shadow for visibility
            graphics.drawString(minecraft.font, skipText, x, y, textColor, true);
        }
    }
}