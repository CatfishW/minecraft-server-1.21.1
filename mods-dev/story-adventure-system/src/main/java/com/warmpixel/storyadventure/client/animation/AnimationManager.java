package com.warmpixel.storyadventure.client.animation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnimationManager {
    private static AnimationManager instance;
    private final Map<ResourceLocation, AnimationDefinition> animations = new HashMap<>();
    private final Map<UUID, ActiveAnimation> activeAnimations = new HashMap<>();

    public static AnimationManager getInstance() {
        if (instance == null) {
            instance = new AnimationManager();
        }
        return instance;
    }

    public void load(ResourceManager resourceManager) {
        animations.clear();
        
        // 1. Load from Resource Packs (Built-in)
        Map<ResourceLocation, Resource> resources = resourceManager.listResources("animations", 
            path -> path.getPath().endsWith(".json"));

        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            Resource resource = entry.getValue();
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                AnimationDefinition def = new AnimationDefinition(json);
                
                String path = id.getPath();
                String name = path.substring("animations/".length(), path.length() - ".json".length());
                ResourceLocation animId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), name);
                
                animations.put(animId, def);
                StoryAdventureMod.LOGGER.info("Loaded built-in animation: {}", animId);
            } catch (Exception e) {
                StoryAdventureMod.LOGGER.error("Failed to load built-in animation: {}", id, e);
            }
        }

        // 2. Load from Filesystem (External / Hot Update)
        try {
            java.nio.file.Path configPath = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("storyadventure").resolve("animations");
            
            if (java.nio.file.Files.exists(configPath)) {
                try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(configPath)) {
                    stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                        try (Reader reader = java.nio.file.Files.newBufferedReader(p)) {
                            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                            AnimationDefinition def = new AnimationDefinition(json);
                            
                            String fileName = p.getFileName().toString();
                            String name = fileName.substring(0, fileName.length() - ".json".length());
                            ResourceLocation animId = ResourceLocation.fromNamespaceAndPath("storyadventure", name);
                            
                            animations.put(animId, def);
                            StoryAdventureMod.LOGGER.info("Loaded external animation: {}", animId);
                        } catch (Exception e) {
                            StoryAdventureMod.LOGGER.error("Failed to load external animation: {}", p, e);
                        }
                    });
                }
            } else {
                java.nio.file.Files.createDirectories(configPath);
                StoryAdventureMod.LOGGER.info("Created external animations directory: {}", configPath);
            }
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("Failed to process external animations directory", e);
        }
    }

    public void registerAnimation(ResourceLocation id, AnimationDefinition definition) {
        animations.put(id, definition);
    }

    public boolean hasAnimation(ResourceLocation id) {
        return animations.containsKey(id);
    }
    
    public java.util.Set<ResourceLocation> getAnimationIds() {
        return animations.keySet();
    }

    public void startAnimation(Entity entity, String animationId) {
        ResourceLocation id = ResourceLocation.tryParse(animationId);
        if (id == null) return;
        
        AnimationDefinition def = animations.get(id);
        if (def != null) {
            activeAnimations.put(entity.getUUID(), new ActiveAnimation(def, System.nanoTime()));
        } else {
            StoryAdventureMod.LOGGER.warn("Animation not found: {}", animationId);
        }
    }

    public void stopAnimation(Entity entity) {
        activeAnimations.remove(entity.getUUID());
    }

    public void tick() {
        // Remove finished animations
        activeAnimations.entrySet().removeIf(entry -> {
            ActiveAnimation anim = entry.getValue();
            if (anim.def.isLoop()) return false;
            
            float elapsed = (System.nanoTime() - anim.startTime) / 50_000_000f;
            return elapsed > anim.def.getLengthTicks();
        });
    }

    public boolean applyRotation(Entity entity, String boneName, ModelPart part) {
        ActiveAnimation anim = activeAnimations.get(entity.getUUID());
        if (anim == null) return false;

        float elapsed = (System.nanoTime() - anim.startTime) / 50_000_000f;
        if (anim.def.isLoop()) {
            elapsed %= anim.def.getLengthTicks();
        }

        BoneAnimation boneAnim = anim.def.getBone(boneName);
        if (boneAnim != null) {
            Vec3 rot = boneAnim.getRotationAt(elapsed);
            part.xRot = (float) rot.x;
            part.yRot = (float) rot.y;
            part.zRot = (float) rot.z;
            return true;
        }
        return false;
    }

    private static record ActiveAnimation(AnimationDefinition def, long startTime) {}
}
