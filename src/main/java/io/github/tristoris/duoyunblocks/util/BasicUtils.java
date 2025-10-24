package io.github.tristoris.duoyunblocks.util;

import net.minecraft.text.Text;
import net.minecraft.world.World;

public class BasicUtils {
    public static void broadcastMessage(World world, String message) {
        if (world.isClient()) return; // only run on the server

        var server = world.getServer();
        if (server != null) {
            server.getPlayerManager().broadcast(
                    Text.literal("§6[§eDuoyun Block§6] §r" + message),
                    false // false = normal chat, true = action bar
            );
        }
    }
    private BasicUtils() {}
}
