package io.github.tristoris.duoyunblocks;

import io.github.tristoris.duoyunblocks.blocks.BlockDefiner;
import io.github.tristoris.duoyunblocks.components.ModDataComponents;
import io.github.tristoris.duoyunblocks.creative.CreativeTabDefiner;
import io.github.tristoris.duoyunblocks.entities.EntityDefiner;
import io.github.tristoris.duoyunblocks.items.ItemDefiner;
import io.github.tristoris.duoyunblocks.recipe.DuoyunBlockRecipes;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuoyunBlocks implements ModInitializer {
	public static final String MOD_ID = "duoyunblocks";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModDataComponents.registerModDataComponents(); // components first (common side)

		BlockDefiner.init();     // triggers static registration
		ItemDefiner.init();      // triggers static registration (BlockItem exists now)
		EntityDefiner.init();    // block entity type
		DuoyunBlockRecipes.init(); // recipe serializers
		CreativeTabDefiner.init();

		LOGGER.info("Duoyun Blocks mod loaded!");
	}
}