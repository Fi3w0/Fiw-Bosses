package com.fiw.fiw_bosses.command;

import com.fiw.fiw_bosses.api.FiwBossesAPI;
import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.FightSummaryConfig;
import com.fiw.fiw_bosses.config.SettingsLoader;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.BossVariants;
import com.fiw.fiw_bosses.data.BossData;
import com.fiw.fiw_bosses.data.KillRecords;
import com.fiw.fiw_bosses.data.SpawnData;
import com.fiw.fiw_bosses.data.SpawnRules;
import com.fiw.fiw_bosses.summon.BossSpawner;
import com.fiw.fiw_bosses.summon.ScheduleRunner;
import com.fiw.fiw_bosses.config.MinionConfigLoader;
import com.fiw.fiw_bosses.config.MinionDefinition;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.ModRefs;
import com.fiw.fiw_bosses.entity.MinionEntity;
import com.fiw.fiw_bosses.integration.FiwToolsBridge;
import com.fiw.fiw_bosses.network.NetworkHandler;
import com.fiw.fiw_bosses.skin.SkinCache;
import com.fiw.fiw_bosses.util.ConfiguredItemStacks;
import com.fiw.fiw_bosses.util.TextUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class BossCommand {

    private BossCommand() {}

    private static void sendSkinToNearbyPlayers(ServerLevel level, BossEntity boss) {
        BossSpawner.sendSkinToNearbyPlayers(level, boss);
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
                                        StringArgumentType.getString(ctx, "boss_id"), null, 1, null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> spawnBoss(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "boss_id"),
                                                BlockPosArgument.getBlockPos(ctx, "pos"), 1, null))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(ctx -> spawnBoss(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "boss_id"),
                                                        BlockPosArgument.getBlockPos(ctx, "pos"),
                                                        IntegerArgumentType.getInteger(ctx, "count"), null))))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(ctx -> spawnBoss(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "boss_id"), null,
                                                IntegerArgumentType.getInteger(ctx, "count"), null)))
                                .then(Commands.literal("variant")
                                        .then(Commands.argument("variant", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    BossDefinition d = BossConfigLoader.getDefinition(
                                                            StringArgumentType.getString(ctx, "boss_id"));
                                                    if (d != null && d.variants != null) d.variants.keySet().forEach(builder::suggest);
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> spawnBoss(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "boss_id"), null, 1,
                                                        StringArgumentType.getString(ctx, "variant")))
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .executes(ctx -> spawnBoss(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "boss_id"),
                                                                BlockPosArgument.getBlockPos(ctx, "pos"), 1,
                                                                StringArgumentType.getString(ctx, "variant"))))))))
                .then(Commands.literal("limits")
                        .then(Commands.argument("boss_id", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    BossConfigLoader.getDefinitions().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> limitsStatus(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "boss_id")))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> limitsReset(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "boss_id"))))))
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
                .then(Commands.literal("phase")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.literal("next")
                                        .executes(ctx -> changePhase(ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "targets"), 1, 0)))
                                .then(Commands.literal("prev")
                                        .executes(ctx -> changePhase(ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "targets"), -1, 0)))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("phase", IntegerArgumentType.integer(1))
                                                .executes(ctx -> changePhase(ctx.getSource(),
                                                        EntityArgument.getEntities(ctx, "targets"), 0,
                                                        IntegerArgumentType.getInteger(ctx, "phase")))))))
                .then(Commands.literal("schedule")
                        .then(Commands.literal("list")
                                .executes(ctx -> scheduleList(ctx.getSource())))
                        .then(Commands.literal("trigger")
                                .then(Commands.argument("schedule_id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            ScheduleRunner.schedules().forEach(sc -> builder.suggest(sc.id));
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> scheduleTrigger(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "schedule_id"))))))
                .then(Commands.literal("stats")
                        .executes(ctx -> statsAll(ctx.getSource()))
                        .then(Commands.argument("boss_id", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    BossConfigLoader.getDefinitions().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> statsBoss(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "boss_id")))))
                .then(Commands.literal("rage")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.literal("start")
                                        .then(Commands.argument("rage", StringArgumentType.word())
                                                .executes(ctx -> startRage(ctx.getSource(),
                                                        EntityArgument.getEntities(ctx, "targets"),
                                                        StringArgumentType.getString(ctx, "rage")))))
                                .then(Commands.literal("end")
                                        .executes(ctx -> endRage(ctx.getSource(),
                                                EntityArgument.getEntities(ctx, "targets"))))))
                .then(Commands.literal("summary")
                        .executes(ctx -> summaryStatus(ctx.getSource()))
                        .then(Commands.literal("on")
                                .executes(ctx -> summaryEnabled(ctx.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> summaryEnabled(ctx.getSource(), false)))
                        .then(Commands.literal("top")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 10))
                                        .executes(ctx -> summaryTop(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count")))))
                        .then(Commands.literal("show")
                                .then(Commands.argument("section", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (String section : SUMMARY_SECTIONS) builder.suggest(section);
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("visible", BoolArgumentType.bool())
                                                .executes(ctx -> summaryShow(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "section"),
                                                        BoolArgumentType.getBool(ctx, "visible")))))))
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

    private static int spawnBoss(CommandSourceStack source, String bossId, BlockPos pos, int count, String variant) {
        BossDefinition base = BossConfigLoader.getDefinition(bossId);
        if (base == null) {
            source.sendFailure(Component.literal("Unknown boss: " + bossId));
            return 0;
        }
        if (variant != null && BossVariants.find(base, variant) == null) {
            source.sendFailure(Component.literal("Boss " + bossId + " has no variant: " + variant));
            return 0;
        }
        BossDefinition def = BossVariants.apply(base, variant);

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
            // Jitter extra spawns slightly so they don't stack on one point
            double ox = i == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 3.0;
            double oz = i == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 3.0;
            double roomY = BossSpawner.roomFor(level, def, x + ox, y, z + oz);
            if (Double.isNaN(roomY)) {
                source.sendFailure(Component.literal("Not enough room for boss '" + def.id + "' here; it needs about "
                        + BossSpawner.describeSize(def) + " blocks of open space"));
                return spawned;
            }
            BossEntity boss = BossSpawner.spawn(level, def, x + ox, roomY, z + oz, MobSpawnType.COMMAND);
            if (boss == null) {
                source.sendFailure(Component.literal("Failed to create boss entity"));
                return spawned;
            }
            spawned++;
        }

        final int n = spawned;
        final double fx = x, fy = y, fz = z;
        source.sendSuccess(() -> Component.literal(n > 1 ? "Spawned " + n + "x boss " : "Spawned boss ")
                .append(Component.literal(bossId).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(variant != null ? " (" + variant + ")" : ""))
                .append(Component.literal(" at " + (int) fx + ", " + (int) fy + ", " + (int) fz)), true);
        return n;
    }

    private static int scheduleList(CommandSourceStack source) {
        var list = ScheduleRunner.schedules();
        if (list.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No spawn schedules.").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Spawn schedules:").withStyle(ChatFormatting.GREEN), false);
        for (var sc : list) {
            boolean alive = ScheduleRunner.isAlive(sc, BossData.SCHEDULES.get());
            String when = alive ? "boss alive" : "next in " + SpawnRules.formatDuration(Math.max(0, ScheduleRunner.millisUntil(sc)));
            String timing = sc.hasInterval() ? "every " + sc.intervalMinutes + "m" : "at " + String.join(", ", sc.times);
            source.sendSuccess(() -> Component.literal("  " + sc.id).withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" → " + sc.boss + (sc.variant != null ? " (" + sc.variant + ")" : "")
                            + ", " + timing + ", " + when).withStyle(ChatFormatting.GRAY)), false);
        }
        return list.size();
    }

    private static int scheduleTrigger(CommandSourceStack source, String id) {
        var sc = ScheduleRunner.schedules().stream().filter(x -> x.id.equals(id)).findFirst().orElse(null);
        if (sc == null) {
            source.sendFailure(Component.literal("Unknown schedule: " + id));
            return 0;
        }
        if (!ScheduleRunner.trigger(source.getServer(), sc)) {
            source.sendFailure(Component.literal("The last boss from schedule " + id + " is still alive."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Triggered schedule ")
                .append(Component.literal(id).withStyle(ChatFormatting.GOLD)), true);
        return 1;
    }

    private static int statsAll(CommandSourceStack source) {
        var bosses = BossData.RECORDS.get().bosses;
        if (bosses.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No boss kills recorded yet.").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Boss kill records:").withStyle(ChatFormatting.GREEN), false);
        bosses.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().kills, a.getValue().kills))
                .forEach(e -> source.sendSuccess(() -> Component.literal("  " + e.getKey()).withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(": " + e.getValue().kills + " kill(s)"
                                + (e.getValue().fastestTicks > 0
                                ? ", fastest " + SpawnRules.formatDuration(e.getValue().fastestTicks * 50L) : ""))
                                .withStyle(ChatFormatting.GRAY)), false));
        return bosses.size();
    }

    private static int statsBoss(CommandSourceStack source, String bossId) {
        KillRecords.BossRecord r = BossData.RECORDS.get().bosses.get(bossId);
        if (r == null) {
            source.sendFailure(Component.literal("No kills recorded for " + bossId + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(bossId).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" — " + r.kills + " kill(s)").withStyle(ChatFormatting.GRAY)), false);
        if (r.fastestTicks > 0) {
            source.sendSuccess(() -> Component.literal("  Fastest kill: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(SpawnRules.formatDuration(r.fastestTicks * 50L)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" by " + String.join(", ", r.fastestParty)).withStyle(ChatFormatting.GRAY)), false);
        }
        if (r.topDamageName != null) {
            source.sendSuccess(() -> Component.literal("  Top damage in one fight: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format(java.util.Locale.ROOT, "%,.0f", r.topDamage)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" by " + r.topDamageName).withStyle(ChatFormatting.GRAY)), false);
        }
        int rank = 0;
        for (KillRecords.PlayerKills p : KillRecords.topKillers(r, 5)) {
            final int n = ++rank;
            source.sendSuccess(() -> Component.literal("  " + n + ". " + p.name).withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" — " + p.kills + " kill(s)").withStyle(ChatFormatting.GRAY)), false);
        }
        return r.kills;
    }

    private static int startRage(CommandSourceStack source, Collection<? extends Entity> targets, String rage) {
        int changed = 0;
        for (Entity e : targets) {
            if (FiwBossesAPI.isBoss(e) && ((BossEntity) e).isActive() && ((BossEntity) e).triggerRage(rage)) changed++;
        }
        if (changed == 0) {
            source.sendFailure(Component.literal("No active boss with a rage named '" + rage + "' was selected."));
            return 0;
        }
        final int n = changed;
        source.sendSuccess(() -> Component.literal("Started rage ")
                .append(Component.literal(rage).withStyle(ChatFormatting.RED))
                .append(Component.literal(" on " + n + " boss(es).")), true);
        return n;
    }

    private static int endRage(CommandSourceStack source, Collection<? extends Entity> targets) {
        int changed = 0;
        for (Entity e : targets) {
            if (FiwBossesAPI.isBoss(e) && ((BossEntity) e).getActiveRage() != null) {
                ((BossEntity) e).endRage();
                changed++;
            }
        }
        final int n = changed;
        source.sendSuccess(() -> Component.literal("Ended the rage on " + n + " boss(es)."), true);
        return n;
    }

    private static int limitsStatus(CommandSourceStack source, String bossId) {
        BossDefinition def = BossConfigLoader.getDefinition(bossId);
        if (def == null) {
            source.sendFailure(Component.literal("Unknown boss: " + bossId));
            return 0;
        }
        SpawnData data = BossData.SPAWNS.get();
        int max = def.spawnLimits != null ? def.spawnLimits.maxAlive : 0;
        int cooldown = def.spawnLimits != null ? def.spawnLimits.cooldownSeconds : 0;
        Long last = data.lastSummon.get(bossId);
        long left = last != null && cooldown > 0 ? last + cooldown * 1000L - System.currentTimeMillis() : 0;
        String alive = data.aliveCount(bossId) + (max > 0 ? "/" + max : "") + " alive";
        String wait = left > 0 ? ", summon cooldown " + SpawnRules.formatDuration(left) : ", no summon cooldown";
        source.sendSuccess(() -> Component.literal(bossId).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(": " + alive + wait)), false);
        return 1;
    }

    private static int limitsReset(CommandSourceStack source, String bossId) {
        SpawnRules.reset(BossData.SPAWNS.get(), bossId);
        // Re-add bosses that are actually loaded right now so maxAlive stays correct.
        AABB worldBox = new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000);
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (BossEntity boss : level.getEntitiesOfClass(BossEntity.class, worldBox,
                    e -> !(e instanceof MinionEntity) && e.isAlive() && bossId.equals(e.getBossId()))) {
                BossData.SPAWNS.get().addAlive(bossId, boss.getStringUUID());
            }
        }
        BossData.SPAWNS.save();
        source.sendSuccess(() -> Component.literal("Reset summon cooldowns and alive tracking for ")
                .append(Component.literal(bossId).withStyle(ChatFormatting.GOLD)), true);
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
                    e -> !(e instanceof MinionEntity) && bossId.equals(e.getBossId())));
        }
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No living boss with id '" + bossId + "' found."));
            return 0;
        }
        found.forEach(b -> { b.suppressRewards(); b.kill(); });
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
        found.forEach(b -> { b.suppressRewards(); b.kill(); });
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

        ServerLevel level = source.getLevel();
        double x = source.getPosition().x;
        double y = source.getPosition().y;
        double z = source.getPosition().z;

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            // Jitter extra spawns slightly so they don't stack on one point
            double ox = i == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 3.0;
            double oz = i == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 3.0;

            Entity entity = def.isCustom()
                    ? spawnCustomMinion(level, def, x + ox, y, z + oz)
                    : spawnVanillaMinion(level, def, x + ox, y, z + oz);

            if (entity == null) {
                source.sendFailure(Component.literal("Failed to create minion entity"));
                return spawned;
            }

            level.addFreshEntity(entity);
            if (entity instanceof MinionEntity minion) {
                sendSkinToNearbyPlayers(level, minion);
            }
            spawned++;
        }

        final int n = spawned;
        source.sendSuccess(() -> Component.literal(n > 1 ? "Spawned " + n + "x minion " : "Spawned minion ")
                .append(Component.literal(minionId).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" at " + (int) x + ", " + (int) y + ", " + (int) z)), true);
        return n;
    }

    private static MinionEntity spawnCustomMinion(ServerLevel level, MinionDefinition def, double x, double y, double z) {
        MinionEntity minion = ModRefs.MINION.create(level);
        if (minion == null) return null;

        minion.moveTo(x, y, z, 0, 0);
        minion.applyMinionDefinition(def, null);
        minion.finalizeSpawn(level, level.getCurrentDifficultyAt(minion.blockPosition()),
                MobSpawnType.COMMAND, null);
        return minion;
    }

    // Vanilla-base minion (baseEntity = a real registry id): spawn the raw mob with
    // this definition's stat/equipment overrides, mirroring SummonMinionsGoal's
    // spawnVanillaWithOverrides() but without an owner boss to inherit a target from.
    private static Entity spawnVanillaMinion(ServerLevel level, MinionDefinition def, double x, double y, double z) {
        ResourceLocation typeId = ResourceLocation.tryParse(def.baseEntity);
        if (typeId == null) return null;

        Optional<EntityType<?>> entityTypeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId);
        if (entityTypeOpt.isEmpty()) return null;

        Entity entity = entityTypeOpt.get().create(level);
        if (entity == null) return null;

        entity.moveTo(x, y, z, 0, 0);
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                    MobSpawnType.COMMAND, null);
            mob.setPersistenceRequired();

            var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) { healthAttr.setBaseValue(def.health); mob.setHealth(def.health); }
            var armorAttr = mob.getAttribute(Attributes.ARMOR);
            if (armorAttr != null) armorAttr.setBaseValue(def.armor);
            var speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.setBaseValue(def.speed);
            var dmgAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmgAttr != null) dmgAttr.setBaseValue(def.attackDamage);
            var kbAttr = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kbAttr != null) kbAttr.setBaseValue(def.knockbackResistance);

            if (def.displayName != null) {
                mob.setCustomName(TextUtil.parseColorCodes(def.displayName));
                mob.setCustomNameVisible(true);
            }

            if (def.equipment != null) {
                setSlot(mob, EquipmentSlot.MAINHAND, def.equipment.mainHand);
                setSlot(mob, EquipmentSlot.OFFHAND, def.equipment.offHand);
                setSlot(mob, EquipmentSlot.HEAD, def.equipment.head);
                setSlot(mob, EquipmentSlot.CHEST, def.equipment.chest);
                setSlot(mob, EquipmentSlot.LEGS, def.equipment.legs);
                setSlot(mob, EquipmentSlot.FEET, def.equipment.feet);
            }
        }
        return entity;
    }

    private static void setSlot(Mob mob, EquipmentSlot slot, com.fiw.fiw_bosses.config.EquipmentEntry entry) {
        if (entry == null) return;
        var stack = ConfiguredItemStacks.equipment(entry, mob.level().getServer(), mob.registryAccess(), slot.getSerializedName());
        if (!stack.isEmpty()) mob.setItemSlot(slot, stack);
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
        found.forEach(b -> { b.suppressRewards(); b.kill(); });
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
        found.forEach(b -> { b.suppressRewards(); b.kill(); });
        final int count = found.size();
        source.sendSuccess(() -> Component.literal("Killed ")
                .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.RED))
                .append(Component.literal(" minion(s).")), true);
        return count;
    }

    // ── Reload (bosses + minions) ────────────────────────────────────────────

    private static int reloadConfigs(CommandSourceStack source) {
        int bosses = BossConfigLoader.reload();
        SettingsLoader.reload();
        int minions = MinionConfigLoader.reload();
        SkinCache.fetchAll();
        FiwToolsBridge.reportUnknownToolIds();
        source.sendSuccess(() -> Component.literal("Reloaded " + bosses + " boss + " + minions + " minion definitions.")
                .withStyle(ChatFormatting.GREEN), true);
        List<String> scheduleWarnings = ScheduleRunner.reload();
        for (String w : scheduleWarnings) {
            source.sendSuccess(() -> Component.literal("Schedule: " + w).withStyle(ChatFormatting.YELLOW), false);
        }
        int scheduled = ScheduleRunner.schedules().size();
        if (scheduled > 0) {
            source.sendSuccess(() -> Component.literal(scheduled + " spawn schedule(s) active.")
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return bosses + minions;
    }
    // ── Phase control ────────────────────────────────────────────────────────

    /** delta ±1 steps from the current phase; otherwise jumps to setTo (1-based). */
    private static int changePhase(CommandSourceStack source, Collection<? extends Entity> targets,
                                   int delta, int setTo) {
        int changed = 0;
        for (Entity entity : targets) {
            if (!FiwBossesAPI.isBoss(entity)) continue;
            int wanted = delta != 0 ? FiwBossesAPI.getPhase(entity) + delta : setTo;
            if (!FiwBossesAPI.setPhase(entity, wanted)) continue;
            changed++;
            final String line = FiwBossesAPI.getBossId(entity) + " -> phase " + wanted
                    + "/" + FiwBossesAPI.getPhaseCount(entity);
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GOLD), true);
        }
        if (changed == 0) {
            source.sendFailure(Component.literal("No boss changed phase. Targets must be active bosses "
                    + "(fight started) and the phase must exist and differ from the current one."));
        }
        return changed;
    }

    // ── Fight summary settings (global; saved to settings.json) ──────────────

    private static final List<String> SUMMARY_SECTIONS =
            List.of("damage", "taken", "deaths", "drops", "killer", "duration");

    private static int summaryStatus(CommandSourceStack source) {
        FightSummaryConfig c = SettingsLoader.get().fightSummary;
        String text = "Fight summary: " + (Boolean.TRUE.equals(c.enabled) ? "ON" : "OFF")
                + " | top " + c.topCount + " | range " + c.broadcastRange
                + " | damage=" + c.showDamageDealt + " taken=" + c.showDamageTaken
                + " deaths=" + c.showDeaths + " drops=" + c.showUniqueDrops
                + " killer=" + c.showKillingBlow + " duration=" + c.showDuration
                + " (a boss's own fightSummary block overrides these)";
        source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int summaryEnabled(CommandSourceStack source, boolean enabled) {
        SettingsLoader.get().fightSummary.enabled = enabled;
        return saveSummary(source);
    }

    private static int summaryTop(CommandSourceStack source, int count) {
        SettingsLoader.get().fightSummary.topCount = count;
        return saveSummary(source);
    }

    private static int summaryShow(CommandSourceStack source, String section, boolean visible) {
        FightSummaryConfig c = SettingsLoader.get().fightSummary;
        switch (section) {
            case "damage" -> c.showDamageDealt = visible;
            case "taken" -> c.showDamageTaken = visible;
            case "deaths" -> c.showDeaths = visible;
            case "drops" -> c.showUniqueDrops = visible;
            case "killer" -> c.showKillingBlow = visible;
            case "duration" -> c.showDuration = visible;
            default -> {
                source.sendFailure(Component.literal("Unknown section '" + section + "'. Use: "
                        + String.join(", ", SUMMARY_SECTIONS)));
                return 0;
            }
        }
        return saveSummary(source);
    }

    private static int saveSummary(CommandSourceStack source) {
        if (!SettingsLoader.save()) {
            source.sendFailure(Component.literal("Changed for this session, but settings.json could not be saved."));
        }
        return summaryStatus(source);
    }
}
