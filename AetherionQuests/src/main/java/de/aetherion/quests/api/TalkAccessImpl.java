package de.aetherion.quests.api;

import de.aetherion.core.api.TalkAccess;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.dialog.DialogPace;
import de.aetherion.quests.talk.GuestSpeakers;
import de.aetherion.quests.talk.TalkUx;

import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** {@link TalkAccess} over the TalkUx bubble, so other plugins' NPCs talk the same way ours do. */
public final class TalkAccessImpl implements TalkAccess {

    @Override
    public void registerSpeaker(Speaker speaker) {
        GuestSpeakers.register(speaker);
    }

    @Override
    public void unregisterSpeaker(String id) {
        GuestSpeakers.unregister(id);
    }

    @Override
    public boolean bubbles(Player player) {
        TalkUx ux = TalkUx.get();
        return ux != null && ux.enabledFor(player);
    }

    @Override
    public double reach() {
        TalkUx ux = TalkUx.get();
        return ux == null ? 0.0 : ux.reach();
    }

    /**
     * Guest lines ride the same speech clock as ours ({@link DialogPace}): several {@code say}
     * calls in one tick play one after another at the shared typing speed + read gap.
     */
    @Override
    public boolean say(Player player, String speakerId, String line) {
        TalkUx ux = TalkUx.get();
        if (ux == null || player == null || line == null || !GuestSpeakers.has(speakerId) || !ux.enabledFor(player)
                || !inReach(ux, player, speakerId)) {
            return false;
        }
        String filled = de.aetherion.quests.talk.TalkText.fill(player, line);
        long delay = DialogPace.reserve(player, filled);
        if (delay <= 0L) {
            ux.line(player, speakerId, GuestSpeakers.displayName(speakerId), filled);
            return ux.isTalkingWith(player, speakerId);
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return false;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            TalkUx now = TalkUx.get();
            if (now != null && player.isOnline() && GuestSpeakers.has(speakerId)) {
                now.line(player, speakerId, GuestSpeakers.displayName(speakerId), filled);
            }
        }, delay);
        return true;
    }

    /** Chips land once the queued lines have typed out (never under a line that's still coming). */
    @Override
    public boolean replies(Player player, String speakerId, List<Reply> replies) {
        TalkUx ux = TalkUx.get();
        if (ux == null || player == null || replies == null || replies.isEmpty() || !GuestSpeakers.has(speakerId)) {
            return false;
        }
        List<TalkUx.Choice> choices = new ArrayList<>(replies.size());
        for (Reply reply : replies) {
            if (reply == null) {
                continue;
            }
            NamedTextColor color = reply.color() == null ? NamedTextColor.WHITE : reply.color();
            choices.add(new TalkUx.Choice(reply.label(), color, reply.action(), false, reply.echo()));
        }
        if (choices.isEmpty()) {
            return false;
        }
        long wait = DialogPace.untilReplies(player);
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (wait <= 0L || plugin == null) {
            return ux.choices(player, speakerId, GuestSpeakers.displayName(speakerId), choices);
        }
        if (!ux.enabledFor(player) || !inReach(ux, player, speakerId)) {
            return false;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            TalkUx now = TalkUx.get();
            if (now != null && player.isOnline()) {
                now.choices(player, speakerId, GuestSpeakers.displayName(speakerId), choices);
            }
        }, wait);
        return true;
    }

    private static boolean inReach(TalkUx ux, Player player, String speakerId) {
        org.bukkit.Location at = GuestSpeakers.locate(speakerId);
        double reach = ux.reach() + 2.0;
        return at != null && at.getWorld() != null && at.getWorld().equals(player.getWorld())
                && at.distanceSquared(player.getLocation()) <= reach * reach;
    }

    @Override
    public boolean talkingWith(Player player, String speakerId) {
        TalkUx ux = TalkUx.get();
        return ux != null && ux.isTalkingWith(player, speakerId == null ? null : speakerId.toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public boolean hasReplies(Player player, String speakerId) {
        TalkUx ux = TalkUx.get();
        return ux != null && ux.hasChipsWith(player, speakerId);
    }

    @Override
    public boolean recentlyPicked(Player player) {
        TalkUx ux = TalkUx.get();
        return ux != null && ux.recentlyPicked(player);
    }

    @Override
    public void pulse(Player player) {
        TalkUx ux = TalkUx.get();
        if (ux != null) {
            ux.pulseChips(player);
        }
    }

    @Override
    public void end(Player player) {
        TalkUx ux = TalkUx.get();
        if (ux != null) {
            ux.end(player);
        }
    }

    @Override
    public void bark(String speakerId, String line, Collection<? extends Player> viewers, int ticks) {
        TalkUx ux = TalkUx.get();
        if (ux != null && GuestSpeakers.has(speakerId)) {
            ux.bark(speakerId, GuestSpeakers.displayName(speakerId), line, viewers, ticks);
        }
    }
}
