package io.github.tristoris.duoyunblocks.events;

import io.github.tristoris.duoyunblocks.util.BasicUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns a 7x7 snow arena centered on a player, cages 4 Blazes (no drops/XP),
 * spawns 8 Snow Golems around the player, adds a barrier ceiling + curtains outside the walls,
 * and triggers a reward event when all Blazes are defeated. Supports multiple arenas.
 */
public final class BlazeArenaEvent {

    private static final ConcurrentHashMap<UUID, Set<UUID>> ARENA_TO_BLAZES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, UUID> BLAZE_TO_ARENA = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, BlockPos> ARENA_CENTER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Set<BlockPos>> ARENA_BARRIERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, UUID> ARENA_OWNER = new ConcurrentHashMap<>(); // NEW: arena -> owner player UUID
    private static final Set<UUID> NO_DROP_BLAZES = ConcurrentHashMap.newKeySet();

    private static volatile boolean LISTENER_REGISTERED = false;

    private BlazeArenaEvent() {}

    // ---------- Public API ----------

    public static void spawnArena(ServerWorld world, PlayerEntity player) {
        spawnArenaInternal(world, player.getBlockPos(), player.getUuid());
    }

    public static void spawnArena(ServerWorld world, BlockPos center) {
        spawnArenaInternal(world, center, null); // no owner known
    }

