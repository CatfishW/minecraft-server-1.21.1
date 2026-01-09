package com.novus.items.bounty;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class BountyScrollItem extends Item {
    public static final String TAG_BOUNTY_ID = "BountyId";
    private static final String TAG_TITLE = "Title";
    private static final String TAG_DESC = "Desc";
    private static final String TAG_REQ = "Req";
    private static final String TAG_REWARD = "Reward";

    public BountyScrollItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createScroll(net.minecraft.core.HolderLookup.Provider registries, BountyBoardManager.Bounty bounty) {
        ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("novus_items", "bounty_scroll")));
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_BOUNTY_ID, bounty.id);
        tag.putString(TAG_TITLE, bounty.title);
        tag.putString(TAG_DESC, bounty.description);
        tag.putString(TAG_REQ, bounty.type.name() + " | " + BountyBoardManager.requirementText(bounty));
        tag.putString(TAG_REWARD, BountyBoardManager.rewardText(bounty));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("任务卷轴: " + bounty.title).withStyle(ChatFormatting.GOLD));
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }
        String bountyId = getBountyId(stack);
        if (bountyId.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.literal("§c✦ 这个卷轴没有任务信息。 §c✦"));
            return InteractionResultHolder.fail(stack);
        }
        BountyBoardManager.SubmitResult result = BountyBoardManager.trySubmitScroll(serverPlayer, bountyId);
        if (result != BountyBoardManager.SubmitResult.NONE) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
            if (!serverPlayer.getAbilities().instabuild && result == BountyBoardManager.SubmitResult.COMPLETED) {
                stack.shrink(1);
            }
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    public static String getBountyId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return "";
        }
        CompoundTag tag = data.copyTag();
        return tag.getString(TAG_BOUNTY_ID);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return;
        }
        CompoundTag tag = data.copyTag();
        String title = tag.getString(TAG_TITLE);
        String desc = tag.getString(TAG_DESC);
        String req = tag.getString(TAG_REQ);
        String reward = tag.getString(TAG_REWARD);
        if (!title.isEmpty()) {
            tooltip.add(Component.literal("§f" + title));
        }
        if (!desc.isEmpty()) {
            tooltip.add(Component.literal("§7" + desc));
        }
        if (!req.isEmpty()) {
            tooltip.add(Component.literal("§b任务: §f" + req));
        }
        if (!reward.isEmpty()) {
            tooltip.add(Component.literal("§6奖励: §f" + reward));
        }
        tooltip.add(Component.literal("§7右键提交"));
    }
}
