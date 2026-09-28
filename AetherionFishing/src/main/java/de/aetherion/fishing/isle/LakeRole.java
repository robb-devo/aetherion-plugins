package de.aetherion.fishing.isle;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Villager;

import java.util.List;
import java.util.Locale;

/**
 * The Fishing Eldervale cast — four people, one per corner of the lake, so a visit walks the isle.
 * Presets are standable spots measured from the schematic (config {@code isle-cast.presets});
 * every NPC can also be placed anywhere with its DEV anchor.
 */
public enum LakeRole {

    HARBOURMASTER("Maren Hollowtide", "Harbourmaster", "§b", "fisherman", "swamp", Material.FISHING_ROD,
            "Angler card · waters · records",
            List.of(
                    "Welcome to the harbour. Every water on this isle has its own fish — go meet them.",
                    "Keep your streak clean and the water warms up. Boiling water brings the big ones.",
                    "The shoal moves. If you're not chasing it, someone else is.",
                    "When the bells ring, drop what you're doing and fish."
            )),
    BAIT("Tilly Brinewater", "Bait Shack", "§e", "fisherman", "plains", Material.KELP,
            "Turns your catch into bait",
            List.of(
                    "Bait's made of fish. Circle of life, love. Mostly the fish's.",
                    "Glow Grubs after dark. Trust me. Or trust the eels.",
                    "Emperor's Feast is dear because emperors are dear.",
                    "A missed bite still eats the bait. That's not me, that's the fish."
            )),
    TAXIDERMIST("Odile Marsh", "Taxidermist", "§d", "leatherworker", "taiga", Material.TROPICAL_FISH,
            "Angler's Log · buys trophies",
            List.of(
                    "Bring me the heavy ones. I pay by weight, not by excuses.",
                    "Every species in the Log has a hint. Read it. Then go get wet.",
                    "I've mounted a Reed Pike that bit me back. Twice.",
                    "Records are for breaking. Mine included."
            )),
    LAKEWATCHER("Old Finn", "Lakewatcher", "§3", "cartographer", "swamp", Material.SPYGLASS,
            "Shoal · Silver Run · the Eldermaw",
            List.of(
                    "Watch the water. When it boils, that's the shoal.",
                    "Silver Run's the easy one. The Eldermaw — everyone pulls, or nobody eats.",
                    "Rainy night up in the tarns? Something pale swims there. You didn't hear it from me.",
                    "Forty years I've watched this lake. It watches back."
            ));

    private final String display;
    private final String title;
    private final String color;
    private final String profession;
    private final String type;
    private final Material icon;
    private final String role;
    private final List<String> barks;

    LakeRole(String display, String title, String color, String profession, String type,
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

    public String role() {
        return role;
    }

    public List<String> barks() {
        return barks;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** First name for chat prefixes ("Tilly »"). */
    public String shortName() {
        int space = display.indexOf(' ');
        return display.startsWith("Old ") || space < 0 ? display : display.substring(0, space);
    }

    public static LakeRole byId(String id) {
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
