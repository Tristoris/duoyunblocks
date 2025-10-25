package io.github.tristoris.duoyunblocks.items;

import io.github.tristoris.duoyunblocks.DuoyunBlocks;
import io.github.tristoris.duoyunblocks.blocks.BlockDefiner;
import io.github.tristoris.duoyunblocks.components.ModDataComponents; // <-- import this
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public final class ItemDefiner {
    public static final Item DUOYUN_BLOCK_ITEM_BAD_LUCK =
            register(
                    "duoyun_block_item_negative",
                    settings -> new DuoyunBlockItem(BlockDefiner.DUOYUN_BLOCK, settings, -100),
                    new Item.Settings().component(ModDataComponents.LUCK, -100) // default component
            );

    public static final Item DUOYUN_BLOCK_ITEM =
            register(
                    "duoyun_block_item",
                    settings -> new DuoyunBlockItem(BlockDefiner.DUOYUN_BLOCK, settings, 0),
                    new Item.Settings().component(ModDataComponents.LUCK, 0) // default component
            );

    public static final Item DUOYUN_BLOCK_ITEM_GOOD_LUCK =
            register(
                    "duoyun_block_item_positive",
                    settings -> new DuoyunBlockItem(BlockDefiner.DUOYUN_BLOCK, settings, 100),
                    new Item.Settings().component(ModDataComponents.LUCK, 100) // default component
            );

    private static Item register(String path, Function<Item.Settings, Item> factory, Item.Settings settings) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(DuoyunBlocks.MOD_ID, path));
        return Items.register(key, factory, settings);
    }

    public static void init() {}

    private ItemDefiner() {}
}
