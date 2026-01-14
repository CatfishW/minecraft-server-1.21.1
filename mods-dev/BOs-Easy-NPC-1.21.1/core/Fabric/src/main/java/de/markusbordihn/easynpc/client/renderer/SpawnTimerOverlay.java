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

package de.markusbordihn.easynpc.client.renderer;

import de.markusbordihn.easynpc.client.SpawnTimerData;
import de.markusbordihn.easynpc.network.message.client.SpawnTimerSyncMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side handler for spawn timer information.
 */
public class SpawnTimerOverlay {

  private SpawnTimerOverlay() {}

  /**
   * Register the network handler.
   */
  public static void register() {
    // Register as the spawn timer sync handler
    SpawnTimerSyncMessage.SpawnTimerSyncHandler.setHandler(SpawnTimerOverlay::onNetworkSync);
  }

  /**
   * Called when timer data is received from the server.
   */
  private static void onNetworkSync(List<SpawnTimerSyncMessage.TimerEntry> entries) {
    List<SpawnTimerData.SpawnTimerInfo> timers = new ArrayList<>(entries.size());
    for (SpawnTimerSyncMessage.TimerEntry entry : entries) {
      timers.add(new SpawnTimerData.SpawnTimerInfo(
          entry.templateName(), entry.ticksRemaining(), entry.totalTicks(), entry.isGroupSpawn()));
    }
    SpawnTimerData.setTimers(timers);
  }

  /**
   * Clear all timers.
   */
  public static void clearTimers() {
    SpawnTimerData.clear();
  }

  /**
   * Update timers from server data.
   */
  public static void updateTimers(List<SpawnTimerData.SpawnTimerInfo> timers) {
    SpawnTimerData.setTimers(timers);
  }

  /**
   * Add or update a single timer.
   */
  public static void updateTimer(String templateName, int ticksRemaining, int totalTicks, boolean isGroupSpawn) {
    SpawnTimerData.updateTimer(templateName, ticksRemaining, totalTicks, isGroupSpawn);
  }

}
