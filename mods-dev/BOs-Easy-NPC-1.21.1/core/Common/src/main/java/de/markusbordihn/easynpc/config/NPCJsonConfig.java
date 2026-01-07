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

package de.markusbordihn.easynpc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import de.markusbordihn.easynpc.Constants;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * JSON-based configuration system for Easy NPC.
 * Handles loading NPC templates and global settings from JSON files.
 */
public class NPCJsonConfig {
  
  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String LOG_PREFIX = "[NPCJsonConfig]";
  
  private static final String TEMPLATES_FOLDER = "npc_templates";
  private static final String SETTINGS_FILE = "settings.json";
  private static final String JSON_EXTENSION = ".json";
  
  private static final Gson GSON = new GsonBuilder()
      .setPrettyPrinting()
      .disableHtmlEscaping()
      .create();
  
  // Cached templates
  private static final Map<String, NPCTemplateData> templateCache = new HashMap<>();
  
  // Global settings
  private static GlobalSettings globalSettings = new GlobalSettings();
  
  private static Path configPath;
  private static boolean initialized = false;
  
  private NPCJsonConfig() {}
  
  /**
   * Register and initialize the JSON configuration system.
   */
  public static void registerConfig() {
    if (initialized) {
      log.warn("{} Configuration already initialized", LOG_PREFIX);
      return;
    }
    
    configPath = getConfigPath();
    if (configPath == null) {
      log.error("{} Failed to get configuration path", LOG_PREFIX);
      return;
    }
    
    log.info("{} Initializing JSON configuration at {}", LOG_PREFIX, configPath);
    
    // Create directories
    createDirectories();
    
    // Generate example files if they don't exist
    generateExampleFiles();
    
    // Load global settings
    loadGlobalSettings();
    
    // Load all templates
    reloadTemplates();
    
    initialized = true;
    log.info("{} JSON configuration initialized successfully", LOG_PREFIX);
  }
  
  /**
   * Get the configuration path.
   */
  private static Path getConfigPath() {
    if (Constants.CONFIG_DIR != null) {
      return Constants.CONFIG_DIR.resolve(Constants.MOD_ID);
    }
    return null;
  }
  
  /**
   * Create required directories.
   */
  private static void createDirectories() {
    try {
      Path templatesPath = configPath.resolve(TEMPLATES_FOLDER);
      if (!Files.exists(templatesPath)) {
        Files.createDirectories(templatesPath);
        log.info("{} Created templates directory at {}", LOG_PREFIX, templatesPath);
      }
    } catch (IOException e) {
      log.error("{} Failed to create directories:", LOG_PREFIX, e);
    }
  }
  
  /**
   * Generate example configuration files.
   */
  private static void generateExampleFiles() {
    // Generate settings.json if it doesn't exist
    File settingsFile = configPath.resolve(SETTINGS_FILE).toFile();
    if (!settingsFile.exists()) {
      GlobalSettings defaultSettings = new GlobalSettings();
      saveJsonFile(settingsFile, defaultSettings);
      log.info("{} Generated example settings.json", LOG_PREFIX);
    }
    
    // Generate example NPC template
    File exampleTemplate = configPath.resolve(TEMPLATES_FOLDER).resolve("merchant.json").toFile();
    if (!exampleTemplate.exists()) {
      NPCTemplateData merchantTemplate = createMerchantExample();
      saveJsonFile(exampleTemplate, merchantTemplate);
      log.info("{} Generated example merchant.json template", LOG_PREFIX);
    }
    
    // Generate example guard template
    File guardTemplate = configPath.resolve(TEMPLATES_FOLDER).resolve("guard.json").toFile();
    if (!guardTemplate.exists()) {
      NPCTemplateData guardNpc = createGuardExample();
      saveJsonFile(guardTemplate, guardNpc);
      log.info("{} Generated example guard.json template", LOG_PREFIX);
    }
  }
  
