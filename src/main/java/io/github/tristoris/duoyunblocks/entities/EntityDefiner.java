package io.github.tristoris.duoyunblocks.entities;

import io.github.tristoris.duoyunblocks.DuoyunBlocks;
import io.github.tristoris.duoyunblocks.blocks.BlockDefiner;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class EntityDefiner {
    public static final BlockEntityType<DuoyunBlockEntity> DUOYUN_BLOCK_ENTITY =
            register("duoyun_block_entity", DuoyunBlockEntity::new, BlockDefiner.DUOYUN_BLOCK);

    /**
     * Registers a BlockEntityType with a factory and one or more valid blocks.
     */
    private static <T extends BlockEntity> BlockEntityType<T> register(
            String path,
            FabricBlockEntityTypeBuilder.Factory<T> factory,
            Block... validBlocks
    ) {
        RegistryKey<BlockEntityType<?>> key =
                RegistryKey.of(RegistryKeys.BLOCK_ENTITY_TYPE, Identifier.of(DuoyunBlocks.MOD_ID, path));

        return Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                key,
                FabricBlockEntityTypeBuilder.create(factory, validBlocks).build()
        );
    }


    public static void init() {

    }


    private EntityDefiner() {}
}
