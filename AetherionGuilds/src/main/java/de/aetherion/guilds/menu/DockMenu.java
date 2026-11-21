package de.aetherion.guilds.menu;

import de.aetherion.core.api.ChromePage;
import de.aetherion.guilds.island.DockService;
import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.island.IslandHost;
import de.aetherion.guilds.island.Stages;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The Dock: the phase contract (the stage gate), the daily order and two regular contracts, each with what it
 * wants, what is loaded and what it pays. Click loads what's missing from storage; a full contract calls the
 * airship. Dock Marks buy cosmetics only (airship paint, harbour decoration) on the second page.
 */
public final class DockMenu implements MenuKit.Menu {

    private static final int HEADER = 4;
    private static final int[] CONTRACTS = {19, 21, 23, 25};
    private static final int SHOP = 39;
    private static final int LOAD_ALL = 41;
    private static final int BACK = 45;
    private static final int CLOSE = 49;

    private final HostService hosts;
    private final StructureService structures;
    private final DockService dock;
    private final Shop shop = new Shop();
    private BiConsumer<Player, IslandHost> openHub = (player, host) -> player.performCommand("island");

    public DockMenu(HostService hosts, StructureService structures, DockService dock) {
        this.hosts = hosts;
        this.structures = structures;
        this.dock = dock;
    }

    public void attachHub(BiConsumer<Player, IslandHost> openHub) {
        if (openHub != null) {
            this.openHub = openHub;
        }
    }

