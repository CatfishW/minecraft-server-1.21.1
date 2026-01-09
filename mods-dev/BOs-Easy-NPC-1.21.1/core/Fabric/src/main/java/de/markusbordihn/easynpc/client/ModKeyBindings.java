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

import com.mojang.blaze3d.platform.InputConstants;
import de.markusbordihn.easynpc.client.renderer.SpawnTimerOverlay;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Handles keybindings for EasyNPC client features.
 */
public class ModKeyBindings {

  private static KeyMapping toggleQuestOverlayKey;
  private static KeyMapping toggleQuestDescriptionKey;
  private static KeyMapping toggleSpawnTimerKey;

  private ModKeyBindings() {}

  /**
   * Register all keybindings.
   */
  public static void register() {
    toggleSpawnTimerKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.easy_npc.toggle_spawn_timer",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_N,
        "category.easy_npc.keys"
    ));
    
    toggleQuestOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.easy_npc.toggle_quest_overlay",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_H,
        "category.easy_npc.keys"
    ));

    toggleQuestDescriptionKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "key.easy_npc.toggle_quest_description",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_J,
        "category.easy_npc.keys"
    ));

    ClientTickEvents.END_CLIENT_TICK.register(ModKeyBindings::onClientTick);
  }

  /**
   * Handle client tick for keybind checking.
   */
  private static void onClientTick(Minecraft client) {
    if (toggleSpawnTimerKey.consumeClick()) {
      boolean enabled = SpawnTimerOverlay.toggle();
      if (client.player != null) {
        String status = enabled ? "§aEnabled" : "§cDisabled";
        client.player.displayClientMessage(
            Component.literal("§6[EasyNPC] §7Spawn Timer Overlay: " + status), true);
      }
    }

    if (toggleQuestOverlayKey.consumeClick()) {
      boolean enabled = de.markusbordihn.easynpc.client.renderer.QuestOverlay.toggle();
      if (client.player != null) {
        String status = enabled ? "§aEnabled" : "§cDisabled";
        client.player.displayClientMessage(
            Component.literal("§6[EasyNPC] §7Quest Overlay: " + status), true);
      }
    }

    if (toggleQuestDescriptionKey.consumeClick()) {
      boolean enabled = de.markusbordihn.easynpc.client.renderer.QuestOverlay.toggleDescription();
      if (client.player != null) {
        String status = enabled ? "§aEnabled" : "§cDisabled";
        client.player.displayClientMessage(
            Component.literal("§6[EasyNPC] §7Quest Descriptions: " + status), true);
      }
    }
  }

}
