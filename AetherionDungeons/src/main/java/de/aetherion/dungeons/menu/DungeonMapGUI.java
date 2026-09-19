package de.aetherion.dungeons.menu;

import de.aetherion.dungeons.instance.DungeonLayout;
import de.aetherion.dungeons.instance.DungeonSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class DungeonMapGUI {

    public static final String TITLE = "§5Dungeon Map";
    public static final int CLOSE_SLOT = 49;

    private DungeonMapGUI() {
    }

    public static void open(Player player, DungeonSession session) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 54, TITLE);
        ItemStack pane = named(Material.BLACK_STAINED_GLASS_PANE, " ", false);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }

        DungeonLayout layout = session.layout();
        String here = layout == null
                ? "lobby"
                : layout.roomId(player.getLocation().getX(), player.getLocation().getZ());
        int remaining = session.closeSecondsLeft();

        inventory.setItem(4, named(
                remaining > 0 ? Material.CLOCK : Material.FILLED_MAP,
                remaining > 0 ? "§cInstance closing" : "§5Floor 1",
                false,
                remaining > 0
                        ? new String[]{"§7The portal stays open for §f" + remaining + "s§7.", "§7Loot, then leave."}
                        : statusLore(session)
        ));

        putRoom(inventory, 13, Material.DEEPSLATE_BRICKS, "§7Lobby", here.equals("lobby"),
                "§7Gate Warden. Ready check.",
                session.started() ? "§aGate is open." : "§eGate is sealed.");

        if (layout != null) {
            int[] slots = {20, 21, 22, 23, 24};
            for (DungeonLayout.CombatRoom room : layout.combatRooms()) {
                if (room.index() >= slots.length) {
                    break;
                }
                boolean hereRoom = here.equals("combat_" + room.index());
                boolean cleared = session.isRoomCleared(room.index());
                boolean unlocked = session.isUnlocked(room.index()) && session.started();
                Material icon = room.lootRoom() ? Material.CHEST : (cleared ? Material.MOSSY_STONE_BRICKS : Material.ROTTEN_FLESH);
                String state = !session.started()
                        ? "§8Quiet until ready."
                        : (cleared ? "§aCleared." : (unlocked ? "§cHostiles inside." : "§8Locked."));
                putRoom(inventory, slots[room.index()], icon, "§8" + room.title(), hereRoom,
                        "§7" + DungeonLayout.shapeLabel(room.shape()),
                        room.lootRoom() ? "§6Loot cache in this room." : "§7Combat chamber.",
                        state);
            }
        }

        putRoom(inventory, 31, Material.WITHER_SKELETON_SKULL, "§5Boss Chamber", here.equals("boss"),
                "§7Prototype Sentinel.",
                session.bossDead() ? "§aFallen." : (session.bossReleased() ? "§cAlive — fight here." : "§8Locked until every chamber is clear."));

        putRoom(inventory, 40, Material.OBSIDIAN, "§5Exit Portal", here.equals("exit"),
                "§7Walk into the nether portal",
                "§7to return to the overworld",
                "§7and delete this instance.");

        inventory.setItem(CLOSE_SLOT, named(Material.BARRIER, "§cClose", false, "§7Close the map."));
        player.openInventory(inventory);
    }

    private static String[] statusLore(DungeonSession session) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Your current floor. You are");
        lore.add("§7the glowing room.");
        lore.add("");
        if (!session.started()) {
            lore.add("§eTalk to the Gate Warden.");
        } else if (!session.allCombatCleared()) {
            lore.add("§cClear §f" + session.clearedCount() + "/" + (session.layout() == null ? "?" : session.layout().combatCount()) + " §cchambers.");
        } else if (!session.bossDead()) {
            lore.add("§5Kill the Prototype Sentinel.");
        } else {
            lore.add("§aBoss down. Use the portal.");
        }
        DungeonLayout layout = session.layout();
        if (layout != null) {
            lore.add("");
            lore.add("§8" + layout.combatCount() + " chambers • seed " + Integer.toHexString(layout.seed()));
        }
        return lore.toArray(String[]::new);
    }

    private static void putRoom(Inventory inventory, int slot, Material material, String name, boolean here, String... lore) {
        List<String> lines = new ArrayList<>();
        if (here) {
            lines.add("§aYou are here.");
            lines.add("");
        }
        for (String line : lore) {
            lines.add(line);
        }
        inventory.setItem(slot, named(material, name, here, lines.toArray(String[]::new)));
    }

    private static ItemStack named(Material material, String name, boolean glow, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
