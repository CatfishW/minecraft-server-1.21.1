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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * HTTP client for OpenAI-compatible LLM API.
 * Handles chat completions and model detection.
 */
public class LLMApiClient {
  
  private static final Logger log = LogManager.getLogger(LLMApiClient.class);
  private static final String LOG_PREFIX = "[LLM API Client]";
  private static final Gson GSON = new Gson();
  
  private static final ScheduledExecutorService executor = 
      Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "EasyNPC-LLM-API");
        t.setDaemon(true);
        return t;
      });
  
  private static boolean initialized = false;
  
  private LLMApiClient() {}
  
  /**
   * Initialize the API client, detecting available models if configured to auto-detect.
   */
  public static void initialize() {
    if (initialized) {
      return;
    }
    initialized = true;
    
    if (!LLMConfig.isEnabled()) {
      log.info("{} LLM integration is disabled", LOG_PREFIX);
      return;
    }
    
    // Auto-detect model if configured
    if ("auto".equalsIgnoreCase(LLMConfig.getModel())) {
      detectModel();
    }
  }
  
  /**
   * Detect available model from the API.
   */
  public static void detectModel() {
    CompletableFuture.runAsync(() -> {
      try {
        String modelsUrl = LLMConfig.getModelsUrl();
        log.info("{} Detecting available models from {}", LOG_PREFIX, modelsUrl);
        
        HttpURLConnection connection = (HttpURLConnection) new URL(modelsUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(LLMConfig.getRequestTimeoutMs());
        connection.setReadTimeout(LLMConfig.getRequestTimeoutMs());
        connection.setRequestProperty("Accept", "application/json");
        
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
          try (BufferedReader reader = new BufferedReader(
              new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
              response.append(line);
            }
            
            JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
            if (jsonResponse.has("data") && jsonResponse.get("data").isJsonArray()) {
              JsonArray models = jsonResponse.getAsJsonArray("data");
              if (!models.isEmpty()) {
                String modelId = models.get(0).getAsJsonObject().get("id").getAsString();
                LLMConfig.setDetectedModel(modelId);
              }
            }
          }
        } else {
          log.warn("{} Failed to detect models, HTTP {}", LOG_PREFIX, responseCode);
        }
        
        connection.disconnect();
      } catch (Exception e) {
        log.error("{} Error detecting models: {}", LOG_PREFIX, e.getMessage());
      }
    }, executor);
  }
  
  /**
   * Send a chat completion request to the LLM API.
   *
   * @param messages List of messages in the conversation
   * @return CompletableFuture containing the assistant's response
   */
  public static CompletableFuture<String> sendChatRequest(List<ChatMessage> messages) {
    return CompletableFuture.supplyAsync(() -> {
      if (!LLMConfig.isEnabled()) {
        return "LLM integration is disabled.";
      }
      
      try {
        String chatUrl = LLMConfig.getChatCompletionsUrl();
        String model = LLMConfig.getEffectiveModel();
        
        // Build request JSON
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        
        JsonArray messagesArray = new JsonArray();
        for (ChatMessage message : messages) {
          JsonObject msgObj = new JsonObject();
          msgObj.addProperty("role", message.role());
          msgObj.addProperty("content", message.content());
          messagesArray.add(msgObj);
        }
        requestBody.add("messages", messagesArray);
        
        // Optional parameters
        requestBody.addProperty("max_tokens", 256);
        requestBody.addProperty("temperature", 0.7);
        
        String requestJson = GSON.toJson(requestBody);
        log.debug("{} Sending request to {}: {}", LOG_PREFIX, chatUrl, requestJson);
        
        // Send HTTP request
        HttpURLConnection connection = (HttpURLConnection) new URL(chatUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(LLMConfig.getRequestTimeoutMs());
        connection.setReadTimeout(LLMConfig.getRequestTimeoutMs());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        
        try (OutputStream os = connection.getOutputStream()) {
          os.write(requestJson.getBytes(StandardCharsets.UTF_8));
        }
        
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
          try (BufferedReader reader = new BufferedReader(
              new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
              response.append(line);
            }
            
            JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
            if (jsonResponse.has("choices") && jsonResponse.get("choices").isJsonArray()) {
              JsonArray choices = jsonResponse.getAsJsonArray("choices");
              if (!choices.isEmpty()) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                if (firstChoice.has("message")) {
                  JsonObject message = firstChoice.getAsJsonObject("message");
                  if (message.has("content")) {
                    return message.get("content").getAsString();
                  }
                }
              }
            }
            
            log.warn("{} Unexpected response format: {}", LOG_PREFIX, response);
            return "I'm having trouble responding right now.";
          }
        } else {
          // Read error response
          try (BufferedReader reader = new BufferedReader(
              new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
              errorResponse.append(line);
            }
            log.error("{} API Error (HTTP {}): {}", LOG_PREFIX, responseCode, errorResponse);
          } catch (Exception e) {
            log.error("{} API Error (HTTP {})", LOG_PREFIX, responseCode);
          }
          return "I'm having trouble responding right now.";
        }
        
      } catch (IOException e) {
        log.error("{} Request failed: {}", LOG_PREFIX, e.getMessage());
        return "I'm having trouble responding right now.";
      }
    }, executor);
  }
  
  /**
   * Strip emojis and special unicode characters from text.
   * Keeps ASCII printable characters, extended Latin, and CJK.
   */
  public static String stripEmojis(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      // Keep printable ASCII, extended Latin, and CJK characters
      if ((c >= 0x20 && c <= 0x7E) ||  // ASCII printable
          (c >= 0xA0 && c <= 0xFF) ||  // Extended Latin
          (c >= 0x4E00 && c <= 0x9FFF) || // CJK Unified Ideographs
          (c >= 0x3000 && c <= 0x303F) || // CJK Punctuation
          (c >= 0x3040 && c <= 0x30FF) || // Hiragana/Katakana
          (c >= 0xAC00 && c <= 0xD7AF) || // Korean
          c == '\n' || c == '\t') {
        sb.append(c);
      }
    }
    return sb.toString().replaceAll("\\s+", " ").trim();
  }
  
  /**
   * Record representing a chat message.
   */
  public record ChatMessage(String role, String content) {
    public static ChatMessage system(String content) {
      return new ChatMessage("system", content);
    }
    
    public static ChatMessage user(String content) {
      return new ChatMessage("user", content);
    }
    
    public static ChatMessage assistant(String content) {
      return new ChatMessage("assistant", content);
    }
  }
}
