package com.warmpixel.storyadventure.core.node;

import com.warmpixel.storyadventure.StoryAdventureMod;
import com.warmpixel.storyadventure.core.graph.StageNode;
import com.warmpixel.storyadventure.instance.Instance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Handler for ITEM_INTERACTION nodes.
 * Supports vehicle boarding (buses, cars), item usage, and other entity interactions.
 */
public class ItemInteractionNodeHandler implements NodeHandler {
    
    // Use a flag name for tracking completion
    private static final String ITEM_INTERACTION_COMPLETE_FLAG = "item_interaction_complete";
    
    // Track spawned vehicle UUID per instance
    private static final java.util.Map<UUID, UUID> spawnedVehicles = new java.util.concurrent.ConcurrentHashMap<>();
    
    @Override
    public void onEnter(Instance instance, StageNode node) {
        String interactionType = node.getString("interaction_type", "ride_vehicle");
        String title = node.getString("title", "物品交互");
        String description = node.getString("description", "");
        
        StoryAdventureMod.LOGGER.info("[ItemInteractionNodeHandler] onEnter: instance={}, type={}, title={}", 
            instance.getInstanceId(), interactionType, title);
        
        // Reset completion flag
        instance.getState().setFlag(ITEM_INTERACTION_COMPLETE_FLAG, false);
        
        // Spawn the vehicle around the player if it's a ride_vehicle interaction
        if ("ride_vehicle".equals(interactionType)) {
            spawnVehicleAroundPlayer(instance, node);
        }
        
        // Notify players about the interaction
        syncHudWithInteraction(instance, node);

        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[任务] §f" + title));
                if (!description.isEmpty()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7" + description));
                }
            }
        }
    }
    
    private void syncHudWithInteraction(Instance instance, StageNode node) {
        String title = node.getString("title", "物品交互");
        String description = node.getString("description", "执行操作");
        
        // Build HUD data JSON
        StringBuilder hudJson = new StringBuilder();
        hudJson.append("{");
        hudJson.append("\"title\":\"").append(escapeJson(instance.getGraph().getName())).append("\",");
        hudJson.append("\"chapter\":\"").append(escapeJson(title)).append("\",");
        hudJson.append("\"objectives\":[{");
        hudJson.append("\"text\":\"").append(escapeJson(description)).append("\",");
        hudJson.append("\"complete\":false,");
        hudJson.append("\"current\":true");
        hudJson.append("}]}");
        
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                com.warmpixel.storyadventure.network.NetworkHandler.sendOpenUI(
                    player, 
                    com.warmpixel.storyadventure.network.OpenUIPayload.SCREEN_HUD_SHOW, 
                    hudJson.toString()
                );
            }
        }
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Spawn a vehicle (bus) around the first party member's location.
     */
    private void spawnVehicleAroundPlayer(Instance instance, StageNode node) {
        String busType = node.getString("bus_type", "automobility:shopping_cart");
        String pathName = node.getString("bus_path", "");
        float spawnOffset = node.getFloat("spawn_offset", 5.0f);
        
        // Find first online player
        ServerPlayer player = null;
        for (UUID memberId : instance.getParty().getMembers()) {
            player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) break;
        }
        
        if (player == null) {
            StoryAdventureMod.LOGGER.warn("[ItemInteractionNodeHandler] No online player to spawn vehicle around");
            return;
        }

        UUID existingVehicleId = spawnedVehicles.get(instance.getInstanceId());
        if (existingVehicleId != null) {
            despawnVehicle(instance, existingVehicleId);
            spawnedVehicles.remove(instance.getInstanceId());
        }
        
        ServerLevel level = player.serverLevel();
        String instanceTag = "instance_" + instance.getInstanceId().toString();
        
        BlockPos spawnPos;
        float yaw;
        
        if (node.getData().has("x") && node.getData().has("y") && node.getData().has("z")) {
            spawnPos = BlockPos.containing(node.getDouble("x", 0.0), node.getDouble("y", 0.0), node.getDouble("z", 0.0));
            yaw = node.getFloat("yaw", 0.0f);
        } else {
            // Calculate spawn position offset from player (in front of them)
            Vec3 playerPos = player.position();
            yaw = player.getYRot();
            double offsetX = -Math.sin(Math.toRadians(yaw)) * spawnOffset;
            double offsetZ = Math.cos(Math.toRadians(yaw)) * spawnOffset;
            spawnPos = BlockPos.containing(playerPos.x + offsetX, playerPos.y, playerPos.z + offsetZ);
        }
        
        StoryAdventureMod.LOGGER.info("[ItemInteractionNodeHandler] Spawning bus at {} for instance {}", spawnPos, instance.getInstanceId());
        
        try {
            Entity vehicle = spawnAutomobileVehicle(level, spawnPos, busType, yaw);
            
            if (vehicle != null) {
                // Tag the vehicle for cleanup
                vehicle.addTag(instanceTag);
                vehicle.addTag("story_entity");
                vehicle.addTag("story_vehicle");
                
                // Store vehicle UUID for tracking
                spawnedVehicles.put(instance.getInstanceId(), vehicle.getUUID());
                
                // If path is specified, start NPC driving via BusDriverManager
                if (!pathName.isEmpty()) {
                    startBusDriving(level, vehicle, pathName);
                }
                
                StoryAdventureMod.LOGGER.info("[ItemInteractionNodeHandler] Successfully spawned vehicle {} at {}", 
                    vehicle.getUUID(), spawnPos);
            } else {
                StoryAdventureMod.LOGGER.warn("[ItemInteractionNodeHandler] Failed to spawn vehicle of type {}", busType);
            }
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("[ItemInteractionNodeHandler] Error spawning vehicle", e);
        }
    }
    
    /**
     * Spawn an Automobility vehicle using reflection (to avoid hard dependency).
     */
    private Entity spawnAutomobileVehicle(ServerLevel level, BlockPos pos, String busTypeStr, float yaw) {
        try {
            // First try: spawn via automobility:automobile entity type with frame configuration
            if (busTypeStr.startsWith("cityvehicles:") || busTypeStr.startsWith("automobility:")) {
                Optional<net.minecraft.world.entity.EntityType<?>> autoType = 
                    net.minecraft.world.entity.EntityType.byString("automobility:automobile");
                    
                if (autoType.isPresent()) {
                    Entity vehicle = autoType.get().spawn(level, pos, MobSpawnType.COMMAND);
                    if (vehicle != null) {
                        vehicle.setYRot(yaw);
                        
                        // Configure components using reflection
                        configureAutomobileComponents(level, vehicle, busTypeStr);
                        return vehicle;
                    }
                }
            }
            
            // Fallback: try as regular entity type
            Optional<net.minecraft.world.entity.EntityType<?>> typeOpt = 
                net.minecraft.world.entity.EntityType.byString(busTypeStr);
            if (typeOpt.isPresent()) {
                Entity vehicle = typeOpt.get().spawn(level, pos, MobSpawnType.COMMAND);
                if (vehicle != null) {
                    vehicle.setYRot(yaw);
                    return vehicle;
                }
            }
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.error("[ItemInteractionNodeHandler] Failed to spawn vehicle", e);
        }
        
        return null;
    }
    
    /**
     * Configure Automobility vehicle components (frame, wheel, engine) via reflection.
     */
    private void configureAutomobileComponents(ServerLevel level, Entity vehicle, String frameId) {
        try {
            // Build resource keys for frame, wheel, engine
            net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<Object>> frameRegistryKey = 
                net.minecraft.resources.ResourceKey.createRegistryKey(
                    net.minecraft.resources.ResourceLocation.parse("automobility:automobile_frame"));
            net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<Object>> wheelRegistryKey = 
                net.minecraft.resources.ResourceKey.createRegistryKey(
                    net.minecraft.resources.ResourceLocation.parse("automobility:automobile_wheel"));
            net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<Object>> engineRegistryKey = 
                net.minecraft.resources.ResourceKey.createRegistryKey(
                    net.minecraft.resources.ResourceLocation.parse("automobility:automobile_engine"));
            
            // Use the frameId as-is, and default wheel/engine for cityvehicles
            String wheelId = frameId.startsWith("cityvehicles:") ? "cityvehicles:bus" : "automobility:standard";
            String engineId = "automobility:iron";
            
            net.minecraft.resources.ResourceKey<?> frameKey = net.minecraft.resources.ResourceKey.create(
                frameRegistryKey, net.minecraft.resources.ResourceLocation.parse(frameId));
            net.minecraft.resources.ResourceKey<?> wheelKey = net.minecraft.resources.ResourceKey.create(
                wheelRegistryKey, net.minecraft.resources.ResourceLocation.parse(wheelId));
            net.minecraft.resources.ResourceKey<?> engineKey = net.minecraft.resources.ResourceKey.create(
                engineRegistryKey, net.minecraft.resources.ResourceLocation.parse(engineId));
            
            net.minecraft.core.RegistryAccess registryAccess = level.registryAccess();
            
            @SuppressWarnings("unchecked")
            net.minecraft.core.Holder<?> frameHolder = registryAccess.registryOrThrow(
                (net.minecraft.resources.ResourceKey) frameKey.registryKey())
                .getHolderOrThrow((net.minecraft.resources.ResourceKey) frameKey);
            @SuppressWarnings("unchecked")
            net.minecraft.core.Holder<?> wheelHolder = registryAccess.registryOrThrow(
                (net.minecraft.resources.ResourceKey) wheelKey.registryKey())
                .getHolderOrThrow((net.minecraft.resources.ResourceKey) wheelKey);
            @SuppressWarnings("unchecked")
            net.minecraft.core.Holder<?> engineHolder = registryAccess.registryOrThrow(
                (net.minecraft.resources.ResourceKey) engineKey.registryKey())
                .getHolderOrThrow((net.minecraft.resources.ResourceKey) engineKey);
            
            // Invoke setComponents via reflection
            Method setCompMethod = vehicle.getClass().getMethod("setComponents", 
                net.minecraft.core.Holder.class, 
                net.minecraft.core.Holder.class, 
                net.minecraft.core.Holder.class);
            setCompMethod.invoke(vehicle, frameHolder, wheelHolder, engineHolder);
            
            StoryAdventureMod.LOGGER.info("[ItemInteractionNodeHandler] Configured automobile: frame={}, wheel={}, engine={}", 
                frameId, wheelId, engineId);
                
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.warn("[ItemInteractionNodeHandler] Could not configure automobile components: {}", e.getMessage());
        }
    }
    
    /**
     * Start the bus driving on a path using BusDriverManager (if available).
     */
    private void startBusDriving(ServerLevel level, Entity vehicle, String pathName) {
        try {
            // Use reflection to call BusDriverManager.startDriving
            Class<?> busDriverManagerClass = Class.forName("com.warmpixel.npcbusdriver.BusDriverManager");
            Method startDrivingMethod = busDriverManagerClass.getMethod("startDriving", 
                Entity.class, Entity.class, String.class);
            
            // For now, the vehicle will drive itself (no NPC driver)
            // If you want an NPC driver, you'd spawn one and pass it here
            startDrivingMethod.invoke(null, vehicle, vehicle, pathName);
            
            StoryAdventureMod.LOGGER.info("[ItemInteractionNodeHandler] Started bus driving on path: {}", pathName);
        } catch (ClassNotFoundException e) {
            StoryAdventureMod.LOGGER.warn("[ItemInteractionNodeHandler] BusDriverManager not found - bus will be stationary");
        } catch (Exception e) {
            StoryAdventureMod.LOGGER.warn("[ItemInteractionNodeHandler] Failed to start bus driving: {}", e.getMessage());
        }
    }
    
    @Override
    public void onTick(Instance instance, StageNode node) {
        // Check if already complete
        if (instance.getState().getFlag(ITEM_INTERACTION_COMPLETE_FLAG)) {
            return;
        }
        
        String interactionType = node.getString("interaction_type", "ride_vehicle");
        
        if ("ride_vehicle".equals(interactionType)) {
            checkVehicleRiding(instance, node);
        }
    }
    
    /**
     * Check if any party member is riding a vehicle (like a bus).
     */
    private void checkVehicleRiding(Instance instance, StageNode node) {
        String vehicleTag = node.getString("vehicle_tag", "");
        String instanceTag = "instance_" + instance.getInstanceId().toString();
        UUID spawnedVehicleId = spawnedVehicles.get(instance.getInstanceId());
        
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null && player.isPassenger()) {
                Entity vehicle = player.getVehicle();
                if (vehicle != null) {
                    // Check if it's our spawned vehicle
                    boolean isOurVehicle = vehicle.getTags().contains(instanceTag);
                    
                    // Or check by UUID
                    if (spawnedVehicleId != null && vehicle.getUUID().equals(spawnedVehicleId)) {
                        isOurVehicle = true;
                    }
                    
                    // Or if a specific vehicle tag is required
                    if (!vehicleTag.isEmpty()) {
                        isOurVehicle = isOurVehicle || vehicle.getTags().contains(vehicleTag);
                    }
                    
                    // If riding any vehicle in our instance, complete
                    if (isOurVehicle) {
                        StoryAdventureMod.LOGGER.info("[ItemInteractionNodeHandler] Player {} is riding vehicle {}", 
                            player.getName().getString(), vehicle.getType().getDescriptionId());
                        
                        markComplete(instance, node);
                        return;
                    }
                }
            }
        }
    }
    
    private void markComplete(Instance instance, StageNode node) {
        instance.getState().setFlag(ITEM_INTERACTION_COMPLETE_FLAG, true);
        instance.getState().setNodeResult("complete");
        
        // Notify players
        String title = node.getString("title", "物品交互");
        for (UUID memberId : instance.getParty().getMembers()) {
            ServerPlayer player = instance.getServer().getPlayerList().getPlayer(memberId);
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[完成] §f" + title));
            }
        }
        
        // Trigger transition
        instance.evaluateAutoTransitions();
    }
    
    @Override
    public void onAction(Instance instance, StageNode node, ServerPlayer player, String action, Object data) {
        // Manual completion via action (e.g., from a button or NPC dialog)
        if ("complete".equals(action)) {
            markComplete(instance, node);
        }
    }
    
    @Override
    public void onExit(Instance instance, StageNode node) {
        // Check if we should keep the vehicle (e.g., for next node to use)
        boolean keepVehicle = node.getBoolean("keep_vehicle_on_exit", false);
        
        UUID vehicleUUID = spawnedVehicles.remove(instance.getInstanceId());
        
        if (vehicleUUID != null && !keepVehicle) {
            despawnVehicle(instance, vehicleUUID);
        }
        
        // Clean up completion flag
        instance.getState().setFlag(ITEM_INTERACTION_COMPLETE_FLAG, false);
        
        StoryAdventureMod.LOGGER.info("[ItemInteractionNodeHandler] onExit for instance {}", instance.getInstanceId());
    }
    
    @Override
    public boolean canComplete(Instance instance, StageNode node) {
        return instance.getState().getFlag(ITEM_INTERACTION_COMPLETE_FLAG);
    }

    private void despawnVehicle(Instance instance, UUID vehicleUUID) {
        if (instance.getServer() == null) return;
        
        StoryAdventureMod.LOGGER.info("[ItemInteractionNodeHandler] Despawning vehicle {} for instance {}", 
            vehicleUUID, instance.getInstanceId());
        
        // Search all dimensions for the vehicle
        for (var level : instance.getServer().getAllLevels()) {
            var entity = level.getEntity(vehicleUUID);
            if (entity != null) {
                // Dismount all passengers first
                entity.ejectPassengers();
                entity.discard();
                StoryAdventureMod.LOGGER.info("[ItemInteractionNodeHandler] Vehicle {} discarded from {}", 
                    vehicleUUID, level.dimension().location());
                break;
            }
        }
    }
}
