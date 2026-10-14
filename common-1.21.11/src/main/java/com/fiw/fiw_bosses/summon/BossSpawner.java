package com.fiw.fiw_bosses.summon;

import com.fiw.fiw_bosses.ModRefs;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.network.NetworkHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

/** Creates bosses for every spawn route (admin command, summon items, schedules). */
public final class BossSpawner {

    private BossSpawner() {}

    /**
     * Spawns one boss from an already variant-applied definition. Returns null when the
     * entity could not be created.
     */
    public static BossEntity spawn(ServerLevel level, BossDefinition def, double x, double y, double z,
                                   EntitySpawnReason reason) {
        BossEntity boss = ModRefs.BOSS.create(level, reason);
        if (boss == null) return null;
        boss.snapTo(x, y, z, 0, 0);
        boss.applyDefinition(def);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(boss.blockPosition()), reason, null);
        level.addFreshEntity(boss);
        sendSkinToNearbyPlayers(level, boss);
        return boss;
    }

    public static void sendSkinToNearbyPlayers(ServerLevel level, BossEntity boss) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(boss) <= 128.0D * 128.0D) {
                NetworkHandler.sendSkinToPlayer(player, boss);
            }
        }
    }
}
