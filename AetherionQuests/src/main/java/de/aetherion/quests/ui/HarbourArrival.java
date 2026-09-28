package de.aetherion.quests.ui;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.npc.LivingNpcProfile;
import de.aetherion.quests.npc.NpcPresence;
import de.aetherion.quests.npc.QuestNPC;
import de.aetherion.quests.npc.QuestNPCRegistry;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anker Harbour arrival — the first ten seconds as one authored beat.
 * <pre>
 *   language picked
 *     → "ANKER HARBOUR" place card, foghorn + water (no plugin-name title)
 *     → FIND EGON card (Hub starter-hint, still the single source + shown-once flag)
 *       + two bells rung from where Egon stands + a green flare over him
 *     → Egon calls you over in chat (his own voice cue)
 * </pre>
 * Returning rookies who never spoke to Egon get {@link #nudge(Player)}: no titles,
 * just his bells, the flare and the soft hint.
 */
public final class HarbourArrival {

    private static final String EGON = "egon";
    private static final long FIND_EGON_AT = 46L;
    private static final long EGON_CALLS_AT = 72L;
    private static final long DONE_AT = 110L;
    private static final double FLARE_RANGE = 48.0;

    private static final Particle.DustOptions EGON_GREEN =
            new Particle.DustOptions(Color.fromRGB(72, 210, 96), 1.35f);
    private static final Particle.DustOptions EGON_GREEN_SOFT =
            new Particle.DustOptions(Color.fromRGB(150, 235, 160), 0.9f);

    private static final Set<UUID> PLAYING = ConcurrentHashMap.newKeySet();

    private HarbourArrival() {
    }

    /** True while the arrival beat is still running (other Egon prompts wait). */
    public static boolean isPlaying(Player player) {
        return player != null && PLAYING.contains(player.getUniqueId());
    }

    /** First join, right after the language pick. */
    public static void play(Player player) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null || player == null || !player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        PLAYING.add(id);

        placeCard(player);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            findEgonCard(player);
            flareEgon(player);
            QuestHint.show(player, EGON, egonName());
        }, FIND_EGON_AT);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (String line : LangPack.dialogs(player, "egon_arrival_call", new String[] {
                    "Oi — new face! Over here, by the green glow."
            })) {
                LivingNpcProfile.say(player, EGON, egonName(), line);
            }
        }, EGON_CALLS_AT);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> PLAYING.remove(id), DONE_AT);
    }

    /** Came back without ever talking to Egon: point, don't shout. */
    public static void nudge(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendActionBar(Component.text(
                LangPack.ui(player, "egon_nudge", "Egon's still waiting on the pier · follow the green glow"),
                NamedTextColor.GREEN
        ));
        flareEgon(player);
        QuestHint.show(player, EGON, egonName());
    }

    /* =========================================================
     * BEATS
     * ========================================================= */

    private static void placeCard(Player player) {
        String title = LangPack.ui(player, "arrival_title", "ANKER HARBOUR");
        String subtitle = LangPack.ui(player, "arrival_subtitle", "§7Aetherion · §fmind the planks, rookie");
        player.showTitle(Title.title(
                Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD),
                LegacyComponentSerializer.legacySection().deserialize(subtitle),
                Title.Times.times(Duration.ofMillis(600), Duration.ofMillis(1500), Duration.ofMillis(300))
        ));
        Location at = player.getLocation();
        // Foghorn out on the water, then the pier settling around you.
        player.playSound(at, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, SoundCategory.AMBIENT, 0.45f, 0.53f);
        player.playSound(at, Sound.ENTITY_BOAT_PADDLE_WATER, SoundCategory.AMBIENT, 0.45f, 0.85f);
        player.playSound(at.clone().add(3.0, -1.0, -2.0), Sound.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.AMBIENT, 0.25f, 1.25f);
    }

    private static void findEgonCard(Player player) {
        de.aetherion.core.api.HubAccess hub = de.aetherion.core.api.AetherServices.hub();
        if (hub != null) {
            // Hub owns FIND EGON copy + the shown-once flag. False = already shown this life.
            hub.sendStarterHint(player);
            return;
        }
        player.showTitle(Title.title(
                Component.text(LangPack.ui(player, "find_egon_title", "FIND EGON"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(LangPack.ui(player, "find_egon_subtitle", "Green glow on the pier · right-click him"),
                        NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(700))
        ));
    }

    /**
     * Two bells rung from where Egon stands (so you hear which way to walk)
     * and a green flare climbing over his head. Listener only.
     */
    public static void flareEgon(Player player) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        Location egon = NpcPresence.locate(EGON);
        if (plugin == null
                || player == null
                || egon == null
                || egon.getWorld() == null
                || !egon.getWorld().equals(player.getWorld())
                || egon.distanceSquared(player.getLocation()) > FLARE_RANGE * FLARE_RANGE) {
            return;
        }
        Location bell = egon.clone().add(0.0, 1.6, 0.0);
        player.playSound(bell, Sound.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 0.9f, 1.25f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(bell, Sound.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 0.7f, 1.5f);
            }
        }, 9L);

        new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (!player.isOnline() || step > 9) {
                    cancel();
                    return;
                }
                double y = 0.4 + step * 0.42;
                player.spawnParticle(Particle.DUST, egon.clone().add(0.0, y, 0.0), 2, 0.06, 0.08, 0.06, 0.0, EGON_GREEN);
                if (step == 9) {
                    Location top = egon.clone().add(0.0, y + 0.2, 0.0);
                    for (int i = 0; i < 14; i++) {
                        double a = i * (Math.PI * 2.0 / 14.0);
                        player.spawnParticle(
                                Particle.DUST,
                                top.clone().add(Math.cos(a) * 0.7, 0.0, Math.sin(a) * 0.7),
                                1, 0.0, 0.0, 0.0, 0.0, EGON_GREEN_SOFT
                        );
                    }
                    player.spawnParticle(Particle.HAPPY_VILLAGER, top, 6, 0.35, 0.2, 0.35, 0.0);
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    static String egonName() {
        QuestNPC npc = QuestNPCRegistry.getNPC(EGON);
        if (npc != null && npc.getName() != null && !npc.getName().isBlank()) {
            return npc.getName();
        }
        return "Egon";
    }
}
