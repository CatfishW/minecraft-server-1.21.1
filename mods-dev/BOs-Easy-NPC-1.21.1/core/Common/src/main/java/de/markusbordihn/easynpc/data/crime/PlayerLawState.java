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
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Per-player law enforcement state including wanted level, peace value, and crime history.
 */
public class PlayerLawState {

  public static final String DATA_PLAYER_LAW_STATE_TAG = "PlayerLawState";
  public static final String DATA_PLAYER_UUID_TAG = "PlayerUUID";
  public static final String DATA_WANTED_LEVEL_TAG = "WantedLevel";
  public static final String DATA_PEACE_VALUE_TAG = "PeaceValue";
  public static final String DATA_CRIME_HISTORY_TAG = "CrimeHistory";
  public static final String DATA_LAST_CRIME_TIME_TAG = "LastCrimeTime";
  public static final String DATA_WANTED_DECAY_COOLDOWN_TAG = "WantedDecayCooldown";
  public static final String DATA_CRIME_IMMUNITY_TAG = "CrimeImmunity";

  public static final int MAX_CRIME_HISTORY = 50;
  public static final int DEFAULT_PEACE_VALUE = 100;
  public static final int MIN_PEACE_VALUE = 0;
  public static final int MAX_PEACE_VALUE = 100;
  public static final int MIN_WANTED_LEVEL = 0;
  public static final int DEFAULT_MAX_WANTED_LEVEL = 5;

