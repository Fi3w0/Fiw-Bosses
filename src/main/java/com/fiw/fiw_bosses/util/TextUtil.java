package com.fiw.fiw_bosses.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.Map;

public class TextUtil {

    private static final Map<Character, ChatFormatting> COLOR_MAP = new HashMap<>();

    static {
        COLOR_MAP.put('0', ChatFormatting.BLACK);
        COLOR_MAP.put('1', ChatFormatting.DARK_BLUE);
        COLOR_MAP.put('2', ChatFormatting.DARK_GREEN);
        COLOR_MAP.put('3', ChatFormatting.DARK_AQUA);
        COLOR_MAP.put('4', ChatFormatting.DARK_RED);
        COLOR_MAP.put('5', ChatFormatting.DARK_PURPLE);
        COLOR_MAP.put('6', ChatFormatting.GOLD);
        COLOR_MAP.put('7', ChatFormatting.GRAY);
        COLOR_MAP.put('8', ChatFormatting.DARK_GRAY);
        COLOR_MAP.put('9', ChatFormatting.BLUE);
        COLOR_MAP.put('a', ChatFormatting.GREEN);
        COLOR_MAP.put('b', ChatFormatting.AQUA);
        COLOR_MAP.put('c', ChatFormatting.RED);
        COLOR_MAP.put('d', ChatFormatting.LIGHT_PURPLE);
        COLOR_MAP.put('e', ChatFormatting.YELLOW);
        COLOR_MAP.put('f', ChatFormatting.WHITE);
        COLOR_MAP.put('k', ChatFormatting.OBFUSCATED);
        COLOR_MAP.put('l', ChatFormatting.BOLD);
        COLOR_MAP.put('m', ChatFormatting.STRIKETHROUGH);
        COLOR_MAP.put('n', ChatFormatting.UNDERLINE);
        COLOR_MAP.put('o', ChatFormatting.ITALIC);
        COLOR_MAP.put('r', ChatFormatting.RESET);
    }

    public static Component parseColorCodes(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        MutableComponent result = Component.empty();
        StringBuilder current = new StringBuilder();
        java.util.List<ChatFormatting> activeFormats = new java.util.ArrayList<>();

        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '&' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                ChatFormatting fmt = COLOR_MAP.get(code);
                if (fmt != null) {
                    if (current.length() > 0) {
                        MutableComponent part = Component.literal(current.toString());
                        for (ChatFormatting f : activeFormats) {
                            part = part.withStyle(f);
                        }
                        result.append(part);
                        current = new StringBuilder();
                    }
                    if (fmt == ChatFormatting.RESET) {
                        activeFormats.clear();
                    } else if (fmt.isColor()) {
                        activeFormats.clear();
                        activeFormats.add(fmt);
                    } else {
                        activeFormats.add(fmt);
                    }
                    i++;
                    continue;
                }
            }
            current.append(input.charAt(i));
        }

        if (current.length() > 0) {
            MutableComponent part = Component.literal(current.toString());
            for (ChatFormatting f : activeFormats) {
                part = part.withStyle(f);
            }
            result.append(part);
        }

        return result;
    }
}