  /**
   * Create example merchant NPC template.
   */
  private static NPCTemplateData createMerchantExample() {
    NPCTemplateData template = new NPCTemplateData();
    template.setName("Village Merchant");
    template.setEntityType("easy_npc:villager");
    template.setDescription("A friendly merchant selling various goods");
    
    // Skin config
    NPCTemplateData.SkinConfig skin = new NPCTemplateData.SkinConfig();
    skin.setType("PLAYER_SKIN");
    skin.setPlayerName("Steve");
    template.setSkin(skin);
    
    // Dialog config
    NPCTemplateData.DialogConfig dialog = new NPCTemplateData.DialogConfig();
    dialog.setGreeting("Welcome to my shop, traveler! What can I help you with?");
    dialog.setButtons(new NPCTemplateData.DialogButton[] {
        new NPCTemplateData.DialogButton("Trade", "OPEN_TRADING"),
        new NPCTemplateData.DialogButton("Tell me about yourself", "SHOW_DIALOG:about"),
        new NPCTemplateData.DialogButton("Goodbye", "CLOSE_DIALOG")
    });
    dialog.setAdditionalDialogs(new NPCTemplateData.AdditionalDialog[] {
        new NPCTemplateData.AdditionalDialog("about", 
            "I've been a merchant in this village for many years. My family has traded here for generations!")
    });
    template.setDialog(dialog);
    
    // Trading config
    NPCTemplateData.TradingConfig trading = new NPCTemplateData.TradingConfig();
    trading.setType("BASIC");
    trading.setMaxUses(64);
    trading.setRewardedXP(5);
    trading.setResetsEveryMin(1440); // 24 hours
    trading.setOffers(new NPCTemplateData.TradeOffer[] {
        new NPCTemplateData.TradeOffer(
            new NPCTemplateData.ItemStack("minecraft:emerald", 1),
            new NPCTemplateData.ItemStack("minecraft:diamond", 1)
        ),
        new NPCTemplateData.TradeOffer(
            new NPCTemplateData.ItemStack("minecraft:emerald", 5),
            new NPCTemplateData.ItemStack("minecraft:iron_ingot", 16)
        ),
        new NPCTemplateData.TradeOffer(
            new NPCTemplateData.ItemStack("minecraft:wheat", 20),
            new NPCTemplateData.ItemStack("minecraft:emerald", 1)
        )
    });
    template.setTrading(trading);
    
    // Attributes config
    NPCTemplateData.AttributeConfig attributes = new NPCTemplateData.AttributeConfig();
    attributes.setMaxHealth(20);
    attributes.setMovementSpeed(0.25);
    attributes.setInvulnerable(true);
    attributes.setCanBePushed(false);
    template.setAttributes(attributes);
    
    return template;
  }
  
  /**
   * Create example guard NPC template.
   */
  private static NPCTemplateData createGuardExample() {
    NPCTemplateData template = new NPCTemplateData();
    template.setName("Village Guard");
    template.setEntityType("easy_npc:iron_golem");
    template.setDescription("A guard that protects the village from hostile mobs");
    
    // Skin config
    NPCTemplateData.SkinConfig skin = new NPCTemplateData.SkinConfig();
    skin.setType("DEFAULT");
    template.setSkin(skin);
    
    // Dialog config
    NPCTemplateData.DialogConfig dialog = new NPCTemplateData.DialogConfig();
    dialog.setGreeting("Halt! State your business in our village.");
    dialog.setButtons(new NPCTemplateData.DialogButton[] {
        new NPCTemplateData.DialogButton("I'm just passing through", "SHOW_DIALOG:pass"),
        new NPCTemplateData.DialogButton("I need protection", "SHOW_DIALOG:protect"),
        new NPCTemplateData.DialogButton("Goodbye", "CLOSE_DIALOG")
    });
    dialog.setAdditionalDialogs(new NPCTemplateData.AdditionalDialog[] {
        new NPCTemplateData.AdditionalDialog("pass", "Very well, move along. Stay out of trouble."),
        new NPCTemplateData.AdditionalDialog("protect", "I guard this village day and night. You're safe here.")
    });
    template.setDialog(dialog);
    
    // Attributes config
    NPCTemplateData.AttributeConfig attributes = new NPCTemplateData.AttributeConfig();
    attributes.setMaxHealth(100);
    attributes.setMovementSpeed(0.2);
    attributes.setInvulnerable(false);
    attributes.setAttackDamage(10);
    template.setAttributes(attributes);
    
    // Objectives config
    NPCTemplateData.ObjectiveConfig objectives = new NPCTemplateData.ObjectiveConfig();
    objectives.setAttackHostileMobs(true);
    objectives.setFollowRange(32);
    objectives.setReturnToSpawn(true);
    template.setObjectives(objectives);
    
    return template;
  }
  
