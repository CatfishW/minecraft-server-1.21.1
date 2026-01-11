package com.warmpixel.economy.fabric;

import com.warmpixel.economy.core.FuzzyFlags;
import com.warmpixel.economy.core.ItemKey;
import com.warmpixel.economy.core.ItemSnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ItemKeyFactory {
    private ItemKeyFactory() {
    }

    public static ItemSnapshot snapshot(ItemStack stack, int fuzzyFlags, HolderLookup.Provider provider) {
        Tag rawTag = stack.saveOptional(provider);
        CompoundTag fullTag = rawTag instanceof CompoundTag compound ? compound : new CompoundTag();
        String fullSnbt = NbtCanonicalSnbt.toSnbt(fullTag);

        CompoundTag keyTag = fullTag.copy();
        keyTag.remove("Count");
        keyTag.remove("count");
        keyTag.remove("id");
        applyFuzzy(keyTag, fuzzyFlags);

        String componentsSnbt = NbtCanonicalSnbt.toSnbt(keyTag);
        String registryId = registryId(stack.getItem());
        String hash = sha256(componentsSnbt);
        ItemKey key = new ItemKey(registryId, componentsSnbt, hash, fuzzyFlags);
        return new ItemSnapshot(key, fullSnbt, stack.getCount());
    }

    public static ItemStack stackFromSnbt(String snbt, int count, HolderLookup.Provider provider) {
        CompoundTag tag = NbtCanonicalSnbt.parseCompound(snbt);
        tag.remove("Count");
        tag.remove("count");
        ItemStack stack = ItemStack.parseOptional(provider, tag);
        if (stack.isEmpty()) {
            return stack;
        }
        stack.setCount(count);
        return stack;
    }

    private static void applyFuzzy(CompoundTag tag, int fuzzyFlags) {
        if ((fuzzyFlags & FuzzyFlags.IGNORE_COMPONENTS) != 0) {
            tag.getAllKeys().forEach(tag::remove);
            return;
        }

        if ((fuzzyFlags & FuzzyFlags.IGNORE_DAMAGE) != 0) {
            tag.remove("Damage");
        }

        if ((fuzzyFlags & FuzzyFlags.IGNORE_CUSTOM_NAME) != 0 || (fuzzyFlags & FuzzyFlags.IGNORE_LORE) != 0) {
            CompoundTag display = tag.getCompound("display");
            if (!display.isEmpty()) {
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

        CompoundTag components = tag.getCompound("components");
        if (!components.isEmpty()) {
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
            }
        }
    }

    public static String registryId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

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
