package com.warmpixel.economy.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class AuctionCommands {
    private AuctionCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("ah");

        root.executes(context -> openAuction(context, null, 0));

        root.then(Commands.literal("open")
                .executes(context -> openAuction(context, null, 0))
                .then(Commands.argument("query", StringArgumentType.greedyString())
                        .executes(context -> openAuction(context, StringArgumentType.getString(context, "query"), 0))));

        root.then(Commands.literal("list")
                .then(Commands.argument("startPrice", LongArgumentType.longArg(1))
                        .executes(context -> listAuction(context, null, null))
                        .then(Commands.argument("buyout", LongArgumentType.longArg(0))
                                .executes(context -> listAuction(context, LongArgumentType.getLong(context, "buyout"), null))
                                .then(Commands.argument("duration", IntegerArgumentType.integer(60))
                                        .executes(context -> listAuction(context, LongArgumentType.getLong(context, "buyout"), IntegerArgumentType.getInteger(context, "duration")))))));

        root.then(Commands.literal("bid")
                .then(Commands.argument("listingId", StringArgumentType.word())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(AuctionCommands::bid))));

        root.then(Commands.literal("buyout")
                .then(Commands.argument("listingId", StringArgumentType.word())
                        .executes(AuctionCommands::buyout)));

        root.then(Commands.literal("cancel")
                .then(Commands.argument("listingId", StringArgumentType.word())
                        .executes(AuctionCommands::cancel)));

        dispatcher.register(root);
    }

    private static int openAuction(CommandContext<CommandSourceStack> context, String query, int page) {
        if (!WarmPixelEconomyMod.getContext().config().enableGui) {
            context.getSource().sendFailure(Component.translatable("command.warm_pixel_economy.gui_disabled"));
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayer();
        AuctionGui.open(player, query, page);
        return 1;
    }

    private static int listAuction(CommandContext<CommandSourceStack> context, Long buyout, Integer durationSeconds) {
        ServerPlayer player = context.getSource().getPlayer();
        long startPrice = LongArgumentType.getLong(context, "startPrice");
        long buyoutPrice = buyout == null ? 0 : buyout;
        int duration = durationSeconds == null ? (int) WarmPixelEconomyMod.getContext().config().auction.defaultDurationSeconds : durationSeconds;
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("command.warm_pixel_economy.hold_item_list"));
            return 0;
        }
        long expiresAt = System.currentTimeMillis() + duration * 1000L;
        WarmPixelEconomyMod.getContext().auctionService().createListing(player, stack, stack.getCount(), startPrice,
                        buyoutPrice <= 0 ? null : buyoutPrice, expiresAt, WarmPixelEconomyMod.getContext().config().defaultCurrency)
                .thenAccept(result -> {
                    if (result.success()) {
                        context.getSource().sendSuccess(() -> resultComponent(result), false);
                    } else {
                        context.getSource().sendFailure(resultComponent(result));
                    }
                });
        return 1;
    }

    private static int bid(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        String listingId = StringArgumentType.getString(context, "listingId");
        long amount = LongArgumentType.getLong(context, "amount");
        WarmPixelEconomyMod.getContext().auctionService().placeBid(player, listingId, amount, WarmPixelEconomyMod.getContext().config().defaultCurrency)
                .thenAccept(result -> {
                    if (result.success()) {
                        context.getSource().sendSuccess(() -> resultComponent(result), false);
                    } else {
                        context.getSource().sendFailure(resultComponent(result));
                    }
                });
        return 1;
    }

    private static int buyout(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        String listingId = StringArgumentType.getString(context, "listingId");
        WarmPixelEconomyMod.getContext().auctionService().buyout(player, listingId, WarmPixelEconomyMod.getContext().config().defaultCurrency)
                .thenAccept(result -> {
                    if (result.success()) {
                        context.getSource().sendSuccess(() -> resultComponent(result), false);
                    } else {
                        context.getSource().sendFailure(resultComponent(result));
                    }
                });
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        String listingId = StringArgumentType.getString(context, "listingId");
        WarmPixelEconomyMod.getContext().auctionService().cancelListing(player, listingId)
                .thenAccept(result -> {
                    if (result.success()) {
                        context.getSource().sendSuccess(() -> resultComponent(result), false);
                    } else {
                        context.getSource().sendFailure(resultComponent(result));
                    }
                });
        return 1;
    }

    private static Component resultComponent(com.warmpixel.economy.core.EconomyResult result) {
        if (result.messageKey() == null || result.messageKey().isBlank()) {
            return Component.empty();
        }
        return Component.translatable(result.messageKey(), result.messageArgs());
    }
}
