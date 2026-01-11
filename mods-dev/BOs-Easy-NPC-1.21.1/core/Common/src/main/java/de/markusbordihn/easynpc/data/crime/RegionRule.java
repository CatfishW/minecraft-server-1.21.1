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

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Defines a region where crimes are tracked with specific rules.
 */
public class RegionRule {

  public static final String DATA_REGION_RULE_TAG = "RegionRule";
  public static final String DATA_ID_TAG = "Id";
  public static final String DATA_NAME_TAG = "Name";
  public static final String DATA_ENABLED_TAG = "Enabled";
  public static final String DATA_MODE_TAG = "Mode";
  public static final String DATA_CENTER_X_TAG = "CenterX";
  public static final String DATA_CENTER_Y_TAG = "CenterY";
  public static final String DATA_CENTER_Z_TAG = "CenterZ";
  public static final String DATA_RADIUS_TAG = "Radius";
  public static final String DATA_ENABLED_CRIMES_TAG = "EnabledCrimes";
  public static final String DATA_RESPONSE_RADIUS_TAG = "ResponseRadius";
  public static final String DATA_GUARD_SPAWN_CAP_TAG = "GuardSpawnCap";
  public static final String DATA_COOLDOWN_TICKS_TAG = "CooldownTicks";

  public static final StreamCodec<RegistryFriendlyByteBuf, RegionRule> STREAM_CODEC =
      new StreamCodec<>() {
        @Override
        public RegionRule decode(RegistryFriendlyByteBuf buf) {
          return new RegionRule(buf.readNbt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, RegionRule rule) {
          buf.writeNbt(rule.createTag());
        }
      };

  private UUID id;
  private String name;
  private boolean enabled;
  private RegionMode mode;
  private BlockPos center;
  private int radius;
  private Set<CrimeType> enabledCrimes;
  private int responseRadius;
  private int guardSpawnCap;
  private int cooldownTicks;

  public RegionRule() {
    this.id = UUID.randomUUID();
    this.name = "New Region";
    this.enabled = true;
    this.mode = RegionMode.RADIUS;
    this.center = BlockPos.ZERO;
    this.radius = 100;
    this.enabledCrimes = EnumSet.allOf(CrimeType.class);
    this.responseRadius = 50;
    this.guardSpawnCap = 5;
    this.cooldownTicks = 6000; // 5 minutes
  }

  public RegionRule(CompoundTag compoundTag) {
    this();
    this.load(compoundTag);
  }

  public RegionRule(String name, BlockPos center, int radius) {
    this();
    this.name = name;
    this.center = center;
    this.radius = radius;
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

  public boolean isEnabled() {
    return this.enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public RegionMode getMode() {
    return this.mode;
  }

  public void setMode(RegionMode mode) {
    this.mode = mode;
  }

  public BlockPos getCenter() {
    return this.center;
  }

  public void setCenter(BlockPos center) {
    this.center = center;
  }

  public int getRadius() {
    return this.radius;
  }

  public void setRadius(int radius) {
    this.radius = Math.max(1, radius);
  }

  public Set<CrimeType> getEnabledCrimes() {
    return this.enabledCrimes;
  }

  public void setEnabledCrimes(Set<CrimeType> enabledCrimes) {
    this.enabledCrimes = enabledCrimes;
  }

  public boolean isCrimeEnabled(CrimeType crimeType) {
    return this.enabledCrimes.contains(crimeType);
  }

  public int getResponseRadius() {
    return this.responseRadius;
  }

  public void setResponseRadius(int responseRadius) {
    this.responseRadius = Math.max(1, responseRadius);
  }

  public int getGuardSpawnCap() {
    return this.guardSpawnCap;
  }

  public void setGuardSpawnCap(int guardSpawnCap) {
    this.guardSpawnCap = Math.max(0, guardSpawnCap);
  }

  public int getCooldownTicks() {
    return this.cooldownTicks;
  }

  public void setCooldownTicks(int cooldownTicks) {
    this.cooldownTicks = Math.max(0, cooldownTicks);
  }

  /**
   * Check if a position is within this region.
   */
  public boolean containsPosition(BlockPos pos) {
    if (!this.enabled) {
      return false;
    }
    if (this.mode == RegionMode.WORLD) {
      return true;
    }
    if (this.mode == RegionMode.RADIUS) {
      double distanceSq = this.center.distSqr(pos);
      return distanceSq <= (double) this.radius * this.radius;
    }
    // NAMED mode would require external hook
    return false;
  }

  public void load(CompoundTag compoundTag) {
    if (compoundTag == null || !compoundTag.contains(DATA_REGION_RULE_TAG)) {
      return;
    }

    CompoundTag data = compoundTag.getCompound(DATA_REGION_RULE_TAG);
    if (data.contains(DATA_ID_TAG)) {
      this.id = UUID.fromString(data.getString(DATA_ID_TAG));
    }
    this.name = data.getString(DATA_NAME_TAG);
    this.enabled = data.getBoolean(DATA_ENABLED_TAG);
    this.mode = RegionMode.get(data.getString(DATA_MODE_TAG));
    this.center = new BlockPos(
        data.getInt(DATA_CENTER_X_TAG),
        data.getInt(DATA_CENTER_Y_TAG),
        data.getInt(DATA_CENTER_Z_TAG));
    this.radius = data.getInt(DATA_RADIUS_TAG);
    this.responseRadius = data.getInt(DATA_RESPONSE_RADIUS_TAG);
    this.guardSpawnCap = data.getInt(DATA_GUARD_SPAWN_CAP_TAG);
    this.cooldownTicks = data.getInt(DATA_COOLDOWN_TICKS_TAG);

    // Load enabled crimes
    this.enabledCrimes = EnumSet.noneOf(CrimeType.class);
    if (data.contains(DATA_ENABLED_CRIMES_TAG)) {
      ListTag crimesList = data.getList(DATA_ENABLED_CRIMES_TAG, Tag.TAG_STRING);
      for (int i = 0; i < crimesList.size(); i++) {
        CrimeType type = CrimeType.get(crimesList.getString(i));
        if (type != null) {
          this.enabledCrimes.add(type);
        }
      }
    }
  }

  public CompoundTag save(CompoundTag compoundTag) {
    CompoundTag data = new CompoundTag();
    data.putString(DATA_ID_TAG, this.id.toString());
    data.putString(DATA_NAME_TAG, this.name);
    data.putBoolean(DATA_ENABLED_TAG, this.enabled);
    data.putString(DATA_MODE_TAG, this.mode.name());
    data.putInt(DATA_CENTER_X_TAG, this.center.getX());
    data.putInt(DATA_CENTER_Y_TAG, this.center.getY());
    data.putInt(DATA_CENTER_Z_TAG, this.center.getZ());
    data.putInt(DATA_RADIUS_TAG, this.radius);
    data.putInt(DATA_RESPONSE_RADIUS_TAG, this.responseRadius);
    data.putInt(DATA_GUARD_SPAWN_CAP_TAG, this.guardSpawnCap);
    data.putInt(DATA_COOLDOWN_TICKS_TAG, this.cooldownTicks);

    // Save enabled crimes
    ListTag crimesList = new ListTag();
    for (CrimeType type : this.enabledCrimes) {
      crimesList.add(StringTag.valueOf(type.name()));
    }
    data.put(DATA_ENABLED_CRIMES_TAG, crimesList);

    compoundTag.put(DATA_REGION_RULE_TAG, data);
    return compoundTag;
  }

  public CompoundTag createTag() {
    return this.save(new CompoundTag());
  }

  @Override
  public String toString() {
    return "RegionRule{id=" + id + ", name='" + name + "', enabled=" + enabled + 
           ", mode=" + mode + ", center=" + center + ", radius=" + radius + "}";
  }
}