  public static final StreamCodec<RegistryFriendlyByteBuf, PlayerLawState> STREAM_CODEC =
      new StreamCodec<>() {
        @Override
        public PlayerLawState decode(RegistryFriendlyByteBuf buf) {
          return new PlayerLawState(buf.readNbt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, PlayerLawState state) {
          buf.writeNbt(state.createTag());
        }
      };

  private UUID playerUUID;
  private int wantedLevel;
  private int peaceValue;
  private List<CrimeRecord> crimeHistory;
  private long lastCrimeTime;
  private long wantedDecayCooldown;
  private boolean crimeImmunity;

  public PlayerLawState(UUID playerUUID) {
    this.playerUUID = playerUUID;
    this.wantedLevel = MIN_WANTED_LEVEL;
    this.peaceValue = DEFAULT_PEACE_VALUE;
    this.crimeHistory = new ArrayList<>();
    this.lastCrimeTime = 0;
    this.wantedDecayCooldown = 0;
    this.crimeImmunity = false;
  }

  public PlayerLawState(CompoundTag compoundTag) {
    this(UUID.randomUUID());
    this.load(compoundTag);
  }

  // Getters and Setters
  public UUID getPlayerUUID() {
    return this.playerUUID;
  }

  public int getWantedLevel() {
    return this.wantedLevel;
  }

  public void setWantedLevel(int level) {
    this.wantedLevel = Math.max(MIN_WANTED_LEVEL, level);
  }

  public void addWantedLevel(int amount) {
    this.wantedLevel = Math.max(MIN_WANTED_LEVEL, this.wantedLevel + amount);
  }

  public int getPeaceValue() {
    return this.peaceValue;
  }

  public void setPeaceValue(int value) {
    this.peaceValue = Math.max(MIN_PEACE_VALUE, Math.min(MAX_PEACE_VALUE, value));
  }

  public void addPeaceValue(int amount) {
    this.peaceValue = Math.max(MIN_PEACE_VALUE, Math.min(MAX_PEACE_VALUE, this.peaceValue + amount));
  }

  public void subtractPeaceValue(int amount) {
    this.peaceValue = Math.max(MIN_PEACE_VALUE, this.peaceValue - amount);
  }

  public List<CrimeRecord> getCrimeHistory() {
    return this.crimeHistory;
  }

  public long getLastCrimeTime() {
    return this.lastCrimeTime;
  }

  public void setLastCrimeTime(long time) {
    this.lastCrimeTime = time;
  }

  public long getWantedDecayCooldown() {
    return this.wantedDecayCooldown;
  }

  public void setWantedDecayCooldown(long cooldown) {
    this.wantedDecayCooldown = Math.max(0, cooldown);
  }

  public boolean hasCrimeImmunity() {
    return this.crimeImmunity;
  }

  public void setCrimeImmunity(boolean immunity) {
    this.crimeImmunity = immunity;
  }

  /**
   * Record a new crime for this player.
   */
  public void recordCrime(CrimeType type, long currentTime, BlockPos position, String regionId) {
    CrimeRecord record = new CrimeRecord(type, currentTime, position, regionId);
    this.crimeHistory.add(0, record); // Most recent first
    this.lastCrimeTime = currentTime;
    
    // Trim history if needed
    while (this.crimeHistory.size() > MAX_CRIME_HISTORY) {
      this.crimeHistory.remove(this.crimeHistory.size() - 1);
    }
  }

  /**
   * Count recent crimes of a specific type within a time window.
   */
  public int countRecentCrimes(CrimeType type, long currentTime, long windowTicks) {
    int count = 0;
    for (CrimeRecord record : this.crimeHistory) {
      if (record.getCrimeType() == type && record.getAgeInTicks(currentTime) <= windowTicks) {
        count++;
      }
    }
    return count;
  }

  /**
   * Clear all crimes and reset wanted level.
   */
  public void clearCrimes() {
    this.crimeHistory.clear();
    this.wantedLevel = MIN_WANTED_LEVEL;
    this.peaceValue = DEFAULT_PEACE_VALUE;
    this.lastCrimeTime = 0;
    this.wantedDecayCooldown = 0;
  }

  /**
   * Check if player is wanted.
   */
  public boolean isWanted() {
    return this.wantedLevel > 0;
  }

  /**
   * Decay wanted level by 1 (called periodically).
   */
  public void decayWantedLevel() {
    if (this.wantedLevel > 0) {
      this.wantedLevel--;
    }
  }

  /**
   * Regenerate peace value by amount (called periodically).
   */
  public void regeneratePeace(int amount) {
    this.peaceValue = Math.min(MAX_PEACE_VALUE, this.peaceValue + amount);
  }

  public void load(CompoundTag compoundTag) {
    if (compoundTag == null || !compoundTag.contains(DATA_PLAYER_LAW_STATE_TAG)) {
      return;
    }

    CompoundTag data = compoundTag.getCompound(DATA_PLAYER_LAW_STATE_TAG);
    if (data.contains(DATA_PLAYER_UUID_TAG)) {
      this.playerUUID = UUID.fromString(data.getString(DATA_PLAYER_UUID_TAG));
    }
    this.wantedLevel = data.getInt(DATA_WANTED_LEVEL_TAG);
    this.peaceValue = data.getInt(DATA_PEACE_VALUE_TAG);
    this.lastCrimeTime = data.getLong(DATA_LAST_CRIME_TIME_TAG);
    this.wantedDecayCooldown = data.getLong(DATA_WANTED_DECAY_COOLDOWN_TAG);
    this.crimeImmunity = data.getBoolean(DATA_CRIME_IMMUNITY_TAG);

    // Load crime history
    this.crimeHistory = new ArrayList<>();
    if (data.contains(DATA_CRIME_HISTORY_TAG)) {
      ListTag historyList = data.getList(DATA_CRIME_HISTORY_TAG, Tag.TAG_COMPOUND);
      for (int i = 0; i < historyList.size() && i < MAX_CRIME_HISTORY; i++) {
        this.crimeHistory.add(new CrimeRecord(historyList.getCompound(i)));
      }
    }
  }

  public CompoundTag save(CompoundTag compoundTag) {
    CompoundTag data = new CompoundTag();
    data.putString(DATA_PLAYER_UUID_TAG, this.playerUUID.toString());
    data.putInt(DATA_WANTED_LEVEL_TAG, this.wantedLevel);
    data.putInt(DATA_PEACE_VALUE_TAG, this.peaceValue);
    data.putLong(DATA_LAST_CRIME_TIME_TAG, this.lastCrimeTime);
    data.putLong(DATA_WANTED_DECAY_COOLDOWN_TAG, this.wantedDecayCooldown);
    data.putBoolean(DATA_CRIME_IMMUNITY_TAG, this.crimeImmunity);

    // Save crime history
    ListTag historyList = new ListTag();
    for (CrimeRecord record : this.crimeHistory) {
      historyList.add(record.save());
    }
    data.put(DATA_CRIME_HISTORY_TAG, historyList);

    compoundTag.put(DATA_PLAYER_LAW_STATE_TAG, data);
    return compoundTag;
  }

  public CompoundTag createTag() {
    return this.save(new CompoundTag());
  }

  @Override
  public String toString() {
    return "PlayerLawState{uuid=" + playerUUID + ", wanted=" + wantedLevel + 
           ", peace=" + peaceValue + ", crimes=" + crimeHistory.size() + "}";
  }
}
