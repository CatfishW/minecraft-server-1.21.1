package com.warmpixel.economy.fabric;

import com.warmpixel.economy.api.EconomyApiProvider;
import com.warmpixel.economy.storage.EconomyStorage;
import com.warmpixel.economy.service.AuctionService;
import com.warmpixel.economy.service.BalanceService;
import com.warmpixel.economy.service.DeliveryService;
import com.warmpixel.economy.service.EconomyApiImpl;
import com.warmpixel.economy.service.NumismaticCurrencyService;
import com.warmpixel.economy.service.ShopService;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EconomyContext {
    private final EconomyConfig config;
    private final EconomyStorage storage;
    private BalanceService balanceService;
    private DeliveryService deliveryService;
    private ShopService shopService;
    private AuctionService auctionService;
    private InventoryAdapter inventoryAdapter;
    private NumismaticCurrencyService currencyService;
    private ScheduledExecutorService scheduler;

    public EconomyContext(EconomyConfig config, EconomyStorage storage) {
        this.config = config;
        this.storage = storage;
    }

    public void onServerStarted(MinecraftServer server) {
        this.inventoryAdapter = new InventoryAdapter(server);
        this.currencyService = new NumismaticCurrencyService(server);
        this.balanceService = new BalanceService(server, currencyService);
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WarmPixelEconomy-Storage");
            thread.setDaemon(true);
            return thread;
        });
        this.deliveryService = new DeliveryService(storage, balanceService, inventoryAdapter, executor);
        this.shopService = new ShopService(storage, inventoryAdapter, deliveryService, currencyService, config, executor);
        this.auctionService = new AuctionService(storage, deliveryService, inventoryAdapter, currencyService, server, config, executor);

        EconomyApiProvider.setApi(new EconomyApiImpl(balanceService, shopService, auctionService, config.defaultCurrency));

        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WarmPixelEconomy-Expiry");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> auctionService.processExpirations(config.defaultCurrency),
                config.auctionExpirationCheckSeconds, config.auctionExpirationCheckSeconds, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public EconomyConfig config() {
        return config;
    }

    public BalanceService balanceService() {
        return balanceService;
    }

    public ShopService shopService() {
        return shopService;
    }

    public AuctionService auctionService() {
        return auctionService;
    }

    public DeliveryService deliveryService() {
        return deliveryService;
    }
}
