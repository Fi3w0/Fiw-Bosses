package com.fiw.fiw_bosses.loot;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.LootEntry;
import com.fiw.fiw_bosses.entity.BossEntity;
import com.fiw.fiw_bosses.util.LegacyNbtToComponents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

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

            Identifier itemId = Identifier.tryParse(entry.item);
            if (itemId == null) {
                FiwBosses.LOGGER.warn("Invalid item id in loot: {}", entry.item);
                continue;
            }

            Item item = Registries.ITEM.get(itemId);
            if (item == null) {
                FiwBosses.LOGGER.warn("Unknown item in loot: {}", entry.item);
                continue;
            }

            ItemStack stack = new ItemStack(item, Math.max(1, entry.count));

            if (entry.nbt != null && !entry.nbt.isEmpty()) {
                try {
                    NbtCompound nbt = StringNbtReader.readCompound(entry.nbt);
                    LegacyNbtToComponents.apply(stack, nbt, entity.getRegistryManager());
                } catch (Exception e) {
                    FiwBosses.LOGGER.warn("Failed to parse loot NBT for {}: {}", entry.item, e.getMessage());
                }
            }

            ItemEntity itemEntity = new ItemEntity(
                    entity.getEntityWorld(),
                    entity.getX(),
                    entity.getY() + 0.5,
                    entity.getZ(),
                    stack
            );
            itemEntity.setVelocity(
                    (entity.getRandom().nextDouble() - 0.5) * 0.3,
                    0.3,
                    (entity.getRandom().nextDouble() - 0.5) * 0.3
            );
            entity.getEntityWorld().spawnEntity(itemEntity);
        }
    }
}
