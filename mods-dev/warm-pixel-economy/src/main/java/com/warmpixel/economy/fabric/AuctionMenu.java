package com.warmpixel.economy.fabric;

import com.warmpixel.economy.core.AuctionListing;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
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
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public class AuctionMenu extends AbstractContainerMenu {
    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;
    public static final int LISTING_SLOTS = 45;
    public static final int PREV_SLOT = 45;
    public static final int NEXT_SLOT = 53;

    private final ServerPlayer serverPlayer;
    private final List<AuctionListing> serverListings;
    private final List<AuctionListingView> listingViews;
    private final String query;
    private final int page;
    private final Container container;

    public AuctionMenu(int syncId, Inventory playerInventory, Data data) {
        this(syncId, playerInventory, null, List.of(), data.listings(), data.query(), data.page());
    }

    public AuctionMenu(int syncId, Inventory playerInventory, ServerPlayer player, List<AuctionListing> listings, String query, int page) {
        this(syncId, playerInventory, player, listings, toViews(listings), query, page);
    }

    private AuctionMenu(int syncId, Inventory playerInventory, ServerPlayer player, List<AuctionListing> listings,
                        List<AuctionListingView> views, String query, int page) {
        super(AuctionScreenHandlers.AUCTION_BROWSER, syncId);
        this.serverPlayer = player;
        this.serverListings = listings == null ? List.of() : listings;
        this.listingViews = views == null ? List.of() : views;
        this.query = query == null ? "" : query;
        this.page = page;
        this.container = new SimpleContainer(SIZE);

        for (int i = 0; i < SIZE; i++) {
            addSlot(new Slot(container, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18) {
                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }

        int startY = 140;
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

    private static List<AuctionListingView> toViews(List<AuctionListing> listings) {
        if (listings == null) {
            return List.of();
        }
        List<AuctionListingView> views = new ArrayList<>(listings.size());
        for (AuctionListing listing : listings) {
            views.add(AuctionListingView.from(listing));
        }
        return views;
    }

    private void populate() {
        for (int i = 0; i < serverListings.size() && i < LISTING_SLOTS; i++) {
            AuctionListing listing = serverListings.get(i);
            ItemStack stack = ItemKeyFactory.stackFromSnbt(listing.itemJson(), listing.count(), serverPlayer.getServer().registryAccess());
            List<Component> lore = new ArrayList<>();
            lore.add(Component.translatable("ui.warm_pixel_economy.auction.starting_price", listing.startingPrice()));
            if (listing.highestBid() != null) {
                lore.add(Component.translatable("ui.warm_pixel_economy.auction.current_bid", listing.highestBid()));
            }
            if (listing.buyoutPrice() != null) {
                lore.add(Component.translatable("ui.warm_pixel_economy.auction.buyout", listing.buyoutPrice()));
            }
            stack.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(i, stack);
        }

        ItemStack prev = new ItemStack(Items.FEATHER);
        prev.set(DataComponents.CUSTOM_NAME, Component.translatable("ui.warm_pixel_economy.nav.previous"));
        container.setItem(PREV_SLOT, prev);

        ItemStack next = new ItemStack(Items.SPECTRAL_ARROW);
        next.set(DataComponents.CUSTOM_NAME, Component.translatable("ui.warm_pixel_economy.nav.next"));
        container.setItem(NEXT_SLOT, next);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        if (slotId >= 0 && slotId < LISTING_SLOTS && slotId < serverListings.size()) {
            AuctionListing listing = serverListings.get(slotId);
            if (button == 0 && listing.buyoutPrice() != null) {
                WarmPixelEconomyMod.getContext().auctionService().buyout(server, listing.listingId(), WarmPixelEconomyMod.getContext().config().defaultCurrency);
            } else if (button == 1) {
                server.sendSystemMessage(Component.translatable("ui.warm_pixel_economy.auction.bid_hint", listing.listingId()));
            }
            return;
        }
        if (slotId == PREV_SLOT && page > 0) {
            AuctionGui.open(server, query, page - 1);
            return;
        }
        if (slotId == NEXT_SLOT) {
            AuctionGui.open(server, query, page + 1);
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

    public List<AuctionListingView> listingViews() {
        return listingViews;
    }

    public String query() {
        return query;
    }

    public int page() {
        return page;
    }

    public record Data(List<AuctionListingView> listings, String query, int page) {
        private static final StreamCodec<RegistryFriendlyByteBuf, List<AuctionListingView>> LISTING_LIST_CODEC =
                AuctionListingView.STREAM_CODEC.apply(ByteBufCodecs.list());

        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                LISTING_LIST_CODEC, Data::listings,
                ByteBufCodecs.STRING_UTF8, Data::query,
                ByteBufCodecs.VAR_INT, Data::page,
                Data::new
        );
    }
}
