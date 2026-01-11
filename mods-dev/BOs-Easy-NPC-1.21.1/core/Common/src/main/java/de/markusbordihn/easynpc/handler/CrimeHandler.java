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
import de.markusbordihn.easynpc.data.crime.CrimeType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handler for detecting and processing crimes related to NPC interactions.
 * Integrates with Easy NPC entity system.
 */
public class CrimeHandler {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String LOG_PREFIX = "[CrimeHandler]";

  private static CrimeHandler instance;

  // Track NPCs that are merchants/guards for crime detection
  private Map<UUID, NPCRoleType> npcRoles;

  public enum NPCRoleType {
    MERCHANT,
    GUARD,
    CIVILIAN
  }

  private CrimeHandler() {
    this.npcRoles = new HashMap<>();
  }

  public static CrimeHandler getInstance() {
    if (instance == null) {
      instance = new CrimeHandler();
    }
    return instance;
  }

  /**
   * Register an NPC's role for crime detection.
   */
  public void registerNPCRole(UUID npcUUID, NPCRoleType role) {
    npcRoles.put(npcUUID, role);
    log.debug("{} Registered NPC {} as {}", LOG_PREFIX, npcUUID, role);
  }

  /**
   * Unregister an NPC when it's removed.
   */
  public void unregisterNPC(UUID npcUUID) {
    npcRoles.remove(npcUUID);
  }

  /**
   * Get the role of an NPC.
   */
  public NPCRoleType getNPCRole(UUID npcUUID) {
    return npcRoles.getOrDefault(npcUUID, NPCRoleType.CIVILIAN);
  }

  /**
   * Called when an NPC is killed - detect if it's a crime.
   */
  public void onNPCKilled(Entity killer, Entity victim) {
    if (!(killer instanceof ServerPlayer player)) {
      return; // Only track player crimes
    }
    if (!player.isAlive() || player.isRemoved() || player.isDeadOrDying()) {
      return;
    }

    if (!LawSystemHandler.getInstance().isSystemEnabled()) {
      return;
    }
    
    // Check if the victim is an EasyNPC
    if (!(victim instanceof de.markusbordihn.easynpc.entity.easynpc.EasyNPC<?> easyNPC)) {
      return;
    }
    
    // Skip NPCs from hostile factions (bandits, etc.)
    // Only track crimes for default/friendly faction NPCs
    try {
      if (easyNPC instanceof de.markusbordihn.easynpc.entity.easynpc.data.ConfigDataCapable<?> configCapable) {
        String factionName = configCapable.getFaction();
        // Skip if faction indicates a hostile/bandit NPC
        if (factionName != null && 
            (factionName.toLowerCase().contains("bandit") ||
             factionName.toLowerCase().contains("hostile") ||
             factionName.toLowerCase().contains("enemy") ||
             factionName.toLowerCase().contains("raider") ||
             factionName.toLowerCase().contains("pirate"))) {
          log.debug("{} Skipping crime for hostile faction NPC: {}", LOG_PREFIX, factionName);
          return;
        }
      }
    } catch (Exception e) {
      // If faction check fails, continue with crime detection
      log.debug("{} Could not check faction: {}", LOG_PREFIX, e.getMessage());
    }

    UUID victimUUID = victim.getUUID();
    NPCRoleType role = getNPCRole(victimUUID);
    BlockPos position = victim.blockPosition();

    switch (role) {
      case MERCHANT:
        LawSystemHandler.getInstance().recordCrime(player, CrimeType.MERCHANT_KILL, position);
        log.info("{} Player {} killed merchant NPC", LOG_PREFIX, player.getName().getString());
        break;
      case GUARD:
        LawSystemHandler.getInstance().recordCrime(player, CrimeType.GUARD_KILL, position);
        log.info("{} Player {} killed guard NPC", LOG_PREFIX, player.getName().getString());
        break;
      case CIVILIAN:
        // Civilians might trigger assault charge
        LawSystemHandler.getInstance().recordCrime(player, CrimeType.ASSAULT, position);
        log.info("{} Player {} killed civilian NPC", LOG_PREFIX, player.getName().getString());
        break;
    }

    // Unregister the dead NPC
    unregisterNPC(victimUUID);
  }

  /**
   * Called when a player attacks an NPC (doesn't kill).
   */
  public void onNPCAttacked(ServerPlayer attacker, Entity victim) {
    if (!LawSystemHandler.getInstance().isSystemEnabled()) {
      return;
    }

    // Only register assault if this is a new attack recently
    // This prevents spamming crimes for continuous attacks
    // (Full implementation would track attack history)
    
    // For now, we only track kills as crimes
  }

