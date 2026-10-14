package de.aetherion.bossengine.music;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Plays an {@link NbsSong} for a fight audience. Advances by the song's own
 * tempo (ticks/sec from the NBS header), not a hardcoded 10/20 tps assumption.
 */
public final class FightMusic {

    private final NbsSong song;
    private final Supplier<Collection<Player>> audience;
    private final Supplier<Location> origin;
    private boolean playing;
    private boolean loop = true;
    private boolean followPlayers = true;
    private int cursor;
    private double songTick;
    private float volume = 0.85f;

    public FightMusic(NbsSong song, Supplier<Collection<Player>> audience, Supplier<Location> origin) {
        this.song = song == null ? NbsSong.empty() : song;
        this.audience = audience;
        this.origin = origin;
    }

    public void volume(float v) {
        this.volume = Math.max(0f, Math.min(4f, v));
    }

    public void loop(boolean loop) {
        this.loop = loop;
    }

    public void followPlayers(boolean follow) {
        this.followPlayers = follow;
    }

    public void play() {
        if (song.isEmpty()) {
            return;
        }
        playing = true;
        cursor = 0;
        songTick = 0.0;
    }

    public void stop() {
        playing = false;
        for (Player p : audience.get()) {
            if (p != null && p.isOnline()) {
                p.stopSound(SoundCategory.RECORDS);
            }
        }
    }

    public boolean playing() {
        return playing;
    }

    public int size() {
        return song.notes().size();
    }

    public void tick() {
        if (!playing || song.isEmpty()) {
            return;
        }
        // One Minecraft tick = 1/20 s. Advance the song clock by (songTps / 20).
        songTick += song.ticksPerSecond() / 20.0;
        Location at = origin.get();
        while (cursor < song.notes().size() && song.notes().get(cursor).tick() <= songTick) {
            NbsSong.Note n = song.notes().get(cursor++);
            float pitch = clampPitch((float) Math.pow(2.0, (n.key() - 12) / 12.0));
            float vol = volume * n.velocity();
            for (Player p : audience.get()) {
                if (p == null || !p.isOnline()) {
                    continue;
                }
                if (followPlayers) {
                    p.playSound(p.getLocation(), n.sound(), SoundCategory.RECORDS, vol, pitch);
                } else if (at != null && at.getWorld() != null) {
                    p.playSound(at, n.sound(), SoundCategory.RECORDS, vol, pitch);
                } else {
                    p.playSound(p.getLocation(), n.sound(), SoundCategory.RECORDS, vol, pitch);
                }
            }
        }
        if (cursor >= song.notes().size()) {
            if (loop) {
                cursor = 0;
                songTick = 0.0;
            } else {
                playing = false;
            }
        }
    }

    private static float clampPitch(float p) {
        return Math.max(0.5f, Math.min(2.0f, p));
    }
}
