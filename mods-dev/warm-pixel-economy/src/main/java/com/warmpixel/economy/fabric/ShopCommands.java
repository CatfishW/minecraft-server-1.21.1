package com.warmpixel.economy.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.warmpixel.economy.core.ItemSnapshot;
import com.warmpixel.economy.service.ShopService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ShopCommands {
    private ShopCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("shop");

        root.executes(context -> openShop(context, null, null, 0));

        root.then(Commands.argument("page", IntegerArgumentType.integer(0))
                .executes(context -> openShop(context, null, null, IntegerArgumentType.getInteger(context, "page")))
                .then(Commands.argument("category", StringArgumentType.word())
                        .executes(context -> openShop(context, StringArgumentType.getString(context, "category"), null, IntegerArgumentType.getInteger(context, "page")))));

        root.then(Commands.literal("open")
                .executes(context -> openShop(context, null, null, 0))
                .then(Commands.argument("category", StringArgumentType.word())
                        .executes(context -> openShop(context, StringArgumentType.getString(context, "category"), null, 0))
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(context -> openShop(context, StringArgumentType.getString(context, "category"), StringArgumentType.getString(context, "query"), 0)))));

        root.then(Commands.literal("buy")
                .then(Commands.argument("offerId", StringArgumentType.word())
                        .executes(ShopCommands::buyOffer)
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                                .executes(ShopCommands::buyOfferWithQuantity))));

        root.then(Commands.literal("sell")
                .then(Commands.argument("offerId", StringArgumentType.word())
                        .executes(ShopCommands::sellOffer)
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                                .executes(ShopCommands::sellOfferWithQuantity))));

        root.then(Commands.literal("admin")
                .requires(source -> PermissionHelper.hasPermission(source.getPlayer(), "warm_pixel_economy.admin", 4))
                .then(Commands.literal("offer")
                        .then(Commands.literal("add")
                                .then(Commands.argument("price", LongArgumentType.longArg(0))
                                        .then(Commands.argument("stock", LongArgumentType.longArg(0))
                                                .then(Commands.argument("mode", StringArgumentType.word())
                                                        .executes(ShopCommands::addOffer)
                                                        .then(Commands.argument("category", StringArgumentType.greedyString())
                                                                .executes(ShopCommands::addOfferWithCategory))))))));
        root.then(Commands.literal("admin")
                .requires(source -> PermissionHelper.hasPermission(source.getPlayer(), "warm_pixel_economy.admin", 4))
                .then(Commands.literal("import")
                        .then(Commands.literal("building_blocks")
                                .executes(context -> importBuildingBlocks(context, false))
                                .then(Commands.argument("overwrite", BoolArgumentType.bool())
                                        .executes(context -> importBuildingBlocks(context, BoolArgumentType.getBool(context, "overwrite")))))
                        .then(Commands.literal("survival")
                                .executes(context -> importVanillaSurvival(context, false))
                                .then(Commands.argument("overwrite", BoolArgumentType.bool())
                                        .executes(context -> importVanillaSurvival(context, BoolArgumentType.getBool(context, "overwrite"))))))
                .then(Commands.literal("clear")
                        .executes(ShopCommands::clearAllOffers)));

        dispatcher.register(root);
    }

    private static int openShop(CommandContext<CommandSourceStack> context, String category, String query, int page) {
        if (!WarmPixelEconomyMod.getContext().config().enableGui) {
            context.getSource().sendFailure(Component.translatable("command.warm_pixel_economy.gui_disabled"));
            return 0;
        }
        ServerPlayer player = context.getSource().getPlayer();
        ShopGui.open(player, category, query, page);
        return 1;
    }

    private static int buyOffer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        String offerId = StringArgumentType.getString(context, "offerId");
        String currency = WarmPixelEconomyMod.getContext().config().defaultCurrency;
        WarmPixelEconomyMod.getContext().shopService().buyOffer(player, offerId, currency, 1)
                .thenAccept(result -> {
                    if (result.success()) {
                        context.getSource().sendSuccess(() -> resultComponent(result), false);
                    } else {
                        context.getSource().sendFailure(resultComponent(result));
                    }
                });
        return 1;
    }

    private static int buyOfferWithQuantity(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        String offerId = StringArgumentType.getString(context, "offerId");
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        String currency = WarmPixelEconomyMod.getContext().config().defaultCurrency;
        WarmPixelEconomyMod.getContext().shopService().buyOffer(player, offerId, currency, quantity)
                .thenAccept(result -> {
                    if (result.success()) {
                        context.getSource().sendSuccess(() -> resultComponent(result), false);
                    } else {
                        context.getSource().sendFailure(resultComponent(result));
                    }
                });
        return 1;
    }

    private static int sellOffer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        String offerId = StringArgumentType.getString(context, "offerId");
        String currency = WarmPixelEconomyMod.getContext().config().defaultCurrency;
        WarmPixelEconomyMod.getContext().shopService().sellToShop(player, offerId, currency, 1)
                .thenAccept(result -> {
                    if (result.success()) {
                        context.getSource().sendSuccess(() -> resultComponent(result), false);
                    } else {
                        context.getSource().sendFailure(resultComponent(result));
                    }
                });
        return 1;
    }

    private static int sellOfferWithQuantity(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        String offerId = StringArgumentType.getString(context, "offerId");
        int quantity = IntegerArgumentType.getInteger(context, "quantity");
        String currency = WarmPixelEconomyMod.getContext().config().defaultCurrency;
        WarmPixelEconomyMod.getContext().shopService().sellToShop(player, offerId, currency, quantity)
                .thenAccept(result -> {
                    if (result.success()) {
                        context.getSource().sendSuccess(() -> resultComponent(result), false);
                    } else {
                        context.getSource().sendFailure(resultComponent(result));
                    }
                });
        return 1;
    }

    private static int addOffer(CommandContext<CommandSourceStack> context) {
        return addOfferInternal(context, null);
    }

    private static int addOfferWithCategory(CommandContext<CommandSourceStack> context) {
        return addOfferInternal(context, StringArgumentType.getString(context, "category"));
    }

    private static int addOfferInternal(CommandContext<CommandSourceStack> context, String category) {
        ServerPlayer player = context.getSource().getPlayer();
        long price = LongArgumentType.getLong(context, "price");
        long stock = LongArgumentType.getLong(context, "stock");
        String mode = StringArgumentType.getString(context, "mode");
        boolean buyEnabled = "buy".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
        boolean sellEnabled = "sell".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
        if (!buyEnabled && !sellEnabled) {
            context.getSource().sendFailure(Component.translatable("command.warm_pixel_economy.mode_invalid"));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("command.warm_pixel_economy.hold_item_add"));
            return 0;
        }
        ItemSnapshot snapshot = ItemKeyFactory.snapshot(stack, 0, player.getServer().registryAccess());
        ShopService shopService = WarmPixelEconomyMod.getContext().shopService();
        shopService.createOffer(WarmPixelEconomyMod.getContext().config().shop.adminShopId, snapshot, price, (int) stock, buyEnabled, sellEnabled, category)
                .thenAccept(result -> {
                    if (result.success()) {
                        context.getSource().sendSuccess(() -> resultComponent(result), false);
                    } else {
                        context.getSource().sendFailure(resultComponent(result));
                    }
                });
        return 1;
    }

    private static int importBuildingBlocks(CommandContext<CommandSourceStack> context, boolean overwrite) {
        ServerPlayer player = context.getSource().getPlayer();
        EconomyConfig config = WarmPixelEconomyMod.getContext().config();
        ShopOfferImport.loadOrGenerateVanillaBuildingBlocks(player.getServer(), config, overwrite)
                .thenCompose(entries -> WarmPixelEconomyMod.getContext().shopService()
                        .importOffers(config.shop.adminShopId, entries, player.getServer().registryAccess()))
                .thenAccept(result -> {
                    context.getSource().sendSuccess(
                            () -> Component.translatable("command.warm_pixel_economy.import_summary", result.created(), result.skipped(), result.failed()),
                            false);
                });
        return 1;
    }

    private static int importVanillaSurvival(CommandContext<CommandSourceStack> context, boolean overwrite) {
        ServerPlayer player = context.getSource().getPlayer();
        EconomyConfig config = WarmPixelEconomyMod.getContext().config();
        ShopOfferImport.loadOrGenerateVanillaSurvival(player.getServer(), config, overwrite)
                .thenCompose(entries -> WarmPixelEconomyMod.getContext().shopService()
                        .importOffers(config.shop.adminShopId, entries, player.getServer().registryAccess()))
                .thenAccept(result -> {
                    context.getSource().sendSuccess(
                            () -> Component.translatable("command.warm_pixel_economy.import_summary", result.created(), result.skipped(), result.failed()),
                            false);
                });
        return 1;
    }

    private static int clearAllOffers(CommandContext<CommandSourceStack> context) {
        String adminShopId = WarmPixelEconomyMod.getContext().config().shop.adminShopId;
        WarmPixelEconomyMod.getContext().shopService().clearOffers(adminShopId)
                .thenAccept(result -> {
                    context.getSource().sendSuccess(() -> resultComponent(result), false);
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
