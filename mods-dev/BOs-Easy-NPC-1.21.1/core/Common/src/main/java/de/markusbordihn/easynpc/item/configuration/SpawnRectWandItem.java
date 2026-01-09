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

package de.markusbordihn.easynpc.item.configuration;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.network.components.TextComponent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A wand item for selecting and managing spawn rect areas.
 * - Right-click block: Set first corner (pos1)
 * - Shift + Right-click block: Set second corner (pos2)
 * - Right-click air: Open Spawn Rect Manager UI
 * - When both corners set, can create spawn rect task
 */
public class SpawnRectWandItem extends Item {

  public static final String ID = "spawn_rect_wand";

  public SpawnRectWandItem(Item.Properties properties) {
    super(properties.stacksTo(1));
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    ItemStack itemStack = player.getItemInHand(hand);
    
    // Right-click in air opens the manager UI
    if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
      // TODO: Open Spawn Rect Manager Screen via network packet
      serverPlayer.sendSystemMessage(Component.literal("§a[Spawn Rect Wand] §7Opening manager... (UI coming soon)"));
      
      // Display current selection
      CompoundTag tag = getOrCreateTag(itemStack);
      if (tag.contains("pos1x") && tag.contains("pos2x")) {
        int x1 = tag.getInt("pos1x"), y1 = tag.getInt("pos1y"), z1 = tag.getInt("pos1z");
        int x2 = tag.getInt("pos2x"), y2 = tag.getInt("pos2y"), z2 = tag.getInt("pos2z");
        serverPlayer.sendSystemMessage(Component.literal(
            String.format("§7Current selection: §e(%d, %d, %d) §7to §e(%d, %d, %d)", x1, y1, z1, x2, y2, z2)));
      } else {
        serverPlayer.sendSystemMessage(Component.literal("§7No selection. Right-click blocks to set corners."));
      }
      
      return InteractionResultHolder.success(itemStack);
    }
    
    return InteractionResultHolder.pass(itemStack);
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    Level level = context.getLevel();
    Player player = context.getPlayer();
    ItemStack itemStack = context.getItemInHand();
    BlockPos clickedPos = context.getClickedPos();
    
    if (level.isClientSide || player == null) {
      return InteractionResult.PASS;
    }
    
    // Read existing tag first
    CompoundTag existingTag = getOrCreateTag(itemStack);
    CompoundTag newTag = new CompoundTag();
    
    // Copy ALL existing data first
    for (String key : existingTag.getAllKeys()) {
      newTag.put(key, existingTag.get(key).copy());
    }
    
    if (player.isShiftKeyDown()) {
      // Shift + Right-click: Set pos2
      newTag.putInt("pos2x", clickedPos.getX());
      newTag.putInt("pos2y", clickedPos.getY());
      newTag.putInt("pos2z", clickedPos.getZ());
      setTag(itemStack, newTag);
      
      player.sendSystemMessage(Component.literal(
          String.format("§a[Spawn Rect Wand] §7Set corner 2: §e(%d, %d, %d)", 
              clickedPos.getX(), clickedPos.getY(), clickedPos.getZ())));
      
      // Check if both corners are set
      if (newTag.contains("pos1x")) {
        player.sendSystemMessage(Component.literal("§a[Spawn Rect Wand] §7Selection complete! Right-click air to manage."));
      }
    } else {
      // Regular Right-click: Set pos1
      newTag.putInt("pos1x", clickedPos.getX());
      newTag.putInt("pos1y", clickedPos.getY());
      newTag.putInt("pos1z", clickedPos.getZ());
      setTag(itemStack, newTag);
      
      player.sendSystemMessage(Component.literal(
          String.format("§a[Spawn Rect Wand] §7Set corner 1: §e(%d, %d, %d)", 
              clickedPos.getX(), clickedPos.getY(), clickedPos.getZ())));
    }
    
    return InteractionResult.SUCCESS;
  }

  /**
   * Get the stored pos1 from the item stack.
   */
  public static BlockPos getPos1(ItemStack itemStack) {
    CompoundTag tag = getOrCreateTag(itemStack);
    if (tag.contains("pos1x")) {
      return new BlockPos(tag.getInt("pos1x"), tag.getInt("pos1y"), tag.getInt("pos1z"));
    }
    return null;
  }

  /**
   * Get the stored pos2 from the item stack.
   */
  public static BlockPos getPos2(ItemStack itemStack) {
    CompoundTag tag = getOrCreateTag(itemStack);
    if (tag.contains("pos2x")) {
      return new BlockPos(tag.getInt("pos2x"), tag.getInt("pos2y"), tag.getInt("pos2z"));
    }
    return null;
  }

  /**
   * Check if both corners are set.
   */
  public static boolean hasSelection(ItemStack itemStack) {
    return getPos1(itemStack) != null && getPos2(itemStack) != null;
  }

  /**
   * Clear the selection.
   */
  public static void clearSelection(ItemStack itemStack) {
    CompoundTag tag = new CompoundTag();
    setTag(itemStack, tag);
  }

  private static CompoundTag getOrCreateTag(ItemStack itemStack) {
    CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
    if (customData != null) {
      return customData.copyTag();
    }
    return new CompoundTag();
  }

  private static void setTag(ItemStack itemStack, CompoundTag tag) {
    itemStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
  }

  @Override
  public void appendHoverText(
      ItemStack itemStack,
      TooltipContext tooltipContext,
      List<Component> tooltipList,
      TooltipFlag tooltipFlag) {
    tooltipList.add(TextComponent.getTranslatedTextRaw(Constants.TEXT_ITEM_PREFIX + ID));
    tooltipList.add(Component.literal("§7Right-click block: Set corner 1"));
    tooltipList.add(Component.literal("§7Shift+Right-click: Set corner 2"));
    tooltipList.add(Component.literal("§7Right-click air: Open manager"));
    
    // Show current selection
    BlockPos pos1 = getPos1(itemStack);
    BlockPos pos2 = getPos2(itemStack);
    if (pos1 != null && pos2 != null) {
      tooltipList.add(Component.literal(String.format("§aSelection: §e(%d,%d,%d) - (%d,%d,%d)", 
          pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ())));
    }
  }
}
