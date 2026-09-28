package de.aetherion.items.menu.dev;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.FishAccess;
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
 * DEV → Fishing Island: the Fishing Eldervale hub (teleports, cast, events, the line, progression).
 * Separate from {@link DevMenu.Page#FISHING}, which stays the Fishing gear shelf.
 *
 * <p>Island-side buttons go through {@link FishAccess#devAction}; skill / gear buttons run here.
 * Every action is {@code fishisle:<group>:<verb>[:arg]}. Content stays in the inner columns.
 */
final class FishIsleDevPages {

    static final String PREFIX = "fishisle:";
    private static final int[] CONTENT = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final List<AetherSkill> ELDERVALE_SKILLS = List.of(
            AetherSkill.LAKE_SENSE, AetherSkill.STEADY_LINE, AetherSkill.TALL_TALES, AetherSkill.TIDE_READER);

    private final CustomItem customItem;

    FishIsleDevPages(CustomItem customItem) {
        this.customItem = customItem;
    }

    static boolean owns(DevMenu.Page page) {
        return page != null && page.name().startsWith("FISH_ISLE");
    }

    private static FishAccess fish() {
        try {
            return AetherServices.fishing();
        } catch (LinkageError error) {
            return null;
        }
    }

    // ------------------------------------------------------------------ drawing

    void draw(Inventory inventory, DevMenu.Page page, Player player) {
        switch (page) {
            case FISH_ISLE -> drawHub(inventory);
            case FISH_ISLE_SPOTS -> drawSpots(inventory);
            case FISH_ISLE_NPCS -> drawNpcs(inventory);
            case FISH_ISLE_EVENTS -> drawEvents(inventory);
            case FISH_ISLE_PROGRESS -> drawProgress(inventory);
            default -> {
            }
        }
        inventory.setItem(45, button(Material.ARROW, "§eBack", page == DevMenu.Page.FISH_ISLE ? "back" : "page:FISH_ISLE"));
        inventory.setItem(49, button(Material.BARRIER, "§cClose", "close"));
    }

    private void drawHub(Inventory inventory) {
        List<String> status = new ArrayList<>(status());
        status.add("");
        status.add("§8Fishing Eldervale lives in the hub world.");
        status.add("§8Gear shelf: Fishing Sets (page 1).");
        inventory.setItem(4, button(Material.FISHING_ROD, "§b§lFishing Island", "page:FISH_ISLE", status.toArray(String[]::new)));

        inventory.setItem(10, button(Material.ENDER_PEARL, "§bTeleport: Landing", PREFIX + "tp:Landing",
                "§7Plaza by the Wishing Fountain.", "§8fish-isle.landing"));
        inventory.setItem(11, button(Material.COMPASS, "§eAll Teleports", "page:FISH_ISLE_SPOTS",
                "§7NPC spots · every named water · the shoal."));
        inventory.setItem(12, button(Material.VILLAGER_SPAWN_EGG, "§dNPC Cast", "page:FISH_ISLE_NPCS",
                "§7Harbourmaster · Bait Shack ·", "§7Taxidermist · Lakewatcher.",
                "§7Presets measured from the schem."));
        inventory.setItem(13, button(Material.BELL, "§eEvents & The Line", "page:FISH_ISLE_EVENTS",
                "§7Silver Run · Eldermaw · shoal ·", "§7heat tiers · force a bite."));
        inventory.setItem(14, button(Material.NAUTILUS_SHELL, "§aProgression & Skills", "page:FISH_ISLE_PROGRESS",
                "§7Fishing skill levels · Eldervale loadout ·", "§7rods · Log · rank · waters · bait · resets."));
        inventory.setItem(15, button(Material.LODESTONE, "§6Set Landing Here", PREFIX + "landing:set",
                "§7Writes fish-isle.landing to config.", "§7Aim the hub pad / §f/hubadmin set §7here."));
        inventory.setItem(16, button(Material.LEATHER_CHESTPLATE, "§bFishing Sets I – V", "page:FISHING",
                "§7The gear shelf (armor + rod per tier)."));

        inventory.setItem(19, button(Material.REPEATER, "§aReload Fishing Config", PREFIX + "config:reload",
                "§7Re-reads config.yml: footprint, waters,", "§7shoal spots, bells. Player data untouched."));
        inventory.setItem(20, button(Material.BLAZE_POWDER, "§eWater Outlines (20s)", PREFIX + "waters:show",
                "§7Particle rings for every water near you.", "§8splash lake · green tarn · wax fountain · flame shoal"));
        inventory.setItem(21, button(Material.FILLED_MAP, "§7Where Am I?", PREFIX + "waters:where",
                "§7Water + footprint check at your feet."));

        inventory.setItem(28, button(Material.FISHING_ROD, "§bOpen: Harbourmaster", PREFIX + "open:harbourmaster",
                "§7Angler card board without walking."));
        inventory.setItem(29, button(Material.KELP, "§eOpen: Bait Shack", PREFIX + "open:bait"));
        inventory.setItem(30, button(Material.TROPICAL_FISH, "§dOpen: Angler's Log", PREFIX + "open:taxidermist"));
        inventory.setItem(31, button(Material.SPYGLASS, "§3Open: Lakewatcher", PREFIX + "open:lakewatcher"));
    }

    private void drawSpots(Inventory inventory) {
        inventory.setItem(4, button(Material.COMPASS, "§eFishing Eldervale Teleports", "page:FISH_ISLE_SPOTS",
                "§7NPC spots use the placed NPC, else its preset.", "§7Waters land on the surface at the centre."));
        Map<String, Location> spots = spots();
        int index = 0;
        for (Map.Entry<String, Location> entry : spots.entrySet()) {
            if (index >= CONTENT.length) {
                break;
            }
            Location at = entry.getValue();
            Material icon = index == 0 ? Material.ENDER_PEARL
                    : index <= 4 ? Material.VILLAGER_SPAWN_EGG
                    : entry.getKey().equals("Shoal") ? Material.PRISMARINE_CRYSTALS : Material.WATER_BUCKET;
            inventory.setItem(CONTENT[index++], button(icon, "§f" + entry.getKey(), PREFIX + "tp:" + entry.getKey(),
                    "§8" + at.getBlockX() + (at.getBlockY() == 0 ? "" : " " + at.getBlockY()) + " " + at.getBlockZ()));
        }
        if (spots.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, "§cAetherionFishing offline", "noop"));
        }
    }

    private void drawNpcs(Inventory inventory) {
        inventory.setItem(4, button(Material.VILLAGER_SPAWN_EGG, "§dFishing Eldervale Cast", "page:FISH_ISLE_NPCS",
                "§eLeft §7give anchor  §eRight §7place at preset",
                "§eShift-left §7teleport there  §eShift-right §7remove",
                "§8Villagers respawn with their chunk — no dupes."));
        int index = 0;
        int[] slots = {11, 12, 14, 15};
        for (Map.Entry<String, ItemStack> entry : items("npcs").entrySet()) {
            if (index >= slots.length) {
                break;
            }
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
            inventory.setItem(slots[index++], tagged(icon, PREFIX + "npc:" + entry.getKey()));
        }
        if (index == 0) {
            inventory.setItem(22, button(Material.BARRIER, "§cAetherionFishing offline", "noop"));
            return;
        }
        inventory.setItem(29, button(Material.EMERALD_BLOCK, "§aPlace Whole Cast at Presets", PREFIX + "npcall:preset",
                "§7All four NPCs at their building spots.", "§8Presets: config isle-cast.presets"));
        inventory.setItem(33, button(Material.TNT, "§cRemove Whole Cast", PREFIX + "npcall:remove",
                "§eShift-click §7to confirm."));
    }

    private void drawEvents(Inventory inventory) {
        List<String> status = status();
        inventory.setItem(4, button(Material.BELL, "§eEvents & The Line", "page:FISH_ISLE_EVENTS",
                status.isEmpty() ? new String[]{"§cAetherionFishing offline"} : status.toArray(String[]::new)));
        inventory.setItem(10, button(Material.FEATHER, "§fStart Silver Run", PREFIX + "event:run",
                "§7Bells, then 2 min: lake bites ×3,", "§7one miss forgiven, +XP/coins per fish."));
        inventory.setItem(11, button(Material.HEART_OF_THE_SEA, "§5Start The Eldermaw", PREFIX + "event:maw",
                "§7Bells, then 3 min: every isle catch", "§7heaves its line. Haul it for payouts."));
        inventory.setItem(12, button(Material.BARRIER, "§cStop Lake Event", PREFIX + "event:stop"));
        inventory.setItem(14, button(Material.PRISMARINE_CRYSTALS, "§bShoal Near Me", PREFIX + "shoal:here",
                "§7Raise the shoal on the configured", "§7spot closest to you."));
        inventory.setItem(15, button(Material.PRISMARINE_SHARD, "§bNew Shoal Elsewhere", PREFIX + "shoal:next"));
        inventory.setItem(16, button(Material.SPONGE, "§7Clear Shoal", PREFIX + "shoal:clear"));

        inventory.setItem(19, button(Material.SNOWBALL, "§7Heat → Cold §8(✦0)", PREFIX + "line:heat:0"));
        inventory.setItem(20, button(Material.TORCH, "§eHeat → Warm §8(✦3)", PREFIX + "line:heat:3"));
        inventory.setItem(21, button(Material.CAMPFIRE, "§6Heat → Hot Water §8(✦5)", PREFIX + "line:heat:5"));
        inventory.setItem(22, button(Material.MAGMA_BLOCK, "§cHeat → Boiling §8(✦10)", PREFIX + "line:heat:10",
                "§7Rares ×1.5 · Goldscale Emperor can bite."));
        inventory.setItem(23, button(Material.CONDUIT, "§dHeat → Whirlpool §8(✦20)", PREFIX + "line:heat:20",
                "§7Rares ×2 · legendaries ×2.5."));

        inventory.setItem(28, button(Material.COD, "§9Next Bite: Rare", PREFIX + "line:force:rare",
                "§7Mirrorback Sturgeon — narrow window."));
        inventory.setItem(29, button(Material.GOLD_INGOT, "§6Next Bite: Goldscale Emperor", PREFIX + "line:force:goldscale_emperor",
                "§7Legendary — narrow window, fast marker."));
        inventory.setItem(30, button(Material.GHAST_TEAR, "§6Next Bite: The Pale Ghost", PREFIX + "line:force:pale_ghost"));
        inventory.setItem(31, button(Material.IRON_NUGGET, "§fNext Bite: Silverrun Herring", PREFIX + "line:force:silverrun_herring"));
        inventory.setItem(32, button(Material.TROPICAL_FISH, "§9Next Bite: Wishing Koi", PREFIX + "line:force:wishing_koi"));
        inventory.setItem(34, button(Material.PAPER, "§7How to test", "noop",
                "§71. Heat → Boiling, cast on the isle.",
                "§72. Force a legendary, reel on gold.",
                "§73. Miss a rare → cast the same water",
                "§7   within 40s for the second chance."));
    }

    private void drawProgress(Inventory inventory) {
        int[] levels = {1, 20, 40, 60, 80, 100};
        for (int i = 0; i < levels.length; i++) {
            inventory.setItem(10 + i, button(Material.EXPERIENCE_BOTTLE, "§aFishing Skills → Lv. " + levels[i],
                    PREFIX + "skill:level:" + levels[i], "§7Every Fishing skill, you only."));
        }
        List<String> loadout = new ArrayList<>();
        loadout.add("§7Grants all slots, equips:");
        for (AetherSkill skill : ELDERVALE_SKILLS) {
            loadout.add("§8• §f" + skill.displayName() + " §8— §7" + skill.details());
        }
        inventory.setItem(16, button(Material.NAUTILUS_SHELL, "§dEquip Eldervale Angler Loadout", PREFIX + "skill:equip",
                loadout.toArray(String[]::new)));

        for (int tier = 1; tier <= 5; tier++) {
            ItemStack rod = customItem.fishing().rod(tier);
            String id = tier == 1 ? "fishing_rod" : "fishing_rod_" + tier;
            if (rod != null) {
                inventory.setItem(18 + tier, tagged(rod.clone(), "item:" + id));
            }
        }
        inventory.setItem(24, button(Material.LEATHER_CHESTPLATE, "§bFishing Sets I – V", "page:FISHING",
                "§7The gear shelf (armor + rod per tier)."));
        inventory.setItem(25, button(Material.PLAYER_HEAD, "§6All Trophies", PREFIX + "give:trophies",
                "§7One of each species, record class,", "§7plus an Eldermaw Scale."));

        inventory.setItem(28, button(Material.WRITABLE_BOOK, "§9Log → Every Species", PREFIX + "log:all",
                "§7All species at silver weight."));
        inventory.setItem(29, button(Material.WRITABLE_BOOK, "§bAngler Rank → V", PREFIX + "log:rank:5",
                "§7Fills the Log to rank V.", "§8No rank rewards paid."));
        inventory.setItem(30, button(Material.ENCHANTED_BOOK, "§bAngler Rank → X", PREFIX + "log:rank:10"));
        inventory.setItem(31, button(Material.FILLED_MAP, "§eDiscover All Waters", PREFIX + "waters:all"));
        inventory.setItem(32, button(Material.MAP, "§7Reset Water Discovery", PREFIX + "waters:reset"));
        inventory.setItem(33, button(Material.GLOW_BERRIES, "§e+16 Every Bait", PREFIX + "bait:give"));
        inventory.setItem(34, button(Material.TNT, "§cWipe My Fishing Eldervale Profile", PREFIX + "profile:reset",
                "§7Log, waters, trophies, rank, bait.", "§eShift-click §7to confirm."));
        inventory.setItem(37, button(Material.BOOK, "§7Reset Angler's Log", PREFIX + "log:reset"));
        inventory.setItem(38, button(Material.BUCKET, "§7Empty Bait Tin", PREFIX + "bait:clear"));
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
        if (body.equals("give:trophies")) {
            for (ItemStack trophy : items("trophies").values()) {
                give(player, trophy.clone());
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
        boolean leaves = body.startsWith("tp:") || body.startsWith("open:") || body.startsWith("event:")
                || body.equals("waters:show") || body.startsWith("line:force");
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
                if (skill.category() == AetherSkill.Category.FISHING) {
                    skills.setLevel(player, skill, level);
                }
            }
            return "§aEvery Fishing skill → Lv. " + level + "§a.";
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
                    ? "§dEldervale Angler loadout equipped §7(Lake Sense, Steady Line, Tall Tales, Tide Reader)."
                    : "§eSlots full — could not equip: §f" + String.join(", ", missed) + " §7(/skills to swap).";
        }
        return "§cUnknown skill action.";
    }

    private static String dev(Player player, String action) {
        FishAccess fish = fish();
        if (fish == null) {
            return "§cAetherionFishing is not loaded (or Core predates Fishing Eldervale).";
        }
        try {
            return fish.devAction(player, action);
        } catch (LinkageError error) {
            return "§cAetherionFishing / Core on the server predate the Fishing Island hub — deploy all three jars.";
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
        FishAccess fish = fish();
        if (fish == null) {
            return List.of("§cAetherionFishing offline");
        }
        try {
            return fish.devStatus();
        } catch (LinkageError error) {
            return List.of("§cFishing/Core jar too old for this hub");
        }
    }

    private static Map<String, Location> spots() {
        FishAccess fish = fish();
        if (fish == null) {
            return Map.of();
        }
        try {
            return fish.devSpots();
        } catch (LinkageError error) {
            return Map.of();
        }
    }

    private static Map<String, ItemStack> items(String group) {
        FishAccess fish = fish();
        if (fish == null) {
            return Map.of();
        }
        try {
            return fish.devItems(group);
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
