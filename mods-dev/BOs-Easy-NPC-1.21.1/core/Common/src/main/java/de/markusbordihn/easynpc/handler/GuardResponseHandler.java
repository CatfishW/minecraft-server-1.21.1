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

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.config.NPCTemplateManager;
import de.markusbordihn.easynpc.data.crime.GuardTier;
import de.markusbordihn.easynpc.data.crime.LawSystemConfig;
import de.markusbordihn.easynpc.data.crime.PlayerLawState;
import de.markusbordihn.easynpc.data.crime.RegionRule;
import de.markusbordihn.easynpc.data.objective.ObjectiveDataEntry;
import de.markusbordihn.easynpc.data.objective.ObjectiveType;
import de.markusbordihn.easynpc.entity.easynpc.data.ObjectiveDataCapable;
import de.markusbordihn.easynpc.handler.CrimeHandler.NPCRoleType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handler for guard spawning and response to crimes.
 */
public class GuardResponseHandler {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String LOG_PREFIX = "[GuardResponseHandler]";
  private static final Random RANDOM = new Random();

  private static GuardResponseHandler instance;

  // Track spawned guards per player
  private Map<UUID, Set<UUID>> playerGuards;
  // Track total guards per region
  private Map<UUID, Integer> regionGuardCount;
  // Cooldowns per player
  private Map<UUID, Long> spawnCooldowns;
  private Map<UUID, Long> refreshCooldowns;

  private static final int SPAWN_COOLDOWN_TICKS = 600; // 30 seconds between spawns
  private static final int WANTED_SPAWN_MIN_RADIUS = 15;
  private static final int WANTED_SPAWN_MAX_RADIUS = 30;
  private static final int GUARD_DESPAWN_DISTANCE = 80;
  private static final int GUARD_REFRESH_COOLDOWN_TICKS = 200; // 10 seconds

  private static final String TEMPLATE_TOWN_GUARD = "town_guard";
  private static final String TEMPLATE_ELITE_TOWN_GUARD = "elite_town_guard";
  private static final String LAW_GUARD_TAG = "easy_npc_law_guard";
  private static final String LAW_GUARD_OWNER_PREFIX = "easy_npc_law_owner:";
  private static final int STALE_GUARD_CLEANUP_INTERVAL_TICKS = 40;

  private GuardResponseHandler() {
    this.playerGuards = new HashMap<>();
    this.regionGuardCount = new HashMap<>();
    this.spawnCooldowns = new HashMap<>();
    this.refreshCooldowns = new HashMap<>();
  }

  public static GuardResponseHandler getInstance() {
    if (instance == null) {
      instance = new GuardResponseHandler();
    }
    return instance;
  }

  /**
   * Called when a crime is committed - potentially spawn guards.
   */
  public void onCrimeCommitted(ServerPlayer player, PlayerLawState state, RegionRule region) {
    if (!LawSystemHandler.getInstance().isSystemEnabled()) {
      return;
    }

    int wantedLevel = state.getWantedLevel();
    if (wantedLevel <= 0) {
      return;
    }

    // Check spawn cooldown
    Long lastSpawn = spawnCooldowns.get(player.getUUID());
    long currentTime = player.level().getGameTime();
    if (lastSpawn != null && currentTime - lastSpawn < SPAWN_COOLDOWN_TICKS) {
      return;
    }

    // Check region guard cap
    int currentGuards = getGuardCountForPlayer(player.getUUID());
    if (currentGuards >= region.getGuardSpawnCap()) {
      log.debug("{} Guard cap reached for player {}", LOG_PREFIX, player.getName().getString());
      return;
    }

    // Get appropriate guard tier
    LawSystemConfig config = LawSystemHandler.getInstance().getConfig();
    GuardTier tier = config.getGuardTierForWantedLevel(wantedLevel);
    if (tier == null) {
      log.warn("{} No guard tier found for wanted level {}", LOG_PREFIX, wantedLevel);
      return;
    }

    SpawnConfig spawnConfig = getSpawnConfigForWantedLevel(wantedLevel, tier);
    if (spawnConfig == null || spawnConfig.spawnCount <= 0) {
      return;
    }

    int availableSlots = Math.max(0, region.getGuardSpawnCap() - currentGuards);
    if (availableSlots <= 0) {
      log.debug("{} Guard cap reached for player {}", LOG_PREFIX, player.getName().getString());
      return;
    }

    // Spawn guards
    int spawnCount = Math.min(spawnConfig.spawnCount, availableSlots);
    spawnGuardSquad(player, tier, region, spawnConfig.templateName,
        spawnConfig.minRadius, spawnConfig.maxRadius, spawnCount);
    spawnCooldowns.put(player.getUUID(), currentTime);
  }

