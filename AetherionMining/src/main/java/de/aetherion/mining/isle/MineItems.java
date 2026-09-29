package de.aetherion.mining.isle;

import de.aetherion.items.core.ItemKeys;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Inventory counting for contracts and the Hearth. "Plain" ore is vanilla stacks only — a custom
 * item that happens to be a diamond (specimen, compressed, quest item) is never eaten.
 */
final class MineItems {

    private MineItems() {
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

    static void takePlain(PlayerInventory inventory, Material material, int amount) {
        int left = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!isPlain(stack, material)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            stack.setAmount(stack.getAmount() - take);
            contents[slot] = stack.getAmount() <= 0 ? null : stack;
        }
        inventory.setStorageContents(contents);
    }

    static String itemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        try {
            return stack.getItemMeta().getPersistentDataContainer().get(ItemKeys.item(), PersistentDataType.STRING);
        } catch (LinkageError ignored) {
            return null;
        }
    }

    static int countById(PlayerInventory inventory, String id) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (id != null && id.equals(itemId(stack))) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    static void takeById(PlayerInventory inventory, String id, int amount) {
        int left = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !id.equals(itemId(stack))) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            stack.setAmount(stack.getAmount() - take);
            contents[slot] = stack.getAmount() <= 0 ? null : stack;
        }
        inventory.setStorageContents(contents);
    }

    /** First storage slot holding a specimen (of {@code ore}, or any when null); -1 when none. */
    static int firstSpecimen(PlayerInventory inventory, CrystalFinds crystals, IsleOre ore) {
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            IsleOre found = crystals.oreOf(contents[slot]);
            if (found != null && (ore == null || found == ore)) {
                return slot;
            }
        }
        return -1;
    }

    static int countSpecimens(PlayerInventory inventory, CrystalFinds crystals, IsleOre ore) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            IsleOre found = crystals.oreOf(stack);
            if (found != null && (ore == null || found == ore)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** First storage slot holding a specimen of at least {@code min} grade (any ore); -1 when none. */
    static int firstSpecimenAtLeast(PlayerInventory inventory, CrystalFinds crystals, Grade min) {
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            Grade grade = crystals.gradeOf(contents[slot]);
            if (grade != null && crystals.oreOf(contents[slot]) != null && grade.ordinal() >= min.ordinal()) {
                return slot;
            }
        }
        return -1;
    }

    static int countSpecimensAtLeast(PlayerInventory inventory, CrystalFinds crystals, Grade min) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            Grade grade = crystals.gradeOf(stack);
            if (grade != null && crystals.oreOf(stack) != null && grade.ordinal() >= min.ordinal()) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Takes {@code amount} specimens of at least {@code min} grade, lowest grade first. */
    static void takeSpecimensAtLeast(PlayerInventory inventory, CrystalFinds crystals, Grade min, int amount) {
        int left = amount;
        for (Grade grade : Grade.values()) {
            if (grade.ordinal() < min.ordinal()) {
                continue;
            }
            ItemStack[] contents = inventory.getStorageContents();
            for (int slot = 0; slot < contents.length && left > 0; slot++) {
                ItemStack stack = contents[slot];
                if (stack == null || crystals.gradeOf(stack) != grade || crystals.oreOf(stack) == null) {
                    continue;
                }
                int take = Math.min(left, stack.getAmount());
                left -= take;
                stack.setAmount(stack.getAmount() - take);
                contents[slot] = stack.getAmount() <= 0 ? null : stack;
            }
            inventory.setStorageContents(contents);
            if (left <= 0) {
                return;
            }
        }
    }

    /** Takes the cheapest matching specimen, so a Heartstone is never eaten when a Rough would do. */
    static void takeSpecimen(PlayerInventory inventory, CrystalFinds crystals, IsleOre ore) {
        int slot = -1;
        long cheapest = Long.MAX_VALUE;
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            IsleOre found = crystals.oreOf(contents[i]);
            if (found == null || (ore != null && found != ore)) {
                continue;
            }
            long value = crystals.valueOf(contents[i]);
            if (value < cheapest) {
                cheapest = value;
                slot = i;
            }
        }
        if (slot < 0) {
            return;
        }
        ItemStack stack = inventory.getItem(slot);
        if (stack != null) {
            stack.setAmount(stack.getAmount() - 1);
            inventory.setItem(slot, stack.getAmount() <= 0 ? null : stack);
        }
    }
}
