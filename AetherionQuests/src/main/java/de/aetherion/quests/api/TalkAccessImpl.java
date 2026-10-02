package de.aetherion.quests.api;

import de.aetherion.core.api.TalkAccess;
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

    @Override
    public boolean say(Player player, String speakerId, String line) {
        TalkUx ux = TalkUx.get();
        if (ux == null || player == null || !GuestSpeakers.has(speakerId) || !ux.enabledFor(player)) {
            return false;
        }
        ux.line(player, speakerId, GuestSpeakers.displayName(speakerId), line);
        return ux.isTalkingWith(player, speakerId);
    }

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
        return !choices.isEmpty() && ux.choices(player, speakerId, GuestSpeakers.displayName(speakerId), choices);
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
