package io.github.tristoris.duoyunblocks.blocks;

import io.github.tristoris.duoyunblocks.DuoyunBlocks;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public final class BlockDefiner {
    public static final Block DUOYUN_BLOCK =
            register("duoyun_block",
                    s -> new DuoyunBlock(
                            s.strength(0.6f, 6.0f)  // faster to mine
                                    .requiresTool(),       // wrong tool = slow/no drops, right tool = fast
                            0.0),
                    Block.Settings.create());

    private static Block register(String path, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
        final Identifier identifier = Identifier.of(DuoyunBlocks.MOD_ID, path);
        final RegistryKey<Block> registryKey = RegistryKey.of(RegistryKeys.BLOCK, identifier);

        final Block block = Blocks.register(registryKey, factory, settings);
        Items.register(block);
        return block;
    }

    public static void init() {

    }

    private BlockDefiner() {}
}
