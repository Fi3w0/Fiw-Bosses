package com.fiw.fiw_bosses.loot;

import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.LootEntry;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.ConfiguredItemStacks;
import net.minecraft.server.level.ServerLevel;
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

            ItemStack stack = ConfiguredItemStacks.loot(entry, entity.level().getServer());
            if (stack.isEmpty()) continue;
            entity.spawnAtLocation(stack);
        }
    }
}
