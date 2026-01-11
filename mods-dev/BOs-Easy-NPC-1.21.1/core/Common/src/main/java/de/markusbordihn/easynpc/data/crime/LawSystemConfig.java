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

/**
 * Global configuration for the law enforcement system.
 */
public class LawSystemConfig {

  public static final String DATA_LAW_CONFIG_TAG = "LawSystemConfig";
  public static final String DATA_SYSTEM_ENABLED_TAG = "SystemEnabled";
  public static final String DATA_MAX_WANTED_TAG = "MaxWantedLevel";
  public static final String DATA_PEACE_MIN_TAG = "PeaceValueMin";
  public static final String DATA_PEACE_MAX_TAG = "PeaceValueMax";
  public static final String DATA_PEACE_REGEN_RATE_TAG = "PeaceRegenRate";
  public static final String DATA_WANTED_DECAY_RATE_TAG = "WantedDecayRate";
  public static final String DATA_WANTED_DECAY_DELAY_TAG = "WantedDecayDelayTicks";
  public static final String DATA_RESET_ON_DEATH_TAG = "ResetOnDeath";
  public static final String DATA_RESET_ON_JAIL_TAG = "ResetOnJail";
  public static final String DATA_RESET_ON_BRIBE_TAG = "ResetOnBribe";
  public static final String DATA_REGIONS_TAG = "Regions";
  public static final String DATA_CRIME_RULE_TAG = "CrimeRule";
  public static final String DATA_GUARD_TIERS_TAG = "GuardTiers";
  public static final String DATA_MERCHANT_TEMPLATES_TAG = "MerchantTemplates";
  public static final String DATA_PROFILE_NAME_TAG = "ProfileName";

