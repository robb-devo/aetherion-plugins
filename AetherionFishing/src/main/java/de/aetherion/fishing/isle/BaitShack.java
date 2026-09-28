package de.aetherion.fishing.isle;

import de.aetherion.fishing.FishingSkills;
import de.aetherion.items.AetherionItems;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tilly's Bait Shack: turns the lake's everyday catch (cod, salmon, kelp, tropical fish,
 * prismarine, pufferfish, compressed fish) into bait charges. "Plain" means a vanilla stack —
 * a trophy, a quest item or anything custom that happens to be a cod is never eaten.
 */
public final class BaitShack {

    public static final int MAX_CHARGES = 96;

    private final FishIsle isle;

    BaitShack(FishIsle isle) {
        this.isle = isle;
    }

    static boolean isPlain(ItemStack stack, Material material) {
        if (stack == null || stack.getType() != material) {
            return false;
        }
        if (!stack.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasCustomModelData() && meta.getPersistentDataContainer().isEmpty();
    }

    static int countPlain(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (isPlain(stack, material)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        try {
            AetherionItems items = AetherionItems.getInstance();
            return items == null ? null : items.getItemManager().getItemId(stack);
        } catch (LinkageError ignored) {
            return null;
        }
    }

    static int countCustom(PlayerInventory inventory, String id) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (id.equalsIgnoreCase(itemId(stack))) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Human-readable name for an Aetherion ingredient id. */
    static String customName(String id) {
        return switch (id) {
            case "compressed_cod" -> "Compressed Cod";
            case "compressed_salmon" -> "Compressed Salmon";
            case "compressed_prismarine_shard" -> "Compressed Prismarine";
            default -> id.replace('_', ' ');
        };
    }

    static String plainName(Material material) {
        String raw = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    /** What is still missing for one batch (empty = can make). */
    public List<String> missing(Player player, Bait bait) {
        List<String> out = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        for (Map.Entry<Material, Integer> entry : bait.plain().entrySet()) {
            int have = countPlain(inventory, entry.getKey());
            if (have < entry.getValue()) {
                out.add((entry.getValue() - have) + " " + plainName(entry.getKey()));
            }
        }
        for (Map.Entry<String, Integer> entry : bait.custom().entrySet()) {
            int have = countCustom(inventory, entry.getKey());
            if (have < entry.getValue()) {
                out.add((entry.getValue() - have) + " " + customName(entry.getKey()));
            }
        }
        if (bait.coins() > 0L && FishingSkills.balance(player) < bait.coins()) {
            out.add(LakeText.coins(bait.coins()) + " coins");
        }
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        if (profile.charges(bait) + bait.charges() > MAX_CHARGES) {
            out.add("room in the tin (max " + MAX_CHARGES + ")");
        }
        return out;
    }

    public boolean make(Player player, Bait bait) {
        List<String> missing = missing(player, bait);
        if (!missing.isEmpty()) {
            player.sendMessage("§eTilly §8» §fNeed " + String.join(", ", missing) + " for " + bait.display() + ", love.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 0.7f, 1.0f);
            return false;
        }
        if (bait.coins() > 0L && !FishingSkills.takeCoins(player, bait.coins())) {
            player.sendMessage("§cNot enough coins.");
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        for (Map.Entry<Material, Integer> entry : bait.plain().entrySet()) {
            takePlain(inventory, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : bait.custom().entrySet()) {
            takeCustom(inventory, entry.getKey(), entry.getValue());
        }
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        profile.bait.merge(bait, bait.charges(), Integer::sum);
        if (profile.activeBait() == null) {
            profile.activeBait = bait;
        }
        isle.profiles().markDirty();
        player.sendMessage("§eTilly §8» §f" + bait.charges() + "× " + bait.display() + " §7in your tin"
                + (profile.activeBait == bait ? " §8(on the hook)" : " §8(pick it on the board to use it)") + "§7.");
        player.playSound(player.getLocation(), Sound.BLOCK_COMPOSTER_FILL_SUCCESS, SoundCategory.PLAYERS, 0.8f, 1.1f);
        return true;
    }

    /** Put {@code bait} on the hook (null = fish without bait). */
    public void use(Player player, Bait bait) {
        AnglerProfiles.Profile profile = isle.profiles().of(player);
        if (bait != null && profile.charges(bait) <= 0) {
            player.sendMessage("§7You have no " + bait.display() + ".");
            return;
        }
        profile.activeBait = bait;
        isle.profiles().markDirty();
        LakeText.bar(player, bait == null ? "§7Fishing without bait." : "§eOn the hook: §f" + bait.display()
                + " §8(" + profile.charges(bait) + ")");
    }

    private static void takePlain(PlayerInventory inventory, Material material, int amount) {
        int left = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!isPlain(stack, material)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            if (take >= stack.getAmount()) {
                contents[slot] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
        inventory.setStorageContents(contents);
    }

    private static void takeCustom(PlayerInventory inventory, String id, int amount) {
        int left = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!id.equalsIgnoreCase(itemId(stack))) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            if (take >= stack.getAmount()) {
                contents[slot] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
        inventory.setStorageContents(contents);
    }
}
