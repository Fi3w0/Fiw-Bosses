package com.fiw.fiw_bosses.fight;

import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.FightSummaryConfig;
import com.fiw.fiw_bosses.config.LootEntry;
import com.fiw.fiw_bosses.config.RewardsConfig;
import com.fiw.fiw_bosses.config.SettingsLoader;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.loot.BossLootHandler;
import com.fiw.fiw_bosses.util.ConfiguredItemStacks;
import com.fiw.fiw_bosses.util.TextUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Raid-style boss fights: tracks who is engaged, and on death hands out per-player loot,
 * runs reward commands and posts the fight summary. Called from {@link BossEntity}.
 */
public final class BossFightHandler {

    // A player the boss targeted within this many ticks still counts as engaged.
    private static final long TARGET_MEMORY_TICKS = 200;

    private BossFightHandler() {}

    /** The player behind a damage source (direct hit or projectile owner), if any. */
    public static ServerPlayer playerFrom(DamageSource source) {
        return source != null && source.getEntity() instanceof ServerPlayer sp ? sp : null;
    }

    // ── Combat tracking (called every server tick while the boss is active) ──

    /**
     * Records damage taken and deaths for engaged players near the boss. Loader-free:
     * polls effective HP (health + absorption), so it works identically on every version.
     * A player is engaged once they hit the boss or the boss targeted them recently;
     * bystanders and far-away players are never recorded.
     */
    public static void trackCombat(BossEntity boss) {
        if (!(boss.level() instanceof ServerLevel level) || !boss.isActive()) return;
        long now = level.getGameTime();
        int range = SettingsLoader.get().combatTrackingRange;
        FightLedger ledger = boss.getLedger();
        Map<UUID, Float> trackedHp = boss.getTrackedHp();
        Map<UUID, Long> lastTargeted = boss.getLastTargeted();

        if (boss.getTarget() instanceof ServerPlayer targeted) {
            lastTargeted.put(targeted.getUUID(), now);
        }

        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.distanceToSqr(boss) > (double) range * range) continue;
            UUID id = player.getUUID();
            seen.add(id);
            float hp = player.isDeadOrDying() ? 0f : player.getHealth() + player.getAbsorptionAmount();
            Float last = trackedHp.put(id, hp);
            if (last == null) continue;
            // Sampled every few ticks: with doImmediateRespawn a player can die and come back as a
            // fresh entity between two samples. Count that as the death we didn't see.
            if (player.tickCount < BossEntity.COMBAT_TRACK_TICKS && last > 0f) hp = 0f;

            Long targetedAt = lastTargeted.get(id);
            boolean engaged = ledger.hasDealtDamage(id)
                    || (targetedAt != null && now - targetedAt <= TARGET_MEMORY_TICKS);
            if (!engaged) continue;

