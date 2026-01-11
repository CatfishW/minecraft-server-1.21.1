package com.warmpixel.economy.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;

public class AuctionConfirmMenu extends AbstractContainerMenu {
    private final AuctionListingView listing;
    private final Container container;
    private final ServerPlayer serverPlayer;

    public AuctionConfirmMenu(int syncId, Inventory playerInventory, Data data) {
        this(syncId, playerInventory, (ServerPlayer) playerInventory.player, data.listing());
    }

    public AuctionConfirmMenu(int syncId, Inventory playerInventory, ServerPlayer player, AuctionListingView listing) {
        super(AuctionScreenHandlers.AUCTION_CONFIRM, syncId);
        this.listing = listing;
        this.serverPlayer = player;
        this.container = new SimpleContainer(27);

        for (int i = 0; i < 27; i++) {
            addSlot(new Slot(container, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18) {
                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }

        // Add player inventory slots
        int startY = 84;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, startY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, startY + 58));
        }

        if (player != null) {
            populate();
        }
    }

    private void populate() {
        ItemStack confirm = new ItemStack(Items.GREEN_WOOL);
        confirm.set(DataComponents.CUSTOM_NAME, Component.translatable("ui.warm_pixel_economy.trade.confirm"));
        container.setItem(11, confirm);

        ItemStack cancel = new ItemStack(Items.RED_WOOL);
        cancel.set(DataComponents.CUSTOM_NAME, Component.translatable("ui.warm_pixel_economy.trade.cancel"));
        container.setItem(15, cancel);

        ItemStack item = ItemKeyFactory.stackFromSnbt(listing.itemJson(), listing.count(), serverPlayer.getServer().registryAccess());
        container.setItem(13, item);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer server)) return;
        if (slotId == 11) {
            WarmPixelEconomyMod.getContext().auctionService().buyout(server, listing.listingId(), WarmPixelEconomyMod.getContext().config().defaultCurrency)
                .thenAccept(result -> server.server.execute(() -> {
                    server.closeContainer();
                    if (result.success()) {
                        server.sendSystemMessage(Component.translatable(result.messageKey(), result.messageArgs()));
                    } else {
                        server.sendSystemMessage(Component.translatable(result.messageKey(), result.messageArgs()).withStyle(s -> s.withColor(0xE07A7A)));
                    }
                }));
        } else if (slotId == 15) {
            server.closeContainer();
            AuctionGui.open(server, null, 0); // Go back to auction house
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public record Data(AuctionListingView listing) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                AuctionListingView.STREAM_CODEC, Data::listing,
                Data::new
        );
    }
}
