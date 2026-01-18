package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.core.event.StoryEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerGameMode.class)
public abstract class BlockBreakBypassMixin {
    
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
    public void onHandleBlockBreakAction(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int i, int j, CallbackInfo ci) {
        // When a player START_DESTROY_BLOCK, we check if it's a story target
        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            // Forward to StoryEventListener for potential protection bypass
            // This runs before most protection mods which usually hook into the same place or standard callbacks
            try {
                // We use a helper method in StoryEventListener specifically for this bypass
                // Using reflection or direct call if accessible
                com.warmpixel.storyadventure.core.event.StoryEventListener.onAttackBlock(player, player.serverLevel(), net.minecraft.world.InteractionHand.MAIN_HAND, pos, direction);
            } catch (Exception ignored) {}
        }
    }
}
