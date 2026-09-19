package de.aetherion.stressbots.role;

import de.aetherion.core.api.AetherServices;
import de.aetherion.core.api.PetAccess;
import de.aetherion.core.api.PetCatalogItem;
import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;
import de.aetherion.items.model.BoosterApplier;
import de.aetherion.items.model.BoosterType;
import de.aetherion.items.progress.ProgressionService;
import de.aetherion.items.skill.AetherSkill;
import de.aetherion.items.skill.SkillService;
import de.aetherion.stressbots.AetherionStressBots;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

/**
 * Player-like extras after a role kit: mixed-tier gear, equipped skills (so activity XP
 * actually lands), item boosters, optional pet follow, starter coins, progression flags.
 * Does <em>not</em> call {@code /skills setlevel} — bots still grind by doing the work.
 */
public final class BotPlaystyle {

    private BotPlaystyle() {
    }

    public static void enrich(AetherionStressBots plugin, Player player, BotRole role, CustomItem items) {
        if (plugin == null || player == null || role == null || items == null) {
            return;
        }
        int index = nameIndex(player);
        int tier = gearTier(index);
        kitGear(player.getInventory(), items, role, tier);
        applyBoosters(plugin, player, role, items, index);
        equipSkills(plugin, player, role, index);
        unlockProgress(plugin, player, role);
        seedCoins(plugin, player, role, index);
        maybePet(plugin, player, role, index);
        if (role == BotRole.CATCH && index >= 3) {
            giveSphere(player, "rare", 8);
        }
        player.updateInventory();
    }

    public static int gearTier(Player player) {
        return gearTier(nameIndex(player));
    }

    static int gearTier(int index) {
        if (index <= 1) {
            return 1;
        }
        if (index == 2) {
            return 2;
        }
        if (index <= 4) {
            return 3;
        }
        if (index == 5) {
            return 4;
        }
        return 2 + (Math.abs(index) % 3);
    }

    static int nameIndex(Player player) {
        if (player == null || player.getName() == null) {
            return 1;
        }
        String digits = player.getName().replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(digits));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    public static void kitMining(PlayerInventory inv, CustomItem items, int tier) {
        int t = clamp(tier, 1, 5);
        inv.setHelmet(miningHelmet(items, t));
        inv.setChestplate(miningChest(items, t));
        inv.setLeggings(miningLegs(items, t));
        inv.setBoots(miningBoots(items, t));
        inv.setItemInMainHand(miningPick(items, t));
        BotRoleRegistry.giveSpare(inv, miningPick(items, Math.max(1, t - 1)));
    }

    public static void kitCombat(PlayerInventory inv, CustomItem items, int tier, boolean spare) {
        int t = clamp(tier, 1, 5);
        inv.setHelmet(combatHelmet(items, t));
        inv.setChestplate(combatChest(items, t));
        inv.setLeggings(combatLegs(items, t));
        inv.setBoots(combatBoots(items, t));
        inv.setItemInMainHand(combatSword(items, t));
        if (spare) {
            BotRoleRegistry.giveSpare(inv, combatSword(items, Math.max(1, t - 1)));
        }
    }

    public static void kitForaging(PlayerInventory inv, CustomItem items, int tier) {
        int t = clamp(tier, 1, 5);
        inv.setHelmet(items.foraging().helmet(t));
        inv.setChestplate(items.foraging().chestplate(t));
        inv.setLeggings(items.foraging().leggings(t));
        inv.setBoots(items.foraging().boots(t));
        inv.setItemInMainHand(items.foraging().axe(t));
        BotRoleRegistry.giveSpare(inv, items.foraging().axe(Math.max(1, t - 1)));
    }

    public static void kitFishing(PlayerInventory inv, CustomItem items, int tier) {
        int t = clamp(tier, 1, 5);
        inv.setHelmet(items.fishing().helmet(t));
        inv.setChestplate(items.fishing().chestplate(t));
        inv.setLeggings(items.fishing().leggings(t));
        inv.setBoots(items.fishing().boots(t));
        inv.setItemInMainHand(items.fishing().rod(t));
        BotRoleRegistry.giveSpare(inv, items.fishing().rod(Math.max(1, t - 1)));
    }

