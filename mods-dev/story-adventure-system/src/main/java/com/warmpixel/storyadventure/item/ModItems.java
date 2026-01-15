package com.warmpixel.storyadventure.item;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for all Story Adventure items.
 */
public class ModItems {
    
    public static final String MOD_ID = StoryAdventureMod.MOD_ID;
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    public static final Item ADMIN_WAND = register("admin_wand", new AdminWandItem());
    public static final Item CAMERA_WAND = register("camera_wand", new CameraWandItem());
    
    private static Item register(String name, Item item) {
        return Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, name),
            item
        );
    }
    
    public static void registerItems() {
        LOGGER.info("Registering Story Adventure items...");
    }
}

