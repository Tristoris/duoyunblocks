package io.github.tristoris.duoyunblocks.recipe;

import io.github.tristoris.duoyunblocks.items.DuoyunBlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

public class DuoyunBlockUpgrade extends SpecialCraftingRecipe {
    public DuoyunBlockUpgrade(CraftingRecipeCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        int gold = 0;
        boolean foundLucky = false;

        // Iterate over every input slot
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.isOf(Items.GOLD_INGOT)) {
                gold++;
            } else if (stack.getItem() instanceof DuoyunBlockItem) {
                if (foundLucky) return false;
                foundLucky = true;
            } else {
                return false; // invalid extra item
            }
        }

        return gold == 1 && foundLucky;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack base = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof DuoyunBlockItem) {
                // copy NBT/components but force count = 1
                base = stack.copyWithCount(1);
                break;
            }
        }

        if (base.isEmpty()) return ItemStack.EMPTY;

        DuoyunBlockItem.increaseLuck(base, 5);
        return base;
    }

    @Override
    public RecipeSerializer<? extends SpecialCraftingRecipe> getSerializer() {
        return DuoyunBlockRecipes.DUOYUN_BLOCK_UPGRADE;
    }
}
