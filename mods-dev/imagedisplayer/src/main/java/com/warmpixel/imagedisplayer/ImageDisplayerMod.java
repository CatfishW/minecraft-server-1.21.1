package com.warmpixel.imagedisplayer;

import com.warmpixel.imagedisplayer.block.BillboardBlock;
import com.warmpixel.imagedisplayer.block.entity.BillboardBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageDisplayerMod implements ModInitializer {
    public static final String MOD_ID = "imagedisplayer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Block BILLBOARD_BLOCK = new BillboardBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final BlockEntityType<BillboardBlockEntity> BILLBOARD_BLOCK_ENTITY = BlockEntityType.Builder.of(BillboardBlockEntity::new, BILLBOARD_BLOCK).build(null);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing ImageDisplayer");

        Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "billboard"), BILLBOARD_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "billboard"), new BlockItem(BILLBOARD_BLOCK, new Item.Properties()));
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "billboard"), BILLBOARD_BLOCK_ENTITY);
    }
}
