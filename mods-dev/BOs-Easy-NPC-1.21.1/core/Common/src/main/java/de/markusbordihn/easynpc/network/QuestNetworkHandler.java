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

package de.markusbordihn.easynpc.network;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.data.quest.QuestProgressTracker;
import de.markusbordihn.easynpc.handler.QuestSyncHandler;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class QuestNetworkHandler {
  
  public static final ResourceLocation QUEST_ACCEPT = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "quest_accept");
  
  public static void handleQuestAccept(FriendlyByteBuf buffer, ServerPlayer serverPlayer) {
      if (serverPlayer == null) {
          return;
      }
      UUID questId = buffer.readUUID();
      ServerPlayer player = serverPlayer;
      player.getServer().execute(() -> {
          QuestProgressTracker tracker = QuestProgressTracker.get(player.serverLevel());
          tracker.acceptQuest(player.getUUID(), questId);
          QuestSyncHandler.syncQuest(player, questId);
      });
  }


}
