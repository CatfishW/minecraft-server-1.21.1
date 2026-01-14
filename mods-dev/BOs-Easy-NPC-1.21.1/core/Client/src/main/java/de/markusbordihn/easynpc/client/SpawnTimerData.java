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

package de.markusbordihn.easynpc.client;

import java.util.ArrayList;
import java.util.List;

public final class SpawnTimerData {

  private static final List<SpawnTimerInfo> ACTIVE_TIMERS = new ArrayList<>();

  private SpawnTimerData() {}

  public static List<SpawnTimerInfo> getTimers() {
    return List.copyOf(ACTIVE_TIMERS);
  }

  public static boolean hasTimers() {
    return !ACTIVE_TIMERS.isEmpty();
  }

  public static void setTimers(List<SpawnTimerInfo> timers) {
    ACTIVE_TIMERS.clear();
    ACTIVE_TIMERS.addAll(timers);
  }

  public static void clear() {
    ACTIVE_TIMERS.clear();
  }

  public static void updateTimer(String templateName, int ticksRemaining, int totalTicks, boolean isGroupSpawn) {
    ACTIVE_TIMERS.removeIf(timer -> timer.templateName.equals(templateName));
    ACTIVE_TIMERS.add(new SpawnTimerInfo(templateName, ticksRemaining, totalTicks, isGroupSpawn));
  }

  public static class SpawnTimerInfo {
    public final String templateName;
    public final int ticksRemaining;
    public final int totalTicks;
    public final boolean isGroupSpawn;

    public SpawnTimerInfo(String templateName, int ticksRemaining, int totalTicks, boolean isGroupSpawn) {
      this.templateName = templateName;
      this.ticksRemaining = ticksRemaining;
      this.totalTicks = totalTicks;
      this.isGroupSpawn = isGroupSpawn;
    }
  }
}
