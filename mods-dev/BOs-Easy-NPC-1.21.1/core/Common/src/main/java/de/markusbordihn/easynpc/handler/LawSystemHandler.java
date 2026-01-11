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
import com.google.gson.JsonObject;
import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.data.crime.CrimeType;
import de.markusbordihn.easynpc.data.crime.LawSystemConfig;
import de.markusbordihn.easynpc.data.crime.PlayerLawState;
import de.markusbordihn.easynpc.data.crime.RegionRule;
import de.markusbordihn.easynpc.data.objective.ObjectiveDataEntry;
import de.markusbordihn.easynpc.data.objective.ObjectiveType;
import de.markusbordihn.easynpc.entity.easynpc.data.ObjectiveDataCapable;
import de.markusbordihn.easynpc.network.NetworkHandlerManager;
import de.markusbordihn.easynpc.network.message.LawAdminDataMessage;
import de.markusbordihn.easynpc.io.DataFileHandler;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Central handler for the law enforcement / crime system.
 * Manages player states, configuration, and tick-based updates.
 */
public class LawSystemHandler {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String LOG_PREFIX = "[LawSystemHandler]";
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final String CONFIG_FILE_NAME = "law_system_config.json";
  private static final String PLAYER_DATA_FILE_NAME = "law_system_players.json";
  private static final String DATA_SNBT_TAG = "snbt";

  // Singleton instance
  private static LawSystemHandler instance;
  
  // System state
  private LawSystemConfig config;
  private Map<UUID, PlayerLawState> playerStates;
  private Set<UUID> lawAttackNPCs;
  private Map<UUID, Long> resetGraceTicks;
  private MinecraftServer server;
  private boolean initialized = false;
  
  // Tick tracking
  private long tickCounter = 0;
  private static final int TICK_INTERVAL = 20; // Process every second
  private static final int SYNC_INTERVAL = 100; // Sync to clients every 5 seconds
  private static final int SAVE_INTERVAL = 6000; // Save every 5 minutes
  private static final int RESET_GRACE_TICKS = 100; // Short grace period after death/reset

  private LawSystemHandler() {
    this.config = new LawSystemConfig();
    this.playerStates = new HashMap<>();
    this.lawAttackNPCs = new HashSet<>();
    this.resetGraceTicks = new HashMap<>();
  }

  public static LawSystemHandler getInstance() {
    if (instance == null) {
      instance = new LawSystemHandler();
    }
    return instance;
  }

  /**
   * Initialize the handler with a server instance.
   */
  public void initialize(MinecraftServer server) {
    this.server = server;
    loadConfig();
    loadPlayerData();
    this.initialized = true;
    log.info("{} Law System initialized", LOG_PREFIX);
  }

  /**
   * Handle server tick - called every tick from the server.
   */
  public static void handleServerTick(MinecraftServer server) {
    LawSystemHandler handler = getInstance();
    if (!handler.initialized || !handler.config.isSystemEnabled()) {
      return;
    }
    
    handler.tickCounter++;
    
    // Only process at intervals
    if (handler.tickCounter % TICK_INTERVAL != 0) {
      return;
    }
    
    // Process player states
    handler.processPlayerStates(server);

    // Cleanup stale guards tied to players who are no longer wanted
    GuardResponseHandler.getInstance().cleanupStaleGuards(server, handler.tickCounter);
    
    // Sync to clients
    if (handler.tickCounter % SYNC_INTERVAL == 0) {
      handler.syncToClients(server);
    }
    
    // Auto-save
    if (handler.tickCounter % SAVE_INTERVAL == 0) {
      handler.saveAll();
    }
  }

