package de.aetherion.items.lore;

import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.item.ItemPresentation;
import de.aetherion.items.item.LoreLocale;
import de.aetherion.items.model.Rarity;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Story finds for "Das Schlaflied": lore items with no stats, readable books and the Queen's
 * letters. Each one is also a Chronicle page (page id = {@link #findId()}).
 * <p>
 * English only. Quests decides when a find is granted; this class only builds the stacks and
 * the Chronicle text. Every stack carries {@code aetherion:item}, {@code aetherion:lore_find}
 * and {@code aetherion:find_id}.
 */
public enum StoryLore {

    BOOK_MAD_KING("lore_book_mad_king", "book_mad_king", Material.WRITTEN_BOOK, "The Mad King", Rarity.COMMON,
            "Approved by the Registry. Scarier than it needs to be.",
            "Mother Berit's old copy.",
            List.of("§eRight-click §7to read."),
            List.of("§7A children's book from the Capital.",
                    "§7The king has a crown of lightning",
                    "§7and no face at all."),
            "Mother Berit gave you her copy."),

    MUSIC_BOX_GEAR("lore_music_box_gear", "music_box_gear", Material.CLOCK, "Music Box Gear", Rarity.RARE,
            "It still turns when no one is looking.",
            "Deep in the Shabby Mine.",
            List.of("§7Ysolde Thorne at the Archive wants this."),
            List.of("§7Brass, hand-cut, older than the Registry.",
                    "§7Something in the mine hummed",
                    "§7a lullaby around it."),
            "The Hollow Lurker guarded it."),

    SUN_GUARD_INSIGNIA("lore_sun_guard_insignia", "sun_guard_insignia", Material.GOLD_NUGGET, "Sun Guard Insignia", Rarity.RARE,
            "Polished for a thousand years. Never worn again.",
            "Taken from Skuldugery's coat.",
            List.of("§7Ysolde Thorne at the Archive wants this."),
            List.of("§7The Sun Guard was real.",
                    "§7Somebody kept this badge bright",
                    "§7and never pinned it on again."),
            "Skuldugery wore it under the soot."),

    TINY_PAPER_CROWN("lore_tiny_paper_coronet", "tiny_paper_crown", Material.PAPER, "Tiny Paper Crown", Rarity.UNCOMMON,
            "Folded by small hands. Sized for a hen.",
            "Fell off McNugget.",
            List.of(),
            List.of("§7A paper crown, folded with care.",
                    "§7McNugget wore it into battle.",
                    "§7Nobody knows whose hands made it."),
            "McNugget dropped it."),

    ANCHOR_CHAIN_LINK("lore_anchor_chain_link", "anchor_chain_link", Material.CHAIN, "Anchor Chain Link", Rarity.UNCOMMON,
            "Too heavy for a troll. He carried it anyway.",
            "From the Bridge Troll's toll chain.",
            List.of(),
            List.of("§7One link of a mooring chain,",
                    "§7as thick as your arm.",
                    "§7The troll called it his."),
            "The Bridge Troll hoarded it."),

    MAELIS_LETTER_1("lore_maelis_letter_1", "maelis_letter_1", Material.WRITTEN_BOOK, "Letter Signed 'M' · I", Rarity.UNCOMMON,
            "Folded small. Smells faintly of lamp oil.",
            "Found in a world chest.",
            List.of("§eRight-click §7to read.", "§7Ysolde Thorne collects these."),
            List.of("§7A song for a child",
                    "§7who was afraid of the dark."),
            "Found in a world chest."),

    MAELIS_LETTER_2("lore_maelis_letter_2", "maelis_letter_2", Material.WRITTEN_BOOK, "Letter Signed 'M' · II", Rarity.UNCOMMON,
            "Someone pressed a star into the wax.",
            "Found in a world chest.",
            List.of("§eRight-click §7to read.", "§7Ysolde Thorne collects these."),
            List.of("§7Lamps over the harbour.",
                    "§7A child asleep against the glass."),
            "Found in a world chest."),

    MAELIS_LETTER_3("lore_maelis_letter_3", "maelis_letter_3", Material.WRITTEN_BOOK, "Letter Signed 'M' · III", Rarity.UNCOMMON,
            "The ink ran where something dripped on it.",
            "Found in a world chest.",
            List.of("§eRight-click §7to read.", "§7Ysolde Thorne collects these."),
            List.of("§7The outer isles began to forget.",
                    "§7'Finish the last verse with me.'"),
            "Found in a world chest."),

    SEALED_WRIT("lore_sealed_writ_silent_year", "sealed_writ_silent_year", Material.WRITTEN_BOOK,
            "Sealed Writ of the Silent Year", Rarity.EPIC,
            "The wax is a thousand years old. The order isn't.",
            "Carried by the Pathwarden.",
            List.of("§eRight-click §7to read.", "§7Ysolde Thorne at the Archive wants this."),
            List.of("§7'Let no one walk to him.'",
                    "§7'Let no one walk from him.'",
                    "§7One signature reads: Thorne."),
            "The Pathwarden kept it for a thousand years.");

    /** PDC marker: this stack is a story find. */
    public static final NamespacedKey LORE_FIND = new NamespacedKey("aetherion", "lore_find");
    /** PDC: the find / Chronicle page id. */
    public static final NamespacedKey FIND_ID = new NamespacedKey("aetherion", "find_id");

    private final String itemId;
    private final String findId;
    private final Material material;
    private final String name;
    private final Rarity rarity;
    private final String flavor;
    private final String origin;
    private final List<String> use;
    private final List<String> chronicle;
    private final String foundAt;

    StoryLore(String itemId, String findId, Material material, String name, Rarity rarity,
              String flavor, String origin, List<String> use, List<String> chronicle, String foundAt) {
        this.itemId = itemId;
        this.findId = findId;
        this.material = material;
        this.name = name;
        this.rarity = rarity;
        this.flavor = flavor;
        this.origin = origin;
        this.use = use;
        this.chronicle = chronicle;
        this.foundAt = foundAt;
    }

    public String itemId() {
        return itemId;
    }

    /** Chronicle page id. */
    public String findId() {
        return findId;
    }

    public Material material() {
        return material;
    }

    public String displayName() {
        return name;
    }

    public Rarity rarity() {
        return rarity;
    }

    /** Short Chronicle text (already colored). */
    public List<String> chronicle() {
        return chronicle;
    }

    public String foundAt() {
        return foundAt;
    }

    /** Chronicle text in the requested language (English when a line has no German twin). */
    public List<String> chronicle(boolean german) {
        List<String> out = new ArrayList<>(chronicle.size());
        for (String line : chronicle) {
            out.add(LoreLocale.translateLine(line, german));
        }
        return out;
    }

    public String foundAt(boolean german) {
        return LoreLocale.translateLine(foundAt, german);
    }

    public boolean readable() {
        return material == Material.WRITTEN_BOOK;
    }

    /** Fresh stack for this find. */
    public ItemStack create() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(ItemKeys.item(), PersistentDataType.STRING, itemId);
        meta.getPersistentDataContainer().set(ItemKeys.rarity(), PersistentDataType.STRING, rarity.name());
        meta.getPersistentDataContainer().set(LORE_FIND, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(FIND_ID, PersistentDataType.STRING, findId);
        if (meta instanceof BookMeta book) {
            book.setTitle(name);
            book.setAuthor(authorEnglish());
            book.setGeneration(BookMeta.Generation.ORIGINAL);
            book.setPages(StoryBooks.pages(findId));
        }
        meta.setDisplayName(rarity.getChatColor() + name);
        List<String> lore = new ArrayList<>();
        lore.add("§7§o" + flavor);
        lore.add("§8§o" + origin);
        if (!use.isEmpty()) {
            lore.add("");
            lore.addAll(use);
        }
        lore.add("");
        lore.add("§8Story find · a page in your Chronicle");
        meta.setLore(lore);
        meta.setMaxStackSize(1);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        ItemPresentation.polish(meta);
        item.setItemMeta(meta);
        return item;
    }

    public String authorEnglish() {
        return switch (this) {
            case BOOK_MAD_KING -> "The Registry";
            case SEALED_WRIT -> "The Council";
            default -> "M";
        };
    }

    /** By item id ({@code lore_*}) or find id; null when unknown. */
    public static StoryLore byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (StoryLore lore : values()) {
            if (lore.itemId.equals(key) || lore.findId.equals(key)) {
                return lore;
            }
        }
        return null;
    }

    /** Fresh stack by item id or find id; null when unknown. */
    public static ItemStack create(String id) {
        StoryLore lore = byId(id);
        return lore == null ? null : lore.create();
    }

    /** Find id carried by a stack, or null. */
    public static String findIdOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(FIND_ID, PersistentDataType.STRING);
    }

    public static boolean isLoreFind(ItemStack stack) {
        return findIdOf(stack) != null;
    }
}
