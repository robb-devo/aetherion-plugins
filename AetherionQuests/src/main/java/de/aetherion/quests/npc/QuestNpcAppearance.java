package de.aetherion.quests.npc;

import de.aetherion.core.AetherKeys;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Quest NPCs are normal villagers — same pattern as the trader and
 * dungeon keeper. Silent, invulnerable, no AI, no duplicate copies.
 */
public final class QuestNpcAppearance {

    private QuestNpcAppearance() {
    }

    public static NamespacedKey npcKey() {
        return AetherKeys.QUEST_NPC;
    }

    public static NamespacedKey visualKey() {
        return new NamespacedKey(plugin(), "quest_npc_visual");
    }

    public static boolean isVisual(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(visualKey(), PersistentDataType.BYTE);
    }

    public static boolean isHost(Entity entity) {
        return entity instanceof Villager && npcId(entity) != null && !isVisual(entity);
    }

    public static String npcId(Entity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getPersistentDataContainer().get(npcKey(), PersistentDataType.STRING);
    }

    public static Villager spawnHost(org.bukkit.Location location, QuestNPC npc) {
        if (location == null || location.getWorld() == null || npc == null) {
            return null;
        }
        return location.getWorld().spawn(location, Villager.class, spawned -> apply(spawned, npc));
    }

    public static void apply(Entity entity, QuestNPC npc) {
        if (!(entity instanceof Villager villager) || npc == null) {
            return;
        }
        String id = npc.getId() == null ? "" : npc.getId().toLowerCase();
        Look look = lookFor(id);

        villager.setCustomName("§b" + npc.getName());
        villager.setCustomNameVisible(false);
        villager.setAI(false);
        villager.setAware(false);
        villager.setGravity(true);
        villager.setInvulnerable(true);
        villager.setCollidable(false);
        villager.setSilent(true);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setCanPickupItems(false);
        villager.setInvisible(false);
        villager.setAdult();
        villager.setAgeLock(true);
        villager.clearActiveItem();
        villager.setRecipes(List.of());
        villager.setProfession(Villager.Profession.NITWIT);
        villager.setVillagerType(look.type());
        villager.setVillagerLevel(1);
        villager.setVillagerExperience(0);

        EntityEquipment gear = villager.getEquipment();
        if (gear != null) {
            gear.clear();
            if (look.hand() != null) {
                gear.setItemInMainHand(new ItemStack(look.hand()));
                gear.setItemInMainHandDropChance(0.0f);
            }
        }

        tagNpc(villager, id);
        villager.getPersistentDataContainer().remove(visualKey());
        NpcNametags.hide(villager);
    }

    public static void silenceLiving(LivingEntity living) {
        if (living == null) {
            return;
        }
        living.setSilent(true);
        living.setInvulnerable(true);
        living.setCollidable(false);
        living.setCanPickupItems(false);
        living.setCustomNameVisible(false);
        NpcNametags.hide(living);
        if (living instanceof Mob mob) {
            mob.setAware(false);
            mob.setAI(false);
        }
        if (living instanceof Villager villager) {
            villager.setInvisible(false);
            villager.setRecipes(List.of());
            villager.setProfession(Villager.Profession.NITWIT);
        }
    }

    public static void tagNpc(Entity entity, String npcId) {
        if (entity == null || npcId == null || npcId.isBlank()) {
            return;
        }
        entity.getPersistentDataContainer().set(npcKey(), PersistentDataType.STRING, npcId.toLowerCase());
    }

