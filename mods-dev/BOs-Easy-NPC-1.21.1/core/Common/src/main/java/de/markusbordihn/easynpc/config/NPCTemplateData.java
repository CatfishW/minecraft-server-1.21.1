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

/**
 * POJO class representing a complete NPC configuration template.
 * Used for JSON serialization/deserialization.
 */
public class NPCTemplateData {
  
  private String name;
  private String entityType;
  private String description;
  private SkinConfig skin;
  private DialogConfig dialog;
  private TradingConfig trading;
  private AttributeConfig attributes;
  private ObjectiveConfig objectives;
  private ActionConfig actions;
  
  // Getters and setters
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  
  public String getEntityType() { return entityType; }
  public void setEntityType(String entityType) { this.entityType = entityType; }
  
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  
  public SkinConfig getSkin() { return skin; }
  public void setSkin(SkinConfig skin) { this.skin = skin; }
  
  public DialogConfig getDialog() { return dialog; }
  public void setDialog(DialogConfig dialog) { this.dialog = dialog; }
  
  public TradingConfig getTrading() { return trading; }
  public void setTrading(TradingConfig trading) { this.trading = trading; }
  
  public AttributeConfig getAttributes() { return attributes; }
  public void setAttributes(AttributeConfig attributes) { this.attributes = attributes; }
  
  public ObjectiveConfig getObjectives() { return objectives; }
  public void setObjectives(ObjectiveConfig objectives) { this.objectives = objectives; }
  
  public ActionConfig getActions() { return actions; }
  public void setActions(ActionConfig actions) { this.actions = actions; }
  
  /**
   * Skin configuration.
   */
  public static class SkinConfig {
    private String type = "DEFAULT"; // DEFAULT, PLAYER_SKIN, URL_SKIN, CUSTOM
    private String playerName;
    private String skinUrl;
    private String textureId;
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    
    public String getSkinUrl() { return skinUrl; }
    public void setSkinUrl(String skinUrl) { this.skinUrl = skinUrl; }
    
    public String getTextureId() { return textureId; }
    public void setTextureId(String textureId) { this.textureId = textureId; }
  }
  
  /**
   * Dialog configuration.
   */
  public static class DialogConfig {
    private String greeting;
    private DialogButton[] buttons;
    private AdditionalDialog[] additionalDialogs;
    private boolean useLLM = false;
    private String llmSystemPrompt;
    
    public String getGreeting() { return greeting; }
    public void setGreeting(String greeting) { this.greeting = greeting; }
    
    public DialogButton[] getButtons() { return buttons; }
    public void setButtons(DialogButton[] buttons) { this.buttons = buttons; }
    
    public AdditionalDialog[] getAdditionalDialogs() { return additionalDialogs; }
    public void setAdditionalDialogs(AdditionalDialog[] additionalDialogs) { this.additionalDialogs = additionalDialogs; }
    
    public boolean isUseLLM() { return useLLM; }
    public void setUseLLM(boolean useLLM) { this.useLLM = useLLM; }
    
    public String getLlmSystemPrompt() { return llmSystemPrompt; }
    public void setLlmSystemPrompt(String llmSystemPrompt) { this.llmSystemPrompt = llmSystemPrompt; }
  }
  
  /**
   * Dialog button.
   */
  public static class DialogButton {
    private String label;
    private String action;
    private String condition;
    
    public DialogButton() {}
    
    public DialogButton(String label, String action) {
      this.label = label;
      this.action = action;
    }
    
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
  }
  
  /**
   * Additional dialog entry.
   */
  public static class AdditionalDialog {
    private String id;
    private String text;
    private DialogButton[] buttons;
    
    public AdditionalDialog() {}
    
    public AdditionalDialog(String id, String text) {
      this.id = id;
      this.text = text;
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public DialogButton[] getButtons() { return buttons; }
    public void setButtons(DialogButton[] buttons) { this.buttons = buttons; }
  }
  
  /**
   * Trading configuration.
   */
  public static class TradingConfig {
    private String type = "BASIC"; // NONE, BASIC, ADVANCED, CUSTOM
    private int maxUses = 64;
    private int rewardedXP = 0;
    private int resetsEveryMin = 0;
    private TradeOffer[] offers;
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public int getMaxUses() { return maxUses; }
    public void setMaxUses(int maxUses) { this.maxUses = maxUses; }
    
    public int getRewardedXP() { return rewardedXP; }
    public void setRewardedXP(int rewardedXP) { this.rewardedXP = rewardedXP; }
    
    public int getResetsEveryMin() { return resetsEveryMin; }
    public void setResetsEveryMin(int resetsEveryMin) { this.resetsEveryMin = resetsEveryMin; }
    
    public TradeOffer[] getOffers() { return offers; }
    public void setOffers(TradeOffer[] offers) { this.offers = offers; }
  }
  
  /**
   * Trade offer.
   */
  public static class TradeOffer {
    private ItemStack buy;
    private ItemStack buyExtra;
    private ItemStack sell;
    private int maxUses = 64;
    private int xpReward = 1;
    
    public TradeOffer() {}
    
    public TradeOffer(ItemStack buy, ItemStack sell) {
      this.buy = buy;
      this.sell = sell;
    }
    
    public ItemStack getBuy() { return buy; }
    public void setBuy(ItemStack buy) { this.buy = buy; }
    
    public ItemStack getBuyExtra() { return buyExtra; }
    public void setBuyExtra(ItemStack buyExtra) { this.buyExtra = buyExtra; }
    