  /**
   * Called when a player steals from a merchant.
   */
  public void onTheft(ServerPlayer thief, Entity victim, BlockPos position) {
    if (!LawSystemHandler.getInstance().isSystemEnabled()) {
      return;
    }

    LawSystemHandler.getInstance().recordCrime(thief, CrimeType.THEFT, position);
    log.info("{} Player {} committed theft", LOG_PREFIX, thief.getName().getString());
  }

  /**
   * Called when a player enters a restricted area.
   */
  public void onTrespassing(ServerPlayer player, BlockPos position) {
    if (!LawSystemHandler.getInstance().isSystemEnabled()) {
      return;
    }

    LawSystemHandler.getInstance().recordCrime(player, CrimeType.TRESPASSING, position);
    log.info("{} Player {} is trespassing", LOG_PREFIX, player.getName().getString());
  }

  /**
   * Determine if an EasyNPC entity is a merchant based on its configuration.
   */
  public NPCRoleType determineNPCRole(EasyNPC<?> easyNPC) {
    if (easyNPC == null) {
      return NPCRoleType.CIVILIAN;
    }

    // Prefer trading capability as merchant indicator.
    try {
      var tradingData = easyNPC.getEasyNPCTradingData();
      if (tradingData != null && tradingData.getTradingDataSet() != null
          && !tradingData.getTradingDataSet().isType(de.markusbordihn.easynpc.data.trading.TradingType.NONE)) {
        return NPCRoleType.MERCHANT;
      }
    } catch (Exception e) {
      // Ignore trading detection issues and fall back to faction/profession.
    }

    String factionName = "";
    try {
      var configData = easyNPC.getEasyNPCConfigData();
      if (configData != null) {
        factionName = configData.getFaction();
      }
    } catch (Exception e) {
      // Ignore faction lookup issues.
    }
    String factionLower = factionName != null ? factionName.toLowerCase() : "";

    if (factionLower.contains("merchant") || factionLower.contains("trader")) {
      return NPCRoleType.MERCHANT;
    }
    if (factionLower.contains("guard") || factionLower.contains("soldier")) {
      return NPCRoleType.GUARD;
    }

    // Check profession as a backup hint.
    try {
      var professionData = easyNPC.getEasyNPCProfessionData();
      if (professionData != null && professionData.getProfession() != null) {
        String profession = professionData.getProfession().name().toLowerCase();
        if (profession.contains("merchant") || profession.contains("trader")) {
          return NPCRoleType.MERCHANT;
        }
        if (profession.contains("guard") || profession.contains("soldier")) {
          return NPCRoleType.GUARD;
        }
      }
    } catch (Exception e) {
      // Ignore profession lookup issues.
    }
    
    return NPCRoleType.CIVILIAN;
  }

  /**
   * Auto-register an Easy NPC based on its configuration.
   */
  public void autoRegisterEasyNPC(EasyNPC<?> easyNPC) {
    NPCRoleType role = determineNPCRole(easyNPC);
    registerNPCRole(easyNPC.getEntityUUID(), role);
  }

  /**
   * Clear all tracked NPCs.
   */
  public void clear() {
    npcRoles.clear();
  }

  /**
   * Get count of tracked merchants.
   */
  public int getMerchantCount() {
    int count = 0;
    for (NPCRoleType role : npcRoles.values()) {
      if (role == NPCRoleType.MERCHANT) {
        count++;
      }
    }
    return count;
  }

  /**
   * Get count of tracked guards.
   */
  public int getGuardCount() {
    int count = 0;
    for (NPCRoleType role : npcRoles.values()) {
      if (role == NPCRoleType.GUARD) {
        count++;
      }
    }
    return count;
  }

  /**
   * Despawn all tracked merchants across all dimensions.
   */
  public int despawnAllMerchants(MinecraftServer server) {
    if (server == null) {
      return 0;
    }
    int removed = 0;
    for (UUID npcUUID : new java.util.ArrayList<>(npcRoles.keySet())) {
      if (npcRoles.get(npcUUID) != NPCRoleType.MERCHANT) {
        continue;
      }
      boolean deleted = false;
      for (ServerLevel level : server.getAllLevels()) {
        Entity entity = level.getEntity(npcUUID);
        if (entity != null) {
          entity.discard();
          deleted = true;
          break;
        }
      }
      if (deleted) {
        removed++;
      }
      npcRoles.remove(npcUUID);
    }
    log.info("{} Despawned {} merchant NPCs", LOG_PREFIX, removed);
    return removed;
  }
}
