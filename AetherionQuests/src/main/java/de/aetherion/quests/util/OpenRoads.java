package de.aetherion.quests.util;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.lang.LangPack;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.ui.QuestHint;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The soft mid-game opening after Miss Ledger's stamp.
 * <p>
 * Not a spine and not a quest chain — four doors the harbour already has
 * (Vex, Rite Warden, Craftsman, Surveyor) plus "the wider map", in the order a
 * new player gets the most out of them. Each road is "done" by state the game
 * already tracks; nothing new is persisted here. {@code /guide}, Ledger's desk and
 * the graduation beat all read from this one list so they never disagree.
 */
public final class OpenRoads {

    public enum Road {
        STEEL("vex", "Sergeant Vex", "§c", Material.IRON_SWORD,
                "Borderlands gate",
                "Ten hostiles. Learn how a hit lands here."),
        RITES("rite_keeper", "Rite Warden", "§c", Material.BLAZE_POWDER,
                "The waste · powder altar",
                "Light a spirit. Meet something bigger."),
        CRAFT("craftsman", "Craftsman", "§e", Material.CRAFTING_TABLE,
                "Market forge",
                "Side job: show him a Mining Pickaxe. He pays."),
        SURVEY("surveyor", "Surveyor", "§b", Material.MAP,
                "Blueprint desk",
                "Troll pages from the veins become tools."),
        WILDS(null, "The wider map", "§a", Material.COMPASS,
                "Off the roads",
                "New areas unlock teleports. Crates hide where paths don't go.");

        private final String npcId;
        private final String label;
        private final String color;
        private final Material icon;
        private final String where;
        private final String pitch;

        Road(String npcId, String label, String color, Material icon, String where, String pitch) {
            this.npcId = npcId;
            this.label = label;
            this.color = color;
            this.icon = icon;
            this.where = where;
            this.pitch = pitch;
        }

        public String npcId() {
            return npcId;
        }

        public String label() {
            return label;
        }

        public String color() {
            return color;
        }

        public Material icon() {
            return icon;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public String where(Player player) {
            return LangPack.ui(player, "roads." + key() + "_where", where);
        }

        public String pitch(Player player) {
            return LangPack.ui(player, "roads." + key() + "_pitch", pitch);
        }
    }

    private static final String CRAFTSMAN_GIFT_KEY = "craftsman_mining_pick_gift";

    private OpenRoads() {
    }

    public static boolean done(Player player, QuestManager qm, Road road) {
        if (player == null || road == null) {
            return false;
        }
        return switch (road) {
            case STEEL -> qm != null && QuestStoryGate.questCompleted(player, qm, "lesson_steel");
            case RITES -> qm != null && QuestStoryGate.questCompleted(player, qm, "border_rites");
            case CRAFT -> craftsmanPaid(player);
            case SURVEY -> surveyorMet(player);
            case WILDS -> false;
        };
    }

    /** First road still open, in the order the harbour intends. WILDS when everything else is filed. */
    public static Road next(Player player, QuestManager qm) {
        for (Road road : Road.values()) {
            if (road == Road.WILDS) {
                continue;
            }
            if (!done(player, qm, road)) {
                return road;
            }
        }
        return Road.WILDS;
    }

    /**
     * Plain lines for {@code /guide} after orientation. Empty while orientation is still open
     * (the tutorial tips own that stretch).
     */
    public static List<String> tips(Player player, QuestManager qm) {
        List<String> tips = new ArrayList<>();
        if (player == null || qm == null || !QuestStoryGate.tutorialDone(player, qm)) {
            return tips;
        }
        Road next = next(player, qm);
        tips.add(LangPack.ui(player, "roads.tip_next", "Next road:") + " " + next.color() + next.label()
                + " §7— " + next.where(player) + ". " + next.pitch(player));
        int others = 0;
        for (Road road : Road.values()) {
            if (others >= 2) {
                break;
            }
            if (road == next || road == Road.WILDS || done(player, qm, road)) {
                continue;
            }
            tips.add(road.color() + road.label() + " §7— " + road.pitch(player));
            others++;
        }
        if (next != Road.WILDS) {
            tips.add(Road.WILDS.color() + Road.WILDS.label() + " §7— " + Road.WILDS.pitch(player));
        }
        return tips;
    }

    /** Point the yellow arrow at a road's NPC (no-op for the wider map). */
    public static void pin(Player player, Road road) {
        if (player == null || road == null) {
            return;
        }
        if (road.npcId() == null) {
            QuestHint.clearPending(player);
            player.sendActionBar(Component.text(
                    LangPack.ui(player, "roads.wilds_action", "Map's yours · look for a glint off the path"),
                    NamedTextColor.GREEN
            ));
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.2f);
            return;
        }
        QuestHint.show(player, road.npcId(), road.label());
        player.sendActionBar(Component.text(
                "→ " + road.label() + " · " + road.where(player),
                colorOf(road)
        ));
        player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.6f, 1.25f);
    }

    public static NamedTextColor colorOf(Road road) {
        if (road == null) {
            return NamedTextColor.GRAY;
        }
        return switch (road) {
            case STEEL, RITES -> NamedTextColor.RED;
            case CRAFT -> NamedTextColor.YELLOW;
            case SURVEY -> NamedTextColor.AQUA;
            case WILDS -> NamedTextColor.GREEN;
        };
    }

    private static boolean craftsmanPaid(Player player) {
        AetherionQuests plugin = AetherionQuests.getInstance();
        return plugin != null
                && plugin.getPlayerQuestStorage() != null
                && plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), CRAFTSMAN_GIFT_KEY);
    }

    /** Surveyor opens the blueprint hunt on the first post-stamp talk — that's "met". */
    private static boolean surveyorMet(Player player) {
        try {
            Object items = Class.forName("de.aetherion.items.AetherionItems")
                    .getMethod("getInstance").invoke(null);
            if (items == null) {
                return false;
            }
            Object service = items.getClass().getMethod("blueprintUnlocks").invoke(items);
            if (service == null) {
                return false;
            }
            Object on = service.getClass().getMethod("isHuntEnabled", Player.class).invoke(service, player);
            return on instanceof Boolean && (Boolean) on;
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            return false;
        }
    }
}
