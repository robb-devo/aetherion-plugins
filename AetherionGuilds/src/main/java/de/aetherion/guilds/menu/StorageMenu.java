package de.aetherion.guilds.menu;

import de.aetherion.core.api.ChromePage;
import de.aetherion.guilds.island.HostService;
import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.logistics.Res;
import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A Storage Hut or Depot: what the belts delivered, withdrawable by stack or all at once, and goods from your
 * inventory go in with a click (one stack, shift: all of that good, or the button: everything that fits).
 */
public final class StorageMenu implements MenuKit.Menu, MenuKit.PackClick {

    private static final int BACK = 45;
    private static final int UPGRADE = 47;
    private static final int TAKE_ALL = 48;
    private static final int INFO = 49;
    private static final int PUT_ALL = 50;
    private static final int DECONSTRUCT = 51;
    private static final int CLOSE = 53;

    private record Context(UUID structureId, List<Res> slots) {
    }

    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private BuildMenu build;
    private ProductionMenu production;

    public StorageMenu(HostService hosts, StructureService structures, LogisticsService logistics) {
        this.hosts = hosts;
        this.structures = structures;
        this.logistics = logistics;
    }

    public void attach(BuildMenu build) {
        this.build = build;
    }

    public void attachProduction(ProductionMenu production) {
        this.production = production;
    }

    public void open(Player player, PlacedStructure structure) {
        if (structure == null) {
            return;
        }
        if (!hosts.canCollect(player, structure.host()) && !hosts.isAdminBypass(player)) {
            player.sendMessage(hosts.rankHint(structure.host(), "open this storage"));
            return;
        }
        List<Res> order = new ArrayList<>(structure.store().keySet());
        order.sort(Comparator.comparing((Res r) -> r.type().ordinal()).thenComparing(r -> r.form().ordinal()));
        Inventory inventory = MenuKit.create(this, new Context(structure.id(), order), 54,
                "§8" + structure.label());
        for (int i = 0; i < order.size() && i < 45; i++) {
            Res res = order.get(i);
            long amount = structure.store().getOrDefault(res, 0L);
            List<String> lore = new ArrayList<>();
            lore.add("§7Stored: §f" + String.format("%,d", amount));
            if (res.form() != Res.Form.RAW) {
                lore.add("§8= " + GuildFormat.compact(res.rawEquivalent(amount)) + " raw");
            }
            lore.add("");
            lore.add("§eClick §7take a stack");
            lore.add("§eShift-click §7fill your inventory");
            ItemStack icon = MenuKit.decorate(res.icon(), res.color() + res.display(), lore);
            icon.setAmount((int) Math.max(1, Math.min(64, amount)));
            inventory.setItem(i, icon);
        }
        long used = PlacedStructure.total(structure.store());
        long cap = structure.capacity();
        de.aetherion.guilds.logistics.MachineState state = logistics.status(structure);
        List<String> info = new ArrayList<>();
        info.add(state.tag());
        info.add(state.active() || state.hint().isEmpty() ? "§8Belts that run into its walls fill it." : "§8" + state.hint());
        info.add("");
        info.add("§7Filled: §f" + GuildFormat.compact(used) + "§8/§7" + GuildFormat.compact(cap) + " §8goods");
        info.add(de.aetherion.guilds.project.GuildProjectService.bar(used, cap));
        List<String> feeders = logistics.feeders(structure);
        if (feeders.isEmpty()) {
            info.add(logistics.summary(structure));
        } else {
            // every sender by name with what it steadily brings: an idle Mill says so instead of padding a total
            info.addAll(feeders.subList(0, Math.min(5, feeders.size())));
            if (feeders.size() > 5) {
                info.add("§8… and " + (feeders.size() - 5) + " more");
            }
        }
        info.add(logistics.inPerMinute(structure) > 0
                ? "§7Arriving now §f+" + LogisticsService.perMinute(logistics.inPerMinute(structure)) + " §8(live)"
                : "§8Nothing arriving right now.");
        info.add("");
        info.add("§8A Compressed item counts as 128,");
        info.add("§8a Compacted one as 16,384.");
        inventory.setItem(INFO, MenuKit.named(structure.type().icon(), "§6" + structure.label(), info));
        inventory.setItem(TAKE_ALL, MenuKit.named(Material.HOPPER, "§aTake everything",
                "§7As much as fits in your inventory."));
        long carried = carriedGoods(player);
        inventory.setItem(PUT_ALL, MenuKit.named(Material.CHEST_MINECART, "§aPut everything in",
                carried > 0 ? "§7Every good from your inventory §8(§f" + String.format("%,d", carried) + "§8)"
                        : "§8Nothing of the island's goods in your inventory.",
                "§7Or §fclick §7a stack in your inventory §8(shift: all of it)§7.",
                "§8Storage and your pack pay for building and upgrades."));
        inventory.setItem(DECONSTRUCT, MenuKit.pickUpTile("§7It comes down, everything it cost comes back",
                "§7and it keeps its tier for the next one you build.",
                "§8Its goods move to your other huts, or come", "§8back with the next hut you set down."));
        org.bukkit.inventory.ItemStack upgrade = UpgradeTile.of(player, structures, structure);
        if (upgrade != null) {
            inventory.setItem(UPGRADE, upgrade);
        }
        inventory.setItem(BACK, MenuKit.named(Material.ARROW, "§eProduction"));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        java.util.Map<Integer, ChromePage.Mark> marks = ChromePage.marks(inventory);
        ChromePage.block(marks, 0, 44, ChromePage.Mark.POCKET);
        ChromePage.open(player, inventory, ChromePage.Theme.STORAGE, null, marks);
    }

