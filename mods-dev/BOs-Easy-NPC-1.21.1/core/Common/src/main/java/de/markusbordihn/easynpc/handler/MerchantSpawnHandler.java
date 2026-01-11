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
import de.markusbordihn.easynpc.data.crime.LawSystemConfig;
import de.markusbordihn.easynpc.data.crime.MerchantTemplate;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handler for spawning merchant NPC groups from law system templates.
 */
public class MerchantSpawnHandler {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String LOG_PREFIX = "[MerchantSpawnHandler]";
  private static final Random RANDOM = new Random();

  private static MerchantSpawnHandler instance;

  private MerchantSpawnHandler() {}

  public static MerchantSpawnHandler getInstance() {
    if (instance == null) {
      instance = new MerchantSpawnHandler();
    }
    return instance;
  }

  /**
   * Spawn one or more merchant groups near a position.
   */
  public void spawnMerchantGroup(ServerLevel level, BlockPos center, int groupCount) {
    if (level == null || center == null) {
      return;
    }
    LawSystemConfig config = LawSystemHandler.getInstance().getConfig();
    List<MerchantTemplate> templates = config.getMerchantTemplates();
    MerchantTemplate template =
        !templates.isEmpty() ? templates.get(0) : createFallbackTemplate();

    int groups = Math.max(1, groupCount);
    for (int groupIndex = 0; groupIndex < groups; groupIndex++) {
      int groupSize = template.getRandomGroupSize();
      for (int i = 0; i < groupSize; i++) {
        BlockPos spawnPos = findSpawnPosition(level, center, 10 + (groupIndex * 2));
        if (spawnPos == null) {
          log.warn("{} Could not find valid spawn position for merchant group", LOG_PREFIX);
          continue;
        }
        UUID merchantUUID = spawnMerchantAtPosition(level, spawnPos, template);
        if (merchantUUID != null) {
          CrimeHandler.getInstance().registerNPCRole(merchantUUID, CrimeHandler.NPCRoleType.MERCHANT);
        }
      }
    }

    log.info(
        "{} Spawned {} merchant group(s) using template {}",
        LOG_PREFIX,
        groups,
        template.getName());
  }

  private MerchantTemplate createFallbackTemplate() {
    MerchantTemplate template = new MerchantTemplate();
    template.setName("Merchant");
    template.setNpcTemplateName("merchant");
    template.setMinGroupSize(1);
    template.setMaxGroupSize(2);
    return template;
  }

  private UUID spawnMerchantAtPosition(ServerLevel level, BlockPos pos, MerchantTemplate template) {
    String templateName = template.getNpcTemplateName();
    if (templateName == null || templateName.isEmpty()) {
      templateName = "merchant";
    }
    try {
      Entity entity = NPCTemplateManager.spawnEntityFromTemplate(
          level, templateName, pos.getX(), pos.getY(), pos.getZ());
      if (entity != null) {
        return entity.getUUID();
      }
    } catch (Exception e) {
      log.error("{} Failed to spawn merchant: {}", LOG_PREFIX, e.getMessage());
    }
    return null;
  }

  private BlockPos findSpawnPosition(ServerLevel level, BlockPos center, int radius) {
    for (int attempt = 0; attempt < 10; attempt++) {
      int offsetX = RANDOM.nextInt(radius * 2) - radius;
      int offsetZ = RANDOM.nextInt(radius * 2) - radius;
      BlockPos testPos = center.offset(offsetX, 0, offsetZ);
      BlockPos groundPos = level.getHeightmapPos(
          net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, testPos);
      if (isValidSpawnPosition(level, groundPos)) {
        return groundPos;
      }
    }
    return null;
  }

  private boolean isValidSpawnPosition(ServerLevel level, BlockPos pos) {
    return level.getBlockState(pos.below()).isSolid()
        && level.getBlockState(pos).isAir()
        && level.getBlockState(pos.above()).isAir();
  }
}
