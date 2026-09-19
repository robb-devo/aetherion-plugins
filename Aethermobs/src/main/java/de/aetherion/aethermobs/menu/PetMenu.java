package de.aetherion.aethermobs.menu;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.pet.ActivePetManager;
import de.aetherion.aethermobs.pet.PetDefinition;
import de.aetherion.aethermobs.pet.PetFlavor;
import de.aetherion.aethermobs.pet.PetHabitat;
import de.aetherion.aethermobs.pet.PetHead;
import de.aetherion.aethermobs.pet.PetInstance;
import de.aetherion.aethermobs.pet.PetSpawnType;
import de.aetherion.aethermobs.pet.PlayerPetCollection;
import de.aetherion.aethermobs.pet.skill.PetSkill;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.model.Rarity;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PetMenu implements Listener {

    private static final int INVENTORY_SIZE = 54;
    private static final int PET_SLOTS = 44;

    private static final String TITLE =
            "§5§l✦ YOUR PETS ✦";

    private static final String DETAILS_TITLE =
            "§5§l✦ PET DETAILS ✦";

    private static final String AETHERLEX_LANDS_TITLE =
            "§5§l✦ AETHERLEX ✦";

    private static final String AETHERLEX_DEPTHS_TITLE =
            "§5§l✦ AETHERLEX · II ✦";

    private static final String AETHERLEX_SKY_TITLE =
            "§5§l✦ AETHERLEX · SKY ✦";

    private static final String AETHERLEX_NETHER_TITLE =
            "§5§l✦ AETHERLEX · NETHER ✦";

    private static final String AETHERLEX_DUNGEON_TITLE =
            "§5§l✦ AETHERLEX · DUNGEON ✦";

    private static final int MANAGER_BACK_SLOT = 45;
    private static final int COLLECTION_TAB_SLOT = 46;
    private static final int AETHERLEX_LANDS_TAB_SLOT = 47;
    private static final int AETHERLEX_DEPTHS_TAB_SLOT = 48;
    private static final int AETHERLEX_SKY_TAB_SLOT = 49;
    private static final int AETHERLEX_NETHER_TAB_SLOT = 50;
    private static final int AETHERLEX_DUNGEON_TAB_SLOT = 51;
    private static final int EQUIPPED_SLOT = 52;
    /** Shared with manager back: previous page when browsing collection page 2+. */
    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 53;

    /*
     * Details layout:
     *
     * Row 1: empty
     * Row 2: pet
     * Row 3: empty
     * Row 4: actions
     * Row 5: empty
     * Row 6: empty
     */

    private static final int DETAILS_PET_SLOT = 13;

    private static final int EQUIP_SLOT = 29;
    private static final int RELEASE_SLOT = 33;

    private static final int BACK_SLOT = 45;

    private final AetherMobs plugin;

    private final NamespacedKey petIndexKey;

    private final NamespacedKey pageKey;

    public PetMenu(
            AetherMobs plugin
    ) {

        this.plugin = plugin;

        this.petIndexKey =
                new NamespacedKey(
                        plugin,
                        "menu_pet_index"
                );

        this.pageKey =
                new NamespacedKey(
                        plugin,
                        "menu_page"
                );
    }

    public void open(
            Player player
    ) {

        open(
                player,
                0
        );
    }

    public void open(
            Player player,
            int page
    ) {

        PlayerPetCollection collection =
                plugin.getPetCollection(
                        player
                );

        List<PetInstance> pets =
                collection.getPets();

        int contentPages =
                Math.max(
                        1,
                        (int) Math.ceil(
                                pets.size()
                                        / (double) PET_SLOTS
                        )
                );

        int totalPages =
                Math.max(
                        2,
                        contentPages
                );

        page =
                Math.max(
                        0,
                        Math.min(
                                page,
                                totalPages - 1
                        )
                );

        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        INVENTORY_SIZE,
                        TITLE
                );

        fillBackground(
                inventory
        );

        applyStorageTabs(
                inventory,
                player,
                StoragePage.COLLECTION,
                page,
                totalPages
        );

        int startIndex =
                page * PET_SLOTS;

        int endIndex =
                Math.min(
                        startIndex + PET_SLOTS,
                        pets.size()
                );

        for (
                int index = startIndex;
                index < endIndex;
                index++
        ) {

            PetInstance pet =
                    pets.get(index);

            int slot =
                    index - startIndex;

            inventory.setItem(
                    slot,
                    createPetItem(
                            pet,
                            collection.isEquipped(
                                    pet
                            ),
                            index
                    )
            );
        }

        player.openInventory(
                inventory
        );
    }

    public void openAetherlex(
            Player player
    ) {

        openAetherlex(
                player,
                0
        );
    }

    public void openAetherlex(
            Player player,
            int lexiconPage
    ) {

        StoragePage page = switch (lexiconPage) {
            case 1 -> StoragePage.AETHERLEX_DEPTHS;
            case 2 -> StoragePage.AETHERLEX_SKY;
            case 3 -> StoragePage.AETHERLEX_NETHER;
            case 4 -> StoragePage.AETHERLEX_DUNGEON;
            default -> StoragePage.AETHERLEX_LANDS;
        };

        List<PetDefinition> entries =
                getAetherlexPets(
                        page
                );

        String title =
                switch (page) {

                    case AETHERLEX_DEPTHS ->
                            AETHERLEX_DEPTHS_TITLE;

                    case AETHERLEX_SKY ->
                            AETHERLEX_SKY_TITLE;

                    case AETHERLEX_NETHER ->
                            AETHERLEX_NETHER_TITLE;

                    case AETHERLEX_DUNGEON ->
                            AETHERLEX_DUNGEON_TITLE;

                    default ->
                            AETHERLEX_LANDS_TITLE;
                };

        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        INVENTORY_SIZE,
                        title
                );

        fillBackground(
                inventory
        );

        applyStorageTabs(
                inventory,
                player,
                page,
                0,
                1
        );

        PlayerPetCollection collection =
                plugin.getPetCollection(
                        player
                );

        for (int index = 0;
             index < entries.size()
                     && index < PET_SLOTS;
             index++) {

            inventory.setItem(
                    index,
                    createAetherlexItem(
                            entries.get(index),
                            collection
                    )
            );
        }

        player.openInventory(
                inventory
        );
    }

    private void openDetails(
            Player player,
            PetInstance pet,
            int petIndex
    ) {

        PlayerPetCollection collection =
                plugin.getPetCollection(
                        player
                );

        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        INVENTORY_SIZE,
                        DETAILS_TITLE
                );

        /*
         * =====================================================
         * PET
         * =====================================================
         */

        inventory.setItem(
                DETAILS_PET_SLOT,
                createPetItem(
                        pet,
                        collection.isEquipped(
                                pet
                        ),
                        petIndex
                )
        );

        /*
         * =====================================================
         * ACTIONS
         * =====================================================
         */

        if (collection.isEquipped(pet)) {

            inventory.setItem(
                    EQUIP_SLOT,
                    createActionItem(
                            Material.REDSTONE_BLOCK,
                            "§c§lUnequip Pet",
                            "§7Remove this pet from",
                            "§7your active pet slot."
                    )
            );

        } else {

            inventory.setItem(
                    EQUIP_SLOT,
                    createActionItem(
                            Material.EMERALD_BLOCK,
                            "§a§lEquip Pet",
                            "§7Equip this pet.",
                            "",
                            "§7Your current pet will",
                            "§7automatically be replaced."
                    )
            );
        }

        inventory.setItem(
                RELEASE_SLOT,
                createActionItem(
                        Material.BARRIER,
                        "§c§lRelease Pet",
                        "§7Permanently release this pet.",
                        "",
                        "§cThis cannot be undone."
                )
        );

        /*
         * =====================================================
         * BACK
         * =====================================================
         */

        inventory.setItem(
                BACK_SLOT,
                createActionItem(
                        Material.ARROW,
                        "§e§lBack",
                        "§7Return to your collection."
                )
        );

        player.openInventory(
                inventory
        );
    }

    private ItemStack createPetItem(
            PetInstance pet,
            boolean equipped,
            int petIndex
    ) {

        ItemStack item =
                PetHead.create(
                        pet
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return item;
        }

        String rarityColor =
                getRarityColor(
                        pet.getRarity()
                );

        String petName =
                pet.getDefinition()
                        .getDisplayName();

        meta.setDisplayName(
                equipped
                        ? "§a✔ "
                        + rarityColor
                        + "§l"
                        + petName
                        : rarityColor
                        + "§l"
                        + petName
        );

        if (equipped) {
            meta.setEnchantmentGlintOverride(true);
        }

        List<String> lore =
                new ArrayList<>();

        if (equipped) {

            lore.add(
                    "§a§lEQUIPPED"
            );

            lore.add("");
        }

        /*
         * =====================================================
         * BASIC PET INFORMATION
         * =====================================================
         */

        lore.add(
                "§7Rarity: "
                        + rarityColor
                        + formatRarity(
                        pet.getRarity()
                )
        );

        lore.add(
                "§7Variant: §f"
                        + formatVariant(
                        pet.getVariant()
                )
        );

        lore.add(
                "§7Level: §f"
                        + pet.getLevel()
        );

        /*
         * =====================================================
         * EXPERIENCE
         * =====================================================
         */

        lore.add("");

        lore.add(
                "§d§lExperience"
        );

        lore.add(
                "§7"
                        + createExperienceBar(
                        pet
                )
        );

        lore.add(
                pet.getLevel() >= pet.getMaxLevel()
                        ? "§6MAX"
                        : "§7XP: §f"
                        + pet.getExperience()
                        + " §7/ §f"
                        + pet.getRequiredExperience(pet.getLevel())
        );

        lore.add(
                "§7Progress: §f"
                        + formatPercentage(
                        pet.getExperienceProgress()
                )
        );

        if (pet.getDefinition() != null
                && "hacker".equalsIgnoreCase(pet.getDefinition().getId())) {
            lore.add("");
            lore.add(
                    PetFlavor.glitchLine()
            );
        }

        /*
         * =====================================================
         * CORE STAT
         * =====================================================
         */

        lore.add("");

        if (pet.getDefinition() != null
                && pet.getDefinition().isPercentBonus()) {

            lore.add(
                    "§6§lDungeon Aura"
            );

            lore.add(
                    "§f+"
                            + formatValue(
                            pet.getStats()
                                    .getScaledCoreValue(
                                            pet.getLevel()
                                    )
                    )
                            + "% "
                            + dungeonAuraName(
                            pet.getDefinition()
                                    .getDungeonAura()
                    )
            );

            lore.add(
                    "§8Only active inside dungeons"
            );

        } else {

        lore.add(
                "§6§lCore Stat"
        );

        lore.add(
                "§7"
                        + formatCapability(
                        pet.getStats()
                                .getCoreStat()
                )
                        + ": §f"
                        + formatValue(
                        pet.getStats()
                                .getScaledCoreValue(
                                        pet.getLevel()
                                )
                )
        );

        }

        /*
         * =====================================================
         * BONUS STATS
         * =====================================================
         */

        if (!pet.getStats()
                .getBonusStats()
                .isEmpty()) {

            lore.add("");

            lore.add(
                    "§6§lBonus Stats"
            );

            for (
                    var entry :
                    pet.getStats()
                            .getBonusStats()
                            .entrySet()
            ) {

                lore.add(
                        "§7"
                                + formatCapability(
                                entry.getKey()
                        )
                                + ": §f"
                                + formatValue(
                                pet.getStats()
                                        .getScaledBonusStat(
                                                entry.getKey(),
                                                pet.getLevel()
                                        )
                        )
                );
            }
        }

        PetSkill skill =
                PetSkill.fromPet(
                        pet
                );

        if (skill != PetSkill.NONE) {

            lore.add("");

            lore.add(
                    "§b§lSkill"
            );

            lore.add(
                    "§f"
                            + skill.getDisplayName()
            );

            lore.add(
                    "§7"
                            + skill.describe(
                            pet
                    )
            );
        }

        lore.add("");

        if (equipped) {

            lore.add(
                    "§7Click to manage this pet."
            );

        } else {

            lore.add(
                    "§eClick to view this pet."
            );
        }

        meta.setLore(
                lore
        );

        meta.getPersistentDataContainer().set(
                petIndexKey,
                PersistentDataType.INTEGER,
                petIndex
        );

        item.setItemMeta(
                meta
        );

        return item;
    }

    private String createExperienceBar(
            PetInstance pet
    ) {

        int totalBars = 20;

        double progress =
                Math.max(
                        0.0,
                        Math.min(
                                1.0,
                                pet.getExperienceProgress()
                        )
                );

        int filledBars =
                (int) Math.floor(
                        progress * totalBars
                );

        StringBuilder bar =
                new StringBuilder();

        for (
                int i = 0;
                i < totalBars;
                i++
        ) {

            if (i < filledBars) {

                bar.append("§a█");

            } else {

                bar.append("§8█");
            }
        }

        return bar.toString();
    }

    private String formatPercentage(
            double progress
    ) {

        return String.format(
                "%.1f%%",
                progress * 100.0
        );
    }

    private ItemStack createActionItem(
            Material material,
            String name,
            String... loreLines
    ) {

        ItemStack item =
                new ItemStack(
                        material
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.setDisplayName(
                name
        );

        meta.setLore(
                List.of(
                        loreLines
                )
        );

        item.setItemMeta(
                meta
        );

        return item;
    }

    private ItemStack createNavigationItem(
            Material material,
            String name,
            String loreText,
            int page
    ) {

        ItemStack item =
                createActionItem(
                        material,
                        name,
                        loreText
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            meta.getPersistentDataContainer().set(
                    pageKey,
                    PersistentDataType.INTEGER,
                    page
            );

            item.setItemMeta(
                    meta
            );
        }

        return item;
    }

    private void fillBackground(
            Inventory inventory
    ) {

        ItemStack filler =
                new ItemStack(
                        Material.GRAY_STAINED_GLASS_PANE
                );

        ItemMeta meta =
                filler.getItemMeta();

        if (meta != null) {

            meta.setDisplayName(
                    " "
            );

            filler.setItemMeta(
                    meta
            );
        }

        for (int slot = 45; slot < 54; slot++) {

            inventory.setItem(
                    slot,
                    filler
            );
        }
    }

    private enum StoragePage {
        COLLECTION,
        AETHERLEX_LANDS,
        AETHERLEX_DEPTHS,
        AETHERLEX_SKY,
        AETHERLEX_NETHER,
        AETHERLEX_DUNGEON
    }

    private void applyStorageTabs(
            Inventory inventory,
            Player player,
            StoragePage currentPage,
            int collectionPage,
            int totalPages
    ) {

        PlayerPetCollection collection =
                plugin.getPetCollection(
                        player
                );

        PetInstance equipped =
                collection == null
                        ? null
                        : collection.getEquippedPet();

        String equippedLine =
                equipped == null
                        ? "§8No pet equipped."
                        : "§7Active: §a"
                        + equipped.getDefinition().getDisplayName();

        inventory.setItem(
                COLLECTION_TAB_SLOT,
                createTabItem(
                        Material.CHEST,
                        "§d§lYour Pets",
                        currentPage == StoragePage.COLLECTION,
                        "§7Open your caught pets.",
                        "§7Equip or release them here.",
                        "",
                        equippedLine
                )
        );

        inventory.setItem(
                AETHERLEX_LANDS_TAB_SLOT,
                createTabItem(
                        Material.BOOK,
                        "§b§lAetherlex",
                        currentPage == StoragePage.AETHERLEX_LANDS,
                        "§7Lands and caves.",
                        "§7See every known surface",
                        "§7and cave pet."
                )
        );

        inventory.setItem(
                AETHERLEX_DEPTHS_TAB_SLOT,
                createTabItem(
                        Material.HEART_OF_THE_SEA,
                        "§b§lAetherlex II",
                        currentPage == StoragePage.AETHERLEX_DEPTHS,
                        "§7Oceans and Aetherion.",
                        "§7Aquatic pets plus the",
                        "§7last dragon."
                )
        );

        inventory.setItem(
                AETHERLEX_SKY_TAB_SLOT,
                createTabItem(
                        Material.FEATHER,
                        "§b§lAetherlex Sky",
                        currentPage == StoragePage.AETHERLEX_SKY,
                        "§7Open sky.",
                        "§7See every flying pet."
                )
        );

        inventory.setItem(
                AETHERLEX_NETHER_TAB_SLOT,
                createTabItem(
                        Material.NETHERRACK,
                        "§c§lAetherlex Nether",
                        currentPage == StoragePage.AETHERLEX_NETHER,
                        "§7The Nether.",
                        "§7Wither and nether pets."
                )
        );

        inventory.setItem(
                AETHERLEX_DUNGEON_TAB_SLOT,
                createTabItem(
                        Material.DEEPSLATE_BRICKS,
                        "§8§lAetherlex Dungeon",
                        currentPage == StoragePage.AETHERLEX_DUNGEON,
                        "§7Dungeon floors.",
                        "§7Pets that only appear",
                        "§7inside dungeon instances."
                )
        );

        inventory.setItem(
                EQUIPPED_SLOT,
                createEquippedSlotItem(collection)
        );

        /*
         * Collection page 2+: left arrow is Previous Page.
         * Page 1 / Aetherlex: left arrow returns to Aetherion Manager.
         */
        if (currentPage == StoragePage.COLLECTION
                && collectionPage > 0) {

            inventory.setItem(
                    PREVIOUS_SLOT,
                    createNavigationItem(
                            Material.ARROW,
                            "§e§lPrevious Page",
                            "§7Go to page "
                                    + collectionPage
                                    + ".",
                            collectionPage
                    )
            );

        } else {

            inventory.setItem(
                    MANAGER_BACK_SLOT,
                    createActionItem(
                            Material.ARROW,
                            "§e§lBack",
                            "§7Return to the Aetherion Manager."
                    )
            );
        }

        if (currentPage == StoragePage.COLLECTION
                && collectionPage < totalPages - 1) {

            inventory.setItem(
                    NEXT_SLOT,
                    createNavigationItem(
                            Material.ARROW,
                            "§e§lNext Page",
                            "§7Open the next storage page.",
                            collectionPage
                    )
            );
        }
    }

    private ItemStack createTabItem(
            Material material,
            String name,
            boolean selected,
            String... loreLines
    ) {

        ItemStack item =
                createActionItem(
                        material,
                        selected
                                ? name
                                : "§7"
                                + ChatColor.stripColor(name),
                        loreLines
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta != null) {

            meta.setEnchantmentGlintOverride(
                    selected
            );

            item.setItemMeta(
                    meta
            );
        }

        return item;
    }

    private ItemStack createEquippedSlotItem(
            PlayerPetCollection collection
    ) {

        if (collection == null) {
            return createEmptyEquippedItem();
        }

        PetInstance equipped =
                collection.getEquippedPet();

        if (equipped == null) {
            return createEmptyEquippedItem();
        }

        int petIndex =
                collection.getPets()
                        .indexOf(equipped);

        if (petIndex < 0) {
            return createEmptyEquippedItem();
        }

        return createPetItem(
                equipped,
                true,
                petIndex
        );
    }

    private ItemStack createEmptyEquippedItem() {

        return createActionItem(
                Material.GRAY_DYE,
                "§8No pet equipped",
                "§7Catch one, then equip it",
                "§7from your collection."
        );
    }

    private List<PetDefinition> getAetherlexPets(
            StoragePage page
    ) {

        List<PetDefinition> entries =
                new ArrayList<>();

        if (plugin.getPetRegistry() == null) {
            return entries;
        }

        for (PetDefinition definition :
                plugin.getPetRegistry()
                        .getAll()) {

            if (page == StoragePage.AETHERLEX_NETHER) {
                if (isAetherlexNetherPet(definition)) {
                    entries.add(definition);
                }
            } else if (page == StoragePage.AETHERLEX_DUNGEON) {
                if (isAetherlexDungeonPet(definition)) {
                    entries.add(definition);
                }
            } else if (page == StoragePage.AETHERLEX_SKY) {
                if (isAetherlexSkyPet(definition)) {
                    entries.add(definition);
                }
            } else if (page == StoragePage.AETHERLEX_LANDS) {
                if (isAetherlexLandsPet(definition)) {
                    entries.add(definition);
                }
            } else if (page == StoragePage.AETHERLEX_DEPTHS) {
                if (isAetherlexDepthsPet(definition)) {
                    entries.add(definition);
                }
            }
        }

        entries.sort(
                Comparator
                        .comparing(
                                (PetDefinition definition) ->
                                        definition.getSpawnType()
                                                .ordinal()
                        )
                        .thenComparing(
                                PetDefinition::getDisplayName
                        )
        );

        return entries;
    }

    private boolean isAetherlexLandsPet(
            PetDefinition definition
    ) {

        String id =
                definition.getId()
                        .toLowerCase();

        if (id.equals("wither")
                || id.equals("fire_dragon")
                || id.equals("blaze")
                || id.equals("slime_minion")
                || id.equals("ghast")) {

            return false;
        }

        PetSpawnType spawnType =
                definition.getSpawnType();

        return spawnType == PetSpawnType.SURFACE
                || spawnType == PetSpawnType.CAVE;
    }

    private boolean isAetherlexSkyPet(
            PetDefinition definition
    ) {

        return definition.getSpawnType()
                == PetSpawnType.SKY;
    }

    private boolean isAetherlexNetherPet(
            PetDefinition definition
    ) {

        return definition != null
                && definition.getSpawnType() == PetSpawnType.NETHER;
    }

    private boolean isAetherlexDungeonPet(
            PetDefinition definition
    ) {

        return definition != null
                && definition.isDungeonPet();
    }

    private boolean isAetherlexDepthsPet(
            PetDefinition definition
    ) {

        if (definition == null
                || isAetherlexNetherPet(definition)
                || isAetherlexDungeonPet(definition)
                || isAetherlexSkyPet(definition)
                || isAetherlexLandsPet(definition)) {

            return false;
        }

        return definition.getSpawnType() == PetSpawnType.AQUATIC;
    }

    private ItemStack createAetherlexItem(
            PetDefinition definition,
            PlayerPetCollection collection
    ) {

        boolean caught =
                collection != null
                        && collection.hasCaught(
                        definition.getId()
                );

        boolean revealed =
                collection != null
                        && collection.hasRevealed(
                        definition.getId()
                );

        ItemStack item =
                revealed
                        ? PetHead.create(
                        definition.getId()
                )
                        : new ItemStack(
                        Material.CLAY
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.setDisplayName(
                revealed
                        ? "§d§l" + definition.getDisplayName()
                        : "§8" + definition.getDisplayName()
        );

        List<String> lore =
                new ArrayList<>();

        if (caught) {

            lore.add(
                    "§a§lCaught"
            );

        } else if (revealed) {

            lore.add(
                    "§e§lSighted"
            );

        } else {

            lore.add(
                    "§8Undiscovered"
            );
        }

        lore.add("");

        lore.add(
                "§6§lHabitat"
        );

        lore.add(
                "§7"
                        + formatHabitat(
                        definition
                )
        );

        if (!revealed) {

            lore.add("");

            if (definition.isShopExclusive()
                    || "hacker".equalsIgnoreCase(definition.getId())) {
                lore.add(
                        "§8Cannot be caught."
                );
                lore.add(
                        "§8Buy it in the §bAether Shop§8."
                );
            } else {
                lore.add(
                        "§8Find it in the world"
                );

                lore.add(
                        "§8or hit it with a catch sphere."
                );
            }

            meta.setLore(
                    lore
            );

            item.setItemMeta(
                    meta
            );

            return item;
        }

        lore.add("");

        lore.add(
                PetFlavor.forId(
                        definition.getId()
                )
        );

        lore.add("");

        lore.add(
                "§6§lCore Stat"
        );

        if (definition.rollsRandomCoreStat()) {
            lore.add(
                    "§7Any · §8rolls on grant"
            );
        } else {
            lore.add(
                    "§7"
                            + formatCapability(
                            definition.getCoreStat()
                    )
            );
        }

        PetSkill skill =
                PetSkill.fromId(
                        definition.getId()
                );

        lore.add("");

        lore.add(
                "§b§lSkill"
        );

        if (skill == PetSkill.NONE) {

            lore.add(
                    "§8None yet"
            );

        } else {

            lore.add(
                    "§f"
                            + skill.getDisplayName()
            );

            lore.add(
                    "§7"
                            + skill.getDescription()
            );

            if (skill == PetSkill.GUARDIAN_BEAM) {

                lore.add(
                        "§7Epic: §f1 beam"
                );

                lore.add(
                        "§7Legendary+: §f2 beams"
                );
            }

            if (skill == PetSkill.HACKER_BREACH) {
                lore.add(
                        "§7Rare: §f+2 reach · 1 hijack · 30s / 60s CD"
                );
                lore.add(
                        "§7Epic: §f+3 reach · 2 hijacks · 25s / 45s CD"
                );
            }
        }

        lore.add("");

        lore.add(
                "§6§lRarities"
        );

        List<Rarity> rarities =
                new ArrayList<>(
                        definition.getRarityConfigs()
                                .keySet()
                );

        rarities.sort(
                Comparator.comparingInt(
                        Enum::ordinal
                )
        );

        for (Rarity rarity : rarities) {

            lore.add(
                    "§7• "
                            + getRarityColor(rarity)
                            + formatRarity(rarity)
            );
        }

        meta.setLore(
                lore
        );

        item.setItemMeta(
                meta
        );

        return item;
    }

    private String formatHabitat(
            PetDefinition definition
    ) {

        String id =
                definition.getId()
                        .toLowerCase();

        if (id.equals("wither")
                || id.equals("fire_dragon")
                || id.equals("blaze")
                || id.equals("slime_minion")
                || id.equals("ghast")) {
            return "The Nether";
        }

        if (id.equals("aetherion")) {
            return "Mythic surface encounter";
        }

        if (id.equals("hacker")
                || definition.isShopExclusive()) {
            return "Aether Shop · shards only";
        }

        if (id.equals("allay")) {
            return PetHabitat.DARK.getDisplayName();
        }

        if (definition.isDeepAquatic()) {

            return "Deep water · "
                    + definition.getMinWaterDepth()
                    + "–"
                    + definition.getMaxWaterDepth()
                    + " blocks below the surface";
        }

        PetHabitat habitat =
                definition.getHabitat();

        if (habitat != PetHabitat.ANY) {
            return habitat.getDisplayName();
        }

        return switch (definition.getSpawnType()) {

            case SURFACE ->
                    "Overworld surface";

            case CAVE ->
                    "Underground caves";

            case AQUATIC ->
                    "Oceans, rivers and lakes";

            case SKY ->
                    "Open sky";

            case NETHER ->
                    "The Nether";

            case DUNGEON ->
                    "Dungeon floors";
        };
    }

    private boolean isStorageMenu(
            String title
    ) {

        return title.equals(TITLE)
                || title.equals(AETHERLEX_LANDS_TITLE)
                || title.equals(AETHERLEX_DEPTHS_TITLE)
                || title.equals(AETHERLEX_SKY_TITLE)
                || title.equals(AETHERLEX_NETHER_TITLE)
                || title.equals(AETHERLEX_DUNGEON_TITLE);
    }

    private boolean handleStorageTabClick(
            Player player,
            int slot
    ) {

        if (slot == MANAGER_BACK_SLOT) {
            de.aetherion.items.util.ManagerNav.openManager(player);
            return true;
        }

        if (slot == COLLECTION_TAB_SLOT) {

            open(
                    player
            );

            player.playSound(
                    player.getLocation(),
                    Sound.UI_BUTTON_CLICK,
                    0.6f,
                    1.1f
            );

            return true;
        }

        if (slot == AETHERLEX_LANDS_TAB_SLOT) {

            openAetherlex(
                    player,
                    0
            );

            player.playSound(
                    player.getLocation(),
                    Sound.ITEM_BOOK_PAGE_TURN,
                    0.8f,
                    1.1f
            );

            return true;
        }

        if (slot == AETHERLEX_DEPTHS_TAB_SLOT) {

            openAetherlex(
                    player,
                    1
            );

            player.playSound(
                    player.getLocation(),
                    Sound.ITEM_BOOK_PAGE_TURN,
                    0.8f,
                    1.1f
            );

            return true;
        }

        if (slot == AETHERLEX_SKY_TAB_SLOT) {

            openAetherlex(
                    player,
                    2
            );

            player.playSound(
                    player.getLocation(),
                    Sound.ITEM_BOOK_PAGE_TURN,
                    0.8f,
                    1.1f
            );

            return true;
        }

        if (slot == AETHERLEX_NETHER_TAB_SLOT) {

            openAetherlex(
                    player,
                    3
            );

            player.playSound(
                    player.getLocation(),
                    Sound.ITEM_BOOK_PAGE_TURN,
                    0.8f,
                    1.1f
            );

            return true;
        }

        if (slot == AETHERLEX_DUNGEON_TAB_SLOT) {

            openAetherlex(
                    player,
                    4
            );

            player.playSound(
                    player.getLocation(),
                    Sound.ITEM_BOOK_PAGE_TURN,
                    0.8f,
                    1.1f
            );

            return true;
        }

        return false;
    }

    @EventHandler
    public void onInventoryClick(
            InventoryClickEvent event
    ) {

        String title =
                event.getView()
                        .getTitle();

        if (!isStorageMenu(title)
                && !title.equals(DETAILS_TITLE)) {

            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked()
                instanceof Player player)) {

            return;
        }

        if (event.getRawSlot() < 0
                || event.getRawSlot()
                >= INVENTORY_SIZE) {

            return;
        }

        if (title.equals(TITLE)) {

            handleCollectionClick(
                    player,
                    event.getRawSlot(),
                    event.getCurrentItem()
            );

            return;
        }

        if (isStorageMenu(title)) {

            handleAetherlexClick(
                    player,
                    event.getRawSlot()
            );

            return;
        }

        handleDetailsClick(
                player,
                event.getRawSlot(),
                event.getCurrentItem()
        );
    }

    private void handleCollectionClick(
            Player player,
            int slot,
            ItemStack clickedItem
    ) {

        if (slot == PREVIOUS_SLOT
                && clickedItem != null
                && clickedItem.hasItemMeta()
                && clickedItem.getItemMeta()
                .getPersistentDataContainer()
                .has(pageKey, PersistentDataType.INTEGER)) {

            int currentPage =
                    getPage(
                            clickedItem
                    );

            open(
                    player,
                    Math.max(
                            0,
                            currentPage - 1
                    )
            );

            player.playSound(
                    player.getLocation(),
                    Sound.ITEM_BOOK_PAGE_TURN,
                    0.7f,
                    1.0f
            );

            return;
        }

        if (handleStorageTabClick(
                player,
                slot
        )) {

            return;
        }

        PlayerPetCollection collection =
                plugin.getPetCollection(
                        player
                );

        List<PetInstance> pets =
                collection.getPets();

        if ((slot >= 0 && slot < PET_SLOTS || slot == EQUIPPED_SLOT)
                && clickedItem != null
                && clickedItem.hasItemMeta()) {

            Integer petIndex =
                    clickedItem
                            .getItemMeta()
                            .getPersistentDataContainer()
                            .get(
                                    petIndexKey,
                                    PersistentDataType.INTEGER
                            );

            if (petIndex == null
                    || petIndex < 0
                    || petIndex >= pets.size()) {

                return;
            }

            PetInstance pet =
                    pets.get(
                            petIndex
                    );

            openDetails(
                    player,
                    pet,
                    petIndex
            );

            player.playSound(
                    player.getLocation(),
                    Sound.UI_BUTTON_CLICK,
                    0.6f,
                    1.2f
            );

            return;
        }

        if (slot == NEXT_SLOT) {

            int currentPage =
                    getPage(
                            clickedItem
                    );

            open(
                    player,
                    currentPage + 1
            );

            player.playSound(
                    player.getLocation(),
                    Sound.ITEM_BOOK_PAGE_TURN,
                    0.7f,
                    1.15f
            );
        }
    }

    private void handleAetherlexClick(
            Player player,
            int slot
    ) {

        handleStorageTabClick(
                player,
                slot
        );
    }

    private void handleDetailsClick(
            Player player,
            int slot,
            ItemStack clickedItem
    ) {

        if (slot == BACK_SLOT) {

            open(player);

            return;
        }

        PlayerPetCollection collection =
                plugin.getPetCollection(
                        player
                );

        ItemStack displayedPet =
                player.getOpenInventory()
                        .getItem(
                                DETAILS_PET_SLOT
                        );

        if (displayedPet == null
                || !displayedPet.hasItemMeta()) {

            return;
        }

        Integer petIndex =
                displayedPet
                        .getItemMeta()
                        .getPersistentDataContainer()
                        .get(
                                petIndexKey,
                                PersistentDataType.INTEGER
                        );

        if (petIndex == null
                || petIndex < 0
                || petIndex >= collection
                .getPets()
                .size()) {

            return;
        }

        PetInstance selectedPet =
                collection
                        .getPets()
                        .get(
                                petIndex
                        );

        if (slot == EQUIP_SLOT) {

            ActivePetManager activePetManager =
                    plugin.getActivePetManager();

            if (activePetManager == null) {

                player.sendMessage(
                        "§c✦ §fThe pet system is not ready yet."
                );

                return;
            }

            if (collection.isEquipped(
                    selectedPet
            )) {

                collection.unequipPet(
                        selectedPet
                );

                activePetManager.unequip(
                        player
                );

                plugin.getPetDataManager()
                        .save(
                                collection
                        );

                player.sendMessage(
                        "§e✦ §f"
                                + selectedPet
                                .getDefinition()
                                .getDisplayName()
                                + " §7has been unequipped."
                );

                player.playSound(
                        player.getLocation(),
                        Sound.UI_BUTTON_CLICK,
                        0.8f,
                        0.9f
                );

            } else {

                collection.equipPet(
                        selectedPet
                );

                activePetManager.equip(
                        player,
                        selectedPet
                );

                plugin.getPetDataManager()
                        .save(
                                collection
                        );

                player.sendMessage(
                        "§a✦ §f"
                                + selectedPet
                                .getDefinition()
                                .getDisplayName()
                                + " §7is now equipped!"
                );

                player.playSound(
                        player.getLocation(),
                        Sound.ENTITY_PLAYER_LEVELUP,
                        0.7f,
                        1.4f
                );

                try {
                    Class.forName("de.aetherion.items.util.QuestProgressHook")
                            .getMethod("noteUsed", org.bukkit.entity.Player.class, String.class)
                            .invoke(null, player, "AETHER_PET");
                } catch (Throwable ignored) {
                }
            }

            open(
                    player
            );

            return;
        }

        if (slot == RELEASE_SLOT) {

            boolean wasEquipped =
                    collection.isEquipped(
                            selectedPet
                    );

            if (wasEquipped) {

                ActivePetManager activePetManager =
                        plugin.getActivePetManager();

                if (activePetManager != null) {

                    activePetManager.unequip(
                            player
                    );
                }
            }

            collection.removePet(
                    selectedPet
            );

            plugin.getPetDataManager()
                    .save(
                            collection
                    );

            player.sendMessage(
                    "§c✦ §f"
                            + selectedPet
                            .getDefinition()
                            .getDisplayName()
                            + " §7has been released."
            );

            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_ITEM_BREAK,
                    0.8f,
                    0.8f
            );

            open(
                    player
            );
        }
    }

    private int getPage(
            ItemStack item
    ) {

        if (item == null
                || !item.hasItemMeta()) {

            return 0;
        }

        Integer page =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(
                                pageKey,
                                PersistentDataType.INTEGER
                        );

        return page == null
                ? 0
                : page;
    }

    @EventHandler
    public void onInventoryDrag(
            InventoryDragEvent event
    ) {

        String title =
                event.getView()
                        .getTitle();

        if (!isStorageMenu(title)
                && !title.equals(DETAILS_TITLE)) {

            return;
        }

        event.setCancelled(true);
    }

    private String formatRarity(
            Rarity rarity
    ) {

        String text =
                rarity.name()
                        .toLowerCase();

        return Character.toUpperCase(
                text.charAt(0)
        )
                + text.substring(1);
    }

    private String formatVariant(
            Object variant
    ) {

        String text =
                variant.toString()
                        .toLowerCase();

        return Character.toUpperCase(
                text.charAt(0)
        )
                + text.substring(1);
    }

    private String formatCapability(
            ItemCapability capability
    ) {

        String text =
                capability.name()
                        .toLowerCase()
                        .replace(
                                "_",
                                " "
                        );

        StringBuilder result =
                new StringBuilder();

        for (String word :
                text.split(" ")) {

            if (word.isEmpty()) {
                continue;
            }

            result.append(
                    Character.toUpperCase(
                            word.charAt(0)
                    )
            );

            result.append(
                    word.substring(1)
            );

            result.append(" ");
        }

        return result.toString().trim();
    }

    private String dungeonAuraName(
            PetDefinition.DungeonAura aura
    ) {

        if (aura == null) {
            return "Stats";
        }

        return switch (aura) {

            case ALL ->
                    "all stats";

            case DAMAGE ->
                    "dungeon damage";

            case BOW ->
                    "bow damage";

            case NONE ->
                    "stats";
        };
    }

    private String formatValue(
            double value
    ) {

        return String.format(
                "%.2f",
                value
        );
    }

    private String getRarityColor(
            Rarity rarity
    ) {

        return switch (rarity) {

            case COMMON ->
                    ChatColor.WHITE.toString();

            case UNCOMMON ->
                    ChatColor.GREEN.toString();

            case RARE ->
                    ChatColor.BLUE.toString();

            case EPIC ->
                    ChatColor.DARK_PURPLE.toString();

            case LEGENDARY ->
                    ChatColor.GOLD.toString();

            case MYTHIC ->
                    ChatColor.LIGHT_PURPLE.toString();

            case AETHERED ->
                    ChatColor.DARK_RED.toString();
        };
    }
}