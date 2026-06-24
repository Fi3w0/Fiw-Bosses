package com.fiw.fiw_bosses.text;

import java.util.HashMap;
import java.util.Map;

/**
 * Loader-neutral representation of a Minecraft {@code &}-style color/format code.
 * Each {@code common-<v>} module maps these onto its version's
 * {@code Formatting}/{@code ChatFormatting} enum when building text.
 */
public enum FormatCode {
    BLACK('0', true),
    DARK_BLUE('1', true),
    DARK_GREEN('2', true),
    DARK_AQUA('3', true),
    DARK_RED('4', true),
    DARK_PURPLE('5', true),
    GOLD('6', true),
    GRAY('7', true),
    DARK_GRAY('8', true),
    BLUE('9', true),
    GREEN('a', true),
    AQUA('b', true),
    RED('c', true),
    LIGHT_PURPLE('d', true),
    YELLOW('e', true),
    WHITE('f', true),
    OBFUSCATED('k', false),
    BOLD('l', false),
    STRIKETHROUGH('m', false),
    UNDERLINE('n', false),
    ITALIC('o', false),
    RESET('r', false);

    public final char code;
    /** True for the 16 colors (which reset previously active formats); false for styles + reset. */
    public final boolean color;

    FormatCode(char code, boolean color) {
        this.code = code;
        this.color = color;
    }

    private static final Map<Character, FormatCode> BY_CODE = new HashMap<>();

    static {
        for (FormatCode f : values()) {
            BY_CODE.put(f.code, f);
        }
    }

    /** Resolve a code char (case-insensitive) to a FormatCode, or null if unknown. */
    public static FormatCode fromCode(char c) {
        return BY_CODE.get(Character.toLowerCase(c));
    }
}
