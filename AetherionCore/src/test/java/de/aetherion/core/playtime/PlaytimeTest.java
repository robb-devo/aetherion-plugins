package de.aetherion.core.playtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytimeTest {

    @Test
    void formatsGermanAndEnglish() {
        assertEquals("0 seconds", PlaytimeFormat.format(0, false));
        assertEquals("0 Sekunden", PlaytimeFormat.format(-4, true));
        assertEquals("1 second", PlaytimeFormat.format(1, false));
        assertEquals("1 Sekunde", PlaytimeFormat.format(1, true));
        assertEquals("1 minute and 1 second", PlaytimeFormat.format(61, false));
        assertEquals("1 Minute und 1 Sekunde", PlaytimeFormat.format(61, true));
        assertEquals("1 hour, 1 minute and 1 second", PlaytimeFormat.format(3661, false));
        assertEquals("1 Tag", PlaytimeFormat.format(86_400, true));
        assertEquals("1 day, 1 hour, 1 minute and 1 second", PlaytimeFormat.format(90_061, false));
        assertEquals("2 Tage, 3 Stunden und 4 Sekunden", PlaytimeFormat.format(2 * 86_400L + 3 * 3_600L + 4, true));
    }

    @Test
    void resolvesOutsideWorldFolders(@TempDir File root) {
        File data = new File(root, "plugins/AetherionCore");
        File missingShared = new File(root, "missing/shared");
        assertEquals(new File(data, "playtime"),
                PlaytimePaths.resolve("  ", " ", missingShared, data));

        File shared = new File(root, "crafty/shared");
        assertTrue(shared.mkdirs());
        assertEquals(new File(shared, "playtime"),
                PlaytimePaths.resolve("", "", shared, data));
        assertEquals(new File("/var/opt/minecraft/crafty/shared/playtime"),
                PlaytimePaths.resolve("", "/var/opt/minecraft/crafty/shared", null, data));
        assertEquals(new File("/var/playtime"),
                PlaytimePaths.resolve(" /var/playtime ", "/var/opt/minecraft/crafty/shared", shared, data));
    }

    @Test
    void storePathDoesNotContainSiblings(@TempDir File root) throws Exception {
        File store = new File(root, "playtime");
        File inside = new File(store, "player.yml");
        File sibling = new File(root, "playtime-backup/player.yml");
        assertTrue(store.mkdirs());
        assertTrue(sibling.getParentFile().mkdirs());
        Files.writeString(inside.toPath(), "seconds: 1\n");
        Files.writeString(sibling.toPath(), "seconds: 1\n");
        assertTrue(PlaytimePaths.isInside(store, store));
        assertTrue(PlaytimePaths.isInside(store, inside));
        assertFalse(PlaytimePaths.isInside(store, sibling));
        assertFalse(PlaytimePaths.isInside(store, root));
    }

    @Test
    void sessionFlushesAndSurvivesReload(@TempDir File root) throws Exception {
        MutableClock clock = new MutableClock();
        Logger log = quietLog();
        PlaytimeStore store = new PlaytimeStore(root, log, clock);
        store.open();
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");

        store.join(id, "Steve");
        assertEquals(0L, store.seconds(id));
        clock.now = 2_500_000_000L;
        assertEquals(2L, store.seconds(id));
        store.flushOnline();
        clock.now = 4_600_000_000L;
        assertEquals(4L, store.seconds(id));

        store.quit(id);
        assertEquals(4L, store.seconds(id));
        assertEquals(id, store.findByName("steve"));

        PlaytimeStore reloaded = new PlaytimeStore(root, log);
        reloaded.open();
        assertEquals(4L, reloaded.seconds(id));
        assertEquals("Steve", reloaded.name(id));

        assertEquals(4L, reloaded.reset(id));
        assertEquals(0L, reloaded.seconds(id));

        PlaytimeStore afterReset = new PlaytimeStore(root, log);
        afterReset.open();
        assertEquals(0L, afterReset.seconds(id));
        assertEquals("Steve", afterReset.name(id));
    }

    @Test
    void otherBackendSeesQuitFlush(@TempDir File root) throws Exception {
        MutableClock clock = new MutableClock();
        Logger log = quietLog();
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PlaytimeStore hub = new PlaytimeStore(root, log, clock);
        hub.open();
        hub.join(id, "Alex");
        clock.now = 5_000_000_000L;
        hub.quit(id);

        PlaytimeStore mmo = new PlaytimeStore(root, log);
        mmo.open();
        assertEquals(5L, mmo.seconds(id));
        mmo.join(id, "Alex");
        assertEquals(5L, mmo.seconds(id));
    }

    @Test
    void offlinePlayersStayPutWhileSomeoneElseIsOnline(@TempDir File root) throws Exception {
        Logger log = quietLog();
        UUID keeper = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID online = UUID.fromString("44444444-4444-4444-4444-444444444444");
        MutableClock clock = new MutableClock();
        PlaytimeStore first = new PlaytimeStore(root, log, clock);
        first.open();
        first.join(keeper, "Keeper");
        clock.now = 9_000_000_000L;
        first.quit(keeper);

        PlaytimeStore second = new PlaytimeStore(root, log, clock);
        second.open();
        second.join(online, "Online");
        clock.now = 12_000_000_000L;
        second.flushOnline();
        assertEquals(9L, second.seconds(keeper));
        assertEquals(3L, second.seconds(online));
        assertNull(second.findByName("nobody"));
    }

    private static Logger quietLog() {
        Logger log = Logger.getLogger("playtime-test-" + UUID.randomUUID());
        log.setUseParentHandlers(false);
        return log;
    }

    private static final class MutableClock implements LongSupplier {
        private long now;

        @Override
        public long getAsLong() {
            return now;
        }
    }
}
