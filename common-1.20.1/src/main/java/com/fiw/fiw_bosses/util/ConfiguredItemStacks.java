package com.fiw.fiw_bosses.util;

import com.fiw.fiw_bosses.config.EquipmentEntry;
import com.fiw.fiw_bosses.config.LootEntry;
import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.fiw.fiw_bosses.integration.FiwToolsBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

public final class ConfiguredItemStacks {
    private ConfiguredItemStacks() {}

    public static ItemStack equipment(EquipmentEntry entry, MinecraftServer server, String slotName) {
        if (entry == null) return ItemStack.EMPTY;
        if (entry.toolId != null && !entry.toolId.isEmpty()) {
            ItemStack stack = FiwToolsBridge.getItemStack(entry.toolId, server, 1);
            if (stack != null && !stack.isEmpty()) return stack;
            if (FiwToolsBridge.isPresent()) {
                FiwBossesCore.LOGGER.warn("Unknown Fiw Tools item id in equipment slot {}: {}", slotName, entry.toolId);
            }
            return ItemStack.EMPTY;
        }
        return vanilla(entry.item, entry.nbt, 1, "equipment slot " + slotName);
    }

    public static ItemStack loot(LootEntry entry, int count, MinecraftServer server) {
        if (entry == null) return ItemStack.EMPTY;
        count = Math.max(1, count);
        if (entry.toolId != null && !entry.toolId.isEmpty()) {
            ItemStack stack = FiwToolsBridge.getItemStack(entry.toolId, server, count);
            if (stack != null && !stack.isEmpty()) return stack;
            if (FiwToolsBridge.isPresent()) {
                FiwBossesCore.LOGGER.warn("Unknown Fiw Tools item id in loot: {}", entry.toolId);
            }
            return ItemStack.EMPTY;
        }
        return vanilla(entry.item, entry.nbt, count, "loot");
    }

    private static ItemStack vanilla(String itemIdString, String nbtString, int count, String context) {
        if (itemIdString == null || itemIdString.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation itemId = ResourceLocation.tryParse(itemIdString);
        if (itemId == null) {
            FiwBossesCore.LOGGER.warn("Invalid item id in {}: {}", context, itemIdString);
            return ItemStack.EMPTY;
        }

        var item = BuiltInRegistries.ITEM.getOptional(itemId);
        if (item.isEmpty()) {
            FiwBossesCore.LOGGER.warn("Unknown item in {}: {}", context, itemIdString);
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item.get(), count);
        // On 1.20.1 the legacy 1.0.9 JSON nbt IS the native item NBT format, so it
        // can be applied directly — no component conversion needed.
        if (nbtString != null && !nbtString.isEmpty()) {
            try {
                CompoundTag tag = TagParser.parseTag(nbtString);
                stack.setTag(tag);
            } catch (Exception e) {
                FiwBossesCore.LOGGER.warn("Failed to parse NBT for {} {}: {}", context, itemIdString, e.getMessage());
            }
        }
        return stack;
    }
}
