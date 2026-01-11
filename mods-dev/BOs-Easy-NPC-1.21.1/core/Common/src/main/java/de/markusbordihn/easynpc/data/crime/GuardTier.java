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

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Defines a tier of guards that spawn at specific wanted levels.
 */
public class GuardTier {

  public static final String DATA_GUARD_TIER_TAG = "GuardTier";
  public static final String DATA_TIER_TAG = "Tier";
  public static final String DATA_MIN_WANTED_TAG = "MinWantedLevel";
  public static final String DATA_HEALTH_TAG = "Health";
  public static final String DATA_SPEED_TAG = "Speed";
  public static final String DATA_ATTACK_DAMAGE_TAG = "AttackDamage";
  public static final String DATA_WEAPONS_TAG = "Weapons";
  public static final String DATA_ARMOR_TAG = "Armor";
  public static final String DATA_IS_ARCHER_TAG = "IsArcher";
  public static final String DATA_IS_CAPTAIN_TAG = "IsCaptain";
  public static final String DATA_IS_TRACKER_TAG = "IsTracker";
  public static final String DATA_SPAWN_RADIUS_TAG = "SpawnRadius";
  public static final String DATA_DESPAWN_DISTANCE_TAG = "DespawnDistance";
  public static final String DATA_DESPAWN_TIME_TAG = "DespawnTime";
  public static final String DATA_SQUAD_SIZE_TAG = "SquadSize";
  public static final String DATA_TEMPLATE_NAME_TAG = "TemplateName";