    public void open(Player player, IslandHost host) {
        if (host == null || host.isGuild() || !hosts.exists(host)) {
            return;
        }
        if (!hosts.isMember(player, host) && !hosts.isAdminBypass(player)) {
            player.sendMessage("§cThat's not your Dock.");
            return;
        }
        Inventory inventory = MenuKit.framed(this, host, 54, "§8✦ Dock · " + hosts.title(host));
        int stage = dock.stage(host);
        List<String> head = new ArrayList<>();
        head.add("§6" + Stages.label(stage));
        head.add("§7Ships sent §f" + dock.shipped(host) + " §8· §7Dock Marks §b" + dock.marks(host));
        head.add("");
        head.add("§7Belts that end on the Dock fill its");
        head.add("§7contracts (never your storage).");
        head.add("§7Or click a contract: it loads what's");
        head.add("§7missing straight from storage.");
        head.add("§7A full contract calls the airship.");
        if (dock.flying(host)) {
            head.add("");
            head.add("§b✈ The airship is in right now.");
        }
        inventory.setItem(HEADER, MenuKit.glow(MenuKit.named(Material.SPRUCE_BOAT, "§b§l✦ Dock", head)));
        List<DockService.Contract> contracts = slots(host);
        for (int i = 0; i < CONTRACTS.length; i++) {
            DockService.Contract contract = contracts.get(i);
            inventory.setItem(CONTRACTS[i], contract == null ? empty(host, i) : tile(player, host, contract));
        }
        inventory.setItem(LOAD_ALL, MenuKit.named(Material.HOPPER, "§eLoad every contract",
                "§7Moves what every open contract", "§7still misses from your storage.", "", "§e▶ Click"));
        inventory.setItem(SHOP, MenuKit.named(Material.EMERALD, "§bDock Marks shop",
                "§7You have §b" + dock.marks(host) + " §7Dock Marks.", "", "§7Airship paint and harbour",
                "§7decoration. Cosmetic only.", "", "§e▶ Open"));
        inventory.setItem(BACK, MenuKit.named(Material.ARROW, "§eHub"));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        ChromePage.open(player, inventory, ChromePage.Theme.ISLAND);
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 0.7f, 0.9f);
    }

    /** Fixed slots: phase, daily, regular 1, regular 2 (null where there is none right now). */
    private List<DockService.Contract> slots(IslandHost host) {
        List<DockService.Contract> out = new ArrayList<>();
        out.add(dock.phase(host));
        out.add(dock.daily(host));
        List<DockService.Contract> regular = dock.regular(host);
        out.add(regular.size() > 0 ? regular.get(0) : null);
        out.add(regular.size() > 1 ? regular.get(1) : null);
        return out;
    }

    private ItemStack empty(IslandHost host, int slot) {
        return switch (slot) {
            case 0 -> MenuKit.named(Material.NETHER_STAR, "§6" + Stages.label(dock.stage(host)),
                    "§7Every stage is reached.", "§7The daily order and the", "§7contracts keep coming.");
            case 1 -> MenuKit.named(Material.CLOCK, "§7Daily order", "§7Shipped for today.", "§7A new one tomorrow.");
            default -> MenuKit.named(Material.PAPER, "§7Contract", "§7The broker is writing", "§7a new one. A minute.");
        };
    }

    private ItemStack tile(Player player, IslandHost host, DockService.Contract contract) {
        List<String> lore = new ArrayList<>();
        lore.add("§8" + contract.client());
        if (contract.kind() == DockService.Kind.PHASE) {
            lore.add("§6Ships you to " + Stages.label(dock.stage(host) + 1));
        }
        lore.add("");
        for (DockService.Need need : contract.needs()) {
            long have = Math.min(need.amount(), contract.loaded(need.res()));
            long stock = structures.stock(host, need.res());
            lore.add((have >= need.amount() ? "§a✔ " : "§8• ") + "§f" + GuildFormat.compact(have) + "§8/§f"
                    + GuildFormat.compact(need.amount()) + " " + need.res().color() + need.res().display()
                    + (have < need.amount() ? " §8(" + GuildFormat.compact(stock) + " in storage)" : ""));
        }
        lore.add("§7" + bar(contract.fill()));
        lore.add("");
        lore.add("§7Pays §6" + GuildFormat.compact(contract.coins()) + " coins §8· §b" + contract.marks() + " Dock Marks");
        String extra = extraLine(contract.extra());
        if (extra != null) {
            lore.add("§7and " + extra);
        }
        lore.add("");
        boolean full = contract.full();
        if (full) {
            lore.add(dock.flying(host) ? "§7The airship is out. It takes this next." : "§a▶ Click: call the airship");
        } else {
            lore.add("§e▶ Click: load what's missing from storage");
        }
        Material icon = switch (contract.kind()) {
            case PHASE -> Material.BEACON;
            case DAILY -> Material.CLOCK;
            default -> Material.WRITABLE_BOOK;
        };
        String name = (contract.kind() == DockService.Kind.PHASE ? "§6§l" : contract.kind() == DockService.Kind.DAILY
                ? "§e§l" : "§f") + contract.title() + (full ? " §a✔" : "");
        ItemStack item = MenuKit.named(full ? Material.CHEST_MINECART : icon, name, lore);
        return full || contract.kind() == DockService.Kind.PHASE ? MenuKit.glow(item) : item;
    }

    private static String extraLine(String extra) {
        if (extra == null) {
            return null;
        }
        if (extra.startsWith("quarry:")) {
            try {
                return "§ea " + de.aetherion.guilds.model.QuarryType.valueOf(extra.substring(7)).display();
            } catch (IllegalArgumentException ignored) {
                return "§ea second quarry";
            }
        }
        if (extra.startsWith("core:")) {
            return "§d" + extra.substring(5) + " Quarry Core";
        }
        return null;
    }

    private static String bar(double fill) {
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, fill)) * 20);
        return "§a" + "|".repeat(filled) + "§8" + "|".repeat(20 - filled) + " §7" + Math.round(fill * 100) + "%";
    }

    @Override
    public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        IslandHost host = (IslandHost) holder.context();
        switch (slot) {
            case CLOSE -> player.closeInventory();
            case BACK -> openHub.accept(player, host);
            case SHOP -> shop.open(player, host);
            case LOAD_ALL -> {
                long moved = 0L;
                for (DockService.Contract contract : dock.contracts(host)) {
                    moved += dock.load(player, host, contract);
                }
                player.sendMessage(moved > 0L ? "§a" + GuildFormat.compact(moved) + " goods loaded onto the quay."
                        : "§7Nothing in storage that the contracts still want.");
                open(player, host);
            }
            default -> {
                for (int i = 0; i < CONTRACTS.length; i++) {
                    if (CONTRACTS[i] != slot) {
                        continue;
                    }
                    DockService.Contract contract = slots(host).get(i);
                    if (contract == null) {
                        return;
                    }
                    if (contract.full()) {
                        if (dock.ship(player, host, contract)) {
                            player.closeInventory();
                        }
                        return;
                    }
                    long moved = dock.load(player, host, contract);
                    if (moved <= 0L) {
                        player.sendMessage("§7Nothing in storage that this contract wants yet.");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.0f);
                    }
                    if (contract.full() && !dock.flying(host)) {
                        if (dock.ship(player, host, contract)) {
                            player.closeInventory();
                            return;
                        }
                    }
                    open(player, host);
                    return;
                }
            }
        }
    }

    /** Page two: Dock Marks buy airship paint and decoration. Cosmetic only. */
    private final class Shop implements MenuKit.Menu {

        private static final int PAINTS = 19;
        private static final int DECOR = 29;

        void open(Player player, IslandHost host) {
            Inventory inventory = MenuKit.framed(this, host, 54, "§8✦ Dock Marks · " + dock.marks(host));
            inventory.setItem(HEADER, MenuKit.glow(MenuKit.named(Material.EMERALD, "§b§lDock Marks §f" + dock.marks(host),
                    "§7Earned with every ship.", "§7Spent only on looks: nothing", "§7here makes you stronger.")));
            String current = dock.paint(host);
            for (int i = 0; i < DockService.PAINTS.size() && i < 7; i++) {
                String[] paint = DockService.PAINTS.get(i);
                Material wool = Material.matchMaterial(paint[1]);
                boolean owned = dock.ownsPaint(host, paint[0]);
                boolean picked = paint[0].equals(current);
                List<String> lore = new ArrayList<>();
                lore.add("§7Airship paint");
                lore.add("");
                lore.add(picked ? "§a✔ On your airship" : owned ? "§e▶ Click to paint" : "§7Costs §b" + DockService.PAINT_PRICE
                        + " Dock Marks §8· §e▶ Click to buy");
                ItemStack item = MenuKit.named(wool == null ? Material.WHITE_WOOL : wool, (picked ? "§a" : "§f") + paint[2], lore);
                inventory.setItem(PAINTS + i, picked ? MenuKit.glow(item) : item);
            }
            for (int i = 0; i < DockService.DECOR.size() && i < 7; i++) {
                DockService.Decor decor = DockService.DECOR.get(i);
                ItemStack icon = new ItemStack(decor.material());
                inventory.setItem(DECOR + i, MenuKit.decorate(icon, "§f" + decor.label() + " §8×" + decor.amount(),
                        List.of("§7Decoration for your island.", "", "§7Costs §b" + decor.price() + " Dock Marks",
                                "§e▶ Click to buy")));
            }
            inventory.setItem(BACK, MenuKit.named(Material.ARROW, "§eDock"));
            inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
            ChromePage.open(player, inventory, ChromePage.Theme.ISLAND);
        }

        @Override
        public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
            IslandHost host = (IslandHost) holder.context();
            if (slot == CLOSE) {
                player.closeInventory();
                return;
            }
            if (slot == BACK) {
                DockMenu.this.open(player, host);
                return;
            }
            if (!hosts.canPlace(player, host)) {
                player.sendMessage(hosts.rankHint(host, "spend Dock Marks"));
                return;
            }
            if (slot >= PAINTS && slot < PAINTS + DockService.PAINTS.size()) {
                dock.paint(player, host, DockService.PAINTS.get(slot - PAINTS)[0]);
                open(player, host);
                return;
            }
            if (slot >= DECOR && slot < DECOR + DockService.DECOR.size()) {
                dock.buy(player, host, DockService.DECOR.get(slot - DECOR));
                open(player, host);
            }
        }
    }
}
