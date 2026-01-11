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

public class BountyBoardScreen extends Screen {
    private enum Tab {
        AVAILABLE,
        MY
    }

    private Tab tab = Tab.AVAILABLE;
    private List<BountyBoardManager.BountyView> available = new ArrayList<>();
    private List<BountyBoardManager.BountyView> my = new ArrayList<>();
    private List<String> refreshTimes = new ArrayList<>();
    private List<String> taskPool = new ArrayList<>();
    private boolean isOp = false;
    private int systemRefreshAddCount = 3;
    private int playerMaxActive = 1;

    private int listTop;
    private int listLeft;
    private int listWidth;
    private int rowHeight = 30;
    private int visibleRows = 5;
    private int pageIndex = 0;

    public BountyBoardScreen(List<BountyBoardManager.BountyView> available, List<BountyBoardManager.BountyView> my, List<String> refreshTimes, boolean isOp, int systemRefreshAddCount, int playerMaxActive, List<String> taskPool) {
        super(Component.literal("悬赏板"));
        updateData(available, my, refreshTimes, isOp, systemRefreshAddCount, playerMaxActive, taskPool);
    }

    public void updateData(List<BountyBoardManager.BountyView> available, List<BountyBoardManager.BountyView> my, List<String> refreshTimes, boolean isOp, int systemRefreshAddCount, int playerMaxActive, List<String> taskPool) {
        this.available = new ArrayList<>(available);
        this.my = new ArrayList<>(my);
        this.refreshTimes = new ArrayList<>(refreshTimes);
        this.taskPool = new ArrayList<>(taskPool);
        this.isOp = isOp;
        this.systemRefreshAddCount = systemRefreshAddCount;
        this.playerMaxActive = playerMaxActive;
        rebuild();
    }

    public List<String> getTaskPool() {
        return new ArrayList<>(taskPool);
    }

    public void setTaskPool(List<String> taskPool) {
        this.taskPool = new ArrayList<>(taskPool);
    }

    @Override
    protected void init() {
        listWidth = 360;
        listLeft = (this.width - listWidth) / 2;
        listTop = 50;
        rebuild();
    }

