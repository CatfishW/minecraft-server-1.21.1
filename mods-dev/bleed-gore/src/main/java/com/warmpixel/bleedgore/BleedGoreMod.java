package com.warmpixel.bleedgore;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class BleedGoreMod implements ModInitializer {
    public static final String MOD_ID = "bleed_gore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final TagKey<net.minecraft.world.entity.EntityType<?>> BOSS_TAG =
        TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("minecraft", "bosses"));

    private static BleedGoreConfig config;
    private static int particlesThisTick = 0;
    private static boolean applyingBleedDamage = false;
    private static final Random random = new Random();
    
    // Track last hit direction for directional blood sprays
    private static final Map<Integer, Vec3> lastHitDirections = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        config = ConfigManager.load(LOGGER);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            particlesThisTick = 0;
            if (config.dotEnabled) {
                BleedTracker.tick(server);
            }
            if (config.lowHealthBleedingEnabled) {
                BleedTracker.tickLowHealthBleeding(server, config);
            }
        });
        LOGGER.info("Bleed Gore initialized with realistic effects");
    }
    
    public static BleedGoreConfig getConfig() {
        return config;
    }

    public static void handleEntityDamaged(LivingEntity entity, DamageSource source, float amount) {
        if (applyingBleedDamage) return;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        if (amount < config.minDamageForBlood) return;
        if (!isEntityEnabled(entity)) return;
        if (isBlacklistedDamageSource(source)) return;
        if (isBlacklistedEntity(entity)) return;

        HitType hitType = classifyHitType(source);
        BloodType bloodType = getBloodType(entity);
        
        // Calculate impact direction for directional blood
        Vec3 impactDirection = calculateImpactDirection(entity, source);
        lastHitDirections.put(entity.getId(), impactDirection);
        
        // Scale particles based on damage
        float damageScale = Math.min(amount / 10.0f, 3.0f) * config.damageParticleMultiplier;
        
        // Check for critical hit (extra gore)
        boolean isCritical = source.getEntity() instanceof Player player && 
                            player.fallDistance > 0 && !player.onGround();

        if (config.particlesEnabled || config.decalsEnabled) {
            spawnBloodEffects(serverLevel, entity, hitType, bloodType, false, damageScale, 
                            isCritical, impactDirection);
        }
        
        // Play blood sound
        if (config.bloodSoundsEnabled && amount > 2.0f) {
            playBloodSound(serverLevel, entity, hitType);
        }

        if (config.dotEnabled) {
            float multiplier = switch (hitType) {
                case PROJECTILE -> config.projectileDotMultiplier;
                case EXPLOSION -> config.explosionDotMultiplier;
                case MELEE -> config.meleeDotMultiplier;
                default -> 1.0f;
            };
            float damagePerTick = config.dotDamage * multiplier;
            if (damagePerTick > 0.0f) {
                BleedTracker.addOrRefresh(entity, config, damagePerTick);
            }
        }
        
        // Register for low health bleeding
        if (config.lowHealthBleedingEnabled) {
            float healthPercent = entity.getHealth() / entity.getMaxHealth();
            if (healthPercent <= config.lowHealthThreshold) {
                BleedTracker.registerLowHealthEntity(entity, config);
            }
        }
    }

    public static void handleEntityDeath(LivingEntity entity, DamageSource source) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        if (!isEntityEnabled(entity)) return;
        if (isBlacklistedEntity(entity)) return;

        BloodType bloodType = getBloodType(entity);
        Vec3 impactDirection = lastHitDirections.getOrDefault(entity.getId(), entity.getLookAngle().reverse());
        
        if (config.deathExplosionEnabled) {
            spawnDeathExplosion(serverLevel, entity, bloodType, impactDirection);
        } else {
            spawnBloodEffects(serverLevel, entity, HitType.EXPLOSION, bloodType, true, 2.5f, 
                            false, impactDirection);
        }
        
        // Play death splat sound
        if (config.bloodSoundsEnabled) {
            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.MUD_FALL, SoundSource.NEUTRAL, 0.8f, 0.7f + random.nextFloat() * 0.3f);
        }
        
        // Cleanup tracking
        lastHitDirections.remove(entity.getId());
        BleedTracker.removeEntity(entity);
    }
    
    /**
     * Spawns a dramatic death explosion with gibs and blood burst
     */
    private static void spawnDeathExplosion(ServerLevel level, LivingEntity entity, 
                                            BloodType bloodType, Vec3 impactDirection) {
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5;
        double z = entity.getZ();
        
        ParticleOptions bloodParticle = getBloodParticle(bloodType, config.bloodParticleScale * 1.5f);
        ParticleOptions chunkParticle = new BlockParticleOption(ParticleTypes.BLOCK, bloodType.block.defaultBlockState());
        
        // Blood burst - directional spray opposite to impact
        int burstCount = Math.min(allowParticles(config.deathBloodBurstCount), config.deathBloodBurstCount);
        if (burstCount > 0) {
            for (int i = 0; i < burstCount; i++) {
                double spreadX = (random.nextDouble() - 0.5) * 0.8;
                double spreadY = random.nextDouble() * 0.5;
                double spreadZ = (random.nextDouble() - 0.5) * 0.8;
                
                double velX = -impactDirection.x * config.deathBloodSpeed + spreadX * 0.1;
                double velY = config.deathBloodSpeed * 0.5 + spreadY * 0.1;
                double velZ = -impactDirection.z * config.deathBloodSpeed + spreadZ * 0.1;
                
                level.sendParticles(bloodParticle, x + spreadX, y + spreadY, z + spreadZ,
                    0, velX, velY, velZ, 1.0);
            }
        }
        
        // Gibs/chunks flying out
        if (config.deathDropOrgans) {
            int gibCount = Math.min(allowParticles(config.deathGibCount), config.deathGibCount);
            for (int i = 0; i < gibCount; i++) {
                double spreadX = (random.nextDouble() - 0.5) * entity.getBbWidth();
                double spreadY = random.nextDouble() * entity.getBbHeight();
                double spreadZ = (random.nextDouble() - 0.5) * entity.getBbWidth();
                
                // Random outward velocity
                double velX = (random.nextDouble() - 0.5) * 0.3;
                double velY = random.nextDouble() * 0.2;
                double velZ = (random.nextDouble() - 0.5) * 0.3;
                
                level.sendParticles(chunkParticle, x + spreadX, y + spreadY, z + spreadZ,
                    0, velX, velY, velZ, 0.5);
            }
        }
        
        // Ground blood pool
        double groundY = findDecalY(level, entity);
        if (groundY >= 0) {
            ParticleOptions decalParticle = getBloodParticle(bloodType, config.decalParticleScale * 1.5f);
            int poolCount = Math.min(allowParticles(15), 15);
            level.sendParticles(decalParticle, x, groundY, z, poolCount,
                entity.getBbWidth() * 0.8, 0.02, entity.getBbWidth() * 0.8, 0.0);
        }
    }

    public static void spawnBleedingEffect(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        BloodType type = getBloodType(entity);
        
        // Dripping blood effect
        level.sendParticles(getBloodParticle(type, 0.4f),
            entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
            2, 0.15, 0.3, 0.15, 0.02);
        
        // Blood trail on ground if moving
        if (entity.getDeltaMovement().horizontalDistance() > 0.05) {
            double groundY = findDecalY(level, entity);
            if (groundY >= 0) {
                level.sendParticles(getBloodParticle(type, 0.3f),
                    entity.getX(), groundY, entity.getZ(),
                    1, 0.1, 0.01, 0.1, 0.0);
            }
        }
    }
    
    /**
     * Low-health passive bleeding effect
     */
    public static void spawnLowHealthBleedEffect(LivingEntity entity, float intensity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        BloodType type = getBloodType(entity);
        
        // Passive dripping blood
        int particleCount = Math.max(1, (int)(2 * intensity * config.lowHealthBleedIntensity));
        
        level.sendParticles(getBloodParticle(type, 0.3f),
            entity.getX(), entity.getY() + entity.getBbHeight() * 0.3, entity.getZ(),
            particleCount, 0.2, 0.2, 0.2, 0.01);
        
        // Occasional drip sound
        if (config.bloodSoundsEnabled && random.nextFloat() < 0.2f) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA, SoundSource.NEUTRAL, 
                0.3f, 0.8f + random.nextFloat() * 0.4f);
        }
    }

    public static void applyBleedDamage(LivingEntity entity, float damage) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        applyingBleedDamage = true;
        try {
            entity.hurt(serverLevel.damageSources().generic(), damage);
            spawnBleedingEffect(entity);
        } finally {
            applyingBleedDamage = false;
        }
    }
    
    private static Vec3 calculateImpactDirection(LivingEntity entity, DamageSource source) {
        if (source.getDirectEntity() != null) {
            return source.getDirectEntity().getLookAngle();
        } else if (source.getEntity() != null) {
            return source.getEntity().position().subtract(entity.position()).normalize();
        } else {
            // Random direction for environmental damage
            return new Vec3(random.nextDouble() - 0.5, 0.2, random.nextDouble() - 0.5).normalize();
        }
    }
    
    private static void playBloodSound(ServerLevel level, LivingEntity entity, HitType hitType) {
        float volume = switch (hitType) {
            case EXPLOSION -> 0.7f;
            case PROJECTILE -> 0.5f;
            default -> 0.4f;
        };
        
        var sound = random.nextBoolean() ? SoundEvents.MUD_HIT : SoundEvents.WET_GRASS_HIT;
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
            sound, SoundSource.NEUTRAL, volume, 0.8f + random.nextFloat() * 0.4f);
    }

    private static void spawnBloodEffects(ServerLevel level, LivingEntity entity, HitType hitType, 
                                         BloodType bloodType, boolean isDeath, float damageScale,
                                         boolean isCritical, Vec3 impactDirection) {
        float multiplier = isDeath ? 2.5f : damageScale;
        if (isCritical) multiplier *= config.criticalHitMultiplier;
        
        int particleCount = (int) (switch (hitType) {
            case PROJECTILE -> config.projectileParticleCount;
            case EXPLOSION -> config.explosionParticleCount;
            case MELEE -> config.meleeParticleCount;
            case FIRE -> config.fireParticleCount;
            case FALL -> config.fallParticleCount;
        } * multiplier);

        int dropletCount = (int) (switch (hitType) {
            case PROJECTILE -> config.projectileDropletCount;
            case EXPLOSION -> config.explosionDropletCount;
            case MELEE -> config.meleeDropletCount;
            case FIRE -> config.fireDropletCount;
            case FALL -> config.fallDropletCount;
        } * multiplier);

        int decalCount = (int) (switch (hitType) {
            case PROJECTILE -> config.projectileDecalCount;
            case EXPLOSION -> config.explosionDecalCount;
            case MELEE -> config.meleeDecalCount;
            case FIRE -> config.fireDecalCount;
            case FALL -> config.fallDecalCount;
        } * multiplier);

        int decalChunkCount = (int) (switch (hitType) {
            case PROJECTILE -> config.projectileDecalChunkCount;
            case EXPLOSION -> config.explosionDecalChunkCount;
            case MELEE -> config.meleeDecalChunkCount;
            case FIRE -> config.fireDecalChunkCount;
            case FALL -> config.fallDecalChunkCount;
        } * multiplier);

        int max = isDeath ? config.maxParticlesPerHit * 3 : config.maxParticlesPerHit;
        particleCount = Math.min(particleCount, max);
        dropletCount = Math.min(dropletCount, max);
        decalCount = Math.min(decalCount, max);
        decalChunkCount = Math.min(decalChunkCount, max);

        ParticleOptions bloodParticle = getBloodParticle(bloodType, config.bloodParticleScale);
        ParticleOptions dropletParticle = getBloodParticle(bloodType, config.dropletParticleScale);
        ParticleOptions chunkParticle = new BlockParticleOption(ParticleTypes.BLOCK, bloodType.block.defaultBlockState());
        ParticleOptions decalParticle = getBloodParticle(bloodType, config.decalParticleScale);

        // Directional blood spray
        if (config.directionalBloodEnabled && config.particlesEnabled && particleCount > 0) {
            int allowed = allowParticles(particleCount);
            if (allowed > 0) {
                double sprayX = entity.getX() - impactDirection.x * 0.3;
                double sprayY = entity.getY() + entity.getBbHeight() * 0.6;
                double sprayZ = entity.getZ() - impactDirection.z * 0.3;
                
                // Spray blood in opposite direction of impact
                float speed = config.spraySpeed * (isDeath ? 2.0f : 1.0f) * damageScale;
                level.sendParticles(bloodParticle, sprayX, sprayY, sprayZ,
                    allowed,
                    entity.getBbWidth() * config.spraySpread, 
                    entity.getBbHeight() * 0.4, 
                    entity.getBbWidth() * config.spraySpread,
                    speed);
            }
        } else if (config.particlesEnabled && particleCount > 0) {
            int allowed = allowParticles(particleCount);
            if (allowed > 0) {
                level.sendParticles(bloodParticle,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                    allowed,
                    entity.getBbWidth() * config.spraySpread, entity.getBbHeight() * 0.4, entity.getBbWidth() * config.spraySpread,
                    config.spraySpeed * (isDeath ? 2.0f : 1.0f));
            }
        }

        // Droplets
        if (config.particlesEnabled && dropletCount > 0) {
            int allowed = allowParticles(dropletCount);
            if (allowed > 0) {
                level.sendParticles(dropletParticle,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    allowed,
                    entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.3,
                    config.spraySpeed * 0.8);
            }
        }

        // Ground decals
        if (config.decalsEnabled && decalCount > 0) {
            int allowed = allowParticles(decalCount);
            if (allowed > 0) {
                double y = findDecalY(level, entity);
                if (y >= 0.0) {
                    level.sendParticles(decalParticle,
                        entity.getX(), y, entity.getZ(),
                        allowed,
                        entity.getBbWidth() * config.decalSpread * (isDeath ? 2.0 : 1.0), 0.02, entity.getBbWidth() * config.decalSpread * (isDeath ? 2.0 : 1.0),
                        0.0);
                }
            }
        }

        // Chunk/gib particles
        if (config.decalsEnabled && decalChunkCount > 0) {
            int allowed = allowParticles(decalChunkCount);
            if (allowed > 0) {
                double y = findDecalY(level, entity);
                if (y >= 0.0) {
                    level.sendParticles(chunkParticle,
                        entity.getX(), y, entity.getZ(),
                        allowed,
                        entity.getBbWidth() * 0.2, 0.02, entity.getBbWidth() * 0.2,
                        0.02);
                }
            }
        }
        
        // Extra gibs on death
        if (isDeath && config.particlesEnabled) {
            int gibCount = 5 + level.random.nextInt(5);
            level.sendParticles(chunkParticle,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                gibCount,
                entity.getBbWidth() * 0.5, entity.getBbHeight() * 0.5, entity.getBbWidth() * 0.5,
                0.15);
        }
    }

    private static ParticleOptions getBloodParticle(BloodType type, float scale) {
        return new DustParticleOptions(type.color, Math.max(0.01f, scale));
    }

    private static int allowParticles(int requested) {
        int remaining = Math.max(0, config.maxParticlesPerTick - particlesThisTick);
        int allowed = Math.min(requested, remaining);
        particlesThisTick += allowed;
        return allowed;
    }

    private static boolean isEntityEnabled(LivingEntity entity) {
        if (entity instanceof Player) return config.enablePlayers;
        if (isBoss(entity)) return config.enableBosses;
        return config.enableMobs;
    }
    
    private static boolean isBlacklistedEntity(LivingEntity entity) {
        String entityId = getEntityId(entity);
        return config.blacklistEntities.contains(entityId);
    }
    
    private static boolean isBlacklistedDamageSource(DamageSource source) {
        String sourceId = source.type().msgId();
        return config.blacklistDamageSources.contains(sourceId);
    }
    
    private static String getEntityId(LivingEntity entity) {
        if (entity instanceof Player) return "player";
        return entity.getType().builtInRegistryHolder().key().location().toString();
    }

    private static boolean isBoss(LivingEntity entity) {
        if (entity instanceof EnderDragon || entity instanceof WitherBoss) return true;
        return entity.getType().is(BOSS_TAG);
    }

    private static BloodType getBloodType(LivingEntity entity) {
        String entityId = getEntityId(entity);
        ResourceLocation id = entity.getType().builtInRegistryHolder().key().location();
        String path = id.getPath();
        
        // Check custom config colors first
        if (config.entityBloodColors.containsKey(entityId)) {
            String hexColor = config.entityBloodColors.get(entityId);
            try {
                float r = Integer.parseInt(hexColor.substring(0, 2), 16) / 255.0f;
                float g = Integer.parseInt(hexColor.substring(2, 4), 16) / 255.0f;
                float b = Integer.parseInt(hexColor.substring(4, 6), 16) / 255.0f;
                return new BloodType(new Vector3f(r, g, b), Blocks.REDSTONE_BLOCK);
            } catch (Exception e) {
                // Fall through to defaults
            }
        }
        
        // Check if solid entity (bone/metal particles)
        if (config.solidEntities.contains(entityId)) {
            return BloodType.UNDEAD_BONE;
        }
        
        // Default entity type detection
        if (entity.getType().is(TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("skeletons"))) || path.contains("skeleton") || path.contains("wither")) {
            return BloodType.UNDEAD_BONE;
        }
        if (path.contains("spider") || path.contains("silverfish") || path.contains("bee") || path.contains("creeper")) {
            return BloodType.INSECT;
        }
        if (path.contains("ender") || path.contains("shulker")) {
            return BloodType.ENDER;
        }
        if (path.contains("slime")) {
            return BloodType.SLIME;
        }
        if (path.contains("iron_golem") || path.contains("guardian")) {
            return BloodType.IRON;
        }
        if (path.contains("magma") || path.contains("blaze") || path.contains("ghast")) {
            return BloodType.FIRE;
        }
        
        return BloodType.NORMAL;
    }

    private static double findDecalY(ServerLevel level, LivingEntity entity) {
        if (config.maxDecalDrop <= 0) return -1.0;
        int x = Mth.floor(entity.getX());
        int z = Mth.floor(entity.getZ());
        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int entityY = Mth.floor(entity.getY());
        if (topY < entityY - config.maxDecalDrop) return -1.0;
        return Math.min(entity.getY(), topY + config.decalYOffset);
    }

    private static HitType classifyHitType(DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return HitType.EXPLOSION;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return HitType.PROJECTILE;
        if (source.is(DamageTypeTags.IS_FIRE)) return HitType.FIRE;
        if (source.is(DamageTypeTags.IS_FALL)) return HitType.FALL;
        return HitType.MELEE;
    }

    public enum HitType {
        MELEE, PROJECTILE, EXPLOSION, FIRE, FALL
    }

    private static class BloodType {
        static final BloodType NORMAL = new BloodType(new Vector3f(0.6f, 0.02f, 0.02f), Blocks.REDSTONE_BLOCK);
        static final BloodType UNDEAD_BONE = new BloodType(new Vector3f(0.85f, 0.85f, 0.8f), Blocks.BONE_BLOCK);
        static final BloodType INSECT = new BloodType(new Vector3f(0.4f, 0.6f, 0.1f), Blocks.SLIME_BLOCK);
        static final BloodType ENDER = new BloodType(new Vector3f(0.4f, 0.0f, 0.5f), Blocks.PURPUR_BLOCK);
        static final BloodType SLIME = new BloodType(new Vector3f(0.2f, 0.8f, 0.2f), Blocks.SLIME_BLOCK);
        static final BloodType IRON = new BloodType(new Vector3f(0.5f, 0.5f, 0.5f), Blocks.IRON_BLOCK);
        static final BloodType FIRE = new BloodType(new Vector3f(0.9f, 0.3f, 0.0f), Blocks.MAGMA_BLOCK);

        final Vector3f color;
        final net.minecraft.world.level.block.Block block;

        BloodType(Vector3f color, net.minecraft.world.level.block.Block block) {
            this.color = color;
            this.block = block;
        }
    }
}
