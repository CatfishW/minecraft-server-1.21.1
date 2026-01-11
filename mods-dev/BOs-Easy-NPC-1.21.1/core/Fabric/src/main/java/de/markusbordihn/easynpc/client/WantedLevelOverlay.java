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

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Client-side HUD overlay for displaying wanted level (GTA-style stars) and peace value.
 * Renders above the health bar.
 */
public class WantedLevelOverlay {

  // Current player state (synced from server)
  private static int wantedLevel = 0;
  private static int peaceValue = 100;
  private static boolean hasImmunity = false;
  
  // Debug mode - always show overlay
  private static boolean debugMode = false;
  
  // Animation state
  private static long lastWantedChange = 0;
  private static int previousWantedLevel = 0;
  private static boolean isFlashing = false;
  
  // Constants
  private static final int MAX_WANTED_LEVEL = 5;
  private static final int MAX_PEACE_VALUE = 100;
  private static final String STAR_FILLED = "★";
  private static final String STAR_EMPTY = "☆";
  private static final String WANTED_TEXT = "你被通缉了";
  
  private WantedLevelOverlay() {}
  
  /**
   * Register the HUD render callback.
   */
  public static void register() {
    HudRenderCallback.EVENT.register(WantedLevelOverlay::onHudRender);
  }
  
  /**
   * Toggle debug mode (always show overlay for testing).
   */
  public static boolean toggleDebug() {
    debugMode = !debugMode;
    if (debugMode && wantedLevel == 0) {
      // Set test values in debug mode
      wantedLevel = 2;
      peaceValue = 75;
    }
    return debugMode;
  }
  
  /**
   * Check if debug mode is enabled.
   */
  public static boolean isDebugMode() {
    return debugMode;
  }
  
  /**
   * Update the player's law state from server sync.
   */
  public static void updateFromServer(int wantedLevel, int peaceValue, boolean hasImmunity) {
    if (wantedLevel > WantedLevelOverlay.wantedLevel) {
      WantedLevelOverlay.lastWantedChange = System.currentTimeMillis();
      WantedLevelOverlay.previousWantedLevel = WantedLevelOverlay.wantedLevel;
      WantedLevelOverlay.isFlashing = true;
    }
    WantedLevelOverlay.wantedLevel = wantedLevel;
    WantedLevelOverlay.peaceValue = peaceValue;
    WantedLevelOverlay.hasImmunity = hasImmunity;
  }
  
  /**
   * Reset state (e.g., on disconnect).
   */
  public static void reset() {
    wantedLevel = 0;
    peaceValue = 100;
    hasImmunity = false;
    isFlashing = false;
    debugMode = false;
  }
  
  /**
   * Called each frame to render the HUD overlay.
   */
  private static void onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player == null || minecraft.screen != null) {
      return;
    }
    
    // Don't render if player has no wanted level and full peace (unless debug mode)
    if (!debugMode && wantedLevel == 0 && peaceValue >= MAX_PEACE_VALUE && !hasImmunity) {
      return;
    }
    
    Font font = minecraft.font;
    int screenWidth = minecraft.getWindow().getGuiScaledWidth();
    int screenHeight = minecraft.getWindow().getGuiScaledHeight();
    
    // Position above the health bar (like GTA)
    int centerX = screenWidth / 2;
    int baseY = screenHeight - (wantedLevel > 0 ? 90 : 60); // Above hotbar/health
    
    // Handle flash animation
    long currentTime = System.currentTimeMillis();
    if (isFlashing && currentTime - lastWantedChange > 2000) {
      isFlashing = false;
    }
    boolean showStars = !isFlashing || (currentTime / 200) % 2 == 0;
    
    // Render wanted stars
    if (wantedLevel > 0 || isFlashing) {
      StringBuilder stars = new StringBuilder();
      for (int i = 0; i < MAX_WANTED_LEVEL; i++) {
        if (i < wantedLevel) {
          stars.append(showStars ? STAR_FILLED : " ");
        } else {
          stars.append(STAR_EMPTY);
        }
      }
      
      String starsStr = stars.toString();
      int starsWidth = font.width(starsStr);
      int starsX = centerX - starsWidth / 2;
      
      // Draw shadow
      guiGraphics.drawString(font, starsStr, starsX + 1, baseY + 1, 0x000000, false);
      // Draw stars - orange/yellow color like GTA
      int starColor = isFlashing ? 0xFFFF00 : 0xFFA500;
      guiGraphics.drawString(font, starsStr, starsX, baseY, starColor, false);
    }

    if (wantedLevel > 0) {
      int textWidth = font.width(WANTED_TEXT);
      int textX = centerX - textWidth / 2;
      int textY = baseY + 10;
      guiGraphics.drawString(font, WANTED_TEXT, textX + 1, textY + 1, 0x000000, false);
      guiGraphics.drawString(font, WANTED_TEXT, textX, textY, 0xFFE066, false);
    }
    
    // Render peace value bar
    int barWidth = 60;
    int barHeight = 4;
    int barX = centerX - barWidth / 2;
    int barY = baseY + (wantedLevel > 0 ? 22 : 12);
    
    float peacePercent = (float) peaceValue / MAX_PEACE_VALUE;
    int filledWidth = (int) (barWidth * peacePercent);
    
    // Background (dark)
    guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0x80000000);
    
    // Peace bar fill - color based on level
    int barColor;
    if (peacePercent > 0.6f) {
      barColor = 0xFF00FF00; // Green - peaceful
    } else if (peacePercent > 0.3f) {
      barColor = 0xFFFFFF00; // Yellow - caution
    } else {
      barColor = 0xFFFF0000; // Red - danger
    }
    guiGraphics.fill(barX, barY, barX + filledWidth, barY + barHeight, barColor);
    
    // Immunity indicator
    if (hasImmunity) {
      String immunityText = "§b[IMMUNE]";
      int immunityWidth = font.width(immunityText);
      guiGraphics.drawString(font, immunityText, centerX - immunityWidth / 2, baseY - 10, 0xFFFFFF, false);
    }
  }
  
  // Getters
  public static int getWantedLevel() { return wantedLevel; }
  public static int getPeaceValue() { return peaceValue; }
  public static boolean hasImmunity() { return hasImmunity; }
}