  /**
   * Spawn a guard squad for a player.
   */
  public void spawnGuardSquad(ServerPlayer player, GuardTier tier, RegionRule region) {
    spawnGuardSquad(
        player,
        tier,
        region,
        tier.getTemplateName(),
        tier.getSpawnRadius(),
        tier.getSpawnRadius(),
        tier.getSquadSize());
  }

  /**
   * Refresh guards around a wanted player when they move far away.
   */
  public void refreshGuardsForPlayer(ServerPlayer player, PlayerLawState state, RegionRule region) {
    if (player == null || state == null || region == null) {
      return;
    }
    if (!LawSystemHandler.getInstance().isSystemEnabled() || state.getWantedLevel() <= 0) {
      return;
    }

    UUID playerUUID = player.getUUID();
    long currentTime = player.level().getGameTime();
    Long lastRefresh = refreshCooldowns.get(playerUUID);
    if (lastRefresh != null && currentTime - lastRefresh < GUARD_REFRESH_COOLDOWN_TICKS) {
      return;
    }

    ServerLevel level = player.serverLevel();
    int removedGuards = despawnGuardsTooFar(player, level);
    if (removedGuards > 0) {
      adjustRegionGuardCount(region.getId(), -removedGuards);
    }

    if (getGuardCountForPlayer(playerUUID) <= 0) {
      LawSystemConfig config = LawSystemHandler.getInstance().getConfig();
      GuardTier tier = config.getGuardTierForWantedLevel(state.getWantedLevel());
      if (tier != null) {
        SpawnConfig spawnConfig = getSpawnConfigForWantedLevel(state.getWantedLevel(), tier);
        if (spawnConfig != null && spawnConfig.spawnCount > 0) {
          int availableSlots = Math.max(0, region.getGuardSpawnCap());
          int spawnCount = Math.min(spawnConfig.spawnCount, availableSlots);
          if (spawnCount > 0) {
            spawnGuardSquad(player, tier, region, spawnConfig.templateName,
                spawnConfig.minRadius, spawnConfig.maxRadius, spawnCount);
            spawnCooldowns.put(playerUUID, currentTime);
          }
        }
      }
    }

    refreshCooldowns.put(playerUUID, currentTime);
  }

  /**
   * Find a valid spawn position near the player.
   */
  private BlockPos findSpawnPosition(ServerLevel level, BlockPos center, int radius) {
    return findSpawnPosition(level, center, 0, radius);
  }

  private BlockPos findSpawnPosition(ServerLevel level, BlockPos center, int minRadius, int maxRadius) {
    int clampedMin = Math.max(0, minRadius);
    int clampedMax = Math.max(clampedMin, maxRadius);
    for (int attempt = 0; attempt < 12; attempt++) {
      int offsetX = RANDOM.nextInt(clampedMax * 2 + 1) - clampedMax;
      int offsetZ = RANDOM.nextInt(clampedMax * 2 + 1) - clampedMax;
      if (offsetX == 0 && offsetZ == 0) {
        continue;
      }
      if (clampedMin > 0 && (offsetX * offsetX + offsetZ * offsetZ) < clampedMin * clampedMin) {
        continue;
      }

      BlockPos testPos = center.offset(offsetX, 0, offsetZ);
      // Find ground level
      BlockPos groundPos = level.getHeightmapPos(
          net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 
          testPos);

      // Validate spawn position
      if (isValidSpawnPosition(level, groundPos)) {
        return groundPos;
      }
    }
    return null;
  }

