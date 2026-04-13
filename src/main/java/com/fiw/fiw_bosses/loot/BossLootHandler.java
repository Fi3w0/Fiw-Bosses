package com.fiw.fiw_bosses.loot;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.LootEntry;
import com.fiw.fiw_bosses.entity.BossEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

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
            if (entry.item == null) continue;
            if (entity.getRandom().nextFloat() > entry.chance) continue;

            ResourceLocation itemId = ResourceLocation.tryParse(entry.item);
            if (itemId == null) {
                FiwBosses.LOGGER.warn("Invalid item id in loot: {}", entry.item);
                continue;
            }

            var item = BuiltInRegistries.ITEM.getOptional(itemId);
            if (item.isEmpty()) {
                FiwBosses.LOGGER.warn("Unknown item in loot: {}", entry.item);
                continue;
            }

            ItemStack stack = new ItemStack(item.get(), Math.max(1, entry.count));

            if (entry.nbt != null && !entry.nbt.isEmpty()) {
                try {
                    CompoundTag tag = TagParser.parseTag(entry.nbt);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                } catch (Exception e) {
                    FiwBosses.LOGGER.warn("Failed to parse loot NBT for {}: {}", entry.item, e.getMessage());
                }
            }

            entity.spawnAtLocation(stack);
        }
    }
}