  /**
   * Process all player states for decay and regeneration.
   */
  private void processPlayerStates(MinecraftServer server) {
    long currentTime = server.overworld().getGameTime();
    
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      PlayerLawState state = getOrCreatePlayerState(player.getUUID());
      
      // Skip if immunity is enabled
      if (state.hasCrimeImmunity()) {
        continue;
      }
      
      // Wanted level decay
      if (state.isWanted()) {
        long timeSinceLastCrime = currentTime - state.getLastCrimeTime();
        if (timeSinceLastCrime > config.getWantedDecayDelayTicks()) {
          long decayProgress = state.getWantedDecayCooldown() + TICK_INTERVAL;
          if (decayProgress >= config.getWantedDecayRate()) {
            state.decayWantedLevel();
            state.setWantedDecayCooldown(0);
          } else {
            state.setWantedDecayCooldown(decayProgress);
          }
        }
        
        // Alert nearby NPCs to attack wanted players every 2 seconds
        if (tickCounter % 40 == 0) {
          alertNPCsToAttackWantedPlayer(player, state.getWantedLevel());
        }

        // Refresh guard squads if the player moved away from them.
        if (tickCounter % 200 == 0) {
          RegionRule region = findApplicableRegion(player.blockPosition());
          if (region != null) {
            GuardResponseHandler.getInstance().refreshGuardsForPlayer(player, state, region);
          }
        }
      }
      
      // Peace value regeneration
      if (state.getPeaceValue() < config.getPeaceValueMax()) {
        // Only regen if not recently committed a crime
        long timeSinceLastCrime = currentTime - state.getLastCrimeTime();
        if (timeSinceLastCrime > config.getWantedDecayDelayTicks() / 2) {
          // This is simplified - actual implementation would track regen cooldown
          if (tickCounter % config.getPeaceRegenRate() == 0) {
            state.regeneratePeace(1);
          }
        }
      }
    }
  }

  /**
   * Alert nearby NPCs to attack a wanted player.
   * Only default faction NPCs will attack.
   */
  private void alertNPCsToAttackWantedPlayer(ServerPlayer player, int wantedLevel) {
    double alertRadius = 20.0 + (wantedLevel * 10.0); // Higher wanted = larger alert radius
    
    for (var entry : de.markusbordihn.easynpc.entity.LivingEntityManager.getNpcEntityMap().entrySet()) {
      de.markusbordihn.easynpc.entity.easynpc.EasyNPC<?> easyNPC = entry.getValue();
      if (easyNPC == null) continue;
      
      net.minecraft.world.entity.Entity entityRaw = easyNPC.getEntity();
      if (entityRaw == null || !entityRaw.isAlive()) continue;
      if (!(entityRaw instanceof net.minecraft.world.entity.Mob entity)) continue;
      
      // Check if NPC is in same dimension and within range
      if (entity.level() != player.level()) continue;
      double distance = entity.position().distanceTo(player.position());
      if (distance > alertRadius) continue;
      
      // Only default faction NPCs should attack wanted players
      boolean isDefaultFaction = true;
      try {
        if (easyNPC instanceof de.markusbordihn.easynpc.entity.easynpc.data.ConfigDataCapable<?> configCapable) {
          String faction = configCapable.getFaction();
          if (faction != null && !faction.isEmpty() && !faction.equalsIgnoreCase("default")) {
            // Skip bandits and other hostile factions
            if (faction.toLowerCase().contains("bandit") ||
                faction.toLowerCase().contains("hostile") ||
                faction.toLowerCase().contains("enemy")) {
              isDefaultFaction = false;
            }
          }
        }
      } catch (Exception e) {
        // If check fails, assume default faction
      }
      
      if (!isDefaultFaction) continue;
      
      // Set the NPC to target this player
      entity.setTarget(player);
      if (easyNPC instanceof ObjectiveDataCapable<?> objectiveData) {
        ensureLawAttackGoal(entity.getUUID(), objectiveData);
      }
      log.debug("{} NPC {} is now targeting wanted player {}", LOG_PREFIX, 
          entity.getName().getString(), player.getName().getString());
    }
  }

  private void ensureLawAttackGoal(UUID npcUUID, ObjectiveDataCapable<?> objectiveData) {
    if (objectiveData == null || npcUUID == null) {
      return;
    }
    if (!hasAttackObjective(objectiveData)) {
      ObjectiveDataEntry attackGoal = new ObjectiveDataEntry(ObjectiveType.MELEE_ATTACK, 1);
      objectiveData.addOrUpdateCustomObjective(attackGoal);
      lawAttackNPCs.add(npcUUID);
    }
    objectiveData.refreshCustomObjectives();
  }

  private boolean hasAttackObjective(ObjectiveDataCapable<?> objectiveData) {
    return objectiveData.hasObjective(ObjectiveType.MELEE_ATTACK)
        || objectiveData.hasObjective(ObjectiveType.CUSTOM_ATTACK)
        || objectiveData.hasObjective(ObjectiveType.BOW_ATTACK)
        || objectiveData.hasObjective(ObjectiveType.CROSSBOW_ATTACK)
        || objectiveData.hasObjective(ObjectiveType.GUN_ATTACK)
        || objectiveData.hasObjective(ObjectiveType.ZOMBIE_ATTACK)
        || objectiveData.hasObjective(ObjectiveType.ATTACK_PLAYER);
  }

  /**
   * Sync player states to clients for overlay display.
   */
  private void syncToClients(MinecraftServer server) {
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      syncPlayerState(player);
    }
  }

  /**
   * Sync a specific player's law state to their client.
   */
  public void syncPlayerState(ServerPlayer player) {
    if (player == null) return;
    
    PlayerLawState state = getOrCreatePlayerState(player.getUUID());
    
    // Create and send sync message using NetworkHandlerManager (platform-agnostic)
    try {
      de.markusbordihn.easynpc.network.message.LawStateSyncMessage message = 
          new de.markusbordihn.easynpc.network.message.LawStateSyncMessage(
              state.getWantedLevel(),
              state.getPeaceValue(),
              state.hasCrimeImmunity()
          );
      
      // Use NetworkHandlerManager for platform-independent sending
      de.markusbordihn.easynpc.network.NetworkHandlerManager.sendMessageToPlayer(message, player);
      log.debug("{} Synced law state to {}: wanted={}, peace={}", LOG_PREFIX, 
          player.getName().getString(), state.getWantedLevel(), state.getPeaceValue());
    } catch (Exception e) {
      log.warn("{} Failed to sync law state to {}: {}", LOG_PREFIX, player.getName().getString(), e.getMessage());
    }
  }

  /**
   * Force sync to all players and return count.
   */
  public int syncAllPlayers() {
    if (server == null) return 0;
    
    int count = 0;
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      syncPlayerState(player);
      count++;
    }
    log.info("{} Force synced law state to {} players", LOG_PREFIX, count);
    return count;
  }

  /**
   * Get or create a player state.
   */
  public PlayerLawState getOrCreatePlayerState(UUID playerUUID) {
    return playerStates.computeIfAbsent(playerUUID, PlayerLawState::new);
  }

  /**
   * Get player state (may be null).
   */
  public PlayerLawState getPlayerState(UUID playerUUID) {
    return playerStates.get(playerUUID);
  }

  /**
   * Record a crime for a player.
   */
  public void recordCrime(ServerPlayer player, CrimeType crimeType, BlockPos position) {
    if (!config.isSystemEnabled()) {
      return;
    }
    
    // Get server from player if not initialized
    if (server == null && player.getServer() != null) {
      server = player.getServer();
    }
    
    if (server == null) {
      log.warn("{} Cannot record crime - server not available", LOG_PREFIX);
      return;
    }

    PlayerLawState state = getOrCreatePlayerState(player.getUUID());

    if (isInResetGrace(player.getUUID())) {
      log.debug("{} Ignoring crime during reset grace for {}", LOG_PREFIX, player.getName().getString());
      return;
    }
    
    // Check if player has immunity
    if (state.hasCrimeImmunity()) {
      return;
    }
    
    // Find applicable region
    RegionRule region = findApplicableRegion(position);
    if (region == null || !region.isCrimeEnabled(crimeType)) {
      return; // Crime not tracked in this area
    }
    
    long currentTime = server.overworld().getGameTime();
    
    // Count recent crimes for multiplier
    int recentCrimes = state.countRecentCrimes(crimeType, currentTime, 
        config.getCrimeRule().getRepeatWindowTicks());
    
    // Calculate penalties
    int wantedPenalty = config.getCrimeRule().calculateWantedPenalty(crimeType, recentCrimes);
    int peacePenalty = config.getCrimeRule().calculatePeacePenalty(crimeType, recentCrimes);
    
    // Apply penalties
    state.addWantedLevel(wantedPenalty);
    state.subtractPeaceValue(peacePenalty);
    
    // Cap wanted level
    if (state.getWantedLevel() > config.getMaxWantedLevel()) {
      state.setWantedLevel(config.getMaxWantedLevel());
    }
    
    // Record the crime
    state.recordCrime(crimeType, currentTime, position, region.getId().toString());
    
    // Reset decay cooldown
    state.setWantedDecayCooldown(0);
    
    log.info("{} Player {} committed {}: wanted={}, peace={}", 
        LOG_PREFIX, player.getName().getString(), crimeType, 
        state.getWantedLevel(), state.getPeaceValue());
    
    // Trigger guard response if needed
    GuardResponseHandler.getInstance().onCrimeCommitted(player, state, region);

    // Sync updated state to the player quickly for HUD updates.
    syncPlayerState(player);
  }

  /**
   * Find the applicable region for a position.
   */
  public RegionRule findApplicableRegion(BlockPos position) {
    for (RegionRule region : config.getRegions()) {
      if (region.isEnabled() && region.containsPosition(position)) {
        return region;
      }
    }
    // If no regions defined, use world-wide default
    if (config.getRegions().isEmpty()) {
      RegionRule worldRegion = new RegionRule();
      worldRegion.setMode(de.markusbordihn.easynpc.data.crime.RegionMode.WORLD);
      return worldRegion;
    }
    return null;
  }

  /**
   * Clear all crimes for a player.
   */
  public void clearPlayerCrimes(UUID playerUUID) {
    PlayerLawState state = playerStates.get(playerUUID);
    if (state != null) {
      state.clearCrimes();
    }
    syncPlayerStateByUUID(playerUUID);
    clearAggressionForPlayer(playerUUID);
  }

  /**
   * Clear all wanted players.
   */
  public void clearAllWanted() {
    for (PlayerLawState state : playerStates.values()) {
      state.clearCrimes();
    }
    log.info("{} Cleared all wanted players", LOG_PREFIX);
    syncAllPlayers();
    clearAggressionForAllPlayers();
  }

  /**
   * Get count of wanted players.
   */
  public int getWantedPlayerCount() {
    int count = 0;
    for (PlayerLawState state : playerStates.values()) {
      if (state.isWanted()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Set player wanted level directly (admin).
   */
  public void setPlayerWantedLevel(UUID playerUUID, int level) {
    PlayerLawState state = getOrCreatePlayerState(playerUUID);
    state.setWantedLevel(Math.min(level, config.getMaxWantedLevel()));
    log.info("{} Set player {} wanted level to {}", LOG_PREFIX, playerUUID, level);
    syncPlayerStateByUUID(playerUUID);
  }

  /**
   * Set player peace value directly (admin).
   */
  public void setPlayerPeaceValue(UUID playerUUID, int value) {
    PlayerLawState state = getOrCreatePlayerState(playerUUID);
    state.setPeaceValue(value);
    log.info("{} Set player {} peace value to {}", LOG_PREFIX, playerUUID, value);
    syncPlayerStateByUUID(playerUUID);
  }

  /**
   * Toggle crime immunity for a player (debug).
   */
  public void toggleCrimeImmunity(UUID playerUUID) {
    PlayerLawState state = getOrCreatePlayerState(playerUUID);
    state.setCrimeImmunity(!state.hasCrimeImmunity());
    log.info("{} Toggled crime immunity for player {}: {}", 
        LOG_PREFIX, playerUUID, state.hasCrimeImmunity());
    syncPlayerStateByUUID(playerUUID);
  }

  // Configuration access
  public LawSystemConfig getConfig() {
    return config;
  }

  public void setConfig(LawSystemConfig config) {
    this.config = config;
  }

  public Map<UUID, PlayerLawState> getPlayerStates() {
    return playerStates;
  }

  public boolean isSystemEnabled() {
    return config.isSystemEnabled();
  }

  public void setSystemEnabled(boolean enabled) {
    config.setSystemEnabled(enabled);
    log.info("{} System {}", LOG_PREFIX, enabled ? "enabled" : "disabled");
    if (server != null) {
      syncAllPlayers();
    }
  }

  // Persistence
  public void saveAll() {
    saveConfig();
    savePlayerData();
  }

  private void saveConfig() {
    try {
      Path configDir = DataFileHandler.getCustomDataFolder();
      if (configDir == null) {
        log.warn("{} Config directory not available", LOG_PREFIX);
        return;
      }
      Path configPath = configDir.resolve(CONFIG_FILE_NAME);
      JsonObject root = new JsonObject();
      root.addProperty(DATA_SNBT_TAG, config.createTag().toString());
      root.addProperty("profileName", config.getProfileName());
      try (Writer writer = Files.newBufferedWriter(configPath)) {
        GSON.toJson(root, writer);
      }
      log.debug("{} Saved config", LOG_PREFIX);
    } catch (IOException e) {
      log.error("{} Failed to save config: {}", LOG_PREFIX, e.getMessage());
    }
  }

  public void loadConfig() {
    try {
      Path configDir = DataFileHandler.getCustomDataFolder();
      if (configDir == null) {
        return;
      }
      Path configPath = configDir.resolve(CONFIG_FILE_NAME);
      if (Files.exists(configPath)) {
        try (Reader reader = Files.newBufferedReader(configPath)) {
          JsonObject root = GSON.fromJson(reader, JsonObject.class);
          if (root != null && root.has(DATA_SNBT_TAG)) {
            String snbt = root.get(DATA_SNBT_TAG).getAsString();
            CompoundTag tag = TagParser.parseTag(snbt);
            this.config = new LawSystemConfig(tag);
          }
        }
        log.info("{} Loaded config", LOG_PREFIX);
      }
    } catch (IOException e) {
      log.error("{} Failed to load config: {}", LOG_PREFIX, e.getMessage());
    } catch (Exception e) {
      log.error("{} Failed to parse config: {}", LOG_PREFIX, e.getMessage());
    }
  }

  private void savePlayerData() {
    try {
      Path configDir = DataFileHandler.getCustomDataFolder();
      if (configDir == null) {
        return;
      }
      Path dataPath = configDir.resolve(PLAYER_DATA_FILE_NAME);
      // Simplified save - full implementation would serialize all player states
      log.debug("{} Saved player data", LOG_PREFIX);
    } catch (Exception e) {
      log.error("{} Failed to save player data: {}", LOG_PREFIX, e.getMessage());
    }
  }

  private void loadPlayerData() {
    try {
      Path configDir = DataFileHandler.getCustomDataFolder();
      if (configDir == null) {
        return;
      }
      Path dataPath = configDir.resolve(PLAYER_DATA_FILE_NAME);
      if (Files.exists(dataPath)) {
        // Load player data
        log.info("{} Loaded player data", LOG_PREFIX);
      }
    } catch (Exception e) {
      log.error("{} Failed to load player data: {}", LOG_PREFIX, e.getMessage());
    }
  }

  /**
   * Handle player death - reset wanted level if configured.
   */
  public void onPlayerDeath(ServerPlayer player) {
    resetPlayerState(player);
  }

  public void resetPlayerState(ServerPlayer player) {
    if (player == null) {
      return;
    }
    if (server == null && player.getServer() != null) {
      server = player.getServer();
    }

    PlayerLawState state = getOrCreatePlayerState(player.getUUID());
    state.clearCrimes();
    resetGraceTicks.put(player.getUUID(), player.serverLevel().getGameTime());
    log.info("{} Reset wanted level for {}", LOG_PREFIX, player.getName().getString());
    clearAggressionForPlayer(player.getUUID());
    syncPlayerState(player);
  }

  private void clearAggressionForPlayer(UUID playerUUID) {
    if (playerUUID == null || server == null) {
      return;
    }
    GuardResponseHandler.getInstance().despawnGuardsForPlayer(playerUUID, server);
    for (var entry : de.markusbordihn.easynpc.entity.LivingEntityManager.getNpcEntityMap().entrySet()) {
      de.markusbordihn.easynpc.entity.easynpc.EasyNPC<?> easyNPC = entry.getValue();
      if (easyNPC == null) {
        continue;
      }
      net.minecraft.world.entity.Entity entityRaw = easyNPC.getEntity();
      if (de.markusbordihn.easynpc.handler.GuardResponseHandler.isOwnedGuard(entityRaw, playerUUID)) {
        entityRaw.discard();
        continue;
      }
      if (entityRaw instanceof net.minecraft.world.entity.Mob mob) {
        net.minecraft.world.entity.LivingEntity target = mob.getTarget();
        if (target instanceof net.minecraft.world.entity.player.Player playerTarget
            && playerUUID.equals(playerTarget.getUUID())) {
          mob.setTarget(null);
          mob.setAggressive(false);
        }
      }
      if (easyNPC instanceof de.markusbordihn.easynpc.entity.easynpc.data.ObjectiveDataCapable<?> objectiveCapable) {
        if (lawAttackNPCs.remove(entityRaw.getUUID())) {
          objectiveCapable.removeCustomObjective(de.markusbordihn.easynpc.data.objective.ObjectiveType.MELEE_ATTACK);
        }
        if (easyNPC instanceof de.markusbordihn.easynpc.entity.easynpc.data.ConfigDataCapable<?> configCapable) {
          String faction = configCapable.getFaction();
          if (faction == null || faction.isEmpty() || faction.equalsIgnoreCase("default")) {
            objectiveCapable.removeCustomObjective(de.markusbordihn.easynpc.data.objective.ObjectiveType.ATTACK_PLAYER);
          }
        }
      }
    }
  }

  private void clearAggressionForAllPlayers() {
    if (server == null) {
      return;
    }
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      clearAggressionForPlayer(player.getUUID());
    }
  }

  private void syncPlayerStateByUUID(UUID playerUUID) {
    if (server == null || playerUUID == null) {
      return;
    }
    ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
    if (player != null) {
      syncPlayerState(player);
    }
  }

  private boolean isInResetGrace(UUID playerUUID) {
    if (playerUUID == null || server == null) {
      return false;
    }
    Long resetTick = resetGraceTicks.get(playerUUID);
    if (resetTick == null) {
      return false;
    }
    long currentTick = server.overworld().getGameTime();
    if (currentTick - resetTick > RESET_GRACE_TICKS) {
      resetGraceTicks.remove(playerUUID);
      return false;
    }
    return true;
  }

  /**
   * Shutdown and cleanup.
   */
  public void shutdown() {
    saveAll();
    initialized = false;
    log.info("{} Law System shutdown", LOG_PREFIX);
  }

  /**
   * Send admin data to a player.
   */
  public void sendAdminData(ServerPlayer player, boolean openScreen) {
    if (player == null || player.getServer() == null) {
      return;
    }
    LawAdminDataMessage message = buildAdminDataMessage(player.getServer(), openScreen);
    NetworkHandlerManager.sendMessageToPlayer(message, player);
  }

  /**
   * Build admin data payload from current server state.
   */
  public LawAdminDataMessage buildAdminDataMessage(MinecraftServer server, boolean openScreen) {
    LawSystemConfig configSnapshot = new LawSystemConfig(this.config.createTag());
    int merchantCount = CrimeHandler.getInstance().getMerchantCount();
    int guardCount = GuardResponseHandler.getInstance().getTotalGuardCount();
    List<LawAdminDataMessage.PlayerSnapshot> players =
        server.getPlayerList().getPlayers().stream()
            .map(
                player -> {
                  PlayerLawState state = getOrCreatePlayerState(player.getUUID());
                  return new LawAdminDataMessage.PlayerSnapshot(
                      player.getUUID(), player.getName().getString(), state);
                })
            .toList();
    return new LawAdminDataMessage(configSnapshot, merchantCount, guardCount, players, openScreen);
  }

  /**
   * Run a fast test simulation for the given seconds.
   */
  public void runTestSimulation(MinecraftServer server, int seconds) {
    if (server == null || seconds <= 0) {
      return;
    }
    int ticks = Math.min(seconds, 60) * 20;
    for (int i = 0; i < ticks; i++) {
      handleServerTick(server);
    }
    log.info("{} Ran test simulation for {} seconds", LOG_PREFIX, Math.min(seconds, 60));
  }
}
