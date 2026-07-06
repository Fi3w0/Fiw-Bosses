package com.fiw.fiw_bosses.loot;

import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.LootEntry;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.ConfiguredItemStacks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class BossLootHandler {
    private BossLootHandler() {}

    public static void dropLoot(BossEntity boss, BossDefinition definition) {
        if (definition.loot == null || definition.loot.isEmpty()) return;
        dropLootEntries(boss, definition.loot);
    }

    public static void dropLootEntries(LivingEntity entity, List<LootEntry> lootEntries) {
        if (lootEntries == null) return;

        for (LootEntry entry : lootEntries) {
            if (entity.getRandom().nextFloat() > entry.chance) continue;

            int count = resolveCount(entry, entity.getRandom());
            if (count <= 0) continue;

            ItemStack stack = ConfiguredItemStacks.loot(entry, count, entity.level().getServer());
            if (stack.isEmpty()) continue;

            // Split oversized drops into valid stack sizes (e.g. 120 -> 64 + 56)
            int remaining = stack.getCount();
            while (remaining > 0) {
                int size = Math.min(remaining, stack.getMaxStackSize());
                entity.spawnAtLocation(stack.copyWithCount(size));
                remaining -= size;
            }
        }
    }

    /** Drop count: random in [minCount, maxCount] when a range is set, else fixed count. */
    private static int resolveCount(LootEntry entry, RandomSource random) {
        if (entry.minCount == null && entry.maxCount == null) return Math.max(1, entry.count);
        int min = entry.minCount != null ? entry.minCount : entry.maxCount;
        int max = entry.maxCount != null ? entry.maxCount : entry.minCount;
        if (max < min) { int tmp = min; min = max; max = tmp; }
        min = Math.max(0, min);
        max = Math.max(0, max);
        return min + random.nextInt(max - min + 1);
    }
}
