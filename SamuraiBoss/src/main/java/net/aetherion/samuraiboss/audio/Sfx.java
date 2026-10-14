package net.aetherion.samuraiboss.audio;

import net.aetherion.samuraiboss.Settings;
import net.aetherion.samuraiboss.util.Rand;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;

/**
 * Das Klang-Vokabular des Kampfes. Jeder Effekt besteht aus mehreren übereinander gelegten
 * Vanilla-Sounds (z. B. "Chin" beim Einrasten = Falltür + Kette in hoher Tonlage).
 * Mit Resource Pack werden einzelne Effekte durch eigene Sounds (samurai:…) ersetzt.
 */
public final class Sfx {

    public enum Id {
        DRAW, CLICK, OPEN, SLASH, HEAVY, DASH, CUT, PETALS, BELL, TIMESTOP, TIMERESUME, KANJI, SHATTER, SUMMON,
        BOOM, WIND, THUNDER, HEARTBEAT, TAIKO, HURT, GHOST, REBUILD, CRACK, IMPACT, WHOOSH, CHIME, BLADE_FLY,
        BLADE_HIT, TORNADO, CLONE, TELEGRAPH, FLASH, RISE, LAND, BREAK_WOOD, BREAK_PAPER, BREAK_STONE, CLANG, GLINT,
        DEATH, VICTORY
    }

    record Layer(String key, float volume, float pitch, float var) {
    }

    private static final Map<Id, Layer[]> VANILLA = new EnumMap<>(Id.class);
    private static final Map<Id, String> PACK = new EnumMap<>(Id.class);

    private static void v(Id id, Layer... layers) {
        VANILLA.put(id, layers);
    }

    private static Layer l(String key, float vol, float pitch) {
        return new Layer(key, vol, pitch, 0.05f);
    }

    private static Layer l(String key, float vol, float pitch, float var) {
        return new Layer(key, vol, pitch, var);
    }

