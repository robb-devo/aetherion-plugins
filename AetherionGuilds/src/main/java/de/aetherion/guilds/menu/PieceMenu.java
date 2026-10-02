package de.aetherion.guilds.menu;

import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.logistics.MachineState;
import de.aetherion.guilds.logistics.Res;
import de.aetherion.guilds.model.QuarryMinion;
import de.aetherion.guilds.model.QuarryType;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureType;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One belt piece (Splitter / Overflow Gate / Sorter): its rule in one line, where each exit goes, what's inside,
 * and for a Sorter the good it picks (one click on a tile).
 */
public final class PieceMenu implements MenuKit.Menu {

    private static final int HEADER = 4;
    private static final int EXITS = 20;
    private static final int INSIDE = 24;
    private static final int[] CHOICES = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int BACK = 36;
    private static final int DECONSTRUCT = 38;
    private static final int CLOSE = 40;
    private static final int SORTER_EXITS = 11;
    private static final int SORTER_INSIDE = 15;

    private record Context(UUID structure, List<String> choices) {
    }

    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private ProductionMenu production;

    public PieceMenu(HostService hosts, StructureService structures, LogisticsService logistics) {
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
    }

    public void attachProduction(ProductionMenu production) {
        this.production = production;
    }

    public void open(Player player, PlacedStructure piece) {
        if (piece == null || !piece.type().router()) {
            return;
        }
        if (!hosts.isMember(player, piece.host()) && !hosts.isAdminBypass(player)) {
            player.sendMessage("§cThat's not your island.");
            return;
        }
        boolean sorter = piece.type() == StructureType.SORTER;
        List<String> choices = new ArrayList<>();
        Inventory inventory = MenuKit.framed(this, new Context(piece.id(), choices), 45, "§8" + piece.label());
        MachineState state = logistics.status(piece);
        List<String> head = new ArrayList<>(piece.type().blurb());
        head.add("");
        head.add(state.tag());
        if (!state.hint().isEmpty() && state != MachineState.RUNNING) {
            head.add("§8" + state.hint());
        }
        inventory.setItem(HEADER, MenuKit.glow(MenuKit.named(piece.type().icon(), "§6§l" + piece.label(), head)));
        inventory.setItem(sorter ? SORTER_EXITS : EXITS, exitsTile(piece));
        inventory.setItem(sorter ? SORTER_INSIDE : INSIDE, insideTile(piece));
        if (sorter) {
            for (Res.Form form : Res.Form.values()) {
                choices.add("form:" + form.name());
            }
            Set<QuarryType> types = new LinkedHashSet<>();
            for (QuarryMinion minion : hosts.minions(piece.host())) {
                types.add(minion.quarryType());
            }
            for (Res res : piece.input().keySet()) {
                types.add(res.type());
            }
            if (piece.filter() != null && piece.filter().startsWith("type:")) {
                try {
                    types.add(QuarryType.valueOf(piece.filter().substring(5)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            types.add(QuarryType.COBBLESTONE);
            for (QuarryType type : types) {
                choices.add("type:" + type.name());
            }
            for (int i = 0; i < choices.size() && i < CHOICES.length; i++) {
                String choice = choices.get(i);
                boolean picked = choice.equals(piece.filter());
                List<String> lore = new ArrayList<>();
                lore.add(picked ? "§a✔ Goes out the front now" : "§7Send this out the §ffront§7,");
                if (!picked) {
                    lore.add("§7everything else out the §fsides§7.");
                    lore.add("");
                    lore.add("§e▶ Pick");
                }
                ItemStack icon = MenuKit.decorate(LogisticsService.filterIcon(choice),
                        (picked ? "§a" : "§f") + capital(LogisticsService.filterName(choice)), lore);
                inventory.setItem(CHOICES[i], picked ? MenuKit.glow(icon) : icon);
            }
        }
        inventory.setItem(DECONSTRUCT, MenuKit.named(Material.IRON_AXE, "§cTake it down",
                "§7Only when empty. Half the coins back.", "§7The belts around it stay.", "§eShift-click §7to confirm"));
        inventory.setItem(BACK, MenuKit.named(Material.ARROW, "§eProduction"));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    private ItemStack exitsTile(PlacedStructure piece) {
        List<String> lore = new ArrayList<>();
        BlockFace front = LogisticsService.frontOf(piece);
        List<LogisticsService.Route> exits = logistics.exits(piece);
        if (exits.isEmpty()) {
            lore.add("§8No belt leads away from it yet.");
            lore.add("§7Lay one §faway §7from any side");
            lore.add("§7(the orange arrow marks the front).");
        }
        for (LogisticsService.Route route : exits) {
            String side = side(front, route.face());
            String to = route.to() == null ? "§c✖ leads nowhere" : "§f" + logistics.label(route.to());
            lore.add((route.front() ? "§6" : "§7") + side + " §8→ " + to);
        }
        lore.add("");
        lore.add(switch (piece.type()) {
            case OVERFLOW -> "§8The front gets all it can take first.";
            case SORTER -> "§8Picked good: front. Everything else: sides.";
            default -> "§8Each exit gets an equal share.";
        });
        lore.add("§8Its front faces §7" + pretty(front) + "§8.");
        return MenuKit.named(Material.IRON_TRAPDOOR, "§eExits", lore);
    }

    private ItemStack insideTile(PlacedStructure piece) {
        List<String> lore = new ArrayList<>();
        if (piece.input().isEmpty()) {
            lore.add("§8empty, goods pass straight through");
        }
        int shown = 0;
        for (Map.Entry<Res, Long> entry : piece.input().entrySet()) {
            if (shown++ >= 8) {
                lore.add("§8…");
                break;
            }
            lore.add(entry.getKey().color() + entry.getKey().display() + " §f" + String.format("%,d", entry.getValue()));
        }
        lore.add("");
        lore.add("§8Goods wait here only while");
        lore.add("§8every exit they may take is full.");
        if (!piece.input().isEmpty()) {
            lore.add("§eClick §7to take it out");
        }
        return MenuKit.named(Material.HOPPER, "§eInside it", lore);
    }

    private static String side(BlockFace front, BlockFace face) {
        if (face == null || face == front) {
            return "Front";
        }
        if (face == front.getOppositeFace()) {
            return "Back";
        }
        BlockFace left = switch (front) {
            case SOUTH -> BlockFace.EAST;
            case NORTH -> BlockFace.WEST;
            case EAST -> BlockFace.NORTH;
            default -> BlockFace.SOUTH;
        };
        return face == left ? "Left" : "Right";
    }

    private static String pretty(BlockFace face) {
        return face.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String capital(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    @Override
    public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        Context context = (Context) holder.context();
        PlacedStructure piece = structures.get(context.structure());
        if (piece == null) {
            player.closeInventory();
            return;
        }
        boolean sorter = piece.type() == StructureType.SORTER;
        if (slot == CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == BACK) {
            if (production != null) {
                production.open(player, piece.host());
            } else {
                player.closeInventory();
            }
            return;
        }
        if (slot == DECONSTRUCT) {
            if (click.isShiftClick()) {
                player.closeInventory();
                structures.deconstruct(player, piece);
            } else {
                player.sendMessage("§7Shift-click to take the " + piece.type().display() + " down.");
            }
            return;
        }
        if (slot == (sorter ? SORTER_INSIDE : INSIDE)) {
            if (!hosts.canCollect(player, piece.host())) {
                player.sendMessage(hosts.rankHint(piece.host(), "take from belt pieces"));
                return;
            }
            long total = 0L;
            for (Res res : new ArrayList<>(piece.input().keySet())) {
                total += StorageMenu.give(player, piece.input(), res, Long.MAX_VALUE);
            }
            if (total > 0L) {
                structures.markDirty();
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.1f);
            }
            open(player, piece);
            return;
        }
        if (!sorter) {
            return;
        }
        for (int i = 0; i < CHOICES.length && i < context.choices().size(); i++) {
            if (CHOICES[i] != slot) {
                continue;
            }
            if (!hosts.canPlace(player, piece.host())) {
                player.sendMessage(hosts.rankHint(piece.host(), "set sorters"));
                return;
            }
            String choice = context.choices().get(i);
            piece.setFilter(choice);
            structures.markDirty();
            logistics.markDirty(piece.host());
            player.playSound(player.getLocation(), Sound.BLOCK_COPPER_GRATE_HIT, 0.9f, 1.3f);
            player.sendMessage("§aSorter set: §f" + capital(LogisticsService.filterName(choice))
                    + " §7goes out the front, everything else out the sides.");
            open(player, piece);
            return;
        }
    }
}
