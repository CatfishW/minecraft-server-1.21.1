/*
 * Copyright 2024 Markus Bordihn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusbordihn.easynpc.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.config.NPCTemplateManager;
import de.markusbordihn.easynpc.io.DataFileHandler;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SpawningHandler {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String LOG_PREFIX = "[SpawningHandler]";
  private static final List<SpawnTask> spawnTasks = new ArrayList<>();
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private static final String SPAWNING_TASKS_FILE = "spawning_tasks.json";
  private static int syncTickCounter = 0;
  private static final int SYNC_INTERVAL = 20; // Sync every second

  private SpawningHandler() {}

  public static void handleServerTick(MinecraftServer server) {
    if (spawnTasks.isEmpty()) {
      return;
    }

    boolean modified = false;
    Iterator<SpawnTask> iterator = spawnTasks.iterator();
    while (iterator.hasNext()) {
      SpawnTask task = iterator.next();
      // Tick returns true if the state changed (e.g. spawned new mob, or removed dead one)
      if (task.tick()) {
        modified = true;
      }
    }
    
    // Save if state changed
    if (modified) {
      saveTasks();
    }
    
    // Broadcast timer sync to all players periodically
    if (++syncTickCounter >= SYNC_INTERVAL) {
      syncTickCounter = 0;
      broadcastTimerSync(server);
    }
  }

  /**
   * Broadcast timer sync to all connected players.
   */
  private static void broadcastTimerSync(MinecraftServer server) {
    if (spawnTasks.isEmpty()) {
      return;
    }
    
    List<de.markusbordihn.easynpc.network.message.client.SpawnTimerSyncMessage.TimerEntry> entries = new ArrayList<>();
    for (SpawnTask task : spawnTasks) {
      int ticksRemaining = task.delayTicks - task.tickCounter;
      if (ticksRemaining < 0) ticksRemaining = 0;
      entries.add(new de.markusbordihn.easynpc.network.message.client.SpawnTimerSyncMessage.TimerEntry(
          task.templateName, ticksRemaining, task.delayTicks, task.groupSpawn));
    }
    
    de.markusbordihn.easynpc.network.message.client.SpawnTimerSyncMessage message = 
        new de.markusbordihn.easynpc.network.message.client.SpawnTimerSyncMessage(entries);
    
    for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
      de.markusbordihn.easynpc.network.NetworkHandlerManager.sendMessageToPlayer(message, player);
    }
  }

  public static void addSpawningTask(
      String templateName,
      ServerLevel level,
      BlockPos pos1,
      BlockPos pos2,
      int maxCount,
      int delayTicks) {
    addSpawningTask(templateName, level, pos1, pos2, maxCount, delayTicks, false);
  }

  public static void addSpawningTask(
      String templateName,
      ServerLevel level,
      BlockPos pos1,
      BlockPos pos2,
      int maxCount,
      int delayTicks,
      boolean groupSpawn) {
    spawnTasks.add(new SpawnTask(templateName, level, pos1, pos2, maxCount, delayTicks, groupSpawn));
    log.info(
        "{} Added new spawning task for template '{}' with target population {}, {} ticks delay, groupSpawn={}.",
        LOG_PREFIX,
        templateName,
        maxCount,
        delayTicks,
        groupSpawn);
    saveTasks();
  }

  public static int stopSpawningTasks() {
    int count = spawnTasks.size();
    if (count > 0) {
      spawnTasks.clear();
      log.info("{} Stopped {} spawning tasks.", LOG_PREFIX, count);
      saveTasks();
    }
    return count;
  }

  /**
   * Get timer info for all active group spawn tasks.
   * Returns list of: [templateName, ticksRemaining, totalTicks, isGroupSpawn]
   */
  public static List<Object[]> getTimerInfo() {
    List<Object[]> results = new ArrayList<>();
    for (SpawnTask task : spawnTasks) {
      int ticksRemaining = task.delayTicks - task.tickCounter;
      if (ticksRemaining < 0) ticksRemaining = 0;
      results.add(new Object[]{
          task.templateName,
          ticksRemaining,
          task.delayTicks,
          task.groupSpawn
      });
    }
    return results;
  }

  public static void onNPCDeath(Entity entity) {
    if (entity == null) {
      return;
    }
    UUID uuid = entity.getUUID();
    boolean modified = false;
    for (SpawnTask task : spawnTasks) {
      if (task.notifyDeath(uuid)) {
        modified = true;
      }
    }
    if (modified) {
      saveTasks();
    }
  }

  public static void saveTasks() {
    Path dataFolder = DataFileHandler.getCustomDataFolder();
    if (dataFolder == null) {
      return;
    }
    Path file = dataFolder.resolve(SPAWNING_TASKS_FILE);
    
    List<SpawnTaskData> dataList = new ArrayList<>();
    for (SpawnTask task : spawnTasks) {
      dataList.add(new SpawnTaskData(task));
    }
    
    try (Writer writer = Files.newBufferedWriter(file)) {
      gson.toJson(dataList, writer);
      log.debug("{} Saved {} spawning tasks to {}", LOG_PREFIX, dataList.size(), file);
    } catch (IOException e) {
      log.error("{} Failed to save spawning tasks:", LOG_PREFIX, e);
    }
  }

  public static void loadTasks(MinecraftServer server) {
    Path dataFolder = DataFileHandler.getCustomDataFolder();
    if (dataFolder == null) {
      return;
    }
    Path file = dataFolder.resolve(SPAWNING_TASKS_FILE);
    
    if (!Files.exists(file)) {
      return;
    }
    
    try (Reader reader = Files.newBufferedReader(file)) {
      List<SpawnTaskData> dataList = gson.fromJson(reader, new TypeToken<List<SpawnTaskData>>(){}.getType());
      if (dataList != null) {
        spawnTasks.clear();
        for (SpawnTaskData data : dataList) {
          try {
            ResourceKey<Level> levelKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(data.levelName));
            ServerLevel level = server.getLevel(levelKey);
            if (level != null) {
              spawnTasks.add(new SpawnTask(data, level));
            } else {
              log.warn("{} Could not find level '{}' for spawning task", LOG_PREFIX, data.levelName);
            }
          } catch (Exception e) {
             log.error("{} Failed to restore spawning task: {}", LOG_PREFIX, e.getMessage());
          }
        }
        log.info("{} Loaded {} spawning tasks from {}", LOG_PREFIX, spawnTasks.size(), file);
      }
    } catch (IOException e) {
      log.error("{} Failed to load spawning tasks:", LOG_PREFIX, e);
    }
  }

  private static class SpawnTask {
    private final String templateName;
    private final ServerLevel level;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final int maxCount;
    private final int delayTicks;
    private final boolean groupSpawn;
    private final Random random = new Random();
    private final Set<UUID> activeEntityUUIDs = new HashSet<>();

    private int tickCounter = 0;
    private boolean initialSpawnDone = false;

    public SpawnTask(
        String templateName,
        ServerLevel level,
        BlockPos pos1,
        BlockPos pos2,
        int maxCount,
        int delayTicks,
        boolean groupSpawn) {
      this.templateName = templateName;
      this.level = level;
      this.minX = Math.min(pos1.getX(), pos2.getX());
      this.minZ = Math.min(pos1.getZ(), pos2.getZ());
      this.maxX = Math.max(pos1.getX(), pos2.getX());
      this.maxZ = Math.max(pos1.getZ(), pos2.getZ());
      this.maxCount = maxCount;
      this.delayTicks = delayTicks;
      this.groupSpawn = groupSpawn;
    }

    public SpawnTask(SpawnTaskData data, ServerLevel level) {
       this.templateName = data.templateName;
       this.level = level;
       this.minX = data.minX;
       this.minZ = data.minZ;
       this.maxX = data.maxX;
       this.maxZ = data.maxZ;
       this.maxCount = data.maxCount;
       this.delayTicks = data.delayTicks;
       this.groupSpawn = data.groupSpawn;
       this.tickCounter = data.tickCounter;
       this.initialSpawnDone = data.initialSpawnDone;
       if (data.activeEntityUUIDs != null) {
         this.activeEntityUUIDs.addAll(data.activeEntityUUIDs);
       }
    }

    /**
     * @return true if state changed (spawned or removed entity)
     */
    public boolean tick() {
      if (tickCounter++ < delayTicks) {
        // For group spawn, do initial spawn immediately on first tick
        if (groupSpawn && !initialSpawnDone) {
          return doGroupSpawn();
        }
        return false;
      }
      tickCounter = 0;
      boolean changed = false;

      // Check if ANY part of the spawn area is loaded
      boolean isAreaLoaded = level.isLoaded(new BlockPos(minX, 64, minZ)) || 
                             level.isLoaded(new BlockPos(maxX, 64, maxZ)) ||
                             level.isLoaded(new BlockPos((minX + maxX) / 2, 64, (minZ + maxZ) / 2));
      
      // If area is completely unloaded, skip this tick but don't stop the task
      if (!isAreaLoaded) {
        return false;
      }

      if (groupSpawn) {
        // Group spawn mode: kill all existing, then spawn all at once
        changed = doGroupSpawn();
      } else {
        // Normal mode: maintain population
        // 1. Maintain population: Remove dead entities
        Iterator<UUID> iterator = activeEntityUUIDs.iterator();
        while (iterator.hasNext()) {
          UUID uuid = iterator.next();
          Entity entity = level.getEntity(uuid);
          
          // Remove if:
          // 1. Entity is loaded and explicitly dead/removed.
          // 2. Entity is null (not in world) - in a loaded area, this means it's gone.
          boolean shouldRemove = (entity != null && !entity.isAlive()) || (entity == null);
          
          if (shouldRemove) {
            iterator.remove();
            changed = true;
            log.debug("{} Entity {} removed from tracking (entity={}, alive={})", 
                LOG_PREFIX, uuid, entity, entity != null ? entity.isAlive() : "N/A");
          }
        }

        // 2. Spawn new entities if below population limit
        if (activeEntityUUIDs.size() < maxCount) {
          if (spawnWithRetries(3)) {
            changed = true;
          }
        }
      }
      
      return changed;
    }

    /**
     * Group spawn: kill all existing entities and spawn maxCount new ones at once.
     */
    private boolean doGroupSpawn() {
      // Kill all existing tracked entities
      for (UUID uuid : activeEntityUUIDs) {
        Entity entity = level.getEntity(uuid);
        if (entity != null && entity.isAlive()) {
          entity.discard();
          log.debug("{} Group spawn: discarded entity {}", LOG_PREFIX, uuid);
        }
      }
      activeEntityUUIDs.clear();

      // Spawn all maxCount entities at once
      int spawned = 0;
      for (int i = 0; i < maxCount; i++) {
        if (spawnWithRetries(3)) {
          spawned++;
        }
      }
      initialSpawnDone = true;
      log.info("{} Group spawn: spawned {} entities for template '{}'", LOG_PREFIX, spawned, templateName);
      return spawned > 0;
    }

    private boolean spawnWithRetries(int retries) {
      for (int i = 0; i < retries; i++) {
        double x = minX + random.nextInt(maxX - minX + 1) + 0.5;
        double z = minZ + random.nextInt(maxZ - minZ + 1) + 0.5;
        int y =
            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);

        Entity spawned = NPCTemplateManager.spawnEntityFromTemplate(level, templateName, x, y, z);
        if (spawned != null) {
          activeEntityUUIDs.add(spawned.getUUID());
          return true;
        }
      }
      log.warn("{} Failed to spawn NPC after {} attempts.", LOG_PREFIX, retries);
      return false;
    }

    public boolean notifyDeath(UUID uuid) {
      if (activeEntityUUIDs.remove(uuid)) {
        log.debug("{} Entity {} died, removed from active list.", LOG_PREFIX, uuid);
        return true;
      }
      return false;
    }

    public boolean isFinished() {
      // With population maintenance, the task runs indefinitely until explicitly stopped.
      return false;
    }
  }
  
  private static class SpawnTaskData {
    String templateName;
    String levelName;
    int minX;
    int minZ;
    int maxX;
    int maxZ;
    int maxCount;
    int delayTicks;
    boolean groupSpawn;
    int tickCounter;
    boolean initialSpawnDone;
    Set<UUID> activeEntityUUIDs;
    
    public SpawnTaskData(SpawnTask task) {
       this.templateName = task.templateName;
       this.levelName = task.level.dimension().location().toString();
       this.minX = task.minX;
       this.minZ = task.minZ;
       this.maxX = task.maxX;
       this.maxZ = task.maxZ;
       this.maxCount = task.maxCount;
       this.delayTicks = task.delayTicks;
       this.groupSpawn = task.groupSpawn;
       this.tickCounter = task.tickCounter;
       this.initialSpawnDone = task.initialSpawnDone;
       this.activeEntityUUIDs = new HashSet<>(task.activeEntityUUIDs);
    }
  }
}
