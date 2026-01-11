package com.warmpixel.economy.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ShopOfferImport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String IMPORT_DIR = "warm-pixel-economy/imports";
    private static final String BUILDING_BLOCKS_FILE = "vanilla_building_blocks.json";
    private static final String SURVIVAL_ITEMS_FILE = "vanilla_survival.json";

    private ShopOfferImport() {
    }

    public static CompletableFuture<List<ShopOfferImportEntry>> loadOrGenerateVanillaBuildingBlocks(MinecraftServer server, EconomyConfig config,
                                                                                                   boolean overwrite) {
        return loadOrGenerate(server, config, BUILDING_BLOCKS_FILE, overwrite, () -> generateVanillaBuildingBlocks(server, config));
    }

    public static CompletableFuture<List<ShopOfferImportEntry>> loadOrGenerateVanillaSurvival(MinecraftServer server, EconomyConfig config,
                                                                                              boolean overwrite) {
        return loadOrGenerate(server, config, SURVIVAL_ITEMS_FILE, overwrite, () -> new ArrayList<>()); 
    }

    private static CompletableFuture<List<ShopOfferImportEntry>> loadOrGenerate(MinecraftServer server, EconomyConfig config, String fileName, boolean overwrite, java.util.function.Supplier<List<ShopOfferImportEntry>> generator) {
        CompletableFuture<List<ShopOfferImportEntry>> future = new CompletableFuture<>();
        server.execute(() -> {
            Path importPath = resolveImportPath(fileName);
            System.out.println("[WarmPixelEconomy] Loading import FROM: " + importPath.toAbsolutePath());
            List<ShopOfferImportEntry> entries = null;
            if (overwrite) {
                entries = generator.get();
                // If generator is empty, try loading from file instead of overwriting with nothing
                if (entries.isEmpty() && Files.exists(importPath)) {
                    entries = loadEntries(importPath);
                } else if (!entries.isEmpty()) {
                    saveEntries(importPath, entries);
                }
            } else {
                entries = loadEntries(importPath);
            }
            if (entries == null) {
                entries = generator.get();
            }
            future.complete(entries);
        });
        return future;
    }

    private static Path resolveImportPath(String fileName) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve(IMPORT_DIR)
                .resolve(fileName);
    }

    private static List<ShopOfferImportEntry> generateVanillaBuildingBlocks(MinecraftServer server, EconomyConfig config) {
        CreativeModeTabs.tryRebuildTabContents(server.getWorldData().enabledFeatures(), true, server.registryAccess());
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(CreativeModeTabs.BUILDING_BLOCKS);
        Set<String> seen = new HashSet<>();
        List<ShopOfferImportEntry> entries = new ArrayList<>();
        for (ItemStack stack : tab.getDisplayItems()) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null || !"minecraft".equals(id.getNamespace())) {
                continue;
            }
            String registryId = id.toString();
            if (!seen.add(registryId)) {
                continue;
            }
            entries.add(new ShopOfferImportEntry(
                    registryId,
                    Math.max(1, config.shop.importDefaultCount),
                    config.shop.importDefaultPrice,
                    config.shop.importDefaultStock,
                    config.shop.importBuyEnabled,
                    config.shop.importSellEnabled,
                    "building_blocks"
            ));
        }
        entries.sort(Comparator.comparing(ShopOfferImportEntry::registryId));
        return entries;
    }

    private static List<ShopOfferImportEntry> loadEntries(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ShopOfferImportEntry[] entries = GSON.fromJson(reader, ShopOfferImportEntry[].class);
            if (entries == null) {
                return null;
            }
            return new ArrayList<>(List.of(entries));
        } catch (IOException e) {
            return null;
        }
    }

    private static void saveEntries(Path path, List<ShopOfferImportEntry> entries) {
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(entries, writer);
            }
        } catch (IOException ignored) {
            // Best-effort.
        }
    }
}
