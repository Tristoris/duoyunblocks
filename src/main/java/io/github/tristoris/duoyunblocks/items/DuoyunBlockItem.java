package io.github.tristoris.duoyunblocks.items;

import io.github.tristoris.duoyunblocks.components.ModDataComponents;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;


public class DuoyunBlockItem extends BlockItem {
    private final int defaultLuck;

    public DuoyunBlockItem(Block block, Settings settings, double luck) {
        super(block, settings);
        this.defaultLuck = (int) luck;
    }

    public int getDefaultLuck() {
        return defaultLuck;
    }

    @Override
    public ItemStack getDefaultStack() {
        ItemStack stack = super.getDefaultStack();
        setLuck(stack, defaultLuck); // ensure component is present on the default stack
        return stack;
    }

    public static int getLuck(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.LUCK, 0);
    }

    public static void setLuck(ItemStack stack, int value) {
        stack.set(ModDataComponents.LUCK, value);
    }

    public static void increaseLuck(ItemStack stack, int amount) {
        setLuck(stack, Math.min(getLuck(stack) + amount, 100));
    }

    public static void decreaseLuck(ItemStack stack, int amount) {
        setLuck(stack, Math.max(-100, getLuck(stack) - amount));
    }

    @Override
    public void appendTooltip(ItemStack stack,
                              Item.TooltipContext context,
                              net.minecraft.component.type.TooltipDisplayComponent displayComponent,
                              java.util.function.Consumer<Text> textConsumer,
                              net.minecraft.item.tooltip.TooltipType type) {
        Integer luck = stack.getOrDefault(ModDataComponents.LUCK, null);
        // If missing, show the true default for THIS item (don’t just show 0)
        int shown = (luck != null) ? luck : defaultLuck;
        textConsumer.accept(Text.literal("Luck: " + shown).formatted(Formatting.GOLD));
        super.appendTooltip(stack, context, displayComponent, textConsumer, type);
    }
}