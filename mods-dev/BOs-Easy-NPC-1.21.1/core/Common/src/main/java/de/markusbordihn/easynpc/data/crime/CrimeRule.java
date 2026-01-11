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

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Defines penalty mappings and multipliers for crimes.
 */
public class CrimeRule {

  public static final String DATA_CRIME_RULE_TAG = "CrimeRule";
  public static final String DATA_WANTED_PENALTIES_TAG = "WantedPenalties";
  public static final String DATA_PEACE_PENALTIES_TAG = "PeacePenalties";
  public static final String DATA_REPEAT_MULTIPLIER_TAG = "RepeatMultiplier";
  public static final String DATA_REPEAT_WINDOW_TAG = "RepeatWindowTicks";

  public static final StreamCodec<RegistryFriendlyByteBuf, CrimeRule> STREAM_CODEC =
      new StreamCodec<>() {
        @Override
        public CrimeRule decode(RegistryFriendlyByteBuf buf) {
          return new CrimeRule(buf.readNbt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, CrimeRule rule) {
          buf.writeNbt(rule.createTag());
        }
      };

  private Map<CrimeType, Integer> wantedPenalties;
  private Map<CrimeType, Integer> peacePenalties;
  private float repeatOffenseMultiplier;
  private int repeatWindowTicks;

  public CrimeRule() {
    this.wantedPenalties = new EnumMap<>(CrimeType.class);
    this.peacePenalties = new EnumMap<>(CrimeType.class);
    
    // Initialize with defaults from CrimeType
    for (CrimeType type : CrimeType.values()) {
      this.wantedPenalties.put(type, type.getDefaultWantedPenalty());
      this.peacePenalties.put(type, type.getDefaultPeacePenalty());
    }
    
    this.repeatOffenseMultiplier = 1.5f;
    this.repeatWindowTicks = 12000; // 10 minutes
  }

  public CrimeRule(CompoundTag compoundTag) {
    this();
    this.load(compoundTag);
  }

  // Getters and Setters
  public int getWantedPenalty(CrimeType type) {
    return this.wantedPenalties.getOrDefault(type, type.getDefaultWantedPenalty());
  }

  public void setWantedPenalty(CrimeType type, int penalty) {
    this.wantedPenalties.put(type, Math.max(0, penalty));
  }

  public int getPeacePenalty(CrimeType type) {
    return this.peacePenalties.getOrDefault(type, type.getDefaultPeacePenalty());
  }

  public void setPeacePenalty(CrimeType type, int penalty) {
    this.peacePenalties.put(type, Math.max(0, penalty));
  }

  public Map<CrimeType, Integer> getWantedPenalties() {
    return this.wantedPenalties;
  }

  public Map<CrimeType, Integer> getPeacePenalties() {
    return this.peacePenalties;
  }

  public float getRepeatOffenseMultiplier() {
    return this.repeatOffenseMultiplier;
  }

  public void setRepeatOffenseMultiplier(float multiplier) {
    this.repeatOffenseMultiplier = Math.max(1.0f, multiplier);
  }

  public int getRepeatWindowTicks() {
    return this.repeatWindowTicks;
  }

  public void setRepeatWindowTicks(int ticks) {
    this.repeatWindowTicks = Math.max(0, ticks);
  }

  /**
   * Calculate the actual wanted penalty considering repeat offenses.
   */
  public int calculateWantedPenalty(CrimeType type, int repeatCount) {
    int basePenalty = getWantedPenalty(type);
    if (repeatCount > 0) {
      return (int) (basePenalty * Math.pow(repeatOffenseMultiplier, repeatCount));
    }
    return basePenalty;
  }

  /**
   * Calculate the actual peace penalty considering repeat offenses.
   */
  public int calculatePeacePenalty(CrimeType type, int repeatCount) {
    int basePenalty = getPeacePenalty(type);
    if (repeatCount > 0) {
      return (int) (basePenalty * Math.pow(repeatOffenseMultiplier, repeatCount));
    }
    return basePenalty;
  }

  public void load(CompoundTag compoundTag) {
    if (compoundTag == null || !compoundTag.contains(DATA_CRIME_RULE_TAG)) {
      return;
    }

    CompoundTag data = compoundTag.getCompound(DATA_CRIME_RULE_TAG);
    this.repeatOffenseMultiplier = data.getFloat(DATA_REPEAT_MULTIPLIER_TAG);
    this.repeatWindowTicks = data.getInt(DATA_REPEAT_WINDOW_TAG);

    // Load wanted penalties
    if (data.contains(DATA_WANTED_PENALTIES_TAG)) {
      CompoundTag wantedTag = data.getCompound(DATA_WANTED_PENALTIES_TAG);
      for (CrimeType type : CrimeType.values()) {
        if (wantedTag.contains(type.name())) {
          this.wantedPenalties.put(type, wantedTag.getInt(type.name()));
        }
      }
    }

    // Load peace penalties
    if (data.contains(DATA_PEACE_PENALTIES_TAG)) {
      CompoundTag peaceTag = data.getCompound(DATA_PEACE_PENALTIES_TAG);
      for (CrimeType type : CrimeType.values()) {
        if (peaceTag.contains(type.name())) {
          this.peacePenalties.put(type, peaceTag.getInt(type.name()));
        }
      }
    }
  }

  public CompoundTag save(CompoundTag compoundTag) {
    CompoundTag data = new CompoundTag();
    data.putFloat(DATA_REPEAT_MULTIPLIER_TAG, this.repeatOffenseMultiplier);
    data.putInt(DATA_REPEAT_WINDOW_TAG, this.repeatWindowTicks);

    // Save wanted penalties
    CompoundTag wantedTag = new CompoundTag();
    for (Map.Entry<CrimeType, Integer> entry : this.wantedPenalties.entrySet()) {
      wantedTag.putInt(entry.getKey().name(), entry.getValue());
    }
    data.put(DATA_WANTED_PENALTIES_TAG, wantedTag);

    // Save peace penalties
    CompoundTag peaceTag = new CompoundTag();
    for (Map.Entry<CrimeType, Integer> entry : this.peacePenalties.entrySet()) {
      peaceTag.putInt(entry.getKey().name(), entry.getValue());
    }
    data.put(DATA_PEACE_PENALTIES_TAG, peaceTag);

    compoundTag.put(DATA_CRIME_RULE_TAG, data);
    return compoundTag;
  }

  public CompoundTag createTag() {
    return this.save(new CompoundTag());
  }
}
