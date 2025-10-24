package io.github.tristoris.duoyunblocks.blocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tristoris.duoyunblocks.components.ModDataComponents;
import io.github.tristoris.duoyunblocks.entities.EntityDefiner;
import io.github.tristoris.duoyunblocks.entities.DuoyunBlockEntity;
import io.github.tristoris.duoyunblocks.items.DuoyunBlockItem;
import io.github.tristoris.duoyunblocks.util.BasicUtils;
import io.github.tristoris.duoyunblocks.util.TickTasks;
import io.github.tristoris.duoyunblocks.util.TimeUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DuoyunBlock extends BlockWithEntity {

    public static final MapCodec<DuoyunBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    createSettingsCodec(), // handles the Block.Settings
                    Codec.DOUBLE.fieldOf("luck").forGetter(block -> block.luck)
            ).apply(instance, DuoyunBlock::new)
    );    private static final UUID LUCK_UUID = UUID.fromString("4c3f0d9f-9c23-4c0b-9f1f-111111111111");
    private double luck;

    public DuoyunBlock(Settings settings, double luck) {
        super(settings);
        this.luck = luck;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world.isClient()) return;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof DuoyunBlockEntity duoyunBe) {
            // 1) read component from the item stack if present
            Integer luckFromItem = stack.get(ModDataComponents.LUCK);

            double baseLuck;
            if (luckFromItem != null) {
                baseLuck = luckFromItem;
            } else if (stack.getItem() instanceof DuoyunBlockItem lbi) {
                baseLuck = lbi.getDefaultLuck();
            } else {
                // final fallback: the block's constructor default (do NOT mutate this.luck!)
                baseLuck = this.luck;
            }

            // clamp to [-100, 100]
            double finalLuck = Math.max(-100, Math.min(100, baseLuck));

            // store per-block-instance luck in the BE
            duoyunBe.setLuck(finalLuck);

            // (no mutation of the block singleton!)
            BasicUtils.broadcastMessage(world, "placed block, luck is: " + finalLuck);
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        super.onBreak(world, pos, state, player);
        if (!world.isClient()) {
            ItemStack tool = player.getMainHandStack();
            ItemEnchantmentsComponent enchantments = tool.getEnchantments();
            Set<RegistryEntry<Enchantment>> entries = enchantments.getEnchantments();

            for (RegistryEntry<Enchantment> entry : entries) {
                if (entry.getKey().equals(Optional.of(Enchantments.SILK_TOUCH))) {
                    BasicUtils.broadcastMessage(world, "silktouch spotted?");
                    return state;
                }
                BasicUtils.broadcastMessage(world, entry.toString());
            }

            BasicUtils.broadcastMessage(world, "no silktouch");

            // --- use the placed block's own luck from its BlockEntity ---
            double beLuck = this.luck; // fallback to the block's default if BE missing
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof DuoyunBlockEntity duoyunBe) {
                beLuck = duoyunBe.getLuck();
            }

            double playerLuck = player.getLuck();
            double bonusLuck = luckConverter(playerLuck) * 100.0;

            // final luck used for event roll
            double finalLuck = (beLuck + bonusLuck) / 2.5;

            // log what we're actually using
            BasicUtils.broadcastMessage(world,
                    String.format("luck used -> block: %.1f, player bonus: %.1f, final: %.1f",
                            beLuck, bonusLuck, finalLuck));

            Vec3d center = Vec3d.ofCenter(pos);
            Random rand = new Random();

            // generates a number between -100 and 100 shifted by finalLuck
            double hit = Math.min(Math.max((Math.random() - 0.5) * 140 + finalLuck, -100), 100);
            BasicUtils.broadcastMessage(world, "calculating chances, hit is : " + hit);

            // duoyun block events:
            TreeMap<Integer, Runnable> events = new TreeMap<>();
            events.put(-100, () -> rollCalamity(world));
            events.put(-20, () -> spawnZombiePiglins(world, pos, rand));
            //events.put(0,   () -> applyBadLuck(player));
            events.put(10,  () -> sprayNuggets(world, center, rand));
            events.put(30,  () -> spawnFallingIronBlock(world, pos));
            events.put(50,  () -> dropDiamond(world, center, rand));

            Runnable action = Optional
                    .ofNullable(events.floorEntry((int) hit))
                    .map(Map.Entry::getValue)
                    .orElse(() -> rollOneHundredEvent(world));

            action.run();

            // print the per-block luck that actually influenced this break
            BasicUtils.broadcastMessage(world, "broke block, block luck was : " + beLuck);
        }

        return state;
    }


    private void rollCalamity(World world) {
        BasicUtils.broadcastMessage(world, "calamity rolled");
    }

    private void spawnZombiePiglins(World world, BlockPos pos, Random rand) {
        final int count = 8 + rand.nextInt(6); // 8..13
        final double R = 4.0;                  // radius in blocks
        final double y = pos.getY() + 2.0;     // 2 blocks above so they fall

        // spawn around the center of the broken block
        final double cx = pos.getX() + 0.5;
        final double cz = pos.getZ() + 0.5;

        for (int i = 0; i < count; i++) {
            // area-uniform point in a disk: r = R*sqrt(u), theta in [0, 2π)
            double u = rand.nextDouble();
            double r = R * Math.sqrt(u);
            double theta = rand.nextDouble() * Math.PI * 2.0;

            double x = cx + r * Math.cos(theta);
            double z = cz + r * Math.sin(theta);

            // small vertical jitter to reduce entity overlap
            double jy = (rand.nextDouble() - 0.5) * 0.2;

            ZombifiedPiglinEntity piglin = EntityType.ZOMBIFIED_PIGLIN.create(world, SpawnReason.EVENT);
            if (piglin != null) {
                piglin.refreshPositionAndAngles(
                        x,
                        y + jy,
                        z,
                        rand.nextFloat() * 360F, // yaw: random facing direction
                        0.0F                     // pitch: level
                );
                world.spawnEntity(piglin);
            }
        }
    }

    private void applyBadLuck(PlayerEntity player) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.UNLUCK,
                    TimeUtils.minutesToTicks(10),  // duration in ticks
                    1         // amplifier: 0 = level I, 1 = level II, etc.
            ));

            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NAUSEA,
                    TimeUtils.minutesToTicks(1),
                    0
            ));
    }

    private void sprayNuggets(World world, Vec3d pos, Random rand) {
        int ironNuggets   = rand.nextInt(51 - 29 + 1) + 29;
        int goldNuggets   = rand.nextInt(33 - 21  + 1) + 21;
        int copperNuggets = rand.nextInt(39 - 25 + 1) + 25;

        List<ItemStack> drops = new ArrayList<>(List.of(
                new ItemStack(Items.IRON_NUGGET, ironNuggets),
                new ItemStack(Items.GOLD_NUGGET, goldNuggets),
                new ItemStack(Items.COPPER_NUGGET, copperNuggets)
        ));

        Collections.shuffle(drops, rand);
        sprayItems(world, pos, drops, rand);
    }

    /**
     * Sprays items in timed bursts: 8–10 single nuggets at once, every 0.4s.
     * Vertical velocity is doubled; horizontal speed halved to keep the same range.
     */
    private void sprayItems(World world, Vec3d origin, List<ItemStack> stacks, Random rand) {
        if (!(world instanceof ServerWorld server)) return;

        // ===== knobs =====
        final double baseMinSpeed  = 0.225;
        final double baseMaxSpeed  = 0.375;
        final double upMin         = 0.14;
        final double upMax         = 0.28;
        final double desiredRadius = 5.0;
        final double spreadScale   = Math.max(1.0, desiredRadius / 3.0);
        final double minSpeed      = baseMinSpeed * spreadScale;
        final double maxSpeed      = baseMaxSpeed * spreadScale;

        final double jitter = 0.01;   // tiny spawn jitter

        // Burst settings
        final int burstMin     = 11;
        final int burstMax     = 14;
        final int burstPeriod  = 8;   // ticks -> 0.4s
        final double vyBoost   = 2.0; // 2x vertical lift
        final double vxFactor  = 0.5; // halve horizontal speed to keep same range

        // Flatten stacks into single-item stacks
        List<ItemStack> singles = new ArrayList<>();
        for (ItemStack stack : stacks) {
            int c = stack.getCount();
            for (int i = 0; i < c; i++) singles.add(stack.copyWithCount(1));
        }
        Collections.shuffle(singles, rand);

        int cursor = 0;
        int burstIndex = 0;

        while (cursor < singles.size()) {
            int remaining = singles.size() - cursor;
            int sizeThisBurst = Math.min(remaining, burstMin + rand.nextInt(burstMax - burstMin + 1));
            int delay = burstIndex * burstPeriod;

            // slice the items for this burst
            List<ItemStack> burst = new ArrayList<>(singles.subList(cursor, cursor + sizeThisBurst));
            cursor += sizeThisBurst;
            burstIndex++;

            TickTasks.in(server, delay, () -> {
                // Spawn all items in this burst at the same tick in different directions
                for (ItemStack single : burst) {
                    double phi   = server.random.nextDouble() * Math.PI * 2.0;

                    // horizontal speed scaled down to keep distance similar (airtime ~2x)
                    double base = minSpeed + server.random.nextDouble() * (maxSpeed - minSpeed);
                    double hspd = base * vxFactor;

                    // vertical speed doubled
                    double vy = (upMin + server.random.nextDouble() * (upMax - upMin)) * vyBoost;

                    double sx = origin.x + (server.random.nextDouble() * 2 - 1) * jitter;
                    double sy = origin.y + 1.1;
                    double sz = origin.z + (server.random.nextDouble() * 2 - 1) * jitter;

                    ItemEntity entity = new ItemEntity(server, sx, sy, sz, single.copy());

                    double vx = Math.cos(phi) * hspd;
                    double vz = Math.sin(phi) * hspd;

                    entity.setVelocity(vx, vy, vz);
                    entity.setToDefaultPickupDelay();
                    server.spawnEntity(entity);
                }
            });
        }
    }



    private void spawnFallingIronBlock(World world, BlockPos targetPos) {
        int groundY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                targetPos.getX(), targetPos.getZ());

        // spawn at the very top of the world
        int spawnY = world.getHeight() - 1;

        BlockState iron = Blocks.IRON_BLOCK.getDefaultState();

        // spawn the falling block via the factory
        FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(
                world,
                new BlockPos(targetPos.getX(), spawnY, targetPos.getZ()),
                iron
        );

        double speed = 5; // try 1.2–1.8; higher = faster
        falling.setVelocity(0.0, -speed, 0.0);
        // optional damage like an anvil
        falling.setHurtEntities(2.0F, 40);

        // simple: fire lightning & set fire after ~40 ticks (~2s)
        // (tweak this if you raise/lower spawnY)
        TickTasks.in((ServerWorld) world, 90, () -> {
            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    targetPos.getX(), targetPos.getZ());
            double x = targetPos.getX() + 0.5;
            double z = targetPos.getZ() + 0.5;

            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.EVENT);
            if (bolt != null) {
                bolt.refreshPositionAfterTeleport(x, topY, z);
                world.spawnEntity(bolt);
            }

            BlockPos firePos = new BlockPos(targetPos.getX(), topY + 1, targetPos.getZ());
            if (world.isAir(firePos)) {
                world.setBlockState(firePos, Blocks.FIRE.getDefaultState(), 11);
            }
        });
    }

    private void dropDiamond(World world, Vec3d pos, Random rand) {
        int amountDiamonds = rand.nextInt(3 - 1 + 1) + 1;
        ItemStack stack = new ItemStack(Items.DIAMOND, amountDiamonds);

        ItemEntity itemEntity = new ItemEntity(world, pos.x, pos.y, pos.z, stack);
        world.spawnEntity(itemEntity);
    }

    public static double luckConverter(double x) {
        double x3 = x * x * x;
        double x5 = x3 * x * x;
        return 0.45 * Math.tanh(0.565 * x - 0.101 * x3 + 0.0151 * x5);
    }

    private void rollOneHundredEvent(World world) {
        BasicUtils.broadcastMessage(world, "big prize rolled");
    }


    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DuoyunBlockEntity(EntityDefiner.DUOYUN_BLOCK_ENTITY, pos, state, this.luck);
    }
}