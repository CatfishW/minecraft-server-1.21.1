package com.warmpixel.npcbusdriver;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.*;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class BusDriverManager {
    private static final Map<UUID, BusDriverTask> activeTasks = new HashMap<>();

    public static boolean isManagedVehicle(UUID uuid) {
        return activeTasks.containsKey(uuid);
    }

    public static Entity getManagedDriver(UUID vehicleUuid) {
        BusDriverTask task = activeTasks.get(vehicleUuid);
        return (task != null) ? task.executor : null;
    }

    public static UUID getActualVehicleUUID(Entity entity) {
        if (entity == null) return null;
        if (activeTasks.containsKey(entity.getUUID())) return entity.getUUID();
        
        // Handle Automobility HitboxEntity
        try {
            if (entity.getClass().getName().contains("HitboxEntity")) {
                Method m = entity.getClass().getMethod("automobile");
                Entity auto = (Entity) m.invoke(entity);
                if (auto != null && activeTasks.containsKey(auto.getUUID())) {
                    return auto.getUUID();
                }
            }
        } catch (Exception e) {}
        
        return null;
    }

    public static void forceDriverIntoSeat(Entity vehicle) {
        BusDriverTask task = activeTasks.get(vehicle.getUUID());
        if (task != null && task.executor != null && !task.executor.isRemoved()) {
            
            // Ensure NPC is in the same world and close enough (or just teleport)
            if (task.executor.level() != vehicle.level() || task.executor.distanceToSqr(vehicle) > 100) {
                task.executor.teleportTo((net.minecraft.server.level.ServerLevel)vehicle.level(), vehicle.getX(), vehicle.getY(), vehicle.getZ(), Set.of(), vehicle.getYRot(), vehicle.getXRot());
            }

            List<Entity> passengers = vehicle.getPassengers();
            
            // If the driver is already a passenger (any seat), don't reshuffle seats.
            if (passengers.contains(task.executor)) {
                return;
            }

            // 1. If NPC is already in seat 0, we are good.
            if (!passengers.isEmpty() && passengers.get(0) == task.executor) {
                return; 
            }

            // 2. If someone else is in seat 0, eject THEM.
            if (!passengers.isEmpty()) {
                Entity seat0 = passengers.get(0);
                if (seat0 != task.executor) {
                    seat0.stopRiding();
                    NPCBusDriverMod.LOGGER.info("Ejected {} from driver seat of {} to make room for NPC.", seat0.getName().getString(), vehicle.getUUID());
                }
            }

            // 3. If NPC is in another seat, they must stop riding first to move.
            if (task.executor.getVehicle() != null) {
                task.executor.stopRiding();
            }
            
            // 4. Force NPC into seat 0 (riding with 'force' usually puts them in first available seat, which is 0 now)
            task.executor.startRiding(vehicle, true);
            NPCBusDriverMod.LOGGER.info("Seated NPC {} into driver seat of {} (forced)", task.executor.getName().getString(), vehicle.getUUID());
        }
    }

    public static void startDriving(Entity vehicle, Entity driver, String pathName) {
        List<BlockPos> path = PathManager.loadPath(pathName);
        if (path != null) {
            activeTasks.put(vehicle.getUUID(), new BusDriverTask(vehicle, driver, pathName, path));
        }
    }

    public static void reloadAllPaths() {
        PathManager.clearCache();
        for (BusDriverTask task : activeTasks.values()) {
            task.reloadPath();
        }
    }

    public static void requestStop(Entity vehicle, int seconds) {
        if (activeTasks.containsKey(vehicle.getUUID())) {
            activeTasks.get(vehicle.getUUID()).requestStop(seconds);
        }
    }

    public static void requestStopNearby(Vec3 pos, int seconds) {
        double closestDist = 100.0; // 10 blocks squared
        BusDriverTask closestTask = null;
        for (BusDriverTask task : activeTasks.values()) {
            if (task.vehicle == null || task.vehicle.isRemoved()) continue;
            double d = task.vehicle.position().distanceToSqr(pos);
            if (d < closestDist) {
                closestDist = d;
                closestTask = task;
            }
        }
        if (closestTask != null) {
            NPCBusDriverMod.LOGGER.info("Requesting stop for vehicle {} at distance {}", closestTask.uuid, Math.sqrt(closestDist));
            closestTask.requestStop(seconds);
        } else {
            NPCBusDriverMod.LOGGER.info("No bus found within 10 blocks of {}", pos);
        }
    }

    public static void tick() {
        Iterator<Map.Entry<UUID, BusDriverTask>> it = activeTasks.entrySet().iterator();
        while (it.hasNext()) {
            BusDriverTask task = it.next().getValue();
            task.update();
            if (task.finished) {
                // Only remove if explicitly finished or vehicle is permanently gone (dead)
                // If unloaded, we keep the task and wait for reload
                if (task.isPermanentlyRemoved()) {
                    task.stop();
                    it.remove();
                    NPCBusDriverMod.LOGGER.info("Removing task for vehicle {}", task.uuid);
                }
            }
        }
    }

    static class BusDriverTask {
        UUID uuid;
        UUID driverUUID;
        net.minecraft.server.level.ServerLevel level;
        Entity vehicle;
        Entity executor;
        String pathName;
        List<BlockPos> path;
        int currentIndex = 0;
        boolean finished = false;
        
        // Reflection cache
        private Object inputObject;
        private Method inputMethod;
        private boolean reflectionFailed = false;
        private int pauseTicks = 0;


        public BusDriverTask(Entity vehicle, Entity driver, String pathName, List<BlockPos> path) {
            this.vehicle = vehicle;
            this.executor = driver;
            this.uuid = vehicle.getUUID();
            this.driverUUID = driver.getUUID();
            this.pathName = pathName;
            this.path = path;
            if (vehicle.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                 this.level = sl;
            }
            
            // Find closest point to start
            double minDist = Double.MAX_VALUE;
            int closest = 0;
            for(int i=0; i<path.size(); i++) {
                double d = vehicle.distanceToSqr(Vec3.atCenterOf(path.get(i)));
                if(d < minDist) {
                    minDist = d;
                    closest = i;
                }
            }
            this.currentIndex = closest;
            NPCBusDriverMod.LOGGER.info("Started BusDriverTask for vehicle {} with driver {} at path index {}", uuid, driverUUID, currentIndex);
        }
        
        public void reloadPath() {
            List<BlockPos> newPath = PathManager.loadPath(pathName);
            if (newPath != null) {
                this.path = newPath;
                // Re-validate index
                if (currentIndex >= path.size()) currentIndex = 0;
                NPCBusDriverMod.LOGGER.info("Reloaded path for vehicle {}", uuid);
            }
        }

        public boolean isPermanentlyRemoved() {
            if (vehicle == null) return false;
            return false;
        }

        public void update() {
            if (finished) return;
            
            // Validate vehicle
            if (vehicle == null || vehicle.isRemoved()) {
                if (level != null) {
                    Entity fresh = level.getEntity(uuid);
                    if (fresh != null) {
                        this.vehicle = fresh;
                        NPCBusDriverMod.LOGGER.info("Re-acquired vehicle entity {}", uuid);
                    } else {
                        // Still waiting for load
                        return;
                    }
                } else {
                   finished = true;
                   return;
                }
            }

            // Validate and Ensure Driver (Seat 0)
            if (executor == null || executor.isRemoved()) {
                Entity freshDriver = level.getEntity(driverUUID);
                if (freshDriver != null) {
                    this.executor = freshDriver;
                }
            }

            if (executor != null && !executor.isRemoved()) {
                BusDriverManager.forceDriverIntoSeat(vehicle);
            }

            // Stop for nearby players (Distance: 15 blocks)
            List<net.minecraft.server.level.ServerPlayer> nearbyPlayers = level.getPlayers(p -> 
                p.position().distanceToSqr(vehicle.position()) < 225.0 && p.getVehicle() != vehicle && !p.isSpectator()
            );
            
            if (!nearbyPlayers.isEmpty()) {
                // Check if any player just boarded (to stay stopped for a bit)
                pauseTicks = Math.max(pauseTicks, 40); // Stay stopped for at least 2 seconds if players are near
                
                Vec3 velocity = vehicle.getDeltaMovement();
                double speedSq = velocity.horizontalDistanceSqr();
                float yaw = vehicle.getYRot();
                Vec3 forward = Vec3.directionFromRotation(0, yaw);
                double dot = velocity.x * forward.x + velocity.z * forward.z;

                boolean shouldBrake = speedSq > 0.005 && dot > 0; 
                setInputs(false, shouldBrake, false, false, false, false);
                return;
            }
            
            // Handle pausing
            if (pauseTicks > 0) {
                pauseTicks--;
                // Only brake if we are still moving, otherwise just release all inputs to avoid reversing
                if (vehicle.getDeltaMovement().horizontalDistanceSqr() > 0.01) {
                    stop(); 
                } else {
                    setInputs(false, false, false, false, false, false);
                }
                return;
            }
            
            if (currentIndex >= path.size()) {
                 currentIndex = 0;
            }

            Vec3 target = Vec3.atCenterOf(path.get(currentIndex));
            double dist = vehicle.position().distanceTo(target);

            if (dist < 5.0) { // Increased distance check for smoother turning (4.0 -> 5.0)
                currentIndex++;
                if (currentIndex >= path.size()) {
                     currentIndex = 0;
                }
            }
            
            // Re-calc target for steering
            target = Vec3.atCenterOf(path.get(currentIndex));
            
            // Steering logic
            Vec3 toTarget = target.subtract(vehicle.position()).normalize();
            double targetYaw = Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
            double currentYaw = vehicle.getYRot();
            
            // Normalize angles
            double diff = Mth.wrapDegrees(targetYaw - currentYaw);
            
            boolean left = false;
            boolean right = false;
            boolean fwd = true;
            boolean back = false;
            boolean drift = false;
            
            // Improved Steering and Speed Control
            float turnSpeed = 5.0f; 
            if (Math.abs(diff) > 2) {
                if (diff < 0) {
                    left = true;
                    vehicle.setYRot(vehicle.getYRot() - (float)Math.min(Math.abs(diff), turnSpeed));
                } else {
                    right = true;
                    vehicle.setYRot(vehicle.getYRot() + (float)Math.min(diff, turnSpeed));
                }
            }
            
            // Speed logic: slow down on corners, only brake if really sharp
            if (Math.abs(diff) > 30) {
                drift = true;
                fwd = true; // Keep moving forward but with drift/braking
                if (Math.abs(diff) > 60) {
                    fwd = false;
                    back = true; // Brake hard
                }
            }
            
            // If we are extremely far from target, maybe we overshot. 
            // Don't reverse unless path is behind and we are slow.
            if (Math.abs(diff) > 120 && vehicle.getDeltaMovement().length() < 0.1) {
                fwd = false;
                back = true; // Reverse to turn
            }

            setInputs(fwd, back, left, right, drift, false);
            
            // Eject monsters from the bus (but NOT our driver)
            if (vehicle.isAlive()) {
                for (Entity passenger : vehicle.getPassengers()) {
                    if (passenger instanceof net.minecraft.world.entity.monster.Monster && passenger != executor) {
                        passenger.stopRiding();
                    }
                }
            }
        }
        
        private void ensureEngineRunning() {
             try {
                 // Try to call engineRunning() just to see if it exists, or look for a setter.
                 // Actually, if it's already running, we don't need to do anything.
                 // But some versions might need a "start" signal if the driver is an NPC.
                 // In 0.5.0, it seems automatic when inputs are provided.
             } catch (Exception e) {
                 // Ignore
             }
        }
        
        public void stop() {
            // Check speed to avoid reversing
            boolean moving = vehicle.getDeltaMovement().horizontalDistanceSqr() > 0.01;
            setInputs(false, moving, false, false, false, false);
        }
        
        public void setInputs(boolean fwd, boolean back, boolean left, boolean right, boolean drift, boolean jump) {
            if (reflectionFailed || vehicle == null) return;
            try {
                if (inputObject == null && inputMethod == null) {
                    // Try to get the 'input' field from the vehicle (Automobility 0.5.0+)
                    try {
                        Field inputField = vehicle.getClass().getDeclaredField("input");
                        inputField.setAccessible(true);
                        inputObject = inputField.get(vehicle);
                        
                        if (inputObject != null) {
                            // Find setDigitalInputs on the Input object
                            // boolean setDigitalInputs(boolean accelerating, boolean braking, boolean left, boolean right, boolean drift, boolean jump)
                            inputMethod = inputObject.getClass().getMethod("setDigitalInputs", boolean.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);
                        }
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        NPCBusDriverMod.LOGGER.warn("Could not find 'input' field on vehicle, falling back to method search. Error: {}", e.getMessage());
                    }

                    // Fallback to provideClientInput ONLY if input field path failed (though provideClientInput is known to crash on server)
                    // We keep this check just in case, but prioritize the field access.
                    if (inputMethod == null) {
                         try {
                              Method m = vehicle.getClass().getMethod("provideClientInput", boolean.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);
                              m.setAccessible(true);
                              inputMethod = m;
                              // inputObject remains null, indicating we invoke on vehicle
                         } catch (NoSuchMethodException ex) {
                              reflectionFailed = true;
                              NPCBusDriverMod.LOGGER.error("Could not find input method for vehicle: {}", vehicle.getClass().getName());
                              return;
                         }
                    }
                }
                
                if (inputMethod != null) {
                    if (inputObject != null) {
                        // Invoke on the Input object
                        inputMethod.invoke(inputObject, fwd, back, left, right, drift, jump);
                    } else {
                        // Invoke on the vehicle (Risky, but fallback)
                        inputMethod.invoke(vehicle, fwd, back, left, right, drift, jump);
                    }
                }
            } catch (Exception e) {
                NPCBusDriverMod.LOGGER.error("Error invoking input method", e);
                reflectionFailed = true;
            }
        }
        
        public void requestStop(int seconds) {
            NPCBusDriverMod.LOGGER.info("Bus stop requested for {} seconds for vehicle {}", seconds, vehicle.getId());
            this.pauseTicks = seconds * 20;
        }
    }
    
    public static Entity setupBusDriver(net.minecraft.server.level.ServerLevel level, Entity executor, String pathName, String busTypeStr) {
        List<BlockPos> path = PathManager.loadPath(pathName);
        if (path == null || path.isEmpty()) {
            NPCBusDriverMod.LOGGER.error("Path '{}' not found or empty.", pathName);
            return null;
        }

        Entity vehicle = null;
        Optional<net.minecraft.world.entity.EntityType<?>> typeOpt = net.minecraft.world.entity.EntityType.byString(busTypeStr);
        if (typeOpt.isPresent()) {
             vehicle = typeOpt.get().spawn(level, BlockPos.containing(executor.position()), net.minecraft.world.entity.MobSpawnType.COMMAND);
        } else {
             // Try as Automobility Item
             net.minecraft.resources.ResourceLocation itemId = net.minecraft.resources.ResourceLocation.parse(busTypeStr);
             if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemId)) {
                 net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
                 
                 try {
                     Class<?> itemsClass = Class.forName("io.github.foundationgames.automobility.item.AutomobilityItems");
                     Object eventualComponent = itemsClass.getField("COMPONENT_AUTOMOBILE_DATA").get(null);
                     Object componentTypeObj = eventualComponent.getClass().getMethod("get").invoke(eventualComponent);
                     
                     net.minecraft.core.component.DataComponentType<?> componentType = (net.minecraft.core.component.DataComponentType<?>) componentTypeObj;
                     
                     net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
                     if (stack.has((net.minecraft.core.component.DataComponentType)componentType)) {
                         Optional<net.minecraft.world.entity.EntityType<?>> autoType = net.minecraft.world.entity.EntityType.byString("automobility:automobile");
                         if (autoType.isPresent()) {
                              Entity genericVehicle = autoType.get().spawn(level, BlockPos.containing(executor.position()), net.minecraft.world.entity.MobSpawnType.COMMAND);
                              if (genericVehicle != null) {
                                  vehicle = genericVehicle;
                                  
                                  Object dataObj = stack.get((net.minecraft.core.component.DataComponentType)componentType);
                                  
                                  Object frameKeyObj = dataObj.getClass().getMethod("frame").invoke(dataObj);
                                  Object wheelKeyObj = dataObj.getClass().getMethod("wheel").invoke(dataObj);
                                  Object engineKeyObj = dataObj.getClass().getMethod("engine").invoke(dataObj);
                                  
                                  net.minecraft.resources.ResourceKey<?> frameKey = (net.minecraft.resources.ResourceKey<?>) frameKeyObj;
                                  net.minecraft.resources.ResourceKey<?> wheelKey = (net.minecraft.resources.ResourceKey<?>) wheelKeyObj;
                                  net.minecraft.resources.ResourceKey<?> engineKey = (net.minecraft.resources.ResourceKey<?>) engineKeyObj;
                                  
                                  net.minecraft.core.RegistryAccess registryAccess = level.registryAccess();
                                  
                                  net.minecraft.core.Holder<?> frameHolder = registryAccess.registryOrThrow((net.minecraft.resources.ResourceKey)frameKey.registryKey()).getHolderOrThrow((net.minecraft.resources.ResourceKey)frameKey);
                                  net.minecraft.core.Holder<?> wheelHolder = registryAccess.registryOrThrow((net.minecraft.resources.ResourceKey)wheelKey.registryKey()).getHolderOrThrow((net.minecraft.resources.ResourceKey)wheelKey);
                                  net.minecraft.core.Holder<?> engineHolder = registryAccess.registryOrThrow((net.minecraft.resources.ResourceKey)engineKey.registryKey()).getHolderOrThrow((net.minecraft.resources.ResourceKey)engineKey);
                                  
                                  Method setCompMethod = genericVehicle.getClass().getMethod("setComponents", net.minecraft.core.Holder.class, net.minecraft.core.Holder.class, net.minecraft.core.Holder.class);
                                  setCompMethod.invoke(genericVehicle, frameHolder, wheelHolder, engineHolder);
                                  
                                  NPCBusDriverMod.LOGGER.info("Configured Automobility Vehicle from Item Data: {}", busTypeStr);
                              }
                         }
                     } else {
                         NPCBusDriverMod.LOGGER.warn("Item {} does not have Automobility Component Data.", busTypeStr);
                     }
                 } catch (Exception e) {
                     NPCBusDriverMod.LOGGER.error("Automobility spawning error: ", e);
                 }
             } else {
                 // Fallback: Check PREFABS list (for non-item prefabs found in Creative Tabs)
                 try {
                     Class<?> itemClass = Class.forName("io.github.foundationgames.automobility.item.AutomobileItem");
                     List<?> prefabs = (List<?>) itemClass.getField("PREFABS").get(null);
                     
                     boolean applied = false;
                     for (Object dataObj : prefabs) {
                          // dataObj is AutomobileData (Record)
                          // prefabName() returns Optional<ResourceLocation>
                          Method nameMethod = dataObj.getClass().getMethod("prefabName");
                          Optional<?> pNameOpt = (Optional<?>) nameMethod.invoke(dataObj);
                          
                          if (pNameOpt.isPresent() && pNameOpt.get().equals(itemId)) {
                               // Found Match!
                               Optional<net.minecraft.world.entity.EntityType<?>> autoType = net.minecraft.world.entity.EntityType.byString("automobility:automobile");
                               if (autoType.isPresent()) {
                                    Entity genericVehicle = autoType.get().spawn(level, BlockPos.containing(executor.position()), net.minecraft.world.entity.MobSpawnType.COMMAND);
                                    if (genericVehicle != null) {
                                        vehicle = genericVehicle;
                                        // Extract Keys
                                        Object frameKeyObj = dataObj.getClass().getMethod("frame").invoke(dataObj);
                                        Object wheelKeyObj = dataObj.getClass().getMethod("wheel").invoke(dataObj);
                                        Object engineKeyObj = dataObj.getClass().getMethod("engine").invoke(dataObj);
                                        
                                        net.minecraft.resources.ResourceKey<?> frameKey = (net.minecraft.resources.ResourceKey<?>) frameKeyObj;
                                        net.minecraft.resources.ResourceKey<?> wheelKey = (net.minecraft.resources.ResourceKey<?>) wheelKeyObj;
                                        net.minecraft.resources.ResourceKey<?> engineKey = (net.minecraft.resources.ResourceKey<?>) engineKeyObj;
                                        
                                        net.minecraft.core.RegistryAccess registryAccess = level.registryAccess();
                                        
                                        net.minecraft.core.Holder<?> frameHolder = registryAccess.registryOrThrow((net.minecraft.resources.ResourceKey)frameKey.registryKey()).getHolderOrThrow((net.minecraft.resources.ResourceKey)frameKey);
                                        net.minecraft.core.Holder<?> wheelHolder = registryAccess.registryOrThrow((net.minecraft.resources.ResourceKey)wheelKey.registryKey()).getHolderOrThrow((net.minecraft.resources.ResourceKey)wheelKey);
                                        net.minecraft.core.Holder<?> engineHolder = registryAccess.registryOrThrow((net.minecraft.resources.ResourceKey)engineKey.registryKey()).getHolderOrThrow((net.minecraft.resources.ResourceKey)engineKey);
                                        
                                        Method setCompMethod = genericVehicle.getClass().getMethod("setComponents", net.minecraft.core.Holder.class, net.minecraft.core.Holder.class, net.minecraft.core.Holder.class);
                                        setCompMethod.invoke(genericVehicle, frameHolder, wheelHolder, engineHolder);
                                        
                                        NPCBusDriverMod.LOGGER.info("Configured Automobility Vehicle from Prefab List: {}", busTypeStr);
                                        applied = true;
                                    }
                                    break;
                               }
                          }
                     }
                     
                     if (!applied) {
                         // Specific handling for City Vehicles frames (which are not Prefabs or Items)
                         if (busTypeStr.startsWith("cityvehicles:")) {
                             NPCBusDriverMod.LOGGER.info("Detected City Vehicles ID, attempting to construct from Frame: {}", busTypeStr);
                             Optional<net.minecraft.world.entity.EntityType<?>> autoType = net.minecraft.world.entity.EntityType.byString("automobility:automobile");
                             if (autoType.isPresent()) {
                                  Entity genericVehicle = autoType.get().spawn(level, BlockPos.containing(executor.position()), net.minecraft.world.entity.MobSpawnType.COMMAND);
                                  if (genericVehicle != null) {
                                      vehicle = genericVehicle;
                                      
                                      // Construct ResourceKeys manually
                                      net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<Object>> frameRegistryKey = net.minecraft.resources.ResourceKey.createRegistryKey(net.minecraft.resources.ResourceLocation.parse("automobility:automobile_frame"));
                                      net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<Object>> wheelRegistryKey = net.minecraft.resources.ResourceKey.createRegistryKey(net.minecraft.resources.ResourceLocation.parse("automobility:automobile_wheel"));
                                      net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<Object>> engineRegistryKey = net.minecraft.resources.ResourceKey.createRegistryKey(net.minecraft.resources.ResourceLocation.parse("automobility:automobile_engine"));

                                      net.minecraft.resources.ResourceKey<?> frameKey = net.minecraft.resources.ResourceKey.create(frameRegistryKey, net.minecraft.resources.ResourceLocation.parse(busTypeStr));
                                      // Assume 'cityvehicles:bus' for wheel, or derive? Usually it's 'cityvehicles:bus' for all buses.
                                      net.minecraft.resources.ResourceKey<?> wheelKey = net.minecraft.resources.ResourceKey.create(wheelRegistryKey, net.minecraft.resources.ResourceLocation.parse("cityvehicles:bus"));
                                      net.minecraft.resources.ResourceKey<?> engineKey = net.minecraft.resources.ResourceKey.create(engineRegistryKey, net.minecraft.resources.ResourceLocation.parse("automobility:iron"));
                                      
                                      net.minecraft.core.RegistryAccess registryAccess = level.registryAccess();
                                      
                                      // Resolve Holders
                                      // Note: Registries for these are dynamic.
                                      // registryOrThrow might fail if the registry isn't found? automobility registries should be there.
                                      net.minecraft.core.Holder<?> frameHolder = registryAccess.registryOrThrow((net.minecraft.resources.ResourceKey)frameKey.registryKey()).getHolderOrThrow((net.minecraft.resources.ResourceKey)frameKey);
                                      net.minecraft.core.Holder<?> wheelHolder = registryAccess.registryOrThrow((net.minecraft.resources.ResourceKey)wheelKey.registryKey()).getHolderOrThrow((net.minecraft.resources.ResourceKey)wheelKey);
                                      net.minecraft.core.Holder<?> engineHolder = registryAccess.registryOrThrow((net.minecraft.resources.ResourceKey)engineKey.registryKey()).getHolderOrThrow((net.minecraft.resources.ResourceKey)engineKey);
                                      
                                      Method setCompMethod = genericVehicle.getClass().getMethod("setComponents", net.minecraft.core.Holder.class, net.minecraft.core.Holder.class, net.minecraft.core.Holder.class);
                                      setCompMethod.invoke(genericVehicle, frameHolder, wheelHolder, engineHolder);
                                      
                                      NPCBusDriverMod.LOGGER.info("Configured City Vehicle Frame: {}", busTypeStr);
                                      applied = true;
                                  }
                             }
                         }
                     }

                     if (!applied) {
                         NPCBusDriverMod.LOGGER.warn("Item/Prefab {} not found in Registry or Prefab list.", busTypeStr);
                     }
                     
                 } catch (Exception e) {
                     NPCBusDriverMod.LOGGER.error("Automobility Prefab scan error: ", e);
                 }
             }
        }

        if (vehicle != null) {
            executor.startRiding(vehicle, true);
            startDriving(vehicle, executor, pathName);
            
            // Spawn 2 Town Guards
            spawnGuards(level, vehicle);
            
            return vehicle;
        }
        return null;
    }

    private static void spawnGuards(net.minecraft.server.level.ServerLevel level, Entity vehicle) {
        Optional<de.markusbordihn.easynpc.config.NPCTemplateData> guardTemplateOpt = de.markusbordihn.easynpc.config.NPCTemplateManager.getTemplate("town_guard");
        if (guardTemplateOpt.isPresent()) {
            de.markusbordihn.easynpc.config.NPCTemplateData template = guardTemplateOpt.get();
            for (int i = 0; i < 2; i++) {
                boolean spawned = de.markusbordihn.easynpc.config.NPCTemplateManager.spawnFromTemplate(level, template, vehicle.getX(), vehicle.getY(), vehicle.getZ());
                if (spawned) {
                    // Find the guard we just spawned
                    net.minecraft.world.phys.AABB searchBox = vehicle.getBoundingBox().inflate(2.0);
                    List<net.minecraft.world.entity.LivingEntity> entities = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, searchBox, e -> {
                         return !e.isPassenger() && e.getType().toString().contains("humanoid");
                    });
                    if (!entities.isEmpty()) {
                        entities.get(0).startRiding(vehicle);
                    }
                }
            }
        } else {
             NPCBusDriverMod.LOGGER.warn("Town Guard template 'town_guard' not found for auto-spawning guards.");
        }
    }
}