    public static void removeTree(Entity entity) {
        if (entity == null) {
            return;
        }
        for (Entity passenger : List.copyOf(entity.getPassengers())) {
            removeTree(passenger);
        }
        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            vehicle.removePassenger(entity);
        }
        entity.remove();
    }

    private static Look lookFor(String id) {
        return switch (id) {
            case "miner" -> new Look(Villager.Type.TAIGA, Material.IRON_PICKAXE);
            case "chicken_keeper" -> new Look(Villager.Type.PLAINS, Material.EGG);
            case "tollkeeper" -> new Look(Villager.Type.SAVANNA, Material.GOLDEN_AXE);
            case "dockhand" -> new Look(Villager.Type.SWAMP, Material.INK_SAC);
            case "ash_scout" -> new Look(Villager.Type.TAIGA, Material.BOW);
            case "colossus_scholar" -> new Look(Villager.Type.SNOW, Material.BOOK);
            case "arena_proctor" -> new Look(Villager.Type.DESERT, Material.GLASS_BOTTLE);
            case "veil_priest" -> new Look(Villager.Type.DESERT, Material.ENDER_PEARL);
            case "patch_intern" -> new Look(Villager.Type.PLAINS, Material.WRITTEN_BOOK);
            case "void_janitor" -> new Look(Villager.Type.TAIGA, Material.BRUSH);
            case "fuse" -> new Look(Villager.Type.SAVANNA, Material.FLINT_AND_STEEL);
            case "claims_adjuster" -> new Look(Villager.Type.DESERT, Material.IRON_PICKAXE);
            case "repo_agent" -> new Look(Villager.Type.SNOW, Material.BOOK);
            case "hunter" -> new Look(Villager.Type.TAIGA, Material.LEATHER);
            case "lumberjack" -> new Look(Villager.Type.TAIGA, Material.IRON_AXE);
            case "farmer" -> new Look(Villager.Type.PLAINS, Material.IRON_HOE);
            case "craftsman", "blacksmith" -> new Look(Villager.Type.SAVANNA, Material.IRON_INGOT);
            case "collector" -> new Look(Villager.Type.DESERT, Material.GOLD_NUGGET);
            case "lark" -> new Look(Villager.Type.JUNGLE, Material.SNOWBALL);
            case "fisher", "fisherman" -> new Look(Villager.Type.SWAMP, Material.FISHING_ROD);
            case "fishmonger" -> new Look(Villager.Type.SWAMP, Material.COD);
            case "foreman" -> new Look(Villager.Type.TAIGA, Material.IRON_PICKAXE);
            case "merchant" -> new Look(Villager.Type.SAVANNA, Material.EMERALD);
            case "egon" -> new Look(Villager.Type.PLAINS, Material.IRON_CHESTPLATE);
            case "quartermaster" -> new Look(Villager.Type.SNOW, Material.MAP);
            case "fry_gossip" -> new Look(Villager.Type.PLAINS, Material.COOKED_CHICKEN);
            case "dock_whisper" -> new Look(Villager.Type.SWAMP, Material.NAUTILUS_SHELL);
            case "larder" -> new Look(Villager.Type.PLAINS, Material.BREAD);
            case "pet_scout" -> new Look(Villager.Type.JUNGLE, Material.LEAD);
            case "ore_ledger" -> new Look(Villager.Type.DESERT, Material.COAL);
            case "timber_clerk" -> new Look(Villager.Type.TAIGA, Material.OAK_LOG);
            case "canopy_clerk" -> new Look(Villager.Type.TAIGA, Material.SPRUCE_LOG);
            case "dock_scaler" -> new Look(Villager.Type.SWAMP, Material.COD);
            case "root_cellar" -> new Look(Villager.Type.PLAINS, Material.CARROT);
            case "sphere_proctor" -> new Look(Villager.Type.JUNGLE, Material.SNOWBALL);
            case "quarry_broker" -> new Look(Villager.Type.SAVANNA, Material.COBBLESTONE);
            case "slag_poet" -> new Look(Villager.Type.DESERT, Material.RAW_IRON);
            case "bait_theory" -> new Look(Villager.Type.SWAMP, Material.FISHING_ROD);
            case "farm_isle_guide" -> new Look(Villager.Type.PLAINS, Material.WHEAT);
            case "forage_pad_guide" -> new Look(Villager.Type.TAIGA, Material.SPRUCE_SAPLING);
            case "eldervale_welcome" -> new Look(Villager.Type.TAIGA, Material.IRON_PICKAXE);
            case "eldervale_upgrade" -> new Look(Villager.Type.SAVANNA, Material.ANVIL);
            case "isle_clerk" -> new Look(Villager.Type.PLAINS, Material.OAK_SAPLING);
            case "dungeon_gate" -> new Look(Villager.Type.SNOW, Material.ENDER_PEARL);
            case "amethyst_mines_guide" -> new Look(Villager.Type.TAIGA, Material.AMETHYST_SHARD);
            default -> new Look(Villager.Type.PLAINS, Material.STICK);
        };
    }

    private static JavaPlugin plugin() {
        return JavaPlugin.getProvidingPlugin(QuestNpcAppearance.class);
    }

    private record Look(Villager.Type type, Material hand) {
    }
}
