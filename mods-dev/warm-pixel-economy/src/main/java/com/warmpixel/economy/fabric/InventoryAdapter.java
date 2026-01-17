package com.warmpixel.economy.fabric;

import com.warmpixel.economy.core.FuzzyFlags;
import com.warmpixel.economy.core.ItemKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 背包适配器 - 处理玩家背包中的物品操作
 * 所有操作都在主线程上执行以确保线程安全
 */
public class InventoryAdapter {
    
    private final MinecraftServer server;

    public InventoryAdapter(MinecraftServer server) {
        this.server = server;
    }

    /**
     * 异步移除匹配的物品
     */
    public CompletableFuture<Boolean> removeMatching(ServerPlayer player, ItemKey key, int count) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                boolean result = removeMatchingSync(player, key, count);
                future.complete(result);
            } catch (Exception e) {
                System.err.println("[Economy] Error removing items: " + e.getMessage());
                e.printStackTrace();
                future.complete(false);
            }
        });
        return future;
    }

    /**
     * 异步计算匹配物品的数量
     */
    public CompletableFuture<Integer> countMatching(ServerPlayer player, ItemKey key) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                int result = countMatchingSync(player, key);
                future.complete(result);
            } catch (Exception e) {
                System.err.println("[Economy] Error counting items: " + e.getMessage());
                e.printStackTrace();
                future.complete(0);
            }
        });
        return future;
    }

    /**
     * 异步插入物品栈
     */
    public CompletableFuture<Boolean> insertStack(ServerPlayer player, ItemStack stack) {
        return insertItems(player, stack, stack.getCount());
    }

    /**
     * 异步插入指定数量的物品
     */
    public CompletableFuture<Boolean> insertItems(ServerPlayer player, ItemStack template, int count) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                boolean result = insertItemsSync(player, template, count);
                future.complete(result);
            } catch (Exception e) {
                System.err.println("[Economy] Error inserting items: " + e.getMessage());
                e.printStackTrace();
                future.complete(false);
            }
        });
        return future;
    }

    /**
     * 异步检查是否能插入物品栈
     */
    public CompletableFuture<Boolean> canInsertStack(ServerPlayer player, ItemStack stack) {
        return canInsertItems(player, stack, stack.getCount());
    }

    /**
     * 异步检查是否能插入指定数量的物品
     */
    public CompletableFuture<Boolean> canInsertItems(ServerPlayer player, ItemStack template, int count) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                boolean result = canInsertItemsSync(player, template, count);
                future.complete(result);
            } catch (Exception e) {
                System.err.println("[Economy] Error checking insert: " + e.getMessage());
                e.printStackTrace();
                future.complete(false);
            }
        });
        return future;
    }

    /**
     * 同步计算匹配物品的数量
     */
    private int countMatchingSync(ServerPlayer player, ItemKey key) {
        if (player == null || key == null) {
            return 0;
        }
        
        Inventory inventory = player.getInventory();
        int totalCount = 0;
        
        // 获取匹配信息
        MatchInfo matchInfo = createMatchInfo(key);
        if (matchInfo == null) {
            System.err.println("[Economy] Failed to create match info for: " + key.registryId());
            return 0;
        }
        
        // 遍历背包的主要区域（0-35: 主背包, 36-39: 盔甲, 40: 副手）
        // 只检查主背包区域
        for (int i = 0; i < 36; i++) {
            ItemStack slotStack = inventory.getItem(i);
            if (slotStack.isEmpty()) {
                continue;
            }
            
            if (matchesItem(slotStack, matchInfo)) {
                totalCount += slotStack.getCount();
            }
        }
        
        return totalCount;
    }

    /**
     * 同步移除匹配的物品
     * 返回是否成功移除了指定数量的物品
     */
    private boolean removeMatchingSync(ServerPlayer player, ItemKey key, int count) {
        if (player == null || key == null) {
            return false;
        }
        
        if (count <= 0) {
            return true; // 不需要移除
        }
        
        Inventory inventory = player.getInventory();
        
        // 第一步：计算可用数量（必须先验证）
        int available = countMatchingSync(player, key);
        if (available < count) {
            System.out.println("[Economy] Sell validation failed: available=" + available + ", requested=" + count);
            return false;
        }
        
        // 获取匹配信息
        MatchInfo matchInfo = createMatchInfo(key);
        if (matchInfo == null) {
            return false;
        }
        
        // 第二步：收集所有匹配的槽位及其数量
        List<SlotInfo> matchingSlots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack slotStack = inventory.getItem(i);
            if (slotStack.isEmpty()) {
                continue;
            }
            
            if (matchesItem(slotStack, matchInfo)) {
                matchingSlots.add(new SlotInfo(i, slotStack.getCount()));
            }
        }
        
        // 再次验证总数
        int totalInSlots = matchingSlots.stream().mapToInt(s -> s.count).sum();
        if (totalInSlots < count) {
            System.out.println("[Economy] Slot validation failed: totalInSlots=" + totalInSlots + ", requested=" + count);
            return false;
        }
        
        // 第三步：执行移除
        int remaining = count;
        for (SlotInfo slot : matchingSlots) {
            if (remaining <= 0) {
                break;
            }
            
            ItemStack slotStack = inventory.getItem(slot.index);
            if (slotStack.isEmpty()) {
                continue; // 槽位可能在操作过程中被修改
            }
            
            int removeCount = Math.min(remaining, slotStack.getCount());
            slotStack.shrink(removeCount);
            
            if (slotStack.isEmpty()) {
                inventory.setItem(slot.index, ItemStack.EMPTY);
            }
            
            remaining -= removeCount;
        }
        
        // 同步背包变更到客户端
        syncInventory(player);
        
        boolean success = remaining <= 0;
        if (!success) {
            System.err.println("[Economy] Failed to remove all items: remaining=" + remaining);
        }
        
        return success;
    }

    /**
     * 同步插入物品栈
     */
    /**
     * 同步插入指定数量的物品
     */
    private boolean insertItemsSync(ServerPlayer player, ItemStack template, int totalCount) {
        if (player == null || template == null || totalCount <= 0) {
            return true;
        }

        Inventory inventory = player.getInventory();
        int remainingToInsert = totalCount;
        int maxStackSize = Math.min(template.getMaxStackSize(), inventory.getMaxStackSize());

        // 首先检查空间是否足够
        if (!canInsertItemsSync(player, template, totalCount)) {
            return false;
        }

        // 第一遍：填充现有的相同物品堆叠
        for (int i = 0; i < 36 && remainingToInsert > 0; i++) {
            ItemStack slotStack = inventory.getItem(i);

            if (slotStack.isEmpty()) {
                continue;
            }

            if (!ItemStack.isSameItemSameComponents(slotStack, template)) {
                continue;
            }

            int space = maxStackSize - slotStack.getCount();
            if (space <= 0) {
                continue;
            }

            int toAdd = Math.min(space, remainingToInsert);
            slotStack.grow(toAdd);
            remainingToInsert -= toAdd;
        }

        // 第二遍：放入空槽位
        for (int i = 0; i < 36 && remainingToInsert > 0; i++) {
            ItemStack slotStack = inventory.getItem(i);

            if (!slotStack.isEmpty()) {
                continue;
            }

            int toAdd = Math.min(maxStackSize, remainingToInsert);
            ItemStack newStack = template.copy();
            newStack.setCount(toAdd);
            inventory.setItem(i, newStack);
            remainingToInsert -= toAdd;
        }

        // 同步背包
        syncInventory(player);

        return remainingToInsert <= 0;
    }

    private boolean insertStackSync(ServerPlayer player, ItemStack stack) {
        return insertItemsSync(player, stack, stack.getCount());
    }

    /**
     * 同步检查是否能插入物品栈
     */
    /**
     * 同步检查是否能插入指定数量的物品
     */
    private boolean canInsertItemsSync(ServerPlayer player, ItemStack template, int totalCount) {
        if (player == null || template == null || totalCount <= 0) {
            return true;
        }

        Inventory inventory = player.getInventory();
        int remaining = totalCount;
        int maxStackSize = Math.min(template.getMaxStackSize(), inventory.getMaxStackSize());

        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack slotStack = inventory.getItem(i);

            if (slotStack.isEmpty()) {
                remaining -= maxStackSize;
                continue;
            }

            if (ItemStack.isSameItemSameComponents(slotStack, template)) {
                int space = maxStackSize - slotStack.getCount();
                if (space > 0) {
                    remaining -= space;
                }
            }
        }

        return remaining <= 0;
    }

    private boolean canInsertStackSync(ServerPlayer player, ItemStack stack) {
        return canInsertItemsSync(player, stack, stack.getCount());
    }

    /**
     * 创建匹配信息
     */
    private MatchInfo createMatchInfo(ItemKey key) {
        if (key == null || key.registryId() == null) {
            return null;
        }
        
        String registryId = key.registryId();
        int fuzzyFlags = key.fuzzyFlags();
        boolean idOnlyMatch = (fuzzyFlags & FuzzyFlags.IGNORE_COMPONENTS) != 0;
        
        ItemStack reference = null;
        if (!idOnlyMatch) {
            reference = ItemKeyFactory.stackFromKey(key, 1, server.registryAccess());
            if (reference.isEmpty()) {
                // 无法创建参考栈，回退到ID匹配
                idOnlyMatch = true;
            }
        }
        
        return new MatchInfo(registryId, reference, idOnlyMatch);
    }

    /**
     * 检查物品是否匹配
     */
    private boolean matchesItem(ItemStack stack, MatchInfo matchInfo) {
        if (stack.isEmpty() || matchInfo == null) {
            return false;
        }
        
        // 检查注册ID
        String stackRegistryId = ItemKeyFactory.registryId(stack.getItem());
        if (!stackRegistryId.equals(matchInfo.registryId)) {
            return false;
        }
        
        // 如果只匹配ID，到这里就完成了
        if (matchInfo.idOnlyMatch) {
            return true;
        }
        
        // 完整组件匹配
        if (matchInfo.reference != null) {
            return ItemStack.isSameItemSameComponents(stack, matchInfo.reference);
        }
        
        return true;
    }

    /**
     * 同步玩家背包到客户端
     */
    private void syncInventory(ServerPlayer player) {
        if (player != null) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
        }
    }

    /**
     * 匹配信息内部类
     */
    private static class MatchInfo {
        final String registryId;
        final ItemStack reference;
        final boolean idOnlyMatch;
        
        MatchInfo(String registryId, ItemStack reference, boolean idOnlyMatch) {
            this.registryId = registryId;
            this.reference = reference;
            this.idOnlyMatch = idOnlyMatch;
        }
    }

    /**
     * 槽位信息内部类
     */
    private static class SlotInfo {
        final int index;
        final int count;
        
        SlotInfo(int index, int count) {
            this.index = index;
            this.count = count;
        }
    }
}