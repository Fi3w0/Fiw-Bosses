package com.fiw.fiw_bosses.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

public class TextUtil {

    private static final Map<Character, Formatting> COLOR_MAP = new HashMap<>();

    static {
        COLOR_MAP.put('0', Formatting.BLACK);
        COLOR_MAP.put('1', Formatting.DARK_BLUE);
        COLOR_MAP.put('2', Formatting.DARK_GREEN);
        COLOR_MAP.put('3', Formatting.DARK_AQUA);
        COLOR_MAP.put('4', Formatting.DARK_RED);
        COLOR_MAP.put('5', Formatting.DARK_PURPLE);
        COLOR_MAP.put('6', Formatting.GOLD);
        COLOR_MAP.put('7', Formatting.GRAY);
        COLOR_MAP.put('8', Formatting.DARK_GRAY);
        COLOR_MAP.put('9', Formatting.BLUE);
        COLOR_MAP.put('a', Formatting.GREEN);
        COLOR_MAP.put('b', Formatting.AQUA);
        COLOR_MAP.put('c', Formatting.RED);
        COLOR_MAP.put('d', Formatting.LIGHT_PURPLE);
        COLOR_MAP.put('e', Formatting.YELLOW);
        COLOR_MAP.put('f', Formatting.WHITE);
        COLOR_MAP.put('k', Formatting.OBFUSCATED);
        COLOR_MAP.put('l', Formatting.BOLD);
        COLOR_MAP.put('m', Formatting.STRIKETHROUGH);
        COLOR_MAP.put('n', Formatting.UNDERLINE);
        COLOR_MAP.put('o', Formatting.ITALIC);
        COLOR_MAP.put('r', Formatting.RESET);
    }

    public static Text parseColorCodes(String input) {
        if (input == null || input.isEmpty()) {
            return Text.empty();
        }

        MutableText result = Text.empty();
        StringBuilder current = new StringBuilder();
        java.util.List<Formatting> activeFormats = new java.util.ArrayList<>();

        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '&' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                Formatting fmt = COLOR_MAP.get(code);
                if (fmt != null) {
                    if (current.length() > 0) {
                        MutableText part = Text.literal(current.toString());
                        for (Formatting f : activeFormats) {
                            part = part.formatted(f);
                        }
                        result.append(part);
                        current = new StringBuilder();
                    }
                    if (fmt == Formatting.RESET) {
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
            MutableText part = Text.literal(current.toString());
            for (Formatting f : activeFormats) {
                part = part.formatted(f);
            }
            result.append(part);
        }

        return result;
    }
}
