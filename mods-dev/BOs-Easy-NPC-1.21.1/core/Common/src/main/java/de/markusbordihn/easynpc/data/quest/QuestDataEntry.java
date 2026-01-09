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

package de.markusbordihn.easynpc.data.quest;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public class QuestDataEntry {

  public static final String DATA_ID_TAG = "Id";
  public static final String DATA_TITLE_TAG = "Title";
  public static final String DATA_DESCRIPTION_TAG = "Description";
  public static final String DATA_OBJECTIVE_TYPE_TAG = "ObjectiveType";
  public static final String DATA_OBJECTIVE_TARGET_TAG = "ObjectiveTarget";
  public static final String DATA_OBJECTIVE_AMOUNT_TAG = "ObjectiveAmount";
  public static final String DATA_REWARD_XP_TAG = "RewardXP";
  public static final String DATA_REWARD_ITEM_ID_TAG = "RewardItemID";
  public static final String DATA_REWARD_ITEM_AMOUNT_TAG = "RewardItemAmount";

  private UUID id;
  private String title;
  private String description;
  private String objectiveType;
  private String objectiveTarget;
  private int objectiveAmount;
  private int rewardXP;
  private String rewardItemID;
  private int rewardItemAmount;

  public QuestDataEntry(UUID id, String title, String description) {
    this.id = id;
    this.title = title;
    this.description = description;
  }

  public QuestDataEntry(CompoundTag compoundTag) {
    load(compoundTag);
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public void setObjective(String type, String target, int amount) {
      this.objectiveType = type;
      this.objectiveTarget = target;
      this.objectiveAmount = amount;
  }

  public String getObjectiveType() {
      return objectiveType;
  }

  public String getObjectiveTarget() {
      return objectiveTarget;
  }

  public int getObjectiveAmount() {
      return objectiveAmount;
  }

  public void setRewardItem(String itemId, int amount) {
      this.rewardItemID = itemId;
      this.rewardItemAmount = amount;
  }

  public String getRewardItemID() {
      return rewardItemID;
  }

  public int getRewardItemAmount() {
      return rewardItemAmount;
  }

  public void setRewardXP(int xp) {
      this.rewardXP = xp;
  }

  public int getRewardXP() {
      return rewardXP;
  }

  public void load(CompoundTag compoundTag) {
    if (compoundTag.hasUUID(DATA_ID_TAG)) {
      this.id = compoundTag.getUUID(DATA_ID_TAG);
    }
    this.title = compoundTag.getString(DATA_TITLE_TAG);
    this.description = compoundTag.getString(DATA_DESCRIPTION_TAG);
    this.objectiveType = compoundTag.getString(DATA_OBJECTIVE_TYPE_TAG);
    this.objectiveTarget = compoundTag.getString(DATA_OBJECTIVE_TARGET_TAG);
    this.objectiveAmount = compoundTag.getInt(DATA_OBJECTIVE_AMOUNT_TAG);
    this.rewardXP = compoundTag.getInt(DATA_REWARD_XP_TAG);
    this.rewardItemID = compoundTag.getString(DATA_REWARD_ITEM_ID_TAG);
    this.rewardItemAmount = compoundTag.getInt(DATA_REWARD_ITEM_AMOUNT_TAG);
  }

  public CompoundTag save(CompoundTag compoundTag) {
    if (this.id != null) {
      compoundTag.putUUID(DATA_ID_TAG, this.id);
    }
    compoundTag.putString(DATA_TITLE_TAG, this.title != null ? this.title : "");
    compoundTag.putString(DATA_DESCRIPTION_TAG, this.description != null ? this.description : "");
    compoundTag.putString(DATA_OBJECTIVE_TYPE_TAG, this.objectiveType != null ? this.objectiveType : "");
    compoundTag.putString(DATA_OBJECTIVE_TARGET_TAG, this.objectiveTarget != null ? this.objectiveTarget : "");
    compoundTag.putInt(DATA_OBJECTIVE_AMOUNT_TAG, this.objectiveAmount);
    compoundTag.putInt(DATA_REWARD_XP_TAG, this.rewardXP);
    compoundTag.putString(DATA_REWARD_ITEM_ID_TAG, this.rewardItemID != null ? this.rewardItemID : "");
    compoundTag.putInt(DATA_REWARD_ITEM_AMOUNT_TAG, this.rewardItemAmount);
    return compoundTag;
  }
}
