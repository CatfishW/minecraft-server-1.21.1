package com.novus.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class AetherScrollItem extends Item {
    private final FlightDuration flightDuration;

    public AetherScrollItem(FlightDuration duration, Properties properties) {
        super(properties);
        this.flightDuration = duration;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // Check if player is in survival/adventure mode
            if (serverPlayer.isCreative() || serverPlayer.isSpectator()) {
                serverPlayer.sendSystemMessage(Component.literal("§cYou can only use Aether Scrolls in survival mode!"));
                return InteractionResultHolder.fail(stack);
            }
            
            // Check if player already has permanent flight
            if (FlightManager.hasPermanentFlight(serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.literal("§aYou already have permanent flight!"));
                return InteractionResultHolder.fail(stack);
            }
            
            // Grant flight time
            FlightManager.grantFlightTime(serverPlayer, flightDuration);
            
            // Play activation sound
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            
            // Send success message
            if (flightDuration.isPermanent()) {
                serverPlayer.sendSystemMessage(Component.literal("§6✦ §eYou have been granted §6§lPERMANENT FLIGHT§e! §6✦"));
            } else {
                serverPlayer.sendSystemMessage(Component.literal("§a✦ Flight granted for §e" + flightDuration.getDisplayName() + "§a! §6✦"));
            }
            
            // Consume the scroll
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        
        return InteractionResultHolder.consume(stack);
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        
        tooltip.add(Component.literal("§7Right-click to activate"));
        
        if (flightDuration.isPermanent()) {
            tooltip.add(Component.literal("§6§lPermanent Flight").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("§8A legendary scroll granting eternal flight"));
        } else {
            tooltip.add(Component.literal("§bDuration: §f" + flightDuration.getDisplayName()));
            tooltip.add(Component.literal("§8Grants temporary flight ability"));
        }
    }
    
    @Override
    public boolean isFoil(ItemStack stack) {
        // Make permanent scroll have enchantment glint
        return flightDuration.isPermanent();
    }
    
    public FlightDuration getFlightDuration() {
        return flightDuration;
    }
}