    private void rebuild() {
        if (this.minecraft == null) {
            return;
        }
        this.clearWidgets();
        int centerX = this.width / 2;

        int topY = 18;
        int gap = 6;
        List<ButtonSpec> specs = new ArrayList<>();
        specs.add(new ButtonSpec("可接悬赏", 90, () -> {
            tab = Tab.AVAILABLE;
            pageIndex = 0;
            rebuild();
        }));
        specs.add(new ButtonSpec("我的任务", 70, () -> {
            tab = Tab.MY;
            pageIndex = 0;
            rebuild();
        }));
        specs.add(new ButtonSpec("刷新", 50, () -> {
            ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.REQUEST_SYNC, "", "", "", "", "", "", ""));
        }));
        specs.add(new ButtonSpec("发布", 50, () -> {
            this.minecraft.setScreen(new PublishBountyScreen(this));
        }));
        if (isOp) {
            specs.add(new ButtonSpec("管理", 50, () -> this.minecraft.setScreen(new AdminTimeScreen(this))));
        }
        specs.add(new ButtonSpec("关闭", 50, this::onClose));

        int totalW = -gap;
        for (ButtonSpec spec : specs) {
            totalW += spec.width + gap;
        }
        int x = centerX - totalW / 2;
        for (ButtonSpec spec : specs) {
            addRenderableWidget(Button.builder(Component.literal(spec.label), button -> spec.onPress.run()).bounds(x, topY, spec.width, 20).build());
            x += spec.width + gap;
        }

        List<BountyBoardManager.BountyView> current = tab == Tab.AVAILABLE ? available : my;

        int pageSize = visibleRows;
        int totalPages = Math.max(1, (current.size() + pageSize - 1) / pageSize);
        pageIndex = Math.max(0, Math.min(totalPages - 1, pageIndex));
        int start = pageIndex * pageSize;
        int end = Math.min(current.size(), start + pageSize);

        for (int i = start; i < end; i++) {
            int row = i - start;
            int y = listTop + row * rowHeight;
            BountyBoardManager.BountyView view = current.get(i);

            if (tab == Tab.AVAILABLE) {
                if (isOp) {
                    int btnW = 34;
                    int btnGap = 4;
                    int total = btnW * 3 + btnGap * 2;
                    int bx = listLeft + listWidth - 4 - total;
                    addRenderableWidget(Button.builder(Component.literal("接"), button -> {
                        ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.ACCEPT, view.id(), "", "", "", "", "", ""));
                    }).bounds(bx, y + 5, btnW, 20).build()).active = view.canAccept();
                    addRenderableWidget(Button.builder(Component.literal("删"), button -> {
                        ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.ADMIN_DELETE, view.id(), "", "", "", "", "", ""));
                    }).bounds(bx + btnW + btnGap, y + 5, btnW, 20).build());
                    addRenderableWidget(Button.builder(Component.literal("完"), button -> {
                        ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.ADMIN_FORCE_COMPLETE, view.id(), "", "", "", "", "", ""));
                    }).bounds(bx + (btnW + btnGap) * 2, y + 5, btnW, 20).build());
                } else {
                    addRenderableWidget(Button.builder(Component.literal("接取"), button -> {
                        ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.ACCEPT, view.id(), "", "", "", "", "", ""));
                    }).bounds(listLeft + listWidth - 46, y + 5, 40, 20).build()).active = view.canAccept();
                }
            } else {
                int btnW = 40;
                int btnGap = 4;
                int right = listLeft + listWidth - 6;
                int by = y + 5;

                if (view.canReview()) {
                    if (view.canCancel()) {
                        addRenderableWidget(Button.builder(Component.literal("取消"), button -> {
                            ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.CANCEL_PUBLISHED, view.id(), "", "", "", "", "", ""));
                        }).bounds(right - btnW * 3 - btnGap * 2, by, btnW, 20).build()).active = true;
                    }
                    addRenderableWidget(Button.builder(Component.literal("通过"), button -> {
                        ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.REVIEW_APPROVE, view.id(), "", "", "", "", "", ""));
                    }).bounds(right - btnW * 2 - btnGap, by, btnW, 20).build()).active = true;

                    addRenderableWidget(Button.builder(Component.literal("拒绝"), button -> {
                        ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.REVIEW_DENY, view.id(), "", "", "", "", "", ""));
                    }).bounds(right - btnW, by, btnW, 20).build()).active = true;
                } else if (view.mineAccepted()) {
                    boolean submitActive = view.canSubmit();
                    if ("ITEM_DELIVERY".equals(view.type())) {
                        int need = Math.max(1, view.requirementCount());
                        submitActive = countInventoryItem(view.requirementId()) >= need;
                    }
                    addRenderableWidget(Button.builder(Component.literal("提交"), button -> {
                        ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.SUBMIT, view.id(), "", "", "", "", "", ""));
                    }).bounds(right - btnW * 2 - btnGap, by, btnW, 20).build()).active = submitActive;

                    addRenderableWidget(Button.builder(Component.literal("放弃"), button -> {
                        ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.ABANDON_ACTIVE, view.id(), "", "", "", "", "", ""));
                    }).bounds(right - btnW, by, btnW, 20).build()).active = view.canAbandon();
                } else if (view.canCancel()) {
                    addRenderableWidget(Button.builder(Component.literal("取消"), button -> {
                        ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.CANCEL_PUBLISHED, view.id(), "", "", "", "", "", ""));
                    }).bounds(right - btnW, by, btnW, 20).build()).active = true;
                }
            }
        }

        addRenderableWidget(Button.builder(Component.literal("〈"), button -> {
            pageIndex = Math.max(0, pageIndex - 1);
            rebuild();
        }).bounds(listLeft - 22, listTop - 24, 18, 18).build()).active = pageIndex > 0;

        addRenderableWidget(Button.builder(Component.literal("〉"), button -> {
            pageIndex = Math.min(totalPages - 1, pageIndex + 1);
            rebuild();
        }).bounds(listLeft + listWidth + 4, listTop - 24, 18, 18).build()).active = pageIndex < totalPages - 1;

        int footerY = listTop + visibleRows * rowHeight + 6;
        addRenderableWidget(Button.builder(Component.literal("上一页"), button -> {
            pageIndex = Math.max(0, pageIndex - 1);
            rebuild();
        }).bounds(listLeft, footerY + 10, 60, 18).build()).active = pageIndex > 0;

        addRenderableWidget(Button.builder(Component.literal("下一页"), button -> {
            pageIndex = Math.min(totalPages - 1, pageIndex + 1);
            rebuild();
        }).bounds(listLeft + listWidth - 60, footerY + 10, 60, 18).build()).active = pageIndex < totalPages - 1;
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;

        graphics.drawCenteredString(this.font, "悬赏板", centerX, 6, 0xFFFFFF);
        int footerY = listTop + visibleRows * rowHeight + 6;
        graphics.drawString(this.font, "刷新: " + String.join(", ", refreshTimes), listLeft, footerY, 0xAAAAAA);
        List<BountyBoardManager.BountyView> current = tab == Tab.AVAILABLE ? available : my;
        graphics.drawString(this.font, "数量: " + current.size(), listLeft + listWidth - 70, footerY, 0xAAAAAA);
        int pageSize = visibleRows;
        int totalPages = Math.max(1, (current.size() + pageSize - 1) / pageSize);
        int start = Math.max(0, Math.min(totalPages - 1, pageIndex)) * pageSize;
        int end = Math.min(current.size(), start + pageSize);
        graphics.drawCenteredString(this.font, "第 " + (Math.max(0, Math.min(totalPages - 1, pageIndex)) + 1) + "/" + totalPages + " 页", centerX, footerY, 0xAAAAAA);

        for (int i = start; i < end; i++) {
            int row = i - start;
            int y = listTop + row * rowHeight;
            BountyBoardManager.BountyView view = current.get(i);

            int rowLeft = listLeft;
            int rowRight = listLeft + listWidth;
            int rowTop = y;
            int rowBottom = y + rowHeight - 1;
            int border = 0x55FFFFFF;
            graphics.fill(rowLeft, rowTop, rowRight, rowTop + 1, border);
            graphics.fill(rowLeft, rowBottom, rowRight, rowBottom + 1, border);
            graphics.fill(rowLeft, rowTop, rowLeft + 1, rowBottom + 1, border);
            graphics.fill(rowRight - 1, rowTop, rowRight, rowBottom + 1, border);

            renderIcons(graphics, view, listLeft + 4, y);

            int textX = listLeft + 46;
            float scale = 0.85F;
            int tagColor = view.system() ? 0xFFAA00 : 0x55FF55;
            String titleLine;
            if (tab == Tab.AVAILABLE) {
                String source = view.system() ? "系统派单" : "玩家派单";
                String issuer = view.issuerName().isEmpty() ? "" : (" · " + view.issuerName());
                titleLine = source + issuer + " | " + view.title();
            } else {
                String prefix = view.mineAccepted() ? "我接的" : (view.mineIssued() ? "我发布" : "待验收");
                titleLine = prefix + " | " + view.title();
                tagColor = view.mineAccepted() ? 0x55FFFF : 0xFFDD55;
            }

            String taskLine = "任务: " + requirementText(view);
            if (tab == Tab.MY && view.mineAccepted() && "ITEM_DELIVERY".equals(view.type()) && this.minecraft != null && this.minecraft.player != null) {
                int have = countInventoryItem(view.requirementId());
                int need = Math.max(1, view.requirementCount());
                taskLine = "任务: " + itemName(view.requirementId()) + " (" + Math.min(have, need) + "/" + need + ")";
            }
            String rewardLine = "奖励: " + rewardText(view);

            drawScaledString(graphics, titleLine, textX, y + 2, tagColor, scale);
            drawScaledString(graphics, taskLine, textX, y + 12, 0x55FFFF, scale);
            drawScaledString(graphics, rewardLine, textX, y + 22, 0xFFDD55, scale);

            if (tab == Tab.MY && view.mineIssued() && view.accepterName() != null && !view.accepterName().isEmpty()) {
                drawScaledString(graphics, "接取者: " + view.accepterName(), listLeft + listWidth - 160, y + 2, 0xFFAA00, scale);
            }
        }

        int shown = end - start;
        for (int row = shown; row < visibleRows; row++) {
            int y = listTop + row * rowHeight;
            int rowLeft = listLeft;
            int rowRight = listLeft + listWidth;
            int rowTop = y;
            int rowBottom = y + rowHeight - 1;
            int border = 0x22FFFFFF;
            graphics.fill(rowLeft, rowTop, rowRight, rowTop + 1, border);
            graphics.fill(rowLeft, rowBottom, rowRight, rowBottom + 1, border);
            graphics.fill(rowLeft, rowTop, rowLeft + 1, rowBottom + 1, border);
            graphics.fill(rowRight - 1, rowTop, rowRight, rowBottom + 1, border);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class PublishBountyScreen extends Screen {
        private final BountyBoardScreen parent;
        private EditBox titleBox;
        private EditBox descBox;
        private EditBox reqBox;
        private EditBox rewardBox;
        private String bountyType = "ITEM";
        private String rewardType = "ITEM";

        protected PublishBountyScreen(BountyBoardScreen parent) {
            super(Component.literal("发布悬赏"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int left = centerX - 160;
            int top = 34;

            titleBox = new EditBox(this.font, left, top + 12, 320, 20, Component.literal(""));
            titleBox.setMaxLength(32);
            titleBox.setValue("急单");
            addRenderableWidget(titleBox);

            descBox = new EditBox(this.font, left, top + 52, 320, 20, Component.literal(""));
            descBox.setMaxLength(200);
            descBox.setValue("先到先得，别问，问就是缺人。");
            addRenderableWidget(descBox);

            reqBox = new EditBox(this.font, left, top + 92, 320, 20, Component.literal(""));
            reqBox.setMaxLength(200);
            reqBox.setValue("16");
            addRenderableWidget(reqBox);

            rewardBox = new EditBox(this.font, left, top + 132, 320, 20, Component.literal(""));
            rewardBox.setMaxLength(200);
            rewardBox.setValue("1");
            addRenderableWidget(rewardBox);

            addRenderableWidget(Button.builder(Component.literal("类型: " + bountyType), button -> {
                bountyType = switch (bountyType) {
                    case "ITEM" -> "KILL";
                    case "KILL" -> "CUSTOM";
                    default -> "ITEM";
                };
                button.setMessage(Component.literal("类型: " + bountyType));
                if ("ITEM".equals(bountyType)) {
                    reqBox.setValue("16");
                } else if ("KILL".equals(bountyType)) {
                    reqBox.setValue("minecraft:zombie 5");
                } else {
                    reqBox.setValue("描述你的自定义要求");
                }
            }).bounds(left, top + 160, 140, 20).build());

            addRenderableWidget(Button.builder(Component.literal("奖励: " + rewardType), button -> {
                rewardType = "ITEM".equals(rewardType) ? "COMMAND" : "ITEM";
                button.setMessage(Component.literal("奖励: " + rewardType));
                if ("ITEM".equals(rewardType)) {
                    rewardBox.setValue("1");
                } else {
                    rewardBox.setValue("give {player} minecraft:diamond 1");
                }
            }).bounds(left + 150, top + 160, 170, 20).build());

            addRenderableWidget(Button.builder(Component.literal("发布"), button -> {
                ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(
                    BountyBoardManager.ActionType.PUBLISH,
                    "",
                    titleBox.getValue(),
                    descBox.getValue(),
                    bountyType,
                    reqBox.getValue(),
                    rewardType,
                    rewardBox.getValue()
                ));
                Minecraft.getInstance().setScreen(parent);
            }).bounds(left, top + 188, 100, 20).build());

            addRenderableWidget(Button.builder(Component.literal("返回"), button -> Minecraft.getInstance().setScreen(parent)).bounds(left + 110, top + 188, 100, 20).build());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            int centerX = this.width / 2;
            int left = centerX - 160;
            int top = 20;
            graphics.drawCenteredString(this.font, "发布悬赏", centerX, 6, 0xFFFFFF);
            graphics.drawString(this.font, "标题", left, top + 20, 0xAAAAAA);
            graphics.drawString(this.font, "描述", left, top + 60, 0xAAAAAA);
            graphics.drawString(this.font, "要求", left, top + 100, 0xAAAAAA);
            graphics.drawString(this.font, "奖励", left, top + 140, 0xAAAAAA);
            graphics.drawString(this.font, "ITEM 要求: 副手拿目标物品；奖励 ITEM: 主手拿奖励物品", left, this.height - 18, 0x777777);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private static class AdminTimeScreen extends Screen {
        private final BountyBoardScreen parent;
        private EditBox timeBox;
        private EditBox addCountBox;
        private EditBox playerMaxActiveBox;

        protected AdminTimeScreen(BountyBoardScreen parent) {
            super(Component.literal("悬赏板管理"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int left = centerX - 160;
            int top = 28;

            int labelW = 84;
            int inputW = 64;
            int btnW = 52;
            int gap = 6;

            int row1Y = top + 24;
            timeBox = new EditBox(this.font, left + labelW, row1Y, inputW, 20, Component.literal(""));
            timeBox.setMaxLength(5);
            timeBox.setValue(parent.refreshTimes.isEmpty() ? "12:00" : parent.refreshTimes.get(0));
            addRenderableWidget(timeBox);

            addRenderableWidget(Button.builder(Component.literal("添加"), button -> {
                ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.ADD_REFRESH_TIME, timeBox.getValue(), "", "", "", "", "", ""));
            }).bounds(left + labelW + inputW + gap, row1Y, btnW, 20).build());

            addRenderableWidget(Button.builder(Component.literal("删除"), button -> {
                ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.REMOVE_REFRESH_TIME, timeBox.getValue(), "", "", "", "", "", ""));
            }).bounds(left + labelW + inputW + gap + btnW + gap, row1Y, btnW, 20).build());

            int row2Y = top + 58;
            addCountBox = new EditBox(this.font, left + labelW, row2Y, inputW, 20, Component.literal(""));
            addCountBox.setMaxLength(3);
            addCountBox.setValue(String.valueOf(parent.systemRefreshAddCount));
            addRenderableWidget(addCountBox);
            addRenderableWidget(Button.builder(Component.literal("设置新增"), button -> {
                ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.SET_REFRESH_ADD_COUNT, addCountBox.getValue(), "", "", "", "", "", ""));
            }).bounds(left + labelW + inputW + gap, row2Y, 72, 20).build());

            int row3Y = top + 92;
            playerMaxActiveBox = new EditBox(this.font, left + labelW, row3Y, inputW, 20, Component.literal(""));
            playerMaxActiveBox.setMaxLength(3);
            playerMaxActiveBox.setValue(String.valueOf(parent.playerMaxActive));
            addRenderableWidget(playerMaxActiveBox);
            addRenderableWidget(Button.builder(Component.literal("设置上限"), button -> {
                ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.SET_PLAYER_MAX_ACTIVE, playerMaxActiveBox.getValue(), "", "", "", "", "", ""));
            }).bounds(left + labelW + inputW + gap, row3Y, 72, 20).build());

            int row4Y = top + 126;
            addRenderableWidget(Button.builder(Component.literal("任务池"), button -> Minecraft.getInstance().setScreen(new TaskPoolScreen(parent, this)))
                .bounds(left, row4Y, 100, 20).build());

            addRenderableWidget(Button.builder(Component.literal("立即刷新"), button -> {
                ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.REFRESH_NOW, "", "", "", "", "", "", ""));
            }).bounds(left, top + 160, 100, 20).build());

            addRenderableWidget(Button.builder(Component.literal("返回"), button -> Minecraft.getInstance().setScreen(parent)).bounds(left + 110, top + 160, 80, 20).build());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            int centerX = this.width / 2;
            graphics.drawCenteredString(this.font, "悬赏板管理", centerX, 6, 0xFFFFFF);
            int left = centerX - 160;
            int top = 28;
            graphics.drawString(this.font, "刷新时间", left, top + 28, 0xAAAAAA);
            graphics.drawString(this.font, "每次新增", left, top + 62, 0xAAAAAA);
            graphics.drawString(this.font, "玩家最大接取", left, top + 96, 0xAAAAAA);
            graphics.drawString(this.font, "任务池", left, top + 130, 0xAAAAAA);

            int valueX = left + 240;
            graphics.drawString(this.font, "当前: " + parent.systemRefreshAddCount, valueX, top + 62, 0x777777);
            graphics.drawString(this.font, "当前: " + parent.playerMaxActive, valueX, top + 96, 0x777777);
            graphics.drawString(this.font, "条目数: " + parent.taskPool.size(), left + 100, top + 130, 0x777777);
            graphics.drawString(this.font, "当前刷新点: " + String.join(", ", parent.refreshTimes), left, top + 194, 0x777777);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private void renderIcons(net.minecraft.client.gui.GuiGraphics graphics, BountyBoardManager.BountyView view, int x, int y) {
        net.minecraft.world.item.ItemStack icon = net.minecraft.world.item.ItemStack.EMPTY;
        if ("ITEM_DELIVERY".equals(view.type())) {
            icon = itemStackFromId(view.requirementId());
        } else if ("ENTITY_KILL".equals(view.type())) {
            icon = itemStackFromId("minecraft:iron_sword");
        }
        if (icon.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 0);
        float scale = 0.75F;
        graphics.pose().scale(scale, scale, 1.0F);
        int sx = (int) ((x + 2) / scale);
        int sy = (int) ((y + 10) / scale);
        graphics.renderItem(icon, sx, sy);
        graphics.pose().popPose();
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

    private int countInventoryItem(String itemId) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return 0;
        }
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (rl == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) {
            return 0;
        }
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        int found = 0;
        for (net.minecraft.world.item.ItemStack stack : this.minecraft.player.getInventory().items) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() != item) continue;
            found += stack.getCount();
        }
        return found;
    }

    private String itemName(String itemId) {
        net.minecraft.world.item.ItemStack stack = itemStackFromId(itemId);
        return stack.isEmpty() ? itemId : stack.getHoverName().getString();
    }

    private static String requirementText(BountyBoardManager.BountyView view) {
        if ("ITEM_DELIVERY".equals(view.type())) {
            net.minecraft.world.item.ItemStack stack = itemStackFromId(view.requirementId());
            String name = stack.isEmpty() ? view.requirementId() : stack.getHoverName().getString();
            return name + " x" + Math.max(1, view.requirementCount());
        }
        if ("ENTITY_KILL".equals(view.type())) {
            String name = entityName(view.requirementId());
            int need = Math.max(1, view.requirementCount());
            int prog = Math.max(0, view.progress());
            int target = Math.max(1, view.target());
            return name + " x" + need + " (" + prog + "/" + target + ")";
        }
        return view.requirementId();
    }

    private static String rewardText(BountyBoardManager.BountyView view) {
        if (view.rewardIsCommand()) {
            return "指令奖励";
        }
        if (view.rewardItemId() == null || view.rewardItemId().isEmpty()) {
            return "";
        }
        net.minecraft.world.item.ItemStack stack = itemStackFromId(view.rewardItemId());
        String name = stack.isEmpty() ? view.rewardItemId() : stack.getHoverName().getString();
        return name + " x" + Math.max(1, view.rewardItemCount());
    }

    private static String entityName(String id) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl == null) {
            return id == null ? "" : id;
        }
        if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
            return id;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(rl).getDescription().getString();
    }

    private record ButtonSpec(String label, int width, Runnable onPress) {
    }

    private static net.minecraft.world.item.ItemStack itemStackFromId(String id) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl == null) return net.minecraft.world.item.ItemStack.EMPTY;
        if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) return net.minecraft.world.item.ItemStack.EMPTY;
        return new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl));
    }
}
