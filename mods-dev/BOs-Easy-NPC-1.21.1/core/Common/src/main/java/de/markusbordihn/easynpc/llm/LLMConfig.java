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

import de.markusbordihn.easynpc.config.Config;
import java.io.File;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Configuration for LLM (Large Language Model) integration.
 * Manages API settings and default prompts for NPC conversations.
 */
public class LLMConfig extends Config {
  
  private static final Logger log = LogManager.getLogger(LLMConfig.class);
  private static final String LOG_PREFIX = "[LLM Config]";
  
  private static final String CONFIG_FILE_NAME = "llm.properties";
  private static final String CONFIG_FILE_HEADER = "Easy NPC LLM Configuration";
  
  // Configuration keys
  private static final String KEY_API_ENDPOINT = "api_endpoint";
  private static final String KEY_MODEL = "model";
  private static final String KEY_ENABLED = "enabled";
  private static final String KEY_MAX_CONVERSATION_HISTORY = "max_conversation_history";
  private static final String KEY_REQUEST_TIMEOUT_MS = "request_timeout_ms";
  private static final String KEY_DEFAULT_SYSTEM_PROMPT = "default_system_prompt";
  
  // Default values
  private static final String DEFAULT_API_ENDPOINT = "https://game.agaii.org/llm/v1";
  private static final String DEFAULT_MODEL = "auto";
  private static final boolean DEFAULT_ENABLED = true;
  private static final int DEFAULT_MAX_CONVERSATION_HISTORY = 10;
  private static final int DEFAULT_REQUEST_TIMEOUT_MS = 30000;
  private static final String DEFAULT_SYSTEM_PROMPT = 
      "You are an NPC in Minecraft. Respond in character, be helpful and friendly. Keep responses concise.";
  
  // Loaded configuration values
  private static String apiEndpoint = DEFAULT_API_ENDPOINT;
  private static String model = DEFAULT_MODEL;
  private static boolean enabled = DEFAULT_ENABLED;
  private static int maxConversationHistory = DEFAULT_MAX_CONVERSATION_HISTORY;
  private static int requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;
  private static String defaultSystemPrompt = DEFAULT_SYSTEM_PROMPT;
  
  // Auto-detected model cache
  private static String detectedModel = null;
  
  private LLMConfig() {}
  
  public static void registerConfig() {
    registerConfigFile(CONFIG_FILE_NAME, CONFIG_FILE_HEADER);
    loadConfig();
  }
  
  private static void loadConfig() {
    File configFile = getConfigFile(CONFIG_FILE_NAME);
    if (configFile == null || !configFile.exists()) {
      log.info("{} Config file not found, using defaults", LOG_PREFIX);
      return;
    }
    
    Properties properties = readConfigFile(configFile);
    Properties unmodifiedProperties = new Properties();
    unmodifiedProperties.putAll(properties);
    
    // Parse configuration values
    apiEndpoint = parseStringConfigValue(properties, KEY_API_ENDPOINT, DEFAULT_API_ENDPOINT);
    model = parseStringConfigValue(properties, KEY_MODEL, DEFAULT_MODEL);
    enabled = parseConfigValue(properties, KEY_ENABLED, DEFAULT_ENABLED);
    maxConversationHistory = parseConfigValue(properties, KEY_MAX_CONVERSATION_HISTORY, DEFAULT_MAX_CONVERSATION_HISTORY);
    requestTimeoutMs = parseConfigValue(properties, KEY_REQUEST_TIMEOUT_MS, DEFAULT_REQUEST_TIMEOUT_MS);
    defaultSystemPrompt = parseStringConfigValue(properties, KEY_DEFAULT_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
    
    // Update config file if values were added
    updateConfigFileIfChanged(configFile, CONFIG_FILE_HEADER, properties, unmodifiedProperties);
    
    log.info("{} Loaded LLM configuration:", LOG_PREFIX);
    log.info("{}   API Endpoint: {}", LOG_PREFIX, apiEndpoint);
    log.info("{}   Model: {}", LOG_PREFIX, model);
    log.info("{}   Enabled: {}", LOG_PREFIX, enabled);
    log.info("{}   Max Conversation History: {}", LOG_PREFIX, maxConversationHistory);
    log.info("{}   Request Timeout: {}ms", LOG_PREFIX, requestTimeoutMs);
  }
  
  private static String parseStringConfigValue(Properties properties, String key, String defaultValue) {
    if (properties.containsKey(key)) {
      String value = properties.getProperty(key);
      if (value != null && !value.trim().isEmpty()) {
        return value.trim();
      }
    }
    properties.setProperty(key, defaultValue);
    return defaultValue;
  }
  
  // Getters
  public static String getApiEndpoint() {
    return apiEndpoint;
  }
  
  public static String getModel() {
    return model;
  }
  
  public static String getEffectiveModel() {
    if ("auto".equalsIgnoreCase(model)) {
      return detectedModel != null ? detectedModel : model;
    }
    return model;
  }
  
  public static void setDetectedModel(String model) {
    detectedModel = model;
    log.info("{} Auto-detected model: {}", LOG_PREFIX, model);
  }
  
  public static boolean isEnabled() {
    return enabled;
  }
  
  public static int getMaxConversationHistory() {
    return maxConversationHistory;
  }
  
  public static int getRequestTimeoutMs() {
    return requestTimeoutMs;
  }
  
  public static String getDefaultSystemPrompt() {
    return defaultSystemPrompt;
  }
  
  public static String getChatCompletionsUrl() {
    String base = apiEndpoint.endsWith("/") ? apiEndpoint : apiEndpoint + "/";
    return base + "chat/completions";
  }
  
  public static String getModelsUrl() {
    String base = apiEndpoint.endsWith("/") ? apiEndpoint : apiEndpoint + "/";
    return base + "models";
  }
}
