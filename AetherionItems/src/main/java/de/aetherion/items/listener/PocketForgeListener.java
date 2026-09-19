package de.aetherion.items.listener;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.economy.CompressedResource;
import de.aetherion.items.item.AccessoryItems;
import de.aetherion.items.manager.ItemManager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

public final class PocketForgeListener implements Listener {

    private static final int VANILLA_PER_COMPRESSED = de.aetherion.items.economy.EconomyCurve.RAW_PER_COMPRESSED;
    private static final int COMPRESSED_PER_COMPACTED = de.aetherion.items.economy.EconomyCurve.COMPRESSED_PER_COMPACTED;

    private final ItemManager items;

    public PocketForgeListener(AetherionItems plugin, ItemManager items) {
        this.items = items;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SURVIVAL) {
                continue;
            }
            if (!AccessoryItems.holdingOffhand(player, AccessoryItems.FORGE_ID)) {
                continue;
            }
            forge(player);
        }
    }

    private void forge(Player player) {
        PlayerInventory inventory = player.getInventory();
        int compressedMade = 0;
        int compactedMade = 0;
        for (CompressedResource resource : CompressedResource.values()) {
            int vanilla = countVanilla(inventory, resource);
            int compressed = countId(inventory, resource.compressedId());
            int newCompressed = vanilla / VANILLA_PER_COMPRESSED;
            int vanillaLeft = vanilla % VANILLA_PER_COMPRESSED;
            int compressedAfter = compressed + newCompressed;
            int newCompacted = compressedAfter / COMPRESSED_PER_COMPACTED;
            int compressedLeft = compressedAfter % COMPRESSED_PER_COMPACTED;
            if (newCompressed == 0 && newCompacted == 0) {
                continue;
            }
            removeVanilla(inventory, resource, vanilla);
            removeId(inventory, resource.compressedId(), compressed);
            giveVanilla(player, resource, vanillaLeft);
            give(player, resource.compressed(), compressedLeft);
            give(player, resource.compacted(), newCompacted);
            compressedMade += newCompressed;
            compactedMade += newCompacted;
        }
        if (compressedMade <= 0 && compactedMade <= 0) {
            return;
        }
        List<String> parts = new ArrayList<>();
        if (compressedMade > 0) {
            parts.add("§f" + compressedMade + " §7compressed");
        }
        if (compactedMade > 0) {
            parts.add("§b" + compactedMade + " §7compacted");
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "§6Pocket Forge §8· " + String.join(" §8· ", parts)
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 0.35f, 1.35f);
    }

    private int countVanilla(PlayerInventory inventory, CompressedResource resource) {
        int total = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isVanillaInput(stack, resource)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private int countId(PlayerInventory inventory, String itemId) {
        int total = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (matchesId(stack, itemId)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void removeVanilla(PlayerInventory inventory, CompressedResource resource, int amount) {
        int left = amount;
        for (int slot = 0; slot < 36 && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isVanillaInput(stack, resource)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                inventory.setItem(slot, null);
            }
            left -= take;
        }
    }

    private void removeId(PlayerInventory inventory, String itemId, int amount) {
        int left = amount;
        for (int slot = 0; slot < 36 && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!matchesId(stack, itemId)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                inventory.setItem(slot, null);
            }
            left -= take;
        }
    }

    private void giveVanilla(Player player, CompressedResource resource, int amount) {
        if (amount <= 0) {
            return;
        }
        give(player, new ItemStack(resource.input()), amount);
    }

    private void give(Player player, ItemStack template, int amount) {
        if (amount <= 0) {
            return;
        }
        int left = amount;
        int max = Math.max(1, template.getMaxStackSize());
        while (left > 0) {
            ItemStack stack = template.clone();
            int take = Math.min(max, left);
            stack.setAmount(take);
            var leftover = player.getInventory().addItem(stack);
            leftover.values().forEach(remain ->
                    player.getWorld().dropItemNaturally(player.getLocation(), remain));
            left -= take;
        }
    }

    private boolean isVanillaInput(ItemStack stack, CompressedResource resource) {
        if (stack == null || stack.getType().isAir() || stack.getType() != resource.input()) {
            return false;
        }
        return items.getItemId(stack) == null;
    }

    private boolean matchesId(ItemStack stack, String itemId) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return itemId.equalsIgnoreCase(items.getItemId(stack));
    }
}
