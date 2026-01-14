package com.warmpixel.bleedgore;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class BleedGoreConfig {
    // ===== Entity Filtering =====
    public boolean enablePlayers = true;
    public boolean enableMobs = true;
    public boolean enableBosses = true;
    public List<String> blacklistEntities = new ArrayList<>();
    public List<String> blacklistDamageSources = new ArrayList<>(List.of("starve", "drown", "freeze"));
    
    // ===== Features Toggle =====
    public boolean particlesEnabled = true;
    public boolean decalsEnabled = true;
    public boolean dotEnabled = true;
    public boolean lowHealthBleedingEnabled = true;  // Bleed more when low health
    public boolean directionalBloodEnabled = true;   // Blood sprays in direction of impact
    public boolean velocityTrailsEnabled = true;     // Blood trails based on velocity
    public boolean waterWashAwayEnabled = true;      // Blood fades faster in water/rain
    public boolean deathExplosionEnabled = true;     // Extra gore on death
    public boolean bloodSoundsEnabled = true;        // Sound effects for blood
    
    // ===== Particle Limits =====
    public int maxParticlesPerTick = 500;
    public int maxParticlesPerHit = 64;
    public int maxBloodEntitiesWorld = 256;  // Max blood entities in world at once
    
    // ===== Blood Visual Settings =====
    public float bloodColorR = 0.6f;
    public float bloodColorG = 0.02f;
    public float bloodColorB = 0.02f;
    public float bloodParticleScale = 0.65f;
    public float dropletParticleScale = 0.35f;
    public float decalParticleScale = 0.5f;
    
    // ===== Spray Physics =====
    public float spraySpread = 0.35f;
    public float spraySpeed = 0.06f;
    public float decalSpread = 0.12f;
    public float decalYOffset = 0.02f;
    public int maxDecalDrop = 4;
    public float bloodSprayDistance = 1.5f;  // How far blood sprays based on damage
    public float bloodGravity = 0.05f;       // Gravity affecting blood particles
    
    // ===== Damage-based Particle Scaling =====
    public float damageParticleMultiplier = 1.5f;   // More damage = more particles
    public float minDamageForBlood = 0.5f;          // Minimum damage to produce blood
    public float criticalHitMultiplier = 2.0f;      // Bonus particles for critical hits
    
    // ===== Hit Type Particle Counts =====
    public int meleeParticleCount = 14;
    public int projectileParticleCount = 10;
    public int explosionParticleCount = 24;
    public int fireParticleCount = 4;           // Reduced for fire damage
    public int fallParticleCount = 8;           // Fall damage blood
    
    public int meleeDropletCount = 6;
    public int projectileDropletCount = 4;
    public int explosionDropletCount = 10;
    public int fireDropletCount = 2;
    public int fallDropletCount = 4;
    
    public int meleeDecalCount = 5;
    public int projectileDecalCount = 4;
    public int explosionDecalCount = 8;
    public int fireDecalCount = 1;
    public int fallDecalCount = 6;
    
    public int meleeDecalChunkCount = 2;
    public int projectileDecalChunkCount = 2;
    public int explosionDecalChunkCount = 4;
    public int fireDecalChunkCount = 0;
    public int fallDecalChunkCount = 3;
    
    // ===== DOT (Damage Over Time) Bleeding =====
    public int dotDurationTicks = 40;
    public int dotIntervalTicks = 20;
    public float dotDamage = 0.5f;
    public float meleeDotMultiplier = 1.0f;
    public float projectileDotMultiplier = 0.8f;
    public float explosionDotMultiplier = 1.2f;
    public boolean refreshBleedOnHit = true;
    public int maxConcurrentBleeds = 256;
    
    // ===== Low Health Bleeding =====
    public float lowHealthThreshold = 0.5f;      // Start bleeding below 50% health
    public int lowHealthBleedInterval = 40;       // Base tick interval for low health bleeding
    public float lowHealthBleedIntensity = 1.0f;  // Multiplier for low health bleed particles
    
    // ===== Death Gore Effects =====
    public int deathGibCount = 8;                // Number of "chunks" on death
    public int deathBloodBurstCount = 20;        // Particles for blood burst on death
    public float deathBloodSpeed = 0.15f;        // Velocity of death blood spray
    public boolean deathDropOrgans = true;       // Drop chunk particles representing organs
    
    // ===== Water/Rain Effects =====
    public float waterFadeMultiplier = 3.0f;     // How much faster blood fades in water
    public float rainFadeMultiplier = 1.5f;      // How much faster blood fades in rain
    
    // ===== Custom Entity Blood Colors (Hex format without #) =====
    // Format: "entity_id" -> "RRGGBB"
    public Map<String, String> entityBloodColors = new HashMap<>(Map.ofEntries(
        // Green blood - insects and slimes
        Map.entry("minecraft:creeper", "66AA22"),
        Map.entry("minecraft:slime", "77DD44"),
        Map.entry("minecraft:spider", "446622"),
        Map.entry("minecraft:cave_spider", "336611"),
        Map.entry("minecraft:bee", "FFCC00"),
        Map.entry("minecraft:silverfish", "888866"),
        Map.entry("minecraft:endermite", "553366"),
        
        // Purple blood - ender mobs
        Map.entry("minecraft:enderman", "9922DD"),
        Map.entry("minecraft:ender_dragon", "CC44FF"),
        Map.entry("minecraft:shulker", "BB66CC"),
        
        // Black/gray blood - undead
        Map.entry("minecraft:wither", "222222"),
        Map.entry("minecraft:wither_skeleton", "333333"),
        Map.entry("minecraft:phantom", "445566"),
        
        // White/bone - skeletons
        Map.entry("minecraft:skeleton", "DDDDCC"),
        Map.entry("minecraft:stray", "AADDEE"),
        Map.entry("minecraft:bogged", "CCDDAA"),
        
        // Orange/fire - nether mobs
        Map.entry("minecraft:blaze", "FF6600"),
        Map.entry("minecraft:magma_cube", "FF4400"),
        Map.entry("minecraft:ghast", "FFEEEE"),
        Map.entry("minecraft:strider", "CC4422"),
        
        // Blue - water mobs
        Map.entry("minecraft:guardian", "4488BB"),
        Map.entry("minecraft:elder_guardian", "66AACC"),
        Map.entry("minecraft:squid", "223366"),
        Map.entry("minecraft:glow_squid", "44FFFF"),
        
        // Special
        Map.entry("minecraft:iron_golem", "888888"),
        Map.entry("minecraft:snow_golem", "FFFFFF"),
        Map.entry("minecraft:allay", "77DDFF")
    ));
    
    // ===== Solid Entities (no blood, bone/metal particles instead) =====
    public List<String> solidEntities = new ArrayList<>(List.of(
        "minecraft:iron_golem",
        "minecraft:snow_golem", 
        "minecraft:skeleton",
        "minecraft:wither_skeleton",
        "minecraft:stray",
        "minecraft:bogged"
    ));
}