  public static final StreamCodec<RegistryFriendlyByteBuf, LawSystemConfig> STREAM_CODEC =
      new StreamCodec<>() {
        @Override
        public LawSystemConfig decode(RegistryFriendlyByteBuf buf) {
          return new LawSystemConfig(buf.readNbt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, LawSystemConfig config) {
          buf.writeNbt(config.createTag());
        }
      };

  private boolean systemEnabled;
  private int maxWantedLevel;
  private int peaceValueMin;
  private int peaceValueMax;
  private int peaceRegenRate; // Ticks between regen
  private int wantedDecayRate; // Ticks between decay
  private int wantedDecayDelayTicks; // Delay after last crime before decay starts
  private boolean resetOnDeath;
  private boolean resetOnJail;
  private boolean resetOnBribe;
  private List<RegionRule> regions;
  private CrimeRule crimeRule;
  private List<GuardTier> guardTiers;
  private List<MerchantTemplate> merchantTemplates;
  private String profileName;

  public LawSystemConfig() {
    this.systemEnabled = true;
    this.maxWantedLevel = 5;
    this.peaceValueMin = 0;
    this.peaceValueMax = 100;
    this.peaceRegenRate = 1200; // 1 minute
    this.wantedDecayRate = 6000; // 5 minutes
    this.wantedDecayDelayTicks = 12000; // 10 minutes after last crime
    this.resetOnDeath = true;
    this.resetOnJail = true;
    this.resetOnBribe = true;
    this.regions = new ArrayList<>();
    this.crimeRule = new CrimeRule();
    this.guardTiers = new ArrayList<>();
    this.merchantTemplates = new ArrayList<>();
    this.profileName = "Default";
    
    initializeDefaultGuardTiers();
  }

  public LawSystemConfig(CompoundTag compoundTag) {
    this();
    this.load(compoundTag);
  }

  private void initializeDefaultGuardTiers() {
    // Default 5-tier guard system
    for (int i = 1; i <= 5; i++) {
      this.guardTiers.add(new GuardTier(i, i));
    }
  }

  // Getters and Setters
  public boolean isSystemEnabled() {
    return this.systemEnabled;
  }

  public void setSystemEnabled(boolean enabled) {
    this.systemEnabled = enabled;
  }

  public int getMaxWantedLevel() {
    return this.maxWantedLevel;
  }

  public void setMaxWantedLevel(int level) {
    this.maxWantedLevel = Math.max(1, level);
  }

  public int getPeaceValueMin() {
    return this.peaceValueMin;
  }

  public void setPeaceValueMin(int min) {
    this.peaceValueMin = Math.max(0, min);
  }

  public int getPeaceValueMax() {
    return this.peaceValueMax;
  }

  public void setPeaceValueMax(int max) {
    this.peaceValueMax = Math.max(this.peaceValueMin, max);
  }

  public int getPeaceRegenRate() {
    return this.peaceRegenRate;
  }

  public void setPeaceRegenRate(int rate) {
    this.peaceRegenRate = Math.max(1, rate);
  }

  public int getWantedDecayRate() {
    return this.wantedDecayRate;
  }

  public void setWantedDecayRate(int rate) {
    this.wantedDecayRate = Math.max(1, rate);
  }

  public int getWantedDecayDelayTicks() {
    return this.wantedDecayDelayTicks;
  }

  public void setWantedDecayDelayTicks(int delay) {
    this.wantedDecayDelayTicks = Math.max(0, delay);
  }

  public boolean isResetOnDeath() {
    return this.resetOnDeath;
  }

  public void setResetOnDeath(boolean reset) {
    this.resetOnDeath = reset;
  }

  public boolean isResetOnJail() {
    return this.resetOnJail;
  }

  public void setResetOnJail(boolean reset) {
    this.resetOnJail = reset;
  }

  public boolean isResetOnBribe() {
    return this.resetOnBribe;
  }

  public void setResetOnBribe(boolean reset) {
    this.resetOnBribe = reset;
  }

  public List<RegionRule> getRegions() {
    return this.regions;
  }

  public void addRegion(RegionRule region) {
    this.regions.add(region);
  }

  public void removeRegion(RegionRule region) {
    this.regions.remove(region);
  }

  public CrimeRule getCrimeRule() {
    return this.crimeRule;
  }

  public void setCrimeRule(CrimeRule crimeRule) {
    this.crimeRule = crimeRule;
  }

  public List<GuardTier> getGuardTiers() {
    return this.guardTiers;
  }

  public GuardTier getGuardTierForWantedLevel(int wantedLevel) {
    GuardTier result = null;
    for (GuardTier tier : this.guardTiers) {
      if (tier.getMinWantedLevel() <= wantedLevel) {
        if (result == null || tier.getTier() > result.getTier()) {
          result = tier;
        }
      }
    }
    return result;
  }

  public List<MerchantTemplate> getMerchantTemplates() {
    return this.merchantTemplates;
  }

  public void addMerchantTemplate(MerchantTemplate template) {
    this.merchantTemplates.add(template);
  }

  public String getProfileName() {
    return this.profileName;
  }

  public void setProfileName(String profileName) {
    this.profileName = profileName;
  }

  /**
   * Apply a preset profile.
   */
  public void applyPreset(String presetName) {
    switch (presetName.toLowerCase()) {
      case "hardcore":
        this.maxWantedLevel = 10;
        this.peaceRegenRate = 3600; // 3 minutes
        this.wantedDecayRate = 18000; // 15 minutes
        this.resetOnDeath = false;
        this.crimeRule.setRepeatOffenseMultiplier(2.0f);
        break;
      case "casual":
        this.maxWantedLevel = 3;
        this.peaceRegenRate = 600; // 30 seconds
        this.wantedDecayRate = 2400; // 2 minutes
        this.resetOnDeath = true;
        this.crimeRule.setRepeatOffenseMultiplier(1.2f);
        break;
      case "rp":
        this.maxWantedLevel = 5;
        this.peaceRegenRate = 1200; // 1 minute
        this.wantedDecayRate = 6000; // 5 minutes
        this.resetOnDeath = true;
        this.resetOnJail = true;
        break;
      default:
        // Default preset
        break;
    }
    this.profileName = presetName;
  }

  public void load(CompoundTag compoundTag) {
    if (compoundTag == null || !compoundTag.contains(DATA_LAW_CONFIG_TAG)) {
      return;
    }

    CompoundTag data = compoundTag.getCompound(DATA_LAW_CONFIG_TAG);
    this.systemEnabled = data.getBoolean(DATA_SYSTEM_ENABLED_TAG);
    this.maxWantedLevel = data.getInt(DATA_MAX_WANTED_TAG);
    this.peaceValueMin = data.getInt(DATA_PEACE_MIN_TAG);
    this.peaceValueMax = data.getInt(DATA_PEACE_MAX_TAG);
    this.peaceRegenRate = data.getInt(DATA_PEACE_REGEN_RATE_TAG);
    this.wantedDecayRate = data.getInt(DATA_WANTED_DECAY_RATE_TAG);
    this.wantedDecayDelayTicks = data.getInt(DATA_WANTED_DECAY_DELAY_TAG);
    this.resetOnDeath = data.getBoolean(DATA_RESET_ON_DEATH_TAG);
    this.resetOnJail = data.getBoolean(DATA_RESET_ON_JAIL_TAG);
    this.resetOnBribe = data.getBoolean(DATA_RESET_ON_BRIBE_TAG);
    this.profileName = data.getString(DATA_PROFILE_NAME_TAG);

    // Load crime rule
    if (data.contains(DATA_CRIME_RULE_TAG)) {
      this.crimeRule = new CrimeRule(data.getCompound(DATA_CRIME_RULE_TAG));
    }

    // Load regions
    this.regions = new ArrayList<>();
    if (data.contains(DATA_REGIONS_TAG)) {
      ListTag regionsList = data.getList(DATA_REGIONS_TAG, Tag.TAG_COMPOUND);
      for (int i = 0; i < regionsList.size(); i++) {
        this.regions.add(new RegionRule(regionsList.getCompound(i)));
      }
    }

    // Load guard tiers
    this.guardTiers = new ArrayList<>();
    if (data.contains(DATA_GUARD_TIERS_TAG)) {
      ListTag tiersList = data.getList(DATA_GUARD_TIERS_TAG, Tag.TAG_COMPOUND);
      for (int i = 0; i < tiersList.size(); i++) {
        this.guardTiers.add(new GuardTier(tiersList.getCompound(i)));
      }
    }
    if (this.guardTiers.isEmpty()) {
      initializeDefaultGuardTiers();
    }

    // Load merchant templates
    this.merchantTemplates = new ArrayList<>();
    if (data.contains(DATA_MERCHANT_TEMPLATES_TAG)) {
      ListTag templatesList = data.getList(DATA_MERCHANT_TEMPLATES_TAG, Tag.TAG_COMPOUND);
      for (int i = 0; i < templatesList.size(); i++) {
        this.merchantTemplates.add(new MerchantTemplate(templatesList.getCompound(i)));
      }
    }
  }

  public CompoundTag save(CompoundTag compoundTag) {
    CompoundTag data = new CompoundTag();
    data.putBoolean(DATA_SYSTEM_ENABLED_TAG, this.systemEnabled);
    data.putInt(DATA_MAX_WANTED_TAG, this.maxWantedLevel);
    data.putInt(DATA_PEACE_MIN_TAG, this.peaceValueMin);
    data.putInt(DATA_PEACE_MAX_TAG, this.peaceValueMax);
    data.putInt(DATA_PEACE_REGEN_RATE_TAG, this.peaceRegenRate);
    data.putInt(DATA_WANTED_DECAY_RATE_TAG, this.wantedDecayRate);
    data.putInt(DATA_WANTED_DECAY_DELAY_TAG, this.wantedDecayDelayTicks);
    data.putBoolean(DATA_RESET_ON_DEATH_TAG, this.resetOnDeath);
    data.putBoolean(DATA_RESET_ON_JAIL_TAG, this.resetOnJail);
    data.putBoolean(DATA_RESET_ON_BRIBE_TAG, this.resetOnBribe);
    data.putString(DATA_PROFILE_NAME_TAG, this.profileName);

    // Save crime rule
    data.put(DATA_CRIME_RULE_TAG, this.crimeRule.createTag());

    // Save regions
    ListTag regionsList = new ListTag();
    for (RegionRule region : this.regions) {
      regionsList.add(region.createTag());
    }
    data.put(DATA_REGIONS_TAG, regionsList);

    // Save guard tiers
    ListTag tiersList = new ListTag();
    for (GuardTier tier : this.guardTiers) {
      tiersList.add(tier.createTag());
    }
    data.put(DATA_GUARD_TIERS_TAG, tiersList);

    // Save merchant templates
    ListTag templatesList = new ListTag();
    for (MerchantTemplate template : this.merchantTemplates) {
      templatesList.add(template.createTag());
    }
    data.put(DATA_MERCHANT_TEMPLATES_TAG, templatesList);

    compoundTag.put(DATA_LAW_CONFIG_TAG, data);
    return compoundTag;
  }

  public CompoundTag createTag() {
    return this.save(new CompoundTag());
  }
}
