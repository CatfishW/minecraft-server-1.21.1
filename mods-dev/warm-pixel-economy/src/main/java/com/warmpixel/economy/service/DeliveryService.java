package com.warmpixel.economy.service;

import com.warmpixel.economy.api.EconomyEvents;
import com.warmpixel.economy.core.Delivery;
import com.warmpixel.economy.core.DeliveryStatus;
import com.warmpixel.economy.core.DeliveryType;
import com.warmpixel.economy.fabric.InventoryAdapter;
import com.warmpixel.economy.fabric.ItemKeyFactory;
import com.warmpixel.economy.storage.EconomyStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class DeliveryService {
    private final EconomyStorage storage;
    private final BalanceService balanceService;
    private final InventoryAdapter inventoryAdapter;
    private final ExecutorService executor;

    public DeliveryService(EconomyStorage storage, BalanceService balanceService, InventoryAdapter inventoryAdapter, ExecutorService executor) {
        this.storage = storage;
        this.balanceService = balanceService;
        this.inventoryAdapter = inventoryAdapter;
        this.executor = executor;
    }

    public CompletableFuture<Delivery> createItemDelivery(UUID ownerId, String itemHash, String itemJson, int count) {
        Delivery delivery = new Delivery(UUID.randomUUID().toString(), ownerId.toString(), DeliveryType.ITEM, itemHash, itemJson,
                count, null, 0, DeliveryStatus.PENDING, System.currentTimeMillis(), 0);
        return CompletableFuture.supplyAsync(() -> {
            storage.insertDelivery(delivery);
            EconomyEvents.DELIVERY_CREATED.invoker().onDeliveryCreated(delivery.deliveryId(), ownerId, DeliveryType.ITEM.name());
            return delivery;
        }, executor);
    }

    public CompletableFuture<Delivery> createMoneyDelivery(UUID ownerId, String currencyId, long amount) {
        Delivery delivery = new Delivery(UUID.randomUUID().toString(), ownerId.toString(), DeliveryType.MONEY, null, null,
                0, currencyId, amount, DeliveryStatus.PENDING, System.currentTimeMillis(), 0);
        return CompletableFuture.supplyAsync(() -> {
            storage.insertDelivery(delivery);
            EconomyEvents.DELIVERY_CREATED.invoker().onDeliveryCreated(delivery.deliveryId(), ownerId, DeliveryType.MONEY.name());
            return delivery;
        }, executor);
    }

    public CompletableFuture<Void> claimDeliveries(ServerPlayer player, int limit, String currencyId) {
        return CompletableFuture.supplyAsync(() -> storage.listPendingDeliveries(player.getUUID().toString(), limit), executor)
                .thenCompose(deliveries -> processDeliveries(player, deliveries, currencyId));
    }

    private CompletableFuture<Void> processDeliveries(ServerPlayer player, List<Delivery> deliveries, String currencyId) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (Delivery delivery : deliveries) {
            future = future.thenCompose(ignored -> handleDelivery(player, delivery, currencyId));
        }
        return future;
    }

    private CompletableFuture<Void> handleDelivery(ServerPlayer player, Delivery delivery, String currencyId) {
        if (delivery.type() == DeliveryType.MONEY) {
            return balanceService.credit(player.getUUID(), currencyId == null ? delivery.currencyId() : currencyId, delivery.amount(), "delivery")
                    .thenCompose(result -> CompletableFuture.runAsync(() -> {
                        if (result.success()) {
                            storage.markDeliveryClaimed(delivery.deliveryId());
                        } else {
                            storage.updateDeliveryAttempt(delivery.deliveryId());
                        }
                    }, executor));
        }

        ItemStack stack = ItemKeyFactory.stackFromSnbt(delivery.itemJson(), delivery.count(), player.getServer().registryAccess());
        return inventoryAdapter.insertStack(player, stack).thenCompose(success -> CompletableFuture.runAsync(() -> {
            if (success) {
                storage.markDeliveryClaimed(delivery.deliveryId());
            } else {
                storage.updateDeliveryAttempt(delivery.deliveryId());
            }
        }, executor));
    }
}
