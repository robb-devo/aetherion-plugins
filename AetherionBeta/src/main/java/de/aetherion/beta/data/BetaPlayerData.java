package de.aetherion.beta.data;

import de.aetherion.beta.BetaLang;
import de.aetherion.beta.Milestone;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BetaPlayerData {

    private final UUID uuid;
    private String name = "";
    private BetaLang lang;
    private final EnumSet<Milestone> done = EnumSet.noneOf(Milestone.class);
    private final EnumMap<Milestone, Boolean> auto = new EnumMap<>(Milestone.class);
    private final Map<String, String> answers = new HashMap<>();
    private boolean submitted;
    private long firstSeenMs;
    private long lastSeenMs;
    private long sessionStartMs;
    private long playedMs;
    private boolean bookGiven;
    private boolean dirty;

    public BetaPlayerData(UUID uuid) {
        this.uuid = uuid;
        long now = System.currentTimeMillis();
        this.firstSeenMs = now;
        this.lastSeenMs = now;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            dirty = true;
        }
    }

    public BetaLang lang() {
        return lang;
    }

    public void setLang(BetaLang lang) {
        this.lang = lang;
        dirty = true;
    }

    public BetaLang langOr(BetaLang fallback) {
        return lang != null ? lang : fallback;
    }

    public boolean isDone(Milestone milestone) {
        return done.contains(milestone);
    }

    public int doneCount() {
        return done.size();
    }

    public int totalMilestones() {
        return Milestone.values().length;
    }

    public boolean mark(Milestone milestone, boolean autoMarked) {
        if (done.contains(milestone)) {
            if (autoMarked) {
                auto.put(milestone, true);
            }
            return false;
        }
        done.add(milestone);
        auto.put(milestone, autoMarked);
        dirty = true;
        return true;
    }

    public boolean toggleManual(Milestone milestone) {
        if (Boolean.TRUE.equals(auto.get(milestone)) && done.contains(milestone)) {
            return false;
        }
        if (done.contains(milestone)) {
            done.remove(milestone);
            auto.remove(milestone);
        } else {
            done.add(milestone);
            auto.put(milestone, false);
        }
        dirty = true;
        return true;
    }

    public boolean isAuto(Milestone milestone) {
        return Boolean.TRUE.equals(auto.get(milestone));
    }

    public Map<String, String> answers() {
        return answers;
    }

    public void answer(String question, String value) {
        answers.put(question, value);
        dirty = true;
    }

    public boolean submitted() {
        return submitted;
    }

    public void setSubmitted(boolean submitted) {
        this.submitted = submitted;
        dirty = true;
    }

    public long firstSeenMs() {
        return firstSeenMs;
    }

    public void setFirstSeenMs(long firstSeenMs) {
        this.firstSeenMs = firstSeenMs;
    }

    public long lastSeenMs() {
        return lastSeenMs;
    }

    public void touchSeen() {
        this.lastSeenMs = System.currentTimeMillis();
        dirty = true;
    }

    public void startSession() {
        sessionStartMs = System.currentTimeMillis();
        touchSeen();
    }

    public void endSession() {
        if (sessionStartMs > 0L) {
            playedMs += Math.max(0L, System.currentTimeMillis() - sessionStartMs);
            sessionStartMs = 0L;
            dirty = true;
        }
        touchSeen();
    }

    public long playedMs() {
        long live = playedMs;
        if (sessionStartMs > 0L) {
            live += Math.max(0L, System.currentTimeMillis() - sessionStartMs);
        }
        return live;
    }

    public void setPlayedMs(long playedMs) {
        this.playedMs = playedMs;
    }

    public boolean bookGiven() {
        return bookGiven;
    }

    public void setBookGiven(boolean bookGiven) {
        this.bookGiven = bookGiven;
        dirty = true;
    }

    public boolean dirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }

    public EnumSet<Milestone> doneSnapshot() {
        return EnumSet.copyOf(done);
    }
}
