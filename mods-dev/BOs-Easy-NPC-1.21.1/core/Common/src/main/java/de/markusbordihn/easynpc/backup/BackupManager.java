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

package de.markusbordihn.easynpc.backup;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.entity.LivingEntityManager;
import de.markusbordihn.easynpc.handler.PresetHandler;
import de.markusbordihn.easynpc.io.BackupDataFiles;
import java.io.File;
import java.nio.file.Path;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BackupManager {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String LOG_PREFIX = "[Backup Manager]";
  private static final long BACKUP_INTERVAL_MILLIS = 60L * 60L * 1000L;
  private static final long BACKUP_TICK_INTERVAL = 20L * 60L;
  private static final ConcurrentHashMap<UUID, Long> lastNPCBackupTime = new ConcurrentHashMap<>();

  private static long lastBackupTime;
  private static long backupTicks;

  private BackupManager() {}

  public static void performBackup() {
    if (backupTicks++ < BACKUP_TICK_INTERVAL) {
      return;
    }

    long now = System.currentTimeMillis();
    if (lastBackupTime == 0L || now - lastBackupTime > BACKUP_INTERVAL_MILLIS) {
      backupNPCData(now);
      lastBackupTime = now;
    }
    backupTicks = 0L;
  }

  private static void backupNPCData(long backupTime) {
    Date currentDate = new Date(backupTime);
    LivingEntityManager.getNpcEntityMap()
        .forEach(
            (uuid, easyNPC) -> {
              if (uuid == null || easyNPC == null) {
                return;
              }

              Long previousBackup = lastNPCBackupTime.get(uuid);
              if (previousBackup != null
                  && backupTime - previousBackup < BACKUP_INTERVAL_MILLIS) {
                log.debug(
                    "{} [Skipping] Backup for {} already done in the last hour.",
                    LOG_PREFIX,
                    easyNPC);
                return;
              }

              Path backupFilePath = BackupDataFiles.getBackupFile(uuid, currentDate);
              if (backupFilePath == null) {
                log.error("{} Backup file path for {} is null.", LOG_PREFIX, easyNPC);
                return;
              }

              File backupFile = backupFilePath.toFile();
              if (PresetHandler.exportPreset(easyNPC, backupFile)) {
                lastNPCBackupTime.put(uuid, backupTime);
              } else {
                log.error("{} Backup failed for {}", LOG_PREFIX, easyNPC);
              }
            });
  }
}
