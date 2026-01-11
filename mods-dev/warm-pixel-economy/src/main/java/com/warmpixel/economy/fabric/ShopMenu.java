package com.warmpixel.economy.fabric;

import com.warmpixel.economy.core.ShopOffer;
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

public class ShopMenu extends AbstractContainerMenu {
    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;
    public static final int OFFER_SLOTS = 45;
    public static final int PREV_SLOT = 45;
    public static final int NEXT_SLOT = 53;
    public static final int CAT_ALL_SLOT = 46;
    public static final int CAT_BUILDING_SLOT = 47;
    public static final int CAT_MATERIALS_SLOT = 48;
    public static final int CAT_FOOD_SLOT = 49;
    public static final int CAT_MOB_DROPS_SLOT = 50;
    public static final int CAT_UTILITY_SLOT = 51;
    public static final int CAT_NATURE_SLOT = 52;

    private final ServerPlayer serverPlayer;
    private final List<ShopOffer> serverOffers;
    private final List<ShopOfferView> offerViews;
    private final String category;
    private final String query;
    private final int page;
    private final Container container;

    public ShopMenu(int syncId, Inventory playerInventory, Data data) {
        this(syncId, playerInventory, null, List.of(), data.offers(), data.category(), data.query(), data.page());
    }

    public ShopMenu(int syncId, Inventory playerInventory, ServerPlayer player, List<ShopOffer> offers,
                    String category, String query, int page) {
        this(syncId, playerInventory, player, offers, toViews(offers), category, query, page);
    }

    private ShopMenu(int syncId, Inventory playerInventory, ServerPlayer player, List<ShopOffer> offers,
                     List<ShopOfferView> views, String category, String query, int page) {
        super(ShopScreenHandlers.SHOP_BROWSER, syncId);
        this.serverPlayer = player;
        this.serverOffers = offers == null ? List.of() : offers;
        this.offerViews = views == null ? List.of() : views;
        this.category = category == null ? "" : category;
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

    private static List<ShopOfferView> toViews(List<ShopOffer> offers) {
        if (offers == null) {
            return List.of();
        }
        List<ShopOfferView> views = new ArrayList<>(offers.size());
        for (ShopOffer offer : offers) {
            views.add(ShopOfferView.from(offer));
        }
        return views;
    }

    private void populate() {
        for (int i = 0; i < serverOffers.size() && i < OFFER_SLOTS; i++) {
            ShopOffer offer = serverOffers.get(i);
            ItemStack stack = ItemKeyFactory.stackFromSnbt(offer.itemJson(), offer.count(), serverPlayer.getServer().registryAccess());
            List<Component> lore = new ArrayList<>();
            
            // Category tag if present
            if (offer.category() != null && !offer.category().isBlank()) {
                lore.add(Component.literal("§7[§b" + offer.category() + "§7]"));
            }
            
            // Price with gold coin icon - Translatable
            long buyPrice = offer.price();
            long sellPrice = Math.max(1, (long) (buyPrice * 0.1)); // 10% of buy price
            
            lore.add(Component.translatable("ui.warm_pixel_economy.shop.buy_price", buyPrice).withStyle(s -> s.withColor(0xFFD966)));
            lore.add(Component.translatable("ui.warm_pixel_economy.shop.sell_price", sellPrice).withStyle(s -> s.withColor(0x7EB8E8)));
            
            // Stock with appropriate coloring
            if (offer.infiniteStock()) {
                lore.add(Component.translatable("ui.warm_pixel_economy.shop.offer.stock_infinite").withStyle(s -> s.withColor(0xB7C0C8)));
            } else {
                int stockColor = offer.stock() > 100 ? 0xA : offer.stock() > 10 ? 0xE : 0xC;
                lore.add(Component.translatable("ui.warm_pixel_economy.shop.offer.stock", offer.stock()).withStyle(s -> s.withColor(stockColor == 0xA ? 0x87E0A0 : stockColor == 0xE ? 0xFFD966 : 0xE07A7A)));
            }
            
            // Styled action hints
            lore.add(Component.literal("")); // Separator
            lore.add(Component.translatable("ui.warm_pixel_economy.shop.offer.buy_hint").withStyle(s -> s.withColor(0xFFD966)));
            lore.add(Component.translatable("ui.warm_pixel_economy.shop.offer.sell_hint").withStyle(s -> s.withColor(0x87E0A0)));
            
            stack.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(i, stack);
        }

        // Category Filters
        addCategoryIcon(CAT_ALL_SLOT, Items.COMPASS, "All", "ui.warm_pixel_economy.category.all");
        addCategoryIcon(CAT_BUILDING_SLOT, Items.BRICKS, "Building", "ui.warm_pixel_economy.category.building");
        addCategoryIcon(CAT_MATERIALS_SLOT, Items.DIAMOND, "Materials", "ui.warm_pixel_economy.category.materials");
        addCategoryIcon(CAT_FOOD_SLOT, Items.APPLE, "Food", "ui.warm_pixel_economy.category.food");
        addCategoryIcon(CAT_MOB_DROPS_SLOT, Items.BONE, "Mob Drops", "ui.warm_pixel_economy.category.mob_drops");
        addCategoryIcon(CAT_UTILITY_SLOT, Items.CRAFTING_TABLE, "Utility", "ui.warm_pixel_economy.category.utility");
        addCategoryIcon(CAT_NATURE_SLOT, Items.OAK_SAPLING, "Nature", "ui.warm_pixel_economy.category.nature");

        ItemStack prev = new ItemStack(Items.FEATHER);
        prev.set(DataComponents.CUSTOM_NAME, Component.translatable("ui.warm_pixel_economy.nav.previous").withStyle(s -> s.withColor(0xFFD966)));
        container.setItem(PREV_SLOT, prev);

        ItemStack next = new ItemStack(Items.SPECTRAL_ARROW);
        next.set(DataComponents.CUSTOM_NAME, Component.translatable("ui.warm_pixel_economy.nav.next").withStyle(s -> s.withColor(0xFFD966)));
        container.setItem(NEXT_SLOT, next);
    }

    private void addCategoryIcon(int slot, net.minecraft.world.item.Item item, String catId, String translationKey) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable(translationKey));
        if (this.category.equals(catId) || (catId.equals("All") && this.category.isEmpty())) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        container.setItem(slot, stack);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        if (slotId >= 0 && slotId < OFFER_SLOTS && slotId < serverOffers.size()) {
            ShopOffer offer = serverOffers.get(slotId);
            TradeMode mode = button == 1 ? TradeMode.SELL : TradeMode.BUY;
            if (mode == TradeMode.BUY && !offer.buyEnabled()) {
                server.displayClientMessage(Component.translatable("ui.warm_pixel_economy.shop.offer.buy_disabled"), true);
                return;
            }
            if (mode == TradeMode.SELL && !offer.sellEnabled()) {
                server.displayClientMessage(Component.translatable("ui.warm_pixel_economy.shop.offer.sell_disabled"), true);
                return;
            }
            ShopTradeGui.open(server, offer, mode, category, query, page);
            return;
        }
        if (slotId == PREV_SLOT && page > 0) {
            ShopGui.open(server, category.isBlank() ? null : category, query.isBlank() ? null : query, page - 1);
            return;
        }
        if (slotId == NEXT_SLOT) {
            ShopGui.open(server, category.isBlank() ? null : category, query.isBlank() ? null : query, page + 1);
            return;
        }
        
