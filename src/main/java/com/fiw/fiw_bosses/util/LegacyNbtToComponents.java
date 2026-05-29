package com.fiw.fiw_bosses.util;

import com.fiw.fiw_bosses.FiwBosses;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a legacy 1.20.1-style NBT compound (the {@code nbt} string in the
 * boss/minion JSON configs) to an {@link ItemStack} on Minecraft 1.21.11.
 *
 * <p>Well-known vanilla keys are lifted into their proper {@code DataComponentType}
 * slots so behaviour matches the NeoForge 1.21.1 / Fabric 1.20.1 versions of the
 * mod (sharpness actually enchants the sword, display.Name actually renames the
 * item, etc.). Anything not recognised is tucked into {@code minecraft:custom_data}
 * so future translators or other mods can still read it.
 */
public final class LegacyNbtToComponents {
    private LegacyNbtToComponents() {}

    /** Mutates {@code stack} in place. {@code nbt} is consumed and may be modified. */
    public static void apply(ItemStack stack, NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        applyEnchantments(stack, nbt, lookup);
        applyDisplay(stack, nbt, lookup);
        applyCustomModelData(stack, nbt);
        applyAttributeModifiers(stack, nbt, lookup);
        applyDamage(stack, nbt);
        applyUnbreakable(stack, nbt);
        applyRepairCost(stack, nbt);
        // Anything left over is preserved verbatim under custom_data.
        if (!nbt.isEmpty()) {
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        }
    }

    // ── Enchantments ────────────────────────────────────────────────────────

