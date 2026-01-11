package com.warmpixel.economy.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.warmpixel.economy.core.EconomyResult;
import com.warmpixel.economy.service.BalanceService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class EconomyCommands {
    private EconomyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eco")
                .then(Commands.literal("balance")
                        .executes(EconomyCommands::balanceSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> PermissionHelper.hasPermission(source.getPlayer(), "warm_pixel_economy.admin", 4))
                                .executes(EconomyCommands::balanceOther)))
                .then(Commands.literal("pay")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(EconomyCommands::pay))))
                .then(Commands.literal("admin")
                        .requires(source -> PermissionHelper.hasPermission(source.getPlayer(), "warm_pixel_economy.admin", 4))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                .executes(EconomyCommands::adminSet))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                .executes(EconomyCommands::adminAdd))))
                        .then(Commands.literal("sub")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                                .executes(EconomyCommands::adminSub))))
                )
                .then(Commands.literal("delivery")
                        .then(Commands.literal("claim")
                                .executes(EconomyCommands::claimDeliveries)))
        );
    }

    private static int balanceSelf(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        BalanceService balanceService = WarmPixelEconomyMod.getContext().balanceService();
        balanceService.getBalance(player.getUUID(), WarmPixelEconomyMod.getContext().config().defaultCurrency)
                .thenAccept(balance -> context.getSource().sendSuccess(
                        () -> Component.translatable("command.warm_pixel_economy.balance_self", balance),
                        false));
        return 1;
    }

    private static int balanceOther(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        BalanceService balanceService = WarmPixelEconomyMod.getContext().balanceService();
        balanceService.getBalance(target.getUUID(), WarmPixelEconomyMod.getContext().config().defaultCurrency)
                .thenAccept(balance -> context.getSource().sendSuccess(
                        () -> Component.translatable("command.warm_pixel_economy.balance_other", target.getName().getString(), balance),
                        false));
        return 1;
    }

    private static int pay(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayer();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        long amount = LongArgumentType.getLong(context, "amount");
        BalanceService balanceService = WarmPixelEconomyMod.getContext().balanceService();
        String currency = WarmPixelEconomyMod.getContext().config().defaultCurrency;
        balanceService.debit(sender.getUUID(), currency, amount, "pay")
                .thenCompose(result -> {
                    if (!result.success()) {
                        context.getSource().sendFailure(resultComponent(result));
                        return java.util.concurrent.CompletableFuture.completedFuture(result);
                    }
                    return balanceService.credit(target.getUUID(), currency, amount, "pay");
                })
                .thenAccept(result -> {
                    if (result.success()) {
                        context.getSource().sendSuccess(
                                () -> Component.translatable("command.warm_pixel_economy.pay_success", amount, target.getName().getString()),
                                false);
                    } else {
                        context.getSource().sendFailure(resultComponent(result));
                    }
                });
        return 1;
    }

    private static int adminSet(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        long amount = LongArgumentType.getLong(context, "amount");
        BalanceService balanceService = WarmPixelEconomyMod.getContext().balanceService();
        String currency = WarmPixelEconomyMod.getContext().config().defaultCurrency;
        balanceService.getBalance(target.getUUID(), currency)
                .thenCompose(balance -> balanceService.debit(target.getUUID(), currency, balance, "admin_set"))
                .thenCompose(result -> balanceService.credit(target.getUUID(), currency, amount, "admin_set"))
                .thenAccept(result -> context.getSource().sendSuccess(
                        () -> Component.translatable("command.warm_pixel_economy.set_balance", amount),
                        false));
        return 1;
    }

    private static int adminAdd(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return adminAdjust(context, true);
    }

    private static int adminSub(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return adminAdjust(context, false);
    }

    private static int adminAdjust(CommandContext<CommandSourceStack> context, boolean add) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        long amount = LongArgumentType.getLong(context, "amount");
        BalanceService balanceService = WarmPixelEconomyMod.getContext().balanceService();
        String currency = WarmPixelEconomyMod.getContext().config().defaultCurrency;
        java.util.concurrent.CompletableFuture<EconomyResult> future = add
                ? balanceService.credit(target.getUUID(), currency, amount, "admin_add")
                : balanceService.debit(target.getUUID(), currency, amount, "admin_sub");
        future.thenAccept(result -> {
            if (result.success()) {
                context.getSource().sendSuccess(
                        () -> Component.translatable("message.warm_pixel_economy.balance_updated"),
                        false);
            } else {
                context.getSource().sendFailure(resultComponent(result));
            }
        });
        return 1;
    }

    private static int claimDeliveries(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        WarmPixelEconomyMod.getContext().deliveryService().claimDeliveries(player, 50, WarmPixelEconomyMod.getContext().config().defaultCurrency)
                .thenAccept(ignored -> context.getSource().sendSuccess(
                        () -> Component.translatable("command.warm_pixel_economy.deliveries_processed"),
                        false));
        return 1;
    }

    private static Component resultComponent(EconomyResult result) {
        if (result.messageKey() == null || result.messageKey().isBlank()) {
            return Component.empty();
        }
        return Component.translatable(result.messageKey(), result.messageArgs());
    }
}
