package com.novus.items.bounty;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.arguments.EntityArgument;
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
    private static final Map<UUID, Set<String>> activeBountiesByPlayer = new ConcurrentHashMap<>();
    private static final List<String> refreshTimes = new ArrayList<>();
    private static final Map<String, String> lastRefreshDateByTime = new ConcurrentHashMap<>();
    private static int systemRefreshAddCount = 3;
    private static int systemMaxAvailable = 12;
    private static int playerMaxActive = 1;
    private static final List<String> systemTaskPool = new ArrayList<>();
    private static final Map<String, List<Reward>> pendingRefundsByIssuerUuid = new ConcurrentHashMap<>();

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
        public String issuerName = "";
        public String accepterUuid = "";
        public String accepterName = "";
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
        String requirementId,
        int requirementCount,
        int progress,
        int target,
        String rewardItemId,
        int rewardItemCount,
        boolean rewardIsCommand,
        boolean system,
        String issuerName,
        String status,
        String accepterName,
        boolean canAccept,
        boolean mineAccepted,
        boolean mineIssued,
        boolean canSubmit,
        boolean canReview,
        boolean canAbandon,
        boolean canCancel
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyView> CODEC = new StreamCodec<>() {
            @Override
            public BountyView decode(RegistryFriendlyByteBuf buf) {
                String id = buf.readUtf(64);
                String title = buf.readUtf(64);
                String description = buf.readUtf(256);
                String type = buf.readUtf(32);
                String requirementId = buf.readUtf(128);
                int requirementCount = buf.readVarInt();
                int progress = buf.readVarInt();
                int target = buf.readVarInt();
                String rewardItemId = buf.readUtf(128);
                int rewardItemCount = buf.readVarInt();
                boolean rewardIsCommand = buf.readBoolean();
                boolean system = buf.readBoolean();
                String issuerName = buf.readUtf(32);
                String status = buf.readUtf(16);
                String accepterName = buf.readUtf(64);
                boolean canAccept = buf.readBoolean();
                boolean mineAccepted = buf.readBoolean();
                boolean mineIssued = buf.readBoolean();
                boolean canSubmit = buf.readBoolean();
                boolean canReview = buf.readBoolean();
                boolean canAbandon = buf.readBoolean();
                boolean canCancel = buf.readBoolean();
                return new BountyView(id, title, description, type, requirementId, requirementCount, progress, target, rewardItemId, rewardItemCount, rewardIsCommand, system, issuerName, status, accepterName, canAccept, mineAccepted, mineIssued, canSubmit, canReview, canAbandon, canCancel);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, BountyView value) {
                buf.writeUtf(value.id, 64);
                buf.writeUtf(value.title, 64);
                buf.writeUtf(value.description, 256);
                buf.writeUtf(value.type, 32);
                buf.writeUtf(value.requirementId, 128);
                buf.writeVarInt(value.requirementCount);
                buf.writeVarInt(value.progress);
                buf.writeVarInt(value.target);
                buf.writeUtf(value.rewardItemId, 128);
                buf.writeVarInt(value.rewardItemCount);
                buf.writeBoolean(value.rewardIsCommand);
                buf.writeBoolean(value.system);
                buf.writeUtf(value.issuerName, 32);
                buf.writeUtf(value.status, 16);
                buf.writeUtf(value.accepterName, 64);
                buf.writeBoolean(value.canAccept);
                buf.writeBoolean(value.mineAccepted);
                buf.writeBoolean(value.mineIssued);
                buf.writeBoolean(value.canSubmit);
                buf.writeBoolean(value.canReview);
                buf.writeBoolean(value.canAbandon);
                buf.writeBoolean(value.canCancel);
            }
        };
    }

    public record BountyBoardOpenPayload(List<BountyView> available, List<BountyView> my, List<String> refreshTimes, boolean isOp, int systemRefreshAddCount, int playerMaxActive, List<String> taskPool) implements CustomPacketPayload {
        public static final Type<BountyBoardOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("novus_items", "bounty_board_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyBoardOpenPayload> CODEC = new StreamCodec<>() {
            @Override
            public BountyBoardOpenPayload decode(RegistryFriendlyByteBuf buf) {
                int availableSize = buf.readVarInt();
                List<BountyView> available = new ArrayList<>(availableSize);
                for (int i = 0; i < availableSize; i++) {
                    available.add(BountyView.CODEC.decode(buf));
                }

                int mySize = buf.readVarInt();
                List<BountyView> my = new ArrayList<>(mySize);
                for (int i = 0; i < mySize; i++) {
                    my.add(BountyView.CODEC.decode(buf));
                }

                int timesSize = buf.readVarInt();
                List<String> refreshTimes = new ArrayList<>(timesSize);
                for (int i = 0; i < timesSize; i++) {
                    refreshTimes.add(buf.readUtf(16));
                }

                boolean isOp = buf.readBoolean();
                int systemRefreshAddCount = buf.readVarInt();
                int playerMaxActive = buf.readVarInt();
                int poolSize = buf.readVarInt();
                List<String> pool = new ArrayList<>(poolSize);
                for (int i = 0; i < poolSize; i++) {
                    pool.add(buf.readUtf(128));
                }
                return new BountyBoardOpenPayload(available, my, refreshTimes, isOp, systemRefreshAddCount, playerMaxActive, pool);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, BountyBoardOpenPayload value) {
                buf.writeVarInt(value.available.size());
                for (BountyView view : value.available) {
                    BountyView.CODEC.encode(buf, view);
                }

                buf.writeVarInt(value.my.size());
                for (BountyView view : value.my) {
                    BountyView.CODEC.encode(buf, view);
                }

                buf.writeVarInt(value.refreshTimes.size());
                for (String time : value.refreshTimes) {
                    buf.writeUtf(time == null ? "" : time, 16);
                }

                buf.writeBoolean(value.isOp);
                buf.writeVarInt(value.systemRefreshAddCount);
                buf.writeVarInt(value.playerMaxActive);

                buf.writeVarInt(value.taskPool.size());
                for (String entry : value.taskPool) {
                    buf.writeUtf(entry == null ? "" : entry, 128);
                }
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BountyBoardSyncPayload(List<BountyView> available, List<BountyView> my, List<String> refreshTimes, boolean isOp, int systemRefreshAddCount, int playerMaxActive, List<String> taskPool) implements CustomPacketPayload {
        public static final Type<BountyBoardSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("novus_items", "bounty_board_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyBoardSyncPayload> CODEC = new StreamCodec<>() {
            @Override
            public BountyBoardSyncPayload decode(RegistryFriendlyByteBuf buf) {
                BountyBoardOpenPayload open = BountyBoardOpenPayload.CODEC.decode(buf);
                return new BountyBoardSyncPayload(open.available, open.my, open.refreshTimes, open.isOp, open.systemRefreshAddCount, open.playerMaxActive, open.taskPool);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, BountyBoardSyncPayload value) {
                BountyBoardOpenPayload.CODEC.encode(buf, new BountyBoardOpenPayload(value.available, value.my, value.refreshTimes, value.isOp, value.systemRefreshAddCount, value.playerMaxActive, value.taskPool));
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public enum ActionType {
        REQUEST_SYNC,
        ACCEPT,
        ABANDON_ACTIVE,
        PUBLISH,
        SUBMIT_MANUAL,
        SUBMIT,
        CANCEL_PUBLISHED,
        REVIEW_APPROVE,
        REVIEW_DENY,
        ADMIN_DELETE,
        ADMIN_FORCE_COMPLETE,
        ADD_REFRESH_TIME,
        REMOVE_REFRESH_TIME,
        REFRESH_NOW,
        SET_REFRESH_ADD_COUNT,
        SET_MAX_AVAILABLE,
        SET_PLAYER_MAX_ACTIVE,
        POOL_ADD,
        POOL_REMOVE,
        POOL_REPLACE,
        POOL_ADD_RANDOM
    }

    public record BountyBoardActionPayload(ActionType action, String id, String title, String description, String bountyType, String requirementText, String rewardType, String rewardText) implements CustomPacketPayload {
        public static final Type<BountyBoardActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("novus_items", "bounty_board_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BountyBoardActionPayload> CODEC = new StreamCodec<>() {
            @Override
            public BountyBoardActionPayload decode(RegistryFriendlyByteBuf buf) {
                int actionOrdinal = buf.readVarInt();
                String id = buf.readUtf(128);
                String title = buf.readUtf(128);
                String description = buf.readUtf(256);
                String bountyType = buf.readUtf(32);
                String requirementText = buf.readUtf(256);
                String rewardType = buf.readUtf(32);
                String rewardText = buf.readUtf(256);
                ActionType action = ActionType.values()[Math.max(0, Math.min(ActionType.values().length - 1, actionOrdinal))];
                return new BountyBoardActionPayload(action, id, title, description, bountyType, requirementText, rewardType, rewardText);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, BountyBoardActionPayload value) {
                buf.writeVarInt(value.action.ordinal());
                buf.writeUtf(value.id, 128);
                buf.writeUtf(value.title, 128);
                buf.writeUtf(value.description, 256);
                buf.writeUtf(value.bountyType, 32);
                buf.writeUtf(value.requirementText, 256);
                buf.writeUtf(value.rewardType, 32);
                buf.writeUtf(value.rewardText, 256);
            }
        };

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
            ensureDefaultPools();
            if (refreshTimes.isEmpty()) {
                refreshTimes.add("12:00");
            }
            saveData();
        }

        if (tickCounter % CHECK_INTERVAL == 0) {
            maybeRefreshSystemBounties(server);
        }

        if (tickCounter % 40 == 0) {
            deliverPendingRefunds(server);
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
        var payload = new BountyBoardOpenPayload(buildAvailableViews(player), buildMyViews(player), new ArrayList<>(refreshTimes), player.hasPermissions(2), systemRefreshAddCount, playerMaxActive, new ArrayList<>(systemTaskPool));
        ServerPlayNetworking.send(player, payload);
    }

    public static void registerCommands(CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(net.minecraft.commands.Commands.literal("bountyboard")
            .requires(source -> source.hasPermission(2))
            .then(net.minecraft.commands.Commands.literal("open")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    openBoard(player);
                    return 1;
                })
                .then(net.minecraft.commands.Commands.argument("player", EntityArgument.player())
                    .executes(context -> {
                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                        openBoard(target);
                        context.getSource().sendSuccess(() -> Component.literal("已为玩家打开悬赏板: " + target.getGameProfile().getName()), false);
                        return 1;
                    })))
            .then(net.minecraft.commands.Commands.literal("time")
                .then(net.minecraft.commands.Commands.literal("list")
                    .executes(BountyBoardManager::listRefreshTimes))
                .then(net.minecraft.commands.Commands.literal("add")
                    .then(net.minecraft.commands.Commands.argument("time", StringArgumentType.word())
                        .executes(BountyBoardManager::addRefreshTime)))
                .then(net.minecraft.commands.Commands.literal("remove")
                    .then(net.minecraft.commands.Commands.argument("time", StringArgumentType.word())
                        .executes(BountyBoardManager::removeRefreshTime))))
            .then(net.minecraft.commands.Commands.literal("system")
                .then(net.minecraft.commands.Commands.literal("list")
                    .executes(BountyBoardManager::listSystemSettings))
                .then(net.minecraft.commands.Commands.literal("addCount")
                    .then(net.minecraft.commands.Commands.argument("count", IntegerArgumentType.integer(0, 64))
                        .executes(BountyBoardManager::setSystemAddCount)))
                .then(net.minecraft.commands.Commands.literal("maxAvailable")
                    .then(net.minecraft.commands.Commands.argument("count", IntegerArgumentType.integer(0, 128))
                        .executes(BountyBoardManager::setSystemMaxAvailable))))
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

    private static int listSystemSettings(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("系统悬赏: addCount=" + systemRefreshAddCount + ", maxAvailable=" + systemMaxAvailable), false);
        return 1;
    }

    private static int setSystemAddCount(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        systemRefreshAddCount = Math.max(0, count);
        saveData();
        context.getSource().sendSuccess(() -> Component.literal("已设置系统悬赏每次刷新新增数量: " + systemRefreshAddCount), false);
        return 1;
    }

    private static int setSystemMaxAvailable(CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        systemMaxAvailable = Math.max(0, count);
        saveData();
        context.getSource().sendSuccess(() -> Component.literal("已设置系统悬赏最大可接数量: " + systemMaxAvailable), false);
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
        int availableSystem = 0;
        for (Bounty bounty : bountiesById.values()) {
            if (bounty.system && bounty.status == BountyStatus.AVAILABLE) {
                availableSystem++;
            }
        }

        int toAdd = Math.max(0, systemRefreshAddCount);
        int hardRemaining = Math.max(0, 200 - availableSystem);
        toAdd = Math.min(toAdd, hardRemaining);
        for (int i = 0; i < toAdd; i++) {
            Bounty bounty = createRandomSystemBounty(server, random);
            bounty.system = true;
            bounty.issuerUuid = "";
            bounty.issuerName = "Server";
            bountiesById.put(bounty.id, bounty);
        }
        saveData();
    }

    private static void addSystemBountyIfCapacity(MinecraftServer server, RandomSource random, int desiredAdd) {
        int availableSystem = 0;
        for (Bounty bounty : bountiesById.values()) {
            if (bounty.system && bounty.status == BountyStatus.AVAILABLE) {
                availableSystem++;
            }
        }
        int toAdd = Math.max(0, desiredAdd);
        int hardRemaining = Math.max(0, 200 - availableSystem);
        toAdd = Math.min(toAdd, hardRemaining);
        for (int i = 0; i < toAdd; i++) {
            Bounty bounty = createRandomSystemBounty(server, random);
            bounty.system = true;
            bounty.issuerUuid = "";
            bounty.issuerName = "Server";
            bountiesById.put(bounty.id, bounty);
        }
        saveData();
    }

    private static void enforceSystemMaxAvailable() {
        if (systemMaxAvailable < 0) {
            systemMaxAvailable = 0;
        }
    }

    private static Bounty createRandomSystemBounty(MinecraftServer server, RandomSource random) {
        TaskPoolEntry entry = pickRandomTaskPoolEntry(random);
        if (entry == null) {
            TaskPoolReward reward = defaultRewardFor(TaskPoolKind.KILL);
            return createSystemKillBounty(random, ResourceLocation.fromNamespaceAndPath("minecraft", "zombie"), reward);
        }
        if (entry.kind == TaskPoolKind.KILL) {
            return createSystemKillBounty(random, entry.id, entry.reward);
        }
        return createSystemDeliveryBounty(random, entry.id, entry.kind == TaskPoolKind.CROPS, entry.reward);
    }

    private static TaskPoolEntry pickRandomTaskPoolEntry(RandomSource random) {
        if (systemTaskPool.isEmpty()) {
            ensureDefaultPools();
        }
        if (systemTaskPool.isEmpty()) {
            return null;
        }
        int tries = Math.min(16, systemTaskPool.size());
        for (int i = 0; i < tries; i++) {
            String raw = systemTaskPool.get(random.nextInt(systemTaskPool.size()));
            TaskPoolEntry parsed = parseTaskPoolEntry(raw);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static Bounty createSystemKillBounty(RandomSource random, ResourceLocation entityId, TaskPoolReward poolReward) {
        ResourceLocation id = entityId;
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
        bounty.reward = rewardFromPool(poolReward);
        return bounty;
    }

    private static Bounty createSystemDeliveryBounty(RandomSource random, ResourceLocation itemId, boolean crops, TaskPoolReward poolReward) {
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
        bounty.reward = rewardFromPool(poolReward);
        return bounty;
    }

    private static Reward rewardFromPool(TaskPoolReward poolReward) {
        Reward reward = new Reward();
        reward.type = RewardType.ITEM;
        reward.itemId = poolReward == null ? "novus_items:bronze_novus_coin" : poolReward.itemId.toString();
        reward.itemCount = poolReward == null ? 10 : Math.max(1, poolReward.count);
        reward.itemCustomDataNbt = "";
        return reward;
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
            case ABANDON_ACTIVE -> abandonActive(player, payload.id);
            case PUBLISH -> publish(player, payload);
            case SUBMIT_MANUAL -> submit(player, payload.id);
            case SUBMIT -> submit(player, payload.id);
            case CANCEL_PUBLISHED -> cancelPublished(player, payload.id);
            case REVIEW_APPROVE -> reviewApprove(player, payload.id);
            case REVIEW_DENY -> reviewDeny(player, payload.id);
            case ADMIN_DELETE -> adminDelete(player, payload.id);
            case ADMIN_FORCE_COMPLETE -> adminForceComplete(player, payload.id);
            case ADD_REFRESH_TIME -> addRefreshTimeGui(player, payload.id);
            case REMOVE_REFRESH_TIME -> removeRefreshTimeGui(player, payload.id);
            case REFRESH_NOW -> refreshNowGui(player);
            case SET_REFRESH_ADD_COUNT -> setRefreshAddCountGui(player, payload.id);
            case SET_MAX_AVAILABLE -> setMaxAvailableGui(player, payload.id);
            case SET_PLAYER_MAX_ACTIVE -> setPlayerMaxActiveGui(player, payload.id);
            case POOL_ADD -> poolAdd(player, payload.id);
            case POOL_REMOVE -> poolRemove(player, payload.id);
            case POOL_REPLACE -> poolReplace(player, payload.id, payload.title);
            case POOL_ADD_RANDOM -> poolAddRandom(player);
        }
    }

    private static void setRefreshAddCountGui(ServerPlayer player, String value) {
        if (!player.hasPermissions(2)) {
            return;
        }
        int count = parseNonNegativeIntOrDefault(value, systemRefreshAddCount);
        systemRefreshAddCount = Math.max(0, Math.min(64, count));
        saveData();
        broadcastSync(player.server);
        player.sendSystemMessage(Component.literal("§a✦ 已设置每次刷新新增数量: " + systemRefreshAddCount + " §a✦"));
    }

    private static void setMaxAvailableGui(ServerPlayer player, String value) {
        if (!player.hasPermissions(2)) {
            return;
        }
        int count = parseNonNegativeIntOrDefault(value, systemMaxAvailable);
        systemMaxAvailable = Math.max(0, Math.min(128, count));
        saveData();
        broadcastSync(player.server);
        player.sendSystemMessage(Component.literal("§a✦ 已设置系统悬赏最大可接数量: " + systemMaxAvailable + " §a✦"));
    }

    private static void setPlayerMaxActiveGui(ServerPlayer player, String value) {
        if (!player.hasPermissions(2)) {
            return;
        }
        int count = parseNonNegativeIntOrDefault(value, playerMaxActive);
        playerMaxActive = Math.max(0, Math.min(32, count));
        saveData();
        broadcastSync(player.server);
        player.sendSystemMessage(Component.literal("§a✦ 已设置玩家同时可接悬赏数量: " + playerMaxActive + " §a✦"));
    }

    private static void addRefreshTimeGui(ServerPlayer player, String time) {
        if (!player.hasPermissions(2)) {
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
        if (!player.hasPermissions(2)) {
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
        if (!player.hasPermissions(2)) {
            return;
        }
        generateSystemBounties(player.server, player.server.overworld().getRandom());
        broadcastSync(player.server);
        player.sendSystemMessage(Component.literal("§a✦ 已立即刷新悬赏 §a✦"));
    }

    private enum TaskPoolKind {
        KILL,
        CROPS,
        BLOCKS
    }

    private record TaskPoolReward(ResourceLocation itemId, int count) {
    }

    private record TaskPoolEntry(TaskPoolKind kind, ResourceLocation id, TaskPoolReward reward) {
    }

    private static TaskPoolReward defaultRewardFor(TaskPoolKind kind) {
        int coins = switch (kind) {
            case KILL -> 10;
            case CROPS -> 12;
            case BLOCKS -> 14;
        };
        return new TaskPoolReward(ResourceLocation.fromNamespaceAndPath("novus_items", "bronze_novus_coin"), coins);
    }

    private static TaskPoolReward parseRewardPart(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        String[] parts = v.split("\\s+");
        if (parts.length < 1) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        int count = 1;
        if (parts.length >= 2) {
            try {
                count = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ignored) {
                count = 1;
            }
        }
        count = Math.min(64, count);
        return new TaskPoolReward(id, count);
    }

    private static TaskPoolEntry parseTaskPoolEntry(String raw) {
        String v = safeText(raw, 120);
        if (v.isEmpty()) {
            return null;
        }
        String[] halves = v.split("\\|", 2);
        String left = halves[0].trim();
        String right = halves.length == 2 ? halves[1].trim() : "";

        String[] parts = left.split("\\s+", 2);
        if (parts.length != 2) {
            return null;
        }
        TaskPoolKind kind;
        try {
            kind = TaskPoolKind.valueOf(parts[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(parts[1].trim());
        if (id == null) {
            return null;
        }
        if (kind == TaskPoolKind.KILL) {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                return null;
            }
        } else {
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                return null;
            }
        }
        TaskPoolReward reward = parseRewardPart(right);
        if (reward == null) {
            reward = defaultRewardFor(kind);
        }
        return new TaskPoolEntry(kind, id, reward);
    }

    private static String normalizeTaskPoolEntry(String raw) {
        TaskPoolEntry parsed = parseTaskPoolEntry(raw);
        if (parsed == null) {
            return "";
        }
        return parsed.kind.name() + " " + parsed.id.toString() + " | " + parsed.reward.itemId.toString() + " " + parsed.reward.count;
    }

    private static void poolAdd(ServerPlayer player, String entry) {
        if (!player.hasPermissions(2)) {
            return;
        }
        String v = normalizeTaskPoolEntry(entry);
        if (v.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c✦ 条目格式错误：KILL/CROPS/BLOCKS + 空格 + id，可选 | 奖励物品id 数量 §c✦"));
            return;
        }
        if (!systemTaskPool.contains(v)) {
            systemTaskPool.add(v);
            systemTaskPool.sort(Comparator.naturalOrder());
        }
        saveData();
        broadcastSync(player.server);
        player.sendSystemMessage(Component.literal("§a✦ 已添加任务池条目: " + v + " §a✦"));
    }

    private static void poolRemove(ServerPlayer player, String entry) {
        if (!player.hasPermissions(2)) {
            return;
        }
        String v = normalizeTaskPoolEntry(entry);
        if (v.isEmpty()) {
            v = safeText(entry, 120);
        }
        if (v.isEmpty()) {
            return;
        }
        systemTaskPool.remove(v);
        saveData();
        broadcastSync(player.server);
        player.sendSystemMessage(Component.literal("§e✦ 已移除任务池条目: " + v + " §e✦"));
    }

    private static void poolReplace(ServerPlayer player, String oldEntry, String newEntry) {
        if (!player.hasPermissions(2)) {
            return;
        }
        String oldV = normalizeTaskPoolEntry(oldEntry);
        if (oldV.isEmpty()) {
            oldV = safeText(oldEntry, 120);
        }
        String newV = normalizeTaskPoolEntry(newEntry);
        if (oldV.isEmpty() || newV.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c✦ 条目格式错误：KILL/CROPS/BLOCKS + 空格 + id，可选 | 奖励物品id 数量 §c✦"));
            return;
        }
        int idx = systemTaskPool.indexOf(oldV);
        if (idx >= 0) {
            systemTaskPool.set(idx, newV);
        } else if (!systemTaskPool.contains(newV)) {
            systemTaskPool.add(newV);
        }
        systemTaskPool.sort(Comparator.naturalOrder());
        saveData();
        broadcastSync(player.server);
        player.sendSystemMessage(Component.literal("§a✦ 已更新任务池条目: " + oldV + " -> " + newV + " §a✦"));
    }

    private static void poolAddRandom(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            return;
        }
        RandomSource random = player.server.overworld().getRandom();
        TaskPoolKind kind = TaskPoolKind.values()[random.nextInt(TaskPoolKind.values().length)];
        String id = switch (kind) {
            case KILL -> switch (random.nextInt(5)) {
                case 0 -> "minecraft:zombie";
                case 1 -> "minecraft:skeleton";
                case 2 -> "minecraft:creeper";
                case 3 -> "minecraft:spider";
                default -> "minecraft:enderman";
            };
            case CROPS -> switch (random.nextInt(4)) {
                case 0 -> "minecraft:wheat";
                case 1 -> "minecraft:carrot";
                case 2 -> "minecraft:potato";
                default -> "minecraft:beetroot";
            };
            case BLOCKS -> switch (random.nextInt(4)) {
                case 0 -> "minecraft:cobblestone";
                case 1 -> "minecraft:stone";
                case 2 -> "minecraft:oak_planks";
                default -> "minecraft:bricks";
            };
        };
        int coins = switch (kind) {
            case KILL -> 8 + random.nextInt(9);
            case CROPS -> 10 + random.nextInt(11);
            case BLOCKS -> 12 + random.nextInt(13);
        };
        String entry = kind.name() + " " + id + " | novus_items:bronze_novus_coin " + coins;
        String v = normalizeTaskPoolEntry(entry);
        if (v.isEmpty()) {
            return;
        }
        if (!systemTaskPool.contains(v)) {
            systemTaskPool.add(v);
            systemTaskPool.sort(Comparator.naturalOrder());
        }
        saveData();
        broadcastSync(player.server);
        player.sendSystemMessage(Component.literal("§a✦ 已随机新增任务池条目: " + v + " §a✦"));
    }

    private static void abandonActive(ServerPlayer player, String bountyIdHint) {
        String bountyId = bountyIdHint == null ? "" : bountyIdHint.trim();

        UUID playerId = player.getUUID();
        Set<String> actives = activeBountiesByPlayer.get(playerId);
        if (bountyId.isEmpty()) {
            if (actives == null || actives.isEmpty()) {
                player.sendSystemMessage(Component.literal("§c✦ 你没有正在进行的悬赏。 §c✦"));
                return;
            }
            if (actives.size() == 1) {
                bountyId = actives.iterator().next();
            } else {
                player.sendSystemMessage(Component.literal("§c✦ 你正在进行多个悬赏，请在“我的任务”里选择要放弃的任务。 §c✦"));
                return;
            }
        }

        removeActiveBounty(playerId, bountyId);

        Bounty bounty = bountiesById.get(bountyId);
        if (bounty == null) {
            player.sendSystemMessage(Component.literal("§e✦ 已放弃悬赏（任务已不存在）。 §e✦"));
            broadcastSync(player.server);
            return;
        }
        if (!Objects.equals(bounty.accepterUuid, player.getUUID().toString())) {
            player.sendSystemMessage(Component.literal("§e✦ 已放弃悬赏。 §e✦"));
            broadcastSync(player.server);
            return;
        }
        bountiesById.remove(bountyId);
        refundToIssuer(bounty);
        saveData();
        broadcastSync(player.server);
        player.sendSystemMessage(Component.literal("§e✦ 已放弃悬赏。 §e✦"));
    }

    private static void adminDelete(ServerPlayer player, String bountyId) {
        if (!player.hasPermissions(2)) {
            return;
        }
        if (bountyId == null || bountyId.isEmpty()) {
            return;
        }
        Bounty bounty = bountiesById.remove(bountyId);
        if (bounty != null && bounty.status == BountyStatus.ACCEPTED && bounty.accepterUuid != null && !bounty.accepterUuid.isEmpty()) {
            try {
                removeActiveBounty(UUID.fromString(bounty.accepterUuid), bountyId);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (bounty != null) {
            refundToIssuer(bounty);
        }
        saveData();
        broadcastSync(player.server);
        player.sendSystemMessage(Component.literal("§a✦ 已删除悬赏。 §a✦"));
    }

    private static void adminForceComplete(ServerPlayer player, String bountyId) {
        if (!player.hasPermissions(2)) {
            return;
        }
        if (bountyId == null || bountyId.isEmpty()) {
            return;
        }
        Bounty bounty = bountiesById.get(bountyId);
        if (bounty == null) {
            return;
        }
        if (bounty.status == BountyStatus.AVAILABLE) {
            bountiesById.remove(bountyId);
            refundToIssuer(bounty);
            saveData();
            broadcastSync(player.server);
            player.sendSystemMessage(Component.literal("§a✦ 已直接完成并移除该悬赏。 §a✦"));
            return;
        }
        if (bounty.status == BountyStatus.ACCEPTED || bounty.status == BountyStatus.PENDING_REVIEW) {
            ServerPlayer accepter = getPlayerByUuid(player.server, bounty.accepterUuid);
            if (accepter == null) {
                player.sendSystemMessage(Component.literal("§c✦ 接取者不在线，无法强制完成发奖。 §c✦"));
                return;
            }
            complete(accepter, bounty, true);
            broadcastSync(player.server);
            player.sendSystemMessage(Component.literal("§a✦ 已强制完成并发奖。 §a✦"));
        }
    }

    private static void sendSync(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, BountyBoardSyncPayload.TYPE)) {
            return;
        }
        ServerPlayNetworking.send(player, new BountyBoardSyncPayload(buildAvailableViews(player), buildMyViews(player), new ArrayList<>(refreshTimes), player.hasPermissions(2), systemRefreshAddCount, playerMaxActive, new ArrayList<>(systemTaskPool)));
    }

    private static void broadcastSync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSync(player);
        }
    }

    private static int getActiveCount(UUID playerId) {
        Set<String> set = activeBountiesByPlayer.get(playerId);
        return set == null ? 0 : set.size();
    }

    private static void addActiveBounty(UUID playerId, String bountyId) {
        if (bountyId == null || bountyId.isEmpty()) {
            return;
        }
        activeBountiesByPlayer.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(bountyId);
    }

    private static void removeActiveBounty(UUID playerId, String bountyId) {
        Set<String> set = activeBountiesByPlayer.get(playerId);
        if (set == null) {
            return;
        }
        set.remove(bountyId);
        if (set.isEmpty()) {
            activeBountiesByPlayer.remove(playerId);
        }
    }

    private static void accept(ServerPlayer player, String bountyId) {
        if (bountyId == null || bountyId.isEmpty()) {
            return;
        }
        int activeCount = getActiveCount(player.getUUID());
        if (playerMaxActive <= 0 || activeCount >= playerMaxActive) {
            player.sendSystemMessage(Component.literal("§c✦ 你同时最多只能接取 " + playerMaxActive + " 个悬赏。 §c✦"));
            return;
        }
        Bounty bounty = bountiesById.get(bountyId);
        if (bounty == null || bounty.status != BountyStatus.AVAILABLE) {
            player.sendSystemMessage(Component.literal("§c✦ 这个悬赏已被别人抢走了。 §c✦"));
            return;
        }
        bounty.status = BountyStatus.ACCEPTED;
        bounty.accepterUuid = player.getUUID().toString();
        bounty.accepterName = player.getGameProfile().getName();
        bounty.progress = 0;
        bounty.target = getTarget(bounty);
        addActiveBounty(player.getUUID(), bounty.id);
        saveData();
        player.sendSystemMessage(Component.literal("§a✦ 已接取悬赏。请前往“我的任务”提交。 §a✦"));
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
        boolean isOp = player.hasPermissions(2);
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
        bounty.system = isOp;
        bounty.status = BountyStatus.AVAILABLE;
        bounty.type = type;
        bounty.title = title;
        bounty.description = description;
        bounty.issuerUuid = isOp ? "" : player.getUUID().toString();
        bounty.issuerName = isOp ? "Server" : player.getGameProfile().getName();
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

    private static void submit(ServerPlayer player, String bountyId) {
        if (bountyId == null || bountyId.isEmpty()) {
            return;
        }
        Bounty bounty = bountiesById.get(bountyId);
        if (bounty == null) {
            player.sendSystemMessage(Component.literal("§c✦ 悬赏不存在。 §c✦"));
            return;
        }
        if (!Objects.equals(bounty.accepterUuid, player.getUUID().toString())) {
            player.sendSystemMessage(Component.literal("§c✦ 你不是这个悬赏的接取者。 §c✦"));
            return;
        }
        if (bounty.status == BountyStatus.PENDING_REVIEW) {
            player.sendSystemMessage(Component.literal("§e✦ 正在等待验收。 §e✦"));
            return;
        }
        if (bounty.status != BountyStatus.ACCEPTED) {
            player.sendSystemMessage(Component.literal("§c✦ 悬赏状态异常。 §c✦"));
            return;
        }

        if (bounty.type == BountyType.CUSTOM_MANUAL) {
            bounty.status = BountyStatus.PENDING_REVIEW;
            bounty.pendingReviewerUuid = bounty.issuerUuid;
            saveData();
            notifyIssuer(player.server, bounty, player);
            broadcastSync(player.server);
            player.sendSystemMessage(Component.literal("§e✦ 已提交给发布者验收，请等待。 §e✦"));
            return;
        }

        if (bounty.type == BountyType.ENTITY_KILL) {
            if (bounty.progress < bounty.target) {
                player.sendSystemMessage(Component.literal("§c✦ 还没完成： " + bounty.progress + "/" + bounty.target + " §c✦"));
                return;
            }
            complete(player, bounty, false);
            broadcastSync(player.server);
            return;
        }

        if (bounty.type == BountyType.ITEM_DELIVERY) {
            ResourceLocation itemId = ResourceLocation.tryParse(bounty.requirement.itemId);
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                player.sendSystemMessage(Component.literal("§c✦ 目标物品无效。 §c✦"));
                return;
            }
            int need = Math.max(1, bounty.requirement.itemCount);
            ItemStack template = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
            if (!InventoryHelper.hasItems(player, template, need)) {
                player.sendSystemMessage(Component.literal("§c✦ 物品不足，无法提交。 §c✦"));
                return;
            }
            InventoryHelper.consumeItems(player, template, need);
            complete(player, bounty, false);
            broadcastSync(player.server);
        }
    }

    private static void cancelPublished(ServerPlayer player, String bountyId) {
        if (bountyId == null || bountyId.isEmpty()) {
            return;
        }
        Bounty bounty = bountiesById.get(bountyId);
        if (bounty == null) {
            return;
        }
        if (bounty.system || bounty.issuerUuid == null || bounty.issuerUuid.isEmpty()) {
            return;
        }
        if (!Objects.equals(bounty.issuerUuid, player.getUUID().toString()) && !player.hasPermissions(2)) {
            return;
        }

        if (bounty.accepterUuid != null && !bounty.accepterUuid.isEmpty()) {
            try {
                removeActiveBounty(UUID.fromString(bounty.accepterUuid), bounty.id);
            } catch (IllegalArgumentException ignored) {
            }
            ServerPlayer accepter = getPlayerByUuid(player.server, bounty.accepterUuid);
            if (accepter != null) {
                accepter.sendSystemMessage(Component.literal("§c✦ 发布者已取消该悬赏。 §c✦"));
            }
        }

        bountiesById.remove(bounty.id);
        refundToIssuer(bounty);
        saveData();
        broadcastSync(player.server);
        player.sendSystemMessage(Component.literal("§e✦ 已取消悬赏并返还材料。 §e✦"));
    }

    private static void reviewApprove(ServerPlayer player, String bountyId) {
        Bounty bounty = bountiesById.get(bountyId);
        if (bounty == null || bounty.status != BountyStatus.PENDING_REVIEW) {
            return;
        }
        if (!(Objects.equals(bounty.issuerUuid, player.getUUID().toString()) || (player.hasPermissions(2) && bounty.system))) {
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
        if (!(Objects.equals(bounty.issuerUuid, player.getUUID().toString()) || (player.hasPermissions(2) && bounty.system))) {
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
        Set<String> activeIds = activeBountiesByPlayer.get(player.getUUID());
        if (activeIds == null || activeIds.isEmpty()) {
            return;
        }
        ResourceLocation killedId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (killedId == null) {
            return;
        }
        boolean changed = false;
        for (String bountyId : activeIds) {
            Bounty bounty = bountiesById.get(bountyId);
            if (bounty == null || bounty.status != BountyStatus.ACCEPTED || bounty.type != BountyType.ENTITY_KILL) {
                continue;
            }
            if (!Objects.equals(bounty.requirement.entityId, killedId.toString())) {
                continue;
            }
            bounty.progress = Math.min(bounty.target, bounty.progress + 1);
            changed = true;
        }
        if (changed) {
            saveData();
            sendSync(player);
        }
    }

    private static void complete(ServerPlayer player, Bounty bounty, boolean manualReviewed) {
        grantReward(player, bounty.reward);
        bounty.status = BountyStatus.COMPLETED;
        removeActiveBounty(player.getUUID(), bounty.id);
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
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
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
            } catch (CommandSyntaxException ignored) {
            }
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void refundToIssuer(Bounty bounty) {
        if (bounty == null || bounty.system) {
            return;
        }
        if (bounty.issuerUuid == null || bounty.issuerUuid.isEmpty()) {
            return;
        }
        if (bounty.reward == null || bounty.reward.type != RewardType.ITEM) {
            return;
        }
        Reward copy = new Reward();
        copy.type = bounty.reward.type;
        copy.itemId = bounty.reward.itemId == null ? "" : bounty.reward.itemId;
        copy.itemCount = bounty.reward.itemCount;
        copy.itemCustomDataNbt = bounty.reward.itemCustomDataNbt == null ? "" : bounty.reward.itemCustomDataNbt;
        copy.command = "";
        pendingRefundsByIssuerUuid.computeIfAbsent(bounty.issuerUuid, k -> new ArrayList<>()).add(copy);
    }

    private static void deliverPendingRefunds(MinecraftServer server) {
        if (pendingRefundsByIssuerUuid.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<Reward>> entry : new HashMap<>(pendingRefundsByIssuerUuid).entrySet()) {
            String issuerUuid = entry.getKey();
            List<Reward> rewards = entry.getValue();
            if (rewards == null || rewards.isEmpty()) {
                pendingRefundsByIssuerUuid.remove(issuerUuid);
                continue;
            }
            ServerPlayer issuer = getPlayerByUuid(server, issuerUuid);
            if (issuer == null) {
                continue;
            }
            for (Reward reward : rewards) {
                if (reward == null || reward.type != RewardType.ITEM) {
                    continue;
                }
                grantReward(issuer, reward);
            }
            pendingRefundsByIssuerUuid.remove(issuerUuid);
            saveData();
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
        int activeCount = getActiveCount(player.getUUID());
        for (Bounty bounty : bountiesById.values()) {
            if (bounty.status != BountyStatus.AVAILABLE) {
                continue;
            }
            boolean canAccept = playerMaxActive > 0 && activeCount < playerMaxActive;
            String requirementId = "";
            int requirementCount = 0;
            int progress = 0;
            int target = 0;
            if (bounty.type == BountyType.ITEM_DELIVERY) {
                requirementId = bounty.requirement.itemId;
                requirementCount = bounty.requirement.itemCount;
            } else if (bounty.type == BountyType.ENTITY_KILL) {
                requirementId = bounty.requirement.entityId;
                requirementCount = bounty.requirement.entityCount;
                progress = bounty.progress;
                target = bounty.target;
            } else {
                requirementId = bounty.requirement.manualText;
            }

            String rewardItemId = "";
            int rewardItemCount = 0;
            boolean rewardIsCommand = bounty.reward != null && bounty.reward.type == RewardType.COMMAND;
            if (bounty.reward != null && bounty.reward.type == RewardType.ITEM) {
                rewardItemId = bounty.reward.itemId;
                rewardItemCount = bounty.reward.itemCount;
            }

            views.add(new BountyView(
                bounty.id,
                bounty.title,
                bounty.description,
                bounty.type.name(),
                requirementId == null ? "" : requirementId,
                Math.max(0, requirementCount),
                Math.max(0, progress),
                Math.max(0, target),
                rewardItemId == null ? "" : rewardItemId,
                Math.max(0, rewardItemCount),
                rewardIsCommand,
                bounty.system,
                bounty.issuerName == null || bounty.issuerName.isEmpty() ? (bounty.system ? "Server" : "Player") : bounty.issuerName,
                bounty.status.name(),
                "",
                canAccept,
                false,
                false,
                false,
                false,
                false,
                false
            ));
        }
        views.sort(Comparator.comparing(BountyView::title));
        return views;
    }

    private static List<BountyView> buildMyViews(ServerPlayer player) {
        List<BountyView> views = new ArrayList<>();
        boolean isOp = player.hasPermissions(2);
        String uuid = player.getUUID().toString();
        for (Bounty bounty : bountiesById.values()) {
            boolean mineAccepted = Objects.equals(bounty.accepterUuid, uuid) && (bounty.status == BountyStatus.ACCEPTED || bounty.status == BountyStatus.PENDING_REVIEW);
            boolean mineIssued = !bounty.system && Objects.equals(bounty.issuerUuid, uuid) && (bounty.status == BountyStatus.AVAILABLE || bounty.status == BountyStatus.ACCEPTED || bounty.status == BountyStatus.PENDING_REVIEW);
            boolean canReview = bounty.status == BountyStatus.PENDING_REVIEW && (Objects.equals(bounty.issuerUuid, uuid) || (isOp && bounty.system));
            if (!(mineAccepted || mineIssued || canReview)) {
                continue;
            }

            String accepterName = "";
            if (bounty.accepterName != null && !bounty.accepterName.isEmpty()) {
                accepterName = bounty.accepterName;
            } else if (bounty.accepterUuid != null && !bounty.accepterUuid.isEmpty() && (bounty.status == BountyStatus.ACCEPTED || bounty.status == BountyStatus.PENDING_REVIEW)) {
                ServerPlayer accepter = getPlayerByUuid(player.server, bounty.accepterUuid);
                accepterName = accepter == null ? "离线玩家" : accepter.getGameProfile().getName();
            }

            String requirementId = "";
            int requirementCount = 0;
            int progress = 0;
            int target = 0;
            if (bounty.type == BountyType.ITEM_DELIVERY) {
                requirementId = bounty.requirement.itemId;
                requirementCount = bounty.requirement.itemCount;
                target = Math.max(1, requirementCount);
            } else if (bounty.type == BountyType.ENTITY_KILL) {
                requirementId = bounty.requirement.entityId;
                requirementCount = bounty.requirement.entityCount;
                progress = bounty.progress;
                target = bounty.target;
            } else {
                requirementId = bounty.requirement.manualText;
                target = 1;
            }

            String rewardItemId = "";
            int rewardItemCount = 0;
            boolean rewardIsCommand = bounty.reward != null && bounty.reward.type == RewardType.COMMAND;
            if (bounty.reward != null && bounty.reward.type == RewardType.ITEM) {
                rewardItemId = bounty.reward.itemId;
                rewardItemCount = bounty.reward.itemCount;
            }

            boolean canSubmit = mineAccepted && bounty.status == BountyStatus.ACCEPTED && (bounty.type != BountyType.ENTITY_KILL || bounty.progress >= bounty.target);
            boolean canAbandon = mineAccepted && bounty.status == BountyStatus.ACCEPTED;
            boolean canCancel = mineIssued && bounty.status != BountyStatus.COMPLETED;

            views.add(new BountyView(
                bounty.id,
                bounty.title,
                bounty.description,
                bounty.type.name(),
                requirementId == null ? "" : requirementId,
                Math.max(0, requirementCount),
                Math.max(0, progress),
                Math.max(0, target),
                rewardItemId == null ? "" : rewardItemId,
                Math.max(0, rewardItemCount),
                rewardIsCommand,
                bounty.system,
                bounty.issuerName == null || bounty.issuerName.isEmpty() ? (bounty.system ? "Server" : "Player") : bounty.issuerName,
                bounty.status.name(),
                accepterName,
                false,
                mineAccepted,
                mineIssued,
                canSubmit,
                canReview,
                canAbandon,
                canCancel
            ));
        }
        views.sort(Comparator.comparing(BountyView::mineAccepted).reversed().thenComparing(BountyView::mineIssued).reversed().thenComparing(BountyView::title));
        return views;
    }

    public static String requirementText(Bounty bounty) {
        if (bounty.type == BountyType.ITEM_DELIVERY) {
            ResourceLocation itemId = ResourceLocation.tryParse(bounty.requirement.itemId);
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                return bounty.requirement.itemId + " x" + bounty.requirement.itemCount;
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
            return stack.getHoverName().getString() + " x" + bounty.requirement.itemCount;
        }
        if (bounty.type == BountyType.ENTITY_KILL) {
            ResourceLocation entityId = ResourceLocation.tryParse(bounty.requirement.entityId);
            if (entityId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
                return bounty.requirement.entityId + " x" + bounty.requirement.entityCount + " (" + bounty.progress + "/" + bounty.target + ")";
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityId);
            return type.getDescription().getString() + " x" + bounty.requirement.entityCount + " (" + bounty.progress + "/" + bounty.target + ")";
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
        ResourceLocation itemId = ResourceLocation.tryParse(bounty.reward.itemId);
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            int count = Math.max(1, bounty.reward.itemCount);
            return bounty.reward.itemId + " x" + count;
        }
        int count = Math.max(1, bounty.reward.itemCount);
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId), count);
        return stack.getHoverName().getString() + " x" + count;
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

    private static int parseNonNegativeIntOrDefault(String value, int def) {
        if (value == null) return def;
        try {
            int i = Integer.parseInt(value.trim());
            return Math.max(0, i);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void saveData() {
        if (dataFile == null) return;
        try {
            DataFileV2 data = new DataFileV2();
            data.bounties = new HashMap<>(bountiesById);
            data.activeBountyByPlayer = new HashMap<>();
            for (Map.Entry<UUID, Set<String>> entry : activeBountiesByPlayer.entrySet()) {
                data.activeBountyByPlayer.put(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
            }
            data.refreshTimes = new ArrayList<>(refreshTimes);
            data.lastRefreshDateByTime = new HashMap<>(lastRefreshDateByTime);
            data.systemRefreshAddCount = systemRefreshAddCount;
            data.systemMaxAvailable = systemMaxAvailable;
            data.playerMaxActive = playerMaxActive;
            data.systemTaskPool = new ArrayList<>(systemTaskPool);
            data.pendingRefundsByIssuerUuid = new HashMap<>(pendingRefundsByIssuerUuid);
            Files.writeString(dataFile, GSON.toJson(data));
        } catch (IOException e) {
        }
    }

    private static void loadData() {
        if (dataFile == null || !Files.exists(dataFile)) return;
        try {
            String json = Files.readString(dataFile);
            DataFileV2 dataV2 = null;
            try {
                dataV2 = GSON.fromJson(json, new TypeToken<DataFileV2>() {}.getType());
            } catch (Exception ignored) {
            }

            DataFileV1 dataV1 = null;
            if (dataV2 == null) {
                try {
                    dataV1 = GSON.fromJson(json, new TypeToken<DataFileV1>() {}.getType());
                } catch (Exception ignored) {
                }
            }

            if (dataV2 == null && dataV1 == null) {
                return;
            }

            bountiesById.clear();
            Map<String, Bounty> loadedBounties = dataV2 != null ? dataV2.bounties : dataV1.bounties;
            if (loadedBounties != null) {
                bountiesById.putAll(loadedBounties);
            }
            activeBountiesByPlayer.clear();
            if (dataV2 != null && dataV2.activeBountyByPlayer != null) {
                for (Map.Entry<String, List<String>> entry : dataV2.activeBountyByPlayer.entrySet()) {
                    try {
                        UUID playerId = UUID.fromString(entry.getKey());
                        Set<String> set = ConcurrentHashMap.newKeySet();
                        if (entry.getValue() != null) {
                            for (String bountyId : entry.getValue()) {
                                if (bountyId != null && bountiesById.containsKey(bountyId)) {
                                    set.add(bountyId);
                                }
                            }
                        }
                        if (!set.isEmpty()) {
                            activeBountiesByPlayer.put(playerId, set);
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            } else if (dataV1 != null && dataV1.activeBountyByPlayer != null) {
                for (Map.Entry<String, String> entry : dataV1.activeBountyByPlayer.entrySet()) {
                    try {
                        UUID playerId = UUID.fromString(entry.getKey());
                        String bountyId = entry.getValue();
                        if (bountyId == null || !bountiesById.containsKey(bountyId)) {
                            continue;
                        }
                        Set<String> set = ConcurrentHashMap.newKeySet();
                        set.add(bountyId);
                        activeBountiesByPlayer.put(playerId, set);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            refreshTimes.clear();
            List<String> loadedRefreshTimes = dataV2 != null ? dataV2.refreshTimes : dataV1.refreshTimes;
            if (loadedRefreshTimes != null) {
                for (String time : loadedRefreshTimes) {
                    if (isValidTime(time)) {
                        refreshTimes.add(time);
                    }
                }
                refreshTimes.sort(Comparator.naturalOrder());
            }
            lastRefreshDateByTime.clear();
            Map<String, String> loadedLast = dataV2 != null ? dataV2.lastRefreshDateByTime : dataV1.lastRefreshDateByTime;
            if (loadedLast != null) {
                lastRefreshDateByTime.putAll(loadedLast);
            }
            Integer loadedAdd = dataV2 != null ? dataV2.systemRefreshAddCount : dataV1.systemRefreshAddCount;
            Integer loadedMax = dataV2 != null ? dataV2.systemMaxAvailable : dataV1.systemMaxAvailable;
            if (loadedAdd != null) {
                systemRefreshAddCount = Math.max(0, loadedAdd);
            }
            if (loadedMax != null) {
                systemMaxAvailable = Math.max(0, loadedMax);
            }
            if (dataV2 != null && dataV2.playerMaxActive != null) {
                playerMaxActive = Math.max(0, dataV2.playerMaxActive);
            }

            systemTaskPool.clear();
            pendingRefundsByIssuerUuid.clear();
            if (dataV2 != null) {
                if (dataV2.systemTaskPool != null) {
                    systemTaskPool.addAll(dataV2.systemTaskPool);
                } else {
                    if (dataV2.systemKillPool != null) {
                        for (String id : dataV2.systemKillPool) {
                            String norm = normalizeTaskPoolEntry("KILL " + safeText(id, 120));
                            if (!norm.isEmpty()) systemTaskPool.add(norm);
                        }
                    }
                    if (dataV2.systemCropsPool != null) {
                        for (String id : dataV2.systemCropsPool) {
                            String norm = normalizeTaskPoolEntry("CROPS " + safeText(id, 120));
                            if (!norm.isEmpty()) systemTaskPool.add(norm);
                        }
                    }
                    if (dataV2.systemBlocksPool != null) {
                        for (String id : dataV2.systemBlocksPool) {
                            String norm = normalizeTaskPoolEntry("BLOCKS " + safeText(id, 120));
                            if (!norm.isEmpty()) systemTaskPool.add(norm);
                        }
                    }
                }
                if (dataV2.pendingRefundsByIssuerUuid != null) pendingRefundsByIssuerUuid.putAll(dataV2.pendingRefundsByIssuerUuid);
            }
            ensureDefaultPools();
        } catch (Exception ignored) {
        }
    }

    private static void ensureDefaultPools() {
        if (systemTaskPool.isEmpty()) {
            systemTaskPool.add("KILL minecraft:zombie | novus_items:bronze_novus_coin 10");
            systemTaskPool.add("KILL minecraft:skeleton | novus_items:bronze_novus_coin 10");
            systemTaskPool.add("KILL minecraft:creeper | novus_items:bronze_novus_coin 12");
            systemTaskPool.add("KILL minecraft:spider | novus_items:bronze_novus_coin 9");
            systemTaskPool.add("KILL minecraft:enderman | novus_items:bronze_novus_coin 14");
            systemTaskPool.add("CROPS minecraft:wheat | novus_items:bronze_novus_coin 12");
            systemTaskPool.add("CROPS minecraft:carrot | novus_items:bronze_novus_coin 12");
            systemTaskPool.add("CROPS minecraft:potato | novus_items:bronze_novus_coin 12");
            systemTaskPool.add("CROPS minecraft:beetroot | novus_items:bronze_novus_coin 13");
            systemTaskPool.add("BLOCKS minecraft:cobblestone | novus_items:bronze_novus_coin 14");
            systemTaskPool.add("BLOCKS minecraft:stone | novus_items:bronze_novus_coin 15");
            systemTaskPool.add("BLOCKS minecraft:oak_planks | novus_items:bronze_novus_coin 14");
            systemTaskPool.add("BLOCKS minecraft:bricks | novus_items:bronze_novus_coin 16");
            systemTaskPool.sort(Comparator.naturalOrder());
        }
    }

    private static class DataFileV1 {
        public Map<String, Bounty> bounties = new HashMap<>();
        public Map<String, String> activeBountyByPlayer = new HashMap<>();
        public List<String> refreshTimes = new ArrayList<>();
        public Map<String, String> lastRefreshDateByTime = new HashMap<>();
        public Integer systemRefreshAddCount;
        public Integer systemMaxAvailable;
    }

    private static class DataFileV2 {
        public Map<String, Bounty> bounties = new HashMap<>();
        public Map<String, List<String>> activeBountyByPlayer = new HashMap<>();
        public List<String> refreshTimes = new ArrayList<>();
        public Map<String, String> lastRefreshDateByTime = new HashMap<>();
        public Integer systemRefreshAddCount;
        public Integer systemMaxAvailable;
        public Integer playerMaxActive;
        public List<String> systemTaskPool;
        public List<String> systemKillPool;
        public List<String> systemCropsPool;
        public List<String> systemBlocksPool;
        public Map<String, List<Reward>> pendingRefundsByIssuerUuid;
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
