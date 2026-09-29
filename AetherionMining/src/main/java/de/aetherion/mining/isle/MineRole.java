package de.aetherion.mining.isle;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Villager;

import java.util.List;
import java.util.Locale;

/**
 * The Mining Eldervale cast. There are six of them, spread from the Landing down to the Last Lamp,
 * so learning the isle means walking it top to bottom. Preset spots were measured from the schematic
 * (config {@code mine-cast.presets}). Each NPC can also be moved with its DEV anchor.
 *
 * <p>None of them replace the Quests NPCs already on the isle (Forgehand, Surveyor, Ore Ledger,
 * the Eldervale welcome). Those keep their dialogue. This cast only runs the Mining loops.
 */
public enum MineRole {

    LAMPWARDEN("Old Wick", "Lampwarden", "§e", "cartographer", "taiga", Material.LANTERN,
            "Mine Compass · districts · depth card",
            List.of(
                    "Every lamp on this rock is mine. Every dark bit is yours.",
                    "Seven quarries, four depths, one old man with a ladder. Ask me where.",
                    "The Undercroft's lit by nothing. Take a lantern in your off-hand or learn the walls by feel.",
                    "Heard a bell? That's the mountain. Heard two? Run.",
                    "They call it Glimmerwater because nobody could spell the old name."
            )),
    CLERK("Otto Brassbuckle", "Contract Office", "§6", "librarian", "plains", Material.WRITABLE_BOOK,
            "Foreman Contracts · shifts · rerolls",
            List.of(
                    "Three jobs, one stamp, no refunds. Sign here.",
                    "Deep shifts pay better. Deep shifts also have fewer ceilings.",
                    "The Mint wants gold. The Mint always wants gold.",
                    "Hand it in with me. The Foreman doesn't do paperwork. The Foreman does shouting.",
                    "Your Union Card is the only thing in this office that's worth more than the desk."
            )),
    COOK("Nan Coalbright", "The Hearth", "§c", "butcher", "snow", Material.BAKED_POTATO,
            "Miner's rations · mining-only buffs",
            List.of(
                    "Eat before you go down. Nobody's ever mined well on an empty stomach and a full head.",
                    "I take payment in ore. Coins taste of pockets.",
                    "Pasty's for the long haul. Coffee's for the rhythm. The broth is for bragging.",
                    "One ration at a time, love. Your stomach's not a sack.",
                    "Bring me a specimen and I'll make you something you'll tell your grandchildren about."
            )),
    ASSAYER("Ilse Veyne", "Assay Office", "§d", "librarian", "savanna", Material.AMETHYST_SHARD,
            "Specimen Cabinet · collections · records",
            List.of(
                    "Carats don't lie. Miners do. Put it on the scale.",
                    "A Perfect grade is rarer than an honest foreman.",
                    "Heartstones only grow in the Amethyst Mine. The mountain keeps the best in the basement.",
                    "Fill a row and the whole seam starts paying you respect.",
                    "The record board's up there. Your name isn't. Yet."
            )),
    FORGEMASTER("Brann Emberlock", "The Deep Forge", "§6", "weaponsmith", "desert", Material.ANVIL,
            "Forge Works · marks · reputation",
            List.of(
                    "Ore's just rock that hasn't met me yet.",
                    "Temper the head, trust the swing. The Forgehand upstairs does the fancy tools, I do the honest ones.",
                    "Reputation here isn't bought. It's hammered.",
                    "Every Forge Mark stays with you. The steel forgets, the smith doesn't.",
                    "Heat Ward before the Emberseam. I'm not scraping you off the walls again."
            )),
    HERMIT("Hollis Underhill", "Last Lamp", "§3", "mason", "swamp", Material.SOUL_LANTERN,
            "Depth records · deep trade · Stonejaw bounty",
            List.of(
                    "You made it down. Most turn back at the Fangs.",
                    "Down here the rock listens. Mine loud and something answers.",
                    "Stonejaw's out there. Big as a cart, twice as rude. Bring me its jaw.",
                    "Flares and canaries, fair price, paid in ore. Nobody down here takes coins seriously.",
                    "The lower you mine, the better it pays. The walls know it too."
            ));

    private final String display;
    private final String title;
    private final String color;
    private final String profession;
    private final String type;
    private final Material icon;
    private final String role;
    private final List<String> barks;

    MineRole(String display, String title, String color, String profession, String type,
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

    /** First name for chat prefixes ("Otto »", "Old Wick »"). */
    public String shortName() {
        int space = display.indexOf(' ');
        return display.startsWith("Old ") || space < 0 ? display : display.substring(0, space);
    }

    public static MineRole byId(String id) {
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
