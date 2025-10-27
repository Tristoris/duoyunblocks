package io.github.tristoris.duoyunblocks.entities;

import io.github.tristoris.duoyunblocks.DuoyunBlocks;
import io.github.tristoris.duoyunblocks.blocks.BlockDefiner;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
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

    private static <T extends Entity> EntityType<T> registerEntity(String path, EntityType.Builder<T> builder) {
        Identifier id = Identifier.of(DuoyunBlocks.MOD_ID, path);
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);

        // ✅ 1.21+: build() takes the RegistryKey<EntityType<?>>
        EntityType<T> type = builder.build(key);

        // register in global registry
        return Registry.register(Registries.ENTITY_TYPE, key, type);
    }

    public static void init() {

    }


    private EntityDefiner() {}
}