    static {
        v(Id.DRAW, l("item.trident.return", 1.2f, 1.55f), l("item.armor.equip_chain", 0.9f, 1.35f), l("block.chain.hit", 0.6f, 1.7f));
        v(Id.CLICK, l("block.iron_trapdoor.close", 1.3f, 1.95f, 0.02f), l("block.chain.place", 1.0f, 1.85f, 0.02f), l("block.note_block.hat", 0.7f, 1.9f, 0f));
        v(Id.OPEN, l("block.lever.click", 0.8f, 1.6f), l("block.chain.hit", 0.5f, 1.4f));
        v(Id.SLASH, l("entity.player.attack.sweep", 1.4f, 0.95f, 0.12f), l("item.trident.throw", 0.8f, 1.45f, 0.1f));
        v(Id.HEAVY, l("entity.player.attack.sweep", 2.0f, 0.65f), l("item.trident.throw", 1.2f, 0.9f), l("entity.wind_charge.wind_burst", 0.8f, 0.8f));
        v(Id.DASH, l("item.trident.riptide_1", 1.2f, 1.45f, 0.1f), l("entity.illusioner.mirror_move", 0.9f, 1.4f));
        v(Id.CUT, l("entity.player.attack.crit", 1.4f, 0.8f), l("block.glass.break", 0.7f, 1.6f, 0.1f), l("item.trident.hit", 1.0f, 1.3f));
        v(Id.PETALS, l("block.cherry_leaves.break", 1.0f, 1.1f, 0.2f), l("block.pink_petals.place", 1.0f, 1.2f, 0.2f));
        v(Id.BELL, l("block.bell.use", 4.0f, 0.5f, 0f), l("block.bell.resonate", 3.0f, 0.62f, 0f), l("block.note_block.bell", 1.2f, 0.53f, 0f));
        v(Id.TIMESTOP, l("block.beacon.deactivate", 3.0f, 0.5f, 0f), l("block.respawn_anchor.deplete", 2.0f, 0.6f, 0f), l("entity.warden.heartbeat", 2.0f, 0.5f, 0f));
        v(Id.TIMERESUME, l("block.beacon.activate", 2.5f, 1.6f, 0f), l("entity.wind_charge.wind_burst", 2.0f, 0.7f, 0f));
        v(Id.KANJI, l("block.amethyst_block.chime", 2.0f, 0.7f, 0.1f), l("block.end_portal_frame.fill", 1.5f, 0.6f));
        v(Id.SHATTER, l("block.glass.break", 2.0f, 0.75f, 0.1f), l("block.amethyst_cluster.break", 2.0f, 1.2f, 0.1f));
        v(Id.SUMMON, l("entity.evoker.prepare_summon", 1.5f, 1.3f), l("block.amethyst_block.resonate", 1.5f, 1.6f), l("item.trident.return", 1.0f, 0.9f));
        v(Id.BOOM, l("entity.generic.explode", 3.0f, 0.75f, 0.1f), l("entity.warden.sonic_boom", 1.2f, 1.5f, 0.05f));
        v(Id.WIND, l("entity.wind_charge.wind_burst", 1.6f, 0.9f, 0.15f), l("entity.ender_dragon.flap", 1.2f, 1.2f, 0.1f));
        v(Id.THUNDER, l("item.trident.thunder", 3.0f, 1.1f, 0.05f), l("entity.generic.explode", 2.0f, 0.6f));
        v(Id.HEARTBEAT, l("entity.warden.heartbeat", 3.0f, 0.65f, 0f));
        v(Id.TAIKO, l("block.note_block.basedrum", 3.0f, 0.55f, 0f), l("entity.generic.explode", 0.25f, 1.9f, 0f));
        v(Id.HURT, l("block.anvil.land", 0.35f, 1.8f, 0.1f), l("item.armor.equip_netherite", 0.9f, 1.3f, 0.1f));
        v(Id.GHOST, l("entity.vex.charge", 1.2f, 0.6f, 0.1f), l("entity.allay.ambient_without_item", 1.0f, 0.5f, 0.1f));
        v(Id.REBUILD, l("block.amethyst_block.resonate", 0.6f, 1.4f, 0.3f), l("block.note_block.chime", 0.5f, 1.6f, 0.3f));
        v(Id.CRACK, l("block.glass.break", 1.5f, 0.6f), l("entity.zombie.break_wooden_door", 0.8f, 1.6f));
        v(Id.IMPACT, l("entity.generic.explode", 1.6f, 1.2f, 0.1f), l("entity.iron_golem.attack", 1.4f, 0.7f, 0.1f));
        v(Id.WHOOSH, l("entity.ender_dragon.flap", 1.2f, 1.6f, 0.15f), l("entity.wind_charge.throw", 1.0f, 1.2f, 0.15f));
        v(Id.CHIME, l("block.note_block.chime", 1.4f, 1.0f, 0f), l("block.amethyst_block.chime", 1.2f, 1.2f, 0.1f));
        v(Id.BLADE_FLY, l("item.trident.throw", 1.2f, 1.6f, 0.15f), l("entity.arrow.shoot", 0.8f, 1.8f, 0.1f));
        v(Id.BLADE_HIT, l("item.trident.hit_ground", 1.4f, 1.3f, 0.1f), l("block.amethyst_block.break", 1.0f, 1.4f, 0.1f));
        v(Id.TORNADO, l("entity.breeze.idle_air", 2.5f, 0.7f, 0.05f), l("item.elytra.flying", 1.5f, 1.2f, 0f));
        v(Id.CLONE, l("entity.illusioner.mirror_move", 1.6f, 0.8f, 0.05f), l("entity.illusioner.cast_spell", 1.2f, 0.7f, 0.05f));
        v(Id.TELEGRAPH, l("block.note_block.chime", 0.9f, 1.8f, 0.05f), l("block.amethyst_block.step", 0.8f, 1.5f, 0.1f));
        v(Id.FLASH, l("entity.firework_rocket.blast", 1.5f, 1.4f), l("block.beacon.power_select", 1.2f, 1.8f));
        v(Id.RISE, l("block.beacon.ambient", 2.0f, 1.9f, 0f), l("entity.evoker.prepare_wololo", 1.0f, 1.4f, 0f));
        v(Id.LAND, l("entity.generic.big_fall", 1.4f, 0.8f), l("block.stone.break", 1.0f, 0.6f));
        v(Id.BREAK_WOOD, l("entity.zombie.break_wooden_door", 1.4f, 0.9f, 0.15f), l("block.wood.break", 1.4f, 0.8f, 0.15f));
        v(Id.BREAK_PAPER, l("block.wool.break", 1.6f, 1.3f, 0.15f), l("item.book.page_turn", 1.4f, 0.8f, 0.2f));
        v(Id.BREAK_STONE, l("block.stone.break", 1.6f, 0.7f, 0.15f), l("block.deepslate_tiles.break", 1.4f, 0.8f, 0.15f));
        v(Id.CLANG, l("block.anvil.land", 0.5f, 1.4f, 0.1f), l("item.shield.block", 1.2f, 1.2f, 0.1f));
        v(Id.GLINT, l("block.amethyst_block.chime", 1.2f, 1.9f, 0.05f), l("block.note_block.chime", 0.6f, 2.0f, 0f));
        v(Id.DEATH, l("block.bell.resonate", 3.0f, 0.5f, 0f), l("entity.allay.death", 1.5f, 0.5f, 0f), l("block.amethyst_block.resonate", 2.0f, 0.5f, 0f));
        v(Id.VICTORY, l("ui.toast.challenge_complete", 1.0f, 1.0f, 0f), l("block.note_block.chime", 1.5f, 1.2f, 0f));

        PACK.put(Id.DRAW, "samurai:katana.draw");
        PACK.put(Id.CLICK, "samurai:katana.click");
        PACK.put(Id.BELL, "samurai:bell");
        PACK.put(Id.TAIKO, "samurai:taiko");
        PACK.put(Id.SLASH, "samurai:slash");
        PACK.put(Id.TIMESTOP, "samurai:timestop");
        PACK.put(Id.SHATTER, "samurai:shatter");
        PACK.put(Id.WIND, "samurai:wind");
    }

