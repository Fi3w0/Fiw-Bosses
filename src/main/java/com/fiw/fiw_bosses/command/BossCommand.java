package com.fiw.fiw_bosses.command;

import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.entity.BossEntityRegistry;
import com.fiw.fiw_bosses.skin.SkinCache;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class BossCommand {

    private BossCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("boss")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("boss_id", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    BossConfigLoader.getDefinitions().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> spawnBoss(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "boss_id"), null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> spawnBoss(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "boss_id"),
                                                BlockPosArgument.getBlockPos(ctx, "pos"))))))
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
                        .requires(src -> src.hasPermission(3))
                        .executes(ctx -> reloadConfigs(ctx.getSource()))));
    }

    private static int spawnBoss(CommandSourceStack source, String bossId, BlockPos pos) {
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

        BossEntity boss = BossEntityRegistry.BOSS.get().create(level);
        if (boss == null) {
            source.sendFailure(Component.literal("Failed to create boss entity"));
            return 0;
        }

        boss.moveTo(x, y, z, 0, 0);
        boss.applyDefinition(def);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(boss.blockPosition()),
                MobSpawnType.COMMAND, null);
        level.addFreshEntity(boss);

        final double fx = x, fy = y, fz = z;
        source.sendSuccess(() -> Component.literal("Spawned boss ")
                .append(Component.literal(bossId).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" at " + (int) fx + ", " + (int) fy + ", " + (int) fz)), true);
        return 1;
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
                    e -> bossId.equals(e.getBossId())));
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
            found.addAll(level.getEntitiesOfClass(BossEntity.class, worldBox, e -> true));
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

    private static int reloadConfigs(CommandSourceStack source) {
        int loaded = BossConfigLoader.reload();
        SkinCache.fetchAll();
        source.sendSuccess(() -> Component.literal("Reloaded " + loaded + " boss definitions.")
                .withStyle(ChatFormatting.GREEN), true);
        return loaded;
    }
}
