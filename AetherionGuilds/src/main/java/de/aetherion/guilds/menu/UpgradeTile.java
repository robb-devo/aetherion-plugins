package de.aetherion.guilds.menu;

import de.aetherion.guilds.structure.PlacedStructure;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.structure.StructureUpgrades;
import de.aetherion.guilds.util.GuildFormat;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The "grow this building" button on the Storage / Machine pages: next tier, what it does, what it costs. */
final class UpgradeTile {

    private UpgradeTile() {
    }

    static ItemStack of(Player player, StructureService structures, PlacedStructure structure) {
        StructureUpgrades.Tier next = structures.nextTier(structure);
        int max = StructureUpgrades.maxLevel(structure.type());
        if (next == null) {
            if (max <= 1) {
                return null;
            }
            return MenuKit.named(Material.NETHER_STAR, "§6" + structure.label() + " §8(tier " + structure.level() + "/" + max + ")",
                    "§7Fully grown.");
        }
        List<String> lore = new ArrayList<>();
        lore.add("§7Tier §f" + structure.level() + " §8→ §f" + next.level() + "§8/" + max);
        lore.add("");
        lore.addAll(next.perks());
        lore.add("");
        lore.add("§7Costs:");
        for (StructureService.CostLine line : structures.costLines(player, structure, next)) {
            StructureUpgrades.Cost cost = line.cost();
            String have = line.have() == Long.MAX_VALUE ? "∞" : GuildFormat.compact(Math.min(line.have(), 9_999_999_999L));
            lore.add((line.enough() ? "§a✔ " : "§c✖ ") + "§f" + GuildFormat.compact(cost.amount()) + " " + cost.label()
                    + " §8(" + have + ")");
        }
        if (next.minIslandTier() > 1) {
            lore.add("§7Needs Island Tier §f" + next.minIslandTier());
        }
        lore.add("§8Items come from your huts first,");
        lore.add("§8then from your pack.");
        String blocked = structures.upgradeBlocked(player, structure);
        lore.add("");
        lore.add(blocked == null ? "§a▶ Click to build the " + next.name() : "§c✖ " + blocked);
        ItemStack item = MenuKit.named(Material.ANVIL, (blocked == null ? "§a⬆ " : "§e⬆ ") + next.name(), lore);
        return blocked == null ? MenuKit.glow(item) : item;
    }
}
