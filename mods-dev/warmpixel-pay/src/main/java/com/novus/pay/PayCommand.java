package com.novus.pay;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class PayCommand {
    public static void register() {
        // Registering in the ModInitializer via Fabric API CommandRegistrationCallback is standard,
        // but for simplicity in this structure we'll assume a mixin or standard event if available.
        // Wait, Fabric API has CommandRegistrationCallback.
        // I should have put the callback in NovusPayMod. 
        // I will make this method accept a dispatcher or standard event.
        // To keep it simple, I'll use the Fabric API event in NovusPayMod and call this.
    }
    
    // Actually, let's just make a method that takes the dispatcher
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("novuspay")
            .then(Commands.literal("buy")
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(1.0))
                    .executes(PayCommand::buyCoins)
                )
            )
            .then(Commands.literal("rate")
                .executes(PayCommand::showRate)
                .then(Commands.literal("set")
                    .requires(source -> source.hasPermission(4))
                    .then(Commands.argument("ratio", DoubleArgumentType.doubleArg(0.1))
                        .executes(PayCommand::setRate)
                    )
                )
            )
        );
    }

    private static int buyCoins(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            double cny = DoubleArgumentType.getDouble(context, "amount");
            int coins = (int) (cny * PaymentConfig.DATA.exchangeRate);
            
            if (coins <= 0) {
                context.getSource().sendFailure(Component.literal("Amount too small for 1 coin."));
                return 0;
            }
            
            context.getSource().sendSuccess(() -> Component.literal("Initiating payment for " + coins + " coins (" + cny + " CNY)...").withStyle(ChatFormatting.YELLOW), false);
            
            PaymentManager.createOrder(player, cny, coins);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int showRate(CommandContext<CommandSourceStack> context) {
        double rate = PaymentConfig.DATA.exchangeRate;
        context.getSource().sendSuccess(() -> Component.literal("Current Rate: 1 CNY = " + rate + " Novus Coins").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int setRate(CommandContext<CommandSourceStack> context) {
        double newRate = DoubleArgumentType.getDouble(context, "ratio");
        PaymentConfig.DATA.exchangeRate = newRate;
        PaymentConfig.save();
        context.getSource().sendSuccess(() -> Component.literal("Exchange rate set to: 1 CNY = " + newRate + " Novus Coins").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