            String name = player.getName().getString();
            if (hp < last) ledger.recordTaken(id, name, last - hp, now);
            if (hp <= 0f && last > 0f) ledger.recordDeath(id, name, now);
        }
        trackedHp.keySet().retainAll(seen);
    }

    // ── Death: loot, commands, summary ───────────────────────────────────────

    public static void onBossDeath(BossEntity boss, DamageSource source) {
        if (!boss.claimRewards()) return;
        BossDefinition def = boss.getDefinition();
        if (def == null || !(boss.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        RewardsConfig rewards = def.rewards;
        boolean perPlayer = rewards != null && rewards.perPlayer();

        // Shared mode keeps the classic single ground drop (also on /boss kill, as before).
        if (!perPlayer) BossLootHandler.dropLoot(boss, def);
        if (boss.areRewardsSuppressed()) return;

        FightLedger ledger = boss.getLedger();
        ServerPlayer killer = playerFrom(source);
        if (killer == null && boss.getPreDeathFinisher() != null) {
            // Pre-death monologue ends in a self-kill; credit whoever landed the lethal hit.
            killer = server.getPlayerList().getPlayer(boss.getPreDeathFinisher());
        }
        Map<String, String> uniqueDrops = new LinkedHashMap<>();

        if (rewards != null) {
            double minDamage = def.health * Math.max(0f, rewards.minDamagePercent) / 100.0;
            List<FightLedger.Entry> eligible = new ArrayList<>();
            for (FightLedger.Entry e : ledger.eligible(minDamage)) {
                // Offline players can't receive items or run commands; they forfeit.
                if (server.getPlayerList().getPlayer(e.id) != null) eligible.add(e);
            }
            if (perPlayer) uniqueDrops = distributeLoot(boss, def, rewards, eligible, server);
            runRewardCommands(boss, def, rewards, eligible, ledger.totalDealt(), killer, server);
        }

        FightSummaryConfig summary = resolveSummary(def);
        if (Boolean.TRUE.equals(summary.enabled) && !ledger.isEmpty()) {
            sendSummary(boss, def, ledger, summary, killer, uniqueDrops, level);
        }
    }

    public static FightSummaryConfig resolveSummary(BossDefinition def) {
        FightSummaryConfig global = SettingsLoader.get().fightSummary;
        return def.fightSummary != null ? def.fightSummary.over(global) : global;
    }

    /** Rolls per-player loot, delivers it, and returns unique winners (name → item name). */
    private static Map<String, String> distributeLoot(BossEntity boss, BossDefinition def, RewardsConfig rewards,
                                                      List<FightLedger.Entry> eligible, MinecraftServer server) {
        Map<String, String> uniqueDrops = new LinkedHashMap<>();
        if (def.loot == null || def.loot.isEmpty() || eligible.isEmpty()) return uniqueDrops;

        List<RewardMath.Award> awards = RewardMath.allocate(def.loot, eligible, rewards,
                () -> boss.getRandom().nextDouble());
        Map<UUID, List<String>> received = new LinkedHashMap<>();
        for (RewardMath.Award award : awards) {
            ServerPlayer player = server.getPlayerList().getPlayer(award.player());
            if (player == null) continue;
            LootEntry entry = def.loot.get(award.entryIndex());
            int count = BossLootHandler.resolveCount(entry, boss.getRandom());
            if (count <= 0) continue;
            ItemStack stack = ConfiguredItemStacks.loot(entry, count, server);
            if (stack.isEmpty()) continue;

            String itemName = stack.getHoverName().getString();
            received.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                    .add(stack.getCount() + "x " + itemName);
            if (entry.unique) uniqueDrops.put(player.getName().getString(), itemName);
            deliver(player, stack);
        }

        for (Map.Entry<UUID, List<String>> e : received.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player != null) {
                player.sendSystemMessage(TextUtil.parseColorCodes(
                        "&6&l✦ &6Your loot: &f" + String.join("&7, &f", e.getValue())));
            }
        }
        return uniqueDrops;
    }

    /** Into the inventory; whatever doesn't fit drops at the player's feet, pickable only by them. */
    private static void deliver(ServerPlayer player, ItemStack stack) {
        int remaining = stack.getCount();
        while (remaining > 0) {
            int size = Math.min(remaining, stack.getMaxStackSize());
            ItemStack part = stack.copyWithCount(size);
            remaining -= size;
            player.getInventory().add(part);
            if (!part.isEmpty()) {
                ItemEntity dropped = player.drop(part, false);
                if (dropped != null) {
                    dropped.setNoPickUpDelay();
                    dropped.setTarget(player.getUUID());
                }
            }
        }
    }

    private static void runRewardCommands(BossEntity boss, BossDefinition def, RewardsConfig rewards,
                                          List<FightLedger.Entry> eligible, double totalDealt,
                                          ServerPlayer killer, MinecraftServer server) {
        Map<String, String> base = new HashMap<>();
        base.put("boss", def.id);
        base.put("x", String.valueOf(boss.getBlockX()));
        base.put("y", String.valueOf(boss.getBlockY()));
        base.put("z", String.valueOf(boss.getBlockZ()));
        base.put("participants", String.valueOf(eligible.size()));
        base.put("killer", killer != null ? killer.getName().getString() : null);
        base.put("mvp", eligible.isEmpty() ? null : eligible.get(0).name);

        runAll(rewards.commands, base, server);
        for (int i = 0; i < eligible.size(); i++) {
            Map<String, String> vars = playerVars(base, eligible.get(i), i + 1, totalDealt);
            runAll(rewards.participantCommands, vars, server);
            if (i == 0) runAll(rewards.mvpCommands, vars, server);
        }
    }

    private static Map<String, String> playerVars(Map<String, String> base, FightLedger.Entry e,
                                                  int rank, double totalDealt) {
        Map<String, String> vars = new HashMap<>(base);
        vars.put("player", e.name);
        vars.put("damage", String.valueOf(Math.round(e.damageDealt)));
        vars.put("percent", String.valueOf(totalDealt > 0 ? Math.round(e.damageDealt * 100.0 / totalDealt) : 0));
        vars.put("rank", String.valueOf(rank));
        return vars;
    }

    private static void runAll(List<String> commands, Map<String, String> vars, MinecraftServer server) {
        if (commands == null) return;
        outer:
        for (String template : commands) {
            if (template == null || template.isBlank()) continue;
            String cmd = template;
            for (Map.Entry<String, String> v : vars.entrySet()) {
                String key = "{" + v.getKey() + "}";
                if (!cmd.contains(key)) continue;
                // e.g. {killer} when the boss died to lava: skip rather than run a broken command.
                if (v.getValue() == null) continue outer;
                cmd = cmd.replace(key, v.getValue());
            }
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            try {
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack().withSuppressedOutput(), cmd);
            } catch (Exception e) {
                FiwBossesCore.LOGGER.warn("Boss reward command failed '{}': {}", cmd, e.getMessage());
            }
        }
    }

    private static void sendSummary(BossEntity boss, BossDefinition def, FightLedger ledger,
                                    FightSummaryConfig cfg, ServerPlayer killer,
                                    Map<String, String> uniqueDrops, ServerLevel level) {
        String bossName = def.displayName != null ? def.displayName : def.id;
        List<String> lines = FightSummary.lines(bossName, ledger, cfg,
                ledger.durationTicks(level.getGameTime()),
                killer != null ? killer.getName().getString() : null, uniqueDrops);

        // Every participant (wherever they are) plus anyone watching nearby.
        Set<ServerPlayer> recipients = new LinkedHashSet<>();
        MinecraftServer server = level.getServer();
        for (FightLedger.Entry e : ledger.participants()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.id);
            if (p != null) recipients.add(p);
        }
        int range = Math.max(0, cfg.broadcastRange != null ? cfg.broadcastRange : 64);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(boss) <= (double) range * range) recipients.add(p);
        }

        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) components.add(TextUtil.parseColorCodes(line));
        for (ServerPlayer p : recipients) {
            for (Component c : components) p.sendSystemMessage(c);
        }
    }
}
