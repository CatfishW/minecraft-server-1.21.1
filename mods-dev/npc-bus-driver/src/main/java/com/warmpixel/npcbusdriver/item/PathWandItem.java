package com.warmpixel.npcbusdriver.item;

import com.warmpixel.npcbusdriver.PathManager;
import com.warmpixel.npcbusdriver.network.OpenWandGuiPayload;
import de.markusbordihn.easynpc.config.NPCTemplateManager;
import de.markusbordihn.easynpc.config.NPCTemplateData;
import de.markusbordihn.easynpc.config.NPCTemplateData.ActionConfig;
import de.markusbordihn.easynpc.config.NPCTemplateData.ActionEvent;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import com.warmpixel.npcbusdriver.BusDriverManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PathWandItem extends Item {

    public PathWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide || player == null) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            // Shift+RightClick block: Open GUI (same as air)
            openGui((ServerPlayer) player, stack);
        } else {
            // Add point
            addPoint(stack, pos, player);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        if (!level.isClientSide && player.isShiftKeyDown()) {
             openGui((ServerPlayer) player, stack);
             return InteractionResultHolder.success(stack);
        }
        
        return InteractionResultHolder.pass(stack);
    }
    
    private void openGui(ServerPlayer player, ItemStack stack) {
        List<BlockPos> points = getPoints(stack);
        ServerPlayNetworking.send(player, new OpenWandGuiPayload(points));
    }

    private void addPoint(ItemStack stack, BlockPos pos, Player player) {
        List<BlockPos> points = getPoints(stack);
        points.add(pos);
        savePoints(stack, points);
        player.sendSystemMessage(Component.literal("§a[Path Wand] §fAdded point " + points.size() + ": " + pos.toShortString()));
    }
    
    // Called by Packet Handler
    public void removePoint(ItemStack stack, int index) {
        List<BlockPos> points = getPoints(stack);
        if (index >= 0 && index < points.size()) {
            points.remove(index);
            savePoints(stack, points);
        }
    }

    public void spawnDriver(ServerLevel level, Player player, ItemStack stack, String vehicleId) {
             List<BlockPos> points = getPoints(stack);
             if (points.isEmpty()) {
                 player.sendSystemMessage(Component.literal("§c[Path Wand] §fPath is empty."));
                 return;
             }

             // Save "wand_temp" path
             try {
                PathManager.savePath("wand_temp", points);
             } catch (Throwable e) {
                 e.printStackTrace();
                 player.sendSystemMessage(Component.literal("§cFailed to save temp path: " + e.getMessage()));
                 return;
             }
             
             // Check valid template
             Optional<NPCTemplateData> templateOpt = NPCTemplateManager.getTemplate("npc_100_bus_driver");
             if (templateOpt.isEmpty()) {
                 player.sendSystemMessage(Component.literal("§c[Path Wand] §fTemplate 'npc_100_bus_driver' not found."));
                 return;
             }

             NPCTemplateData template = templateOpt.get();
             // Manually copy properties to avoid side effects on original
             NPCTemplateData newTemplate = new NPCTemplateData();
             newTemplate.setName(template.getName());
             newTemplate.setEntityType(template.getEntityType());
             newTemplate.setSkin(template.getSkin());
             newTemplate.setDialog(template.getDialog());
             newTemplate.setDialogs(template.getDialogs());
             newTemplate.setAttributes(template.getAttributes());
             newTemplate.setEquipment(template.getEquipment());
             newTemplate.setDrop(template.getDrop());
             newTemplate.setActions(template.getActions());
             newTemplate.setActionPermissionLevel(template.getActionPermissionLevel());
             newTemplate.setFaction(template.getFaction());
             newTemplate.setObjectives(template.getObjectives());

             BlockPos start = points.get(0);
             boolean spawned = NPCTemplateManager.spawnFromTemplate(level, newTemplate, start.getX(), start.getY(), start.getZ());
             
             if (spawned) {
                 // Try to find the NPC just spawned
                 // Assuming it's at the start position and matches the name "Bus Driver" (from template) or just is a Mob
                 // A tightly bounded check
                 net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(start).inflate(2.0);
                 List<net.minecraft.world.entity.LivingEntity> entities = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, searchBox, e -> {
                     return e instanceof net.minecraft.world.entity.Mob; 
                 });
                 
                 // Sort by distance to start center? Or just take nearest.
                 entities.sort((e1, e2) -> Double.compare(e1.distanceToSqr(start.getX()+0.5, start.getY(), start.getZ()+0.5), e2.distanceToSqr(start.getX()+0.5, start.getY(), start.getZ()+0.5)));
                 
                 if (!entities.isEmpty()) {
                     // We assume the closest new mob is our guy.
                     // Ideally we check age < 5 ticks or something but that's hard.
                     Entity npc = entities.get(0);
                     
                     // Run setup logic
                     if (BusDriverManager.setupBusDriver(level, npc, "wand_temp", vehicleId) != null) {
                        player.sendSystemMessage(Component.literal("§a[Path Wand] §fSpawned Bus Driver & Vehicle."));
                     } else {
                        player.sendSystemMessage(Component.literal("§c[Path Wand] §fSpawned NPC but Vehicle setup failed (check server log)."));
                     }
                 } else {
                     player.sendSystemMessage(Component.literal("§c[Path Wand] §fSpawned NPC but couldn't find it to attach vehicle!"));
                 }
             } else {
                 player.sendSystemMessage(Component.literal("§c[Path Wand] §fFailed to spawn NPC template."));
             }
    }
    
    // NBT Helpers
    public List<BlockPos> getPoints(ItemStack stack) {
        List<BlockPos> points = new ArrayList<>();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("Points", Tag.TAG_LIST)) {
                ListTag list = tag.getList("Points", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag pt = list.getCompound(i);
                    points.add(new BlockPos(pt.getInt("x"), pt.getInt("y"), pt.getInt("z")));
                }
            }
        }
        return points;
    }

    public void savePoints(ItemStack stack, List<BlockPos> points) {
        CompoundTag tag;
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (existing != null) {
             tag = existing.copyTag();
        } else {
             tag = new CompoundTag();
        }

        ListTag list = new ListTag();
        for (BlockPos pos : points) {
           CompoundTag pt = new CompoundTag();
           pt.putInt("x", pos.getX());
           pt.putInt("y", pos.getY());
           pt.putInt("z", pos.getZ());
           list.add(pt);
        }
        tag.put("Points", list);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // Visualization using particles
    public void visualizePath(ServerLevel level, Player player, ItemStack stack) {
        if (level.getGameTime() % 4 != 0) return;

        List<BlockPos> points = getPoints(stack);
        if (points.size() < 2) {
            for (BlockPos p : points) {
                 level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 1, 0, 0, 0, 0);
            }
            return;
        }

        // Catmull-Rom Spline Interpolation
        List<Vec3> controlPoints = new ArrayList<>();
        controlPoints.add(Vec3.atCenterOf(points.get(0)));
        for (BlockPos p : points) controlPoints.add(Vec3.atCenterOf(p));
        controlPoints.add(Vec3.atCenterOf(points.get(points.size() - 1)));

        for (int i = 0; i < controlPoints.size() - 3; i++) {
            Vec3 p0 = controlPoints.get(i);
            Vec3 p1 = controlPoints.get(i + 1);
            Vec3 p2 = controlPoints.get(i + 2);
            Vec3 p3 = controlPoints.get(i + 3);

            for (float t = 0; t < 1.0f; t += 0.1f) {
                Vec3 pos = catmullRom(t, p0, p1, p2, p3);
                level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.5, pos.z, 1, 0, 0, 0, 0);
            }
        }
        
        for(BlockPos p : points) {
             level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0, 0, 0, 0);
        }
    }

    private Vec3 catmullRom(float t, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        float t2 = t * t;
        float t3 = t2 * t;

        double x = 0.5f * ((2 * p1.x) + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);
        double y = 0.5f * ((2 * p1.y) + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);
        double z = 0.5f * ((2 * p1.z) + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3);

        return new Vec3(x, y, z);
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("§7Right-click block: Add Path Point"));
        tooltipComponents.add(Component.literal("§7Shift+Right-click: Open Config GUI"));
        List<BlockPos> points = getPoints(stack);
        tooltipComponents.add(Component.literal("§ePoints: " + points.size()));
    }
}