  public static final StreamCodec<RegistryFriendlyByteBuf, GuardTier> STREAM_CODEC =
      new StreamCodec<>() {
        @Override
        public GuardTier decode(RegistryFriendlyByteBuf buf) {
          return new GuardTier(buf.readNbt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, GuardTier tier) {
          buf.writeNbt(tier.createTag());
        }
      };

  private int tier;
  private int minWantedLevel;
  private int health;
  private float speed;
  private float attackDamage;
  private List<ItemStack> weapons;
  private List<ItemStack> armor;
  private boolean isArcher;
  private boolean isCaptain;
  private boolean isTracker;
  private int spawnRadius;
  private int despawnDistance;
  private int despawnTime;
  private int squadSize;
  private String templateName;

  public GuardTier() {
    this.tier = 1;
    this.minWantedLevel = 1;
    this.health = 20;
    this.speed = 0.35f;
    this.attackDamage = 4.0f;
    this.weapons = new ArrayList<>();
    this.armor = new ArrayList<>();
    this.isArcher = false;
    this.isCaptain = false;
    this.isTracker = false;
    this.spawnRadius = 30;
    this.despawnDistance = 100;
    this.despawnTime = 6000;
    this.squadSize = 2;
    this.templateName = "";
  }

  public GuardTier(int tier, int minWantedLevel) {
    this();
    this.tier = tier;
    this.minWantedLevel = minWantedLevel;
    // Scale stats based on tier
    this.health = 20 + (tier - 1) * 10;
    this.attackDamage = 4.0f + (tier - 1) * 2.0f;
    this.squadSize = 2 + (tier - 1);
  }

  public GuardTier(CompoundTag compoundTag) {
    this();
    this.load(compoundTag);
  }

  // Getters and Setters
  public int getTier() {
    return this.tier;
  }

  public void setTier(int tier) {
    this.tier = Math.max(1, tier);
  }

  public int getMinWantedLevel() {
    return this.minWantedLevel;
  }

  public void setMinWantedLevel(int level) {
    this.minWantedLevel = Math.max(1, level);
  }

  public int getHealth() {
    return this.health;
  }

  public void setHealth(int health) {
    this.health = Math.max(1, health);
  }

  public float getSpeed() {
    return this.speed;
  }

  public void setSpeed(float speed) {
    this.speed = Math.max(0.1f, speed);
  }

  public float getAttackDamage() {
    return this.attackDamage;
  }

  public void setAttackDamage(float damage) {
    this.attackDamage = Math.max(0, damage);
  }

  public List<ItemStack> getWeapons() {
    return this.weapons;
  }

  public void setWeapons(List<ItemStack> weapons) {
    this.weapons = weapons;
  }

  public List<ItemStack> getArmor() {
    return this.armor;
  }

  public void setArmor(List<ItemStack> armor) {
    this.armor = armor;
  }

  public boolean isArcher() {
    return this.isArcher;
  }

  public void setArcher(boolean isArcher) {
    this.isArcher = isArcher;
  }

  public boolean isCaptain() {
    return this.isCaptain;
  }

  public void setCaptain(boolean isCaptain) {
    this.isCaptain = isCaptain;
  }

  public boolean isTracker() {
    return this.isTracker;
  }

  public void setTracker(boolean isTracker) {
    this.isTracker = isTracker;
  }

  public int getSpawnRadius() {
    return this.spawnRadius;
  }

  public void setSpawnRadius(int radius) {
    this.spawnRadius = Math.max(1, radius);
  }

  public int getDespawnDistance() {
    return this.despawnDistance;
  }

  public void setDespawnDistance(int distance) {
    this.despawnDistance = Math.max(1, distance);
  }

  public int getDespawnTime() {
    return this.despawnTime;
  }

  public void setDespawnTime(int time) {
    this.despawnTime = Math.max(0, time);
  }

  public int getSquadSize() {
    return this.squadSize;
  }

  public void setSquadSize(int size) {
    this.squadSize = Math.max(1, size);
  }

  public String getTemplateName() {
    return this.templateName;
  }

  public void setTemplateName(String templateName) {
    this.templateName = templateName != null ? templateName : "";
  }

  public void load(CompoundTag compoundTag) {
    if (compoundTag == null || !compoundTag.contains(DATA_GUARD_TIER_TAG)) {
      return;
    }

    CompoundTag data = compoundTag.getCompound(DATA_GUARD_TIER_TAG);
    this.tier = data.getInt(DATA_TIER_TAG);
    this.minWantedLevel = data.getInt(DATA_MIN_WANTED_TAG);
    this.health = data.getInt(DATA_HEALTH_TAG);
    this.speed = data.getFloat(DATA_SPEED_TAG);
    this.attackDamage = data.getFloat(DATA_ATTACK_DAMAGE_TAG);
    this.isArcher = data.getBoolean(DATA_IS_ARCHER_TAG);
    this.isCaptain = data.getBoolean(DATA_IS_CAPTAIN_TAG);
    this.isTracker = data.getBoolean(DATA_IS_TRACKER_TAG);
    this.spawnRadius = data.getInt(DATA_SPAWN_RADIUS_TAG);
    this.despawnDistance = data.getInt(DATA_DESPAWN_DISTANCE_TAG);
    this.despawnTime = data.getInt(DATA_DESPAWN_TIME_TAG);
    this.squadSize = data.getInt(DATA_SQUAD_SIZE_TAG);
    this.templateName = data.getString(DATA_TEMPLATE_NAME_TAG);

    // Note: ItemStack serialization would require registry access, simplified for now
  }

  public CompoundTag save(CompoundTag compoundTag) {
    CompoundTag data = new CompoundTag();
    data.putInt(DATA_TIER_TAG, this.tier);
    data.putInt(DATA_MIN_WANTED_TAG, this.minWantedLevel);
    data.putInt(DATA_HEALTH_TAG, this.health);
    data.putFloat(DATA_SPEED_TAG, this.speed);
    data.putFloat(DATA_ATTACK_DAMAGE_TAG, this.attackDamage);
    data.putBoolean(DATA_IS_ARCHER_TAG, this.isArcher);
    data.putBoolean(DATA_IS_CAPTAIN_TAG, this.isCaptain);
    data.putBoolean(DATA_IS_TRACKER_TAG, this.isTracker);
    data.putInt(DATA_SPAWN_RADIUS_TAG, this.spawnRadius);
    data.putInt(DATA_DESPAWN_DISTANCE_TAG, this.despawnDistance);
    data.putInt(DATA_DESPAWN_TIME_TAG, this.despawnTime);
    data.putInt(DATA_SQUAD_SIZE_TAG, this.squadSize);
    data.putString(DATA_TEMPLATE_NAME_TAG, this.templateName);

    compoundTag.put(DATA_GUARD_TIER_TAG, data);
    return compoundTag;
  }

  public CompoundTag createTag() {
    return this.save(new CompoundTag());
  }

  @Override
  public String toString() {
    return "GuardTier{tier=" + tier + ", minWanted=" + minWantedLevel + 
           ", health=" + health + ", squad=" + squadSize + "}";
  }
}
