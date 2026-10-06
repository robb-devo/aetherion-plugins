package de.aetherion.beta;

import de.aetherion.beta.data.BetaPlayerData;
import de.aetherion.beta.menu.ChecklistMenu;
import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.CoinAccess;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/**
 * Live side of the beta journal: milestone toasts, open-journal refresh, and
 * the small optional rewards (coins + one cosmetic seal — never power).
 */
public final class BetaJournal {

    /** Reward clusters. {@code need < 0} = feedback submitted. */
    public enum Reward {
        FIRST_STEPS("first-steps", 3, 250, Material.COPPER_INGOT),
        HALFWAY("halfway", 5, 750, Material.GOLD_INGOT),
        FULL_CLEAR("full-clear", 7, 2000, Material.NAUTILUS_SHELL),
        FEEDBACK("feedback", -1, 500, Material.AMETHYST_SHARD);

        public final String id;
        public final int need;
        private final int defaultCoins;
        public final Material icon;

        Reward(String id, int need, int defaultCoins, Material icon) {
            this.id = id;
            this.need = need;
            this.defaultCoins = defaultCoins;
            this.icon = icon;
        }

        public String title(BetaLang lang) {
            return switch (this) {
                case FIRST_STEPS -> Texts.t(lang, "First Steps", "Erste Schritte");
                case HALFWAY -> Texts.t(lang, "Halfway There", "Halbzeit");
                case FULL_CLEAR -> Texts.t(lang, "Full Clear", "Alles erledigt");
                case FEEDBACK -> Texts.t(lang, "Honest Feedback", "Ehrliches Feedback");
            };
        }

        public String requirement(BetaLang lang) {
            return switch (this) {
                case FEEDBACK -> Texts.t(lang, "Submit the feedback form", "Feedback-Bogen absenden");
                default -> Texts.t(lang, "Track " + need + " of 7 steps", need + " von 7 Schritten erkennen lassen");
            };
        }
    }

    public enum State { LOCKED, READY, CLAIMED }

    private BetaJournal() {
    }

    /* ------------------------------------------------------------------ tracking */

    /**
     * Auto-detected progress. Cheap when already done (gather fires on every block).
     */
    public static void progress(AetherionBeta plugin, Player player, Milestone milestone) {
        if (player == null) {
            return;
        }
        BetaPlayerData data = plugin.store().get(player.getUniqueId());
        if (data.isDone(milestone) && data.isAuto(milestone)) {
            return;
        }
        boolean wasReady = anyReady(plugin, data);
        boolean fresh = data.mark(milestone, true);
        BetaLang lang = data.langOr(BetaLang.EN);
        if (fresh) {
            player.sendMessage(Texts.progressToast(lang, Texts.milestoneName(lang, milestone),
                    data.doneCount(), data.totalMilestones()));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.55f, 1.5f);
        }
        announceNewRewards(plugin, player, data, wasReady);
        ChecklistMenu.refresh(plugin, player);
    }

    /** Call after something that can unlock a reward outside milestones (feedback submit). */
    public static void announceNewRewards(AetherionBeta plugin, Player player, BetaPlayerData data, boolean wasReady) {
        if (!enabled(plugin) || wasReady) {
            return;
        }
        BetaLang lang = data.langOr(BetaLang.EN);
        for (Reward reward : Reward.values()) {
            if (state(plugin, data, reward) == State.READY) {
                player.sendMessage(Texts.rewardReadyToast(lang, reward.title(lang)));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);
                return;
            }
        }
    }

    public static boolean anyReady(AetherionBeta plugin, BetaPlayerData data) {
        for (Reward reward : Reward.values()) {
            if (state(plugin, data, reward) == State.READY) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------ rewards */

    public static boolean enabled(AetherionBeta plugin) {
        return plugin.getConfig().getBoolean("rewards.enabled", true);
    }

    public static int coins(AetherionBeta plugin, Reward reward) {
        return Math.max(0, plugin.getConfig().getInt("rewards." + reward.id, reward.defaultCoins));
    }

    public static State state(AetherionBeta plugin, BetaPlayerData data, Reward reward) {
        if (data.claimed(reward.id)) {
            return State.CLAIMED;
        }
        if (!enabled(plugin)) {
            return State.LOCKED;
        }
        boolean met = reward.need < 0 ? data.submitted() : data.trackedCount() >= reward.need;
        return met ? State.READY : State.LOCKED;
    }

    /** @return true when something was paid out. */
    public static boolean claim(AetherionBeta plugin, Player player, Reward reward) {
        BetaPlayerData data = plugin.store().get(player.getUniqueId());
        BetaLang lang = data.langOr(BetaLang.EN);
        if (state(plugin, data, reward) != State.READY) {
            return false;
        }
        int amount = coins(plugin, reward);
        CoinAccess coins = AetherServices.coins();
        if (amount > 0 && coins == null) {
            player.sendMessage(Texts.rewardsOffline(lang));
            return false;
        }
        data.claim(reward.id);
        plugin.store().save(data);
        if (amount > 0) {
            coins.add(player, amount);
        }
        String what = "§6+" + String.format(Locale.US, "%,d", amount) + " coins";
        if (reward == Reward.FULL_CLEAR) {
            ItemStack seal = seal(plugin, lang);
            player.getInventory().addItem(seal).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            what += " §8· §d" + Texts.t(lang, "Courier's Seal", "Kurier-Siegel");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
        }
        player.sendMessage(Texts.rewardClaimed(lang, reward.title(lang), what));
        plugin.getLogger().info("Beta reward " + reward.id + " claimed by " + player.getName() + " (" + amount + " coins)");
        return true;
    }

    /** Cosmetic memento for a full clear. No stats, no use — a keepsake. */
    public static ItemStack seal(AetherionBeta plugin, BetaLang lang) {
        ItemStack item = new ItemStack(Material.NAUTILUS_SHELL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Texts.t(lang, "§d✦ Courier's Seal", "§d✦ Kurier-Siegel"));
            meta.setLore(List.of(
                    Texts.t(lang, "§7Closed beta · 2026", "§7Closed Beta · 2026"),
                    Texts.t(lang, "§7Every step in the journal, ticked.", "§7Jeder Schritt im Journal, abgehakt."),
                    "",
                    Texts.t(lang, "§8Keepsake — thanks for testing Aetherion.", "§8Andenken — danke fürs Testen von Aetherion.")
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "beta_memento"), PersistentDataType.STRING, "courier_seal");
            item.setItemMeta(meta);
        }
        return item;
    }
}
