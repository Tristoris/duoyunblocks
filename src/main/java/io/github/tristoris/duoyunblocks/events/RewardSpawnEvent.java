package io.github.tristoris.duoyunblocks.events;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * RewardSpawnEvent
 *
 * Spawns one or many reward items randomly within a square area centered on a block,
 * with each item dropped exactly 7 blocks above that block's Y.
 * Spawns a firework rocket directly below each item at ground level (same X/Z).
 *
 * Two entry points:
 *  - spawnRewards(world, center, items, radius)
 *  - spawnReward(world, center, itemId, amount, radius)
 */
public final class RewardSpawnEvent {

    private RewardSpawnEvent() {}

    private static final Random RNG = new Random();

    /**
     * Spawn a single reward by its item ID (e.g. "minecraft:diamond") and amount.
     * @param world  Server world
     * @param center Center block position (items fall from y+7; fireworks launch from ground under same x/z)
     * @param itemId Namespaced ID, e.g. "minecraft:diamond"
     * @param amount Stack size (1..max stack size)
     * @param radius Half-size of the square scatter range in blocks (random dx,dz in [-radius, +radius])
     */
    public static void spawnReward(ServerWorld world, BlockPos center, String itemId, int amount, int radius) {
        Item item = Registries.ITEM.get(Identifier.of(itemId));
        if (item == Items.AIR) return; // invalid id

        // 🔁 Scatter: split into singles (amount entities), each will get its own random dx/dz
        int count = Math.max(1, amount);
        java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            stacks.add(new ItemStack(item, 1));
        }

        spawnRewards(world, center, stacks, radius);
    }


    /**
     * Spawn multiple reward stacks.
     * @param world  Server world
     * @param center Center block position (items fall from y+7; fireworks launch from ground under same x/z)
     * @param items  List of ItemStacks to drop (each will be dropped separately)
     * @param radius Half-size of the square scatter range in blocks (random dx,dz in [-radius, +radius])
     */
    public static void spawnRewards(ServerWorld world, BlockPos center, List<ItemStack> items, int radius) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(items, "items");

        if (items.isEmpty()) return;
        int yItem = center.getY() + 7;

        for (ItemStack original : items) {
            if (original == null || original.isEmpty()) continue;
            ItemStack stack = original.copy();

            // pick a random square offset in [-radius, +radius]
            int dx = radius <= 0 ? 0 : (RNG.nextInt(radius * 2 + 1) - radius);
            int dz = radius <= 0 ? 0 : (RNG.nextInt(radius * 2 + 1) - radius);

            double x = center.getX() + 0.5 + dx;
            double z = center.getZ() + 0.5 + dz;

            // --- spawn the item 7 blocks above center Y ---
            ItemEntity itemEnt = new ItemEntity(world, x, yItem, z, stack);
            itemEnt.setVelocity(0.0, -0.35 - world.random.nextDouble() * 0.15, 0.0);
            itemEnt.setToDefaultPickupDelay();
            world.spawnEntity(itemEnt);

            // --- spawn a firework directly below at ground level under same x/z ---
            int groundY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            double fy = groundY + 0.05; // just over the ground
            boolean exploding = world.random.nextBoolean(); // mix: half exploding, half plain

            ItemStack rocket = makeFireworkStack(world, exploding);
            FireworkRocketEntity fw = new FireworkRocketEntity(world, x, fy, z, rocket);
            world.spawnEntity(fw);
        }
    }

    private static ItemStack makeFireworkStack(ServerWorld world, boolean exploding) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);

        int flight = 1 + world.random.nextInt(3); // 1..3
        List<FireworkExplosionComponent> explosions = List.of();

        if (exploding) {
            // 0..4: SMALL_BALL, LARGE_BALL, STAR, CREEPER, BURST
            FireworkExplosionComponent.Type[] types = FireworkExplosionComponent.Type.values();
            FireworkExplosionComponent.Type type = types[world.random.nextInt(types.length)];

            int[] palette = new int[]{
                    0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF,
                    0xFFFF00, 0xFF00FF, 0x00FFFF, 0xFFA500,
                    0x800080, 0x00AEEF
            };
            int c1 = palette[world.random.nextInt(palette.length)];
            int c2 = palette[world.random.nextInt(palette.length)];
            IntList colors = world.random.nextBoolean()
                    ? new IntArrayList(new int[]{c1})
                    : new IntArrayList(new int[]{c1, c2});

            IntList fades = IntList.of();
            if (world.random.nextBoolean()) {
                int fade = palette[world.random.nextInt(palette.length)];
                fades = new IntArrayList(new int[]{fade});
            }

            boolean trail = world.random.nextBoolean();
            boolean twinkle = world.random.nextBoolean(); // (aka "flicker")

            FireworkExplosionComponent explosion =
                    new FireworkExplosionComponent(type, colors, fades, trail, twinkle);

            explosions = List.of(explosion);
        }

        rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(flight, explosions));
        return rocket;
    }
}