  /**
   * Check if a position is valid for spawning.
   */
  private boolean isValidSpawnPosition(ServerLevel level, BlockPos pos) {
    // Check that there's solid ground and air above
    return level.getBlockState(pos.below()).isSolid() &&
           level.getBlockState(pos).isAir() &&
           level.getBlockState(pos.above()).isAir();
  }

  /**
   * Spawn a guard at a specific position using Easy NPC templates.
   */
  private UUID spawnGuardAtPosition(
      ServerLevel level, BlockPos pos, GuardTier tier, String templateName, UUID targetPlayerUUID) {
    String spawnTemplate = templateName;
    if (spawnTemplate == null || spawnTemplate.isEmpty()) {
      spawnTemplate = tier != null ? tier.getTemplateName() : null;
    }
    if (spawnTemplate == null || spawnTemplate.isEmpty()) {
      // Generate a default guard template name based on tier
      spawnTemplate = "guard_tier_" + (tier != null ? tier.getTier() : 1);
    }

    // Try to spawn using NPCTemplateManager
    try {
      // Use correct method signature: spawnEntityFromTemplate returns Entity
      Entity entity = NPCTemplateManager.spawnEntityFromTemplate(
          level, spawnTemplate, pos.getX(), pos.getY(), pos.getZ());
      if (entity != null) {
        UUID guardUUID = entity.getUUID();
        log.debug("{} Spawned guard {} at {}", LOG_PREFIX, guardUUID, pos);

        if (entity instanceof de.markusbordihn.easynpc.entity.easynpc.EasyNPC<?> easyNPC) {
          tagGuardEntity(entity, targetPlayerUUID);
          CrimeHandler.getInstance().registerNPCRole(guardUUID, NPCRoleType.GUARD);
          if (targetPlayerUUID != null) {
            setGuardTarget(level, easyNPC, targetPlayerUUID);
          }
        }
        
        return guardUUID;
      }
    } catch (Exception e) {
      log.error("{} Failed to spawn guard: {}", LOG_PREFIX, e.getMessage());
    }
    
    return null;
  }

  private void tagGuardEntity(Entity entity, UUID targetPlayerUUID) {
    if (entity == null) {
      return;
    }
    entity.addTag(LAW_GUARD_TAG);
    if (targetPlayerUUID != null) {
      entity.addTag(getGuardOwnerTag(targetPlayerUUID));
    }
  }

  private void setGuardTarget(
      ServerLevel level, de.markusbordihn.easynpc.entity.easynpc.EasyNPC<?> easyNPC, UUID targetPlayerUUID) {
    if (easyNPC == null || targetPlayerUUID == null || level == null) {
      return;
    }
    ServerPlayer targetPlayer = level.getServer().getPlayerList().getPlayer(targetPlayerUUID);
    if (targetPlayer == null) {
      return;
    }
    Entity entity = easyNPC.getEntity();
    if (entity instanceof net.minecraft.world.entity.Mob mob) {
      mob.setTarget(targetPlayer);
    }
    ensureAttackGoal(easyNPC);
  }

