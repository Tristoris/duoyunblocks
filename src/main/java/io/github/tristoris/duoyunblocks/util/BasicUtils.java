package io.github.tristoris.duoyunblocks.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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

    public static boolean hasEverEnteredNether(ServerPlayerEntity player) {
        var loader = player.getEntityWorld().getServer().getAdvancementLoader();
        var adv = loader.get(Identifier.of("minecraft", "nether/enter_the_nether"));
        if (adv == null) return false;
        return player.getAdvancementTracker().getProgress(adv).isDone();
    }

    private BasicUtils() {}
}
