package com.fiw.fiw_bosses.command;

import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.MinionConfigLoader;
import com.fiw.fiw_bosses.config.MinionDefinition;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import com.fiw.fiw_bosses.entity.MinionEntity;
import com.fiw.fiw_bosses.skin.SkinCache;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

public class BossCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("boss")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("spawn")
                        .then(CommandManager.argument("boss_id", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    BossConfigLoader.getDefinitions().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> spawnBoss(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "boss_id"), null))
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> spawnBoss(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "boss_id"),
                                                BlockPosArgumentType.getBlockPos(ctx, "pos"))))))
                .then(CommandManager.literal("kill")
                        .then(CommandManager.literal("all")
                                .executes(ctx -> killAllBosses(ctx.getSource())))
                        .then(CommandManager.argument("boss_id", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    BossConfigLoader.getDefinitions().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> killBoss(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "boss_id")))))
                .then(CommandManager.literal("list")
                        .executes(ctx -> listBosses(ctx.getSource())))
                .then(CommandManager.literal("reload")
                        .requires(src -> src.hasPermissionLevel(3))
                        .executes(ctx -> reloadConfigs(ctx.getSource())))
                .then(CommandManager.literal("minion")
                        .then(CommandManager.literal("list")
                                .executes(ctx -> listMinions(ctx.getSource())))
                        .then(CommandManager.literal("spawn")
                                .then(CommandManager.argument("minion_id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            MinionConfigLoader.getDefinitions().keySet().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> spawnMinion(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "minion_id")))))
                        .then(CommandManager.literal("kill")
                                .then(CommandManager.literal("all")
                                        .executes(ctx -> killAllMinions(ctx.getSource())))
                                .then(CommandManager.argument("minion_id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            MinionConfigLoader.getDefinitions().keySet().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> killMinion(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "minion_id")))))));
    }

    // ── Boss commands ────────────────────────────────────────────────────────

    private static int spawnBoss(ServerCommandSource source, String bossId, BlockPos pos) {
        BossDefinition def = BossConfigLoader.getDefinition(bossId);
        if (def == null) {
            source.sendError(Text.literal("Unknown boss: " + bossId));
            return 0;
        }

        ServerWorld world = source.getWorld();
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

        BossEntity boss = BossEntityRegistry.BOSS_TYPE.create(world);
        if (boss == null) {
            source.sendError(Text.literal("Failed to create boss entity"));
            return 0;
        }

        boss.refreshPositionAndAngles(x, y, z, 0, 0);
        boss.applyDefinition(def);
        world.spawnEntity(boss);

        source.sendFeedback(() -> Text.literal("Spawned boss ")
                .append(Text.literal(bossId).formatted(Formatting.GOLD))
                .append(Text.literal(" at " + (int) x + ", " + (int) y + ", " + (int) z)), true);
        return 1;
    }

    private static int listBosses(ServerCommandSource source) {
        var definitions = BossConfigLoader.getDefinitions();
        if (definitions.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No boss definitions loaded.").formatted(Formatting.YELLOW), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Loaded bosses (" + definitions.size() + "):").formatted(Formatting.GREEN), false);
        for (var entry : definitions.entrySet()) {
            BossDefinition def = entry.getValue();
            source.sendFeedback(() -> Text.literal("  - ")
                    .append(Text.literal(entry.getKey()).formatted(Formatting.GOLD))
                    .append(Text.literal(" (HP: " + (int) def.health + ", Phases: " + def.phases.size() + ")")
                            .formatted(Formatting.GRAY)), false);
        }
        return definitions.size();
    }

    private static int killBoss(ServerCommandSource source, String bossId) {
        List<BossEntity> found = new ArrayList<>();
        Box worldBox = new Box(-30000000, -64, -30000000, 30000000, 320, 30000000);
        for (ServerWorld world : source.getServer().getWorlds()) {
            found.addAll(world.getEntitiesByClass(BossEntity.class, worldBox,
                    e -> !(e instanceof MinionEntity) && bossId.equals(e.getBossId())));
        }
        if (found.isEmpty()) {
            source.sendError(Text.literal("No living boss with id '" + bossId + "' found."));
            return 0;
        }
        found.forEach(net.minecraft.entity.Entity::kill);
        final int count = found.size();
        source.sendFeedback(() -> Text.literal("Killed ")
                .append(Text.literal(String.valueOf(count)).formatted(Formatting.RED))
                .append(Text.literal(" boss(es) with id "))
                .append(Text.literal(bossId).formatted(Formatting.GOLD))
                .append(Text.literal(".")), true);
        return count;
    }

    private static int killAllBosses(ServerCommandSource source) {
        List<BossEntity> found = new ArrayList<>();
        Box worldBox = new Box(-30000000, -64, -30000000, 30000000, 320, 30000000);
        for (ServerWorld world : source.getServer().getWorlds()) {
            found.addAll(world.getEntitiesByClass(BossEntity.class, worldBox,
                    e -> !(e instanceof MinionEntity)));
        }
        if (found.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No bosses alive.").formatted(Formatting.YELLOW), false);
            return 0;
        }
        found.forEach(net.minecraft.entity.Entity::kill);
        final int count = found.size();
        source.sendFeedback(() -> Text.literal("Killed ")
                .append(Text.literal(String.valueOf(count)).formatted(Formatting.RED))
                .append(Text.literal(" boss(es).")), true);
        return count;
    }

    // ── Minion commands ──────────────────────────────────────────────────────

    private static int listMinions(ServerCommandSource source) {
        var definitions = MinionConfigLoader.getDefinitions();
        if (definitions.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No minion definitions loaded.").formatted(Formatting.YELLOW), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Loaded minions (" + definitions.size() + "):").formatted(Formatting.GREEN), false);
        for (var entry : definitions.entrySet()) {
            MinionDefinition def = entry.getValue();
            source.sendFeedback(() -> Text.literal("  - ")
                    .append(Text.literal(entry.getKey()).formatted(Formatting.GOLD))
                    .append(Text.literal(" (HP: " + (int) def.health + ", Base: " + def.baseEntity + ")")
                            .formatted(Formatting.GRAY)), false);
        }
        return definitions.size();
    }

    private static int spawnMinion(ServerCommandSource source, String minionId) {
        MinionDefinition def = MinionConfigLoader.getDefinition(minionId);
        if (def == null) {
            source.sendError(Text.literal("Unknown minion: " + minionId));
            return 0;
        }

        if (!def.isCustom()) {
            source.sendError(Text.literal("Minion '" + minionId + "' uses a vanilla base entity — spawn it via a boss with summon_minions."));
            return 0;
        }

        ServerWorld world = source.getWorld();
        double x = source.getPosition().x;
        double y = source.getPosition().y;
        double z = source.getPosition().z;

        MinionEntity minion = BossEntityRegistry.MINION_TYPE.create(world);
        if (minion == null) {
            source.sendError(Text.literal("Failed to create minion entity"));
            return 0;
        }

        minion.refreshPositionAndAngles(x, y, z, 0, 0);
        minion.applyMinionDefinition(def, null);
        world.spawnEntity(minion);

        source.sendFeedback(() -> Text.literal("Spawned minion ")
                .append(Text.literal(minionId).formatted(Formatting.GOLD))
                .append(Text.literal(" at " + (int) x + ", " + (int) y + ", " + (int) z)), true);
        return 1;
    }

    private static int killMinion(ServerCommandSource source, String minionId) {
        List<MinionEntity> found = new ArrayList<>();
        Box worldBox = new Box(-30000000, -64, -30000000, 30000000, 320, 30000000);
        for (ServerWorld world : source.getServer().getWorlds()) {
            found.addAll(world.getEntitiesByClass(MinionEntity.class, worldBox,
                    e -> minionId.equals(e.getMinionId())));
        }
        if (found.isEmpty()) {
            source.sendError(Text.literal("No living minion with id '" + minionId + "' found."));
            return 0;
        }
        found.forEach(net.minecraft.entity.Entity::kill);
        final int count = found.size();
        source.sendFeedback(() -> Text.literal("Killed ")
                .append(Text.literal(String.valueOf(count)).formatted(Formatting.RED))
                .append(Text.literal(" minion(s) with id "))
                .append(Text.literal(minionId).formatted(Formatting.GOLD))
                .append(Text.literal(".")), true);
        return count;
    }

    private static int killAllMinions(ServerCommandSource source) {
        List<MinionEntity> found = new ArrayList<>();
        Box worldBox = new Box(-30000000, -64, -30000000, 30000000, 320, 30000000);
        for (ServerWorld world : source.getServer().getWorlds()) {
            found.addAll(world.getEntitiesByClass(MinionEntity.class, worldBox, e -> true));
        }
        if (found.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No minions alive.").formatted(Formatting.YELLOW), false);
            return 0;
        }
        found.forEach(net.minecraft.entity.Entity::kill);
        final int count = found.size();
        source.sendFeedback(() -> Text.literal("Killed ")
                .append(Text.literal(String.valueOf(count)).formatted(Formatting.RED))
                .append(Text.literal(" minion(s).")), true);
        return count;
    }

    // ── Reload (bosses + minions) ────────────────────────────────────────────

    private static int reloadConfigs(ServerCommandSource source) {
        int bosses = BossConfigLoader.reload();
        int minions = MinionConfigLoader.reload();
        SkinCache.fetchAll();
        source.sendFeedback(() -> Text.literal("Reloaded " + bosses + " boss + " + minions + " minion definitions.")
                .formatted(Formatting.GREEN), true);
        return bosses + minions;
    }
}
