package com.fiw.fiw_bosses.util;

import com.fiw.fiw_bosses.text.FormatCode;
import com.fiw.fiw_bosses.text.TextCodes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Builds a Minecraft {@link Component} from {@code &}-style codes. The parsing is
 * done by the loader-neutral {@link TextCodes} in :core; this class only maps the
 * neutral {@link FormatCode}s onto this version's {@link ChatFormatting} and builds
 * the component. ({@code FormatCode} names match {@code ChatFormatting} names.)
 */
public class TextUtil {

    public static Component parseColorCodes(String input) {
        MutableComponent result = Component.empty();
        for (TextCodes.Span span : TextCodes.parse(input)) {
            MutableComponent part = Component.literal(span.text());
            for (FormatCode f : span.formats()) {
                part = part.withStyle(ChatFormatting.valueOf(f.name()));
            }
            result.append(part);
        }
        return result;
    }
}
