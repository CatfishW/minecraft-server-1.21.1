package com.warmpixel.imagedisplayer.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.warmpixel.imagedisplayer.ImageDisplayerMod;

public class BillboardBlockEntity extends BlockEntity {
    public BillboardBlockEntity(BlockPos pos, BlockState state) {
        super(ImageDisplayerMod.BILLBOARD_BLOCK_ENTITY, pos, state);
    }
}
