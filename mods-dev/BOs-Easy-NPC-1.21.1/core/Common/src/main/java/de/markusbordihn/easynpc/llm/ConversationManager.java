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

import de.markusbordihn.easynpc.llm.LLMApiClient.ChatMessage;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages conversation history between players and NPCs.
 * Maintains per-player-per-NPC message history for context-aware responses.
 */
public class ConversationManager {
  
  private static final Logger log = LogManager.getLogger(ConversationManager.class);
  private static final String LOG_PREFIX = "[Conversation Manager]";
  
  // Map of conversation key to message history
  // Key format: "playerUUID:npcUUID"
  private static final Map<String, LinkedList<ChatMessage>> conversations = new ConcurrentHashMap<>();
  
  private ConversationManager() {}
  
  /**
   * Generate a unique key for a player-NPC conversation.
   */
  public static String getConversationKey(UUID playerUUID, UUID npcUUID) {
    return playerUUID.toString() + ":" + npcUUID.toString();
  }
  
  /**
   * Get the conversation history for a player and NPC.
   *
   * @param playerUUID The player's UUID
   * @param npcUUID The NPC's UUID
   * @return List of messages in the conversation
   */
  public static List<ChatMessage> getConversation(UUID playerUUID, UUID npcUUID) {
    String key = getConversationKey(playerUUID, npcUUID);
    return conversations.getOrDefault(key, new LinkedList<>());
  }
  
  /**
   * Add a message to the conversation history.
   *
   * @param playerUUID The player's UUID
   * @param npcUUID The NPC's UUID
   * @param message The message to add
   */
  public static void addMessage(UUID playerUUID, UUID npcUUID, ChatMessage message) {
    String key = getConversationKey(playerUUID, npcUUID);
    LinkedList<ChatMessage> history = conversations.computeIfAbsent(key, k -> new LinkedList<>());
    
    history.addLast(message);
    
    // Truncate old messages to stay within configured limit
    int maxHistory = LLMConfig.getMaxConversationHistory();
    while (history.size() > maxHistory * 2) { // *2 because we store both user and assistant messages
      history.removeFirst();
    }
    
    log.debug("{} Added message to conversation {} (history size: {})", LOG_PREFIX, key, history.size());
  }
  
  /**
   * Build the full messages array for an LLM request.
   *
   * @param playerUUID The player's UUID
   * @param npcUUID The NPC's UUID
   * @param systemPrompt The NPC's system prompt
   * @param newMessage The player's new message
   * @return List of messages including system prompt, history, and new message
   */
  public static List<ChatMessage> buildMessagesForRequest(
      UUID playerUUID, UUID npcUUID, String systemPrompt, String newMessage) {
    
    List<ChatMessage> messages = new ArrayList<>();
    
    // Add system prompt
    String effectivePrompt = systemPrompt != null && !systemPrompt.isEmpty() 
        ? systemPrompt 
        : LLMConfig.getDefaultSystemPrompt();
    messages.add(ChatMessage.system(effectivePrompt));
    
    // Add conversation history
    messages.addAll(getConversation(playerUUID, npcUUID));
    
    // Add new user message
    ChatMessage userMessage = ChatMessage.user(newMessage);
    messages.add(userMessage);
    
    // Also store the user message in history
    addMessage(playerUUID, npcUUID, userMessage);
    
    return messages;
  }
  
  /**
   * Record the assistant's response in the conversation history.
   *
   * @param playerUUID The player's UUID
   * @param npcUUID The NPC's UUID
   * @param response The assistant's response text
   */
  public static void recordAssistantResponse(UUID playerUUID, UUID npcUUID, String response) {
    addMessage(playerUUID, npcUUID, ChatMessage.assistant(response));
  }
  
  /**
   * Clear the conversation history for a player and NPC.
   *
   * @param playerUUID The player's UUID
   * @param npcUUID The NPC's UUID
   */
  public static void clearConversation(UUID playerUUID, UUID npcUUID) {
    String key = getConversationKey(playerUUID, npcUUID);
    conversations.remove(key);
    log.info("{} Cleared conversation {}", LOG_PREFIX, key);
  }
  
  /**
   * Clear all conversations for a player (e.g., when they disconnect).
   *
   * @param playerUUID The player's UUID
   */
  public static void clearPlayerConversations(UUID playerUUID) {
    String prefix = playerUUID.toString() + ":";
    conversations.keySet().removeIf(key -> key.startsWith(prefix));
    log.info("{} Cleared all conversations for player {}", LOG_PREFIX, playerUUID);
  }
  
  /**
   * Clear all conversations for an NPC (e.g., when it's removed).
   *
   * @param npcUUID The NPC's UUID
   */
  public static void clearNpcConversations(UUID npcUUID) {
    String suffix = ":" + npcUUID.toString();
    conversations.keySet().removeIf(key -> key.endsWith(suffix));
    log.info("{} Cleared all conversations for NPC {}", LOG_PREFIX, npcUUID);
  }
  
  /**
   * Get the total number of active conversations.
   *
   * @return Number of active conversations
   */
  public static int getActiveConversationCount() {
    return conversations.size();
  }
}