    private static void applyEnchantments(ItemStack stack, NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        if (!nbt.contains("Enchantments")) return;
        NbtList list = nbt.getListOrEmpty("Enchantments");
        if (list.isEmpty()) { nbt.remove("Enchantments"); return; }

        RegistryWrapper.Impl<Enchantment> registry = lookup.getOrThrow(RegistryKeys.ENCHANTMENT);
        ItemEnchantmentsComponent.Builder builder =
                new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        boolean any = false;
        for (NbtElement el : list) {
            if (!(el instanceof NbtCompound entry)) continue;
            String idStr = entry.getString("id", "");
            Identifier id = idStr.isEmpty() ? null : Identifier.tryParse(idStr);
            if (id == null) continue;
            int lvl = entry.getInt("lvl", 0);
            if (lvl <= 0) continue;
            RegistryEntry<Enchantment> ench = registry
                    .getOptional(RegistryKey.of(RegistryKeys.ENCHANTMENT, id))
                    .orElse(null);
            if (ench == null) {
                FiwBosses.LOGGER.warn("Unknown enchantment id in JSON nbt: {}", idStr);
                continue;
            }
            builder.add(ench, lvl);
            any = true;
        }
        if (any) stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());
        nbt.remove("Enchantments");
    }

    // ── display.{Name, Lore} ────────────────────────────────────────────────

    private static void applyDisplay(ItemStack stack, NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        if (!nbt.contains("display")) return;
        NbtCompound display = nbt.getCompoundOrEmpty("display");

        String nameJson = display.getString("Name", "");
        if (!nameJson.isEmpty()) {
            Text name = parseTextJson(nameJson, lookup);
            if (name != null) stack.set(DataComponentTypes.CUSTOM_NAME, name);
            display.remove("Name");
        }

        if (display.contains("Lore")) {
            NbtList loreList = display.getListOrEmpty("Lore");
            List<Text> lines = new ArrayList<>();
            for (NbtElement el : loreList) {
                Text line = parseTextJson(el.asString().orElse(""), lookup);
                if (line != null) lines.add(line);
            }
            if (!lines.isEmpty()) {
                stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
            }
            display.remove("Lore");
        }

        // The `display` compound also held `color` (leather armour tint) and
        // `MapColor` historically — translating those is out of scope for the
        // mod's JSON contract. Drop the now-empty/leftover compound so it
        // doesn't leak into CUSTOM_DATA as noise.
        if (display.isEmpty()) nbt.remove("display");
    }

    private static Text parseTextJson(String jsonStr, RegistryWrapper.WrapperLookup lookup) {
        if (jsonStr == null || jsonStr.isEmpty()) return null;
        try {
            JsonElement json = JsonParser.parseString(jsonStr);
            return TextCodecs.CODEC.parse(lookup.getOps(JsonOps.INSTANCE), json).result().orElse(null);
        } catch (Exception e) {
            FiwBosses.LOGGER.warn("Failed to parse JSON text from nbt: {} ({})", jsonStr, e.getMessage());
            return null;
        }
    }

    // ── CustomModelData ─────────────────────────────────────────────────────

    private static void applyCustomModelData(ItemStack stack, NbtCompound nbt) {
        if (!nbt.contains("CustomModelData")) return;
        int value = nbt.getInt("CustomModelData", 0);
        // 1.21.4+ CustomModelData became a list-of-typed-values record.
        // The legacy single-int maps to one float entry; that's what vanilla
        // does when it data-fixes pre-1.21.4 items.
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
                List.of((float) value), List.of(), List.of(), List.of()));
        nbt.remove("CustomModelData");
    }

    // ── AttributeModifiers ──────────────────────────────────────────────────

    private static void applyAttributeModifiers(ItemStack stack, NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        if (!nbt.contains("AttributeModifiers")) return;
        NbtList list = nbt.getListOrEmpty("AttributeModifiers");
        if (list.isEmpty()) { nbt.remove("AttributeModifiers"); return; }

        AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder();
        boolean any = false;
        for (NbtElement el : list) {
            if (!(el instanceof NbtCompound entry)) continue;

            String attrName = entry.getString("AttributeName", "");
            Identifier attrId = attrName.isEmpty() ? null : Identifier.tryParse(attrName);
            if (attrId == null) continue;
            RegistryEntry<EntityAttribute> attrEntry = Registries.ATTRIBUTE.getEntry(attrId).orElse(null);
            if (attrEntry == null) {
                FiwBosses.LOGGER.warn("Unknown attribute id in JSON nbt AttributeModifiers: {}", attrName);
                continue;
            }

            String modName = entry.getString("Name", "fiw_bosses_legacy");
            Identifier modId = Identifier.tryParse("fiw_bosses:legacy/" + sanitise(modName));
            if (modId == null) modId = Identifier.of("fiw_bosses", "legacy");

            double amount = entry.getDouble("Amount", 0.0);
            int opCode = entry.getInt("Operation", 0);
            EntityAttributeModifier.Operation op = switch (opCode) {
                case 1 -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                case 2 -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                default -> EntityAttributeModifier.Operation.ADD_VALUE;
            };
            AttributeModifierSlot slot = parseSlot(entry.getString("Slot", "any"));

            builder.add(attrEntry, new EntityAttributeModifier(modId, amount, op), slot);
            any = true;
        }
        if (any) stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
        nbt.remove("AttributeModifiers");
    }

    private static AttributeModifierSlot parseSlot(String s) {
        if (s == null) return AttributeModifierSlot.ANY;
        return switch (s.toLowerCase()) {
            case "mainhand" -> AttributeModifierSlot.MAINHAND;
            case "offhand"  -> AttributeModifierSlot.OFFHAND;
            case "hand"     -> AttributeModifierSlot.HAND;
            case "head"     -> AttributeModifierSlot.HEAD;
            case "chest"    -> AttributeModifierSlot.CHEST;
            case "legs"     -> AttributeModifierSlot.LEGS;
            case "feet"     -> AttributeModifierSlot.FEET;
            case "armor"    -> AttributeModifierSlot.ARMOR;
            case "body"     -> AttributeModifierSlot.BODY;
            default          -> AttributeModifierSlot.ANY;
        };
    }

    private static String sanitise(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '/' || c == '.') {
                sb.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                sb.append((char) (c + 32));
            } else {
                sb.append('_');
            }
        }
        return sb.length() == 0 ? "x" : sb.toString();
    }

    // ── Scalar / flag components ────────────────────────────────────────────

    private static void applyDamage(ItemStack stack, NbtCompound nbt) {
        if (!nbt.contains("Damage")) return;
        stack.set(DataComponentTypes.DAMAGE, nbt.getInt("Damage", 0));
        nbt.remove("Damage");
    }

    private static void applyUnbreakable(ItemStack stack, NbtCompound nbt) {
        if (!nbt.contains("Unbreakable")) return;
        if (nbt.getBoolean("Unbreakable", false)) {
            stack.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        }
        nbt.remove("Unbreakable");
    }

    private static void applyRepairCost(ItemStack stack, NbtCompound nbt) {
        if (!nbt.contains("RepairCost")) return;
        stack.set(DataComponentTypes.REPAIR_COST, nbt.getInt("RepairCost", 0));
        nbt.remove("RepairCost");
    }
}
