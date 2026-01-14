package com.warmpixel.bleedgore;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BleedTracker {
    private static final Map<UUID, BleedInstance> BLEEDS = new ConcurrentHashMap<>();
    private static final Map<UUID, LowHealthBleedInstance> LOW_HEALTH_BLEEDS = new ConcurrentHashMap<>();

    private BleedTracker() {
    }

    public static void addOrRefresh(LivingEntity entity, BleedGoreConfig config, float damagePerTick) {
        if (config.maxConcurrentBleeds <= 0) {
            return;
        }
        if (BLEEDS.size() >= config.maxConcurrentBleeds) {
            return;
        }
        UUID id = entity.getUUID();
        BleedInstance instance = BLEEDS.get(id);
        if (instance == null) {
            instance = new BleedInstance(id, entity.level().dimension(), config.dotDurationTicks,
                config.dotIntervalTicks, damagePerTick);
            BLEEDS.put(id, instance);
        } else if (config.refreshBleedOnHit) {
            instance.ticksRemaining = config.dotDurationTicks;
            instance.intervalCounter = Math.max(1, config.dotIntervalTicks);
            instance.damagePerTick = damagePerTick;
            instance.dimension = entity.level().dimension();
        }
    }
    
    /**
     * Register an entity for low-health passive bleeding
     */
    public static void registerLowHealthEntity(LivingEntity entity, BleedGoreConfig config) {
        UUID id = entity.getUUID();
        if (!LOW_HEALTH_BLEEDS.containsKey(id)) {
            LOW_HEALTH_BLEEDS.put(id, new LowHealthBleedInstance(id, entity.level().dimension()));
        }
    }
    
    /**
     * Remove entity from all tracking
     */
    public static void removeEntity(LivingEntity entity) {
        UUID id = entity.getUUID();
        BLEEDS.remove(id);
        LOW_HEALTH_BLEEDS.remove(id);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, BleedInstance>> iterator = BLEEDS.entrySet().iterator();
        while (iterator.hasNext()) {
            BleedInstance instance = iterator.next().getValue();
            if (instance.ticksRemaining <= 0) {
                iterator.remove();
                continue;
            }
            ServerLevel level = server.getLevel(instance.dimension);
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (!(level.getEntity(instance.entityId) instanceof LivingEntity entity) || !entity.isAlive()) {
                iterator.remove();
                continue;
            }

            instance.ticksRemaining--;
            if (instance.ticksRemaining % 5 == 0) {
                BleedGoreMod.spawnBleedingEffect(entity);
            }
            instance.intervalCounter--;
            if (instance.intervalCounter <= 0) {
                instance.intervalCounter = Math.max(1, instance.intervalTicks);
                BleedGoreMod.applyBleedDamage(entity, instance.damagePerTick);
            }
        }
    }
    
    /**
     * Tick passive bleeding for low-health entities
     * Entities below the health threshold will periodically drip blood
     */
    public static void tickLowHealthBleeding(MinecraftServer server, BleedGoreConfig config) {
        Iterator<Map.Entry<UUID, LowHealthBleedInstance>> iterator = LOW_HEALTH_BLEEDS.entrySet().iterator();
        while (iterator.hasNext()) {
            LowHealthBleedInstance instance = iterator.next().getValue();
            
            ServerLevel level = server.getLevel(instance.dimension);
            if (level == null) {
                iterator.remove();
                continue;
            }
            
            if (!(level.getEntity(instance.entityId) instanceof LivingEntity entity) || !entity.isAlive()) {
                iterator.remove();
                continue;
            }
            
            float healthPercent = entity.getHealth() / entity.getMaxHealth();
            
            // Remove from tracking if health is above threshold
            if (healthPercent > config.lowHealthThreshold) {
                iterator.remove();
                continue;
            }
            
            // Update dimension if entity moved
            instance.dimension = entity.level().dimension();
            
            // Calculate bleed intensity based on how low health is
            // Lower health = more frequent bleeding
            float intensity = 1.0f - (healthPercent / config.lowHealthThreshold);
            
            // Calculate interval - lower health = shorter interval
            int interval = (int) (config.lowHealthBleedInterval * (1.0f - intensity * 0.7f));
            interval = Math.max(10, interval); // Minimum 10 ticks (0.5 seconds)
            
            instance.tickCounter++;
            if (instance.tickCounter >= interval) {
                instance.tickCounter = 0;
                BleedGoreMod.spawnLowHealthBleedEffect(entity, intensity);
            }
            
            // If in water or rain, fade faster (but still bleed)
            if (config.waterWashAwayEnabled && entity.isInWaterOrRain()) {
                // Reduced effect in water
                instance.tickCounter += (int)(interval * 0.3f);
            }
        }
    }

    private static class BleedInstance {
        private final UUID entityId;
        private ResourceKey<Level> dimension;
        private int ticksRemaining;
        private final int intervalTicks;
        private int intervalCounter;
        private float damagePerTick;

        private BleedInstance(UUID entityId, ResourceKey<Level> dimension, int ticksRemaining,
                              int intervalTicks, float damagePerTick) {
            this.entityId = entityId;
            this.dimension = dimension;
            this.ticksRemaining = ticksRemaining;
            this.intervalTicks = Math.max(1, intervalTicks);
            this.intervalCounter = Math.max(1, intervalTicks);
            this.damagePerTick = damagePerTick;
        }
    }
    
    private static class LowHealthBleedInstance {
        private final UUID entityId;
        private ResourceKey<Level> dimension;
        private int tickCounter = 0;
        
        private LowHealthBleedInstance(UUID entityId, ResourceKey<Level> dimension) {
            this.entityId = entityId;
            this.dimension = dimension;
        }
    }
}
