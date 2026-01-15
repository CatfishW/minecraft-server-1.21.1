package com.warmpixel.storyadventure.core.event;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.instance.Instance;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;

import java.util.UUID;

/**
 * Global event listener for story-related events in the game world.
 */
public class StoryEventListener {
    
    public static void register() {
         // Listen for entity deaths to track combat progress and kill objectives
        ServerLivingEntityEvents.AFTER_DEATH.register(StoryEventListener::onEntityDeath);
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
}
