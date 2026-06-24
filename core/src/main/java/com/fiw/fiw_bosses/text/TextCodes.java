package com.fiw.fiw_bosses.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses {@code &}-prefixed color/format codes into a list of formatted spans.
 * This is the loader-neutral half of the old {@code TextUtil.parseColorCodes} — the
 * per-version {@code TextUtil} in each {@code common-<v>} module turns the spans into
 * the version's {@code Text}/{@code Component}.
 *
 * Behaviour matches the original: a color code clears previously active formats,
 * a style code (k/l/m/n/o) stacks onto them, {@code &r} resets all, and unknown
 * codes are left as literal text.
 */
public final class TextCodes {

    private TextCodes() {}

    /** A run of text with the format codes active over it. */
    public record Span(String text, List<FormatCode> formats) {}

    public static List<Span> parse(String input) {
        List<Span> spans = new ArrayList<>();
        if (input == null || input.isEmpty()) {
            return spans;
        }

        StringBuilder current = new StringBuilder();
        List<FormatCode> active = new ArrayList<>();

        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '&' && i + 1 < input.length()) {
                FormatCode fmt = FormatCode.fromCode(input.charAt(i + 1));
                if (fmt != null) {
                    if (current.length() > 0) {
                        spans.add(new Span(current.toString(), new ArrayList<>(active)));
                        current = new StringBuilder();
                    }
                    if (fmt == FormatCode.RESET) {
                        active.clear();
                    } else if (fmt.color) {
                        active.clear();
                        active.add(fmt);
                    } else {
                        active.add(fmt);
                    }
                    i++;
                    continue;
                }
            }
            current.append(input.charAt(i));
        }

        if (current.length() > 0) {
            spans.add(new Span(current.toString(), new ArrayList<>(active)));
        }

        return spans;
    }
}
