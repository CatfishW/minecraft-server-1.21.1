package com.warmpixel.storyadventure.core.event;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.instance.Instance;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Global event listener for story-related events in the game world.
 */
public class StoryEventListener {
    
    public static void register() {
         // Listen for entity deaths to track combat progress and kill objectives
        ServerLivingEntityEvents.AFTER_DEATH.register(StoryEventListener::onEntityDeath);
        
        // Listen for player deaths to track lives in instances
        ServerPlayerEvents.AFTER_RESPAWN.register(StoryEventListener::onPlayerRespawn);
        
        // Block interactions
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register(StoryEventListener::onAttackBlock);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(StoryEventListener::onUseBlock);
        
        // Projectile hits and player movement are handled via Mixins and Tick logic.
    }
    
    public static void onProjectileHit(net.minecraft.world.entity.projectile.Projectile projectile, net.minecraft.world.phys.BlockHitResult hitResult) {
        if (projectile.getOwner() instanceof ServerPlayer player) {
            handleWorldInteraction(player, "SHOOT_BLOCK", hitResult.getBlockPos());
        }
    }
    
    
    private static void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        // Only care about entities with our story tags
        for (String tag : entity.getTags()) {
            if (tag.startsWith("instance_")) {
                try {
                    String instanceIdStr = tag.substring(9);
                    UUID instanceId = UUID.fromString(instanceIdStr);
                    
                    Instance instance = StoryAdventureMod.getInstance().getInstanceManager().getInstance(instanceId);
                    if (instance != null) {
                        var currentNode = instance.getCurrentNode();
                        if (currentNode != null) {
                            var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(currentNode.getType());
                            if (handler != null) {
                                // Forward to handler
                                handler.onAction(instance, currentNode, null, "enemy_killed", entity);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore malformed tags
                }
            }
        }
    }
    
    private static void onPlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        // Check if player is in an active instance
        UUID playerId = newPlayer.getUUID();
        Instance instance = StoryAdventureMod.getInstance().getInstanceManager().getPlayerInstance(playerId);
        
        if (instance != null && instance.getStatus() == Instance.InstanceStatus.RUNNING) {
            StoryAdventureMod.LOGGER.info("[StoryEventListener] Player {} died in instance {}", 
                newPlayer.getName().getString(), instance.getInstanceId());
            
            // Increment death count and check for failure
            boolean failed = instance.incrementDeathCount();
            
            if (!failed) {
                // Update lives UI for all party members
                syncLivesUIToParty(instance);
            }
        }
    }
    
    private static void syncLivesUIToParty(Instance instance) {
        // Sync lives to all party members by triggering HUD update
        var currentNode = instance.getCurrentNode();
        if (currentNode != null) {
            var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(currentNode.getType());
            if (handler != null) {
                // Trigger HUD sync via handler (if supported)
                try {
                    handler.onAction(instance, currentNode, null, "sync_hud", null);
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.debug("[StoryEventListener] Handler doesn't support sync_hud action: {}", e.getMessage());
                }
            }
        }
    }
    
    public static net.minecraft.world.InteractionResult onAttackBlock(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level, net.minecraft.world.InteractionHand hand, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            handleWorldInteraction(serverPlayer, "BREAK_BLOCK", pos);
        }
        return net.minecraft.world.InteractionResult.PASS;
    }
    
    public static net.minecraft.world.InteractionResult onUseBlock(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            handleWorldInteraction(serverPlayer, "CLICK_BLOCK", hitResult.getBlockPos());
        }
        return net.minecraft.world.InteractionResult.PASS;
    }
    
    private static void handleWorldInteraction(ServerPlayer player, String type, net.minecraft.core.BlockPos pos) {
        // 1. Check if recording mode is active for this admin
        if (com.warmpixel.storyadventure.core.admin.AdminToolManager.isRecording(player)) {
            com.warmpixel.storyadventure.core.admin.AdminToolManager.recordInteraction(player, type, pos);
        }
        
        // 2. Flan Bypass for BREAK_BLOCK
        if ("BREAK_BLOCK".equals(type)) {
            tryBypassProtection(player, pos);
        }
        
        // 2. Check if player is in an active instance
        UUID playerId = player.getUUID();
        Instance instance = StoryAdventureMod.getInstance().getInstanceManager().getPlayerInstance(playerId);
        
        if (instance != null && instance.getStatus() == Instance.InstanceStatus.RUNNING) {
            var currentNode = instance.getCurrentNode();
            if (currentNode != null && currentNode.getType() == com.warmpixel.storyadventure.core.graph.NodeType.WORLD_INTERACTION) {
                var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(currentNode.getType());
                if (handler != null) {
                    // Create data map
                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    data.put("type", type);
                    data.put("pos", pos);
                    
                    handler.onAction(instance, currentNode, player, "world_interaction", data);
                }
            }
        }
    }

    private static void tryBypassProtection(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        // Only bypass if it's a block typically protected (like cobwebs)
        net.minecraft.world.level.block.state.BlockState state = player.serverLevel().getBlockState(pos);
        if (state.isAir()) return;

        Instance instance = StoryAdventureMod.getInstance().getInstanceManager().getPlayerInstance(player.getUUID());
        if (instance != null && instance.getStatus() == Instance.InstanceStatus.RUNNING) {
            var node = instance.getCurrentNode();
            if (node != null && node.getType() == com.warmpixel.storyadventure.core.graph.NodeType.WORLD_INTERACTION) {
                // Forward to handler as a 'test_break' action to see if it's a valid target
                // If it is, the handler will handle the destruction and progress
                var handler = com.warmpixel.storyadventure.core.node.NodeHandlers.getHandler(node.getType());
                if (handler != null) {
                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    data.put("type", "BREAK_BLOCK");
                    data.put("pos", pos);
                    data.put("force", true); // Tell handler to force the break
                    handler.onAction(instance, node, player, "world_interaction", data);
                }
            }
        }
    }
}
