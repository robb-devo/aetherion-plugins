package de.aetherion.hub.item;

import de.aetherion.hub.AetherionHub;
import de.aetherion.hub.model.HubSpawn;
import de.aetherion.hub.service.HubService;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HomesteadMarker {

    public static final String LURKER_CAMP = "mines";
    public static final String DEFAULT_SPAWN = "harbour";

    private final AetherionHub plugin;
    private final HubService hub;
    private final NamespacedKey key;
    private final NamespacedKey anchorKey;
    private final Map<UUID, BukkitTask> blinkTasks = new ConcurrentHashMap<>();

    public HomesteadMarker(AetherionHub plugin, HubService hub) {
        this.plugin = plugin;
        this.hub = hub;
        this.key = new NamespacedKey(plugin, "unlock_spawn");
        this.anchorKey = new NamespacedKey(plugin, "spawn_anchor");
    }

    public NamespacedKey key() {
        return key;
    }

    public ItemStack create(String spawnId) {
        return create(spawnId, false);
    }

    public ItemStack createAnchor(String spawnId) {
        return create(spawnId, true);
    }

    private ItemStack create(String spawnId, boolean anchor) {
        HubSpawn spawn = hub.ensureId(spawnId);
        String name = spawn == null ? spawnId : spawn.displayName();
        String id = spawn == null ? spawnId : spawn.id();

        ItemStack item = new ItemStack(anchor ? Material.LODESTONE : Material.CAMPFIRE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean amethyst = "amethyst".equalsIgnoreCase(id);
            meta.setDisplayName(anchor
                    ? (amethyst ? "§cDEV §dAmethyst Mines §8Spawn Anchor" : "§cDEV §6" + name + " §8Anchor")
                    : "§6" + name + " §8Marker");
            meta.setLore(anchor
                    ? (amethyst
                    ? List.of(
                            "§7Stand where players should appear",
                            "§7in the Amethyst Area (§faether_veins§7).",
                            "§eRight-click §7to save §f/amethyst§7.",
                            "§7Crystal Guide uses the same point.",
                            "",
                            "§8Sneak-click keeps this item."
                    )
                    : List.of(
                            "§7Right-click to save this",
                            "§7teleport for §f" + name + "§7.",
                            "",
                            "§8Players unlock it via quests.",
                            "§eDoes not consume if sneak-held."
                    ))
                    : List.of(
                            "§7Right-click to unlock",
                            "§f" + name + "§7 as a spawn.",
                            id.equals("mines") ? "§7Mine approach — deep tunnels beyond." : "§7Travel there via /spawns.",
                            "",
                            "§eThen open the Aetherion Manager",
                            "§eto set it as your spawn."
                    ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            if (anchor) {
                meta.getPersistentDataContainer().set(anchorKey, PersistentDataType.BYTE, (byte) 1);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isMarker(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    public String spawnId(ItemStack item) {
        if (!isMarker(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public boolean isAnchor(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(anchorKey, PersistentDataType.BYTE);
    }

    public boolean use(Player player, ItemStack item) {
        String spawnId = spawnId(item);
        if (spawnId == null || spawnId.isBlank()) {
            return false;
        }

        if (isAnchor(item)) {
            if (hub.ensureId(spawnId) == null) {
                player.sendMessage("§cThat spawn was retired. Origin map only.");
                return false;
            }
            HubSpawn spawn = hub.setLocation(spawnId, player.getLocation(), true);
            if (spawn == null) {
                player.sendMessage("§cCould not save that spawn.");
                return false;
            }
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.35f);
            player.sendMessage("§aSaved teleport for §f" + spawn.displayName() + "§a.");
            if ("amethyst".equalsIgnoreCase(spawn.id())) {
                player.sendMessage("§7Players use §f/amethyst §7or the Crystal Guide to arrive here.");
            } else {
                player.sendMessage("§7Boss quests unlock this camp in Manager → Spawns.");
            }
            return !player.isSneaking();
        }

        HubSpawn spawn = hub.ensureId(spawnId);
        if (spawn == null) {
            player.sendMessage("§cCould not create that spawn.");
            return false;
        }

        if (hub.isUnlocked(player, spawn.id()) && spawn.hasLocation()) {
            player.sendMessage("§eYou already unlocked §f" + spawn.displayName() + "§e.");
            return false;
        }

        if (!spawn.hasLocation()) {
            hub.setLocation(spawn.id(), player.getLocation(), false);
            player.sendMessage("§7No camp location was set yet — planted it here.");
        }

        hub.unlock(player.getUniqueId(), spawn.id());
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        player.sendMessage("§aYou unlocked §f" + spawn.displayName() + "§a!");
        player.sendMessage("§7Open the Aetherion Manager → Spawns to make it your /spawn.");
        return true;
    }

    /**
     * Pulse-enchant unlock markers in the player's inventory (Miss-Ledger-style tip).
     */
    public void blinkUnlockItem(Player player, int seconds) {
        if (player == null) {
            return;
        }
        stopBlink(player);
        final boolean[] bright = {true};
        final int[] ticksLeft = {Math.max(2, seconds) * 20};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || ticksLeft[0] <= 0) {
                clearBlinkVisual(player);
                stopBlink(player);
                return;
            }
            ticksLeft[0] -= 8;
            bright[0] = !bright[0];
            applyBlinkVisual(player, bright[0]);
            if (bright[0]) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.25f, 1.6f);
            }
        }, 0L, 8L);
        blinkTasks.put(player.getUniqueId(), task);
    }

    public void stopBlink(Player player) {
        if (player == null) {
            return;
        }
        BukkitTask task = blinkTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void applyBlinkVisual(Player player, boolean bright) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (!isMarker(stack) || isAnchor(stack)) {
                continue;
            }
            ItemStack copy = stack.clone();
            ItemMeta meta = copy.getItemMeta();
            if (meta == null) {
                continue;
            }
            String base = meta.getDisplayName() == null ? "§6Marker" : meta.getDisplayName()
                    .replace("§e§l★ ", "")
                    .replace(" ★", "");
            if (bright) {
                meta.setDisplayName("§e§l★ " + base + " ★");
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            } else {
                meta.setDisplayName(base);
                meta.removeEnchant(Enchantment.UNBREAKING);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            copy.setItemMeta(meta);
            player.getInventory().setItem(slot, copy);
        }
    }

    private void clearBlinkVisual(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (!isMarker(stack) || isAnchor(stack)) {
                continue;
            }
            ItemStack copy = stack.clone();
            ItemMeta meta = copy.getItemMeta();
            if (meta == null) {
                continue;
            }
            String name = meta.getDisplayName();
            if (name != null) {
                meta.setDisplayName(name.replace("§e§l★ ", "").replace(" ★", ""));
            }
            meta.removeEnchant(Enchantment.UNBREAKING);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            copy.setItemMeta(meta);
            player.getInventory().setItem(slot, copy);
        }
    }
}