    // Core impl
    private static void spawnArenaInternal(ServerWorld world, BlockPos center, UUID ownerUuid) {
        registerListeners();

        final UUID arenaId = UUID.randomUUID();
        if (ownerUuid != null) ARENA_OWNER.put(arenaId, ownerUuid);
        ARENA_CENTER.put(arenaId, center);

        final int cx = center.getX();
        final int cy = center.getY();
        final int cz = center.getZ();

        // 1) Floor: 7x7 snow; clear interior
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos p = new BlockPos(cx + dx, cy - 1, cz + dz);
                world.setBlockState(p, Blocks.SNOW_BLOCK.getDefaultState(), 3);
                clear(world, p.up(), 3);
            }
        }

        // 2) Walls (radius 4, height 2)
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) != 4 && Math.abs(dz) != 4) continue;
                for (int dy = 0; dy < 2; dy++) {
                    BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + dz);
                    world.setBlockState(pos.down(), Blocks.NETHER_BRICKS.getDefaultState(), 3);
                    world.setBlockState(pos, Blocks.NETHER_BRICKS.getDefaultState(), 3);
                }
            }
        }

        // 2.5) Barrier protection: top ring + vertical curtains outside the wall
        final Set<BlockPos> barrierPositions = new HashSet<>();
        final int floorY   = cy - 1; // snow floor we placed
        final int ceilingY = cy + 4; // 5 blocks above the floor
        final int radius   = 5;      // outside the brick wall (wall is at radius 4)

        // Top ring (hollow square) at ceilingY, radius=5
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                BlockPos p = new BlockPos(cx + dx, ceilingY, cz + dz);
                world.setBlockState(p, Blocks.BARRIER.getDefaultState(), 3);
                barrierPositions.add(p.toImmutable());
            }
        }
        // Vertical curtains from floorY..ceilingY on the same perimeter (outside the wall)
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                for (int y = floorY; y <= ceilingY; y++) {
                    BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                    world.setBlockState(p, Blocks.BARRIER.getDefaultState(), 3);
                    barrierPositions.add(p.toImmutable());
                }
            }
        }
        ARENA_BARRIERS.put(arenaId, barrierPositions);

        // 3) Snow Golems (8 around center)
        BlockPos[] golemSpots = {
                new BlockPos(cx + 1, cy, cz), new BlockPos(cx - 1, cy, cz),
                new BlockPos(cx, cy, cz + 1), new BlockPos(cx, cy, cz - 1),
                new BlockPos(cx + 1, cy, cz + 1), new BlockPos(cx + 1, cy, cz - 1),
                new BlockPos(cx - 1, cy, cz + 1), new BlockPos(cx - 1, cy, cz - 1)
        };
        for (BlockPos gp : golemSpots) {
            world.setBlockState(gp.down(), Blocks.SNOW_BLOCK.getDefaultState(), 3);
            clear(world, gp, 2);
            spawn(world, gp, EntityType.SNOW_GOLEM);
        }

        // 4) Blazes (4 total, at cardinal directions)
        final Set<UUID> blazeSet = ConcurrentHashMap.newKeySet();
        ARENA_TO_BLAZES.put(arenaId, blazeSet);

        int blazeY = cy + 1;
        BlockPos[] blazeSpots = {
                new BlockPos(cx + 4, blazeY, cz),   // East
                new BlockPos(cx - 4, blazeY, cz),   // West
                new BlockPos(cx, blazeY, cz + 4),   // South
                new BlockPos(cx, blazeY, cz - 4)    // North
        };

        for (BlockPos spot : blazeSpots) {
            world.setBlockState(spot.down(), Blocks.NETHER_BRICKS.getDefaultState(), 3);
            clear(world, spot, 3);

            BlazeEntity blaze = EntityType.BLAZE.create(world, SpawnReason.EVENT);
            if (blaze != null) {
                blaze.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
                world.spawnEntity(blaze);

                UUID bid = blaze.getUuid();
                blazeSet.add(bid);
                BLAZE_TO_ARENA.put(bid, arenaId);
                NO_DROP_BLAZES.add(bid); // tracked for drop suppression
            }

            buildStall(world, spot, center);
        }
    }

    // ---------- Event Listener ----------

    private static void registerListeners() {
        if (LISTENER_REGISTERED) return;
        LISTENER_REGISTERED = true;

        ServerLivingEntityEvents.AFTER_DEATH.register((LivingEntity entity, DamageSource source) -> {
            if (!(entity instanceof BlazeEntity blaze)) return;

            UUID blazeId = blaze.getUuid();

            // Disable loot + XP for our tracked blazes only
            if (NO_DROP_BLAZES.remove(blazeId)) {
                ServerWorld sw = (ServerWorld) blaze.getEntityWorld();
                BlockPos pos = blaze.getBlockPos();
                sw.getServer().execute(() -> {
                    for (var it : sw.getEntitiesByClass(
                            net.minecraft.entity.ItemEntity.class, new Box(pos).expand(2.0), e -> true)) {
                        it.discard();
                    }
                });
            }

            // Check arena completion
            UUID arenaId = BLAZE_TO_ARENA.remove(blazeId);
            if (arenaId == null) return;

            Set<UUID> set = ARENA_TO_BLAZES.get(arenaId);
            if (set == null) return;

            set.remove(blazeId);
            if (set.isEmpty()) {
                ARENA_TO_BLAZES.remove(arenaId);

                ServerWorld world = (ServerWorld) blaze.getEntityWorld();
                BlockPos center = ARENA_CENTER.remove(arenaId);

                // Clean up barriers before granting rewards
                removeArenaBarriers(world, arenaId);

                // Resolve owner (may be null/offline)
                PlayerEntity owner = null;
                UUID ownerUuid = ARENA_OWNER.remove(arenaId);
                if (ownerUuid != null) {
                    owner = world.getServer().getPlayerManager().getPlayer(ownerUuid);
                }
                if (owner == null && center != null) {
                    owner = world.getClosestPlayer(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 64.0, false);
                }
                if (owner == null && !world.getPlayers().isEmpty()) {
                    owner = world.getPlayers().get(0); // last fallback
                }

                // Call the required signature with a non-null player when possible
                if (owner != null) {
                    arenaFinished(world, center, owner);
                } else {
                    // If truly no player is available, still log and skip reward to avoid NPE
                    world.getServer().sendMessage(Text.literal("[Arena] All blazes defeated (no player to reward)."));
                }
            }
        });
    }

    // ---------- Helpers ----------

    private static void clear(ServerWorld world, BlockPos pos, int height) {
        for (int i = 0; i < height; i++) {
            world.setBlockState(pos.up(i), Blocks.AIR.getDefaultState(), 3);
        }
    }

    private static void spawn(ServerWorld world, BlockPos pos, EntityType<? extends LivingEntity> type) {
        clear(world, pos, 2);
        LivingEntity e = type.create(world, SpawnReason.EVENT);
        if (e != null) {
            e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
            world.spawnEntity(e);
        }
    }

    private static void buildStall(ServerWorld world, BlockPos at, BlockPos center) {
        int dxToCenter = Integer.compare(center.getX(), at.getX());
        int dzToCenter = Integer.compare(center.getZ(), at.getZ());

        boolean openEast  = dxToCenter > 0;
        boolean openWest  = dxToCenter < 0;
        boolean openSouth = dzToCenter > 0;
        boolean openNorth = dzToCenter < 0;

        BlockPos north = at.north();
        BlockPos south = at.south();
        BlockPos east  = at.east();
        BlockPos west  = at.west();

        clear(world, at, 3);
        clear(world, north, 3);
        clear(world, south, 3);
        clear(world, east,  3);
        clear(world, west,  3);

        world.setBlockState(at.up(2), Blocks.AIR.getDefaultState(), 3);

        java.util.function.BiConsumer<BlockPos, Boolean> fenceTwoHigh = (pos, isOpen) -> {
            world.setBlockState(pos.down(), Blocks.NETHER_BRICKS.getDefaultState(), 3);
            if (!isOpen) {
                world.setBlockState(pos, Blocks.NETHER_BRICK_WALL.getDefaultState(), 3);
                world.setBlockState(pos.up(1), Blocks.NETHER_BRICK_WALL.getDefaultState(), 3);
            } else {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                world.setBlockState(pos.up(1), Blocks.AIR.getDefaultState(), 3);
            }
        };

        java.util.function.BiConsumer<BlockPos, Boolean> ringTwoHigh = (outerPos, isOpenSide) -> {
            if (isOpenSide) return;
            world.setBlockState(outerPos, Blocks.NETHER_BRICKS.getDefaultState(), 3);
            world.setBlockState(outerPos.up(1), Blocks.NETHER_BRICKS.getDefaultState(), 3);
        };

        BlockPos fenceN = north; BlockPos ringN = fenceN.north();
        BlockPos fenceS = south; BlockPos ringS = fenceS.south();
        BlockPos fenceE = east;  BlockPos ringE = fenceE.east();
        BlockPos fenceW = west;  BlockPos ringW = fenceW.west();

        fenceTwoHigh.accept(fenceN, openNorth); ringTwoHigh.accept(ringN, openNorth);
        fenceTwoHigh.accept(fenceS, openSouth); ringTwoHigh.accept(ringS, openSouth);
        fenceTwoHigh.accept(fenceE, openEast);  ringTwoHigh.accept(ringE, openEast);
        fenceTwoHigh.accept(fenceW, openWest);  ringTwoHigh.accept(ringW, openWest);

        world.setBlockState(at.up(2), Blocks.NETHER_BRICK_SLAB.getDefaultState(), 3);
    }

    private static void removeArenaBarriers(ServerWorld world, UUID arenaId) {
        Set<BlockPos> positions = ARENA_BARRIERS.remove(arenaId);
        if (positions == null || positions.isEmpty()) return;
        for (BlockPos pos : positions) {
            if (world.getBlockState(pos).isOf(Blocks.BARRIER)) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
            }
        }
    }

    // ---------- Completion (requires PlayerEntity) ----------

    private static void arenaFinished(ServerWorld world, BlockPos center, PlayerEntity player) {
        world.getServer().sendMessage(Text.literal("[Arena] All blazes defeated!"));
        if (center != null) {
            if (BasicUtils.hasEverEnteredNether((ServerPlayerEntity) player)) {
                BasicUtils.broadcastMessage(player.getEntityWorld(), "has been to nether");
            } else {
                BasicUtils.broadcastMessage(player.getEntityWorld(), "has NOT YET been to nether");
            }
            RewardSpawnEvent.spawnReward(world, center, "minecraft:diamond", 25, 3);
        }
    }
}
