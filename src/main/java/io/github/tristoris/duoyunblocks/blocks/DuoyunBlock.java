package io.github.tristoris.duoyunblocks.blocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tristoris.duoyunblocks.events.BlazeArenaEvent;
import io.github.tristoris.duoyunblocks.components.ModDataComponents;
import io.github.tristoris.duoyunblocks.entities.EntityDefiner;
import io.github.tristoris.duoyunblocks.entities.DuoyunBlockEntity;
import io.github.tristoris.duoyunblocks.items.DuoyunBlockItem;
import io.github.tristoris.duoyunblocks.util.BasicUtils;
import io.github.tristoris.duoyunblocks.util.TickTasks;
import io.github.tristoris.duoyunblocks.util.TimeUtils;
import net.minecraft.block.Block;
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
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DuoyunBlock extends BlockWithEntity {

    public static final BooleanProperty LIT = BooleanProperty.of("lit");
    private static final int LUMINANCE_ON  = 7; // full torch-like
    private static final int LUMINANCE_OFF = 0;
    private static final int GLOW_START = 13000;  // start glowing at 13000 (dusk)
    private static final int GLOW_END   = 23000;  // stop at 23000 (just before dawn)
    private static final int CHECK_PERIOD_TICKS = 200; // re-evaluate every 10s

    public static final MapCodec<DuoyunBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    createSettingsCodec(), // handles the Block.Settings
                    Codec.DOUBLE.fieldOf("luck").forGetter(block -> block.luck)
            ).apply(instance, DuoyunBlock::new)
    );
    private static final UUID LUCK_UUID = UUID.fromString("4c3f0d9f-9c23-4c0b-9f1f-111111111111");
    private final double luck;

    public DuoyunBlock(Settings settings, double luck) {
        // override luminance to depend on our LIT property (wins over any fixed luminance in BlockDefiner)
        super(settings.luminance(state -> state.contains(LIT) && state.get(LIT) ? LUMINANCE_ON : LUMINANCE_OFF));
        this.luck = luck;
        // default off
        this.setDefaultState(this.getStateManager().getDefaultState().with(LIT, false));
    }

    // === NEW: add LIT to blockstate ===
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    // === NEW: schedule periodic checks on place ===
    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient()) {
            // initial time-based LIT evaluation
            boolean shouldGlow = shouldGlow(world);
            if (state.get(LIT) != shouldGlow) {
                world.setBlockState(pos, state.with(LIT, shouldGlow), Block.NOTIFY_ALL);
            }
            // schedule next checks
            world.scheduleBlockTick(pos, this, CHECK_PERIOD_TICKS);
        }

        // === your existing luck transfer logic ===
        if (world.isClient()) return;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof DuoyunBlockEntity duoyunBe) {
            Integer luckFromItem = stack.get(ModDataComponents.LUCK);

            double baseLuck;
            if (luckFromItem != null) {
                baseLuck = luckFromItem;
            } else if (stack.getItem() instanceof DuoyunBlockItem lbi) {
                baseLuck = lbi.getDefaultLuck();
            } else {
                baseLuck = this.luck;
            }

            double finalLuck = Math.max(-100, Math.min(100, baseLuck));
            duoyunBe.setLuck(finalLuck);
        }
    }

    // === NEW: re-evaluate LIT on a timer ===
    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) {
        boolean shouldGlow = shouldGlow(world);
        if (state.get(LIT) != shouldGlow) {
            world.setBlockState(pos, state.with(LIT, shouldGlow), Block.NOTIFY_ALL);
        }
        world.scheduleBlockTick(pos, this, CHECK_PERIOD_TICKS);
    }

    // === NEW: time window logic (supports wrap-around if GLOW_END < GLOW_START) ===
    private boolean shouldGlow(World world) {
        return world.isNight();
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        super.onBreak(world, pos, state, player);
        if (!world.isClient()) {
            if (isInvalidBreakingOccasion(player)) {
                return state;
            }

            ItemStack tool = player.getMainHandStack();
            ItemEnchantmentsComponent enchantments = tool.getEnchantments();
            Set<RegistryEntry<Enchantment>> entries = enchantments.getEnchantments();

            for (RegistryEntry<Enchantment> entry : entries) {
                if (entry.getKey().equals(Optional.of(Enchantments.SILK_TOUCH))) {
                    dropDuoyunBlock(world, pos);
                    return state;
                }
                BasicUtils.broadcastMessage(world, entry.toString());
            }

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

            Vec3d center = Vec3d.ofCenter(pos);
            Random rand = new Random();

            // generates a number between -100 and 100 shifted by finalLuck
            double hit = Math.min(Math.max((Math.random() - 0.5) * 140 + finalLuck, -100), 100);
            //BasicUtils.broadcastMessage(world, "calculating chances, hit is : " + hit);

            // duoyun block events:
            TreeMap<Integer, Runnable> events = new TreeMap<>();
            events.put(-100, () -> rollCalamity(world));
            events.put(-60, () -> BlazeArenaEvent.spawnArena((ServerWorld) world, player));
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

            //BasicUtils.broadcastMessage(world, "broke block, block luck was : " + beLuck);
        }

        return state;
    }

    private void dropDuoyunBlock(World world, BlockPos pos) {
        if (world.isClient()) return;

        BlockEntity be = world.getBlockEntity(pos);
        double beLuck = this.luck; // fallback if BE missing

        if (be instanceof DuoyunBlockEntity duoyunBe) {
            beLuck = duoyunBe.getLuck();
        }

        ItemStack drop = new ItemStack(this.asItem());
        DuoyunBlockItem.setLuck(drop, (int) Math.round(beLuck));

        Block.dropStack(world, pos, drop);
    }

    private static boolean isInvalidBreakingOccasion(@Nullable PlayerEntity player) {
        // only allow survival/adventure players
        return player == null || player.isInCreativeMode() || player.isSpectator();
    }

    private void rollCalamity(World world) {
        BasicUtils.broadcastMessage(world, "calamity rolled");
        if (!(world instanceof ServerWorld server)) return;
    }

    private void spawnZombiePiglins(World world, BlockPos pos, Random rand) {
        final int count = 8 + rand.nextInt(6); // 8..13
        final double R = 4.0;                  // radius in blocks
        final double y = pos.getY() + 2.0;     // 2 blocks above so they fall

        final double cx = pos.getX() + 0.5;
        final double cz = pos.getZ() + 0.5;

        for (int i = 0; i < count; i++) {
            double u = rand.nextDouble();
            double r = R * Math.sqrt(u);
            double theta = rand.nextDouble() * Math.PI * 2.0;

            double x = cx + r * Math.cos(theta);
            double z = cz + r * Math.sin(theta);

            double jy = (rand.nextDouble() - 0.5) * 0.2;

            ZombifiedPiglinEntity piglin = EntityType.ZOMBIFIED_PIGLIN.create(world, SpawnReason.EVENT);
            if (piglin != null) {
                piglin.refreshPositionAndAngles(
                        x,
                        y + jy,
                        z,
                        rand.nextFloat() * 360F,
                        0.0F
                );
                world.spawnEntity(piglin);
            }
        }
    }

    private void applyBadLuck(PlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.UNLUCK,
                TimeUtils.minutesToTicks(10),
                1
        ));

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NAUSEA,
                TimeUtils.minutesToTicks(1),
                0
        ));
    }

    private void sprayNuggets(World world, Vec3d pos, Random rand) {
        int[] ironAmount = new int[]{17, 35};
        int[] goldAmount = new int[]{13, 27};
        int[] copperAmount = new int[]{19, 41};
        int ironNuggets   = (int) 2.3 * rand.nextInt(ironAmount[1] - ironAmount[0] + 1) + ironAmount[0];
        int goldNuggets   = (int) 2.6 * rand.nextInt(goldAmount[1] - goldAmount[0]  + 1) + goldAmount[0];
        int copperNuggets = (int) 2.8 * rand.nextInt(copperAmount[1] - copperAmount[0] + 1) + copperAmount[0];
        int ironIngots   = rand.nextInt(ironAmount[1] - ironAmount[0] + 1) + ironAmount[0];
        int goldIngots   = rand.nextInt(goldAmount[1] - goldAmount[0]  + 1) + goldAmount[0];
        int copperIngots = rand.nextInt(copperAmount[1] - copperAmount[0] + 1) + copperAmount[0];

        List<ItemStack> drops = new ArrayList<>(List.of(
                new ItemStack(Items.IRON_NUGGET, ironNuggets),
                new ItemStack(Items.GOLD_NUGGET, goldNuggets),
                new ItemStack(Items.COPPER_NUGGET, copperNuggets)
        ));

        List<ItemStack> ingots = new ArrayList<>(List.of(
                new ItemStack(Items.IRON_INGOT, ironIngots),
                new ItemStack(Items.GOLD_INGOT, goldIngots),
                new ItemStack(Items.COPPER_INGOT, copperIngots)
        ));

        Collections.shuffle(drops, rand);
        Collections.shuffle(ingots, rand);
        drops.addAll(ingots);
        sprayItems(world, pos, drops, rand);
    }

    /**
     * Sprays items in timed bursts: 8–10 single nuggets at once, every 0.4s.
     * Vertical velocity is doubled; horizontal speed halved to keep the same range.
     */
    private void sprayItems(World world, Vec3d origin, List<ItemStack> stacks, Random rand) {
        if (!(world instanceof ServerWorld server)) return;

        final double baseMinSpeed  = 0.225;
        final double baseMaxSpeed  = 0.375;
        final double upMin         = 0.14;
        final double upMax         = 0.28;
        final double desiredRadius = 5.0;
        final double spreadScale   = Math.max(1.0, desiredRadius / 3.0);
        final double minSpeed      = baseMinSpeed * spreadScale;
        final double maxSpeed      = baseMaxSpeed * spreadScale;

        final double jitter = 0.01;

        final int burstMin     = 11;
        final int burstMax     = 14;
        final int burstPeriod  = 8;
        final double vyBoost   = 2.0;
        final double vxFactor  = 0.5;

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

            List<ItemStack> burst = new ArrayList<>(singles.subList(cursor, cursor + sizeThisBurst));
            cursor += sizeThisBurst;
            burstIndex++;

            TickTasks.in(server, delay, () -> {
                for (ItemStack single : burst) {
                    double phi   = server.random.nextDouble() * Math.PI * 2.0;

                    double base = minSpeed + server.random.nextDouble() * (maxSpeed - minSpeed);
                    double hspd = base * vxFactor;

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
        int spawnY = world.getHeight() - 1;

        BlockState iron = Blocks.IRON_BLOCK.getDefaultState();

        FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(
                world,
                new BlockPos(targetPos.getX(), spawnY, targetPos.getZ()),
                iron
        );

        double speed = 5;
        falling.setVelocity(0.0, -speed, 0.0);
        falling.setHurtEntities(2.0F, 40);

        TickTasks.in((ServerWorld) world, 95, () -> {
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
