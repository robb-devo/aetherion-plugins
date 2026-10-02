package de.aetherion.items.codex;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillSeals;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The claim loop. A tier is reached by playing; it pays out when claimed — from a page, the chat
 * {@code [Claim]} button, or Claim All. Coins count toward lifetime (skill slots), Aetherion XP
 * toward the account level, Shards straight to the wallet.
 */
public final class CodexRewards {

    private CodexRewards() {
    }

    /** Claims every reached, unpaid tier behind {@code key}. Returns the number of tiers paid. */
    public static int claim(Player player, String key) {
        return claim(player, key, true);
    }

    private static int claim(Player player, String key, boolean announce) {
        CodexService service = service();
        if (player == null || key == null || service == null) {
            return 0;
        }
        if (key.startsWith("s:")) {
            AetherSkill skill = AetherSkill.byId(key.substring(2));
            return skill == null ? 0 : SkillSeals.claim(player, skill, announce);
        }
        CodexBook.Card card = CodexBook.card(key);
        if (card == null) {
            return 0;
        }
        CodexBook.State state = CodexBook.state(service, player, card);
        int tiers = state.claimable();
        if (tiers <= 0) {
            if (announce) {
                player.sendMessage(state.maxed() && state.claimed() >= state.tier()
                        ? "§7Nothing left to claim on §f" + card.name() + "§7. Tier IX. Top shelf."
                        : "§7Nothing to claim on §f" + card.name() + " §7yet. §8Next tier at "
                        + CodexText.number(Math.max(0L, state.next())) + ".");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            }
            return 0;
        }
        CodexTiers.Reward reward = state.pending();
        service.setClaimed(player, key, state.tier());
        var skills = skills();
        // Level-up lines print after the claim line, not in the middle of it.
        boolean batch = announce && skills != null && skills.beginRewardBatch(player);
        try {
            if (announce) {
                String range = tiers == 1
                        ? "Tier §6" + CodexTiers.roman(state.tier())
                        : "Tiers §6" + CodexTiers.roman(state.claimed() + 1) + "–" + CodexTiers.roman(state.tier());
                player.sendMessage("§a✔ Claimed " + card.ledger().color() + card.name() + " §7" + range
                        + " §8» " + reward.line());
                chime(player, tiers);
            }
            pay(player, reward);
        } finally {
            if (batch) {
                skills.endRewardBatch(player);
            }
        }
        return tiers;
    }

    /** Claims everything claimable across Collection, Bestiary and skill seals. */
    public static int claimAll(Player player) {
        CodexService service = service();
        if (player == null || service == null) {
            return 0;
        }
        var skills = skills();
        boolean batch = skills != null && skills.beginRewardBatch(player);
        int tiers = 0;
        int entries = 0;
        CodexTiers.Reward total = CodexTiers.Reward.NONE;
        try {
            for (CodexBook.State state : CodexBook.claimables(service, player)) {
                CodexTiers.Reward reward = state.pending();
                int paid = claim(player, state.card().key(), false);
                if (paid > 0) {
                    tiers += paid;
                    entries++;
                    total = total.plus(reward);
                }
            }
            if (skills != null) {
                for (AetherSkill skill : SkillSeals.ready(skills, service, player)) {
                    CodexTiers.Reward reward = SkillSeals.pending(skills, service, player, skill);
                    int paid = SkillSeals.claim(player, skill, false);
                    if (paid > 0) {
                        tiers += paid;
                        entries++;
                        total = total.plus(reward);
                    }
                }
            }
        } finally {
            if (batch) {
                skills.endRewardBatch(player);
            }
        }
        if (tiers <= 0) {
            player.sendMessage("§7Nothing to claim right now. §8Go break something. Respectfully.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return 0;
        }
        player.sendMessage("§a✔ Claimed §f" + tiers + " §atier" + (tiers == 1 ? "" : "s") + " §7across §f"
                + entries + " §7entr" + (entries == 1 ? "y" : "ies") + " §8» " + total.line());
        chime(player, tiers);
        return tiers;
    }

    /** Pays a reward. Aetherion XP goes through the skill service so level-ups announce normally. */
    public static void pay(Player player, CodexTiers.Reward reward) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || player == null || reward == null || reward.empty()) {
            return;
        }
        if (reward.coins() > 0L && plugin.getCoins() != null) {
            plugin.getCoins().add(player, reward.coins());
        }
        if (reward.shards() > 0L && plugin.getShards() != null) {
            plugin.getShards().add(player, reward.shards());
        }
        if (reward.xp() > 0L && plugin.getSkills() != null) {
            plugin.getSkills().addBonusXp(player, reward.xp());
        }
    }

    /** Claimable tiers everywhere (Collection + Bestiary + skill seals). */
    public static int claimableTotal(Player player) {
        CodexService service = service();
        if (player == null || service == null) {
            return 0;
        }
        int total = CodexBook.summary(service, player, CodexBook.Ledger.COLLECTION).claimableTiers()
                + CodexBook.summary(service, player, CodexBook.Ledger.BESTIARY).claimableTiers();
        var skills = skills();
        if (skills != null) {
            total += SkillSeals.readyTiers(skills, service, player);
        }
        return total;
    }

    /** Short preview lines for a Claim All button (first few entries). */
    public static List<String> preview(Player player, int limit) {
        CodexService service = service();
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        if (player == null || service == null) {
            return lines;
        }
        int shown = 0;
        int more = 0;
        for (CodexBook.State state : CodexBook.claimables(service, player)) {
            if (shown < limit) {
                lines.add("§8• " + state.card().ledger().color() + state.card().name() + " §7"
                        + (state.claimable() == 1 ? "Tier " : "Tiers ")
                        + CodexTiers.roman(state.claimed() + 1)
                        + (state.claimable() > 1 ? "–" + CodexTiers.roman(state.tier()) : ""));
                shown++;
            } else {
                more++;
            }
        }
        var skills = skills();
        if (skills != null) {
            for (AetherSkill skill : SkillSeals.ready(skills, service, player)) {
                if (shown < limit) {
                    lines.add("§8• §d" + skill.displayName() + " §7rarity seal");
                    shown++;
                } else {
                    more++;
                }
            }
        }
        if (more > 0) {
            lines.add("§8…and " + more + " more");
        }
        return lines;
    }

    private static void chime(Player player, int tiers) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, tiers > 3 ? 1.6f : 1.3f);
        if (tiers > 3) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.4f);
        }
    }

    public static CodexService service() {
        AetherionItems plugin = AetherionItems.getInstance();
        return plugin == null ? null : plugin.getCodex();
    }

    public static de.aetherion.items.skill.SkillService skills() {
        AetherionItems plugin = AetherionItems.getInstance();
        return plugin == null ? null : plugin.getSkills();
    }
}
