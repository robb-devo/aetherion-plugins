package de.aetherion.items.dungeon;

import de.aetherion.items.item.CustomItem;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.util.GuiItems;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DungeonIdentifyMenu implements Listener {

    public static final String TITLE = "§8Identify Relic";

    private static final int[] WEAPON_SLOTS = {11, 12, 13, 14, 15};
    private static final int[] ARMOR_SLOTS = {11, 13, 15};
    private static final int CANCEL_SLOT = 22;

    private final ItemManager items;
    private final CustomItem customItem;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public DungeonIdentifyMenu(ItemManager items, CustomItem customItem) {
        this.items = items;
        this.customItem = customItem;
    }

    public void open(Player player, String relicId, EquipmentSlot hand) {
        if (player == null || !DungeonRelic.isUnidentified(relicId)) {
            return;
        }
        DungeonGearTier tier = DungeonRelic.tier(relicId);
        if (tier == null || tier == DungeonGearTier.T1) {
            return;
        }
        boolean weapon = DungeonRelic.isWeaponRelic(relicId);
        DungeonPiece piece = weapon ? null : DungeonRelic.piece(relicId);
        if (!weapon && piece == null) {
            return;
        }
        openMenu(player, new Session(tier, piece, weapon, false, hand == null ? EquipmentSlot.HAND : hand, relicId));
    }

    public void openSchematic(Player player, EquipmentSlot hand, DungeonGearTier tier) {
        if (player == null) {
            return;
        }
        DungeonGearTier safe = tier == null ? DungeonGearTier.T1 : tier;
        openMenu(player, new Session(
                safe,
                null,
                true,
                true,
                hand == null ? EquipmentSlot.HAND : hand,
                DungeonWeaponKind.SCHEMATIC_ID
        ));
    }

    private void openMenu(Player player, Session session) {
        sessions.put(player.getUniqueId(), session);
        Inventory inventory = Bukkit.createInventory(new Holder(), 27, session.schematic ? "§8Dungeon Schematic" : TITLE);
        ItemStack pane = GuiItems.named(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
        if (session.schematic) {
            inventory.setItem(4, GuiItems.named(
                    org.bukkit.Material.PAPER,
                    "§5Dungeon Weapon Schematic",
                    "§7Pick the weapon that matches a vestige class.",
                    "§8Tank · Assassin · Soldier · Healer · Shaman",
                    "§8Floor " + session.tier.roman() + ". No farm tools."
            ));
        } else {
            inventory.setItem(4, GuiItems.named(
                    session.tier.relicIcon(),
                    DungeonRelic.displayName(session.tier, session.piece),
                    "§7Pick one. The rest of the identities burn.",
                    "§8Overworld boss relic. Dungeons keep Floor I."
            ));
        }
        if (session.weapon) {
            DungeonCalling[] callings = DungeonCalling.values();
            DungeonWeaponKind[] kinds = session.schematic ? null : DungeonWeaponKind.values();
            if (session.schematic) {
                for (int i = 0; i < callings.length && i < WEAPON_SLOTS.length; i++) {
                    inventory.setItem(WEAPON_SLOTS[i], weaponIcon(DungeonWeaponKind.forCalling(callings[i]), session.tier, callings[i]));
                }
            } else {
                for (int i = 0; i < kinds.length && i < WEAPON_SLOTS.length; i++) {
                    inventory.setItem(WEAPON_SLOTS[i], weaponIcon(kinds[i], session.tier, kinds[i].calling()));
                }
            }
        } else {
            DungeonCalling[] callings = session.tier.armorChoices();
            for (int i = 0; i < callings.length && i < ARMOR_SLOTS.length; i++) {
                inventory.setItem(ARMOR_SLOTS[i], armorIcon(callings[i], session.piece, session.tier));
            }
        }
        inventory.setItem(CANCEL_SLOT, GuiItems.named(
                org.bukkit.Material.BARRIER,
                "§cKeep it blank",
                "§7Walk away. The relic stays unidentified.",
                "§8You can decide later. The dungeon can wait."
        ));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CANCEL_SLOT) {
            player.closeInventory();
            return;
        }
        int index = choiceIndex(slot, sessions.get(player.getUniqueId()));
        if (index < 0) {
            return;
        }
        identify(player, index);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            sessions.remove(event.getPlayer().getUniqueId());
        }
    }

    private void identify(Player player, int index) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        ItemStack identified;
        String chosen;
        if (session.weapon) {
            DungeonWeaponKind kind;
            if (session.schematic) {
                DungeonCalling[] callings = DungeonCalling.values();
                if (index >= callings.length) {
                    return;
                }
                kind = DungeonWeaponKind.forCalling(callings[index]);
            } else {
                DungeonWeaponKind[] kinds = DungeonWeaponKind.values();
                if (index >= kinds.length) {
                    return;
                }
                kind = kinds[index];
            }
            identified = customItem.createDungeonWeapon(kind, session.tier, session.schematic);
            chosen = kind.displayName(session.tier);
        } else {
            DungeonCalling[] callings = session.tier.armorChoices();
            if (index >= callings.length) {
                return;
            }
            DungeonCalling calling = callings[index];
            identified = customItem.createDungeonArmor(calling, session.piece, session.tier);
            chosen = calling.color() + calling.display();
        }
        if (!DungeonArmor.consumeAndReplace(
                player,
                session.hand,
                session.piece == null ? DungeonPiece.CHESTPLATE : session.piece,
                item -> matches(item, session.relicId),
                identified
        )) {
            player.sendMessage(session.schematic
                    ? "§7You misplaced the schematic. The dungeon notices."
                    : "§7You misplaced the relic. The dungeon notices.");
            player.closeInventory();
            return;
        }
        sessions.remove(player.getUniqueId());
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.05f);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.7f, 0.9f);
        player.sendMessage(chosen + (session.schematic
                ? " §7chosen. Same class as the vestige armor."
                : " §7chosen. Core it before the dungeon cares."));
    }

    private boolean matches(ItemStack item, String relicId) {
        String id = items.getItemId(item);
        return relicId != null && relicId.equalsIgnoreCase(id);
    }

    private static int choiceIndex(int slot, Session session) {
        int[] slots = session != null && session.weapon() ? WEAPON_SLOTS : ARMOR_SLOTS;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static ItemStack armorIcon(DungeonCalling calling, DungeonPiece piece, DungeonGearTier tier) {
        return GuiItems.named(
                calling.icon(),
                calling.color() + calling.display() + " §8· " + tier.roman(),
                "§7" + calling.specialty(),
                "",
                "§8Becomes: " + calling.setName() + " " + piece.display() + " " + tier.roman(),
                "§8Boosters: " + calling.boosters(),
                "§8Boss piece until a matching core.",
                "",
                "§eClick to identify this piece."
        );
    }

    private static ItemStack weaponIcon(DungeonWeaponKind kind, DungeonGearTier tier, DungeonCalling calling) {
        DungeonCalling shown = calling == null ? kind.calling() : calling;
        return GuiItems.named(
                kind.material(tier),
                kind.displayName(tier),
                shown.color() + shown.display() + " §8· §7" + shown.setName(),
                "§7" + kind.flavor(),
                "",
                "§8Becomes the " + shown.display() + " class weapon.",
                "",
                "§eClick to identify."
        );
    }

    private record Session(
            DungeonGearTier tier,
            DungeonPiece piece,
            boolean weapon,
            boolean schematic,
            EquipmentSlot hand,
            String relicId
    ) {
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
