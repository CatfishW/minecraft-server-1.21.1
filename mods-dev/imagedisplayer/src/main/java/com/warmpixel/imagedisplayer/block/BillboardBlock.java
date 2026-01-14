package com.warmpixel.imagedisplayer.block;

import com.mojang.serialization.MapCodec;
import com.warmpixel.imagedisplayer.block.entity.BillboardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BillboardBlock extends BaseEntityBlock {
    public static final MapCodec<BillboardBlock> CODEC = simpleCodec(BillboardBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    protected static final VoxelShape NORTH_AABB = Block.box(0.0, 0.0, 15.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape SOUTH_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 1.0);
    protected static final VoxelShape WEST_AABB = Block.box(15.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape EAST_AABB = Block.box(0.0, 0.0, 0.0, 1.0, 16.0, 16.0);

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public BillboardBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_AABB;
            case WEST -> WEST_AABB;
            case EAST -> EAST_AABB;
            default -> NORTH_AABB;
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BillboardBlockEntity(pos, state);
    }
}
