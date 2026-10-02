package de.aetherion.hub.origin;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Villager;

import java.util.List;
import java.util.Locale;

/**
 * The Origin cast: five townsfolk who run the island's own loops (journal, skyways, bells, glowcaps, sky).
 * They are Hub-owned villagers, not Quests NPCs — nobody here talks through the LivingNpc stack, and none of
 * them stand on a Quests NPC's spot. Their spots come from {@code origin.yml → cast.presets}; DEV can move them.
 */
public enum OriginRole {

    GUIDE("Orla Vane", "Origin Journal", "§6", "cartographer", "plains", Material.FILLED_MAP,
            "Journal · tour · districts",
            List.of(
                    "Thirteen districts, one island, and every one of them thinks it's the capital. Only one's right.",
                    "Lost? Good. That's how you find the Starbell Hollow.",
                    "The fountain takes coins. It doesn't give them back. It gives something better, if you're lucky.",
                    "Walk it once, then fly it. Origin looks different from the sky.",
                    "Keep the Journal close. I filled mine twice."
            )),
    KEEPER("Cobb Kettleby", "Skyway Ledger", "§b", "fletcher", "taiga", Material.FEATHER,
            "Skyways · updrafts · glides",
            List.of(
                    "Updraft's behind me. Step in, keep your arms in, don't argue with the wind.",
                    "Four glides and two updrafts on this rock. I've ridden every one of them twice. Once on purpose.",
                    "Sealed pads open when the Surveyor stamps your blueprint. I just keep the ledger.",
                    "The Summit Glide lands you in the Capital. Mostly on your feet.",
                    "Sprinting on a glide? Brave. Pointless, but brave."
            )),
    BELLKEEPER("Sister Aurel", "Keeper of the Seven Bells", "§e", "cleric", "snow", Material.BELL,
            "The Seven Bells · the old hymn",
            List.of(
                    "Seven bells, seven notes. The old hymn only works if you ring them all.",
                    "The high bell in the wharf tower? Arrows, dear. Nobody climbs that.",
                    "Dawn gets three strokes, dusk gets five. The island counts, even if you don't.",
                    "Every bell on Origin was cast for someone. Most of them have forgotten who.",
                    "Hear that hum after dark? Starbells. They answer the bells, if you ask me."
            )),
    TENDER("Fen Glowmoor", "Glowcap Tender", "§9", "farmer", "swamp", Material.GLOW_BERRIES,
            "Glowcap waystones · travel",
            List.of(
                    "Touch a glowcap once and it remembers you. Touch two and they gossip.",
                    "Six grown caps and two sprouts. I planted the sprouts. Don't step on them.",
                    "Right-click any cap you've met and it'll put you by any other. Mushrooms are generous like that.",
                    "They glow brighter at night. So do I, frankly.",
                    "The Northwild cap is the loneliest. Go say hello."
            )),
    STARGAZER("Stellan Voss", "Summit Stargazer", "§d", "librarian", "savanna", Material.SPYGLASS,
            "Vistas · the night sky · wishes",
            List.of(
                    "Stand on a high place and the island names itself. Five of them. Find them.",
                    "Falling stars on Origin are rare. When you see one, sneak and look up. Make it count.",
                    "The aurora comes when it likes. Mostly when you're indoors.",
                    "From here you can see the Borderlands. From there you can't see anything nice.",
                    "The glide down is quicker than the stairs. Also louder."
            ));

    private final String display;
    private final String title;
    private final String color;
    private final String profession;
    private final String type;
    private final Material icon;
    private final String role;
    private final List<String> barks;

    OriginRole(String display, String title, String color, String profession, String type,
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

    public String shortName() {
        int space = display.indexOf(' ');
        return display.startsWith("Sister ") || space < 0 ? display : display.substring(0, space);
    }

    public static OriginRole byId(String id) {
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
