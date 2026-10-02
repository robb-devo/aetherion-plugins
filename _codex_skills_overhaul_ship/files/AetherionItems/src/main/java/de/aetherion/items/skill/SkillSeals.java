package de.aetherion.items.skill;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.codex.CodexRewards;
import de.aetherion.items.codex.CodexService;
import de.aetherion.items.codex.CodexTiers;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Rarity seals: the first time a skill reaches Uncommon, Rare, Epic, Legendary and Mythic it
 * earns a one-off seal (coins + Aether Shards). Seals are claimed like Codex tiers and filed in the
 * Codex claim ledger under {@code s:<skill id>} — retroactive for skills already past a rarity.
 */
public final class SkillSeals {

    public static final int MAX_SEALS = 5;

    private SkillSeals() {
    }

    public static String key(AetherSkill skill) {
        return "s:" + skill.id();
    }

    /** Seals reached by level (0 at Common, 5 at Mythic). */
    public static int reached(SkillService skills, Player player, AetherSkill skill) {
        return SkillProgression.rarityTier(skills.level(player, skill));
    }

    public static int claimed(SkillService skills, CodexService codex, Player player, AetherSkill skill) {
        return Math.min(reached(skills, player, skill), codex == null ? 0 : codex.claimed(player, key(skill)));
    }

    public static int ready(SkillService skills, CodexService codex, Player player, AetherSkill skill) {
        if (skills == null || codex == null) {
            return 0;
        }
        return Math.max(0, reached(skills, player, skill) - codex.claimed(player, key(skill)));
    }

    public static List<AetherSkill> ready(SkillService skills, CodexService codex, Player player) {
        List<AetherSkill> out = new ArrayList<>();
        for (AetherSkill skill : AetherSkill.values()) {
            if (ready(skills, codex, player, skill) > 0) {
                out.add(skill);
            }
        }
        return out;
    }

    public static int readyTiers(SkillService skills, CodexService codex, Player player) {
        int total = 0;
        for (AetherSkill skill : AetherSkill.values()) {
            total += ready(skills, codex, player, skill);
        }
        return total;
    }

    /** Seals claimed across every skill, per rarity tier (index 1–5). */
    public static int[] claimedByRarity(SkillService skills, CodexService codex, Player player) {
        int[] counts = new int[MAX_SEALS + 1];
        for (AetherSkill skill : AetherSkill.values()) {
            int claimed = claimed(skills, codex, player, skill);
            for (int tier = 1; tier <= claimed; tier++) {
                counts[tier]++;
            }
        }
        return counts;
    }

    /** Skills that have reached each rarity (index 1–5). */
    public static int[] reachedByRarity(SkillService skills, Player player) {
        int[] counts = new int[MAX_SEALS + 1];
        for (AetherSkill skill : AetherSkill.values()) {
            int reached = reached(skills, player, skill);
            for (int tier = 1; tier <= reached; tier++) {
                counts[tier]++;
            }
        }
        return counts;
    }

    public static CodexTiers.Reward pending(SkillService skills, CodexService codex, Player player, AetherSkill skill) {
        CodexTiers.Reward total = CodexTiers.Reward.NONE;
        int from = codex == null ? 0 : codex.claimed(player, key(skill));
        int to = reached(skills, player, skill);
        for (int tier = from + 1; tier <= to; tier++) {
            total = total.plus(CodexTiers.seal(tier));
        }
        return total;
    }

    /** Pays every ready seal of one skill. Returns seals paid. */
    public static int claim(Player player, AetherSkill skill, boolean announce) {
        AetherionItems plugin = AetherionItems.getInstance();
        if (plugin == null || player == null || skill == null) {
            return 0;
        }
        SkillService skills = plugin.getSkills();
        CodexService codex = plugin.getCodex();
        if (skills == null || codex == null) {
            return 0;
        }
        int ready = ready(skills, codex, player, skill);
        if (ready <= 0) {
            if (announce) {
                int next = SkillProgression.nextRarityLevel(skills.level(player, skill));
                player.sendMessage("§7No seal ready on §f" + skill.displayName() + "§7."
                        + (next > 0 ? " §8Next at Lv. " + next + "." : " §8All five claimed."));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            }
            return 0;
        }
        CodexTiers.Reward reward = pending(skills, codex, player, skill);
        int reached = reached(skills, player, skill);
        codex.setClaimed(player, key(skill), reached);
        boolean batch = announce && skills.beginRewardBatch(player);
        try {
            if (announce) {
                String rarity = SkillProgression.rarityName(SkillProgression.rarity(reached * SkillProgression.RARITY_EVERY));
                player.sendMessage("§d✦ Rarity seal" + (ready > 1 ? "s" : "") + " §7claimed: "
                        + skills.coloredName(player, skill) + " §8(" + (ready > 1 ? ready + " seals, up to " : "") + rarity
                        + ") §8» " + reward.line());
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
            }
            CodexRewards.pay(player, reward);
        } finally {
            if (batch) {
                skills.endRewardBatch(player);
            }
        }
        return ready;
    }

    /** One-line pip strip for lore: claimed seals green, ready gold, locked dark. */
    public static String pips(SkillService skills, CodexService codex, Player player, AetherSkill skill) {
        int reached = reached(skills, player, skill);
        int claimed = claimed(skills, codex, player, skill);
        StringBuilder out = new StringBuilder("§7Seals ");
        String[] names = {"", "U", "R", "E", "L", "M"};
        String[] colors = {"", "§a", "§b", "§5", "§6", "§d"};
        for (int tier = 1; tier <= MAX_SEALS; tier++) {
            if (tier <= claimed) {
                out.append(colors[tier]).append("◆");
            } else if (tier <= reached) {
                out.append("§e§l✦§r");
            } else {
                out.append("§8◇");
            }
            out.append(tier <= reached ? colors[tier] : "§8").append(names[tier]).append(' ');
        }
        return out.toString().trim();
    }
}
