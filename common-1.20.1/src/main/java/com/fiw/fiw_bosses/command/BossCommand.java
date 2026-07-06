package com.fiw.fiw_bosses.command;

import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.MinionConfigLoader;
import com.fiw.fiw_bosses.config.MinionDefinition;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.ModRefs;
import com.fiw.fiw_bosses.entity.MinionEntity;
import com.fiw.fiw_bosses.integration.FiwToolsBridge;
import com.fiw.fiw_bosses.network.NetworkHandler;
import com.fiw.fiw_bosses.skin.SkinCache;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class BossCommand {

    private BossCommand() {}

    private static void sendSkinToNearbyPlayers(ServerLevel level, BossEntity boss) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(boss) <= 128.0D * 128.0D) {
                NetworkHandler.sendSkinToPlayer(player, boss);
            }
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("boss")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("boss_id", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    BossConfigLoader.getDefinitions().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> spawnBoss(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "boss_id"), null, 1))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> spawnBoss(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "boss_id"),
                                                BlockPosArgument.getBlockPos(ctx, "pos"), 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> spawnBoss(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "boss_id"),
                                                        BlockPosArgument.getBlockPos(ctx, "pos"),
                                                        IntegerArgumentType.getInteger(ctx, "count")))))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> spawnBoss(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "boss_id"), null,
                                                IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("kill")
                        .then(Commands.literal("all")
                                .executes(ctx -> killAllBosses(ctx.getSource())))
                        .then(Commands.argument("boss_id", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    BossConfigLoader.getDefinitions().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> killBoss(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "boss_id")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listBosses(ctx.getSource())))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(ctx -> reloadConfigs(ctx.getSource())))
                .then(Commands.literal("minion")
                        .then(Commands.literal("list")
                                .executes(ctx -> listMinions(ctx.getSource())))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("minion_id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            MinionConfigLoader.getDefinitions().keySet().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> spawnMinion(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "minion_id"), 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> spawnMinion(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "minion_id"),
                                                        IntegerArgumentType.getInteger(ctx, "count"))))))
                        .then(Commands.literal("kill")
                                .then(Commands.literal("all")
                                        .executes(ctx -> killAllMinions(ctx.getSource())))
                                .then(Commands.argument("minion_id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            MinionConfigLoader.getDefinitions().keySet().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> killMinion(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "minion_id")))))));
    }

    // ── Boss commands ────────────────────────────────────────────────────────

    private static int spawnBoss(CommandSourceStack source, String bossId, BlockPos pos, int count) {
        BossDefinition def = BossConfigLoader.getDefinition(bossId);
        if (def == null) {
            source.sendFailure(Component.literal("Unknown boss: " + bossId));
            return 0;
        }

        ServerLevel level = source.getLevel();
        double x, y, z;
        if (pos != null) {
            x = pos.getX() + 0.5;
            y = pos.getY();
            z = pos.getZ() + 0.5;
        } else {
            x = source.getPosition().x;
            y = source.getPosition().y;
            z = source.getPosition().z;
        }

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BossEntity boss = ModRefs.BOSS.create(level);
            if (boss == null) {
                source.sendFailure(Component.literal("Failed to create boss entity"));
                return spawned;
            }

            // Jitter extra spawns slightly so they don't stack on one point
            double ox = i == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 3.0;
            double oz = i == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 3.0;
            boss.moveTo(x + ox, y, z + oz, 0, 0);
            boss.applyDefinition(def);
            boss.finalizeSpawn(level, level.getCurrentDifficultyAt(boss.blockPosition()),
                    MobSpawnType.COMMAND, null, null);
            level.addFreshEntity(boss);
            sendSkinToNearbyPlayers(level, boss);
            spawned++;
        }

        final int n = spawned;
        final double fx = x, fy = y, fz = z;
        source.sendSuccess(() -> Component.literal(n > 1 ? "Spawned " + n + "x boss " : "Spawned boss ")
                .append(Component.literal(bossId).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" at " + (int) fx + ", " + (int) fy + ", " + (int) fz)), true);
        return n;
    }

    private static int listBosses(CommandSourceStack source) {
        var definitions = BossConfigLoader.getDefinitions();
        if (definitions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No boss definitions loaded.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Loaded bosses (" + definitions.size() + "):")
                .withStyle(ChatFormatting.GREEN), false);
        for (var entry : definitions.entrySet()) {
            BossDefinition def = entry.getValue();
            source.sendSuccess(() -> Component.literal("  - ")
                    .append(Component.literal(entry.getKey()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" (HP: " + (int) def.health + ", Phases: " + def.phases.size() + ")")
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        return definitions.size();
    }

    private static int killBoss(CommandSourceStack source, String bossId) {
        List<BossEntity> found = new ArrayList<>();
        AABB worldBox = new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000);
        for (ServerLevel level : source.getServer().getAllLevels()) {
            found.addAll(level.getEntitiesOfClass(BossEntity.class, worldBox,
                    e -> !(e instanceof MinionEntity) && bossId.equals(e.getBossId())));
        }
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No living boss with id '" + bossId + "' found."));
            return 0;
        }
        found.forEach(BossEntity::kill);
        final int count = found.size();
        source.sendSuccess(() -> Component.literal("Killed ")
                .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.RED))
                .append(Component.literal(" boss(es) with id "))
                .append(Component.literal(bossId).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(".")), true);
        return count;
    }

    private static int killAllBosses(CommandSourceStack source) {
        List<BossEntity> found = new ArrayList<>();
        AABB worldBox = new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000);
        for (ServerLevel level : source.getServer().getAllLevels()) {
            found.addAll(level.getEntitiesOfClass(BossEntity.class, worldBox,
                    e -> !(e instanceof MinionEntity)));
        }
        if (found.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No bosses alive.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        found.forEach(BossEntity::kill);
        final int count = found.size();
        source.sendSuccess(() -> Component.literal("Killed ")
                .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.RED))
                .append(Component.literal(" boss(es).")), true);
        return count;
    }

    // ── Minion commands ──────────────────────────────────────────────────────

    private static int listMinions(CommandSourceStack source) {
        var definitions = MinionConfigLoader.getDefinitions();
        if (definitions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No minion definitions loaded.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Loaded minions (" + definitions.size() + "):")
                .withStyle(ChatFormatting.GREEN), false);
        for (var entry : definitions.entrySet()) {
            MinionDefinition def = entry.getValue();
            source.sendSuccess(() -> Component.literal("  - ")
                    .append(Component.literal(entry.getKey()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" (HP: " + (int) def.health + ", Base: " + def.baseEntity + ")")
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        return definitions.size();
    }

    private static int spawnMinion(CommandSourceStack source, String minionId, int count) {
        MinionDefinition def = MinionConfigLoader.getDefinition(minionId);
        if (def == null) {
            source.sendFailure(Component.literal("Unknown minion: " + minionId));
            return 0;
        }

        if (!def.isCustom()) {
            source.sendFailure(Component.literal("Minion '" + minionId + "' uses a vanilla base entity — spawn it via a boss with summon_minions."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        double x = source.getPosition().x;
        double y = source.getPosition().y;
        double z = source.getPosition().z;

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            MinionEntity minion = ModRefs.MINION.create(level);
            if (minion == null) {
                source.sendFailure(Component.literal("Failed to create minion entity"));
                return spawned;
            }

            // Jitter extra spawns slightly so they don't stack on one point
            double ox = i == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 3.0;
            double oz = i == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 3.0;
            minion.moveTo(x + ox, y, z + oz, 0, 0);
            minion.applyMinionDefinition(def, null);
            minion.finalizeSpawn(level, level.getCurrentDifficultyAt(minion.blockPosition()),
                    MobSpawnType.COMMAND, null, null);
            level.addFreshEntity(minion);
            sendSkinToNearbyPlayers(level, minion);
            spawned++;
        }

        final int n = spawned;
        source.sendSuccess(() -> Component.literal(n > 1 ? "Spawned " + n + "x minion " : "Spawned minion ")
                .append(Component.literal(minionId).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" at " + (int) x + ", " + (int) y + ", " + (int) z)), true);
        return n;
    }

    private static int killMinion(CommandSourceStack source, String minionId) {
        List<MinionEntity> found = new ArrayList<>();
        AABB worldBox = new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000);
        for (ServerLevel level : source.getServer().getAllLevels()) {
            found.addAll(level.getEntitiesOfClass(MinionEntity.class, worldBox,
                    e -> minionId.equals(e.getMinionId())));
        }
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No living minion with id '" + minionId + "' found."));
            return 0;
        }
        found.forEach(BossEntity::kill);
        final int count = found.size();
        source.sendSuccess(() -> Component.literal("Killed ")
                .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.RED))
                .append(Component.literal(" minion(s) with id "))
                .append(Component.literal(minionId).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(".")), true);
        return count;
    }

    private static int killAllMinions(CommandSourceStack source) {
        List<MinionEntity> found = new ArrayList<>();
        AABB worldBox = new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000);
        for (ServerLevel level : source.getServer().getAllLevels()) {
            found.addAll(level.getEntitiesOfClass(MinionEntity.class, worldBox, e -> true));
        }
        if (found.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No minions alive.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        found.forEach(BossEntity::kill);
        final int count = found.size();
        source.sendSuccess(() -> Component.literal("Killed ")
                .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.RED))
                .append(Component.literal(" minion(s).")), true);
        return count;
    }

    // ── Reload (bosses + minions) ────────────────────────────────────────────

    private static int reloadConfigs(CommandSourceStack source) {
        int bosses = BossConfigLoader.reload();
        int minions = MinionConfigLoader.reload();
        SkinCache.fetchAll();
        FiwToolsBridge.reportUnknownToolIds();
        source.sendSuccess(() -> Component.literal("Reloaded " + bosses + " boss + " + minions + " minion definitions.")
                .withStyle(ChatFormatting.GREEN), true);
        return bosses + minions;
    }
}
