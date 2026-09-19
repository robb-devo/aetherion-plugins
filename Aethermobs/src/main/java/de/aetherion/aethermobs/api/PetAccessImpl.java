package de.aetherion.aethermobs.api;

import de.aetherion.aethermobs.AetherMobs;
import de.aetherion.aethermobs.pet.PetDefinition;
import de.aetherion.aethermobs.pet.PetExpTreat;
import de.aetherion.aethermobs.pet.PetHead;
import de.aetherion.core.api.PetAccess;
import de.aetherion.core.api.PetCatalogItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Typed AetherMobs surface for network sync and DEV catalog.
 */
public final class PetAccessImpl implements PetAccess {

    private final AetherMobs plugin;

    public PetAccessImpl(AetherMobs plugin) {
        this.plugin = plugin;
    }

    @Override
    public void flushPlayer(UUID playerId) {
        plugin.flushPetsForNetwork(playerId);
    }

    @Override
    public void reloadAfterImport(UUID playerId) {
        plugin.reloadPetsAfterImport(playerId);
    }

    @Override
    public void reequip(Player player) {
        plugin.reequipPetAfterImport(player);
    }

    @Override
    public List<PetCatalogItem> pets() {
        List<PetCatalogItem> items = new ArrayList<>();
        if (plugin.getPetRegistry() == null) {
            return items;
        }
        for (PetDefinition definition : plugin.getPetRegistry().getAll()) {
            if (definition == null) {
                continue;
            }
            String id = definition.getId();
            String name = definition.getDisplayName();
            ItemStack icon;
            try {
                icon = PetHead.create(id);
            } catch (RuntimeException ignored) {
                icon = new ItemStack(eggFor(id));
            }
            if (icon == null || icon.getType().isAir()) {
                icon = new ItemStack(eggFor(id));
            }
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§d" + name);
                meta.setLore(List.of(
                        "§7Left-click to spawn beside you.",
                        "§8Anywhere. Habitat ignored.",
                        "§7Right-click to add to collection."
                ));
                icon.setItemMeta(meta);
            }
            items.add(new PetCatalogItem(id, "§d" + name, icon));
        }
        return items;
    }

    @Override
    public boolean spawnDevPet(Player player, String petId) {
        return plugin.spawnDevPet(player, petId);
    }

    @Override
    public boolean giveDevPet(Player player, String petId) {
        return plugin.giveDevPet(player, petId);
    }

    @Override
    public int unlockAllPetsDev(Player player) {
        return plugin.unlockAllPetsDev(player);
    }

    @Override
    public ItemStack catchSphere(String id) {
        return plugin.createDevCatchSphere(id);
    }

    @Override
    public ItemStack petExpTreat(int tier) {
        return PetExpTreat.create(tier);
    }

    private static Material eggFor(String petId) {
        if (petId == null) {
            return Material.GHAST_SPAWN_EGG;
        }
        return switch (petId.toLowerCase()) {
            case "wolf" -> Material.WOLF_SPAWN_EGG;
            case "pig" -> Material.PIG_SPAWN_EGG;
            case "cow" -> Material.COW_SPAWN_EGG;
            case "bat" -> Material.BAT_SPAWN_EGG;
            case "cavespider", "cave_spider" -> Material.CAVE_SPIDER_SPAWN_EGG;
            case "creeper" -> Material.CREEPER_SPAWN_EGG;
            case "zombie" -> Material.ZOMBIE_SPAWN_EGG;
            case "skeleton" -> Material.SKELETON_SPAWN_EGG;
            case "squid" -> Material.SQUID_SPAWN_EGG;
            case "glowsquid", "glow_squid" -> Material.GLOW_SQUID_SPAWN_EGG;
            case "axolotl" -> Material.AXOLOTL_SPAWN_EGG;
            case "guardian" -> Material.GUARDIAN_SPAWN_EGG;
            case "dolphin" -> Material.DOLPHIN_SPAWN_EGG;
            case "cod" -> Material.COD_SPAWN_EGG;
            case "salmon" -> Material.SALMON_SPAWN_EGG;
            case "pufferfish" -> Material.PUFFERFISH_SPAWN_EGG;
            case "tropical_fish", "tropicalfish" -> Material.TROPICAL_FISH_SPAWN_EGG;
            case "ocelot" -> Material.OCELOT_SPAWN_EGG;
            case "parrot" -> Material.PARROT_SPAWN_EGG;
            case "wither" -> Material.WITHER_SKELETON_SPAWN_EGG;
            case "hawk", "pigeon", "bee", "owl", "butterfly", "bloom_fairy" -> Material.PARROT_SPAWN_EGG;
            case "frog" -> Material.FROG_SPAWN_EGG;
            case "cat" -> Material.CAT_SPAWN_EGG;
            case "sheep" -> Material.SHEEP_SPAWN_EGG;
            case "mooshroom", "mycelord" -> Material.MOOSHROOM_SPAWN_EGG;
            case "sniffer" -> Material.SNIFFER_SPAWN_EGG;
            case "iron_golem" -> Material.IRON_GOLEM_SPAWN_EGG;
            case "witch", "swamp_hag" -> Material.WITCH_SPAWN_EGG;
            case "sand_wraith" -> Material.HUSK_SPAWN_EGG;
            case "forest_spirit", "lush_oracle" -> Material.ALLAY_SPAWN_EGG;
            case "aetherion" -> Material.DRAGON_EGG;
            case "dungeon_dragon" -> Material.PHANTOM_SPAWN_EGG;
            case "dungeon_zombie" -> Material.ZOMBIE_SPAWN_EGG;
            case "dungeon_skeleton" -> Material.SKELETON_SPAWN_EGG;
            default -> Material.GHAST_SPAWN_EGG;
        };
    }
}