    public ItemStack getSell() { return sell; }
    public void setSell(ItemStack sell) { this.sell = sell; }
    
    public int getMaxUses() { return maxUses; }
    public void setMaxUses(int maxUses) { this.maxUses = maxUses; }
    
    public int getXpReward() { return xpReward; }
    public void setXpReward(int xpReward) { this.xpReward = xpReward; }
  }
  
  /**
   * Item stack representation.
   */
  public static class ItemStack {
    private String item;
    private int count = 1;
    private String nbt;
    
    public ItemStack() {}
    
    public ItemStack(String item, int count) {
      this.item = item;
      this.count = count;
    }
    
    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }
    
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    
    public String getNbt() { return nbt; }
    public void setNbt(String nbt) { this.nbt = nbt; }
  }
  
  /**
   * Attribute configuration.
   */
  public static class AttributeConfig {
    private int maxHealth = 20;
    private double movementSpeed = 0.25;
    private double attackDamage = 2;
    private double armor = 0;
    private boolean invulnerable = false;
    private boolean canBePushed = true;
    private boolean canFloat = true;
    private boolean canOpenDoors = true;
    private boolean canClimb = true;
    
    public int getMaxHealth() { return maxHealth; }
    public void setMaxHealth(int maxHealth) { this.maxHealth = maxHealth; }
    
    public double getMovementSpeed() { return movementSpeed; }
    public void setMovementSpeed(double movementSpeed) { this.movementSpeed = movementSpeed; }
    
    public double getAttackDamage() { return attackDamage; }
    public void setAttackDamage(double attackDamage) { this.attackDamage = attackDamage; }
    
    public double getArmor() { return armor; }
    public void setArmor(double armor) { this.armor = armor; }
    
    public boolean isInvulnerable() { return invulnerable; }
    public void setInvulnerable(boolean invulnerable) { this.invulnerable = invulnerable; }
    
    public boolean isCanBePushed() { return canBePushed; }
    public void setCanBePushed(boolean canBePushed) { this.canBePushed = canBePushed; }
    
    public boolean isCanFloat() { return canFloat; }
    public void setCanFloat(boolean canFloat) { this.canFloat = canFloat; }
    
    public boolean isCanOpenDoors() { return canOpenDoors; }
    public void setCanOpenDoors(boolean canOpenDoors) { this.canOpenDoors = canOpenDoors; }
    
    public boolean isCanClimb() { return canClimb; }
    public void setCanClimb(boolean canClimb) { this.canClimb = canClimb; }
  }
  
  /**
   * Objective configuration.
   */
  public static class ObjectiveConfig {
    private boolean attackHostileMobs = false;
    private boolean attackPlayers = false;
    private boolean followOwner = false;
    private boolean returnToSpawn = true;
    private int followRange = 16;
    private int wanderRange = 0;
    private String[] targetEntityTypes;
    
    public boolean isAttackHostileMobs() { return attackHostileMobs; }
    public void setAttackHostileMobs(boolean attackHostileMobs) { this.attackHostileMobs = attackHostileMobs; }
    
    public boolean isAttackPlayers() { return attackPlayers; }
    public void setAttackPlayers(boolean attackPlayers) { this.attackPlayers = attackPlayers; }
    
    public boolean isFollowOwner() { return followOwner; }
    public void setFollowOwner(boolean followOwner) { this.followOwner = followOwner; }
    
    public boolean isReturnToSpawn() { return returnToSpawn; }
    public void setReturnToSpawn(boolean returnToSpawn) { this.returnToSpawn = returnToSpawn; }
    
    public int getFollowRange() { return followRange; }
    public void setFollowRange(int followRange) { this.followRange = followRange; }
    
    public int getWanderRange() { return wanderRange; }
    public void setWanderRange(int wanderRange) { this.wanderRange = wanderRange; }
    
    public String[] getTargetEntityTypes() { return targetEntityTypes; }
    public void setTargetEntityTypes(String[] targetEntityTypes) { this.targetEntityTypes = targetEntityTypes; }
  }
  
  /**
   * Action configuration.
   */
  public static class ActionConfig {
    private ActionEvent[] onInteract;
    private ActionEvent[] onDeath;
    private ActionEvent[] onSpawn;
    private ActionEvent[] onHurt;
    
    public ActionEvent[] getOnInteract() { return onInteract; }
    public void setOnInteract(ActionEvent[] onInteract) { this.onInteract = onInteract; }
    
    public ActionEvent[] getOnDeath() { return onDeath; }
    public void setOnDeath(ActionEvent[] onDeath) { this.onDeath = onDeath; }
    
    public ActionEvent[] getOnSpawn() { return onSpawn; }
    public void setOnSpawn(ActionEvent[] onSpawn) { this.onSpawn = onSpawn; }
    
    public ActionEvent[] getOnHurt() { return onHurt; }
    public void setOnHurt(ActionEvent[] onHurt) { this.onHurt = onHurt; }
  }
  
  /**
   * Action event.
   */
  public static class ActionEvent {
    private String type; // COMMAND, OPEN_DIALOG, OPEN_TRADING, TELEPORT
    private String command;
    private boolean executeAsUser = false;
    private int permissionLevel = 0;
    private String condition;
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    
    public boolean isExecuteAsUser() { return executeAsUser; }
    public void setExecuteAsUser(boolean executeAsUser) { this.executeAsUser = executeAsUser; }
    
    public int getPermissionLevel() { return permissionLevel; }
    public void setPermissionLevel(int permissionLevel) { this.permissionLevel = permissionLevel; }
    
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
  }
}
