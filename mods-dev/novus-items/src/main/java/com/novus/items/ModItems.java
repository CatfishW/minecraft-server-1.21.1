package com.novus.items;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public class ModItems {
    // Currency Items - Novus Coins
    public static final Item BRONZE_NOVUS_COIN = registerItem("bronze_novus_coin",
            new Item(new Item.Properties().stacksTo(64)));
    
    public static final Item SILVER_NOVUS_COIN = registerItem("silver_novus_coin",
            new Item(new Item.Properties().stacksTo(64)));
    
    public static final Item GOLD_NOVUS_COIN = registerItem("gold_novus_coin",
            new Item(new Item.Properties().stacksTo(64)));
    
    public static final Item BANKNOTE_SCROLL = registerItem("banknote_scroll",
            new Item(new Item.Properties().stacksTo(64)));
    
    // Aether Scrolls - different flight durations
    public static final Item AETHER_SCROLL_1H = registerItem("aether_scroll_1h",
            new AetherScrollItem(FlightDuration.ONE_HOUR, new Item.Properties().stacksTo(16)));
    
    public static final Item AETHER_SCROLL_12H = registerItem("aether_scroll_12h",
            new AetherScrollItem(FlightDuration.TWELVE_HOURS, new Item.Properties().stacksTo(16)));
    
    public static final Item AETHER_SCROLL_24H = registerItem("aether_scroll_24h",
            new AetherScrollItem(FlightDuration.ONE_DAY, new Item.Properties().stacksTo(16)));
    
    public static final Item AETHER_SCROLL_3D = registerItem("aether_scroll_3d",
            new AetherScrollItem(FlightDuration.THREE_DAYS, new Item.Properties().stacksTo(16)));
    
    public static final Item AETHER_SCROLL_7D = registerItem("aether_scroll_7d",
            new AetherScrollItem(FlightDuration.SEVEN_DAYS, new Item.Properties().stacksTo(16)));
    
    public static final Item AETHER_SCROLL_PERMANENT = registerItem("aether_scroll_permanent",
            new AetherScrollItem(FlightDuration.PERMANENT, new Item.Properties().stacksTo(1)));

    private static Item registerItem(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(NovusItemsMod.MOD_ID, name), item);
    }

    public static void register() {
        NovusItemsMod.LOGGER.info("Registering Novus Items...");
        
        // Add currency items to creative tab
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            entries.accept(BRONZE_NOVUS_COIN);
            entries.accept(SILVER_NOVUS_COIN);
            entries.accept(GOLD_NOVUS_COIN);
            entries.accept(BANKNOTE_SCROLL);
        });
        
        // Add Aether Scrolls to creative tab
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(AETHER_SCROLL_1H);
            entries.accept(AETHER_SCROLL_12H);
            entries.accept(AETHER_SCROLL_24H);
            entries.accept(AETHER_SCROLL_3D);
            entries.accept(AETHER_SCROLL_7D);
            entries.accept(AETHER_SCROLL_PERMANENT);
        });
    }
}
