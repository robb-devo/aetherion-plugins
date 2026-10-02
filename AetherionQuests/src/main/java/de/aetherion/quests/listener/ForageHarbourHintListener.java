package de.aetherion.quests.listener;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.ui.QuestHint;
import de.aetherion.quests.util.QuestStoryGate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * After Miss Ledger stamps the tutorial shut: soft tip toward the Forage Isle
 * slime pad when the player is back in Anker Harbour.
 * <p>
 * Red thread: once only, and it never steals the compass from a road the player already
 * picked (Ledger's picker, a quest in progress) — then it's a single chat crumb instead.
 * Silent if the player already met Twig or Ledger already pointed at the pad.
 */
public final class ForageHarbourHintListener implements Listener, Runnable {

    private static final String HINT_KEY = "forage_harbour_pad_hint";

    private final AetherionQuests plugin;

    public ForageHarbourHintListener(AetherionQuests plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this, 80L, 40L);
    }

    public static void tryHint(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null || plugin.getPlayerQuestStorage() == null || plugin.getQuestManager() == null) {
            return;
        }
        if (!insideHarbour(player)) {
            return;
        }
        if (insideForageIsle(player)) {
            return;
        }
        if (plugin.getPlayerQuestStorage().hasStarterKit(player.getUniqueId(), HINT_KEY)) {
            return;
        }
        if (!QuestStoryGate.tutorialDone(player, plugin.getQuestManager())) {
            return;
        }

        plugin.getPlayerQuestStorage().markStarterKit(player.getUniqueId(), HINT_KEY);
        de.aetherion.quests.npc.NpcMemory memory = de.aetherion.quests.npc.NpcMemory.get();
        if (memory != null && memory.talks(player.getUniqueId(), "forage_pad_guide") > 0) {
            return; // already met Twig — nothing to discover
        }
        player.sendMessage(de.aetherion.quests.lang.LangPack.msg(player, "harbour_pad",
                "§a✦ §7Harbour pad: §aTwig§7 points to the slime jump → §aForage Isle§7."));
        boolean busy = plugin.getQuestManager().findActiveOrReadyQuest(player) != null;
        String thread = QuestHint.pendingNpc(player);
        if (busy || (thread != null && !"ledger".equals(thread))) {
            return; // one next tip at a time — keep the road they're already on
        }
        QuestHint.clearPending(player);
        QuestHint.show(player, "forage_pad_guide", "Twig · Forage Pad");
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                de.aetherion.quests.lang.LangPack.ui(player, "tip.harbour_pad", "→ Slime pad · Forage Isle (Twig)"),
                net.kyori.adventure.text.format.NamedTextColor.GREEN
        ));
    }

    private static boolean insideHarbour(Player player) {
        try {
            de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
            if (items == null || items.getAreas() == null) {
                return false;
            }
            return items.getAreas().isType(
                    player.getLocation(),
                    de.aetherion.items.world.AreaType.STARTER_PORT
            );
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    private static boolean insideForageIsle(Player player) {
        try {
            de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
            if (items == null || items.getAreas() == null) {
                return false;
            }
            return items.getAreas().isType(
                    player.getLocation(),
                    de.aetherion.items.world.AreaType.FORAGE_ISLE
            );
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            tryHint(player);
        }
    }
}