  private void ensureAttackGoal(de.markusbordihn.easynpc.entity.easynpc.EasyNPC<?> easyNPC) {
    ObjectiveDataCapable<?> objectiveData = easyNPC.getEasyNPCObjectiveData();
    if (objectiveData == null) {
      return;
    }
    if (!hasAttackObjective(objectiveData)) {
      ObjectiveDataEntry attackGoal = new ObjectiveDataEntry(ObjectiveType.MELEE_ATTACK, 1);
      objectiveData.addOrUpdateCustomObjective(attackGoal);
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
   * Get count of guards currently active for a player.
   */
  public int getGuardCountForPlayer(UUID playerUUID) {
    Set<UUID> guards = playerGuards.get(playerUUID);
    return guards != null ? guards.size() : 0;
  }

  /**
   * Get total active guard count.
   */
  public int getTotalGuardCount() {
    int total = 0;
    for (Set<UUID> guards : playerGuards.values()) {
      total += guards.size();
    }
    return total;
  }

  /**
   * Called when a guard dies or despawns.
   */
  public void onGuardRemoved(UUID guardUUID) {
    // Remove from all player tracking
    for (Set<UUID> guards : playerGuards.values()) {
      guards.remove(guardUUID);
    }
    log.debug("{} Guard {} removed", LOG_PREFIX, guardUUID);
  }

  /**
   * Despawn all guards for a specific player.
   */
  public void despawnGuardsForPlayer(UUID playerUUID, ServerLevel level) {
    Set<UUID> guards = playerGuards.get(playerUUID);
    if (guards == null || guards.isEmpty()) {
      return;
    }

    for (UUID guardUUID : new ArrayList<>(guards)) {
      Entity guard = level.getEntity(guardUUID);
      if (guard != null) {
        guard.discard();
      }
    }
    guards.clear();
    spawnCooldowns.remove(playerUUID);
    refreshCooldowns.remove(playerUUID);
    log.info("{} Despawned all guards for player {}", LOG_PREFIX, playerUUID);
  }

  /**
   * Despawn all guards for a player across all levels.
   */
  public void despawnGuardsForPlayer(UUID playerUUID, MinecraftServer server) {
    if (server == null || playerUUID == null) {
      return;
    }
    Set<UUID> guards = playerGuards.get(playerUUID);
    if (guards == null || guards.isEmpty()) {
      despawnGuardsByTag(playerUUID);
      return;
    }

    for (UUID guardUUID : new ArrayList<>(guards)) {
      Entity guard = null;
      for (ServerLevel level : server.getAllLevels()) {
        guard = level.getEntity(guardUUID);
        if (guard != null) {
          guard.discard();
          break;
        }
      }
      guards.remove(guardUUID);
    }
    playerGuards.remove(playerUUID);
    spawnCooldowns.remove(playerUUID);
    refreshCooldowns.remove(playerUUID);
    despawnGuardsByTag(playerUUID);
    log.info("{} Despawned all guards for player {}", LOG_PREFIX, playerUUID);
  }

  public int cleanupStaleGuards(MinecraftServer server, long tickCounter) {
    if (server == null || tickCounter % STALE_GUARD_CLEANUP_INTERVAL_TICKS != 0) {
      return 0;
    }
    int removed = 0;
    for (var entry : de.markusbordihn.easynpc.entity.LivingEntityManager.getNpcEntityMap().entrySet()) {
      de.markusbordihn.easynpc.entity.easynpc.EasyNPC<?> easyNPC = entry.getValue();
      if (easyNPC == null) {
        continue;
      }
      Entity entity = easyNPC.getEntity();
      if (entity == null || !entity.isAlive()) {
        continue;
      }
      UUID ownerUUID = getGuardOwnerUUID(entity);
      if (ownerUUID == null) {
        continue;
      }
      ServerPlayer owner = server.getPlayerList().getPlayer(ownerUUID);
      PlayerLawState state = LawSystemHandler.getInstance().getPlayerState(ownerUUID);
      boolean ownerWanted = owner != null && state != null && state.isWanted();
      if (!ownerWanted) {
        entity.discard();
        onGuardRemoved(entity.getUUID());
        removed++;
      }
    }
    return removed;
  }
  /**
   * Despawn all guards in the system.
   */
  public void despawnAllGuards(ServerLevel level) {
    for (Map.Entry<UUID, Set<UUID>> entry : playerGuards.entrySet()) {
      for (UUID guardUUID : entry.getValue()) {
        Entity guard = level.getEntity(guardUUID);
        if (guard != null) {
          guard.discard();
        }
      }
    }
    playerGuards.clear();
    regionGuardCount.clear();
    log.info("{} Despawned all guards", LOG_PREFIX);
  }

  /**
   * Spawn a patrol at a specific location (admin tool).
   */
  public void spawnPatrolAt(ServerLevel level, BlockPos pos, int tierLevel) {
    LawSystemConfig config = LawSystemHandler.getInstance().getConfig();
    List<GuardTier> tiers = config.getGuardTiers();
    
    GuardTier tier = null;
    for (GuardTier t : tiers) {
      if (t.getTier() == tierLevel) {
        tier = t;
        break;
      }
    }
    
    if (tier == null && !tiers.isEmpty()) {
      tier = tiers.get(0);
    }
    
    if (tier != null) {
      for (int i = 0; i < tier.getSquadSize(); i++) {
        BlockPos spawnPos = findSpawnPosition(level, pos, 10);
        if (spawnPos != null) {
          spawnGuardAtPosition(level, spawnPos, tier, tier.getTemplateName(), null);
        }
      }
      log.info("{} Spawned patrol at {}", LOG_PREFIX, pos);
    }
  }

  /**
   * Spawn a pursuit squad targeting a specific player (admin tool).
   */
  public void spawnPursuitSquad(ServerPlayer targetPlayer, int tierLevel) {
    LawSystemConfig config = LawSystemHandler.getInstance().getConfig();
    GuardTier tier = null;
    
    for (GuardTier t : config.getGuardTiers()) {
      if (t.getTier() == tierLevel) {
        tier = t;
        break;
      }
    }
    
    if (tier == null) {
      tier = config.getGuardTierForWantedLevel(tierLevel);
    }
    
    if (tier != null) {
      RegionRule dummyRegion = new RegionRule();
      dummyRegion.setGuardSpawnCap(100); // High cap for admin spawns
      spawnGuardSquad(targetPlayer, tier, dummyRegion);
      log.info("{} Spawned pursuit squad for {}", LOG_PREFIX, targetPlayer.getName().getString());
    }
  }

  /**
   * Clear all tracking data.
   */
  public void clear() {
    playerGuards.clear();
    regionGuardCount.clear();
    spawnCooldowns.clear();
    refreshCooldowns.clear();
  }

  private SpawnConfig getSpawnConfigForWantedLevel(int wantedLevel, GuardTier tier) {
    int level = Math.max(0, wantedLevel);
    String templateName = tier != null ? tier.getTemplateName() : null;
    int spawnCount = tier != null ? tier.getSquadSize() : 1;

    if (level <= 1) {
      templateName = TEMPLATE_TOWN_GUARD;
      spawnCount = 1 + RANDOM.nextInt(5);
    } else if (level == 2) {
      templateName = TEMPLATE_TOWN_GUARD;
      spawnCount = 2 + RANDOM.nextInt(2);
    } else if (level == 3) {
      templateName = TEMPLATE_TOWN_GUARD;
      spawnCount = 3 + RANDOM.nextInt(2);
    } else if (level == 4) {
      templateName = TEMPLATE_TOWN_GUARD;
      spawnCount = 4 + RANDOM.nextInt(2);
    } else if (level >= 5) {
      templateName = TEMPLATE_ELITE_TOWN_GUARD;
      spawnCount = 5;
    }

    return new SpawnConfig(templateName, WANTED_SPAWN_MIN_RADIUS, WANTED_SPAWN_MAX_RADIUS, spawnCount);
  }

  private void spawnGuardSquad(
      ServerPlayer player,
      GuardTier tier,
      RegionRule region,
      String templateName,
      int minRadius,
      int maxRadius,
      int spawnCount) {
    ServerLevel level = player.serverLevel();
    BlockPos playerPos = player.blockPosition();

    log.info("{} Spawning tier {} squad ({} guards) for player {}",
        LOG_PREFIX, tier.getTier(), spawnCount, player.getName().getString());

    Set<UUID> spawnedGuards = playerGuards.computeIfAbsent(player.getUUID(), k -> new HashSet<>());

    for (int i = 0; i < spawnCount; i++) {
      // Find spawn position
      BlockPos spawnPos = findSpawnPosition(level, playerPos, minRadius, maxRadius);
      if (spawnPos == null) {
        log.warn("{} Could not find valid spawn position", LOG_PREFIX);
        continue;
      }

      // Spawn guard using Easy NPC template system
      UUID guardUUID = spawnGuardAtPosition(level, spawnPos, tier, templateName, player.getUUID());
      if (guardUUID != null) {
        spawnedGuards.add(guardUUID);
      }
    }

    // Update region count
    UUID regionId = region.getId();
    regionGuardCount.merge(regionId, spawnCount, Integer::sum);
  }

  private int despawnGuardsTooFar(ServerPlayer player, ServerLevel level) {
    UUID playerUUID = player.getUUID();
    Set<UUID> guards = playerGuards.get(playerUUID);
    if (guards == null || guards.isEmpty()) {
      return 0;
    }

    int removed = 0;
    for (UUID guardUUID : new ArrayList<>(guards)) {
      Entity guard = level.getEntity(guardUUID);
      if (guard == null || !guard.isAlive() || guard.level() != level) {
        guards.remove(guardUUID);
        removed++;
        continue;
      }
      if (guard.distanceTo(player) > GUARD_DESPAWN_DISTANCE) {
        guard.discard();
        guards.remove(guardUUID);
        removed++;
      }
    }
    if (guards.isEmpty()) {
      playerGuards.remove(playerUUID);
    }
    return removed;
  }

  private void adjustRegionGuardCount(UUID regionId, int delta) {
    if (regionId == null || delta == 0) {
      return;
    }
    int current = regionGuardCount.getOrDefault(regionId, 0);
    int updated = Math.max(0, current + delta);
    if (updated == 0) {
      regionGuardCount.remove(regionId);
    } else {
      regionGuardCount.put(regionId, updated);
    }
  }

  private int despawnGuardsByTag(UUID playerUUID) {
    int removed = 0;
    for (var entry : de.markusbordihn.easynpc.entity.LivingEntityManager.getNpcEntityMap().entrySet()) {
      de.markusbordihn.easynpc.entity.easynpc.EasyNPC<?> easyNPC = entry.getValue();
      if (easyNPC == null) {
        continue;
      }
      Entity entity = easyNPC.getEntity();
      if (isOwnedGuard(entity, playerUUID)) {
        entity.discard();
        removed++;
      }
    }
    return removed;
  }

  public static String getGuardOwnerTag(UUID playerUUID) {
    return LAW_GUARD_OWNER_PREFIX + playerUUID;
  }

  public static UUID getGuardOwnerUUID(Entity entity) {
    if (entity == null) {
      return null;
    }
    for (String tag : entity.getTags()) {
      if (tag.startsWith(LAW_GUARD_OWNER_PREFIX)) {
        String uuidString = tag.substring(LAW_GUARD_OWNER_PREFIX.length());
        try {
          return UUID.fromString(uuidString);
        } catch (IllegalArgumentException ignored) {
          return null;
        }
      }
    }
    return null;
  }

  public static boolean isOwnedGuard(Entity entity, UUID playerUUID) {
    if (entity == null || playerUUID == null) {
      return false;
    }
    return entity.getTags().contains(LAW_GUARD_TAG)
        && entity.getTags().contains(getGuardOwnerTag(playerUUID));
  }

  private static class SpawnConfig {
    private final String templateName;
    private final int minRadius;
    private final int maxRadius;
    private final int spawnCount;

    private SpawnConfig(String templateName, int minRadius, int maxRadius, int spawnCount) {
      this.templateName = templateName;
      this.minRadius = minRadius;
      this.maxRadius = maxRadius;
      this.spawnCount = spawnCount;
    }
  }
}