    @Override
    public void click(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        Context context = (Context) holder.context();
        PlacedStructure structure = structures.get(context.structureId());
        if (structure == null) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case CLOSE -> player.closeInventory();
            case BACK -> {
                if (production != null) {
                    production.open(player, structure.host());
                } else if (build != null) {
                    build.open(player, structure.host());
                }
            }
            case UPGRADE -> {
                if (structures.nextTier(structure) == null) {
                    return;
                }
                if (click == ClickType.SHIFT_RIGHT && structures.missingCoins(player, structure) > 0L) {
                    if (structures.upgradeBlocked(player, structure, true) != null) {
                        structures.upgrade(player, structure, true); // explains why not
                        return;
                    }
                    player.closeInventory();
                    structures.upgrade(player, structure, true);
                    return;
                }
                if (structures.upgradeBlocked(player, structure) != null) {
                    structures.upgrade(player, structure); // explains why not
                    open(player, structure);
                    return;
                }
                player.closeInventory();
                structures.upgrade(player, structure);
            }
            case PUT_ALL -> {
                long put = 0L;
                for (ItemStack item : player.getInventory().getStorageContents()) {
                    Res res = Res.fromItem(item);
                    if (res != null) {
                        put += deposit(player, structure, res, Long.MAX_VALUE);
                    }
                }
                stored(player, structure, put);
            }
            case TAKE_ALL -> {
                long total = 0L;
                for (Res res : new ArrayList<>(structure.store().keySet())) {
                    long given = give(player, structure.store(), res, Long.MAX_VALUE);
                    total += given;
                    if (given <= 0L && player.getInventory().firstEmpty() < 0) {
                        break;
                    }
                }
                done(player, structure, total);
            }
            case DECONSTRUCT -> {
                if (MenuKit.confirmPickUp(player, slot, structure.id(), click)) {
                    player.closeInventory();
                    structures.deconstruct(player, structure);
                }
            }
            default -> {
                if (slot < 0 || slot >= 45 || slot >= context.slots().size()) {
                    return;
                }
                Res res = context.slots().get(slot);
                long max = click.isShiftClick() ? Long.MAX_VALUE : 64L;
                done(player, structure, give(player, structure.store(), res, max));
            }
        }
    }

    @Override
    public void packClick(Player player, MenuKit.Holder holder, int slot, ClickType click) {
        Context context = (Context) holder.context();
        PlacedStructure structure = structures.get(context.structureId());
        if (structure == null) {
            player.closeInventory();
            return;
        }
        if (!hosts.canCollect(player, structure.host()) && !hosts.isAdminBypass(player)) {
            return;
        }
        ItemStack item = player.getInventory().getItem(slot);
        Res res = Res.fromItem(item);
        if (res == null) {
            if (item != null && !item.getType().isAir()) {
                player.sendMessage("§7Only the island's goods go in here §8(quarry goods, Compressed, Compacted)§7.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            }
            return;
        }
        long put;
        if (click.isShiftClick()) {
            put = deposit(player, structure, res, Long.MAX_VALUE);
        } else {
            // just this stack
            long room = room(structure, res);
            int amount = (int) Math.min(item.getAmount(), room);
            if (amount > 0) {
                item.setAmount(item.getAmount() - amount);
                player.getInventory().setItem(slot, item.getAmount() <= 0 ? null : item);
                PlacedStructure.add(structure.store(), res, amount);
            }
            put = Math.max(0, amount);
        }
        stored(player, structure, put);
    }

    /** How many of this good still fit (capacity counts raw: a Compressed takes 128). */
    private static long room(PlacedStructure structure, Res res) {
        long free = structure.capacity() - PlacedStructure.total(structure.store());
        return Math.max(0L, free / res.rawEquivalent(1L));
    }

    /** Moves up to {@code max} of res from the player's inventory into the store; returns how many. */
    private static long deposit(Player player, PlacedStructure structure, Res res, long max) {
        long want = Math.min(Math.min(max, res.carried(player)), room(structure, res));
        if (want <= 0L) {
            return 0L;
        }
        long taken = res.takeCarried(player, want);
        if (taken > 0L) {
            PlacedStructure.add(structure.store(), res, taken);
        }
        return taken;
    }

    private static long carriedGoods(Player player) {
        long n = 0L;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (Res.fromItem(item) != null) {
                n += item.getAmount();
            }
        }
        return n;
    }

    private void stored(Player player, PlacedStructure structure, long put) {
        if (put > 0L) {
            structures.markDirty();
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.6f, 1.2f);
        } else if (PlacedStructure.total(structure.store()) >= structure.capacity()) {
            player.sendMessage("§c" + structure.label() + " is full.");
        }
        open(player, structure);
    }

    private void done(Player player, PlacedStructure structure, long given) {
        if (given > 0L) {
            structures.markDirty();
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.1f);
        } else if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage("§cYour inventory is full.");
        }
        open(player, structure);
    }

    /**
     * Moves up to {@code max} of res from the map into the player's inventory; returns how many. Remove before
     * credit (like the market): the goods leave the store first, whatever doesn't fit in the pack goes back.
     */
    static long give(Player player, Map<Res, Long> from, Res res, long max) {
        long want = Math.min(from.getOrDefault(res, 0L), max);
        if (want <= 0L) {
            return 0L;
        }
        ItemStack template = res.stack(1);
        if (template == null) {
            player.sendMessage("§c" + res.display() + " needs AetherionItems to take out.");
            return 0L;
        }
        long taken = PlacedStructure.take(from, res, want);
        long given = 0L;
        int stackSize = Math.max(1, template.getMaxStackSize());
        while (given < taken) {
            int amount = (int) Math.min(stackSize, taken - given);
            ItemStack stack = template.clone();
            stack.setAmount(amount);
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            int rejected = 0;
            for (ItemStack rest : left.values()) {
                rejected += rest.getAmount();
            }
            given += amount - rejected;
            if (rejected > 0) {
                break;
            }
        }
        if (taken > given) {
            PlacedStructure.add(from, res, taken - given);
        }
        return given;
    }
}
