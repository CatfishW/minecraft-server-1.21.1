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

package de.markusbordihn.easynpc.data.crime;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Template for merchant NPCs that participate in the crime system.
 */
public class MerchantTemplate {

  public static final String DATA_MERCHANT_TEMPLATE_TAG = "MerchantTemplate";
  public static final String DATA_ID_TAG = "Id";
  public static final String DATA_NAME_TAG = "Name";
  public static final String DATA_PROFESSION_TAG = "Profession";
  public static final String DATA_HEALTH_TAG = "Health";
  public static final String DATA_BEHAVIOR_TAG = "Behavior";
  public static final String DATA_MIN_GROUP_SIZE_TAG = "MinGroupSize";
  public static final String DATA_MAX_GROUP_SIZE_TAG = "MaxGroupSize";
  public static final String DATA_SPAWN_INTERVAL_TAG = "SpawnIntervalTicks";
  public static final String DATA_RESPAWN_COOLDOWN_TAG = "RespawnCooldown";
  public static final String DATA_NPC_TEMPLATE_TAG = "NPCTemplate";
  public static final String DATA_CURRENCY_DROP_MIN_TAG = "CurrencyDropMin";
  public static final String DATA_CURRENCY_DROP_MAX_TAG = "CurrencyDropMax";

  public static final StreamCodec<RegistryFriendlyByteBuf, MerchantTemplate> STREAM_CODEC =
      new StreamCodec<>() {
        @Override
        public MerchantTemplate decode(RegistryFriendlyByteBuf buf) {
          return new MerchantTemplate(buf.readNbt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, MerchantTemplate template) {
          buf.writeNbt(template.createTag());
        }
      };

  private UUID id;
  private String name;
  private String profession;
  private int health;
  private AIBehavior behavior;
  private int minGroupSize;
  private int maxGroupSize;
  private int spawnIntervalTicks;
  private int respawnCooldown;
  private String npcTemplateName; // Reference to Easy NPC template
  private int currencyDropMin;
  private int currencyDropMax;

  public MerchantTemplate() {
    this.id = UUID.randomUUID();
    this.name = "Merchant";
    this.profession = "trader";
    this.health = 20;
    this.behavior = AIBehavior.FLEE;
    this.minGroupSize = 1;
    this.maxGroupSize = 3;
    this.spawnIntervalTicks = 12000; // 10 minutes
    this.respawnCooldown = 6000; // 5 minutes
    this.npcTemplateName = "";
    this.currencyDropMin = 1;
    this.currencyDropMax = 10;
  }

  public MerchantTemplate(String name, String profession) {
    this();
    this.name = name;
    this.profession = profession;
  }

  public MerchantTemplate(CompoundTag compoundTag) {
    this();
    this.load(compoundTag);
  }

  // Getters and Setters
  public UUID getId() {
    return this.id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return this.name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getProfession() {
    return this.profession;
  }

  public void setProfession(String profession) {
    this.profession = profession;
  }

  public int getHealth() {
    return this.health;
  }

  public void setHealth(int health) {
    this.health = Math.max(1, health);
  }

  public AIBehavior getBehavior() {
    return this.behavior;
  }

  public void setBehavior(AIBehavior behavior) {
    this.behavior = behavior;
  }

  public int getMinGroupSize() {
    return this.minGroupSize;
  }

  public void setMinGroupSize(int size) {
    this.minGroupSize = Math.max(1, size);
  }

  public int getMaxGroupSize() {
    return this.maxGroupSize;
  }

  public void setMaxGroupSize(int size) {
    this.maxGroupSize = Math.max(this.minGroupSize, size);
  }

  public int getSpawnIntervalTicks() {
    return this.spawnIntervalTicks;
  }

  public void setSpawnIntervalTicks(int ticks) {
    this.spawnIntervalTicks = Math.max(0, ticks);
  }

  public int getRespawnCooldown() {
    return this.respawnCooldown;
  }

  public void setRespawnCooldown(int cooldown) {
    this.respawnCooldown = Math.max(0, cooldown);
  }

  public String getNpcTemplateName() {
    return this.npcTemplateName;
  }

  public void setNpcTemplateName(String templateName) {
    this.npcTemplateName = templateName != null ? templateName : "";
  }

  public int getCurrencyDropMin() {
    return this.currencyDropMin;
  }

  public void setCurrencyDropMin(int min) {
    this.currencyDropMin = Math.max(0, min);
  }

  public int getCurrencyDropMax() {
    return this.currencyDropMax;
  }

  public void setCurrencyDropMax(int max) {
    this.currencyDropMax = Math.max(this.currencyDropMin, max);
  }

  /**
   * Get a random group size between min and max.
   */
  public int getRandomGroupSize() {
    if (this.minGroupSize == this.maxGroupSize) {
      return this.minGroupSize;
    }
    return this.minGroupSize + (int) (Math.random() * (this.maxGroupSize - this.minGroupSize + 1));
  }

  /**
   * Get a random currency drop amount.
   */
  public int getRandomCurrencyDrop() {
    if (this.currencyDropMin == this.currencyDropMax) {
      return this.currencyDropMin;
    }
    return this.currencyDropMin + (int) (Math.random() * (this.currencyDropMax - this.currencyDropMin + 1));
  }

  public void load(CompoundTag compoundTag) {
    if (compoundTag == null || !compoundTag.contains(DATA_MERCHANT_TEMPLATE_TAG)) {
      return;
    }

    CompoundTag data = compoundTag.getCompound(DATA_MERCHANT_TEMPLATE_TAG);
    if (data.contains(DATA_ID_TAG)) {
      this.id = UUID.fromString(data.getString(DATA_ID_TAG));
    }
    this.name = data.getString(DATA_NAME_TAG);
    this.profession = data.getString(DATA_PROFESSION_TAG);
    this.health = data.getInt(DATA_HEALTH_TAG);
    this.behavior = AIBehavior.get(data.getString(DATA_BEHAVIOR_TAG));
    this.minGroupSize = data.getInt(DATA_MIN_GROUP_SIZE_TAG);
    this.maxGroupSize = data.getInt(DATA_MAX_GROUP_SIZE_TAG);
    this.spawnIntervalTicks = data.getInt(DATA_SPAWN_INTERVAL_TAG);
    this.respawnCooldown = data.getInt(DATA_RESPAWN_COOLDOWN_TAG);
    this.npcTemplateName = data.getString(DATA_NPC_TEMPLATE_TAG);
    this.currencyDropMin = data.getInt(DATA_CURRENCY_DROP_MIN_TAG);
    this.currencyDropMax = data.getInt(DATA_CURRENCY_DROP_MAX_TAG);
  }

  public CompoundTag save(CompoundTag compoundTag) {
    CompoundTag data = new CompoundTag();
    data.putString(DATA_ID_TAG, this.id.toString());
    data.putString(DATA_NAME_TAG, this.name);
    data.putString(DATA_PROFESSION_TAG, this.profession);
    data.putInt(DATA_HEALTH_TAG, this.health);
    data.putString(DATA_BEHAVIOR_TAG, this.behavior.name());
    data.putInt(DATA_MIN_GROUP_SIZE_TAG, this.minGroupSize);
    data.putInt(DATA_MAX_GROUP_SIZE_TAG, this.maxGroupSize);
    data.putInt(DATA_SPAWN_INTERVAL_TAG, this.spawnIntervalTicks);
    data.putInt(DATA_RESPAWN_COOLDOWN_TAG, this.respawnCooldown);
    data.putString(DATA_NPC_TEMPLATE_TAG, this.npcTemplateName);
    data.putInt(DATA_CURRENCY_DROP_MIN_TAG, this.currencyDropMin);
    data.putInt(DATA_CURRENCY_DROP_MAX_TAG, this.currencyDropMax);

    compoundTag.put(DATA_MERCHANT_TEMPLATE_TAG, data);
    return compoundTag;
  }

  public CompoundTag createTag() {
    return this.save(new CompoundTag());
  }

  @Override
  public String toString() {
    return "MerchantTemplate{id=" + id + ", name='" + name + "', profession='" + profession + 
           "', groupSize=" + minGroupSize + "-" + maxGroupSize + "}";
  }
}