    public static void kitCatcher(PlayerInventory inv, CustomItem items, int tier) {
        int t = clamp(tier, 1, 3);
        inv.setHelmet(items.catcher().helmet(t));
        inv.setChestplate(items.catcher().chestplate(t));
        inv.setLeggings(items.catcher().leggings(t));
        inv.setBoots(items.catcher().boots(t));
        inv.setItemInMainHand(items.catcher().gaff(t));
    }

    private static void kitGear(PlayerInventory inv, CustomItem items, BotRole role, int tier) {
        switch (role) {
            case MINE, MINING -> kitMining(inv, items, tier);
            case FORAGE -> kitForaging(inv, items, tier);
            case CATCH -> kitCatcher(inv, items, tier);
            case FISH -> kitFishing(inv, items, tier);
            case COMBAT -> kitCombat(inv, items, tier, true);
            default -> kitCombat(inv, items, Math.min(3, tier), false);
        }
    }

    private static void applyBoosters(
            AetherionStressBots plugin,
            Player player,
            BotRole role,
            CustomItem items,
            int index
    ) {
        AetherionItems itemsPlugin = AetherionItems.getInstance();
        if (itemsPlugin == null || itemsPlugin.getItemManager() == null) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        BoosterType primary = boosterFor(role, index);
        BoosterApplier.Status status = BoosterApplier.apply(
                itemsPlugin.getItemManager(),
                tool,
                primary,
                1 + (index % 3 == 0 ? 1 : 0)
        );
        if (status == BoosterApplier.Status.APPLIED) {
            plugin.getActivity().markAction(player, "booster " + primary.name().toLowerCase());
        }
        if (index % 3 != 1) {
            ItemStack spare = items.boosters() == null ? null : items.boosters().byId(primary.name().toLowerCase() + "_booster");
            if (spare != null) {
                player.getInventory().addItem(spare);
            }
        }
        if (index % 5 == 0 && itemsPlugin.xpBoost() != null) {
            itemsPlugin.xpBoost().addPlaytime(player, 20L * 60L * 1000L);
        }
    }

    private static BoosterType boosterFor(BotRole role, int index) {
        return switch (role) {
            case MINE, MINING -> index % 2 == 0 ? BoosterType.COAL : BoosterType.EMERALD;
            case FORAGE -> index % 2 == 0 ? BoosterType.IRON : BoosterType.EMERALD;
            case FISH -> BoosterType.GOLD;
            case CATCH -> BoosterType.WHEAT;
            case COMBAT -> index % 2 == 0 ? BoosterType.REDSTONE : BoosterType.OAK;
            default -> index % 2 == 0 ? BoosterType.LAPIS : BoosterType.GLOWSTONE;
        };
    }

    private static void equipSkills(AetherionStressBots plugin, Player player, BotRole role, int index) {
        AetherionItems itemsPlugin = AetherionItems.getInstance();
        if (itemsPlugin == null || itemsPlugin.getSkills() == null) {
            return;
        }
        SkillService skills = itemsPlugin.getSkills();
        skills.grantBonusSlots(player, 2);
        for (AetherSkill skill : skillsFor(role, index)) {
            skills.equip(player, skill);
        }
        plugin.getActivity().markAction(player, "skills equipped");
    }

