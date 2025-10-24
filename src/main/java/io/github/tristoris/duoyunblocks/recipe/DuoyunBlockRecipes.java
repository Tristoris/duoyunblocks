package io.github.tristoris.duoyunblocks.recipe;

import io.github.tristoris.duoyunblocks.DuoyunBlocks;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class DuoyunBlockRecipes {
    public static final RecipeSerializer<DuoyunBlockUpgrade> DUOYUN_BLOCK_UPGRADE =
            Registry.register(Registries.RECIPE_SERIALIZER,
                    id("duoyun_block_upgrade"),
                    new SpecialCraftingRecipe.SpecialRecipeSerializer<>(DuoyunBlockUpgrade::new));

    public static final RecipeSerializer<DuoyunBlockDowngrade> DUOYUN_BLOCK_DOWNGRADE =
            Registry.register(Registries.RECIPE_SERIALIZER,
                    id("duoyun_block_downgrade"),
                    new SpecialCraftingRecipe.SpecialRecipeSerializer<>(DuoyunBlockDowngrade::new));

    public static void init() { /* called from your mod initializer */ }

    private static Identifier id(String path) {
        return Identifier.of(DuoyunBlocks.MOD_ID, path);
    }

    private DuoyunBlockRecipes() {}
}
