package com.flaneconomy.network;

import com.flaneconomy.FlanEconomyMod;
import com.flaneconomy.service.ClaimSaleEntry;
import com.flaneconomy.service.ClaimSaleService;
import com.flaneconomy.service.FlanClaimService;
import com.flaneconomy.service.NumismaticCurrencyService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class FlanEconomyNetworking {
    private static boolean payloadsRegistered = false;

    private FlanEconomyNetworking() {
    }

    public static void registerPayloadTypes() {
        if (payloadsRegistered) {
            return;
        }
        payloadsRegistered = true;
        PayloadTypeRegistry.playC2S().register(BuyClaimBlocksPayload.TYPE, BuyClaimBlocksPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(BuyClaimPayload.TYPE, BuyClaimPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestClaimMarketPayload.TYPE, RequestClaimMarketPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ListClaimPayload.TYPE, ListClaimPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(UnlistClaimPayload.TYPE, UnlistClaimPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RenameClaimPayload.TYPE, RenameClaimPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(TeleportToClaimPayload.TYPE, TeleportToClaimPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestPlayerClaimsPayload.TYPE, RequestPlayerClaimsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ClaimShopPayload.TYPE, ClaimShopPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ClaimMarketPayload.TYPE, ClaimMarketPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerClaimsPayload.TYPE, PlayerClaimsPayload.STREAM_CODEC);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(BuyClaimBlocksPayload.TYPE, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            NumismaticCurrencyService currencyService = FlanEconomyMod.getCurrencyService();
            FlanClaimService claimService = FlanEconomyMod.getClaimService();
            if (currencyService == null || claimService == null) {
                player.sendSystemMessage(Component.translatable("chat.flaneconomy.not_ready"));
                return;
            }
            int amount = Math.max(1, payload.amount());
            long totalCost = amount * FlanEconomyMod.CLAIM_BLOCK_PRICE;
            currencyService.withdraw(player, totalCost).thenAccept(success -> {
                if (success) {
                    claimService.addClaimBlocks(player, amount);
                    player.sendSystemMessage(Component.translatable("chat.flaneconomy.buy_blocks_success", amount, totalCost));
                    // Refresh client UI
                    openShopForPlayer(player);
                } else {
                    player.sendSystemMessage(Component.translatable("chat.flaneconomy.insufficient_funds"));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(BuyClaimPayload.TYPE, (payload, context) -> {
            handleBuyClaim(payload, context);
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestClaimMarketPayload.TYPE, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            openMarketForPlayer(player);
        });

        ServerPlayNetworking.registerGlobalReceiver(ListClaimPayload.TYPE, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ClaimSaleService saleService = FlanEconomyMod.getSaleService();
            FlanClaimService claimService = FlanEconomyMod.getClaimService();
            if (saleService == null || claimService == null) return;

            Object claim = claimService.getClaimById(payload.claimId());
            if (claim == null) return;
            
            UUID owner = claimService.getClaimOwner(claim);
            if (owner == null || !owner.equals(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("chat.flaneconomy.not_owner"));
                return;
            }

            saleService.setSale(payload.claimId(), owner, payload.price(), payload.iconId());
            player.sendSystemMessage(Component.translatable("chat.flaneconomy.list_success", payload.price()));
            openShopForPlayer(player); // Refresh
        });

        ServerPlayNetworking.registerGlobalReceiver(UnlistClaimPayload.TYPE, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ClaimSaleService saleService = FlanEconomyMod.getSaleService();
            if (saleService == null) return;

            ClaimSaleEntry sale = saleService.getSale(payload.claimId());
            if (sale == null) return;

            if (!sale.sellerId().equals(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("chat.flaneconomy.not_seller"));
                return;
            }

            saleService.removeSale(payload.claimId());
            player.sendSystemMessage(Component.translatable("chat.flaneconomy.unlist_success"));
            openShopForPlayer(player); // Refresh
        });

        ServerPlayNetworking.registerGlobalReceiver(RenameClaimPayload.TYPE, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            FlanClaimService claimService = FlanEconomyMod.getClaimService();
            if (claimService == null) return;

            Object claim = claimService.getClaimById(payload.claimId());
            if (claim == null) return;

            UUID owner = claimService.getClaimOwner(claim);
            if (owner == null || !owner.equals(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("chat.flaneconomy.not_owner"));
                return;
            }

            if (claimService.setClaimName(claim, payload.name())) {
                player.sendSystemMessage(Component.translatable("chat.flaneconomy.rename_success", payload.name()));
            } else {
                player.sendSystemMessage(Component.translatable("chat.flaneconomy.rename_failed"));
            }
            openShopForPlayer(player); // Refresh
        });
        
        ServerPlayNetworking.registerGlobalReceiver(TeleportToClaimPayload.TYPE, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            FlanClaimService claimService = FlanEconomyMod.getClaimService();
            ClaimSaleService saleService = FlanEconomyMod.getSaleService();
            NumismaticCurrencyService currencyService = FlanEconomyMod.getCurrencyService();
            if (claimService == null || saleService == null || currencyService == null) return;

            Object claim = claimService.getClaimById(payload.claimId());
            if (claim == null) return;

            UUID owner = claimService.getClaimOwner(claim);
            boolean isOwner = owner != null && owner.equals(player.getUUID());

            if (!isOwner && !saleService.isForSale(payload.claimId())) {
                player.sendSystemMessage(Component.translatable("chat.flaneconomy.not_for_sale"));
                return;
            }

            if (isOwner) {
                currencyService.withdraw(player, FlanEconomyMod.OWN_TELEPORT_FEE).thenAccept(success -> {
                    if (success) {
                        if (claimService.teleportToClaim(player, claim)) {
                            player.sendSystemMessage(Component.translatable("chat.flaneconomy.teleport_fee_success", FlanEconomyMod.OWN_TELEPORT_FEE));
                        } else {
                            player.sendSystemMessage(Component.translatable("chat.flaneconomy.teleport_failed"));
                        }
                    } else {
                        player.sendSystemMessage(Component.translatable("chat.flaneconomy.insufficient_funds_tp", FlanEconomyMod.OWN_TELEPORT_FEE));
                    }
                });
            } else {
                ClaimSaleEntry sale = saleService.getSale(payload.claimId());
                long fee = (sale != null) ? (long)(sale.price() * 0.1) : FlanEconomyMod.TELEPORT_FEE;
                
                currencyService.withdraw(player, fee).thenAccept(success -> {
                    if (success) {
                        if (claimService.teleportToClaim(player, claim)) {
                            player.sendSystemMessage(Component.translatable("chat.flaneconomy.teleport_fee_success", fee));
                        } else {
                            player.sendSystemMessage(Component.translatable("chat.flaneconomy.teleport_failed"));
                        }
                    } else {
                        player.sendSystemMessage(Component.translatable("chat.flaneconomy.insufficient_funds_tp", fee));
                    }
                });
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestPlayerClaimsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            openMyClaimsForPlayer(player);
        });
    }

    private static void handleBuyClaim(BuyClaimPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = (ServerPlayer) context.player();
        NumismaticCurrencyService currencyService = FlanEconomyMod.getCurrencyService();
        FlanClaimService claimService = FlanEconomyMod.getClaimService();
        ClaimSaleService saleService = FlanEconomyMod.getSaleService();
        if (currencyService == null || claimService == null || saleService == null) {
            player.sendSystemMessage(Component.translatable("chat.flaneconomy.not_ready"));
            return;
        }
        UUID claimId = payload.claimId();
        ClaimSaleEntry sale = saleService.getSale(claimId);
        if (sale == null) {
            player.sendSystemMessage(Component.translatable("chat.flaneconomy.not_for_sale"));
            return;
        }
        Object claim = claimService.getClaimById(claimId);
        if (claim == null) {
            player.sendSystemMessage(Component.translatable("chat.flaneconomy.claim_not_found"));
            return;
        }
        UUID owner = claimService.getClaimOwner(claim);
        if (owner == null || !owner.equals(sale.sellerId())) {
            saleService.removeSale(claimId);
            player.sendSystemMessage(Component.translatable("chat.flaneconomy.no_longer_for_sale"));
            openShopForPlayer(player);
            return;
        }
        currencyService.withdraw(player, sale.price()).thenAccept(success -> {
            if (!success) {
                player.sendSystemMessage(Component.translatable("chat.flaneconomy.insufficient_funds"));
                return;
            }
            currencyService.deposit(owner, sale.price()).thenAccept(depositSuccess -> {
                if (!depositSuccess) {
                    currencyService.deposit(player.getUUID(), sale.price());
                    player.sendSystemMessage(Component.translatable("chat.flaneconomy.payment_failed"));
                    return;
                }
                if (claimService.transferClaim(claim, player.getUUID())) {
                    saleService.removeSale(claimId);
                    player.sendSystemMessage(Component.translatable("chat.flaneconomy.buy_claim_success"));
                } else {
                    currencyService.deposit(player.getUUID(), sale.price());
                    player.sendSystemMessage(Component.translatable("chat.flaneconomy.transfer_failed"));
                }
                openShopForPlayer(player);
            });
        });
    }

    public static void openMarketForPlayer(ServerPlayer player) {
        NumismaticCurrencyService currencyService = FlanEconomyMod.getCurrencyService();
        ClaimSaleService saleService = FlanEconomyMod.getSaleService();
        FlanClaimService claimService = FlanEconomyMod.getClaimService();
        if (currencyService == null || saleService == null || claimService == null) {
            player.sendSystemMessage(Component.translatable("chat.flaneconomy.not_ready"));
            return;
        }

        currencyService.getBalance(player).thenAccept(balance -> {
            java.util.List<ClaimMarketPayload.MarketEntry> entries = new java.util.ArrayList<>();
            for (ClaimSaleEntry sale : saleService.getSales()) {
                Object claim = claimService.getClaimById(sale.claimId());
                if (claim != null) {
                    String name = claimService.getClaimName(claim);
                    if (name.isEmpty()) name = "Unnamed Claim";
                    String sellerName = player.server.getProfileCache()
                            .get(sale.sellerId())
                            .map(profile -> profile.getName())
                            .orElse("Unknown");
                    String icon = sale.iconId() == null ? "minecraft:grass_block" : sale.iconId();
                    entries.add(new ClaimMarketPayload.MarketEntry(sale.claimId(), name, sale.price(), sellerName, icon));
                }
            }
            ServerPlayNetworking.send(player, new ClaimMarketPayload(balance, entries));
        });
    }

    public static void openShopForPlayer(ServerPlayer player) {
        NumismaticCurrencyService currencyService = FlanEconomyMod.getCurrencyService();
        FlanClaimService claimService = FlanEconomyMod.getClaimService();
        ClaimSaleService saleService = FlanEconomyMod.getSaleService();
        if (currencyService == null || claimService == null || saleService == null) {
            player.sendSystemMessage(Component.translatable("chat.flaneconomy.not_ready"));
            return;
        }
        currencyService.getBalance(player).thenAccept(balance -> {
            int claimBlocks = claimService.getClaimBlocks(player);
            Object claim = claimService.getClaimAt(player);
            if (claim == null) {
                ServerPlayNetworking.send(player, new ClaimShopPayload(balance, claimBlocks, false, null, "", false, 0L, "", false, "minecraft:grass_block"));
                return;
            }
            UUID claimId = claimService.getClaimId(claim);
            String claimName = claimService.getClaimName(claim);
            UUID claimOwnerId = claimService.getClaimOwner(claim);
            boolean isOwner = claimOwnerId != null && claimOwnerId.equals(player.getUUID());
            
            if (claimId == null) {
                ServerPlayNetworking.send(player, new ClaimShopPayload(balance, claimBlocks, true, null, claimName, false, 0L, "", isOwner, "minecraft:grass_block"));
                return;
            }
            ClaimSaleEntry sale = saleService.getSale(claimId);
            if (sale == null) {
                ServerPlayNetworking.send(player, new ClaimShopPayload(balance, claimBlocks, true, claimId, claimName, false, 0L, "", isOwner, "minecraft:grass_block"));
                return;
            }
            String sellerName = player.server.getProfileCache()
                    .get(sale.sellerId())
                    .map(profile -> profile.getName())
                    .orElse("Unknown");
            String icon = sale.iconId() == null ? "minecraft:grass_block" : sale.iconId();
            ServerPlayNetworking.send(player, new ClaimShopPayload(balance, claimBlocks, true, claimId, claimName, true, sale.price(), sellerName, isOwner, icon));
        });
    }

    public static void openMyClaimsForPlayer(ServerPlayer player) {
        FlanClaimService claimService = FlanEconomyMod.getClaimService();
        ClaimSaleService saleService = FlanEconomyMod.getSaleService();
        if (claimService == null || saleService == null) return;

        java.util.List<PlayerClaimsPayload.PlayerClaimEntry> entries = new java.util.ArrayList<>();
        UUID playerId = player.getUUID();

        for (Object claim : claimService.getAllClaims()) {
            if (playerId.equals(claimService.getClaimOwner(claim))) {
                UUID claimId = claimService.getClaimId(claim);
                if (claimId == null) continue;

                String name = claimService.getClaimName(claim);
                if (name.isEmpty()) name = "Unnamed Claim";

                ClaimSaleEntry sale = saleService.getSale(claimId);
                boolean forSale = sale != null;
                long price = forSale ? sale.price() : 0;
                String iconId = forSale ? (sale.iconId() == null ? "minecraft:grass_block" : sale.iconId()) : "minecraft:grass_block";

                entries.add(new PlayerClaimsPayload.PlayerClaimEntry(claimId, name, forSale, price, iconId));
            }
        }
        ServerPlayNetworking.send(player, new PlayerClaimsPayload(entries));
    }
}