    private static AetherSkill[] skillsFor(BotRole role, int index) {
        boolean alt = index % 2 == 0;
        return switch (role) {
            case MINE, MINING -> alt
                    ? new AetherSkill[] {AetherSkill.ROCK_WHISPER, AetherSkill.EXTRA_POCKET, AetherSkill.PACK_RAT}
                    : new AetherSkill[] {AetherSkill.QUARRY_MANNERS, AetherSkill.CAVE_SENSE, AetherSkill.SPREAD_SHEET};
            case FORAGE -> alt
                    ? new AetherSkill[] {AetherSkill.WOODWISE, AetherSkill.TIMBER_TAX, AetherSkill.GREEN_THUMB}
                    : new AetherSkill[] {AetherSkill.WOODWISE, AetherSkill.LIGHT_FOOT, AetherSkill.GREEN_THUMB};
            case CATCH -> new AetherSkill[] {AetherSkill.LUCKY_STREAK, AetherSkill.QUICK_HANDS, AetherSkill.LIGHT_FOOT};
            case FISH -> alt
                    ? new AetherSkill[] {AetherSkill.BITE_ME, AetherSkill.SHORT_CAST, AetherSkill.FISH_LEDGER}
                    : new AetherSkill[] {AetherSkill.SHORT_CAST, AetherSkill.BITE_ME, AetherSkill.QUICK_HANDS};
            case COMBAT -> alt
                    ? new AetherSkill[] {AetherSkill.HEAVY_HANDS, AetherSkill.MEAN_STREAK, AetherSkill.BLOOD_TAX}
                    : new AetherSkill[] {AetherSkill.HEAVY_HANDS, AetherSkill.LIFE_ABSORB, AetherSkill.THICK_SKIN};
            case TRADE -> new AetherSkill[] {AetherSkill.PINCH_PENNY, AetherSkill.GOLDEN_HOUR, AetherSkill.LUCKY_STREAK};
            case QUEST -> new AetherSkill[] {AetherSkill.QUIET_PRIDE, AetherSkill.NIGHT_OWL, AetherSkill.QUICK_HANDS};
            case ROAM, PAD -> new AetherSkill[] {AetherSkill.LIGHT_FOOT, AetherSkill.QUIET_PRIDE, AetherSkill.IRON_STOMACH};
        };
    }

    private static void unlockProgress(AetherionStressBots plugin, Player player, BotRole role) {
        AetherionItems itemsPlugin = AetherionItems.getInstance();
        if (itemsPlugin == null || itemsPlugin.progress() == null) {
            return;
        }
        ProgressionService progress = itemsPlugin.progress();
        progress.unlock(player, ProgressionService.Flag.SKILLS);
        if (role == BotRole.CATCH || role == BotRole.ROAM || role == BotRole.PAD) {
            progress.unlock(player, ProgressionService.Flag.PETS);
        }
        if (role == BotRole.TRADE) {
            progress.unlock(player, ProgressionService.Flag.TRADER);
            plugin.getActivity().markAction(player, "trader flag");
        }
    }

    private static void seedCoins(AetherionStressBots plugin, Player player, BotRole role, int index) {
        AetherionItems itemsPlugin = AetherionItems.getInstance();
        if (itemsPlugin == null || itemsPlugin.getCoins() == null) {
            return;
        }
        long amount = switch (role) {
            case TRADE -> 400L + index * 80L;
            case QUEST -> 120L + index * 20L;
            default -> 40L + index * 15L;
        };
        itemsPlugin.getCoins().add(player, amount);
    }

    private static void maybePet(AetherionStressBots plugin, Player player, BotRole role, int index) {
        PetAccess pets = AetherServices.pets();
        if (pets == null) {
            return;
        }
        String petId = pickPetId(pets, role, index);
        int mode = index % 4;
        if (role == BotRole.CATCH) {
            mode = index % 3 == 0 ? 1 : 2;
        }
        boolean ok = switch (mode) {
            case 0 -> false;
            case 1 -> pets.equipDevPet(player, petId);
            case 2 -> pets.giveDevPet(player, petId);
            default -> pets.spawnDevPet(player, petId);
        };
        if (mode != 0) {
            AetherionItems itemsPlugin = AetherionItems.getInstance();
            if (itemsPlugin != null && itemsPlugin.progress() != null) {
                itemsPlugin.progress().unlock(player, ProgressionService.Flag.PETS);
            }
            plugin.getActivity().markAction(player, (ok ? "pet " : "pet miss ") + petId + " mode=" + mode);
        }
        if (mode == 1 || role == BotRole.CATCH) {
            ItemStack treat = pets.petExpTreat(1);
            if (treat != null) {
                player.getInventory().addItem(treat);
            }
        }
    }

