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

import de.markusbordihn.easynpc.network.message.client.SpawnTimerSyncMessage;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Client-side overlay renderer for spawn timer information.
 * Shows countdown until next group spawn respawn.
 */
public class SpawnTimerOverlay {

  private static boolean enabled = false;
  private static final List<SpawnTimerInfo> activeTimers = new ArrayList<>();

  private SpawnTimerOverlay() {}

  /**
   * Register the HUD render callback and network handler.
   */
  public static void register() {
    HudRenderCallback.EVENT.register(SpawnTimerOverlay::onHudRender);
    
    // Register as the spawn timer sync handler
    SpawnTimerSyncMessage.SpawnTimerSyncHandler.setHandler(SpawnTimerOverlay::onNetworkSync);
  }

  /**
   * Called when timer data is received from the server.
   */
  private static void onNetworkSync(List<SpawnTimerSyncMessage.TimerEntry> entries) {
    activeTimers.clear();
    for (SpawnTimerSyncMessage.TimerEntry entry : entries) {
      activeTimers.add(new SpawnTimerInfo(
          entry.templateName(), entry.ticksRemaining(), entry.totalTicks(), entry.isGroupSpawn()));
    }
  }

  /**
   * Toggle the overlay visibility.
   */
  public static boolean toggle() {
    enabled = !enabled;
    return enabled;
  }

  /**
   * Set enabled state.
   */
  public static void setEnabled(boolean state) {
    enabled = state;
  }

  /**
   * Check if enabled.
   */
  public static boolean isEnabled() {
    return enabled;
  }

  /**
   * Clear all timers.
   */
  public static void clearTimers() {
    activeTimers.clear();
  }

  /**
   * Update timers from server data.
   */
  public static void updateTimers(List<SpawnTimerInfo> timers) {
    activeTimers.clear();
    activeTimers.addAll(timers);
  }

  /**
   * Add or update a single timer.
   */
  public static void updateTimer(String templateName, int ticksRemaining, int totalTicks, boolean isGroupSpawn) {
    // Remove existing timer for this template
    activeTimers.removeIf(t -> t.templateName.equals(templateName));
    activeTimers.add(new SpawnTimerInfo(templateName, ticksRemaining, totalTicks, isGroupSpawn));
  }

  /**
   * Called each frame to render the HUD overlay.
   */
  private static void onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
    if (!enabled || activeTimers.isEmpty()) {
      return;
    }

    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null || minecraft.screen != null) {
      return;
    }

    Font font = minecraft.font;
    int screenWidth = minecraft.getWindow().getGuiScaledWidth();
    int y = 10;

    // Title
    String title = "§6[Spawn Timers]";
    guiGraphics.drawString(font, title, screenWidth - font.width(title) - 10, y, 0xFFFFFF);
    y += 12;

    for (SpawnTimerInfo timer : activeTimers) {
      float secondsRemaining = timer.ticksRemaining / 20.0f;
      float totalSeconds = timer.totalTicks / 20.0f;
      float progress = 1.0f - (float) timer.ticksRemaining / timer.totalTicks;
      
      String modeStr = timer.isGroupSpawn ? "§c[G]§r " : "";
      String text = String.format("%s%s: §e%.1fs §7/ %.1fs", 
          modeStr, timer.templateName, secondsRemaining, totalSeconds);
      
      int color = timer.ticksRemaining < 100 ? 0xFF5555 : 0xFFFFFF; // Red when < 5 seconds
      
      guiGraphics.drawString(font, text, screenWidth - font.width(text) - 10, y, color);
      y += 10;
      
      // Progress bar
      int barWidth = 100;
      int barHeight = 4;
      int barX = screenWidth - barWidth - 10;
      int filled = (int) (barWidth * progress);
      
      guiGraphics.fill(barX, y, barX + barWidth, y + barHeight, 0x80000000); // Background
      guiGraphics.fill(barX, y, barX + filled, y + barHeight, timer.isGroupSpawn ? 0xFFFF5555 : 0xFF55FF55); // Fill
      
      y += 8;
    }
  }

  /**
   * Data class for timer info.
   */
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