    private final Settings settings;

    public Sfx(Settings settings) {
        this.settings = settings;
    }

    private boolean pack(Id id) {
        return settings.packEnabled && settings.customSounds && PACK.containsKey(id);
    }

    /** Räumlicher Sound an einer Position (für alle in Hörweite). */
    public void play(Location l, Id id) {
        play(l, id, 1f, 1f);
    }

    public void play(Location l, Id id, float volMul, float pitchMul) {
        World w = l.getWorld();
        if (w == null) return;
        float vol = settings.soundVolume * volMul;
        if (vol <= 0) return;
        if (pack(id)) {
            w.playSound(l, PACK.get(id), SoundCategory.HOSTILE, vol * 1.5f, pitchMul);
            return;
        }
        for (Layer layer : VANILLA.getOrDefault(id, new Layer[0])) {
            float pitch = (layer.pitch + Rand.signed() * layer.var) * pitchMul;
            w.playSound(l, layer.key, SoundCategory.HOSTILE, layer.volume * vol, clampPitch(pitch));
        }
    }

    /** Sound nur für einen Spieler, folgt ihm (z. B. Zeitstillstand im Kopf). */
    public void playFor(Player p, Id id, float volMul, float pitchMul) {
        float vol = settings.soundVolume * volMul;
        if (vol <= 0) return;
        if (pack(id)) {
            p.playSound(p, PACK.get(id), SoundCategory.HOSTILE, vol, pitchMul);
            return;
        }
        for (Layer layer : VANILLA.getOrDefault(id, new Layer[0])) {
            float pitch = (layer.pitch + Rand.signed() * layer.var) * pitchMul;
            p.playSound(p, layer.key, SoundCategory.HOSTILE, layer.volume * vol, clampPitch(pitch));
        }
    }

    /** Ein einzelner beliebiger Sound (für Musik & Sonderfälle). */
    public void raw(Player p, String key, SoundCategory cat, float vol, float pitch) {
        p.playSound(p, key, cat, vol, clampPitch(pitch));
    }

    public void rawAt(Location l, String key, float vol, float pitch) {
        if (l.getWorld() != null) l.getWorld().playSound(l, key, SoundCategory.HOSTILE, vol * settings.soundVolume, clampPitch(pitch));
    }

    private static float clampPitch(float p) {
        return Math.max(0.5f, Math.min(2.0f, p));
    }
}
