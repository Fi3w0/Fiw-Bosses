package com.fiw.fiw_bosses.summon;

import com.fiw.fiw_bosses.ModRefs;
import com.fiw.fiw_bosses.config.BodySize;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.network.NetworkHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;

/** Creates bosses for every spawn route (admin command, summon items, schedules). */
public final class BossSpawner {

    private BossSpawner() {}

    /**
     * Spawns one boss from an already variant-applied definition. Returns null when the
     * entity could not be created.
     */
    public static BossEntity spawn(ServerLevel level, BossDefinition def, double x, double y, double z,
                                   MobSpawnType reason) {
        BossEntity boss = ModRefs.BOSS.create(level);
        if (boss == null) return null;
        boss.moveTo(x, y, z, 0, 0);
        boss.applyDefinition(def);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(boss.blockPosition()), reason, null);
        level.addFreshEntity(boss);
        sendSkinToNearbyPlayers(level, boss);
        return boss;
    }

    /**
     * Feet height at, or up to 3 blocks above, {@code y} where the boss's whole hitbox
     * (disguise mob size x {@code scale}) is clear of blocks; NaN when there is no room.
     */
    public static double roomFor(ServerLevel level, BossDefinition def, double x, double y, double z) {
        EntityDimensions size = bodySize(def);
        for (int dy = 0; dy <= 3; dy++) {
            if (level.noCollision(size.makeBoundingBox(x, y + dy, z))) return y + dy;
        }
        return Double.NaN;
    }

    /** "W x H" of the boss's hitbox in blocks, for refusal messages. */
    public static String describeSize(BossDefinition def) {
        EntityDimensions size = bodySize(def);
        return String.format(java.util.Locale.ROOT, "%.1f x %.1f", size.width(), size.height());
    }

    private static EntityDimensions bodySize(BossDefinition def) {
        String id = def.renderEntity != null && !def.renderEntity.isBlank() ? def.renderEntity : def.baseEntity;
        EntityType<?> type = ModRefs.BOSS;
        if (id != null && !id.isBlank() && !"custom".equalsIgnoreCase(id)) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key != null && !FiwBossesCore.MOD_ID.equals(key.getNamespace())) {
                type = BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(ModRefs.BOSS);
            }
        }
        return type.getDimensions().scale(BodySize.scale(def.scale));
    }

    public static void sendSkinToNearbyPlayers(ServerLevel level, BossEntity boss) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(boss) <= 128.0D * 128.0D) {
                NetworkHandler.sendSkinToPlayer(player, boss);
            }
        }
    }
}
