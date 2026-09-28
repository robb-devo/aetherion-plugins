package de.aetherion.farming.isle;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Villager;

import java.util.List;
import java.util.Locale;

/**
 * The Eldervale cast. Presets are door-front spots measured from the schematic (see config
 * {@code isle-cast.presets}); every NPC can also be placed anywhere with its DEV anchor.
 */
public enum IsleRole {

    WARDEN("Elsie Thatch", "Field Warden", "§a", "farmer", "plains", Material.WHEAT,
            "Your isle board, tours and tips",
            List.of(
                    "Welcome to Eldervale! Ask me anything — or just start pulling carrots.",
                    "Keep your rhythm up in the rows. The field sings if you do it right.",
                    "Every corner of this island has a name. Go find them all.",
                    "If a crop jumps out of the ground at you, grab it. Trust me."
            )),
    CLERK("Hattie Sowerby", "Harvest Orders", "§6", "cartographer", "savanna", Material.PAPER,
            "Orders board · sells prize crops",
            List.of(
                    "Fresh orders on the board, love. Coin's better than the trader's.",
                    "Got a prize crop? I'll pay for the weight.",
                    "The Oven House keeps ordering wheat. Bless them.",
                    "Deliver three and I'll pin up three more."
            )),
    BAKER("Bram Loafwright", "Oven House Baker", "§e", "butcher", "plains", Material.BREAD,
            "Bakes crops into farming food",
            List.of(
                    "Bring me crops, I'll bring you a reason to keep harvesting.",
                    "A Farmhand's Loaf before the rows. Doctor's orders. I'm not a doctor.",
                    "Golden Harvest Pie needs a prize crop. No, you can't use a normal carrot.",
                    "The ovens have been warm since the island was pasted. Don't ask."
            )),
    GRANARY("Gus Tallybarrel", "Granary Keeper", "§b", "librarian", "taiga", Material.BOOK,
            "Crop Mastery ledger",
            List.of(
                    "Every carrot you pull goes in my ledger. Every single one.",
                    "Mastery pays forever. Crop Fortune doesn't wear off.",
                    "Harvests on Eldervale count double in my book.",
                    "Pumpkins? Not here. But I still count them if you bring the numbers."
            )),
    BEEKEEPER("Old Wren", "Beekeeper", "§e", "shepherd", "swamp", Material.HONEYCOMB,
            "Isle events · Bee Bloom · Harvest Moon",
            List.of(
                    "The hives get restless every twenty minutes or so. Watch the fields.",
                    "When the bees bloom a field, harvest there. Double the fun.",
                    "Harvest Moon nights, the ground gives up its prizes.",
                    "This lodge is built out of beehives. The bees are fine with it. Mostly."
            )),
    PIP("Pip", "Farmhand", "§a", "farmer", "savanna", Material.WOODEN_HOE,
            "Tips from the rows",
            List.of(
                    "Harvest spread breaks the neighbours too. Wide rows, wide smile.",
                    "The crows bully the new folk. Click them twice if they're bold.",
                    "I found a prize potato once. Sold it. Regret it.",
                    "The beet terraces up north get the morning sun."
            )),
    MARTA("Marta", "Farmhand", "§a", "farmer", "taiga", Material.HAY_BLOCK,
            "Tips from the rows",
            List.of(
                    "Southfield's quiet. That's how I like it.",
                    "Don't stop between rows. The rhythm drains if you dawdle.",
                    "The Harvest Hall has the Warden. She knows everything.",
                    "Hattie pays more than the trader. Always check the board first."
            )),
    TOBIAS("Tobias", "Scarecrow Tinker", "§7", "fletcher", "plains", Material.CARVED_PUMPKIN,
            "Birds, scarecrows, Golden Hour",
            List.of(
                    "Scarecrows draw the flocks. Clear the wave and you get Golden Hour.",
                    "Bird Law — a skill for people who are tired of arguing with crows.",
                    "Golden Hour stacks with your gear. Plan your harvest around it.",
                    "I built every scarecrow on this island. Some of them look like me."
            ));

    private final String display;
    private final String title;
    private final String color;
    /** Registry keys, resolved at spawn — keeps the enum loadable without a server. */
    private final String profession;
    private final String type;
    private final Material icon;
    private final String role;
    private final List<String> barks;

    IsleRole(String display, String title, String color, String profession, String type,
             Material icon, String role, List<String> barks) {
        this.display = display;
        this.title = title;
        this.color = color;
        this.profession = profession;
        this.type = type;
        this.icon = icon;
        this.role = role;
        this.barks = barks;
    }

    public String display() {
        return display;
    }

    public String title() {
        return title;
    }

    public String color() {
        return color;
    }

    public Villager.Profession profession() {
        Villager.Profession found = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(profession));
        return found != null ? found : Villager.Profession.NITWIT;
    }

    public Villager.Type villagerType() {
        Villager.Type found = Registry.VILLAGER_TYPE.get(NamespacedKey.minecraft(type));
        return found != null ? found : Villager.Type.PLAINS;
    }

    public Material icon() {
        return icon;
    }

    /** What the NPC does, for DEV lore and hologram hints. */
    public String role() {
        return role;
    }

    public List<String> barks() {
        return barks;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Short first name for chat prefixes ("Hattie »"). */
    public String shortName() {
        int space = display.indexOf(' ');
        return display.startsWith("Old ") || space < 0 ? display : display.substring(0, space);
    }

    public boolean flavorOnly() {
        return this == PIP || this == MARTA || this == TOBIAS;
    }

    public static IsleRole byId(String id) {
        if (id == null) {
            return null;
        }
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
