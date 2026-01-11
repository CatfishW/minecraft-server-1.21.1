package com.flaneconomy;

import com.flaneconomy.item.LandDeedItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class FlanEconomyItems {
    public static final Item LAND_DEED = register("land_deed", new LandDeedItem(new Item.Properties().stacksTo(1)));

    public static <T extends Item> T register(String name, T item) {
        return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(FlanEconomyMod.MOD_ID, name), item);
    }

    public static void registerItems() {
        // Class loading triggers registration
    }
}
