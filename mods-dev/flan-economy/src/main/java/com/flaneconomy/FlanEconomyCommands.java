package com.flaneconomy;

import com.flaneconomy.network.FlanEconomyNetworking;
import com.flaneconomy.service.ClaimSaleEntry;
import com.flaneconomy.service.ClaimSaleService;
import com.flaneconomy.service.FlanClaimService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class FlanEconomyCommands {
    private FlanEconomyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("flaneco")
                .executes(FlanEconomyCommands::openShop)
        );

        dispatcher.register(Commands.literal("flanmarket")
                .executes(FlanEconomyCommands::openMarket)
        );

        dispatcher.register(Commands.literal("claimsell")
                .then(Commands.argument("price", LongArgumentType.longArg(1))
                        .executes(FlanEconomyCommands::sellClaim))
        );

        dispatcher.register(Commands.literal("claimunsell")
                .executes(FlanEconomyCommands::unsellClaim)
        );
    }

    private static int openShop(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        FlanEconomyNetworking.openShopForPlayer(player);
        return 1;
    }

    private static int openMarket(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        FlanEconomyNetworking.openMarketForPlayer(player);
        return 1;
    }

    private static int sellClaim(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        long price = LongArgumentType.getLong(context, "price");
        FlanClaimService claimService = FlanEconomyMod.getClaimService();
        ClaimSaleService saleService = FlanEconomyMod.getSaleService();
        if (claimService == null || saleService == null) {
            context.getSource().sendFailure(Component.literal("Claim system not ready."));
            return 0;
        }
        Object claim = claimService.getClaimAt(player);
        if (claim == null) {
            context.getSource().sendFailure(Component.literal("No claim found at your position."));
            return 0;
        }
        UUID owner = claimService.getClaimOwner(claim);
        if (owner == null || !owner.equals(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("You do not own this claim."));
            return 0;
        }
        UUID claimId = claimService.getClaimId(claim);
        if (claimId == null) {
            context.getSource().sendFailure(Component.literal("Unable to sell this claim."));
            return 0;
        }
        saleService.setSale(claimId, owner, price, "minecraft:grass_block");
        context.getSource().sendSuccess(() -> Component.literal("Claim listed for " + price + " coins."), false);
        return 1;
    }

    private static int unsellClaim(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        FlanClaimService claimService = FlanEconomyMod.getClaimService();
        ClaimSaleService saleService = FlanEconomyMod.getSaleService();
        if (claimService == null || saleService == null) {
            context.getSource().sendFailure(Component.literal("Claim system not ready."));
            return 0;
        }
        Object claim = claimService.getClaimAt(player);
        if (claim == null) {
            context.getSource().sendFailure(Component.literal("No claim found at your position."));
            return 0;
        }
        UUID claimId = claimService.getClaimId(claim);
        ClaimSaleEntry sale = saleService.getSale(claimId);
        if (sale == null) {
            context.getSource().sendFailure(Component.literal("This claim is not listed for sale."));
            return 0;
        }
        if (!sale.sellerId().equals(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("You do not own this listing."));
            return 0;
        }
        saleService.removeSale(claimId);
        context.getSource().sendSuccess(() -> Component.literal("Claim sale removed."), false);
        return 1;
    }
}
