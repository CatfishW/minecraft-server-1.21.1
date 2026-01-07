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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side manager for LLM dialog responses.
 * Stores pending responses that will be displayed in the dialog bubble with typing effect.
 */
public class LLMDialogResponseManager {
  
  private static final Map<UUID, ResponseData> pendingResponses = new ConcurrentHashMap<>();
  
  private LLMDialogResponseManager() {}
  
  /**
   * Set a new response for an NPC.
   */
  public static void setResponse(UUID npcUuid, String response) {
    pendingResponses.put(npcUuid, new ResponseData(response, 0, System.currentTimeMillis()));
  }
  
  /**
   * Check if there's a pending response for an NPC.
   */
  public static boolean hasPendingResponse(UUID npcUuid) {
    return pendingResponses.containsKey(npcUuid);
  }
  
  /**
   * Get the current display text with streaming/typing effect.
   * Returns more characters each time it's called to create typing effect.
   * 
   * @param npcUuid The NPC's UUID
   * @param charsPerTick Number of characters to reveal per tick (affects speed)
   * @return The text to display, or null if no response pending
   */
  public static String getStreamedText(UUID npcUuid, int charsPerTick) {
    ResponseData data = pendingResponses.get(npcUuid);
    if (data == null) {
      return null;
    }
    
    // Calculate how many characters to show based on time elapsed
    long elapsed = System.currentTimeMillis() - data.startTime;
    int charsToShow = (int) (elapsed / 30) * charsPerTick; // ~30ms per character batch
    
    if (charsToShow >= data.fullText.length()) {
      // We've shown all the text
      return data.fullText;
    }
    
    return data.fullText.substring(0, Math.min(charsToShow, data.fullText.length()));
  }
  
  /**
   * Check if the streaming is complete (all text shown).
   */
  public static boolean isStreamingComplete(UUID npcUuid) {
    ResponseData data = pendingResponses.get(npcUuid);
    if (data == null) {
      return true;
    }
    
    long elapsed = System.currentTimeMillis() - data.startTime;
    int charsToShow = (int) (elapsed / 30);
    return charsToShow >= data.fullText.length();
  }
  
  /**
   * Clear the response for an NPC.
   */
  public static void clearResponse(UUID npcUuid) {
    pendingResponses.remove(npcUuid);
  }
  
  /**
   * Get the full response text without streaming effect.
   */
  public static String getFullResponse(UUID npcUuid) {
    ResponseData data = pendingResponses.get(npcUuid);
    return data != null ? data.fullText : null;
  }
  
  private record ResponseData(String fullText, int currentIndex, long startTime) {}
}
