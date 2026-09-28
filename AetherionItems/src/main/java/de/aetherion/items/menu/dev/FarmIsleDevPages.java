package de.aetherion.items.menu.dev;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.FarmAccess;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.CustomItem;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillService;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DEV → Farming Island: the Eldervale Farm Isle hub (teleports, cast, props, events, progression).
 * Separate from {@link DevMenu.Page#FARMING}, which stays the Farming gear shelf.
 *
 * <p>Island-side buttons go through {@link FarmAccess#devAction}; skill / gear buttons run here.
 * Every action is {@code farmisle:<group>:<verb>[:arg]}.
 */
final class FarmIsleDevPages {

    static final String PREFIX = "farmisle:";
    private static final int[] CONTENT = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final List<AetherSkill> ELDERVALE_SKILLS = List.of(
            AetherSkill.SOIL_SENSE, AetherSkill.ROW_RHYTHM, AetherSkill.BLUE_RIBBON,
            AetherSkill.BIRD_LAW, AetherSkill.MARKET_DAY);

    private final CustomItem customItem;

    FarmIsleDevPages(CustomItem customItem) {
        this.customItem = customItem;
    }

    static boolean owns(DevMenu.Page page) {
        return page != null && page.name().startsWith("FARM_ISLE");
    }

    private static FarmAccess farm() {
        return AetherServices.farming();
    }

    // ------------------------------------------------------------------ drawing

    void draw(Inventory inventory, DevMenu.Page page, Player player) {
        switch (page) {
            case FARM_ISLE -> drawHub(inventory);
            case FARM_ISLE_SPOTS -> drawSpots(inventory);
            case FARM_ISLE_NPCS -> drawNpcs(inventory);
            case FARM_ISLE_PROPS -> drawProps(inventory);
            case FARM_ISLE_EVENTS -> drawEvents(inventory);
            case FARM_ISLE_PROGRESS -> drawProgress(inventory, player);
            default -> {
            }
        }
        inventory.setItem(45, button(Material.ARROW, "§eBack", page == DevMenu.Page.FARM_ISLE ? "back" : "page:FARM_ISLE"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    private void drawHub(Inventory inventory) {
        List<String> status = new ArrayList<>(status());
        status.add("");
        status.add("§8Eldervale lives in the hub world.");
        status.add("§8Gear shelf: Farming Sets (page 1).");
        inventory.setItem(4, button(Material.HAY_BLOCK, "§a§lFarming Island", "page:FARM_ISLE", status.toArray(String[]::new)));

        inventory.setItem(10, button(Material.ENDER_PEARL, "§bTeleport: Landing", PREFIX + "tp:Landing",
                "§7Where the hub jump pad drops players.", "§8farm-island.island-exit"));
        inventory.setItem(11, button(Material.COMPASS, "§eAll Teleports", "page:FARM_ISLE_SPOTS",
                "§7NPC spots + every named plot."));
        inventory.setItem(12, button(Material.VILLAGER_SPAWN_EGG, "§dNPC Cast", "page:FARM_ISLE_NPCS",
                "§7Warden · Orders · Baker · Granary ·", "§7Beekeeper · three farmhands.",
                "§7Presets at the real building doors."));
        inventory.setItem(13, button(Material.CARVED_PUMPKIN, "§6Props & Tools", "page:FARM_ISLE_PROPS",
                "§7Scarecrow · hay wagon · cane tool ·", "§7district signs · millstones · FAWE list."));
        inventory.setItem(14, button(Material.CLOCK, "§eEvents", "page:FARM_ISLE_EVENTS",
                "§7Bee Bloom · Harvest Moon · birds ·", "§7scarecrow wave · featured · prize pop."));
        inventory.setItem(15, button(Material.GOLDEN_HOE, "§aProgression & Skills", "page:FARM_ISLE_PROGRESS",
                "§7Farming skill levels · Eldervale loadout ·", "§7hoes · mastery · foods · prizes · resets."));
        inventory.setItem(16, button(Material.GRINDSTONE, "§6Millstone & Pantry", "page:MILLSTONE",
                "§7Mill anchors · crop tiers · treats."));

        inventory.setItem(19, button(Material.REPEATER, "§aReload Farming Config", PREFIX + "config:reload",
                "§7Re-reads config.yml: plots, presets,", "§7event timers. Player data untouched."));
        inventory.setItem(20, button(Material.BLAZE_POWDER, "§ePlot Outlines (20s)", PREFIX + "plots:show",
                "§7Particle rings for every plot near you.", "§8flame landmark · splash water · green field"));
        inventory.setItem(21, button(Material.FILLED_MAP, "§7Where Am I?", PREFIX + "plots:where",
                "§7Plot + footprint check at your feet."));
        inventory.setItem(22, button(Material.SUGAR_CANE, "§aSeed Cane + Fields", PREFIX + "seed:run",
                "§7Cane banks by water + crops on bare", "§7farmland inside the footprint.",
                "§eShift-click §7to force a full re-run."));
        inventory.setItem(23, button(Material.END_PORTAL_FRAME, "§8Legacy Portal Tools", "page:PORTALS",
                "§7Old aether_farm_island void world.", "§8Retired 2026-09-20 — kept for reference."));

        inventory.setItem(28, button(Material.WHEAT, "§aOpen: Field Warden", PREFIX + "open:warden",
                "§7Test the isle board without walking."));
        inventory.setItem(29, button(Material.PAPER, "§6Open: Harvest Orders", PREFIX + "open:clerk"));
        inventory.setItem(30, button(Material.BREAD, "§eOpen: Oven House", PREFIX + "open:baker"));
        inventory.setItem(31, button(Material.BOOK, "§bOpen: Crop Mastery", PREFIX + "open:granary"));
        inventory.setItem(32, button(Material.HONEYCOMB, "§eOpen: Beekeeper", PREFIX + "open:beekeeper"));
    }

    private void drawSpots(Inventory inventory) {
        inventory.setItem(4, button(Material.COMPASS, "§eEldervale Teleports", "page:FARM_ISLE_SPOTS",
                "§7NPC spots use the placed NPC, else its preset.", "§7Plots land on the surface at the centre."));
        Map<String, Location> spots = spots();
        int index = 0;
        for (Map.Entry<String, Location> entry : spots.entrySet()) {
            if (index >= CONTENT.length) {
                break;
            }
            Location at = entry.getValue();
            Material icon = index == 0 ? Material.ENDER_PEARL : index <= 8 ? Material.VILLAGER_SPAWN_EGG : Material.GRASS_BLOCK;
            inventory.setItem(CONTENT[index++], button(icon, "§f" + entry.getKey(), PREFIX + "tp:" + entry.getKey(),
                    "§8" + at.getBlockX() + (at.getBlockY() == 0 ? "" : " " + at.getBlockY()) + " " + at.getBlockZ()));
        }
        if (spots.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, "§cAetherionFarming offline", "noop"));
        }
    }

    private void drawNpcs(Inventory inventory) {
        inventory.setItem(4, button(Material.VILLAGER_SPAWN_EGG, "§dEldervale Cast", "page:FARM_ISLE_NPCS",
                "§eLeft §7give anchor  §eRight §7place at preset",
                "§eShift-left §7teleport there  §eShift-right §7remove",
                "§8Villagers respawn with their chunk — no dupes."));
        int index = 0;
        for (Map.Entry<String, ItemStack> entry : items("npcs").entrySet()) {
            ItemStack icon = entry.getValue().clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add("§eLeft §7anchor · §eRight §7preset");
                lore.add("§eShift-left §7go · §eShift-right §7remove");
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inventory.setItem(CONTENT[index++], tagged(icon, PREFIX + "npc:" + entry.getKey()));
        }
        if (index == 0) {
            inventory.setItem(22, button(Material.BARRIER, "§cAetherionFarming offline", "noop"));
            return;
        }
        inventory.setItem(29, button(Material.EMERALD_BLOCK, "§aPlace Whole Cast at Presets", PREFIX + "npcall:preset",
                "§7All eight NPCs at their door-front spots.", "§8Presets: config isle-cast.presets"));
        inventory.setItem(33, button(Material.TNT, "§cRemove Whole Cast", PREFIX + "npcall:remove",
                "§eShift-click §7to confirm."));
    }

    private void drawProps(Inventory inventory) {
        inventory.setItem(4, button(Material.CARVED_PUMPKIN, "§6Props & Tools", "page:FARM_ISLE_PROPS",
                "§7Click to take a tool. Right-click a block to place;", "§7sneak + right-click packs it up."));
        int index = 0;
        for (Map.Entry<String, ItemStack> entry : items("props").entrySet()) {
            inventory.setItem(CONTENT[index++], tagged(entry.getValue().clone(), "item:" + entry.getKey()));
        }
        inventory.setItem(CONTENT[index++], tagged(de.aetherion.items.farm.MillstoneCabinet.createAnchor().clone(), "item:millstone"));
        inventory.setItem(CONTENT[index], tagged(de.aetherion.items.farm.MillstoneWindmill.createAnchor().clone(), "item:millstone_v2"));
        inventory.setItem(31, button(Material.PAPER, "§eFAWE Prop Ideas (paste yourself)", "noop",
                "§7Server schem library → where it fits:",
                "§fae_prop_notice_board §8· §7Market Barn front",
                "§fae_market_stall §8· §7Market Beds edge",
                "§fae_prop_well §8· §7Southfield Hamlet square",
                "§fae_prop_wayside_shrine §8· §7Beet Terraces path",
                "§fae_prop_lantern_post §8· §7paths between plots",
                "§fae_prop_cargo_stack §8· §7Old Granary yard",
                "",
                "§8//schem load <name> · //paste -a (front faces south)",
                "§8Use Plot Outlines to stay inside the footprint."));
    }

    private void drawEvents(Inventory inventory) {
        List<String> status = status();
        inventory.setItem(4, button(Material.CLOCK, "§eIsle Events", "page:FARM_ISLE_EVENTS",
                status.isEmpty() ? new String[]{"§cAetherionFarming offline"} : status.toArray(String[]::new)));
        inventory.setItem(10, button(Material.HONEYCOMB, "§eStart Bee Bloom", PREFIX + "event:bloom",
                "§7Swarms the field you stand in", "§7(or a random field). 8s telegraph, 90s."));
        inventory.setItem(11, button(Material.END_ROD, "§bStart Harvest Moon", PREFIX + "event:moon",
                "§7Prize Crops ×4 for two minutes."));
        inventory.setItem(12, button(Material.BARRIER, "§cStop Isle Event", PREFIX + "event:stop"));
        inventory.setItem(13, button(Material.FEATHER, "§fBird Scare Here", PREFIX + "event:birds",
                "§7Flock on the nearest mature crops.", "§7Isle footprint → bold isle flock."));
        inventory.setItem(14, button(Material.CARVED_PUMPKIN, "§6Scarecrow Wave", PREFIX + "event:scarecrow",
                "§7Nearest placed scarecrow (64 blocks)."));
        inventory.setItem(15, button(Material.WHEAT, "§eReroll Featured Crop", PREFIX + "event:featured"));
        inventory.setItem(16, button(Material.GOLDEN_CARROT, "§6Pop a Prize Crop", PREFIX + "prize:spawn",
                "§7Out of the block you look at", "§7(its crop, else a carrot)."));
        inventory.setItem(19, button(Material.NOTE_BLOCK, "§dMax Harvest Rhythm", PREFIX + "rhythm:max",
                "§7Straight to Harvest Song. Keep harvesting", "§7inside the hold window to keep it."));
    }

    private void drawProgress(Inventory inventory, Player player) {
        inventory.setItem(4, button(Material.GOLDEN_HOE, "§aProgression & Skills", "page:FARM_ISLE_PROGRESS",
                "§7Your own Farming data only.", "§8Resets need a shift-click."));
        int[] levels = {1, 20, 40, 60, 80, 100};
        for (int i = 0; i < levels.length; i++) {
            inventory.setItem(10 + i, button(Material.EXPERIENCE_BOTTLE, "§aFarming Skills → Lv. " + levels[i],
                    PREFIX + "skill:level:" + levels[i], "§7Every Farming skill, you only."));
        }
        List<String> loadout = new ArrayList<>();
        loadout.add("§7Grants all slots, equips:");
        for (AetherSkill skill : ELDERVALE_SKILLS) {
            loadout.add("§8• §f" + skill.displayName() + " §8— §7" + skill.details());
        }
        inventory.setItem(16, button(Material.NOTE_BLOCK, "§dEquip Eldervale Loadout", PREFIX + "skill:equip",
                loadout.toArray(String[]::new)));

        for (int tier = 1; tier <= 5; tier++) {
            ItemStack hoe = customItem.farming().hoe(tier);
            String id = tier == 1 ? "farming_hoe" : "farming_hoe_" + tier;
            inventory.setItem(18 + tier, tagged(hoe.clone(), "item:" + id));
        }
        inventory.setItem(24, button(Material.LEATHER_CHESTPLATE, "§eFarming Sets I – V", "page:FARMING",
                "§7The gear shelf (armor + hoe per tier)."));
        inventory.setItem(25, button(Material.GOLD_NUGGET, "§6All Prize Crops", PREFIX + "give:prizes",
                "§7One of each crop, hefty weight."));

        inventory.setItem(28, button(Material.WRITABLE_BOOK, "§bMastery → 0", PREFIX + "mastery:0"));
        inventory.setItem(29, button(Material.WRITABLE_BOOK, "§bMastery → III", PREFIX + "mastery:3"));
        inventory.setItem(30, button(Material.ENCHANTED_BOOK, "§bMastery → VI", PREFIX + "mastery:6",
                "§7All crops. Rewards are not paid."));
        inventory.setItem(31, button(Material.FILLED_MAP, "§eDiscover All Plots", PREFIX + "plots:all"));
        inventory.setItem(32, button(Material.MAP, "§7Reset Plot Discovery", PREFIX + "plots:reset"));
        inventory.setItem(33, button(Material.PAPER, "§6Reset Order Board", PREFIX + "orders:reset",
                "§7Cooldowns cleared, fresh orders."));
        inventory.setItem(34, button(Material.TNT, "§cWipe My Eldervale Profile", PREFIX + "profile:reset",
                "§7Plots, mastery, prizes, orders, food.", "§eShift-click §7to confirm."));

        int index = 0;
        int[] foodSlots = {37, 38, 39, 40, 41};
        for (Map.Entry<String, ItemStack> entry : items("foods").entrySet()) {
            if (index >= foodSlots.length) {
                break;
            }
            inventory.setItem(foodSlots[index++], tagged(entry.getValue().clone(), "item:" + entry.getKey()));
        }
        inventory.setItem(43, button(Material.MILK_BUCKET, "§7Clear Food Buff", PREFIX + "food:clear"));
    }

    // ------------------------------------------------------------------ clicks

    /** @return the page to reopen, or {@code null} to leave the inventory as is. */
    DevMenu.Page handle(Player player, String action, ClickType click, DevMenu.Page current) {
        String body = action.substring(PREFIX.length());
        boolean shift = click != null && click.isShiftClick();
        boolean right = click != null && click.isRightClick();

        if (body.startsWith("skill:")) {
            player.sendMessage(skill(player, body.substring("skill:".length())));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
            return current;
        }
        if (body.equals("give:prizes")) {
            for (ItemStack prize : items("prizes").values()) {
                give(player, prize.clone());
            }
            return null;
        }
        if (body.startsWith("npc:")) {
            String role = body.substring("npc:".length());
            if (!right && !shift) {
                ItemStack anchor = items("npcs").get(role);
                if (anchor != null) {
                    give(player, anchor.clone());
                }
                return null;
            }
            String verb = shift ? (right ? "remove" : "goto") : "preset";
            reply(player, dev(player, "npc:" + verb + ":" + role));
            return "goto".equals(verb) ? null : current;
        }
        if (body.equals("npcall:preset")) {
            reply(player, dev(player, "npc:preset-all"));
            return current;
        }
        if (body.equals("npcall:remove") || body.equals("profile:reset")) {
            if (!shift) {
                player.sendMessage("§eShift-click to confirm.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.7f);
                return null;
            }
            reply(player, dev(player, body.equals("npcall:remove") ? "npc:remove-all" : "profile:reset"));
            return current;
        }
        if (body.equals("seed:run") && shift) {
            reply(player, dev(player, "seed:force"));
            return null;
        }
        boolean leaves = body.startsWith("tp:") || body.startsWith("open:") || body.equals("prize:spawn")
                || body.startsWith("event:") || body.equals("plots:show");
        if (leaves) {
            player.closeInventory();
        }
        reply(player, dev(player, body));
        return leaves ? null : current;
    }

    private String skill(Player player, String verb) {
        AetherionItems plugin = AetherionItems.getInstance();
        SkillService skills = plugin == null ? null : plugin.getSkills();
        if (skills == null) {
            return "§cSkills are not loaded.";
        }
        if (verb.startsWith("level:")) {
            int level;
            try {
                level = Integer.parseInt(verb.substring("level:".length()));
            } catch (NumberFormatException ignored) {
                return "§cBad level.";
            }
            for (AetherSkill skill : AetherSkill.values()) {
                if (skill.category() == AetherSkill.Category.FARMING) {
                    skills.setLevel(player, skill, level);
                }
            }
            return "§aEvery Farming skill → Lv. " + level + "§a.";
        }
        if (verb.equals("equip")) {
            skills.grantAllSlots(player);
            List<String> missed = new ArrayList<>();
            for (AetherSkill skill : ELDERVALE_SKILLS) {
                if (skills.slotOf(player, skill) < 0 && !skills.equip(player, skill)) {
                    missed.add(skill.displayName());
                }
            }
            return missed.isEmpty()
                    ? "§dEldervale loadout equipped §7(Soil Sense, Row Rhythm, Blue Ribbon, Bird Law, Market Day)."
                    : "§eSlots full — could not equip: §f" + String.join(", ", missed) + " §7(/skills to swap).";
        }
        return "§cUnknown skill action.";
    }

    private static String dev(Player player, String action) {
        FarmAccess farm = farm();
        if (farm == null) {
            return "§cAetherionFarming is not loaded.";
        }
        try {
            return farm.devAction(player, action);
        } catch (LinkageError error) {
            return "§cAetherionFarming / Core on the server predate the Farming Island hub — deploy all three jars.";
        }
    }

    private static void reply(Player player, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        for (String line : message.split("\n")) {
            player.sendMessage(line);
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.25f);
    }

    // ------------------------------------------------------------------ data

    private static List<String> status() {
        FarmAccess farm = farm();
        if (farm == null) {
            return List.of("§cAetherionFarming offline");
        }
        try {
            return farm.devStatus();
        } catch (LinkageError error) {
            return List.of("§cFarming/Core jar too old for this hub");
        }
    }

    private static Map<String, Location> spots() {
        FarmAccess farm = farm();
        if (farm == null) {
            return Map.of();
        }
        try {
            return farm.devSpots();
        } catch (LinkageError error) {
            return Map.of();
        }
    }

    private static Map<String, ItemStack> items(String group) {
        FarmAccess farm = farm();
        if (farm == null) {
            return Map.of();
        }
        try {
            return farm.devItems(group);
        } catch (LinkageError error) {
            return Map.of();
        }
    }

    private static void give(Player player, ItemStack item) {
        if (item == null) {
            return;
        }
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
    }

    private static ItemStack button(Material material, String name, String action, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            de.aetherion.items.util.GuiItems.hideVanilla(meta);
            meta.getPersistentDataContainer().set(ItemKeys.devAction(), PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack tagged(ItemStack item, String action) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ItemKeys.devAction(), PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
    }
}