    private static String pickPetId(PetAccess pets, BotRole role, int index) {
        List<PetCatalogItem> catalog = pets.pets();
        if (catalog != null && !catalog.isEmpty()) {
            return catalog.get(Math.floorMod(index + role.ordinal(), catalog.size())).id();
        }
        return switch (role) {
            case MINE, MINING -> "bat";
            case FORAGE -> "pig";
            case FISH -> "cod";
            case CATCH -> "wolf";
            case COMBAT -> "wolf";
            default -> "cow";
        };
    }

    private static void giveSphere(Player player, String id, int count) {
        PetAccess pets = AetherServices.pets();
        if (pets == null) {
            return;
        }
        ItemStack sphere = pets.catchSphere(id);
        if (sphere == null) {
            return;
        }
        sphere.setAmount(Math.min(64, Math.max(1, count)));
        player.getInventory().addItem(sphere);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ItemStack miningHelmet(CustomItem items, int tier) {
        return switch (tier) {
            case 2 -> items.createMiningHelmet2();
            case 3 -> items.createMiningHelmet3();
            case 4 -> items.createMiningHelmet4();
            case 5 -> items.createMiningHelmet5();
            default -> items.createMiningHelmet();
        };
    }

    private static ItemStack miningChest(CustomItem items, int tier) {
        return switch (tier) {
            case 2 -> items.createMiningChestplate2();
            case 3 -> items.createMiningChestplate3();
            case 4 -> items.createMiningChestplate4();
            case 5 -> items.createMiningChestplate5();
            default -> items.createMiningChestplate();
        };
    }

    private static ItemStack miningLegs(CustomItem items, int tier) {
        return switch (tier) {
            case 2 -> items.createMiningLeggings2();
            case 3 -> items.createMiningLeggings3();
            case 4 -> items.createMiningLeggings4();
            case 5 -> items.createMiningLeggings5();
            default -> items.createMiningLeggings();
        };
    }

    private static ItemStack miningBoots(CustomItem items, int tier) {
        return switch (tier) {
            case 2 -> items.createMiningBoots2();
            case 3 -> items.createMiningBoots3();
            case 4 -> items.createMiningBoots4();
            case 5 -> items.createMiningBoots5();
            default -> items.createMiningBoots();
        };
    }

    private static ItemStack miningPick(CustomItem items, int tier) {
        return switch (tier) {
            case 2 -> items.createMiningPickaxe2();
            case 3 -> items.createMiningPickaxe3();
            case 4 -> items.createMiningPickaxe4();
            case 5 -> items.createMiningPickaxe5();
            default -> items.createMiningPickaxe();
        };
    }

    private static ItemStack combatHelmet(CustomItem items, int tier) {
        return switch (tier) {
            case 2 -> items.createCombatHelmet2();
            case 3 -> items.createCombatHelmet3();
            case 4 -> items.createCombatHelmet4();
            case 5 -> items.createCombatHelmet5();
            default -> items.createCombatHelmet();
        };
    }

    private static ItemStack combatChest(CustomItem items, int tier) {
        return switch (tier) {
            case 2 -> items.createCombatChestplate2();
            case 3 -> items.createCombatChestplate3();
            case 4 -> items.createCombatChestplate4();
            case 5 -> items.createCombatChestplate5();
            default -> items.createCombatChestplate();
        };
    }

    private static ItemStack combatLegs(CustomItem items, int tier) {
        return switch (tier) {
            case 2 -> items.createCombatLeggings2();
            case 3 -> items.createCombatLeggings3();
            case 4 -> items.createCombatLeggings4();
            case 5 -> items.createCombatLeggings5();
            default -> items.createCombatLeggings();
        };
    }

    private static ItemStack combatBoots(CustomItem items, int tier) {
        return switch (tier) {
            case 2 -> items.createCombatBoots2();
            case 3 -> items.createCombatBoots3();
            case 4 -> items.createCombatBoots4();
            case 5 -> items.createCombatBoots5();
            default -> items.createCombatBoots();
        };
    }

    private static ItemStack combatSword(CustomItem items, int tier) {
        return switch (tier) {
            case 2 -> items.createCombatSword2();
            case 3 -> items.createCombatSword3();
            case 4 -> items.createCombatSword4();
            case 5 -> items.createCombatSword5();
            default -> items.createCombatSword();
        };
    }
}
