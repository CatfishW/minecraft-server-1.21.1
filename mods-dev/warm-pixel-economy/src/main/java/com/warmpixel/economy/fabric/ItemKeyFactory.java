package com.warmpixel.economy.fabric;

import com.warmpixel.economy.core.FuzzyFlags;
import com.warmpixel.economy.core.ItemKey;
import com.warmpixel.economy.core.ItemSnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/**
 * 物品键工厂 - 用于创建物品快照和从SNBT恢复物品
 */
public final class ItemKeyFactory {
    
    private ItemKeyFactory() {}

    /**
     * 从物品栈创建快照
     */
    public static ItemSnapshot snapshot(ItemStack stack, int fuzzyFlags, HolderLookup.Provider provider) {
        if (stack.isEmpty()) {
            return null;
        }
        
        Tag rawTag = stack.saveOptional(provider);
        CompoundTag fullTag = rawTag instanceof CompoundTag compound ? compound : new CompoundTag();
        String fullSnbt = NbtCanonicalSnbt.toSnbt(fullTag);

        // 创建用于匹配的键标签
        CompoundTag keyTag = fullTag.copy();
        keyTag.remove("Count");
        keyTag.remove("count");
        
        // 应用模糊匹配规则，但保留id
        applyFuzzy(keyTag, fuzzyFlags);

        String componentsSnbt = NbtCanonicalSnbt.toSnbt(keyTag);
        String registryId = registryId(stack.getItem());
        String hash = sha256(registryId + ":" + componentsSnbt);
        
        ItemKey key = new ItemKey(registryId, componentsSnbt, hash, fuzzyFlags);
        return new ItemSnapshot(key, fullSnbt, stack.getCount());
    }

