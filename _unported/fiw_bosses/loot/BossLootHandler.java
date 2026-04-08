package com.fiw.fiw_bosses.loot;

import com.fiw.fiw_bosses.FiwBosses;
import com.fiw.fiw_bosses.config.BossDefinition;
import com.fiw.fiw_bosses.config.LootEntry;
import com.fiw.fiw_bosses.entity.BossEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class BossLootHandler {

    public static void dropLoot(BossEntity boss, BossDefinition definition) {
        if (definition.loot == null) return;

        for (LootEntry entry : definition.loot) {
            if (entry.item == null) continue;

            // Roll chance
            if (boss.getRandom().nextFloat() > entry.chance) continue;

            Identifier itemId = Identifier.tryParse(entry.item);
            if (itemId == null) {
                FiwBosses.LOGGER.warn("Invalid item ID in loot: {}", entry.item);
                continue;
            }

            Item item = Registries.ITEM.get(itemId);
            if (item == null) {
                FiwBosses.LOGGER.warn("Unknown item ID in loot: {}", entry.item);
                continue;
            }
            ItemStack stack = new ItemStack(item, entry.count);

            // Apply NBT if present
            if (entry.nbt != null && !entry.nbt.isEmpty()) {
                try {
                    NbtCompound nbt = StringNbtReader.parse(entry.nbt);
                    stack.setNbt(nbt);
                } catch (Exception e) {
                    FiwBosses.LOGGER.warn("Failed to parse loot NBT for {}: {}", entry.item, e.getMessage());
                }
            }

            // Spawn item entity
            ItemEntity itemEntity = new ItemEntity(
                    boss.getWorld(),
                    boss.getX(),
                    boss.getY() + 0.5,
                    boss.getZ(),
                    stack
            );
            itemEntity.setVelocity(
                    (boss.getRandom().nextDouble() - 0.5) * 0.3,
                    0.3,
                    (boss.getRandom().nextDouble() - 0.5) * 0.3
            );
            boss.getWorld().spawnEntity(itemEntity);
        }
    }
}
