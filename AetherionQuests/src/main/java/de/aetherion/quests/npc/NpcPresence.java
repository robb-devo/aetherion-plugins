package de.aetherion.quests.npc;

import de.aetherion.quests.AetherionQuests;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Small "someone is talking to you" layer for paced NPC lines.
 * <p>
 * One cue per spoken line ({@link de.aetherion.quests.dialog.DialogPace} keeps the rhythm):
 * a per-NPC voice blip instead of one shared hat tick, and two motes in the speaker's
 * name colour just above their head. Listener only — nobody else on the pier sees it.
 * Text stays untouched; this is body language, not dialog.
 */
public final class NpcPresence {

    /** Voice plays from the NPC when the listener is this close, else at the listener. */
    private static final double VOICE_NEAR = 16.0;
    private static final double SPARKLE_RANGE = 24.0;

    private record Voice(Sound sound, float pitch, float volume) {
    }

    /** Previous shared cue — every living NPC without its own voice keeps it. */
    private static final Voice DEFAULT_VOICE = new Voice(Sound.BLOCK_NOTE_BLOCK_HAT, 1.35f, 0.35f);

    /** Early-cast NPCs get a voice you can recognise with your eyes closed. */
    private static final Map<String, Voice> VOICES = Map.ofEntries(
            Map.entry("egon", new Voice(Sound.BLOCK_NOTE_BLOCK_BIT, 0.84f, 0.26f)),
            Map.entry("lumberjack", new Voice(Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 0.92f, 0.30f)),
            Map.entry("quartermaster", new Voice(Sound.BLOCK_NOTE_BLOCK_BIT, 1.06f, 0.24f)),
            Map.entry("foreman", new Voice(Sound.BLOCK_NOTE_BLOCK_BASS, 1.30f, 0.40f)),
            Map.entry("ledger", new Voice(Sound.BLOCK_NOTE_BLOCK_CHIME, 1.62f, 0.20f)),
            Map.entry("booster_tutor", new Voice(Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 1.18f, 0.24f)),
            Map.entry("farmer", new Voice(Sound.BLOCK_NOTE_BLOCK_BANJO, 1.10f, 0.26f)),
            Map.entry("lark", new Voice(Sound.BLOCK_NOTE_BLOCK_FLUTE, 1.45f, 0.26f)),
            // Past the pier: the drill sergeant barks low, the Warden hums, the forge clinks.
            Map.entry("vex", new Voice(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.78f, 0.34f)),
            Map.entry("rite_keeper", new Voice(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.72f, 0.26f)),
            Map.entry("craftsman", new Voice(Sound.BLOCK_NOTE_BLOCK_COW_BELL, 1.12f, 0.20f)),
            Map.entry("surveyor", new Voice(Sound.BLOCK_NOTE_BLOCK_PLING, 1.28f, 0.16f))
    );

    private NpcPresence() {
    }

    /** One spoken line from {@code npcId} reached {@code player}. */
    public static void cue(Player player, String npcId) {
        if (player == null || !player.isOnline() || npcId == null) {
            return;
        }
        String id = npcId.toLowerCase(Locale.ROOT).trim();
        Voice voice = VOICES.getOrDefault(id, DEFAULT_VOICE);
        Location at = locate(id);
        boolean near = at != null
                && at.getWorld() != null
                && at.getWorld().equals(player.getWorld())
                && at.distanceSquared(player.getLocation()) <= VOICE_NEAR * VOICE_NEAR;

        // Tiny pitch drift so a run of lines sounds like speech, not a metronome.
        float drift = (float) ThreadLocalRandom.current().nextDouble(-0.04, 0.04);
        Location from = near ? at.clone().add(0.0, 1.6, 0.0) : player.getLocation();
        player.playSound(from, voice.sound(), voice.volume(), clampPitch(voice.pitch() + drift));

        if (at == null
                || at.getWorld() == null
                || !at.getWorld().equals(player.getWorld())
                || at.distanceSquared(player.getLocation()) > SPARKLE_RANGE * SPARKLE_RANGE) {
            return;
        }
        LivingNpcProfile profile = LivingNpcProfile.of(id);
        NamedTextColor tone = profile != null ? profile.nameColor() : NamedTextColor.GOLD;
        Particle.DustOptions mote = new Particle.DustOptions(
                Color.fromRGB(tone.red(), tone.green(), tone.blue()),
                0.6f
        );
        player.spawnParticle(Particle.DUST, at.clone().add(0.0, 2.05, 0.0), 2, 0.14, 0.05, 0.14, 0.0, mote);
    }

    /**
     * Best known position of an NPC: live FancyNPC host, then the spawned entity,
     * then {@code npcs.yml}. Feet level. May be null.
     */
    public static Location locate(String npcId) {
        if (npcId == null) {
            return null;
        }
        String id = npcId.toLowerCase(Locale.ROOT).trim();
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return null;
        }
        LivingNpcService living = plugin.getLivingNpcService();
        if (living != null && LivingNpcService.isLiving(id) && living.available()) {
            Location live = living.locationOf(id);
            if (live != null && live.getWorld() != null) {
                return live;
            }
        }
        QuestNPC npc = QuestNPCRegistry.getNPC(id);
        if (npc != null && npc.getEntityId() != null) {
            Entity entity = Bukkit.getEntity(npc.getEntityId());
            if (entity != null && entity.isValid() && !entity.isDead()) {
                return entity.getLocation();
            }
        }
        if (plugin.getNpcDataStorage() != null) {
            return plugin.getNpcDataStorage().getSavedLocation(id);
        }
        return null;
    }

    private static float clampPitch(float pitch) {
        return Math.max(0.5f, Math.min(2.0f, pitch));
    }
}
