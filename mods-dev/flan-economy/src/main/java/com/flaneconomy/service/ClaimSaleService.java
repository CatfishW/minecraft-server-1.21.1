package com.flaneconomy.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClaimSaleService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<ClaimSaleEntry>>() {}.getType();

    private final Path storagePath;
    private final Map<UUID, ClaimSaleEntry> sales = new HashMap<>();

    public ClaimSaleService() {
        this.storagePath = FabricLoader.getInstance().getConfigDir()
                .resolve("flan-economy")
                .resolve("claim_sales.json");
    }

    public void load() {
        sales.clear();
        if (!Files.exists(storagePath)) {
            return;
        }
        try {
            String json = Files.readString(storagePath, StandardCharsets.UTF_8);
            List<ClaimSaleEntry> entries = GSON.fromJson(json, LIST_TYPE);
            if (entries == null) {
                return;
            }
            for (ClaimSaleEntry entry : entries) {
                String icon = entry.iconId() == null ? "minecraft:grass_block" : entry.iconId();
                sales.put(entry.claimId(), new ClaimSaleEntry(entry.claimId(), entry.sellerId(), entry.price(), icon));
            }
        } catch (IOException ignored) {
        }
    }

    public void save() {
        try {
            Files.createDirectories(storagePath.getParent());
            String json = GSON.toJson(sales.values());
            Files.writeString(storagePath, json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public ClaimSaleEntry getSale(UUID claimId) {
        return sales.get(claimId);
    }

    public Collection<ClaimSaleEntry> getSales() {
        return Collections.unmodifiableCollection(sales.values());
    }

    public void setSale(UUID claimId, UUID sellerId, long price, String iconId) {
        sales.put(claimId, new ClaimSaleEntry(claimId, sellerId, price, iconId));
        save();
    }

    public void removeSale(UUID claimId) {
        sales.remove(claimId);
        save();
    }

    public boolean isForSale(UUID claimId) {
        return sales.containsKey(claimId);
    }
}
