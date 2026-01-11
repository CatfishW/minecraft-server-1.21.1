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

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Record of a single crime event.
 */
public class CrimeRecord {

  public static final String DATA_CRIME_TYPE_TAG = "CrimeType";
  public static final String DATA_TIMESTAMP_TAG = "Timestamp";
  public static final String DATA_POSITION_X_TAG = "PosX";
  public static final String DATA_POSITION_Y_TAG = "PosY";
  public static final String DATA_POSITION_Z_TAG = "PosZ";
  public static final String DATA_REGION_ID_TAG = "RegionId";

  private final CrimeType crimeType;
  private final long timestamp;
  private final BlockPos position;
  private final String regionId;

  public CrimeRecord(CrimeType crimeType, long timestamp, BlockPos position, String regionId) {
    this.crimeType = crimeType;
    this.timestamp = timestamp;
    this.position = position;
    this.regionId = regionId;
  }

  public CrimeRecord(CompoundTag tag) {
    this.crimeType = CrimeType.get(tag.getString(DATA_CRIME_TYPE_TAG));
    this.timestamp = tag.getLong(DATA_TIMESTAMP_TAG);
    this.position = new BlockPos(
        tag.getInt(DATA_POSITION_X_TAG),
        tag.getInt(DATA_POSITION_Y_TAG),
        tag.getInt(DATA_POSITION_Z_TAG));
    this.regionId = tag.getString(DATA_REGION_ID_TAG);
  }

  public CrimeType getCrimeType() {
    return this.crimeType;
  }

  public long getTimestamp() {
    return this.timestamp;
  }

  public BlockPos getPosition() {
    return this.position;
  }

  public String getRegionId() {
    return this.regionId;
  }

  /**
   * Get age of this crime in ticks.
   */
  public long getAgeInTicks(long currentTime) {
    return currentTime - this.timestamp;
  }

  public CompoundTag save() {
    CompoundTag tag = new CompoundTag();
    tag.putString(DATA_CRIME_TYPE_TAG, this.crimeType.name());
    tag.putLong(DATA_TIMESTAMP_TAG, this.timestamp);
    tag.putInt(DATA_POSITION_X_TAG, this.position.getX());
    tag.putInt(DATA_POSITION_Y_TAG, this.position.getY());
    tag.putInt(DATA_POSITION_Z_TAG, this.position.getZ());
    tag.putString(DATA_REGION_ID_TAG, this.regionId);
    return tag;
  }

  @Override
  public String toString() {
    return "CrimeRecord{type=" + crimeType + ", time=" + timestamp + ", pos=" + position + "}";
  }
}
