package de.aetherion.aethermobs.pet;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.menu.PetMenu;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.model.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BetaSphereManager implements Listener {

    public static final NamespacedKey BETA_SPHERE_KEY =
            new NamespacedKey(
                    "aethermobs",
                    "beta_sphere"
            );

    public static final NamespacedKey BETA_PROJECTILE_KEY =
            new NamespacedKey(
                    "aethermobs",
                    "beta_projectile"
            );

    public static final NamespacedKey PET_MENU_KEY =
            new NamespacedKey(
                    "aethermobs",
                    "pet_menu"
            );

    public static final NamespacedKey CATCH_SPHERE_KEY =
            new NamespacedKey(
                    "aethermobs",
                    "catch_sphere"
            );

    public static final NamespacedKey SPHERE_PROJECTILE_KEY =
            new NamespacedKey(
                    "aethermobs",
                    "sphere_projectile"
            );

    private final CatchSphereRegistry sphereRegistry;

    private final Map<UUID, Long> cooldowns =
            new ConcurrentHashMap<>();

    private final PetMenu petMenu;

    public BetaSphereManager(
            CatchSphereRegistry sphereRegistry
    ) {

        this.sphereRegistry =
                sphereRegistry;

        AetherMobs plugin =
                (AetherMobs) Bukkit.getPluginManager()
                        .getPlugin("AetherMobs");

        this.petMenu =
                new PetMenu(plugin);
    }

    public ItemStack createCatchSphere(String id) {
        CatchSphere sphere = sphereRegistry.get(id);

        if (sphere == null) {
            return new ItemStack(Material.SNOWBALL);
        }

        ItemStack item = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        Rarity rarity = switch (sphere.getId()) {
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            case "legendary" -> Rarity.LEGENDARY;
            case "beta" -> Rarity.MYTHIC;
            default -> Rarity.COMMON;
        };

        meta.setDisplayName(sphereName(sphere));
        meta.setLore(sphereLore(sphere));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        if (sphere.isInfinite()) {
            meta.setMaxStackSize(1);
        }

        meta.getPersistentDataContainer().set(
                CATCH_SPHERE_KEY,
                PersistentDataType.STRING,
                sphere.getId()
        );

        meta.getPersistentDataContainer().set(
                ItemKeys.item(),
                PersistentDataType.STRING,
                "catch_sphere_" + sphere.getId()
        );

        meta.getPersistentDataContainer().set(
                ItemKeys.rarity(),
                PersistentDataType.STRING,
                rarity.name()
        );

        if (sphere.isInfinite()) {
            meta.getPersistentDataContainer().set(
                    BETA_SPHERE_KEY,
                    PersistentDataType.BYTE,
                    (byte) 1
            );
        }

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createBetaSphere() {
        return createCatchSphere("beta");
    }

    private String sphereName(CatchSphere sphere) {
        return switch (sphere.getId()) {
            case "common" -> "§fCommon Catch Sphere";
            case "rare" -> "§bRare Catch Sphere";
            case "epic" -> "§dEpic Catch Sphere";
            case "legendary" -> "§6Legendary Catch Sphere";
            default -> "§d§l✦ BETA SPHERE ✦";
        };
    }

    private List<String> sphereLore(CatchSphere sphere) {
        if (!sphere.hasRarityTable()) {
            return List.of(
                    "§7Admin Catch Sphere",
                    "",
                    "§d✦ §fCatch Chance: §d" + formatChance(sphere.getCatchChance()),
                    "§d✦ §fAny pet rarity",
                    "§d✦ §fNot consumed on throw",
                    "",
                    "§8AetherMobs Admin"
            );
        }

        return List.of(
                "§7Catch Sphere",
                "",
                "§7Common: §f" + formatChance(sphere.getCatchChance(Rarity.COMMON)),
                "§7Uncommon: §a" + formatChance(sphere.getCatchChance(Rarity.UNCOMMON)),
                "§7Rare: §b" + formatChance(sphere.getCatchChance(Rarity.RARE)),
                "§7Epic: §d" + formatChance(sphere.getCatchChance(Rarity.EPIC)),
                "§7Legendary: §6" + formatChance(sphere.getCatchChance(Rarity.LEGENDARY)),
                "§7Mythic: §5" + formatChance(sphere.getCatchChance(Rarity.MYTHIC)),
                "",
                "§8Consumed on throw"
        );
    }

    public ItemStack createPetMenuItem() {

        ItemStack item =
                new ItemStack(
                        Material.ENDER_CHEST
                );

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.setDisplayName(
                "§b§l✦ PET COLLECTION ✦"
        );

        meta.setLore(
                List.of(
                        "§7Open your Pet Collection.",
                        "",
                        "§b✦ §fManage your pets",
                        "§b✦ §fEquip & unequip pets",
                        "§b✦ §fView pet information",
                        "",
                        "§eClick to open"
                )
        );

        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES
        );

        meta.getPersistentDataContainer().set(
                PET_MENU_KEY,
                PersistentDataType.BYTE,
                (byte) 1
        );

        item.setItemMeta(
                meta
        );

        return item;
    }

    public boolean isPetMenuItem(
            ItemStack item
    ) {

        if (item == null
                || item.getType()
                != Material.ENDER_CHEST
                || !item.hasItemMeta()) {

            return false;
        }

        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(
                        PET_MENU_KEY,
                        PersistentDataType.BYTE
                );
    }

    public CatchSphere getBetaSphere() {
        return sphereRegistry.get("beta");
    }

    public String getSphereId(ItemStack item) {
        if (item == null || item.getType() != Material.SNOWBALL || !item.hasItemMeta()) {
            return null;
        }

        String id = item.getItemMeta()
                .getPersistentDataContainer()
                .get(CATCH_SPHERE_KEY, PersistentDataType.STRING);

        if (id != null && !id.isBlank()) {
            return id;
        }

        if (item.getItemMeta()
                .getPersistentDataContainer()
                .has(BETA_SPHERE_KEY, PersistentDataType.BYTE)) {
            return "beta";
        }

        String itemId = item.getItemMeta()
                .getPersistentDataContainer()
                .get(ItemKeys.item(), PersistentDataType.STRING);

        if (itemId != null && itemId.startsWith("catch_sphere_")) {
            return itemId.substring("catch_sphere_".length());
        }

        return null;
    }

    public CatchSphere getSphere(ItemStack item) {
        return sphereRegistry.get(getSphereId(item));
    }

    public CatchSphere getThrownSphere(Snowball snowball) {
        String id = snowball.getPersistentDataContainer()
                .get(SPHERE_PROJECTILE_KEY, PersistentDataType.STRING);

        if (id == null && isBetaProjectile(snowball)) {
            id = "beta";
        }

        return sphereRegistry.get(id);
    }

    public boolean isCatchSphere(ItemStack item) {
        return getSphere(item) != null;
    }

    public boolean isBetaSphere(ItemStack item) {
        if (item == null || item.getType() != Material.SNOWBALL || !item.hasItemMeta()) {
            return false;
        }

        if (item.getItemMeta()
                .getPersistentDataContainer()
                .has(BETA_SPHERE_KEY, PersistentDataType.BYTE)) {
            return true;
        }

        String id = item.getItemMeta()
                .getPersistentDataContainer()
                .get(CATCH_SPHERE_KEY, PersistentDataType.STRING);

        return "beta".equalsIgnoreCase(id);
    }

    public boolean isBetaProjectile(Snowball snowball) {
        return snowball.getPersistentDataContainer()
                .has(BETA_PROJECTILE_KEY, PersistentDataType.BYTE)
                || snowball.getPersistentDataContainer()
                .has(SPHERE_PROJECTILE_KEY, PersistentDataType.STRING);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (isPetMenuItem(item)) {
            event.setCancelled(true);
            petMenu.open(player);
            return;
        }

        CatchSphere sphere = getSphere(item);

        if (sphere == null) {
            return;
        }

        event.setCancelled(true);

        if (event.getHand() == EquipmentSlot.OFF_HAND
                && getSphere(player.getInventory().getItemInMainHand()) != null) {
            return;
        }

        if (isOnCooldown(player, sphere)) {
            double seconds = getRemainingCooldown(player, sphere) / 1000.0;
            player.sendMessage(
                    "§d✦ §fSphere cooldown: §d"
                            + String.format("%.1f", seconds)
                            + "s"
            );
            return;
        }

        Snowball projectile = player.launchProjectile(Snowball.class);
        projectile.getPersistentDataContainer().set(
                SPHERE_PROJECTILE_KEY,
                PersistentDataType.STRING,
                sphere.getId()
        );

        if (sphere.isInfinite()) {
            projectile.getPersistentDataContainer().set(
                    BETA_PROJECTILE_KEY,
                    PersistentDataType.BYTE,
                    (byte) 1
            );
        } else {
            consumeSphere(player, event.getHand());
        }

        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 1.0f);
        setCooldown(player);
    }

    private void consumeSphere(Player player, EquipmentSlot hand) {
        if (hand == null) {
            hand = EquipmentSlot.HAND;
        }

        ItemStack item = player.getInventory().getItem(hand);

        if (!isCatchSphere(item)) {
            return;
        }

        int amount = item.getAmount() - 1;

        if (amount <= 0) {
            player.getInventory().setItem(hand, null);
        } else {
            item.setAmount(amount);
        }
    }

    private boolean isOnCooldown(Player player, CatchSphere sphere) {
        Long lastUse = cooldowns.get(player.getUniqueId());

        if (lastUse == null) {
            return false;
        }

        return System.currentTimeMillis() - lastUse < sphere.getCooldownMillis();
    }

    private long getRemainingCooldown(Player player, CatchSphere sphere) {
        Long lastUse = cooldowns.get(player.getUniqueId());

        if (lastUse == null) {
            return 0L;
        }

        long elapsed = System.currentTimeMillis() - lastUse;
        return Math.max(0L, sphere.getCooldownMillis() - elapsed);
    }

    private void setCooldown(
            Player player
    ) {

        cooldowns.put(
                player.getUniqueId(),
                System.currentTimeMillis()
        );
    }

    public void giveBetaSphere(Player player) {
        player.getInventory().addItem(createBetaSphere());
    }

    public void removePetMenuItems(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isPetMenuItem(player.getInventory().getItem(slot))) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("AetherMobs"),
                () -> removePetMenuItems(event.getPlayer())
        );
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("AetherMobs"),
                () -> removePetMenuItems(event.getPlayer())
        );
    }

    private String formatChance(
            double chance
    ) {

        if (chance == Math.floor(chance)) {

            return String.format(
                    "%.0f%%",
                    chance
            );
        }

        return String.format(
                "%.2f%%",
                chance
        );
    }
}