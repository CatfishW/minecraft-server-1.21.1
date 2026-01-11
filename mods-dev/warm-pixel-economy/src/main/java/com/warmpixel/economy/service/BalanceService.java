package com.warmpixel.economy.service;

import com.warmpixel.economy.core.EconomyResult;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BalanceService {
    private final MinecraftServer server;
    private final NumismaticCurrencyService currencyService;

    public BalanceService(MinecraftServer server, NumismaticCurrencyService currencyService) {
        this.server = server;
        this.currencyService = currencyService;
    }

    public CompletableFuture<Long> getBalance(UUID playerId, String currencyId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return CompletableFuture.completedFuture(0L);
        }
        return currencyService.getBalance(player);
    }

    public CompletableFuture<EconomyResult> credit(UUID playerId, String currencyId, long amount, String reason) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.player_offline"));
        }
        return currencyService.deposit(player, amount)
                .thenApply(success -> success
                        ? EconomyResult.ok("message.warm_pixel_economy.balance_updated")
                        : EconomyResult.fail("message.warm_pixel_economy.balance_update_failed"));
    }

    public CompletableFuture<EconomyResult> debit(UUID playerId, String currencyId, long amount, String reason) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return CompletableFuture.completedFuture(EconomyResult.fail("message.warm_pixel_economy.player_offline"));
        }
        return currencyService.withdraw(player, amount)
                .thenApply(success -> success
                        ? EconomyResult.ok("message.warm_pixel_economy.balance_updated")
                        : EconomyResult.fail("message.warm_pixel_economy.insufficient_funds"));
    }
}
