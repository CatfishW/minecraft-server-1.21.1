package com.novus.items.client.bounty;

import com.novus.items.bounty.BountyBoardManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class TaskPoolScreen extends Screen {
    private final BountyBoardScreen boardScreen;
    private final Screen backScreen;

    private List<String> entries = new ArrayList<>();
    private int pageIndex = 0;
    private int listLeft;
    private int listTop;
    private int listWidth;
    private int rowHeight = 30;
    private int visibleRows = 7;

    public TaskPoolScreen(BountyBoardScreen boardScreen, Screen backScreen) {
        super(Component.literal("任务池"));
        this.boardScreen = boardScreen;
        this.backScreen = backScreen;
        this.entries = new ArrayList<>(boardScreen.getTaskPool());
    }

    public void updatePool(List<String> entries) {
        this.entries = new ArrayList<>(entries);
        this.boardScreen.setTaskPool(entries);
        rebuild();
    }

    @Override
    protected void init() {
        listWidth = 360;
        listLeft = (this.width - listWidth) / 2;
        listTop = 44;
        rebuild();
    }

    private void rebuild() {
        if (this.minecraft == null) {
            return;
        }
        this.clearWidgets();
        int centerX = this.width / 2;
        int topY = 14;

        addRenderableWidget(Button.builder(Component.literal("新增"), button -> this.minecraft.setScreen(new EditEntryScreen(this, null)))
            .bounds(centerX - 150, topY, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("随机添加"), button -> {
            ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.POOL_ADD_RANDOM, "", "", "", "", "", "", ""));
            ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.REQUEST_SYNC, "", "", "", "", "", "", ""));
        }).bounds(centerX - 80, topY, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("刷新"), button -> ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.REQUEST_SYNC, "", "", "", "", "", "", "")))
            .bounds(centerX - 2, topY, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), button -> this.minecraft.setScreen(backScreen))
            .bounds(centerX + 68, topY, 60, 20).build());

        int pageSize = visibleRows;
        int totalPages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        pageIndex = Math.max(0, Math.min(totalPages - 1, pageIndex));
        int start = pageIndex * pageSize;
        int end = Math.min(entries.size(), start + pageSize);

        for (int i = start; i < end; i++) {
            int row = i - start;
            int y = listTop + row * rowHeight;
            String raw = entries.get(i);

            addRenderableWidget(Button.builder(Component.literal("改"), button -> this.minecraft.setScreen(new EditEntryScreen(this, raw)))
                .bounds(listLeft + listWidth - 88, y + 3, 40, 20).build());

            addRenderableWidget(Button.builder(Component.literal("删"), button -> {
                ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.POOL_REMOVE, raw, "", "", "", "", "", ""));
                ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.REQUEST_SYNC, "", "", "", "", "", "", ""));
            }).bounds(listLeft + listWidth - 44, y + 3, 40, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("〈"), button -> {
            pageIndex = Math.max(0, pageIndex - 1);
            rebuild();
        }).bounds(listLeft - 22, listTop - 24, 18, 18).build()).active = pageIndex > 0;

        addRenderableWidget(Button.builder(Component.literal("〉"), button -> {
            pageIndex = Math.min(totalPages - 1, pageIndex + 1);
            rebuild();
        }).bounds(listLeft + listWidth + 4, listTop - 24, 18, 18).build()).active = pageIndex < totalPages - 1;
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, "任务池", centerX, 6, 0xFFFFFF);

        int pageSize = visibleRows;
        int totalPages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int start = Math.max(0, Math.min(totalPages - 1, pageIndex)) * pageSize;
        int end = Math.min(entries.size(), start + pageSize);

        graphics.drawString(this.font, "条目: " + entries.size(), listLeft, listTop - 18, 0xAAAAAA);
        graphics.drawCenteredString(this.font, "第 " + (Math.max(0, Math.min(totalPages - 1, pageIndex)) + 1) + "/" + totalPages + " 页", centerX, listTop - 18, 0xAAAAAA);

        for (int i = start; i < end; i++) {
            int row = i - start;
            int y = listTop + row * rowHeight;
            String raw = entries.get(i);

            int rowLeft = listLeft;
            int rowRight = listLeft + listWidth;
            int rowTop = y;
            int rowBottom = y + rowHeight - 1;
            int border = 0x55FFFFFF;
            graphics.fill(rowLeft, rowTop, rowRight, rowTop + 1, border);
            graphics.fill(rowLeft, rowBottom, rowRight, rowBottom + 1, border);
            graphics.fill(rowLeft, rowTop, rowLeft + 1, rowBottom + 1, border);
            graphics.fill(rowRight - 1, rowTop, rowRight, rowBottom + 1, border);

            String kind = kindOf(raw);
            String id = idOf(raw);
            String reward = rewardOf(raw);
            renderIcon(graphics, kind, id, listLeft + 6, y);

            int textX = listLeft + 34;
            float scale = 0.9F;
            drawScaledString(graphics, kindLabel(kind) + " | " + id, textX, y + 6, 0xFFFFFF, scale);
            drawScaledString(graphics, "奖励: " + reward, textX, y + 18, 0xAAAAAA, 0.8F);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String kindOf(String raw) {
        if (raw == null) return "";
        String[] parts = raw.trim().split("\\s+", 2);
        return parts.length >= 1 ? parts[0] : "";
    }

    private static String idOf(String raw) {
        if (raw == null) return "";
        String[] halves = raw.trim().split("\\|", 2);
        String left = halves[0].trim();
        String[] parts = left.split("\\s+", 2);
        return parts.length == 2 ? parts[1] : "";
    }

    private static String rewardOf(String raw) {
        if (raw == null) return "";
        String[] halves = raw.trim().split("\\|", 2);
        if (halves.length != 2) {
            return "novus_items:bronze_novus_coin 10";
        }
        String reward = halves[1].trim();
        return reward.isEmpty() ? "novus_items:bronze_novus_coin 10" : reward;
    }

    private static String kindLabel(String kind) {
        if (kind == null) return "";
        return switch (kind.toUpperCase()) {
            case "KILL" -> "击杀";
            case "CROPS" -> "作物";
            case "BLOCKS" -> "建材";
            default -> kind;
        };
    }

    private void drawScaledString(net.minecraft.client.gui.GuiGraphics graphics, String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, (int) (x / scale), (int) (y / scale), color);
        graphics.pose().popPose();
    }

    private void renderIcon(net.minecraft.client.gui.GuiGraphics graphics, String kind, String id, int x, int y) {
        net.minecraft.world.item.ItemStack icon;
        if ("KILL".equalsIgnoreCase(kind)) {
            icon = itemStackFromId("minecraft:iron_sword");
        } else {
            icon = itemStackFromId(id);
        }
        if (icon.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        float scale = 0.75F;
        graphics.pose().scale(scale, scale, 1.0F);
        int sx = (int) (x / scale);
        int sy = (int) ((y + 7) / scale);
        graphics.renderItem(icon, sx, sy);
        graphics.pose().popPose();
    }

    private static net.minecraft.world.item.ItemStack itemStackFromId(String id) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl == null) return net.minecraft.world.item.ItemStack.EMPTY;
        if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) return net.minecraft.world.item.ItemStack.EMPTY;
        return new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl));
    }

    private static class EditEntryScreen extends Screen {
        private final TaskPoolScreen parent;
        private final String oldEntry;
        private String kind = "KILL";
        private EditBox idBox;
        private EditBox rewardIdBox;
        private EditBox rewardCountBox;

        protected EditEntryScreen(TaskPoolScreen parent, String oldEntry) {
            super(Component.literal(oldEntry == null ? "新增任务池条目" : "修改任务池条目"));
            this.parent = parent;
            this.oldEntry = oldEntry;
            if (oldEntry != null) {
                String k = kindOf(oldEntry);
                String id = idOf(oldEntry);
                if (!k.isEmpty()) {
                    kind = k.toUpperCase();
                }
                if (!id.isEmpty()) {
                    this.idValue = id;
                }
                String reward = rewardOf(oldEntry);
                String[] rp = reward.trim().split("\\s+");
                if (rp.length >= 1) {
                    this.rewardIdValue = rp[0].trim();
                }
                if (rp.length >= 2) {
                    this.rewardCountValue = rp[1].trim();
                }
            }
        }

        private String idValue = "";
        private String rewardIdValue = "novus_items:bronze_novus_coin";
        private String rewardCountValue = "10";

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int left = centerX - 160;
            int top = 40;

            addRenderableWidget(Button.builder(Component.literal(kindLabel(kind)), button -> {
                kind = switch (kind) {
                    case "KILL" -> "CROPS";
                    case "CROPS" -> "BLOCKS";
                    default -> "KILL";
                };
                button.setMessage(Component.literal(kindLabel(kind)));
            }).bounds(left, top, 80, 20).build());

            idBox = new EditBox(this.font, left + 90, top, 230, 20, Component.literal(""));
            idBox.setMaxLength(110);
            idBox.setValue(idValue);
            addRenderableWidget(idBox);

            int row2Y = top + 28;
            rewardIdBox = new EditBox(this.font, left + 90, row2Y, 170, 20, Component.literal(""));
            rewardIdBox.setMaxLength(110);
            rewardIdBox.setValue(rewardIdValue);
            addRenderableWidget(rewardIdBox);

            rewardCountBox = new EditBox(this.font, left + 270, row2Y, 50, 20, Component.literal(""));
            rewardCountBox.setMaxLength(3);
            rewardCountBox.setValue(rewardCountValue);
            addRenderableWidget(rewardCountBox);

            addRenderableWidget(Button.builder(Component.literal("保存"), button -> {
                String id = idBox.getValue() == null ? "" : idBox.getValue().trim();
                String rewardId = rewardIdBox.getValue() == null ? "" : rewardIdBox.getValue().trim();
                String rewardCount = rewardCountBox.getValue() == null ? "" : rewardCountBox.getValue().trim();
                String entry = kind + " " + id + " | " + rewardId + " " + rewardCount;
                if (oldEntry == null) {
                    ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.POOL_ADD, entry, "", "", "", "", "", ""));
                } else {
                    ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.POOL_REPLACE, oldEntry, entry, "", "", "", "", ""));
                }
                ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.REQUEST_SYNC, "", "", "", "", "", "", ""));
                Minecraft.getInstance().setScreen(parent);
            }).bounds(left, top + 62, 80, 20).build());

            addRenderableWidget(Button.builder(Component.literal("返回"), button -> Minecraft.getInstance().setScreen(parent))
                .bounds(left + 90, top + 62, 80, 20).build());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            int centerX = this.width / 2;
            graphics.drawCenteredString(this.font, this.title.getString(), centerX, 10, 0xFFFFFF);
            int left = centerX - 160;
            graphics.drawString(this.font, "格式: 类型 + id", left, 24, 0x777777);
            graphics.drawString(this.font, "例: KILL minecraft:zombie", left, 34, 0x777777);
            graphics.drawString(this.font, "奖励: 物品id 数量  (默认铜币)", left, 44, 0x777777);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