  /**
   * Load global settings from settings.json.
   */
  public static void loadGlobalSettings() {
    File settingsFile = configPath.resolve(SETTINGS_FILE).toFile();
    if (!settingsFile.exists()) {
      log.info("{} Settings file not found, using defaults", LOG_PREFIX);
      globalSettings = new GlobalSettings();
      return;
    }
    
    try (FileReader reader = new FileReader(settingsFile)) {
      globalSettings = GSON.fromJson(reader, GlobalSettings.class);
      if (globalSettings == null) {
        globalSettings = new GlobalSettings();
      }
      log.info("{} Loaded global settings from {}", LOG_PREFIX, settingsFile);
    } catch (IOException | JsonSyntaxException e) {
      log.error("{} Failed to load settings file:", LOG_PREFIX, e);
      globalSettings = new GlobalSettings();
    }
  }
  
  /**
   * Reload all templates from disk.
   */
  public static void reloadTemplates() {
    templateCache.clear();
    
    Path templatesPath = configPath.resolve(TEMPLATES_FOLDER);
    if (!Files.exists(templatesPath)) {
      log.warn("{} Templates directory does not exist", LOG_PREFIX);
      return;
    }
    
    try (Stream<Path> files = Files.list(templatesPath)) {
      files.filter(path -> path.toString().endsWith(JSON_EXTENSION))
           .forEach(NPCJsonConfig::loadTemplate);
    } catch (IOException e) {
      log.error("{} Failed to list template files:", LOG_PREFIX, e);
    }
    
    log.info("{} Loaded {} NPC templates", LOG_PREFIX, templateCache.size());
  }
  
  /**
   * Load a single template file.
   */
  private static void loadTemplate(Path templatePath) {
    String fileName = templatePath.getFileName().toString();
    String templateName = fileName.substring(0, fileName.length() - JSON_EXTENSION.length());
    
    try (FileReader reader = new FileReader(templatePath.toFile())) {
      NPCTemplateData template = GSON.fromJson(reader, NPCTemplateData.class);
      if (template != null) {
        templateCache.put(templateName, template);
        log.debug("{} Loaded template: {}", LOG_PREFIX, templateName);
      }
    } catch (IOException | JsonSyntaxException e) {
      log.error("{} Failed to load template {}:", LOG_PREFIX, templateName, e);
    }
  }
  
  /**
   * Get a template by name.
   */
  public static NPCTemplateData getTemplate(String name) {
    return templateCache.get(name);
  }
  
  /**
   * Get all template names.
   */
  public static Set<String> getTemplateNames() {
    return templateCache.keySet();
  }
  
  /**
   * Check if a template exists.
   */
  public static boolean hasTemplate(String name) {
    return templateCache.containsKey(name);
  }
  
  /**
   * Save a template to disk.
   */
  public static boolean saveTemplate(String name, NPCTemplateData template) {
    File templateFile = configPath.resolve(TEMPLATES_FOLDER).resolve(name + JSON_EXTENSION).toFile();
    boolean success = saveJsonFile(templateFile, template);
    if (success) {
      templateCache.put(name, template);
    }
    return success;
  }
  
