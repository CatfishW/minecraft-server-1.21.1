package com.novus.items.bounty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BountyBoardManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private static Path dataFile;
    private static int tickCounter = 0;
    private static final int SAVE_INTERVAL = 20 * 60;
    private static final int CHECK_INTERVAL = 20;

    private static final Map<String, Bounty> bountiesById = new ConcurrentHashMap<>();
    private static final Map<UUID, String> activeBountyByPlayer = new ConcurrentHashMap<>();
    private static final List<String> refreshTimes = new ArrayList<>();
    private static final Map<String, String> lastRefreshDateByTime = new ConcurrentHashMap<>();

    public enum BountyType {
        ITEM_DELIVERY,
        ENTITY_KILL,
        CUSTOM_MANUAL
    }

    public enum RewardType {
        ITEM,
        COMMAND
    }

    public enum BountyStatus {
        AVAILABLE,
        ACCEPTED,
        PENDING_REVIEW,
        COMPLETED
    }

    public static class Reward {
        public RewardType type = RewardType.ITEM;
        public String itemId = "";
        public int itemCount = 0;
        public String itemCustomDataNbt = "";
        public String command = "";
    }

    private record ParsedReward(Reward reward, ItemStack escrow) {
    }

    public static class Requirement {
        public BountyType type = BountyType.ITEM_DELIVERY;
        public String itemId = "";
        public int itemCount = 0;
        public String entityId = "";
        public int entityCount = 0;
        public String manualText = "";
    }

    public static class Bounty {
        public String id = "";
        public String title = "";
        public String description = "";
        public BountyType type = BountyType.ITEM_DELIVERY;
        public BountyStatus status = BountyStatus.AVAILABLE;
        public boolean system = true;
        public String issuerUuid = "";
        public String accepterUuid = "";
        public String pendingReviewerUuid = "";
        public Requirement requirement = new Requirement();
        public Reward reward = new Reward();
        public int progress = 0;
        public int target = 0;
    }

    public record BountyView(
        String id,
        String title,
        String description,
        String type,
        String requirementText,
        String rewardText,
        String iconItemId,
        boolean canAccept,
        boolean canReview,
        String reviewAccepterName
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyView> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), BountyView::id,
            ByteBufCodecs.stringUtf8(64), BountyView::title,
            ByteBufCodecs.stringUtf8(256), BountyView::description,
            ByteBufCodecs.stringUtf8(32), BountyView::type,
            ByteBufCodecs.stringUtf8(256), BountyView::requirementText,
            ByteBufCodecs.stringUtf8(256), BountyView::rewardText,
            ByteBufCodecs.stringUtf8(64), BountyView::iconItemId,
            ByteBufCodecs.BOOL, BountyView::canAccept,
            ByteBufCodecs.BOOL, BountyView::canReview,
            ByteBufCodecs.stringUtf8(64), BountyView::reviewAccepterName,
            BountyView::new
        );
    }

    public record BountyBoardOpenPayload(List<BountyView> available, List<BountyView> review, List<String> refreshTimes, boolean isOp) implements CustomPacketPayload {
        public static final Type<BountyBoardOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("novus_items", "bounty_board_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyBoardOpenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, BountyView.CODEC), BountyBoardOpenPayload::available,
            ByteBufCodecs.collection(ArrayList::new, BountyView.CODEC), BountyBoardOpenPayload::review,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(16)), BountyBoardOpenPayload::refreshTimes,
            ByteBufCodecs.BOOL, BountyBoardOpenPayload::isOp,
            BountyBoardOpenPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BountyBoardSyncPayload(List<BountyView> available, List<BountyView> review, List<String> refreshTimes, boolean isOp) implements CustomPacketPayload {
        public static final Type<BountyBoardSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("novus_items", "bounty_board_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyBoardSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, BountyView.CODEC), BountyBoardSyncPayload::available,
            ByteBufCodecs.collection(ArrayList::new, BountyView.CODEC), BountyBoardSyncPayload::review,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.stringUtf8(16)), BountyBoardSyncPayload::refreshTimes,
            ByteBufCodecs.BOOL, BountyBoardSyncPayload::isOp,
            BountyBoardSyncPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public enum ActionType {
        REQUEST_SYNC,
        ACCEPT,
        PUBLISH,
        SUBMIT_MANUAL,
        REVIEW_APPROVE,
        REVIEW_DENY,
        ADD_REFRESH_TIME,
        REMOVE_REFRESH_TIME,
        REFRESH_NOW
    }

    public enum SubmitResult {
        NONE,
        PENDING_REVIEW,
        COMPLETED
    }

    public record BountyBoardActionPayload(ActionType action, String id, String title, String description, String bountyType, String requirementText, String rewardType, String rewardText) implements CustomPacketPayload {
        public static final Type<BountyBoardActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("novus_items", "bounty_board_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyBoardActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, payload -> payload.action.ordinal(),
            ByteBufCodecs.stringUtf8(64), BountyBoardActionPayload::id,
            ByteBufCodecs.stringUtf8(64), BountyBoardActionPayload::title,
            ByteBufCodecs.stringUtf8(256), BountyBoardActionPayload::description,
            ByteBufCodecs.stringUtf8(32), BountyBoardActionPayload::bountyType,
            ByteBufCodecs.stringUtf8(256), BountyBoardActionPayload::requirementText,
            ByteBufCodecs.stringUtf8(32), BountyBoardActionPayload::rewardType,
            ByteBufCodecs.stringUtf8(256), BountyBoardActionPayload::rewardText,
            (actionOrdinal, id, title, description, bountyType, requirementText, rewardType, rewardText) -> new BountyBoardActionPayload(ActionType.values()[actionOrdinal], id, title, description, bountyType, requirementText, rewardType, rewardText)
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerNetworking() {
        PayloadTypeRegistry.playS2C().register(BountyBoardOpenPayload.TYPE, BountyBoardOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BountyBoardSyncPayload.TYPE, BountyBoardSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BountyBoardActionPayload.TYPE, BountyBoardActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BountyBoardActionPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> handleAction(context.player(), payload));
        });
    }

    public static void registerEvents() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((server, attacker, target) -> {
            if (!(attacker instanceof ServerPlayer player)) {
                return;
            }
            onPlayerKill(player, target.getType());
        });
    }

    public static void tick(MinecraftServer server) {
        tickCounter++;
        if (dataFile == null) {
            dataFile = server.getWorldPath(LevelResource.ROOT).resolve("novus_bounty_board.json");
            loadData();
            if (refreshTimes.isEmpty()) {
                refreshTimes.add("12:00");
                saveData();
            }
        }

        if (tickCounter % CHECK_INTERVAL == 0) {
            maybeRefreshSystemBounties(server);
        }

        if (tickCounter % SAVE_INTERVAL == 0) {
            saveData();
        }
    }

    public static void openBoard(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, BountyBoardOpenPayload.TYPE)) {
            player.sendSystemMessage(Component.literal("§c✦ 你的客户端未安装悬赏板组件，无法打开。 §c✦"));
            return;
        }
        var payload = new BountyBoardOpenPayload(buildAvailableViews(player), buildReviewViews(player), new ArrayList<>(refreshTimes), player.hasPermission(2));
        ServerPlayNetworking.send(player, payload);
    }

    public static void registerCommands(CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(net.minecraft.commands.Commands.literal("bountyboard")
            .requires(source -> source.hasPermission(2))
            .then(net.minecraft.commands.Commands.literal("time")
                .then(net.minecraft.commands.Commands.literal("list")
                    .executes(BountyBoardManager::listRefreshTimes))
                .then(net.minecraft.commands.Commands.literal("add")
                    .then(net.minecraft.commands.Commands.argument("time", StringArgumentType.word())
                        .executes(BountyBoardManager::addRefreshTime)))
                .then(net.minecraft.commands.Commands.literal("remove")
                    .then(net.minecraft.commands.Commands.argument("time", StringArgumentType.word())
                        .executes(BountyBoardManager::removeRefreshTime))))
            .then(net.minecraft.commands.Commands.literal("refreshnow")
                .executes(BountyBoardManager::refreshNow)));
    }

    private static int listRefreshTimes(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("刷新时间: " + String.join(", ", refreshTimes)), false);
        return 1;
    }

    private static int addRefreshTime(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        String time = StringArgumentType.getString(context, "time");
        if (!isValidTime(time)) {
            context.getSource().sendFailure(Component.literal("时间格式错误，请使用 HH:mm"));
            return 0;
        }
        if (!refreshTimes.contains(time)) {
            refreshTimes.add(time);
            refreshTimes.sort(Comparator.naturalOrder());
            saveData();
        }
        context.getSource().sendSuccess(() -> Component.literal("已添加刷新时间: " + time), false);
        return 1;
    }

    private static int removeRefreshTime(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        String time = StringArgumentType.getString(context, "time");
        refreshTimes.remove(time);
        lastRefreshDateByTime.remove(time);
        saveData();
        context.getSource().sendSuccess(() -> Component.literal("已移除刷新时间: " + time), false);
        return 1;
    }

    private static int refreshNow(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        generateSystemBounties(server, server.overworld().getRandom());
        broadcastSync(server);
        context.getSource().sendSuccess(() -> Component.literal("已立即刷新悬赏"), false);
        return 1;
    }

    private static boolean isValidTime(String time) {
        try {
            LocalTime.parse(time, TIME_FORMAT);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static void maybeRefreshSystemBounties(MinecraftServer server) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalTime now = LocalTime.now(ZoneId.systemDefault()).withSecond(0).withNano(0);
        String todayStr = today.toString();
        String nowStr = now.format(TIME_FORMAT);
        if (!refreshTimes.contains(nowStr)) {
            return;
        }
        String lastDate = lastRefreshDateByTime.getOrDefault(nowStr, "");
        if (Objects.equals(lastDate, todayStr)) {
            return;
        }
        lastRefreshDateByTime.put(nowStr, todayStr);
        generateSystemBounties(server, server.overworld().getRandom());
        saveData();
        broadcastSync(server);
    }

    private static void generateSystemBounties(MinecraftServer server, RandomSource random) {
        Set<String> keep = new HashSet<>();
        for (Bounty bounty : bountiesById.values()) {
            if (!bounty.system) {
                keep.add(bounty.id);
            } else if (bounty.status != BountyStatus.AVAILABLE) {
                keep.add(bounty.id);
            }
        }
        bountiesById.keySet().retainAll(keep);

        int count = 6;
        for (int i = 0; i < count; i++) {
            Bounty bounty = createRandomSystemBounty(server, random);
            bountiesById.put(bounty.id, bounty);
        }
        saveData();
    }

    private static Bounty createRandomSystemBounty(MinecraftServer server, RandomSource random) {
        int kind = random.nextInt(3);
        if (kind == 0) {
            return createSystemKillBounty(random);
        }
        if (kind == 1) {
            return createSystemDeliveryBounty(random, true);
        }
        return createSystemDeliveryBounty(random, false);
    }

    private static Bounty createSystemKillBounty(RandomSource random) {
        List<ResourceLocation> mobs = List.of(
            ResourceLocation.fromNamespaceAndPath("minecraft", "zombie"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "skeleton"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "creeper"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "spider"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "enderman")
        );
        ResourceLocation id = mobs.get(random.nextInt(mobs.size()));
        int target = 4 + random.nextInt(7);

        Bounty bounty = new Bounty();
        bounty.id = UUID.randomUUID().toString();
        bounty.system = true;
        bounty.status = BountyStatus.AVAILABLE;
        bounty.type = BountyType.ENTITY_KILL;
        bounty.title = "清理路况";
        bounty.description = "路上全是拦路虎，清一清再开车。";
        bounty.requirement.type = BountyType.ENTITY_KILL;
        bounty.requirement.entityId = id.toString();
        bounty.requirement.entityCount = target;
        bounty.progress = 0;
        bounty.target = target;
        bounty.reward = createCoinReward(random);
        return bounty;
    }

    private static Bounty createSystemDeliveryBounty(RandomSource random, boolean crops) {
        List<ResourceLocation> items = crops
            ? List.of(
                ResourceLocation.fromNamespaceAndPath("minecraft", "wheat"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "carrot"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "potato"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "beetroot")
            )
            : List.of(
                ResourceLocation.fromNamespaceAndPath("minecraft", "cobblestone"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "stone"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "oak_planks"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "bricks")
            );

        ResourceLocation itemId = items.get(random.nextInt(items.size()));
        int target = crops ? (16 + random.nextInt(33)) : (32 + random.nextInt(65));

        Bounty bounty = new Bounty();
        bounty.id = UUID.randomUUID().toString();
        bounty.system = true;
        bounty.status = BountyStatus.AVAILABLE;
        bounty.type = BountyType.ITEM_DELIVERY;
        bounty.title = crops ? "补给运单" : "建材急单";
        bounty.description = crops ? "缺货了，快把食材送来！" : "工地停工了，材料要跟上。";
        bounty.requirement.type = BountyType.ITEM_DELIVERY;
        bounty.requirement.itemId = itemId.toString();
        bounty.requirement.itemCount = target;
        bounty.progress = 0;
        bounty.target = target;
        bounty.reward = createCoinReward(random);
        return bounty;
    }

    private static Reward createCoinReward(RandomSource random) {
        int bronze = 6 + random.nextInt(10);
        Reward reward = new Reward();
        reward.type = RewardType.ITEM;
        reward.itemId = "novus_items:bronze_novus_coin";
        reward.itemCount = bronze;
        reward.itemCustomDataNbt = "";
        return reward;
    }

    private static void handleAction(ServerPlayer player, BountyBoardActionPayload payload) {
        switch (payload.action) {
            case REQUEST_SYNC -> sendSync(player);
            case ACCEPT -> accept(player, payload.id);
            case PUBLISH -> publish(player, payload);
            case SUBMIT_MANUAL -> submitManual(player, payload.id);
            case REVIEW_APPROVE -> reviewApprove(player, payload.id);
            case REVIEW_DENY -> reviewDeny(player, payload.id);
            case ADD_REFRESH_TIME -> addRefreshTimeGui(player, payload.id);
            case REMOVE_REFRESH_TIME -> removeRefreshTimeGui(player, payload.id);
            case REFRESH_NOW -> refreshNowGui(player);
        }
    }

    private static void addRefreshTimeGui(ServerPlayer player, String time) {
        if (!player.hasPermission(2)) {
            return;
        }
        if (!isValidTime(time)) {
            player.sendSystemMessage(Component.literal("§c✦ 时间格式错误，请使用 HH:mm §c✦"));
            return;
        }
        if (!refreshTimes.contains(time)) {
            refreshTimes.add(time);
            refreshTimes.sort(Comparator.naturalOrder());
            saveData();
            broadcastSync(player.server);
        }
        player.sendSystemMessage(Component.literal("§a✦ 已添加刷新时间: " + time + " §a✦"));
    }

    private static void removeRefreshTimeGui(ServerPlayer player, String time) {
        if (!player.hasPermission(2)) {
            return;
        }
        if (time == null || time.isEmpty()) {
            return;
        }
        if (refreshTimes.remove(time)) {
            lastRefreshDateByTime.remove(time);
            saveData();
            broadcastSync(player.server);
        }
        player.sendSystemMessage(Component.literal("§e✦ 已移除刷新时间: " + time + " §e✦"));
    }

    private static void refreshNowGui(ServerPlayer player) {
        if (!player.hasPermission(2)) {
            return;
        }
        generateSystemBounties(player.server, player.server.overworld().getRandom());
        broadcastSync(player.server);
        player.sendSystemMessage(Component.literal("§a✦ 已立即刷新悬赏 §a✦"));
    }

    private static void sendSync(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, BountyBoardSyncPayload.TYPE)) {
            return;
        }
        ServerPlayNetworking.send(player, new BountyBoardSyncPayload(buildAvailableViews(player), buildReviewViews(player), new ArrayList<>(refreshTimes), player.hasPermission(2)));
    }

    private static void broadcastSync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSync(player);
        }
    }

    private static void accept(ServerPlayer player, String bountyId) {
        if (bountyId == null || bountyId.isEmpty()) {
            return;
        }
        if (activeBountyByPlayer.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§c✦ 你已经接取了一个悬赏，先完成再来。 §c✦"));
            return;
        }
        Bounty bounty = bountiesById.get(bountyId);
        if (bounty == null || bounty.status != BountyStatus.AVAILABLE) {
            player.sendSystemMessage(Component.literal("§c✦ 这个悬赏已被别人抢走了。 §c✦"));
            return;
        }
        bounty.status = BountyStatus.ACCEPTED;
        bounty.accepterUuid = player.getUUID().toString();
        bounty.progress = 0;
        bounty.target = getTarget(bounty);
        activeBountyByPlayer.put(player.getUUID(), bounty.id);
        saveData();

        ItemStack scroll = BountyScrollItem.createScroll(player.registryAccess(), bounty);
        if (!player.getInventory().add(scroll)) {
            player.drop(scroll, false);
        }
        player.sendSystemMessage(Component.literal("§a✦ 已接取悬赏，并获得任务卷轴。 §a✦"));
        broadcastSync(player.server);
    }

    private static int getTarget(Bounty bounty) {
        if (bounty.type == BountyType.ITEM_DELIVERY) {
            return Math.max(1, bounty.requirement.itemCount);
        }
        if (bounty.type == BountyType.ENTITY_KILL) {
            return Math.max(1, bounty.requirement.entityCount);
        }
        return 1;
    }

    private static void publish(ServerPlayer player, BountyBoardActionPayload payload) {
        boolean isOp = player.hasPermission(2);
        BountyType type = parseBountyType(payload.bountyType);
        if (type == null) {
            player.sendSystemMessage(Component.literal("§c✦ 悬赏类型错误。 §c✦"));
            return;
        }
        String title = safeText(payload.title, 32);
        String description = safeText(payload.description, 200);
        if (title.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c✦ 标题不能为空。 §c✦"));
            return;
        }

        Requirement requirement = parseRequirement(player, type, payload.requirementText);
        if (requirement == null) {
            return;
        }

        ParsedReward parsedReward = parseReward(player, payload.rewardType, payload.rewardText, isOp);
        if (parsedReward == null) {
            return;
        }

        if (parsedReward.escrow != null && !parsedReward.escrow.isEmpty()) {
            if (!InventoryHelper.hasItems(player, parsedReward.escrow, parsedReward.escrow.getCount())) {
                player.sendSystemMessage(Component.literal("§c✦ 你的背包物品不足以作为奖励，发布失败。 §c✦"));
                return;
            }
            InventoryHelper.consumeItems(player, parsedReward.escrow, parsedReward.escrow.getCount());
        }

        Bounty bounty = new Bounty();
        bounty.id = UUID.randomUUID().toString();
        bounty.system = false;
        bounty.status = BountyStatus.AVAILABLE;
        bounty.type = type;
        bounty.title = title;
        bounty.description = description;
        bounty.issuerUuid = player.getUUID().toString();
        bounty.requirement = requirement;
        bounty.reward = parsedReward.reward;
        bounty.progress = 0;
        bounty.target = getTarget(bounty);

        bountiesById.put(bounty.id, bounty);
        saveData();
        player.sendSystemMessage(Component.literal("§a✦ 悬赏已发布，上板开抢。 §a✦"));
        broadcastSync(player.server);
    }

    private static ParsedReward parseReward(ServerPlayer player, String rewardType, String rewardText, boolean isOp) {
        RewardType type = "command".equalsIgnoreCase(rewardType) ? RewardType.COMMAND : RewardType.ITEM;
        if (type == RewardType.COMMAND) {
            if (!isOp) {
                player.sendSystemMessage(Component.literal("§c✦ 只有管理员可以发布指令奖励。 §c✦"));
                return null;
            }
            String cmd = safeText(rewardText, 200);
            if (cmd.isEmpty()) {
                player.sendSystemMessage(Component.literal("§c✦ 指令不能为空。 §c✦"));
                return null;
            }
            Reward reward = new Reward();
            reward.type = RewardType.COMMAND;
            reward.command = cmd;
            reward.itemId = "";
            reward.itemCount = 0;
            reward.itemCustomDataNbt = "";
            return new ParsedReward(reward, ItemStack.EMPTY);
        }

        int count = parsePositiveIntOrDefault(rewardText, 1);
        ItemStack inHand = player.getMainHandItem();
        if (inHand.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c✦ 请把奖励物品拿在主手。 §c✦"));
            return null;
        }
        if (count <= 0) {
            player.sendSystemMessage(Component.literal("§c✦ 奖励数量必须大于 0。 §c✦"));
            return null;
        }
        ItemStack escrow = inHand.copy();
        escrow.setCount(count);

        Reward reward = new Reward();
        reward.type = RewardType.ITEM;
        reward.itemId = BuiltInRegistries.ITEM.getKey(inHand.getItem()).toString();
        reward.itemCount = count;
        reward.itemCustomDataNbt = "";
        var customData = inHand.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null) {
            reward.itemCustomDataNbt = customData.copyTag().toString();
        }
        reward.command = "";
        return new ParsedReward(reward, escrow);
    }

    private static Requirement parseRequirement(ServerPlayer player, BountyType type, String requirementText) {
        Requirement req = new Requirement();
        req.type = type;
        if (type == BountyType.ITEM_DELIVERY) {
            int count = parsePositiveIntOrDefault(requirementText, 1);
            ItemStack inHand = player.getOffhandItem();
            if (inHand.isEmpty()) {
                player.sendSystemMessage(Component.literal("§c✦ 请把要提交的目标物品拿在副手。 §c✦"));
                return null;
            }
            if (count <= 0) {
                player.sendSystemMessage(Component.literal("§c✦ 目标数量必须大于 0。 §c✦"));
                return null;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(inHand.getItem());
            req.itemId = id.toString();
            req.itemCount = count;
            return req;
        }
        if (type == BountyType.ENTITY_KILL) {
            String[] parts = requirementText.trim().split("\\s+");
            if (parts.length < 2) {
                player.sendSystemMessage(Component.literal("§c✦ 击杀目标格式: <实体ID> <数量>。 §c✦"));
                return null;
            }
            ResourceLocation entityId = ResourceLocation.tryParse(parts[0]);
            int count = parsePositiveIntOrDefault(parts[1], 1);
            if (entityId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
                player.sendSystemMessage(Component.literal("§c✦ 未知实体ID: " + parts[0] + " §c✦"));
                return null;
            }
            req.entityId = entityId.toString();
            req.entityCount = Math.max(1, count);
            return req;
        }
        String text = safeText(requirementText, 200);
        if (text.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c✦ 自定义悬赏需要填写要求内容。 §c✦"));
            return null;
        }
        req.manualText = text;
        return req;
    }

    private static void submitManual(ServerPlayer player, String bountyId) {
        Bounty bounty = bountiesById.get(bountyId);
        if (bounty == null) {
            player.sendSystemMessage(Component.literal("§c✦ 悬赏不存在。 §c✦"));
            return;
        }
        if (bounty.type != BountyType.CUSTOM_MANUAL) {
            player.sendSystemMessage(Component.literal("§c✦ 只有自定义悬赏需要手动提交。 §c✦"));
            return;
        }
        if (!Objects.equals(bounty.accepterUuid, player.getUUID().toString())) {
            player.sendSystemMessage(Component.literal("§c✦ 你不是这个悬赏的接取者。 §c✦"));
            return;
        }
        bounty.status = BountyStatus.PENDING_REVIEW;
        bounty.pendingReviewerUuid = bounty.issuerUuid;
        saveData();
        notifyIssuer(player.server, bounty, player);
        broadcastSync(player.server);
        player.sendSystemMessage(Component.literal("§e✦ 已提交给发布者验收，请等待。 §e✦"));
    }

    private static void reviewApprove(ServerPlayer player, String bountyId) {
        Bounty bounty = bountiesById.get(bountyId);
        if (bounty == null || bounty.status != BountyStatus.PENDING_REVIEW) {
            return;
        }
        if (!Objects.equals(bounty.issuerUuid, player.getUUID().toString())) {
            player.sendSystemMessage(Component.literal("§c✦ 你不是这个悬赏的发布者。 §c✦"));
            return;
        }
        ServerPlayer accepter = getPlayerByUuid(player.server, bounty.accepterUuid);
        if (accepter == null) {
            player.sendSystemMessage(Component.literal("§c✦ 接取者不在线，无法发放奖励。 §c✦"));
            return;
        }
        complete(accepter, bounty, true);
        player.sendSystemMessage(Component.literal("§a✦ 已验收并发放奖励。 §a✦"));
        accepter.sendSystemMessage(Component.literal("§a✦ 你的悬赏已被验收，奖励已发放。 §a✦"));
        broadcastSync(player.server);
    }

    private static void reviewDeny(ServerPlayer player, String bountyId) {
        Bounty bounty = bountiesById.get(bountyId);
        if (bounty == null || bounty.status != BountyStatus.PENDING_REVIEW) {
            return;
        }
        if (!Objects.equals(bounty.issuerUuid, player.getUUID().toString())) {
            player.sendSystemMessage(Component.literal("§c✦ 你不是这个悬赏的发布者。 §c✦"));
            return;
        }
        bounty.status = BountyStatus.ACCEPTED;
        bounty.pendingReviewerUuid = "";
        saveData();
        ServerPlayer accepter = getPlayerByUuid(player.server, bounty.accepterUuid);
        if (accepter != null) {
            accepter.sendSystemMessage(Component.literal("§c✦ 你的悬赏提交被拒绝，可再次提交。 §c✦"));
        }
        player.sendSystemMessage(Component.literal("§e✦ 已拒绝验收。 §e✦"));
        broadcastSync(player.server);
    }

    private static void onPlayerKill(ServerPlayer player, EntityType<?> type) {
        String bountyId = activeBountyByPlayer.get(player.getUUID());
        if (bountyId == null) {
            return;
        }
        Bounty bounty = bountiesById.get(bountyId);
        if (bounty == null || bounty.status != BountyStatus.ACCEPTED || bounty.type != BountyType.ENTITY_KILL) {
            return;
        }
        ResourceLocation killedId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (killedId == null) {
            return;
        }
        if (!Objects.equals(bounty.requirement.entityId, killedId.toString())) {
            return;
        }
        bounty.progress = Math.min(bounty.target, bounty.progress + 1);
        saveData();
    }

    public static SubmitResult trySubmitScroll(ServerPlayer player, String bountyId) {
        if (bountyId == null || bountyId.isEmpty()) {
            return SubmitResult.NONE;
        }
        Bounty bounty = bountiesById.get(bountyId);
        if (bounty == null) {
            player.sendSystemMessage(Component.literal("§c✦ 这个悬赏已过期。 §c✦"));
            return SubmitResult.NONE;
        }
        if (!Objects.equals(bounty.accepterUuid, player.getUUID().toString())) {
            player.sendSystemMessage(Component.literal("§c✦ 你不是这个悬赏的接取者。 §c✦"));
            return SubmitResult.NONE;
        }
        if (bounty.status == BountyStatus.PENDING_REVIEW) {
            player.sendSystemMessage(Component.literal("§e✦ 正在等待发布者验收。 §e✦"));
            return SubmitResult.NONE;
        }
        if (bounty.status != BountyStatus.ACCEPTED) {
            player.sendSystemMessage(Component.literal("§c✦ 悬赏状态异常。 §c✦"));
            return SubmitResult.NONE;
        }

        if (bounty.type == BountyType.CUSTOM_MANUAL) {
            bounty.status = BountyStatus.PENDING_REVIEW;
            bounty.pendingReviewerUuid = bounty.issuerUuid;
            saveData();
            notifyIssuer(player.server, bounty, player);
            broadcastSync(player.server);
            player.sendSystemMessage(Component.literal("§e✦ 已提交给发布者验收，请等待。 §e✦"));
            return SubmitResult.PENDING_REVIEW;
        }

        if (bounty.type == BountyType.ENTITY_KILL) {
            if (bounty.progress < bounty.target) {
                player.sendSystemMessage(Component.literal("§c✦ 还没完成： " + bounty.progress + "/" + bounty.target + " §c✦"));
                return SubmitResult.NONE;
            }
            complete(player, bounty, false);
            broadcastSync(player.server);
            return SubmitResult.COMPLETED;
        }

        if (bounty.type == BountyType.ITEM_DELIVERY) {
            ResourceLocation itemId = ResourceLocation.tryParse(bounty.requirement.itemId);
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                player.sendSystemMessage(Component.literal("§c✦ 目标物品无效。 §c✦"));
                return SubmitResult.NONE;
            }
            int need = Math.max(1, bounty.requirement.itemCount);
            ItemStack template = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
            if (!InventoryHelper.hasItems(player, template, need)) {
                player.sendSystemMessage(Component.literal("§c✦ 物品不足，无法提交。 §c✦"));
                return SubmitResult.NONE;
            }
            InventoryHelper.consumeItems(player, template, need);
            complete(player, bounty, false);
            broadcastSync(player.server);
            return SubmitResult.COMPLETED;
        }

        return SubmitResult.NONE;
    }

    private static void complete(ServerPlayer player, Bounty bounty, boolean manualReviewed) {
        grantReward(player, bounty.reward);
        bounty.status = BountyStatus.COMPLETED;
        activeBountyByPlayer.remove(player.getUUID());
        bountiesById.remove(bounty.id);
        saveData();
        player.sendSystemMessage(Component.literal("§a✦ 悬赏完成，奖励已发放！ §a✦"));
    }

    private static void grantReward(ServerPlayer player, Reward reward) {
        if (reward == null) {
            return;
        }
        if (reward.type == RewardType.COMMAND) {
            String cmd = reward.command.replace("{player}", player.getGameProfile().getName());
            player.server.getCommands().performPrefixedCommand(player.server.createCommandSourceStack().withSuppressedOutput().withPermission(4), cmd);
            return;
        }
        ResourceLocation itemId = ResourceLocation.tryParse(reward.itemId);
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return;
        }
        int count = Math.max(1, reward.itemCount);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId), count);
        if (reward.itemCustomDataNbt != null && !reward.itemCustomDataNbt.isEmpty()) {
            try {
                var tag = net.minecraft.nbt.TagParser.parseTag(reward.itemCustomDataNbt);
                if (tag instanceof net.minecraft.nbt.CompoundTag compoundTag) {
                    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(compoundTag));
                }
            } catch (net.minecraft.nbt.TagParser.TagParseException ignored) {
            }
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void notifyIssuer(MinecraftServer server, Bounty bounty, ServerPlayer submitter) {
        if (bounty.issuerUuid == null || bounty.issuerUuid.isEmpty()) {
            return;
        }
        ServerPlayer issuer = getPlayerByUuid(server, bounty.issuerUuid);
        if (issuer == null) {
            return;
        }
        issuer.sendSystemMessage(Component.literal("§e✦ 你发布的悬赏有人提交验收：" + submitter.getGameProfile().getName() + " §e✦"));
    }

    private static ServerPlayer getPlayerByUuid(MinecraftServer server, String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            return server.getPlayerList().getPlayer(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<BountyView> buildAvailableViews(ServerPlayer player) {
        List<BountyView> views = new ArrayList<>();
        boolean hasActive = activeBountyByPlayer.containsKey(player.getUUID());
        for (Bounty bounty : bountiesById.values()) {
            if (bounty.status != BountyStatus.AVAILABLE) {
                continue;
            }
            boolean canAccept = !hasActive;
            String icon = iconItemIdFor(bounty);
            views.add(new BountyView(
                bounty.id,
                bounty.title,
                bounty.description,
                bounty.type.name(),
                requirementText(bounty),
                rewardText(bounty),
                icon,
                canAccept,
                false,
                ""
            ));
        }
        views.sort(Comparator.comparing(BountyView::title));
        return views;
    }

    private static List<BountyView> buildReviewViews(ServerPlayer player) {
        List<BountyView> views = new ArrayList<>();
        String uuid = player.getUUID().toString();
        for (Bounty bounty : bountiesById.values()) {
            if (bounty.status != BountyStatus.PENDING_REVIEW) {
                continue;
            }
            if (!Objects.equals(bounty.issuerUuid, uuid)) {
                continue;
            }
            String accepterName = Optional.ofNullable(getPlayerByUuid(player.server, bounty.accepterUuid))
                .map(p -> p.getGameProfile().getName())
                .orElse("离线玩家");
            views.add(new BountyView(
                bounty.id,
                bounty.title,
                bounty.description,
                bounty.type.name(),
                requirementText(bounty),
                rewardText(bounty),
                iconItemIdFor(bounty),
                false,
                true,
                accepterName
            ));
        }
        views.sort(Comparator.comparing(BountyView::title));
        return views;
    }

    private static String iconItemIdFor(Bounty bounty) {
        if (bounty.type == BountyType.ITEM_DELIVERY) {
            return bounty.requirement.itemId == null || bounty.requirement.itemId.isEmpty() ? "minecraft:chest" : bounty.requirement.itemId;
        }
        if (bounty.type == BountyType.ENTITY_KILL) {
            return "minecraft:iron_sword";
        }
        return "minecraft:paper";
    }

    public static String requirementText(Bounty bounty) {
        if (bounty.type == BountyType.ITEM_DELIVERY) {
            return bounty.requirement.itemId + " x" + bounty.requirement.itemCount;
        }
        if (bounty.type == BountyType.ENTITY_KILL) {
            return bounty.requirement.entityId + " x" + bounty.requirement.entityCount + " (" + bounty.progress + "/" + bounty.target + ")";
        }
        return bounty.requirement.manualText;
    }

    public static String rewardText(Bounty bounty) {
        if (bounty.reward == null) {
            return "";
        }
        if (bounty.reward.type == RewardType.COMMAND) {
            return "cmd: " + bounty.reward.command;
        }
        if (bounty.reward.itemId == null || bounty.reward.itemId.isEmpty()) {
            return "";
        }
        int count = Math.max(1, bounty.reward.itemCount);
        return bounty.reward.itemId + " x" + count;
    }

    private static BountyType parseBountyType(String bountyType) {
        if (bountyType == null) return null;
        return switch (bountyType.toUpperCase(Locale.ROOT)) {
            case "ITEM_DELIVERY", "ITEM" -> BountyType.ITEM_DELIVERY;
            case "ENTITY_KILL", "KILL" -> BountyType.ENTITY_KILL;
            case "CUSTOM_MANUAL", "CUSTOM" -> BountyType.CUSTOM_MANUAL;
            default -> null;
        };
    }

    private static String safeText(String value, int maxLen) {
        if (value == null) return "";
        String s = value.trim();
        if (s.length() > maxLen) {
            return s.substring(0, maxLen);
        }
        return s;
    }

    private static int parsePositiveIntOrDefault(String value, int def) {
        if (value == null) return def;
        try {
            int i = Integer.parseInt(value.trim());
            return Math.max(1, i);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void saveData() {
        if (dataFile == null) return;
        try {
            DataFile data = new DataFile();
            data.bounties = new HashMap<>(bountiesById);
            data.activeBountyByPlayer = new HashMap<>();
            for (Map.Entry<UUID, String> entry : activeBountyByPlayer.entrySet()) {
                data.activeBountyByPlayer.put(entry.getKey().toString(), entry.getValue());
            }
            data.refreshTimes = new ArrayList<>(refreshTimes);
            data.lastRefreshDateByTime = new HashMap<>(lastRefreshDateByTime);
            Files.writeString(dataFile, GSON.toJson(data));
        } catch (IOException e) {
        }
    }

    private static void loadData() {
        if (dataFile == null || !Files.exists(dataFile)) return;
        try {
            String json = Files.readString(dataFile);
            DataFile data = GSON.fromJson(json, new TypeToken<DataFile>() {}.getType());
            if (data == null) return;
            bountiesById.clear();
            if (data.bounties != null) {
                bountiesById.putAll(data.bounties);
            }
            activeBountyByPlayer.clear();
            if (data.activeBountyByPlayer != null) {
                for (Map.Entry<String, String> entry : data.activeBountyByPlayer.entrySet()) {
                    try {
                        activeBountyByPlayer.put(UUID.fromString(entry.getKey()), entry.getValue());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            refreshTimes.clear();
            if (data.refreshTimes != null) {
                for (String time : data.refreshTimes) {
                    if (isValidTime(time)) {
                        refreshTimes.add(time);
                    }
                }
                refreshTimes.sort(Comparator.naturalOrder());
            }
            lastRefreshDateByTime.clear();
            if (data.lastRefreshDateByTime != null) {
                lastRefreshDateByTime.putAll(data.lastRefreshDateByTime);
            }
        } catch (IOException ignored) {
        }
    }

    private static class DataFile {
        public Map<String, Bounty> bounties = new HashMap<>();
        public Map<String, String> activeBountyByPlayer = new HashMap<>();
        public List<String> refreshTimes = new ArrayList<>();
        public Map<String, String> lastRefreshDateByTime = new HashMap<>();
    }

    public static class InventoryHelper {
        public static boolean hasItems(Player player, ItemStack template, int count) {
            int found = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.isEmpty()) continue;
                if (!ItemStack.isSameItemSameComponents(stack, template)) continue;
                found += stack.getCount();
                if (found >= count) return true;
            }
            return false;
        }

        public static void consumeItems(Player player, ItemStack template, int count) {
            int remaining = count;
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                ItemStack stack = player.getInventory().items.get(i);
                if (stack.isEmpty()) continue;
                if (!ItemStack.isSameItemSameComponents(stack, template)) continue;
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (remaining <= 0) {
                    return;
                }
            }
        }
    }

}
