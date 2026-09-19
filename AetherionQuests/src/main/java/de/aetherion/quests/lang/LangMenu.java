package de.aetherion.quests.lang;

import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.ui.QuestProgressDisplay;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First-join / command language picker. Tiny inventory — no game systems touched.
 */
public final class LangMenu implements Listener {

    public static final String TITLE = "§8Language / Sprache";

    private static final int SLOT_EN = 11;
    private static final int SLOT_DE = 15;
    private static final int SLOT_INFO = 13;

    private static final Set<UUID> REOPEN = ConcurrentHashMap.newKeySet();

    private LangMenu() {
    }

    public static void register(AetherionQuests plugin) {
        plugin.getServer().getPluginManager().registerEvents(new LangMenu(), plugin);
    }

    public static void open(Player player, boolean forceChoice) {
        if (player == null) {
            return;
        }
        if (forceChoice) {
            REOPEN.add(player.getUniqueId());
        } else {
            REOPEN.remove(player.getUniqueId());
        }

        Holder holder = new Holder(forceChoice);
        Inventory inventory = Bukkit.createInventory(holder, 27, TITLE);
        fill(inventory);

        inventory.setItem(SLOT_INFO, item(
                Material.BOOK,
                "§fChoose your language",
                "§7Quests & hints only for now.",
                "§8Items / pets stay English.",
                "",
                "§7Später: /language  ·  /sprache"
        ));
        inventory.setItem(SLOT_EN, item(
                Material.MAP,
                "§a§lEnglish",
                "§7Fully supported",
                "",
                "§eClick to select"
        ));
        inventory.setItem(SLOT_DE, item(
                Material.WRITABLE_BOOK,
                "§6§lDeutsch",
                "§eExperimental / nicht vollständig",
                "§eIn Arbeit — fehlende Zeilen bleiben EN",
                "",
                "§eKlicken zum Auswählen"
        ));

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.35f);
    }

    public static void openIfNeeded(Player player) {
        if (player == null || PlayerLang.hasChosen(player)) {
            return;
        }
        open(player, true);
    }

    private static void fill(Inventory inventory) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", "");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane);
        }
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0 && !(lore.length == 1 && lore[0].isEmpty())) {
                meta.setLore(List.of(lore));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void pick(Player player, LangCode code, boolean forced) {
        PlayerLang.set(player, code);
        REOPEN.remove(player.getUniqueId());
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.55f, 1.4f);

        if (code.german()) {
            player.sendMessage("§6✦ §eSprache: §fDeutsch §8(§eexperimentell§8)");
            player.sendMessage("§7Quests & Hinweise oben — fehlende Texte bleiben Englisch.");
            player.sendMessage("§8Ändern: §7/sprache §8oder §7/language");
        } else {
            player.sendMessage("§6✦ §eLanguage: §fEnglish §8(§afully supported§8)");
            player.sendMessage("§7Quests & hints. Change anytime with §f/language§7.");
        }

        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin != null && plugin.getQuestManager() != null) {
            QuestManager qm = plugin.getQuestManager();
            QuestProgressDisplay.showProgress(player, qm);
        }
        if (forced) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    code.german() ? "Willkommen · Sprache gespeichert" : "Welcome · language saved",
                    net.kyori.adventure.text.format.NamedTextColor.GOLD
            ));
            // FIND EGON only after the language popup closes — never overlapping it.
            AetherionQuests quests = AetherionQuests.getInstance();
            if (quests != null) {
                quests.getServer().getScheduler().runTaskLater(quests, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    try {
                        Class.forName("de.aetherion.hub.api.AetherionHubAPI")
                                .getMethod("sendStarterHint", Player.class)
                                .invoke(null, player);
                    } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
                    }
                }, 12L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == SLOT_EN) {
            pick(player, LangCode.EN, holder.forceChoice());
        } else if (slot == SLOT_DE) {
            pick(player, LangCode.DE, holder.forceChoice());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        if (!holder.forceChoice()) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (PlayerLang.hasChosen(player)) {
            REOPEN.remove(player.getUniqueId());
            return;
        }
        if (!REOPEN.contains(player.getUniqueId())) {
            return;
        }
        AetherionQuests plugin = AetherionQuests.getInstance();
        if (plugin == null) {
            return;
        }
        // Soft re-open — they must pick once.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !PlayerLang.hasChosen(player)) {
                open(player, true);
            }
        }, 8L);
    }

    private record Holder(boolean forceChoice) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
