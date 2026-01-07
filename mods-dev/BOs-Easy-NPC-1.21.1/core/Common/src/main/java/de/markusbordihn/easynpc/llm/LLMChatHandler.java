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

package de.markusbordihn.easynpc.llm;

import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.DialogDataCapable;
import de.markusbordihn.easynpc.llm.LLMApiClient.ChatMessage;
import de.markusbordihn.easynpc.network.NetworkHandlerManager;
import de.markusbordihn.easynpc.network.message.client.LLMChatResponseMessage;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handler for LLM chat interactions between players and NPCs.
 * Processes incoming chat messages and sends responses.
 */
public class LLMChatHandler {
  
  private static final Logger log = LogManager.getLogger(LLMChatHandler.class);
  private static final String LOG_PREFIX = "[LLM Chat Handler]";
  
  private LLMChatHandler() {}
  
  /**
   * Handle a chat message from a player to an NPC.
   *
   * @param serverPlayer The player sending the message
   * @param easyNPC The target NPC
   * @param message The player's message text
   */
  public static void handleChatMessage(ServerPlayer serverPlayer, EasyNPC<?> easyNPC, String message) {
    if (!LLMConfig.isEnabled()) {
      log.debug("{} LLM is disabled, ignoring chat message", LOG_PREFIX);
      return;
    }
    
    UUID playerUUID = serverPlayer.getUUID();
    UUID npcUUID = easyNPC.getEntityUUID();
    String npcName = easyNPC.getEntity().getName().getString();
    
    // Get system prompt from NPC's dialog data or use default
    String systemPrompt = LLMConfig.getDefaultSystemPrompt();
    if (easyNPC instanceof DialogDataCapable<?> dialogData) {
      String customPrompt = dialogData.getLLMSystemPrompt();
      if (customPrompt != null && !customPrompt.isEmpty()) {
        systemPrompt = customPrompt;
      }
    }
    
    // Check if this NPC has LLM enabled
    if (easyNPC instanceof DialogDataCapable<?> dialogData) {
      if (!dialogData.isLLMChatEnabled()) {
        log.debug("{} LLM chat not enabled for NPC {}", LOG_PREFIX, npcUUID);
        return;
      }
    }
    
    log.info("{} Processing chat from {} to NPC {}: {}", LOG_PREFIX, 
        serverPlayer.getName().getString(), npcName, message);
    
    // Build messages and send to API
    List<ChatMessage> messages = ConversationManager.buildMessagesForRequest(
        playerUUID, npcUUID, systemPrompt, message);
    
    CompletableFuture<String> responseFuture = LLMApiClient.sendChatRequest(messages);
    
    responseFuture.thenAccept(response -> {
      // Strip emojis from response
      String cleanResponse = LLMApiClient.stripEmojis(response);
      
      // Record the response in conversation history
      ConversationManager.recordAssistantResponse(playerUUID, npcUUID, cleanResponse);
      
      // Send response to update dialog bubble on client
      sendDialogResponse(serverPlayer, easyNPC, npcName, cleanResponse);
      
      log.info("{} Sent response from NPC {} to {}: {}", LOG_PREFIX, 
          npcName, serverPlayer.getName().getString(), 
          cleanResponse.length() > 50 ? cleanResponse.substring(0, 50) + "..." : cleanResponse);
    }).exceptionally(throwable -> {
      log.error("{} Error getting LLM response: {}", LOG_PREFIX, throwable.getMessage());
      sendDialogResponse(serverPlayer, easyNPC, npcName, "I'm having trouble thinking right now...");
      return null;
    });
  }
  
  /**
   * Send the NPC's response to update the player's dialog bubble.
   * Uses network message to client to update the dialog text directly.
   */
  private static void sendDialogResponse(ServerPlayer serverPlayer, EasyNPC<?> easyNPC, String npcName, String response) {
    if (serverPlayer == null || serverPlayer.isRemoved()) {
      return;
    }
    
    // Send network message to client to update dialog bubble
    serverPlayer.server.execute(() -> {
      if (!serverPlayer.isRemoved()) {
        NetworkHandlerManager.sendMessageToPlayer(
            new LLMChatResponseMessage(easyNPC.getEntityUUID(), npcName, response),
            serverPlayer);
      }
    });
  }
  
  /**
   * Clear conversation when a player disconnects.
   */
  public static void onPlayerDisconnect(ServerPlayer serverPlayer) {
    ConversationManager.clearPlayerConversations(serverPlayer.getUUID());
  }
  
  /**
   * Clear conversations when an NPC is removed.
   */
  public static void onNpcRemoved(UUID npcUUID) {
    ConversationManager.clearNpcConversations(npcUUID);
  }
}
