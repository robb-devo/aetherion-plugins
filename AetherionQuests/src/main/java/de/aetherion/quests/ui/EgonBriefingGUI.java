package de.aetherion.quests.ui;

import de.aetherion.core.api.ChromePage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Rookie briefing desk — Miss Ledger after tutorial. Topic answers stay in the GUI.
 */
public final class EgonBriefingGUI implements Listener {

    public static final String TITLE = "§8Rookie Briefing";
    public static final String DETAIL_TITLE = "§8Briefing · Topic";

    /** One row: all topics together. */
    private static final int SLOT_MANAGER = 9;
    private static final int SLOT_BOOSTERS = 10;
    private static final int SLOT_PETS = 11;
    private static final int SLOT_SKILLS = 12;
    private static final int SLOT_RECIPES = 13;
    private static final int SLOT_MINING = 14;
    private static final int SLOT_STORAGE = 15;
    private static final int SLOT_DUNGEONS = 16;
    private static final int SLOT_TRADER = 17;
    private static final int SLOT_CLOSE = 22;

    private static final int DETAIL_BOOK = 13;
    private static final int DETAIL_BACK = 22;

    public EgonBriefingGUI(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void open(Player player) {
        open(player, "Egon");
    }

    public static void open(Player player, String speaker) {
        String who = speaker == null || speaker.isBlank() ? "Egon" : speaker;
        Inventory inventory = Bukkit.createInventory(
                new Holder(Holder.Mode.TOPICS, -1, who),
                27,
                titleFor(who)
        );
        fillEmpty(inventory);
        inventory.setItem(4, button(
                Material.NETHER_STAR,
                "§6Rookie Briefing",
                "§7Pick a topic. Try to keep up.",
                "§8" + who + " is not paid enough for this."
        ));
        inventory.setItem(SLOT_MANAGER, button(
                de.aetherion.core.api.JournalText.enabled() ? Material.BOOK : Material.NETHER_STAR,
                "§eJournal",
                "§7The book on hotbar slot 9.",
                "§7Every system you unlock lives in it."
        ));
        inventory.setItem(SLOT_BOOSTERS, button(
                Material.COAL,
                "§8Boosters",
                "§7Little upgrades that stick to gear.",
                "§7More boosters, bigger numbers."
        ));
        inventory.setItem(SLOT_PETS, button(
                Material.LEAD,
                "§dPets",
                "§7Companions with stats.",
                "§7They level. You look cooler."
        ));
        inventory.setItem(SLOT_SKILLS, button(
                Material.NETHERITE_SCRAP,
                "§dSkills",
                "§7Skills level as you work.",
                "§7Talents go into slots; more with your level."
        ));
        inventory.setItem(SLOT_RECIPES, button(
                Material.CRAFTING_TABLE,
                "§aRecipes & Crafting",
                "§7Upgrade gear with resources.",
                "§7Previous piece goes in the middle."
        ));
        inventory.setItem(SLOT_MINING, button(
                Material.IRON_PICKAXE,
                "§bMining Power & Fortune",
                "§7Power unlocks harder ores.",
                "§7Fortune multiplies drops."
        ));
        inventory.setItem(SLOT_STORAGE, button(
                Material.CHEST,
                "§6Storage",
                "§7Extra pages for your hoarding.",
                "§7Journal → Storage."
        ));
        inventory.setItem(SLOT_DUNGEONS, button(
                Material.IRON_BARS,
                "§cDungeons",
                "§7Instanced fights. Better loot.",
                "§7Gear levels inside. Don't die loudly."
        ));
        inventory.setItem(SLOT_TRADER, button(
                Material.EMERALD,
                "§2Trader / Bazaar / AH",
                "§7Talk to the trader once.",
                "§7Unlocks market tabs in your Journal."
        ));
        inventory.setItem(SLOT_CLOSE, button(
                Material.BARRIER,
                "§cI'm educated now",
                "§7Close. Go touch grass. Or ore."
        ));
        ChromePage.open(player, inventory, ChromePage.Theme.QUEST);
    }

    private static String titleFor(String speaker) {
        if ("Miss Ledger".equalsIgnoreCase(speaker) || "ledger".equalsIgnoreCase(speaker)) {
            return "§8Miss Ledger · Briefing";
        }
        return "§8" + speaker + " · Rookie Briefing";
    }

    private static void openDetail(Player player, String speaker, int topicSlot) {
        String[] lines = linesFor(topicSlot);
        if (lines == null) {
            return;
        }
        String who = speaker == null || speaker.isBlank() ? "Egon" : speaker;
        String title = de.aetherion.core.api.JournalText.t(topicTitle(topicSlot));
        Inventory inventory = Bukkit.createInventory(
                new Holder(Holder.Mode.DETAIL, topicSlot, who),
                27,
                DETAIL_TITLE
        );
        fillEmpty(inventory);

        List<String> lore = new ArrayList<>();
        lore.add("§8" + who);
        lore.add("");
        for (String line : lines) {
            for (String wrapped : wrap(de.aetherion.core.api.JournalText.t(line), 40)) {
                lore.add("§f" + wrapped);
            }
            lore.add("");
        }
        lore.add("§7Click Back for another topic.");

        inventory.setItem(4, button(Material.NETHER_STAR, "§6" + title, "§7Read it. Then stop inventing deaths."));
        inventory.setItem(DETAIL_BOOK, button(Material.WRITABLE_BOOK, "§e" + title, lore.toArray(String[]::new)));
        inventory.setItem(DETAIL_BACK, button(
                Material.ARROW,
                "§a← Back to topics",
                "§7Pick another. Reluctantly."
        ));
        ChromePage.open(player, inventory, ChromePage.Theme.QUEST);
    }

    private static void fillEmpty(Inventory inventory) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane.clone());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();

