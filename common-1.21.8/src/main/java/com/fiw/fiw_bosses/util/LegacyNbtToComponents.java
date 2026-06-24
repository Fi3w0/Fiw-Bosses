package com.fiw.fiw_bosses.util;

import com.fiw.fiw_bosses.core.FiwBossesCore;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

public final class LegacyNbtToComponents {
    private LegacyNbtToComponents() {}

    public static void apply(ItemStack stack, CompoundTag originalNbt, HolderLookup.Provider lookup) {
        CompoundTag nbt = originalNbt.copy();
        applyEnchantments(stack, nbt, lookup);
        applyDisplay(stack, nbt, lookup);
        applyCustomModelData(stack, nbt);
        applyAttributeModifiers(stack, nbt);
        applyDamage(stack, nbt);
        applyUnbreakable(stack, nbt);
        applyRepairCost(stack, nbt);
        if (!nbt.isEmpty()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        }
    }

    private static void applyEnchantments(ItemStack stack, CompoundTag nbt, HolderLookup.Provider lookup) {
        if (!nbt.contains("Enchantments")) return;
        ListTag list = nbt.getListOrEmpty("Enchantments");
        if (list.isEmpty()) {
            nbt.remove("Enchantments");
            return;
        }

        HolderLookup.RegistryLookup<Enchantment> registry = lookup.lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        boolean any = false;
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag entry)) continue;
            String idString = entry.getString("id").orElse("");
            ResourceLocation id = idString.isEmpty() ? null : ResourceLocation.tryParse(idString);
            int level = entry.getInt("lvl").orElse(0);
            if (id == null || level <= 0) continue;

            Holder.Reference<Enchantment> enchantment = registry
                    .get(ResourceKey.create(Registries.ENCHANTMENT, id))
                    .orElse(null);
            if (enchantment == null) {
                FiwBossesCore.LOGGER.warn("Unknown enchantment id in JSON nbt: {}", idString);
                continue;
            }
            mutable.set(enchantment, level);
            any = true;
        }
        if (any) stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        nbt.remove("Enchantments");
    }

    private static void applyDisplay(ItemStack stack, CompoundTag nbt, HolderLookup.Provider lookup) {
        if (!nbt.contains("display")) return;
        CompoundTag display = nbt.getCompoundOrEmpty("display");

        String nameJson = display.getString("Name").orElse("");
        if (!nameJson.isEmpty()) {
            Component name = parseTextJson(nameJson, lookup);
            if (name != null) stack.set(DataComponents.CUSTOM_NAME, name);
            display.remove("Name");
        }

        if (display.contains("Lore")) {
            List<Component> lines = new ArrayList<>();
            for (Tag tag : display.getListOrEmpty("Lore")) {
                Component line = parseTextJson(tag.asString().orElse(""), lookup);
                if (line != null) lines.add(line);
            }
            if (!lines.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lines));
            display.remove("Lore");
        }

        if (display.isEmpty()) nbt.remove("display");
    }

    private static Component parseTextJson(String jsonString, HolderLookup.Provider lookup) {
        if (jsonString == null || jsonString.isEmpty()) return null;
        try {
            JsonElement json = JsonParser.parseString(jsonString);
            RegistryOps<JsonElement> ops = lookup.createSerializationContext(JsonOps.INSTANCE);
            return ComponentSerialization.CODEC.parse(ops, json).result().orElse(null);
        } catch (Exception e) {
            FiwBossesCore.LOGGER.warn("Failed to parse JSON text from nbt: {} ({})", jsonString, e.getMessage());
            return null;
        }
    }

    private static void applyCustomModelData(ItemStack stack, CompoundTag nbt) {
        if (!nbt.contains("CustomModelData")) return;
        int value = nbt.getInt("CustomModelData").orElse(0);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                List.of((float) value), List.of(), List.of(), List.of()));
        nbt.remove("CustomModelData");
    }

    private static void applyAttributeModifiers(ItemStack stack, CompoundTag nbt) {
        if (!nbt.contains("AttributeModifiers")) return;
        ListTag list = nbt.getListOrEmpty("AttributeModifiers");
        if (list.isEmpty()) {
            nbt.remove("AttributeModifiers");
            return;
        }

        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        boolean any = false;
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag entry)) continue;

            String attributeName = entry.getString("AttributeName").orElse("");
            ResourceLocation attributeId = attributeName.isEmpty() ? null : ResourceLocation.tryParse(attributeName);
            Holder<Attribute> attribute = attributeId == null
                    ? null
                    : BuiltInRegistries.ATTRIBUTE.get(attributeId).orElse(null);
            if (attribute == null) {
                FiwBossesCore.LOGGER.warn("Unknown attribute id in JSON nbt AttributeModifiers: {}", attributeName);
                continue;
            }

            String modifierName = entry.getString("Name").orElse("fiw_bosses_legacy");
            ResourceLocation modifierId = ResourceLocation.tryParse("fiw_bosses:legacy/" + sanitise(modifierName));
            if (modifierId == null) modifierId = ResourceLocation.fromNamespaceAndPath("fiw_bosses", "legacy");

            double amount = entry.getDouble("Amount").orElse(0.0);
            int operationCode = entry.getInt("Operation").orElse(0);
            AttributeModifier.Operation operation = switch (operationCode) {
                case 1 -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                case 2 -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                default -> AttributeModifier.Operation.ADD_VALUE;
            };
            EquipmentSlotGroup slot = parseSlot(entry.getString("Slot").orElse("any"));
            builder.add(attribute, new AttributeModifier(modifierId, amount, operation), slot);
            any = true;
        }
        if (any) stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        nbt.remove("AttributeModifiers");
    }

    private static EquipmentSlotGroup parseSlot(String slot) {
        if (slot == null) return EquipmentSlotGroup.ANY;
        return switch (slot.toLowerCase()) {
            case "mainhand" -> EquipmentSlotGroup.MAINHAND;
            case "offhand" -> EquipmentSlotGroup.OFFHAND;
            case "hand" -> EquipmentSlotGroup.HAND;
            case "head" -> EquipmentSlotGroup.HEAD;
            case "chest" -> EquipmentSlotGroup.CHEST;
            case "legs" -> EquipmentSlotGroup.LEGS;
            case "feet" -> EquipmentSlotGroup.FEET;
            case "armor" -> EquipmentSlotGroup.ARMOR;
            case "body" -> EquipmentSlotGroup.BODY;
            default -> EquipmentSlotGroup.ANY;
        };
    }

    private static void applyDamage(ItemStack stack, CompoundTag nbt) {
        if (!nbt.contains("Damage")) return;
        stack.set(DataComponents.DAMAGE, nbt.getInt("Damage").orElse(0));
        nbt.remove("Damage");
    }

    private static void applyUnbreakable(ItemStack stack, CompoundTag nbt) {
        if (!nbt.contains("Unbreakable")) return;
        if (nbt.getBoolean("Unbreakable").orElse(false)) {
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        }
        nbt.remove("Unbreakable");
    }

    private static void applyRepairCost(ItemStack stack, CompoundTag nbt) {
        if (!nbt.contains("RepairCost")) return;
        stack.set(DataComponents.REPAIR_COST, nbt.getInt("RepairCost").orElse(0));
        nbt.remove("RepairCost");
    }

    private static String sanitise(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '/' || c == '.') {
                out.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                out.append((char) (c + 32));
            } else {
                out.append('_');
            }
        }
        return out.isEmpty() ? "x" : out.toString();
    }
}
