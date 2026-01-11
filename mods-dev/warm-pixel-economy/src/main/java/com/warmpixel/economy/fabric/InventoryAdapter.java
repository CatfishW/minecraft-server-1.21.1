package com.warmpixel.economy.fabric;

import com.warmpixel.economy.core.ItemKey;
import com.warmpixel.economy.core.ItemSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.CompletableFuture;

public class InventoryAdapter {
    private final MinecraftServer server;

    public InventoryAdapter(MinecraftServer server) {
        this.server = server;
    }

    public CompletableFuture<Boolean> removeMatching(ServerPlayer player, ItemKey key, int count) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        server.execute(() -> future.complete(removeMatchingSync(player, key, count)));
        return future;
    }

    public CompletableFuture<Integer> countMatching(ServerPlayer player, ItemKey key) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        server.execute(() -> future.complete(countMatchingSync(player, key)));
        return future;
    }

    public CompletableFuture<Boolean> insertStack(ServerPlayer player, ItemStack stack) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        server.execute(() -> future.complete(insertStackSync(player, stack)));
        return future;
    }

    private int countMatchingSync(ServerPlayer player, ItemKey key) {
        Container inventory = player.getInventory();
        int available = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemSnapshot snapshot = ItemKeyFactory.snapshot(stack, key.fuzzyFlags(), server.registryAccess());
            if (snapshot.key().itemHash().equals(key.itemHash())) {
                available += stack.getCount();
            }
        }
        return available;
    }

    private boolean removeMatchingSync(ServerPlayer player, ItemKey key, int count) {
        Container inventory = player.getInventory();
        int available = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemSnapshot snapshot = ItemKeyFactory.snapshot(stack, key.fuzzyFlags(), server.registryAccess());
            if (snapshot.key().itemHash().equals(key.itemHash())) {
                available += stack.getCount();
            }
        }
        if (available < count) {
            return false;
        }

        int remaining = count;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemSnapshot snapshot = ItemKeyFactory.snapshot(stack, key.fuzzyFlags(), server.registryAccess());
            if (!snapshot.key().itemHash().equals(key.itemHash())) {
                continue;
            }
            int remove = Math.min(remaining, stack.getCount());
            stack.shrink(remove);
            inventory.setItem(i, stack);
            remaining -= remove;
            if (remaining <= 0) {
                return true;
            }
        }
        return true;
    }

    private boolean insertStackSync(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        Container inventory = player.getInventory();
        ItemStack remaining = stack.copy();
        int max = Math.min(remaining.getMaxStackSize(), inventory.getMaxStackSize());
        int available = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotStack = inventory.getItem(i);
            if (slotStack.isEmpty()) {
                available += max;
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                continue;
            }
            int space = max - slotStack.getCount();
            if (space > 0) {
                available += space;
            }
        }
        if (available < remaining.getCount()) {
            return false;
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotStack = inventory.getItem(i);
            if (!ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                continue;
            }
            int space = max - slotStack.getCount();
            if (space <= 0) {
                continue;
            }
            int toMove = Math.min(space, remaining.getCount());
            slotStack.grow(toMove);
            remaining.shrink(toMove);
            inventory.setItem(i, slotStack);
            if (remaining.isEmpty()) {
                return true;
            }
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotStack = inventory.getItem(i);
            if (!slotStack.isEmpty()) {
                continue;
            }
            int toMove = Math.min(max, remaining.getCount());
            ItemStack placed = remaining.copy();
            placed.setCount(toMove);
            inventory.setItem(i, placed);
            remaining.shrink(toMove);
            if (remaining.isEmpty()) {
                return true;
            }
        }
        return remaining.isEmpty();
    }
}
