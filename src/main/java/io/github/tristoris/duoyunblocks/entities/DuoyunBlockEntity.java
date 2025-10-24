package io.github.tristoris.duoyunblocks.entities;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

public class DuoyunBlockEntity extends BlockEntity {
    private double luck;

    public DuoyunBlockEntity(BlockPos pos, BlockState state) {
        super(EntityDefiner.DUOYUN_BLOCK_ENTITY, pos, state);
    }

    public DuoyunBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, double luck) {
        super(type, pos, state);
        this.luck = luck;
    }

    public void setLuck(double luck) {
        this.luck = luck;
        markDirty();
    }

    public double getLuck() { return luck; }
}
