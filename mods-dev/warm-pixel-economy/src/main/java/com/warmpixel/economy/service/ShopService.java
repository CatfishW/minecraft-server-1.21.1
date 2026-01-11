package com.warmpixel.economy.service;

import com.warmpixel.economy.core.EconomyResult;
import com.warmpixel.economy.core.ItemKey;
import com.warmpixel.economy.core.ItemSnapshot;
import com.warmpixel.economy.core.PriceQuote;
import com.warmpixel.economy.core.ShopOffer;
import com.warmpixel.economy.db.TransactionAbortException;
import com.warmpixel.economy.fabric.EconomyConfig;
import com.warmpixel.economy.fabric.InventoryAdapter;
import com.warmpixel.economy.fabric.ItemKeyFactory;
import com.warmpixel.economy.fabric.ShopOfferImportEntry;
import com.warmpixel.economy.fabric.ShopOfferImportResult;
import com.warmpixel.economy.storage.EconomyStorage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class ShopService {
    private final EconomyStorage storage;
    private final InventoryAdapter inventoryAdapter;
    private final DeliveryService deliveryService;
    private final NumismaticCurrencyService currencyService;
    private final EconomyConfig config;
    private final ExecutorService executor;

    public ShopService(EconomyStorage storage, InventoryAdapter inventoryAdapter, DeliveryService deliveryService,
                       NumismaticCurrencyService currencyService, EconomyConfig config, ExecutorService executor) {
        this.storage = storage;
        this.inventoryAdapter = inventoryAdapter;
        this.deliveryService = deliveryService;
        this.currencyService = currencyService;
        this.config = config;
        this.executor = executor;
    }

    public CompletableFuture<EconomyResult> createOffer(String shopId, ItemSnapshot snapshot, long price, int stock,
                                                        boolean buyEnabled, boolean sellEnabled, String category) {
        if (price < 0) {
            return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.price_non_negative"));
        }
        ShopOffer offer = new ShopOffer(
                UUID.randomUUID().toString(),
                shopId,
                snapshot.key().registryId(),
                snapshot.key().itemHash(),
                snapshot.fullSnbt(),
                snapshot.count(),
                price,
                stock,
                config.shop.adminShopInfiniteStock,
                buyEnabled,
                sellEnabled,
                category,
                0
        );
        return CompletableFuture.supplyAsync(() -> {
            storage.createOffer(offer);
            return EconomyResult.ok("message.warm_pixel_economy.offer_created", offer.offerId());
        }, executor);
    }

    public CompletableFuture<EconomyResult> clearOffers(String shopId) {
        return CompletableFuture.supplyAsync(() -> {
            storage.clearOffers(shopId);
            return EconomyResult.ok("message.warm_pixel_economy.shop_cleared");
        }, executor);
    }

    public CompletableFuture<PriceQuote> priceCheck(ItemKey key, int count, String shopId) {
        return CompletableFuture.supplyAsync(() -> {
            List<ShopOffer> offers = storage.listOffers(shopId, null, key.registryId(), 0, 1);
            if (offers.isEmpty()) {
                return new PriceQuote(0, 0, false);
            }
            ShopOffer offer = offers.get(0);
            long total = offer.price() * count;
            return new PriceQuote(offer.price(), total, offer.buyEnabled());
        }, executor);
    }

    public CompletableFuture<List<ShopOffer>> listOffers(String shopId, String category, String query, int page, int pageSize) {
        int offset = Math.max(0, page) * pageSize;
        return CompletableFuture.supplyAsync(() -> storage.listOffers(shopId, category, query, offset, pageSize), executor);
    }

    public CompletableFuture<ShopOfferImportResult> importOffers(String shopId, List<ShopOfferImportEntry> entries,
                                                                 net.minecraft.core.HolderLookup.Provider registryAccess) {
        return CompletableFuture.supplyAsync(() -> {
            int created = 0;
            int skipped = 0;
            int failed = 0;
            for (ShopOfferImportEntry entry : entries) {
                if (entry.registryId() == null || entry.registryId().isBlank()) {
                    failed++;
                    continue;
                }
                if (entry.count() <= 0 || entry.price() < 0 || entry.stock() < 0) {
                    failed++;
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(entry.registryId());
                if (id == null) {
                    failed++;
                    continue;
                }
                if (!"minecraft".equals(id.getNamespace())) {
                    failed++;
                    continue;
                }
                if (storage.hasOffer(shopId, entry.registryId(), entry.category())) {
                    skipped++;
                    continue;
                }
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id), entry.count());
                if (stack.isEmpty() || stack.getItem() == Items.AIR) {
                    failed++;
                    continue;
                }
                ItemSnapshot snapshot = ItemKeyFactory.snapshot(stack, 0, registryAccess);
                ShopOffer offer = new ShopOffer(
                        UUID.randomUUID().toString(),
                        shopId,
                        snapshot.key().registryId(),
                        snapshot.key().itemHash(),
                        snapshot.fullSnbt(),
                        snapshot.count(),
                        entry.price(),
                        entry.stock(),
                        config.shop.adminShopInfiniteStock,
                        entry.buyEnabled(),
                        entry.sellEnabled(),
                        entry.category(),
                        0
                );
                storage.createOffer(offer);
                created++;
            }
            return new ShopOfferImportResult(created, skipped, failed);
        }, executor);
    }

    public CompletableFuture<EconomyResult> buyOffer(ServerPlayer player, String offerId, String currencyId) {
        return buyOffer(player, offerId, currencyId, 1);
    }

    public CompletableFuture<EconomyResult> buyOffer(ServerPlayer player, String offerId, String currencyId, int units) {
        int safeUnits = Math.max(1, units);
        return CompletableFuture.supplyAsync(() -> storage.getOffer(offerId).orElse(null), executor)
                .thenCompose(offer -> {
                    if (offer == null) {
                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.offer_not_found"));
                    }
                    if (!offer.buyEnabled()) {
                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.offer_buy_disabled"));
                    }
                    int totalCount;
                    try {
                        totalCount = Math.multiplyExact(offer.count(), safeUnits);
                    } catch (ArithmeticException e) {
                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.quantity_too_large"));
                    }
                    if (totalCount <= 0) {
                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.quantity_positive"));
                    }
                    if (!offer.infiniteStock() && offer.stock() < totalCount) {
                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.out_of_stock"));
                    }
                    long baseTotal;
                    try {
                        baseTotal = Math.multiplyExact(offer.price(), (long) safeUnits);
                    } catch (ArithmeticException e) {
                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.total_too_large"));
                    }
                    long tax = Math.round(baseTotal * config.taxes.shopTaxRate);
                    long total = baseTotal + tax;
                    return currencyService.withdraw(player, total).thenCompose(withdrawn -> {
                        if (!withdrawn) {
                            return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.insufficient_funds"));
                        }
                        return CompletableFuture.supplyAsync(() -> {
                            ShopOffer fresh = storage.getOffer(offerId).orElse(null);
                            if (fresh == null || !fresh.buyEnabled()) {
                                throw new TransactionAbortException("Offer unavailable.");
                            }
                            if (!fresh.infiniteStock() && fresh.stock() < totalCount) {
                                throw new TransactionAbortException("Out of stock.");
                            }
                            if (!fresh.infiniteStock()) {
                                boolean updated = storage.updateStock(fresh.offerId(), fresh.stock() - totalCount, fresh.version());
                                if (!updated) {
                                    throw new TransactionAbortException("Stock changed.");
                                }
                            }
                            return fresh;
                        }, executor).thenCompose(fresh -> {
                            ItemStack stack = ItemKeyFactory.stackFromSnbt(fresh.itemJson(), totalCount, player.getServer().registryAccess());
                            return inventoryAdapter.insertStack(player, stack).thenCompose(success -> {
                                if (success) {
                                    playSound(player, SoundEvents.VILLAGER_TRADE);
                                    return CompletableFuture.completedFuture(EconomyResult.ok("message.warm_pixel_economy.purchase_complete"));
                                }
                                return deliveryService.createItemDelivery(player.getUUID(), fresh.itemHash(), fresh.itemJson(), totalCount)
                                        .thenApply(delivery -> {
                                            playSound(player, SoundEvents.VILLAGER_TRADE);
                                            return EconomyResult.ok("message.warm_pixel_economy.items_sent_delivery");
                                        });
                            });
                        }).exceptionally(ex -> {
                            currencyService.deposit(player, total);
                            return EconomyResult.fail("message.warm_pixel_economy.offer_unavailable");
                        });
                    });
                });
    }

    public CompletableFuture<EconomyResult> sellToShop(ServerPlayer player, String offerId, String currencyId) {
        return sellToShop(player, offerId, currencyId, 1);
    }

    public CompletableFuture<EconomyResult> sellToShop(ServerPlayer player, String offerId, String currencyId, int units) {
        int safeUnits = Math.max(1, units);
        return CompletableFuture.supplyAsync(() -> storage.getOffer(offerId).orElse(null), executor)
                .thenCompose(offer -> {
                    if (offer == null) {
                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.offer_not_found"));
                    }
                    if (!offer.sellEnabled()) {
                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.offer_sell_disabled"));
                    }
                    int totalCount;
                    try {
                        totalCount = Math.multiplyExact(offer.count(), safeUnits);
                    } catch (ArithmeticException e) {
                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.quantity_too_large"));
                    }
                    if (totalCount <= 0) {
                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.quantity_positive"));
                    }
                    ItemStack offerStack = ItemKeyFactory.stackFromSnbt(offer.itemJson(), offer.count(), player.getServer().registryAccess());
                    ItemKey offerKey = ItemKeyFactory.snapshot(offerStack, 0, player.getServer().registryAccess()).key();
                    return inventoryAdapter.countMatching(player, offerKey).thenCompose(available -> {
                        int offersAvailable = available / offer.count();
                        if (offersAvailable <= 0) {
                            return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.missing_items"));
                        }
                        int offersToSell = Math.min(safeUnits, offersAvailable);
                        int sellCount;
                        try {
                            sellCount = Math.multiplyExact(offer.count(), offersToSell);
                        } catch (ArithmeticException e) {
                            return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.quantity_too_large"));
                        }
                        return inventoryAdapter.removeMatching(player, offerKey, sellCount)
                                .thenCompose(removed -> {
                                    if (!removed) {
                                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.missing_items"));
                                    }
                                    long baseTotal;
                                    try {
                                        long unitSellPrice = Math.max(1, (long) (offer.price() * 0.1));
                                        baseTotal = Math.multiplyExact(unitSellPrice, (long) offersToSell);
                                    } catch (ArithmeticException e) {
                                        inventoryAdapter.insertStack(player, ItemKeyFactory.stackFromSnbt(offer.itemJson(), sellCount, player.getServer().registryAccess()));
                                        return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.total_too_large"));
                                    }
                                    long tax = Math.round(baseTotal * config.taxes.shopTaxRate);
                                    long net = baseTotal - tax;
                                    return currencyService.deposit(player, net).thenCompose(credited -> {
                                        if (!credited) {
                                            inventoryAdapter.insertStack(player, ItemKeyFactory.stackFromSnbt(offer.itemJson(), sellCount, player.getServer().registryAccess()));
                                            return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.balance_update_failed"));
                                        }
                                        playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP);
                                        if (offersToSell < safeUnits) {
                                            return CompletableFuture.completedFuture(EconomyResult.ok("message.warm_pixel_economy.sold_available_items"));
                                        }
                                        return CompletableFuture.completedFuture(EconomyResult.ok("message.warm_pixel_economy.sell_complete"));
                                    });
                                });
                    });
                });
    }

    private void playSound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound) {
        player.getServer().execute(() -> player.playSound(sound, 0.9f, 1.05f));
    }
}