    /**
     * 从完整SNBT恢复物品栈
     */
    public static ItemStack stackFromSnbt(String snbt, int count, HolderLookup.Provider provider) {
        if (snbt == null || snbt.isEmpty() || "{}".equals(snbt)) {
            return ItemStack.EMPTY;
        }
        
        try {
            CompoundTag tag = NbtCanonicalSnbt.parseCompound(snbt);
            if (tag == null || tag.isEmpty()) {
                return ItemStack.EMPTY;
            }
            
            // 移除数量，稍后设置
            tag.remove("Count");
            tag.remove("count");
            
            ItemStack stack = ItemStack.parseOptional(provider, tag);
            if (stack.isEmpty()) {
                // 尝试从id字段提取
                if (tag.contains("id")) {
                    String id = tag.getString("id");
                    stack = stackFromRegistryId(id, 1);
                }
            }
            
            if (!stack.isEmpty() && count > 0) {
                stack.setCount(Math.min(count, stack.getMaxStackSize()));
            }
            
            return stack;
        } catch (Exception e) {
            System.err.println("[Economy] Failed to parse SNBT: " + e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    /**
     * 从ItemKey创建物品栈（带后备机制）
     */
    public static ItemStack stackFromKey(ItemKey key, int count, HolderLookup.Provider provider) {
        if (key == null) {
            return ItemStack.EMPTY;
        }
        
        // 首先尝试从componentsSnbt解析
        ItemStack stack = stackFromSnbt(key.componentsSnbt(), count, provider);
        
        // 如果失败，从registryId创建基础物品
        if (stack.isEmpty() && key.registryId() != null) {
            stack = stackFromRegistryId(key.registryId(), count);
        }
        
        return stack;
    }

    /**
     * 从注册ID创建基础物品栈
     */
    public static ItemStack stackFromRegistryId(String registryId, int count) {
        if (registryId == null || registryId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        
        try {
            ResourceLocation loc = ResourceLocation.tryParse(registryId);
            if (loc == null) {
                return ItemStack.EMPTY;
            }
            
            if (!BuiltInRegistries.ITEM.containsKey(loc)) {
                return ItemStack.EMPTY;
            }
            
            Item item = BuiltInRegistries.ITEM.get(loc);
            ItemStack stack = new ItemStack(item);
            
            if (!stack.isEmpty() && count > 0) {
                stack.setCount(Math.min(count, stack.getMaxStackSize()));
            }
            
            return stack;
        } catch (Exception e) {
            System.err.println("[Economy] Failed to create stack from registry ID: " + registryId);
            return ItemStack.EMPTY;
        }
    }

    /**
     * 检查两个物品栈是否匹配（考虑模糊标志）
     */
    public static boolean matchesKey(ItemStack stack, ItemKey key, HolderLookup.Provider provider) {
        if (stack.isEmpty() || key == null) {
            return false;
        }
        
        // 首先检查注册ID
        String stackRegistryId = registryId(stack.getItem());
        if (!stackRegistryId.equals(key.registryId())) {
            return false;
        }
        
        int fuzzyFlags = key.fuzzyFlags();
        
        // 如果忽略所有组件，只需匹配物品类型
        if ((fuzzyFlags & FuzzyFlags.IGNORE_COMPONENTS) != 0) {
            return true;
        }
        
        // 创建参考物品栈进行比较
        ItemStack reference = stackFromKey(key, 1, provider);
        if (reference.isEmpty()) {
            // 无法创建参考栈时，回退到只匹配物品类型
            return true;
        }
        
        // 使用Minecraft的标准比较
        return ItemStack.isSameItemSameComponents(stack, reference);
    }

    /**
     * 应用模糊匹配规则
     */
    private static void applyFuzzy(CompoundTag tag, int fuzzyFlags) {
        // 始终保留id字段
        String id = tag.contains("id") ? tag.getString("id") : null;
        
        if ((fuzzyFlags & FuzzyFlags.IGNORE_COMPONENTS) != 0) {
            // 清除所有键，但保留id
            Set<String> keys = new HashSet<>(tag.getAllKeys());
            for (String key : keys) {
                if (!"id".equals(key)) {
                    tag.remove(key);
                }
            }
            return;
        }

        // 处理旧版NBT格式
        if ((fuzzyFlags & FuzzyFlags.IGNORE_DAMAGE) != 0) {
            tag.remove("Damage");
        }

        if ((fuzzyFlags & FuzzyFlags.IGNORE_CUSTOM_NAME) != 0 || (fuzzyFlags & FuzzyFlags.IGNORE_LORE) != 0) {
            if (tag.contains("display", Tag.TAG_COMPOUND)) {
                CompoundTag display = tag.getCompound("display");
                if ((fuzzyFlags & FuzzyFlags.IGNORE_CUSTOM_NAME) != 0) {
                    display.remove("Name");
                }
                if ((fuzzyFlags & FuzzyFlags.IGNORE_LORE) != 0) {
                    display.remove("Lore");
                }
                if (display.isEmpty()) {
                    tag.remove("display");
                }
            }
        }

        if ((fuzzyFlags & FuzzyFlags.IGNORE_ENCHANTS) != 0) {
            tag.remove("Enchantments");
            tag.remove("StoredEnchantments");
        }

        // 处理1.20.5+组件格式
        if (tag.contains("components", Tag.TAG_COMPOUND)) {
            CompoundTag components = tag.getCompound("components");
            if ((fuzzyFlags & FuzzyFlags.IGNORE_CUSTOM_NAME) != 0) {
                components.remove("minecraft:custom_name");
            }
            if ((fuzzyFlags & FuzzyFlags.IGNORE_LORE) != 0) {
                components.remove("minecraft:lore");
            }
            if ((fuzzyFlags & FuzzyFlags.IGNORE_DAMAGE) != 0) {
                components.remove("minecraft:damage");
            }
            if ((fuzzyFlags & FuzzyFlags.IGNORE_ENCHANTS) != 0) {
                components.remove("minecraft:enchantments");
                components.remove("minecraft:stored_enchantments");
            }
            if (components.isEmpty()) {
                tag.remove("components");
            }
        }
    }

    /**
     * 获取物品的注册ID
     */
    public static String registryId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /**
     * 计算SHA-256哈希
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}