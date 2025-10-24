package io.github.tristoris.duoyunblocks.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class TickTasks {
    private record Task(int ticksLeft, Runnable run) {}
    private static final Map<ServerWorld, List<Task>> TASKS = new WeakHashMap<>();
    private static boolean registered = false;

    public static void in(ServerWorld world, int ticks, Runnable action) {
        TASKS.computeIfAbsent(world, w -> new ArrayList<>()).add(new Task(ticks, action));
        if (!registered) {
            registered = true;
            ServerTickEvents.END_WORLD_TICK.register(TickTasks::onWorldTick);
        }
    }

    private static void onWorldTick(ServerWorld world) {
        var list = TASKS.get(world);
        if (list == null || list.isEmpty()) return;
        var it = list.listIterator();
        while (it.hasNext()) {
            Task t = it.next();
            int left = t.ticksLeft() - 1;
            if (left <= 0) {
                try { t.run().run(); } finally { it.remove(); }
            } else {
                it.set(new Task(left, t.run()));
            }
        }
    }
    private TickTasks() {}
}