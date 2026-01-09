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
        REVIEW
    }

    private Tab tab = Tab.AVAILABLE;
    private List<BountyBoardManager.BountyView> available = new ArrayList<>();
    private List<BountyBoardManager.BountyView> review = new ArrayList<>();
    private List<String> refreshTimes = new ArrayList<>();
    private boolean isOp = false;

    private int listTop;
    private int listLeft;
    private int listWidth;
    private int rowHeight = 36;
    private int visibleRows = 6;
    private int scrollIndex = 0;

    public BountyBoardScreen(List<BountyBoardManager.BountyView> available, List<BountyBoardManager.BountyView> review, List<String> refreshTimes, boolean isOp) {
        super(Component.literal("悬赏板"));
        updateData(available, review, refreshTimes, isOp);
    }

    public void updateData(List<BountyBoardManager.BountyView> available, List<BountyBoardManager.BountyView> review, List<String> refreshTimes, boolean isOp) {
        this.available = new ArrayList<>(available);
        this.review = new ArrayList<>(review);
        this.refreshTimes = new ArrayList<>(refreshTimes);
        this.isOp = isOp;
        this.scrollIndex = 0;
        rebuild();
    }

    @Override
    protected void init() {
        listWidth = 320;
        listLeft = (this.width - listWidth) / 2;
        listTop = 48;
        rebuild();
    }

    private void rebuild() {
        if (this.minecraft == null) {
            return;
        }
        this.clearWidgets();
        int centerX = this.width / 2;

        addRenderableWidget(Button.builder(Component.literal("可接悬赏"), button -> {
            tab = Tab.AVAILABLE;
            scrollIndex = 0;
            rebuild();
        }).bounds(centerX - 150, 20, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("待验收"), button -> {
            tab = Tab.REVIEW;
            scrollIndex = 0;
            rebuild();
        }).bounds(centerX - 55, 20, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal("刷新"), button -> {
            ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.REQUEST_SYNC, "", "", "", "", "", "", ""));
        }).bounds(centerX + 20, 20, 50, 20).build());

        addRenderableWidget(Button.builder(Component.literal("发布"), button -> {
            this.minecraft.setScreen(new PublishBountyScreen(this));
        }).bounds(centerX + 75, 20, 50, 20).build());

        addRenderableWidget(Button.builder(Component.literal("关闭"), button -> onClose()).bounds(centerX + 130, 20, 50, 20).build());

        List<BountyBoardManager.BountyView> current = tab == Tab.AVAILABLE ? available : review;

        int start = Math.max(0, Math.min(scrollIndex, Math.max(0, current.size() - visibleRows)));
        scrollIndex = start;
        int end = Math.min(current.size(), start + visibleRows);

        for (int i = start; i < end; i++) {
            int row = i - start;
            int y = listTop + row * rowHeight;
            BountyBoardManager.BountyView view = current.get(i);

            if (tab == Tab.AVAILABLE) {
                addRenderableWidget(Button.builder(Component.literal("接取"), button -> {
                    ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.ACCEPT, view.id(), "", "", "", "", "", ""));
                }).bounds(listLeft + listWidth - 46, y + 8, 40, 20).build()).active = view.canAccept();
            } else {
                addRenderableWidget(Button.builder(Component.literal("通过"), button -> {
                    ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.REVIEW_APPROVE, view.id(), "", "", "", "", "", ""));
                }).bounds(listLeft + listWidth - 90, y + 8, 40, 20).build()).active = view.canReview();

                addRenderableWidget(Button.builder(Component.literal("拒绝"), button -> {
                    ClientPlayNetworking.send(new BountyBoardManager.BountyBoardActionPayload(BountyBoardManager.ActionType.REVIEW_DENY, view.id(), "", "", "", "", "", ""));
                }).bounds(listLeft + listWidth - 46, y + 8, 40, 20).build()).active = view.canReview();
            }
        }

        addRenderableWidget(Button.builder(Component.literal("↑"), button -> {
            scrollIndex = Math.max(0, scrollIndex - 1);
            rebuild();
        }).bounds(listLeft + listWidth + 6, listTop, 18, 18).build());

        addRenderableWidget(Button.builder(Component.literal("↓"), button -> {
            List<BountyBoardManager.BountyView> cur = tab == Tab.AVAILABLE ? available : review;
            scrollIndex = Math.min(Math.max(0, cur.size() - visibleRows), scrollIndex + 1);
            rebuild();
        }).bounds(listLeft + listWidth + 6, listTop + 20, 18, 18).build());
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;

        graphics.drawCenteredString(this.font, "悬赏板", centerX, 6, 0xFFFFFF);
        graphics.drawString(this.font, "刷新时间: " + String.join(", ", refreshTimes), 8, this.height - 18, 0xAAAAAA);

        List<BountyBoardManager.BountyView> current = tab == Tab.AVAILABLE ? available : review;
        int start = Math.max(0, Math.min(scrollIndex, Math.max(0, current.size() - visibleRows)));
        int end = Math.min(current.size(), start + visibleRows);

        for (int i = start; i < end; i++) {
            int row = i - start;
            int y = listTop + row * rowHeight;
            int x = listLeft + 6;
            BountyBoardManager.BountyView view = current.get(i);

            graphics.drawString(this.font, view.title(), x, y, 0xFFFFFF);
            graphics.drawString(this.font, view.description(), x, y + 10, 0xAAAAAA);
            graphics.drawString(this.font, "任务: " + view.requirementText(), x, y + 20, 0x55FFFF);
            graphics.drawString(this.font, "奖励: " + view.rewardText(), x, y + 30, 0xFFDD55);

            if (tab == Tab.REVIEW) {
                graphics.drawString(this.font, "提交者: " + view.reviewAccepterName(), listLeft + listWidth - 150, y, 0xFFAA00);
            }
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
            int top = 30;

            titleBox = new EditBox(this.font, left, top, 320, 20, Component.literal(""));
            titleBox.setMaxLength(32);
            titleBox.setValue("急单");
            addRenderableWidget(titleBox);

            descBox = new EditBox(this.font, left, top + 26, 320, 20, Component.literal(""));
            descBox.setMaxLength(200);
            descBox.setValue("先到先得，别问，问就是缺人。");
            addRenderableWidget(descBox);

            reqBox = new EditBox(this.font, left, top + 52, 320, 20, Component.literal(""));
            reqBox.setMaxLength(200);
            reqBox.setValue("16");
            addRenderableWidget(reqBox);

            rewardBox = new EditBox(this.font, left, top + 78, 320, 20, Component.literal(""));
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
            }).bounds(left, top + 104, 140, 20).build());

            addRenderableWidget(Button.builder(Component.literal("奖励: " + rewardType), button -> {
                rewardType = "ITEM".equals(rewardType) ? "COMMAND" : "ITEM";
                button.setMessage(Component.literal("奖励: " + rewardType));
                if ("ITEM".equals(rewardType)) {
                    rewardBox.setValue("1");
                } else {
                    rewardBox.setValue("give {player} minecraft:diamond 1");
                }
            }).bounds(left + 150, top + 104, 170, 20).build());

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
            }).bounds(left, top + 132, 100, 20).build());

            addRenderableWidget(Button.builder(Component.literal("返回"), button -> Minecraft.getInstance().setScreen(parent)).bounds(left + 110, top + 132, 100, 20).build());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            int centerX = this.width / 2;
            int left = centerX - 160;
            int top = 16;
            graphics.drawCenteredString(this.font, "发布悬赏", centerX, 6, 0xFFFFFF);
            graphics.drawString(this.font, "标题", left, top + 14, 0xAAAAAA);
            graphics.drawString(this.font, "描述", left, top + 40, 0xAAAAAA);
            graphics.drawString(this.font, "要求", left, top + 66, 0xAAAAAA);
            graphics.drawString(this.font, "奖励", left, top + 92, 0xAAAAAA);
            graphics.drawString(this.font, "ITEM 要求: 副手拿目标物品；奖励 ITEM: 主手拿奖励物品", left, this.height - 18, 0x777777);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}

