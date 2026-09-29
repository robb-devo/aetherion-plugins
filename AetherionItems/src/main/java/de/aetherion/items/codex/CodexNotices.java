package de.aetherion.items.codex;

import de.aetherion.items.AetherionItems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * The moments: a new entry, a tier reached, a milestone crossed, a ledger maxed. One chat line
 * each, a sound that climbs with the tier, and a title only for milestones and Tier IX.
 */
public final class CodexNotices {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private CodexNotices() {
    }

    /** Called by the ledger after every count change (main thread). */
    static void progress(Player player, String key, long before, long after) {
        if (player == null || !player.isOnline() || after <= before) {
            return;
        }
        CodexBook.Card card = CodexBook.card(key);
        if (card == null) {
            return;
        }
        CodexTiers.Scale scale = card.scale();
        int tierBefore = CodexTiers.tier(scale, before);
        int tierAfter = CodexTiers.tier(scale, after);
        if (tierAfter <= tierBefore) {
            if (before <= 0L) {
                discovered(player, card);
            }
            return;
        }
        tierUp(player, card, tierBefore, tierAfter);
        milestoneCheck(player, card.ledger(), tierAfter - tierBefore);
    }

    private static void discovered(Player player, CodexBook.Card card) {
        player.sendMessage("§8[§" + (card.ledger() == CodexBook.Ledger.COLLECTION ? "a" : "6") + "Codex§8] §7New "
                + card.ledger().title() + " entry: §f" + card.name()
                + " §8· first tier at " + CodexText.number(card.scale().rung(1)));
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.3f);
    }

    private static void tierUp(Player player, CodexBook.Card card, int from, int to) {
        CodexTiers.Reward reward = CodexTiers.Reward.NONE;
        for (int t = from + 1; t <= to; t++) {
            reward = reward.plus(CodexTiers.reward(card.scale(), t));
        }
        String head = card.ledger() == CodexBook.Ledger.COLLECTION ? "§a§lCOLLECTION" : "§6§lBESTIARY";
        String line = head + " §8» §f" + card.name() + " §7reached Tier §6" + CodexTiers.roman(to)
                + (to >= CodexTiers.MAX_TIER ? " §6§l(MAX)" : "") + " ";
        Component claim = LEGACY.deserialize("§e§l[CLAIM]")
                .clickEvent(ClickEvent.runCommand("/codex claim " + card.key()))
                .hoverEvent(HoverEvent.showText(LEGACY.deserialize("§7Pays out:\n" + reward.line()
                        + "\n\n§eClick to claim")));
        Component open = LEGACY.deserialize(" §8[§7view§8]")
                .clickEvent(ClickEvent.runCommand("/codex view " + card.key()))
                .hoverEvent(HoverEvent.showText(LEGACY.deserialize("§7Open the tier ladder")));
        player.sendMessage(LEGACY.deserialize(line).append(claim).append(open));
        float pitch = 0.9f + Math.min(8, to) * 0.1f;
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45f, pitch);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, pitch);
        if (to >= CodexTiers.MAX_TIER) {
            title(player, "§6§l" + card.name().toUpperCase(java.util.Locale.ROOT), "§7Tier IX · maxed. It has nothing left to give.");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }
    }

    private static void milestoneCheck(Player player, CodexBook.Ledger ledger, int gained) {
        CodexService service = CodexRewards.service();
        if (service == null) {
            return;
        }
        int after = CodexBook.summary(service, player, ledger).level();
        int before = after - gained;
        int milestoneBefore = before / CodexTiers.MILESTONE_EVERY;
        int milestoneAfter = after / CodexTiers.MILESTONE_EVERY;
        if (milestoneAfter <= milestoneBefore) {
            return;
        }
        String perk = CodexPerks.perkLine(ledger, milestoneAfter - milestoneBefore);
        title(player, ledger.color() + "§l" + ledger.title().toUpperCase(java.util.Locale.ROOT) + " " + milestoneAfter,
                "§7Level " + after + " §8· " + perk);
        player.sendMessage(ledger.color() + "✦ " + ledger.title() + " milestone §f" + milestoneAfter
                + " §8(Level " + after + ") §8» " + perk + " §8(permanent)");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.15f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin != null && plugin.getSkills() != null && ledger == CodexBook.Ledger.BESTIARY) {
            plugin.getSkills().refresh(player);
        }
    }

    private static void title(Player player, String title, String subtitle) {
        player.showTitle(Title.title(
                LEGACY.deserialize(title),
                LEGACY.deserialize(subtitle),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2200), Duration.ofMillis(450))
        ));
    }
}
