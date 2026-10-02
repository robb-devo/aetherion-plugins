package de.aetherion.guilds.menu;

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

/** A Storage Hut or Depot: what the belts delivered, withdrawable by stack or all at once. */
public final class StorageMenu implements MenuKit.Menu {

    private static final int BACK = 45;
    private static final int TAKE_ALL = 48;
    private static final int INFO = 49;
    private static final int DECONSTRUCT = 51;
    private static final int CLOSE = 53;

    private record Context(UUID structureId, List<Res> slots) {
    }

    private final HostService hosts;
    private final StructureService structures;
    private final LogisticsService logistics;
    private BuildMenu build;

    public StorageMenu(HostService hosts, StructureService structures, LogisticsService logistics) {
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
        if (!hosts.canCollect(player, structure.host()) && !hosts.isAdminBypass(player)) {
            player.sendMessage(hosts.rankHint(structure.host(), "open this storage"));
            return;
        }
        List<Res> order = new ArrayList<>(structure.store().keySet());
        order.sort(Comparator.comparing((Res r) -> r.type().ordinal()).thenComparing(r -> r.form().ordinal()));
        Inventory inventory = MenuKit.create(this, new Context(structure.id(), order), 54,
                "§8" + structure.type().display());
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
        long cap = structure.type().capacity();
        inventory.setItem(INFO, MenuKit.named(structure.type().icon(), "§6" + structure.type().display(),
                "§7Filled: §f" + GuildFormat.compact(used) + "§8/§7" + GuildFormat.compact(cap) + " §8raw-eq",
                de.aetherion.guilds.project.GuildProjectService.bar(used, cap),
                logistics.summary(structure),
                "",
                "§8Belts that run into its walls",
                "§8fill it, day and night."));
        inventory.setItem(TAKE_ALL, MenuKit.named(Material.HOPPER, "§aTake everything",
                "§7As much as fits in your inventory."));
        inventory.setItem(DECONSTRUCT, MenuKit.named(Material.IRON_AXE, "§cTake it down",
                "§7Only when empty. Half the coins back.",
                "§eShift-click §7to confirm"));
        inventory.setItem(BACK, MenuKit.named(Material.ARROW, "§eBlueprints"));
        inventory.setItem(CLOSE, MenuKit.named(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
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
                if (build != null) {
                    build.open(player, structure.host());
                }
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
                if (click.isShiftClick()) {
                    player.closeInventory();
                    structures.deconstruct(player, structure);
                } else {
                    player.sendMessage("§7Shift-click to take the " + structure.type().display() + " down.");
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

    private void done(Player player, PlacedStructure structure, long given) {
        if (given > 0L) {
            structures.markDirty();
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.1f);
        } else if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage("§cYour inventory is full.");
        }
        open(player, structure);
    }

    /** Moves up to {@code max} of res from the map into the player's inventory; returns how many. */
    static long give(Player player, Map<Res, Long> from, Res res, long max) {
        long have = from.getOrDefault(res, 0L);
        long want = Math.min(have, max);
        if (want <= 0L) {
            return 0L;
        }
        ItemStack template = res.stack(1);
        if (template == null) {
            player.sendMessage("§c" + res.display() + " needs AetherionItems to take out.");
            return 0L;
        }
        long given = 0L;
        int stackSize = Math.max(1, template.getMaxStackSize());
        while (given < want) {
            int amount = (int) Math.min(stackSize, want - given);
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
        PlacedStructure.take(from, res, given);
        return given;
    }
}
