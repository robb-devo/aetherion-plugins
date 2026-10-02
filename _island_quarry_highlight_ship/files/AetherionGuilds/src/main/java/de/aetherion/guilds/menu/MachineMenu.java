package de.aetherion.guilds.menu;

import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.logistics.Res;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.util.AetherionItemsAccess;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mill / Forge: recipe, speed, where its belts go, and its buffers (collectable). */
public final class MachineMenu implements MenuKit.Menu {

    private static final int INPUT = 11;
    private static final int INFO = 13;
    private static final int OUTPUT = 15;
    private static final int BACK = 18;
    private static final int DECONSTRUCT = 20;
    private static final int CLOSE = 22;

    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private BuildMenu build;

    public MachineMenu(HostService hosts, StructureService structures, LogisticsService logistics) {
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
    }

    public void attach(BuildMenu build) {
        this.build = build;
    }

    public void open(Player player, PlacedStructure structure) {
        if (structure == null) {
            return;
        }
        if (!hosts.isMember(player, structure.host()) && !hosts.isAdminBypass(player)) {
            player.sendMessage("§cThat's not your island.");
            return;
        }
        Inventory inventory = MenuKit.create(this, structure.id(), 27, "§8" + structure.type().display());
        List<String> info = new ArrayList<>(structure.type().blurb());
        info.add("");
        info.add("§7Speed: §f" + GuildFormat.compact((long) structure.type().ratePerSecond()) + " §7raw-eq / s");
        info.add("§7Out: " + logistics.summary(structure));
        int feeding = logistics.feeding(structure);
        info.add("§7In: " + (feeding > 0 ? "§a" + feeding + " belt" + (feeding == 1 ? "" : "s") : "§8no belts yet"));
        if (!AetherionItemsAccess.available()) {
            info.add("§cAetherionItems is off: it passes items through.");
        }
        long idle = System.currentTimeMillis() - structure.lastWork();
        info.add(idle < 5000L ? "§a● working" : "§8○ idle");
        inventory.setItem(INFO, MenuKit.named(structure.type().icon(), "§6" + structure.type().display(), info));
        inventory.setItem(INPUT, MenuKit.named(Material.HOPPER, "§eWaiting to be processed",
                bufferLore(structure.input(), "§8Leftovers under 128 wait for more.")));
        inventory.setItem(OUTPUT, MenuKit.named(Material.CHEST, "§eReady to leave",
                bufferLore(structure.output(), "§8Goes onto the belt from the chute.")));
        inventory.setItem(DECONSTRUCT, MenuKit.named(Material.IRON_AXE, "§cTake it down",
                "§7Empty it first. Half the coins and",
                "§7the Quarry Mill/Forge item come back.",
                "§eShift-click §7to confirm"));
        inventory.setItem(BACK, MenuKit.named(Material.ARROW, "§eBlueprints"));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    private static List<String> bufferLore(Map<Res, Long> buffer, String note) {
        List<String> lore = new ArrayList<>();
        if (buffer.isEmpty()) {
            lore.add("§8empty");
        }
        int shown = 0;
        for (Map.Entry<Res, Long> entry : buffer.entrySet()) {
            if (shown++ >= 10) {
                lore.add("§8…");
                break;
            }
            lore.add(entry.getKey().color() + entry.getKey().display() + " §f" + String.format("%,d", entry.getValue()));
        }
        lore.add("");
        lore.add(note);
        lore.add("§eClick §7to take it out");
        return lore;
    }

    @Override
    public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        PlacedStructure structure = structures.get((UUID) holder.context());
        if (structure == null) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case CLOSE -> player.closeInventory();
            case BACK -> {
                if (build != null) {
                    build.open(player, structure.host());
                }
            }
            case INPUT, OUTPUT -> {
                if (!hosts.canCollect(player, structure.host())) {
                    player.sendMessage(hosts.rankHint(structure.host(), "take from machines"));
                    return;
                }
                Map<Res, Long> buffer = slot == INPUT ? structure.input() : structure.output();
                long total = 0L;
                for (Res res : new ArrayList<>(buffer.keySet())) {
                    total += StorageMenu.give(player, buffer, res, Long.MAX_VALUE);
                }
                if (total > 0L) {
                    structures.markDirty();
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.1f);
                }
                open(player, structure);
            }
            case DECONSTRUCT -> {
                if (click.isShiftClick()) {
                    player.closeInventory();
                    structures.deconstruct(player, structure);
                } else {
                    player.sendMessage("§7Shift-click to take the " + structure.type().display() + " down.");
                }
            }
            default -> {
            }
        }
    }
}