        String newCategory = null;
        if (slotId == CAT_ALL_SLOT) newCategory = "";
        else if (slotId == CAT_BUILDING_SLOT) newCategory = "Building";
        else if (slotId == CAT_MATERIALS_SLOT) newCategory = "Materials";
        else if (slotId == CAT_FOOD_SLOT) newCategory = "Food";
        else if (slotId == CAT_MOB_DROPS_SLOT) newCategory = "Mob Drops";
        else if (slotId == CAT_UTILITY_SLOT) newCategory = "Utility";
        else if (slotId == CAT_NATURE_SLOT) newCategory = "Nature";
        
        if (newCategory != null) {
            ShopGui.open(server, newCategory.isEmpty() ? null : newCategory, query.isBlank() ? null : query, 0);
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

    public List<ShopOfferView> offerViews() {
        return offerViews;
    }

    public String category() {
        return category;
    }

    public String query() {
        return query;
    }

    public int page() {
        return page;
    }

    public record Data(List<ShopOfferView> offers, String category, String query, int page) {
        private static final StreamCodec<RegistryFriendlyByteBuf, List<ShopOfferView>> OFFER_LIST_CODEC =
                ShopOfferView.STREAM_CODEC.apply(ByteBufCodecs.list());

        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                OFFER_LIST_CODEC, Data::offers,
                ByteBufCodecs.STRING_UTF8, Data::category,
                ByteBufCodecs.STRING_UTF8, Data::query,
                ByteBufCodecs.VAR_INT, Data::page,
                Data::new
        );
    }
}