  /**
   * Save an object to a JSON file.
   */
  private static <T> boolean saveJsonFile(File file, T object) {
    try (FileWriter writer = new FileWriter(file)) {
      GSON.toJson(object, writer);
      return true;
    } catch (IOException e) {
      log.error("{} Failed to save file {}:", LOG_PREFIX, file, e);
      return false;
    }
  }
  
  /**
   * Get the global settings.
   */
  public static GlobalSettings getGlobalSettings() {
    return globalSettings;
  }
  
  /**
   * Get the templates folder path.
   */
  public static Path getTemplatesPath() {
    return configPath != null ? configPath.resolve(TEMPLATES_FOLDER) : null;
  }
  
  /**
   * Check if the configuration system is initialized.
   */
  public static boolean isInitialized() {
    return initialized;
  }
  
  /**
   * Global settings container.
   */
  public static class GlobalSettings {
    private LLMSettings llm = new LLMSettings();
    private SpawnerSettings spawner = new SpawnerSettings();
    private DebugSettings debug = new DebugSettings();
    
    public LLMSettings getLlm() { return llm; }
    public void setLlm(LLMSettings llm) { this.llm = llm; }
    
    public SpawnerSettings getSpawner() { return spawner; }
    public void setSpawner(SpawnerSettings spawner) { this.spawner = spawner; }
    
    public DebugSettings getDebug() { return debug; }
    public void setDebug(DebugSettings debug) { this.debug = debug; }
  }
  
  /**
   * LLM settings.
   */
  public static class LLMSettings {
    private boolean enabled = true;
    private String apiEndpoint = "https://game.agaii.org/llm/v1";
    private String model = "auto";
    private int maxConversationHistory = 10;
    private int requestTimeoutMs = 30000;
    private String defaultSystemPrompt = "You are an NPC in Minecraft. Respond in character, be helpful and friendly. Keep responses concise.";
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public String getApiEndpoint() { return apiEndpoint; }
    public void setApiEndpoint(String apiEndpoint) { this.apiEndpoint = apiEndpoint; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public int getMaxConversationHistory() { return maxConversationHistory; }
    public void setMaxConversationHistory(int maxConversationHistory) { this.maxConversationHistory = maxConversationHistory; }
    
    public int getRequestTimeoutMs() { return requestTimeoutMs; }
    public void setRequestTimeoutMs(int requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }
    
    public String getDefaultSystemPrompt() { return defaultSystemPrompt; }
    public void setDefaultSystemPrompt(String defaultSystemPrompt) { this.defaultSystemPrompt = defaultSystemPrompt; }
  }
  
  /**
   * Spawner settings.
   */
  public static class SpawnerSettings {
    private int maxSpawnDistance = 64;
    private int maxNpcsPerChunk = 4;
    private boolean debugEnabled = false;
    
    public int getMaxSpawnDistance() { return maxSpawnDistance; }
    public void setMaxSpawnDistance(int maxSpawnDistance) { this.maxSpawnDistance = maxSpawnDistance; }
    
    public int getMaxNpcsPerChunk() { return maxNpcsPerChunk; }
    public void setMaxNpcsPerChunk(int maxNpcsPerChunk) { this.maxNpcsPerChunk = maxNpcsPerChunk; }
    
    public boolean isDebugEnabled() { return debugEnabled; }
    public void setDebugEnabled(boolean debugEnabled) { this.debugEnabled = debugEnabled; }
  }
  
  /**
   * Debug settings.
   */
  public static class DebugSettings {
    private boolean logTemplateLoading = false;
    private boolean logConfigChanges = true;
    
    public boolean isLogTemplateLoading() { return logTemplateLoading; }
    public void setLogTemplateLoading(boolean logTemplateLoading) { this.logTemplateLoading = logTemplateLoading; }
    
    public boolean isLogConfigChanges() { return logConfigChanges; }
    public void setLogConfigChanges(boolean logConfigChanges) { this.logConfigChanges = logConfigChanges; }
  }
}