        if (holder.mode() == Holder.Mode.DETAIL) {
            if (slot == DETAIL_BACK) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                open(player, holder.speaker());
            }
            return;
        }

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            String who = holder.speaker() == null || holder.speaker().isBlank() ? "Egon" : holder.speaker();
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    who + ": Try not to invent a new way to die.",
                    net.kyori.adventure.text.format.NamedTextColor.GOLD
            ));
            return;
        }
        String[] lines = linesFor(slot);
        if (lines == null) {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        openDetail(player, holder.speaker(), slot);
    }

    /** Topic title → lines, for the in-world help desk (same content as the chest). */
    public static java.util.Map<String, String[]> talkTopics() {
        java.util.Map<String, String[]> out = new java.util.LinkedHashMap<>();
        for (int slot : new int[] {SLOT_MANAGER, SLOT_BOOSTERS, SLOT_PETS, SLOT_SKILLS, SLOT_RECIPES,
                SLOT_MINING, SLOT_STORAGE, SLOT_DUNGEONS, SLOT_TRADER}) {
            String[] lines = linesFor(slot);
            if (lines != null) {
                out.put(topicTitle(slot), lines.clone());
            }
        }
        return out;
    }

    private static String topicTitle(int slot) {
        return switch (slot) {
            case SLOT_MANAGER -> "Journal";
            case SLOT_BOOSTERS -> "Boosters";
            case SLOT_PETS -> "Pets";
            case SLOT_SKILLS -> "Skills";
            case SLOT_RECIPES -> "Recipes & Crafting";
            case SLOT_MINING -> "Mining Power & Fortune";
            case SLOT_STORAGE -> "Storage";
            case SLOT_DUNGEONS -> "Dungeons";
            case SLOT_TRADER -> "Trader / Bazaar / AH";
            default -> "Topic";
        };
    }

    private static String[] linesFor(int slot) {
        return switch (slot) {
            case SLOT_MANAGER -> new String[]{
                    "The book on slot nine isn't jewelry. It's your Journal.",
                    "Open it. Skills, pets, recipes, storage, spawns — all there.",
                    "It unlocks more the further you get. Check it often."
            };
            case SLOT_BOOSTERS -> new String[]{
                    "Boosters are sticky upgrades. Slap them on gear that allows them.",
                    "Core ones push Mining Power, Fortune, Damage, Defense.",
                    "There's a cap. Don't ask me to raise it. Ask someone with a clipboard."
            };
            case SLOT_PETS -> new String[]{
                    "Pets are equipped in your Journal under Pets. One at a time. They add stats. Quietly.",
                    "They level when you play. Dragons get dramatic about it.",
                    "If it has an aura in a dungeon, read the tooltip before you brag."
            };
            case SLOT_SKILLS -> new String[]{
                    "Skills are what you do: Mining, Farming, Foraging, Fishing, Combat. They level by themselves.",
                    "Talents go into a free talent slot: Journal → Skills. Miss Ledger will check.",
                    "One slot is free at the start. More open with your Aetherion Level: 5, 10, 20 and later."
            };
            case SLOT_RECIPES -> new String[]{
                    "The Craftsman at the market opens the Recipe Book for you.",
                    "Journal → Recipes. Every blueprint lives there.",
                    "You already got Egon's Simple Pickaxe. Next page: Mining Pickaxe — pick in the middle, coal around it.",
                    "Gear upgrades: old piece in the middle, resources around it."
            };
            case SLOT_MINING -> new String[]{
                    "Mining Power is a gate. Low power means the ore laughs at you.",
                    "Fortune multiplies drops. More fortune, more paperwork.",
                    "Mining armor is for fortune. Combat armor is for not dying. Don't mix them up."
            };
            case SLOT_STORAGE -> new String[]{
                    "Storage is in your Journal. Extra pages for the pack-rat lifestyle.",
                    "If your inventory looks like a landfill, that's a you problem — and a Storage problem."
            };
            case SLOT_DUNGEONS -> new String[]{
                    "Dungeons are separate. Better gear, louder deaths.",
                    "Dungeon gear is strong inside, modest outside. By design.",
                    "Journal unlocks after you kill something that deserved a plaque."
            };
            case SLOT_TRADER -> new String[]{
                    "Find the trader. Talk to him once.",
                    "That unlocks Bazaar and Auction House in your Journal.",
                    "He buys junk. You buy better junk. Capitalism with sarcasm."
            };
            default -> null;
        };
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() + word.length() + 1 > width) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(de.aetherion.core.api.JournalText.t(name));
            List<String> text = new ArrayList<>(lore.length);
            for (String line : lore) {
                text.add(de.aetherion.core.api.JournalText.t(line));
            }
            meta.setLore(text);
            item.setItemMeta(meta);
        }
        return item;
    }

    public record Holder(Mode mode, int topicSlot, String speaker) implements InventoryHolder {
        public enum Mode { TOPICS, DETAIL }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
