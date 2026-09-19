package de.aetherion.beta.menu;

import de.aetherion.beta.AetherionBeta;
import de.aetherion.beta.BetaLang;
import de.aetherion.beta.Milestone;
import de.aetherion.beta.Texts;
import de.aetherion.beta.data.BetaPlayerData;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class AdminBetaMenu {

    private AdminBetaMenu() {
    }

    public static void openRoot(AetherionBeta plugin, Player admin) {
        plugin.store().loadAllFromDisk();
        for (Player online : Bukkit.getOnlinePlayers()) {
            plugin.store().get(online.getUniqueId()).setName(online.getName());
        }
        List<BetaPlayerData> players = new ArrayList<>(plugin.store().cached());
        players.sort(Comparator
                .comparing(BetaPlayerData::submitted).reversed()
                .thenComparing(BetaPlayerData::lastSeenMs).reversed());

        int rows = Math.min(6, Math.max(3, ((players.size() - 1) / 7) + 3));
        Inventory inventory = Bukkit.createInventory(new RootHolder(), rows * 9, Texts.adminTitle());
        GuiUtil.frame(inventory);

        inventory.setItem(4, GuiUtil.named(
                Material.NETHER_STAR,
                "§d✦ Beta board",
                "§7Online now: §f" + Bukkit.getOnlinePlayers().size(),
                "§7Tracked players: §f" + players.size(),
                "§7Submitted feedback: §f" + players.stream().filter(BetaPlayerData::submitted).count(),
                "",
                "§8Click a head for details"
        ));

        int slot = 10;
        for (BetaPlayerData data : players) {
            while (slot % 9 == 0 || slot % 9 == 8 || slot >= inventory.getSize() - 9) {
                slot++;
                if (slot >= inventory.getSize()) {
                    break;
                }
            }
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot, head(data));
            slot++;
        }

        inventory.setItem(inventory.getSize() - 5, GuiUtil.named(
                Material.ENDER_PEARL,
                "§eRefresh",
                "§7Reload this board"
        ));
        admin.openInventory(inventory);
    }

    public static void openPlayer(AetherionBeta plugin, Player admin, UUID targetId) {
        BetaPlayerData data = plugin.store().get(targetId);
        BetaLang lang = data.langOr(BetaLang.EN);
        Inventory inventory = Bukkit.createInventory(new PlayerHolder(targetId), 45, "§8Beta · " + safeName(data));
        GuiUtil.frame(inventory);

        inventory.setItem(4, head(data));
        inventory.setItem(19, GuiUtil.named(
                Material.CLOCK,
                "§fSession",
                "§7Played: §f" + formatDuration(data.playedMs()),
                "§7Language: §f" + (data.lang() == null ? "—" : data.lang().name()),
                "§7Book given: §f" + yesNo(data.bookGiven()),
                "§7Submitted: §f" + yesNo(data.submitted())
        ));

        int slot = 21;
        for (Milestone milestone : Milestone.values()) {
            boolean done = data.isDone(milestone);
            inventory.setItem(slot++, GuiUtil.named(
                    done ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    (done ? "§a✔ " : "§8○ ") + Texts.milestoneName(lang, milestone),
                    data.isAuto(milestone) ? "§8auto" : "§8manual"
            ));
        }

        inventory.setItem(37, GuiUtil.named(
                Material.AMETHYST_SHARD,
                "§dFeedback answers",
                "§7Overall: §f" + label(lang, data.answers().get(RatingMenu.Q_OVERALL)),
                "§7Favorite: §f" + label(lang, data.answers().get(RatingMenu.Q_FAVORITE)),
                "§7Confusing: §f" + label(lang, data.answers().get(RatingMenu.Q_CONFUSING)),
                "§7Return: §f" + label(lang, data.answers().get(RatingMenu.Q_RETURN))
        ));
        inventory.setItem(40, GuiUtil.named(Material.ARROW, "§eBack to board"));
        admin.openInventory(inventory);
    }

    public static void handleRoot(AetherionBeta plugin, Player admin, int slot, ItemStack item) {
        if (item == null) {
            return;
        }
        if (item.getType() == Material.ENDER_PEARL) {
            openRoot(plugin, admin);
            return;
        }
        if (item.getType() != Material.PLAYER_HEAD || !(item.getItemMeta() instanceof SkullMeta skull)) {
            return;
        }
        OfflinePlayer offline = skull.getOwningPlayer();
        if (offline == null || offline.getUniqueId() == null) {
            return;
        }
        openPlayer(plugin, admin, offline.getUniqueId());
    }

    public static void handlePlayer(AetherionBeta plugin, Player admin, int slot) {
        if (slot == 40) {
            openRoot(plugin, admin);
        }
    }

    private static ItemStack head(BetaPlayerData data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skull) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(data.uuid());
            skull.setOwningPlayer(offline);
            skull.setDisplayName("§f" + safeName(data));
            List<String> lore = new ArrayList<>();
            lore.add("§7Checklist §f" + data.doneCount() + "§7/§f" + data.totalMilestones());
            lore.add("§7Played §f" + formatDuration(data.playedMs()));
            lore.add(data.submitted() ? "§aFeedback in" : "§8No feedback yet");
            if (data.lang() != null) {
                lore.add("§7Lang §f" + data.lang().name());
            }
            skull.setLore(lore);
            item.setItemMeta(skull);
        }
        return item;
    }

    private static String safeName(BetaPlayerData data) {
        if (data.name() != null && !data.name().isBlank()) {
            return data.name();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(data.uuid());
        return offline.getName() != null ? offline.getName() : data.uuid().toString().substring(0, 8);
    }

    private static String formatDuration(long ms) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(Math.max(0L, ms));
        if (minutes < 60) {
            return minutes + "m";
        }
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String label(BetaLang lang, String key) {
        if (key == null || key.isBlank()) {
            return "—";
        }
        return Texts.choice(lang, key).replace("§a", "").replace("§e", "").replace("§c", "").replace("§f", "").replace("§7", "");
    }

    public static final class RootHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static final class PlayerHolder implements InventoryHolder {
        private final UUID target;

        public PlayerHolder(UUID target) {
            this.target = target;
        }

        public UUID target() {
            return target;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
