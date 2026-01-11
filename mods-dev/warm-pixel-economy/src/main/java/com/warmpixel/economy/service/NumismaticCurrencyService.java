package com.warmpixel.economy.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class NumismaticCurrencyService {
    private final MinecraftServer server;
    private final boolean available;
    private final Object currencyKey;
    private final Method keyGet;
    private final Method getValue;
    private final Method modify;

    public NumismaticCurrencyService(MinecraftServer server) {
        this.server = server;
        Object key = null;
        Method getMethod = null;
        Method valueMethod = null;
        Method modifyMethod = null;
        boolean ok = false;
        try {
            Class<?> modComponents = Class.forName("com.glisco.numismaticoverhaul.ModComponents");
            Field field = modComponents.getField("CURRENCY");
            key = field.get(null);
            getMethod = key.getClass().getMethod("get", Object.class);
            Class<?> componentClass = Class.forName("com.glisco.numismaticoverhaul.currency.CurrencyComponent");
            valueMethod = componentClass.getMethod("getValue");
            modifyMethod = componentClass.getMethod("modify", long.class);
            ok = true;
        } catch (Exception ignored) {
            ok = false;
        }
        this.available = ok;
        this.currencyKey = key;
        this.keyGet = getMethod;
        this.getValue = valueMethod;
        this.modify = modifyMethod;
    }

    public boolean isAvailable() {
        return available;
    }

    public CompletableFuture<Long> getBalance(ServerPlayer player) {
        return supplyOnServer(() -> {
            if (!available || player == null) {
                return 0L;
            }
            try {
                Object component = keyGet.invoke(currencyKey, player);
                Object value = getValue.invoke(component);
                return value instanceof Long l ? l : 0L;
            } catch (Exception e) {
                return 0L;
            }
        });
    }

    public CompletableFuture<Boolean> withdraw(ServerPlayer player, long amount) {
        return supplyOnServer(() -> {
            if (!available || player == null || amount <= 0) {
                return false;
            }
            try {
                Object component = keyGet.invoke(currencyKey, player);
                long balance = (long) getValue.invoke(component);
                if (balance < amount) {
                    return false;
                }
                modify.invoke(component, -amount);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> deposit(ServerPlayer player, long amount) {
        return supplyOnServer(() -> {
            if (!available || player == null || amount <= 0) {
                return false;
            }
            try {
                Object component = keyGet.invoke(currencyKey, player);
                modify.invoke(component, amount);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> deposit(UUID playerId, long amount) {
        return supplyOnServer(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return false;
            }
            if (!available || amount <= 0) {
                return false;
            }
            try {
                Object component = keyGet.invoke(currencyKey, player);
                modify.invoke(component, amount);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private <T> CompletableFuture<T> supplyOnServer(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
