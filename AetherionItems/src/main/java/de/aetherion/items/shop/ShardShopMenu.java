package de.aetherion.items.shop;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.BookPage;
import de.aetherion.items.util.GuiIcons;
import de.aetherion.items.util.ManagerChrome;
import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.economy.ShardService;
import de.aetherion.items.rank.RankBadgeService;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class ShardShopMenu implements Listener {

    public static final String TITLE = "§bAether Shop";
    public static final String TITLE_BOOSTS = "§bAether Shop §8· §dBoosts";
    public static final String TITLE_MATERIALS = "§bAether Shop §8· §aMaterials";
    public static final String TITLE_EXCLUSIVE = "§bAether Shop §8· §5Exclusive";

    /*
     * A book page like the Manager's: the balance on top, the shelves on row 2 with their name or price written
     * under them (BookPage captions), the way back on the last row (tools/gen_gui_chrome.py PAGES['shop*']).
     */
    public static final int BALANCE_SLOT = 4;
    public static final int CLOSE_SLOT = de.aetherion.items.util.ManagerNav.SLOT_45;
    public static final int BACK_SLOT = 40;
    private static final int BOOSTS_SLOT = 19;
    private static final int MATERIALS_SLOT = 22;
    private static final int EXCLUSIVE_SLOT = 25;
    private static final int MVP_SLOT = 22;

    public static final long HACKER_RARE_PRICE = 450L;
    public static final long HACKER_EPIC_PRICE = 850L;

    private final ShardService shards;

    public ShardShopMenu(ShardService shards) {
        this.shards = shards;
    }

    public void open(Player player) {
        boolean german = AetherServices.german(player);
        List<BookPage.Caption> captions = new ArrayList<>();
        captions.add(balanceCaption(player));
        captions.add(new BookPage.Caption(BOOSTS_SLOT, german ? "Booster" : "Boosts", BookPage.State.OPEN));
        captions.add(new BookPage.Caption(MATERIALS_SLOT, german ? "Materialien" : "Materials", BookPage.State.OPEN));
        captions.add(new BookPage.Caption(EXCLUSIVE_SLOT, german ? "Exklusiv" : "Exclusive", BookPage.State.OPEN));
        Inventory inventory = Bukkit.createInventory(new Holder(View.ROOT), 45,
                BookPage.title(ManagerChrome.SHOP_GLYPH, captions));
        fillPanes(inventory);
        inventory.setItem(BALANCE_SLOT, balanceIcon(player));

        inventory.setItem(BOOSTS_SLOT, named(
                Material.EXPERIENCE_BOTTLE,
                "§dBoosts & Charms",
                "§7XP vials and utility charms.",
                "",
                "§eClick to browse."
        ));
        inventory.setItem(MATERIALS_SLOT, named(
                Material.DIAMOND,
                "§aMaterials",
                "§7Compressed and refined stock.",
                "",
                "§eClick to browse."
        ));
        inventory.setItem(EXCLUSIVE_SLOT, named(
                Material.PLAYER_HEAD,
                "§5Exclusive",
                "§7Shop-only pets and oddities.",
                "§8Not catchable in the wild.",
                "",
                "§eClick to browse."
        ));

        inventory.setItem(CLOSE_SLOT, de.aetherion.items.util.ManagerNav.button());
        player.openInventory(inventory);
    }

    public void openBoosts(Player player) {
        openShelf(player, View.BOOSTS, ManagerChrome.SHOP_BOOSTS_GLYPH, boostOffers());
    }

    public void openMaterials(Player player) {
        openShelf(player, View.MATERIALS, ManagerChrome.SHOP_MATERIALS_GLYPH, materialOffers());
    }

    public void openExclusive(Player player) {
        openShelf(player, View.EXCLUSIVE, ManagerChrome.SHOP_EXCLUSIVE_GLYPH, exclusiveOffers());
    }

    /** One shelf: its offers on row 2 with the price under each (faded when you can't afford it). */
    private void openShelf(Player player, View view, char page, Offer[] offers) {
        List<BookPage.Caption> captions = new ArrayList<>();
        captions.add(balanceCaption(player));
        long balance = shards.get(player);
        for (Offer offer : offers) {
            captions.add(new BookPage.Caption(offer.slot, String.format(Locale.US, "%,d", offer.price),
                    balance >= offer.price ? BookPage.State.OPEN : BookPage.State.LOCKED));
        }
        if (view == View.EXCLUSIVE) {
            captions.add(new BookPage.Caption(MVP_SLOT, "MVP++", BookPage.State.NEXT));
        }
        Inventory inventory = Bukkit.createInventory(new Holder(view), 45, BookPage.title(page, captions));
        fillPanes(inventory);
        inventory.setItem(BALANCE_SLOT, balanceIcon(player));

        placeOffers(player, inventory, offers);
        if (view == View.EXCLUSIVE) {
            inventory.setItem(MVP_SLOT, named(
                    Material.NETHER_STAR,
                    "§6MVP§c++",
                    "§7Not for sale.",
                    "§7Some ranks you just have.",
                    "§8A Hypixel homage, nothing more."
            ));
        }

        inventory.setItem(BACK_SLOT, GuiIcons.icon(named(Material.ARROW, "§eBack", "§7To the Aether Shop."), GuiIcons.BACK));
        inventory.setItem(CLOSE_SLOT, de.aetherion.items.util.ManagerNav.button());
        player.openInventory(inventory);
    }

    private BookPage.Caption balanceCaption(Player player) {
        return new BookPage.Caption(BALANCE_SLOT, shards.formatted(player), BookPage.State.NEXT);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            de.aetherion.items.util.ManagerNav.openManager(player);
            return;
        }

        if (holder.view == View.ROOT) {
            if (slot == BOOSTS_SLOT) {
                openBoosts(player);
            } else if (slot == MATERIALS_SLOT) {
                openMaterials(player);
            } else if (slot == EXCLUSIVE_SLOT) {
                openExclusive(player);
            }
            return;
        }

        if (slot == BACK_SLOT) {
            open(player);
            return;
        }

        if (holder.view == View.EXCLUSIVE && slot == MVP_SLOT) {
            de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
            RankBadgeService ranks = plugin == null ? null : plugin.ranks();
            if (ranks != null && ranks.hasMvpPlusPlus(player.getUniqueId())) {
                player.sendMessage("§7Not for sale, David. Some roofs you nail yourself. This one isn't on the list.");
                player.sendMessage("§8The dachdecker years were honest. This button is not.");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_LAND, 0.25f, 1.35f);
            } else {
                player.sendMessage("§6MVP§c++ §7is not in the catalog. You either have it, or you don't.");
            }
            return;
        }

        Offer offer = offerAt(holder.view, slot);
        if (offer == null) {
            return;
        }
        buy(player, holder.view, offer);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void buy(Player player, View view, Offer offer) {
        if (offer.price <= 0L) {
            return;
        }
        if (!shards.take(player, offer.price)) {
            player.sendMessage("§c" + offer.price + " crystals. You have §b" + shards.formatted(player) + "§c.");
            return;
        }

        if (offer.petId != null) {
            if (!grantPet(player, offer.petId, offer.petRarity)) {
                shards.add(player, offer.price);
                player.sendMessage("§cPet grant failed. Crystals refunded.");
                return;
            }
            player.sendMessage("§bAether Shop §8» §7Unlocked §dHacker §7("
                    + offer.petRarity + ") §8· §c-" + offer.price + " crystals");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.35f);
            reopen(player, view);
            return;
        }

        if (offer.create == null) {
            shards.add(player, offer.price);
            return;
        }

        ItemStack bought = offer.create.get();
        if (bought == null || bought.getType().isAir()) {
            shards.add(player, offer.price);
            player.sendMessage("§cOffer failed. Crystals refunded.");
            return;
        }
        player.getInventory().addItem(bought).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendMessage("§bAether Shop §8» §7Bought §f" + plainName(bought)
                + " §8· §c-" + offer.price + " crystals");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.35f);
        reopen(player, view);
    }

    private void reopen(Player player, View view) {
        switch (view) {
            case BOOSTS -> openBoosts(player);
            case MATERIALS -> openMaterials(player);
            case EXCLUSIVE -> openExclusive(player);
            default -> open(player);
        }
    }

    private boolean grantPet(Player player, String petId, String rarity) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherMobs");
        if (plugin == null || !plugin.isEnabled()) {
            player.sendMessage("§cAetherMobs is not loaded.");
            return false;
        }
        try {
            Method method = plugin.getClass().getMethod(
                    "giveShopPet",
                    Player.class,
                    String.class,
                    String.class
            );
            return Boolean.TRUE.equals(method.invoke(plugin, player, petId, rarity));
        } catch (ReflectiveOperationException exception) {
            player.sendMessage("§cCould not grant pet.");
            return false;
        }
    }

    private void placeOffers(Player player, Inventory inventory, Offer[] offers) {
        for (Offer offer : offers) {
            inventory.setItem(offer.slot, offerIcon(player, offer));
        }
    }

    private ItemStack offerIcon(Player player, Offer offer) {
        ItemStack item;
        if (offer.petId != null) {
            item = petIcon(offer);
        } else {
            item = offer.create == null ? null : offer.create.get();
        }
        if (item == null) {
            return named(Material.BARRIER, "§cBroken offer");
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            lore.add("§bPrice: §f" + offer.price + " Aether Crystals");
            if ("phial".equals(offer.id) || "lesser_phial".equals(offer.id)) {
                de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
                if (plugin != null && plugin.xpBoost() != null && plugin.xpBoost().active(player)) {
                    lore.add("§aActive: §f" + plugin.xpBoost().formatted(player) + " §7left");
                    lore.add("§8Buying another adds duration.");
                }
            }
            lore.add("§eClick to buy.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack petIcon(Offer offer) {
        ItemStack head = null;
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("AetherMobs");
            if (plugin != null && plugin.isEnabled()) {
                Class<?> petHead = Class.forName("de.aetherion.aethermobs.pet.PetHead");
                head = (ItemStack) petHead.getMethod("create", String.class).invoke(null, offer.petId);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        if (head == null || head.getType().isAir()) {
            head = new ItemStack(Material.PLAYER_HEAD);
        }
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            boolean epic = "EPIC".equalsIgnoreCase(offer.petRarity);
            meta.setDisplayName(epic ? "§5Hacker §8· §5Epic" : "§9Hacker §8· §9Rare");
            List<String> lore = new ArrayList<>();
            lore.add("§8§kᚠᚢᚦᚨ§r §7Guy Fawkes protocol §8§kᚱᚲ");
            lore.add("");
            lore.add("§bSkill: §fProtocol Hijack");
            if (epic) {
                lore.add("§7+3 block reach");
                lore.add("§7Hijacks §f2 §7hostiles for §f25s");
                lore.add("§7Cooldown: §f45s");
            } else {
                lore.add("§7+2 block reach");
                lore.add("§7Hijacks §f1 §7hostile for §f30s");
                lore.add("§7Cooldown: §f60s");
            }
            lore.add("§8Bosses cannot be hijacked.");
            lore.add("");
            lore.add("§7Core stat: §fAny §8(rolls on buy)");
            lore.add("§8Cannot be caught. Collection unlock.");
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private Offer offerAt(View view, int slot) {
        Offer[] offers = switch (view) {
            case BOOSTS -> boostOffers();
            case MATERIALS -> materialOffers();
            case EXCLUSIVE -> exclusiveOffers();
            default -> new Offer[0];
        };
        for (Offer offer : offers) {
            if (offer.slot == slot) {
                return offer;
            }
        }
        return null;
    }

    private static Offer[] boostOffers() {
        return new Offer[]{
                new Offer(19, AetherBloodVial.LESSER_PRICE, "lesser_phial",
                        AetherBloodVial::createLesser, null, null),
                new Offer(22, AetherBloodVial.PRICE, "phial",
                        AetherBloodVial::create, null, null),
                new Offer(25, 350L, "charm_estate",
                        () -> {
                            de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
                            return plugin == null || plugin.getCustomItem() == null
                                    ? null
                                    : plugin.getCustomItem().accessories().estateCharm();
                        }, null, null)
        };
    }

    private static Offer[] materialOffers() {
        return new Offer[]{
                new Offer(19, 80L, "refined_wheat",
                        () -> stack(CompressedResource.WHEAT.refined(), 1), null, null),
                new Offer(21, 90L, "compacted_iron",
                        () -> stack(CompressedResource.RAW_IRON.compacted(), 1), null, null),
                new Offer(23, 220L, "compacted_diamond",
                        () -> stack(CompressedResource.DIAMOND.compacted(), 1), null, null),
                new Offer(25, 45L, "compressed_diamond_x8",
                        () -> stack(CompressedResource.DIAMOND.compressed(), 8), null, null)
        };
    }

    private static Offer[] exclusiveOffers() {
        return new Offer[]{
                new Offer(19, HACKER_RARE_PRICE, "pet_hacker_rare",
                        null, "hacker", "RARE"),
                new Offer(25, HACKER_EPIC_PRICE, "pet_hacker_epic",
                        null, "hacker", "EPIC")
        };
    }

    private ItemStack balanceIcon(Player player) {
        return named(
                Material.AMETHYST_SHARD,
                "§bAether Crystals",
                "§f" + shards.formatted(player),
                "§7Premium currency for this shop.",
                "§7Get them from the §bCrystal Liquidator§7:",
                "§7coins (expensive), or liquidate",
                "§7compressed / compacted / refined mats.",
                "§8You can also cash crystals back to coins."
        );
    }

    private void fillPanes(Inventory inventory) {
        ItemStack pane = GuiIcons.spacer();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane.clone());
        }
    }

    private static ItemStack stack(ItemStack base, int amount) {
        if (base == null) {
            return null;
        }
        ItemStack copy = base.clone();
        copy.setAmount(Math.max(1, amount));
        return copy;
    }

    private static String plainName(ItemStack item) {
        if (item == null) {
            return "item";
        }
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName().replace('§', '&').replaceAll("&[0-9a-fk-or]", "");
        }
        return item.getType().name().toLowerCase(Locale.ROOT);
    }

    private ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private enum View {
        ROOT,
        BOOSTS,
        MATERIALS,
        EXCLUSIVE
    }

    private record Offer(
            int slot,
            long price,
            String id,
            Supplier<ItemStack> create,
            String petId,
            String petRarity
    ) {
    }

    public static final class Holder implements InventoryHolder {
        private final View view;

        public Holder(View view) {
            this.view = view == null ? View.ROOT : view;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
