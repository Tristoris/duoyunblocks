package io.github.tristoris.duoyunblocks;

import io.github.tristoris.duoyunblocks.blocks.BlockDefiner;
import io.github.tristoris.duoyunblocks.components.ModDataComponents;
import io.github.tristoris.duoyunblocks.creative.CreativeTabDefiner;
import io.github.tristoris.duoyunblocks.entities.EntityDefiner;
import io.github.tristoris.duoyunblocks.items.ItemDefiner;
import io.github.tristoris.duoyunblocks.recipe.DuoyunBlockRecipes;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;
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

		RegistryKey<PlacedFeature> DUOYUN_SINGLE_BLOCK_PLACED =
				RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(MOD_ID, "generated_duoyun_block_single"));

		// Overworld only, during vegetation decoration (surface-like things are commonly added here)
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Feature.VEGETAL_DECORATION,
				DUOYUN_SINGLE_BLOCK_PLACED
		);

		LOGGER.info("Duoyun Blocks mod loaded!");
	}
}