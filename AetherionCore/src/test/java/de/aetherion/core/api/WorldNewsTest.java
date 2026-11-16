package de.aetherion.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

class WorldNewsTest {

    @Test
    void roundTripKeepsEveryField() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("who", "Robb, Shuminite");
        data.put("nth", "1");
        UUID actor = UUID.fromString("00000000-0000-0000-0000-00000000002a");
        WorldNews news = new WorldNews("a1b2-7", 1731780000000L, "mmo-c", WorldNews.BOSS_KILL, "hollow_sun", actor,
                "Robb", data);

        WorldNews back = WorldNews.decode(news.encode());

        assertEquals(news, back);
        assertEquals(1, back.number("nth", 0));
        assertEquals("Robb, Shuminite", back.text("who", ""));
    }

    @Test
    void tabsNewlinesAndBackslashesSurviveInOneLine() {
        WorldNews news = new WorldNews("x-1", 5L, "mmo-r", "test.kind", "a\tb", null, "na\\me",
                Map.of("note", "line one\nline two\r\ttabbed \\ done", "empty", ""));

        String line = news.encode();
        WorldNews back = WorldNews.decode(line);

        assertFalse(line.contains("\n"));
        assertFalse(line.contains("\r"));
        assertEquals(news, back);
        assertNull(back.actor());
        assertEquals("", back.text("empty", "fallback"));
        assertEquals("fallback", back.text("missing", "fallback"));
    }

    @Test
    void brokenLinesAreDroppedNotThrown() {
        assertNull(WorldNews.decode(null));
        assertNull(WorldNews.decode(""));
        assertNull(WorldNews.decode("garbage"));
        assertNull(WorldNews.decode("2\tid\t5\tmmo-r\tkind\tsubject\t\tname"));
        assertNull(WorldNews.decode("1\tid\tnot-a-number\tmmo-r\tkind\tsubject\t\tname"));
        assertNull(WorldNews.decode("1\tid\t5\tmmo-r\tkind\tsubject\tnot-a-uuid\tname"));
        assertNull(WorldNews.decode("1\tid\t5\tmmo-r\t\tsubject\t\tname"));
        assertNotNull(WorldNews.decode("1\tid\t5\tmmo-r\tkind\tsubject\t\tname"));
    }

    @Test
    void extrasWithoutAKeyAreSkippedAndFlagsRead() {
        WorldNews back = WorldNews.decode("1\tid\t5\tmmo-r\tkind\tsubject\t\tname\t=novalue\tplain\tfirst=1\tloud=true");

        assertNotNull(back);
        assertEquals(2, back.data().size());
        assertTrue(back.flag("first"));
        assertTrue(back.flag("loud"));
        assertFalse(back.flag("plain"));
        assertEquals(1, back.number("first", 7));
        assertEquals(7, back.number("loud", 7));
    }

    @Test
    void dayPartsCoverTheWholeClockInOrder() {
        assertEquals(DayPart.DAWN, DayPart.of(23000L));
        assertEquals(DayPart.DAWN, DayPart.of(0L));
        assertEquals(DayPart.MORNING, DayPart.of(450L));
        assertEquals(DayPart.MIDDAY, DayPart.of(6000L));
        assertEquals(DayPart.AFTERNOON, DayPart.of(9000L));
        assertEquals(DayPart.DUSK, DayPart.of(12000L));
        assertEquals(DayPart.EVENING, DayPart.of(14000L));
        assertEquals(DayPart.NIGHT, DayPart.of(18000L));
        assertEquals(DayPart.NIGHT, DayPart.of(22799L));
        assertEquals(DayPart.MORNING, DayPart.of(24000L + 1000L));
        assertTrue(DayPart.MIDDAY.working());
        assertTrue(DayPart.NIGHT.dark());
        assertFalse(DayPart.DUSK.dark());
        assertEquals(0.5d, DayPart.DAWN.progress(23625L), 0.0001d);
        assertEquals(DayPart.DAWN, DayPart.NIGHT.next());
        assertEquals(DayPart.MORNING, DayPart.DAWN.next());
    }
}
