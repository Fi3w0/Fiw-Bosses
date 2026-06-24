package com.fiw.fiw_bosses.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextCodesTest {

    @Test
    void emptyAndNullProduceNoSpans() {
        assertTrue(TextCodes.parse("").isEmpty());
        assertTrue(TextCodes.parse(null).isEmpty());
    }

    @Test
    void plainTextIsOneUnformattedSpan() {
        List<TextCodes.Span> spans = TextCodes.parse("Hello");
        assertEquals(1, spans.size());
        assertEquals("Hello", spans.get(0).text());
        assertTrue(spans.get(0).formats().isEmpty());
    }

    @Test
    void singleColorAppliesToFollowingText() {
        List<TextCodes.Span> spans = TextCodes.parse("&cHello");
        assertEquals(1, spans.size());
        assertEquals("Hello", spans.get(0).text());
        assertEquals(List.of(FormatCode.RED), spans.get(0).formats());
    }

    @Test
    void colorResetsPreviouslyActiveStyles() {
        // &l (bold) then &c (color) — color clears formats, matching original behaviour.
        List<TextCodes.Span> spans = TextCodes.parse("&l&cX");
        assertEquals(1, spans.size());
        assertEquals("X", spans.get(0).text());
        assertEquals(List.of(FormatCode.RED), spans.get(0).formats());
    }

    @Test
    void styleStacksOntoColor() {
        List<TextCodes.Span> spans = TextCodes.parse("&aA&lB");
        assertEquals(2, spans.size());
        assertEquals("A", spans.get(0).text());
        assertEquals(List.of(FormatCode.GREEN), spans.get(0).formats());
        assertEquals("B", spans.get(1).text());
        assertEquals(List.of(FormatCode.GREEN, FormatCode.BOLD), spans.get(1).formats());
    }

    @Test
    void resetClearsFormats() {
        List<TextCodes.Span> spans = TextCodes.parse("&cRed&rPlain");
        assertEquals(2, spans.size());
        assertEquals(List.of(FormatCode.RED), spans.get(0).formats());
        assertEquals("Plain", spans.get(1).text());
        assertTrue(spans.get(1).formats().isEmpty());
    }

    @Test
    void unknownCodeIsLiteral() {
        List<TextCodes.Span> spans = TextCodes.parse("&zX");
        assertEquals(1, spans.size());
        assertEquals("&zX", spans.get(0).text());
        assertTrue(spans.get(0).formats().isEmpty());
    }
}
