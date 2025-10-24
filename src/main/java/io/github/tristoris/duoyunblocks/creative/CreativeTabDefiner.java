package io.github.tristoris.duoyunblocks.creative;

import io.github.tristoris.duoyunblocks.items.ItemDefiner;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;

public final class CreativeTabDefiner {
    public static void init() {
        // Put the block (its BlockItem) under Building Blocks
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
            entries.add(ItemDefiner.DUOYUN_BLOCK_ITEM); // block item is inferred
        });

        // Put the three item variants under Functional (pick any tab you prefer)
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(ItemDefiner.DUOYUN_BLOCK_ITEM_BAD_LUCK);
            entries.add(ItemDefiner.DUOYUN_BLOCK_ITEM);
            entries.add(ItemDefiner.DUOYUN_BLOCK_ITEM_GOOD_LUCK);
        });
    }

    private CreativeTabDefiner() {}
}