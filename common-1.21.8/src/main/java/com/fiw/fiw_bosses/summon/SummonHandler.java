package com.fiw.fiw_bosses.summon;

import com.fiw.fiw_bosses.config.BossConfigLoader;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.BossVariants;
import com.fiw.fiw_bosses.config.SummonEntry;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.data.BossData;
import com.fiw.fiw_bosses.data.SpawnRules;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.integration.FiwToolsBridge;
import com.fiw.fiw_bosses.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * Player summons: right-click with a summon item, optionally on an altar block.
 * Loader modules forward their right-click events here; a true result means the click
 * was used (summoned or refused with a message) and vanilla handling should stop.
 */
public final class SummonHandler {

    private static final double IN_FRONT_DISTANCE = 5.0;

    private SummonHandler() {}

    /** Right-click on a block. */
    public static boolean onUseBlock(ServerPlayer player, InteractionHand hand, BlockPos pos, Direction face) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return false;
        ServerLevel level = (ServerLevel) player.level();
        String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();

        // An altar entry for this block wins over a plain item entry.
        Match match = find(stack, blockId, true);
        if (match == null) match = find(stack, null, false);
        if (match == null) return false;

        BlockPos at = face == Direction.UP || match.entry.altar != null ? pos.above() : pos.relative(face);
        summon(player, hand, stack, match, Vec3.atBottomCenterOf(at));
        return true;
    }

    /** Right-click in the air. Altar entries never fire here. */
    public static boolean onUseItem(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return false;
        Match match = find(stack, null, false);
        if (match == null) return false;
        summon(player, hand, stack, match, inFront(player));
        return true;
    }

    private record Match(BossDefinition def, SummonEntry entry, int index) {}

    private static Match find(ItemStack stack, String blockId, boolean altarOnly) {
        for (Map.Entry<String, BossDefinition> e : BossConfigLoader.getDefinitions().entrySet()) {
            List<SummonEntry> entries = e.getValue().summon;
            if (entries == null) continue;
            for (int i = 0; i < entries.size(); i++) {
                SummonEntry entry = entries.get(i);
                if (entry == null || !entry.hasItem()) continue;
                boolean hasAltar = entry.altar != null && !entry.altar.isEmpty();
                if (altarOnly ? !hasAltar || !normalize(entry.altar).equals(blockId) : hasAltar) continue;
                if (matchesItem(stack, entry)) return new Match(e.getValue(), entry, i);
            }
        }
        return null;
    }

    private static boolean matchesItem(ItemStack stack, SummonEntry entry) {
        if (entry.toolId != null && !entry.toolId.isEmpty()) {
            if (!entry.toolId.equals(FiwToolsBridge.getToolId(stack))) return false;
        } else {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!normalize(entry.item).equals(id)) return false;
        }
        if (entry.name != null && !entry.name.isEmpty()) {
            String wanted = TextUtil.parseColorCodes(entry.name).getString();
            return wanted.equals(stack.getHoverName().getString());
        }
        return true;
    }

    private static void summon(ServerPlayer player, InteractionHand hand, ItemStack stack, Match match, Vec3 at) {
        BossDefinition base = match.def;
        String bossName = base.displayName != null ? base.displayName : base.id;
        String uuid = player.getStringUUID();
        long now = System.currentTimeMillis();

        SpawnRules.Refusal refusal = SpawnRules.check(BossData.SPAWNS.get(), base.id, bossName,
                base.spawnLimits, match.entry, match.index, uuid, now);
        if (refusal != null) {
            player.sendSystemMessage(TextUtil.parseColorCodes(refusal.message()));
            return;
        }

        String variant = match.entry.variant;
        if (variant != null && !variant.isEmpty() && BossVariants.find(base, variant) == null) {
            FiwBossesCore.LOGGER.warn("Summon item for boss '{}' names unknown variant '{}'; spawning the normal boss",
                    base.id, variant);
        }
        BossDefinition def = BossVariants.apply(base, variant);

        ServerLevel level = (ServerLevel) player.level();
        BossEntity boss = BossSpawner.spawn(level, def, at.x, at.y, at.z, EntitySpawnReason.TRIGGERED);
        if (boss == null) return;

        SpawnRules.record(BossData.SPAWNS.get(), base.id, match.entry, match.index, uuid, now);
        BossData.SPAWNS.save();

        if (match.entry.consume && !player.getAbilities().instabuild) stack.shrink(1);
        player.swing(hand, true);

        if (match.entry.message != null && !match.entry.message.isEmpty()) {
            String text = match.entry.message
                    .replace("{player}", player.getName().getString())
                    .replace("{boss}", def.displayName != null ? def.displayName : def.id);
            level.getServer().getPlayerList().broadcastSystemMessage(TextUtil.parseColorCodes(text), false);
        }
    }

    /** A standable spot about five blocks in front of the player, or the player's own position. */
    private static Vec3 inFront(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0e-4) return player.position();
        Vec3 target = player.position().add(flat.normalize().scale(IN_FRONT_DISTANCE));
        BlockPos base = BlockPos.containing(target);
        for (int dy = 3; dy >= -6; dy--) {
            BlockPos p = base.offset(0, dy, 0);
            if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
                    && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
                return Vec3.atBottomCenterOf(p);
            }
        }
        return player.position();
    }

    private static String normalize(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }
}
