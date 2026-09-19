package de.aetherion.bossengine.loot;

import de.aetherion.bossengine.integration.AetherMobsBridge;
import de.aetherion.bossengine.integration.AetherionItemBridge;
import de.aetherion.bossengine.model.LootEntry;
import de.aetherion.bossengine.model.LootSource;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class LootFactory {

    private final AetherionItemBridge itemBridge;
    private final AetherMobsBridge mobsBridge;

    public LootFactory(AetherionItemBridge itemBridge, AetherMobsBridge mobsBridge) {
        this.itemBridge = itemBridge;
        this.mobsBridge = mobsBridge;
    }

    public Optional<ItemStack> create(LootEntry entry, double share) {
        return create(entry, share, null);
    }

    public Optional<ItemStack> create(LootEntry entry, double share, Player player) {
        if (entry == null) {
            return Optional.empty();
        }
        double chance = entry.getChance();
        if (player != null && mobsBridge != null) {
            chance = Math.min(1.0, chance * Math.max(1.0, mobsBridge.bossDropMultiplier(player)));
        }
        if (ThreadLocalRandom.current().nextDouble() > chance) {
            return Optional.empty();
        }

        int amount = rollAmount(entry, share);
        if (amount <= 0) {
            return Optional.empty();
        }

        if (entry.getSource() == LootSource.AETHERION) {
            return itemBridge.create(entry.getItemId(), amount);
        }

        if (entry.getSource() == LootSource.AETHERMOBS) {
            return mobsBridge.createCatchSphere(sphereId(entry.getItemId()), amount);
        }

        if (entry.getMaterial() == null || entry.getMaterial().isAir()) {
            return Optional.empty();
        }
        return Optional.of(new ItemStack(entry.getMaterial(), amount));
    }

    private int rollAmount(LootEntry entry, double share) {
        int min = entry.getMinAmount();
        int max = entry.getMaxAmount();
        int rolled = min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        if (!entry.isScaleWithDamage()) {
            return rolled;
        }
        return Math.max(1, (int) Math.round(rolled * Math.max(0.05, share)));
    }

    private static String sphereId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "epic";
        }
        String raw = itemId.toLowerCase();
        if (raw.startsWith("catch_sphere_")) {
            return raw.substring("catch_sphere_".length());
        }
        return raw;
    }
}
