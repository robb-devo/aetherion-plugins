package de.aetherion.items.listener;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HealthListener implements Listener {

    private static final double VANILLA_HEALTH = 20.0;

    private final JavaPlugin plugin;
    private final ItemManager itemManager;
    private final ActiveEquipmentStats equipmentStats;
    private final NamespacedKey storedHealthKey;
    private final Attribute maxHealthAttribute;

    private int regenPulse;
    private final Map<UUID, Integer> lastDamageTick = new ConcurrentHashMap<>();

    public HealthListener(
            JavaPlugin plugin,
            ItemManager itemManager
    ) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.equipmentStats =
                new ActiveEquipmentStats(
                        itemManager
                );
        this.storedHealthKey = new NamespacedKey(plugin, "stored_player_health");
        this.maxHealthAttribute = resolveMaxHealthAttribute();

        plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::tickHealthDisplay,
                5L,
                5L
        );
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onPlayerJoin(
            PlayerJoinEvent event
    ) {
        Player player = event.getPlayer();
        itemManager.refreshPlayerItems(player);
        updateHealth(player, ApplyMode.RESTORE);
        updateSpeed(player);
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onPlayerRespawn(
            PlayerRespawnEvent event
    ) {
        Player player = event.getPlayer();

        plugin.getServer().getScheduler().runTask(
                plugin,
                () -> {
                    updateHealth(player, ApplyMode.FILL);
                    updateSpeed(player);
                    itemManager.refreshPlayerItems(player);
                    updateHealth(player, ApplyMode.FILL);
                }
        );
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onArmorChange(
            PlayerArmorChangeEvent event
    ) {
        Player player = event.getPlayer();

        plugin.getServer().getScheduler().runTask(
                plugin,
                () -> {
                    updateHealth(player, ApplyMode.NORMAL);
                    updateSpeed(player);
                }
        );
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onItemHeld(
            PlayerItemHeldEvent event
    ) {
        plugin.getServer().getScheduler().runTask(
                plugin,
                () -> {
                    updateHealth(event.getPlayer(), ApplyMode.NORMAL);
                    updateSpeed(event.getPlayer());
                }
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHands(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        plugin.getServer().getScheduler().runTask(
                plugin,
                () -> {
                    updateHealth(event.getPlayer(), ApplyMode.NORMAL);
                    updateSpeed(event.getPlayer());
                }
        );
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastDamageTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
            storeHealth(player);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                storeHealth(player);
                showHealthBar(player);
            });
        }
    }

    @EventHandler(
            priority = EventPriority.HIGH,
            ignoreCancelled = true
    )
    public void onNaturalRegeneration(
            EntityRegainHealthEvent event
    ) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();

        if (reason == EntityRegainHealthEvent.RegainReason.SATIATED
                || reason == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> showHealthBar(player));
            return;
        }

        AttributeInstance maxHealth = maxHealthOf(player);
        if (maxHealth != null && maxHealth.getValue() > VANILLA_HEALTH + 0.05) {
            event.setAmount(event.getAmount() * (maxHealth.getValue() / VANILLA_HEALTH));
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            storeHealth(player);
            showHealthBar(player);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        storeHealth(player);
        lastDamageTick.remove(player.getUniqueId());
        player.setHealthScaled(false);
        AttributeInstance movement = movementSpeedOf(player);
        if (movement != null) {
            clearAetherionSpeedModifiers(movement);
        }
        player.setWalkSpeed(0.2f);
        resetNmsWalkSpeed(player, 0.1f);
    }

    /*
     * =====================================================
     * EXTERNAL REFRESH
     * =====================================================
     *
     * Allows other plugins, such as AetherMobs,
     * to immediately refresh the player's health
     * after equipment changes.
     */

    public void refreshHealth(
            Player player
    ) {

        if (player == null) {
            return;
        }

        updateHealth(player, ApplyMode.NORMAL);
        updateSpeed(player);
    }

    private void tickHealthDisplay() {
        regenPulse++;
        boolean naturalRegen = regenPulse % 16 == 0;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead()) {
                continue;
            }
            // updateHealth already stores + shows the bar; avoid doubling PDC/ActionBar work.
            updateHealth(player, ApplyMode.NORMAL);
            if (naturalRegen) {
                applyScaledNaturalRegen(player);
            }
            reconcileStoredHealth(player);
        }
    }

    private void updateHealth(Player player) {
        updateHealth(player, ApplyMode.NORMAL);
    }

    private void updateHealth(Player player, ApplyMode mode) {
        AttributeInstance maxHealth = maxHealthOf(player);

        if (maxHealth == null) {
            return;
        }

        double bonusHealth =
                equipmentStats.getStat(
                        player,
                        ItemCapability.HEALTH
                );

        double newMaxHealth = VANILLA_HEALTH + bonusHealth;
        double oldMax = maxHealth.getValue();
        double oldCurrent = player.getHealth();

        if (Math.abs(maxHealth.getBaseValue() - newMaxHealth) > 0.01) {
            try {
                maxHealth.setBaseValue(newMaxHealth);
            } catch (IllegalArgumentException exception) {
                try {
                    maxHealth.setBaseValue(Math.min(newMaxHealth, 1024.0));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Could not apply max health " + formatHp(newMaxHealth)
                            + " for " + player.getName());
                }
            }
        }

        double appliedMax = maxHealth.getValue();
        double desired = oldCurrent;

        if (mode == ApplyMode.FILL) {
            desired = appliedMax;
        } else if (mode == ApplyMode.RESTORE) {
            Double stored = storedHealth(player);
            if (stored != null) {
                desired = stored;
            } else if (oldCurrent <= VANILLA_HEALTH + 0.5 && appliedMax > VANILLA_HEALTH + 0.5) {
                desired = appliedMax;
            }
        } else {
            double gained = appliedMax - oldMax;
            if (gained > 0.01) {
                desired = oldCurrent + gained;
            }
        }

        setPlayerHealth(player, Math.min(appliedMax, desired));
        applyHealthScale(player, appliedMax);
        storeHealth(player);
        showHealthBar(player);
    }

    private void applyHealthScale(Player player, double maxHealth) {
        if (maxHealth > VANILLA_HEALTH + 0.05) {
            /*
             * Exactly 10 hearts. Fill amount is current/max, so 271/271 is a
             * full row and 135/271 is five hearts — not a single leftover half.
             */
            player.setHealthScaled(true);
            player.setHealthScale(VANILLA_HEALTH);
            player.sendHealthUpdate();
            return;
        }

        if (player.isHealthScaled()) {
            player.setHealthScaled(false);
            player.sendHealthUpdate();
        }
    }

    public void heal(Player player, double amount) {
        if (player == null || amount <= 0 || !player.isOnline() || player.isDead()) {
            return;
        }
        AttributeInstance maxHealth = maxHealthOf(player);
        if (maxHealth == null) {
            return;
        }
        setPlayerHealth(player, Math.min(maxHealth.getValue(), player.getHealth() + amount));
        storeHealth(player);
    }

    private void applyScaledNaturalRegen(Player player) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        if (player.getFoodLevel() < 18) {
            return;
        }

        AttributeInstance maxHealth = maxHealthOf(player);
        if (maxHealth == null) {
            return;
        }

        double max = maxHealth.getValue();
        double current = player.getHealth();
        if (current >= max - 0.01) {
            return;
        }

        double amount = max / VANILLA_HEALTH;
        setPlayerHealth(player, Math.min(max, current + amount));
        player.setExhaustion(player.getExhaustion() + 6.0f);
    }

    private void showHealthBar(Player player) {
        if (!player.isOnline()) {
            return;
        }

        AttributeInstance maxHealth = maxHealthOf(player);
        double max = maxHealth == null ? VANILLA_HEALTH : Math.max(VANILLA_HEALTH, maxHealth.getValue());
        double current = Math.max(0.0, Math.min(player.getHealth(), max));
        double defense = equipmentStats.getStat(player, ItemCapability.DEFENSE);

        Component health = Component.text()
                .append(Component.text("❤ ", NamedTextColor.RED))
                .append(Component.text(formatHp(current), NamedTextColor.WHITE))
                .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                .append(Component.text(formatHp(max), NamedTextColor.RED))
                .build();

        if (defense > 0.05) {
            Component defenseHud = Component.text()
                    .append(Component.text("🛡 ", NamedTextColor.AQUA))
                    .append(Component.text(formatHp(defense), NamedTextColor.WHITE))
                    .build();
            player.sendActionBar(health.append(Component.text("                      ")).append(defenseHud));
            return;
        }

        player.sendActionBar(health);
    }

    private void setPlayerHealth(Player player, double amount) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        AttributeInstance maxHealth = maxHealthOf(player);
        if (maxHealth == null) {
            return;
        }
        double max = maxHealth.getValue();
        double clamped = Math.max(0.01, Math.min(max, amount));
        try {
            if (Math.abs(player.getHealth() - clamped) > 0.01) {
                player.setHealth(clamped);
            }
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning(
                    "Could not set health for " + player.getName()
                            + " to " + formatHp(clamped) + " / " + formatHp(max)
            );
        }
        player.sendHealthUpdate();
    }

    private void storeHealth(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        player.getPersistentDataContainer().set(
                storedHealthKey,
                PersistentDataType.DOUBLE,
                player.getHealth()
        );
    }

    private Double storedHealth(Player player) {
        if (player == null) {
            return null;
        }
        return player.getPersistentDataContainer().get(storedHealthKey, PersistentDataType.DOUBLE);
    }

    private void reconcileStoredHealth(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        Double stored = storedHealth(player);
        if (stored == null) {
            return;
        }
        AttributeInstance maxHealth = maxHealthOf(player);
        if (maxHealth == null) {
            return;
        }
        double max = maxHealth.getValue();
        double current = player.getHealth();
        Integer damagedAt = lastDamageTick.get(player.getUniqueId());
        if (damagedAt != null && Bukkit.getCurrentTick() - damagedAt <= 15) {
            return;
        }
        /*
         * If gear raised max HP but vanilla current stayed at 20, the heart
         * row collapses to a half heart. Put the stored / scaled HP back.
         */
        if (current <= VANILLA_HEALTH + 0.5
                && stored > current + 0.5
                && max > VANILLA_HEALTH + 0.5) {
            setPlayerHealth(player, Math.min(max, stored));
        }
    }

    private AttributeInstance maxHealthOf(Player player) {
        if (player == null || maxHealthAttribute == null) {
            return null;
        }
        return player.getAttribute(maxHealthAttribute);
    }

    private static Attribute resolveMaxHealthAttribute() {
        try {
            return Attribute.valueOf("MAX_HEALTH");
        } catch (IllegalArgumentException ignored) {
            return Attribute.GENERIC_MAX_HEALTH;
        }
    }

    private String formatHp(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((int) Math.rint(value));
        }

        return String.format(Locale.US, "%.1f", value);
    }

    private void updateSpeed(Player player) {
        AttributeInstance movementSpeed = movementSpeedOf(player);
        if (movementSpeed == null) {
            return;
        }

        var speedKey = ItemKeys.key("movement_speed");
        var paceKey = ItemKeys.key("world_pace");

        // Always strip our keys first, then any legacy aetherion speed/pace/movement mods.
        try {
            movementSpeed.removeModifier(speedKey);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            movementSpeed.removeModifier(paceKey);
        } catch (IllegalArgumentException ignored) {
        }
        clearAetherionSpeedModifiers(movementSpeed);

        // Reset FOV walk-speed baseline before re-sync (avoids sticky inflated walk speed).
        player.setWalkSpeed(0.2f);
        resetNmsWalkSpeed(player, 0.1f);

        // Gear / pets / boots boosters / item SPEED only — skills never contribute.
        double speed = Math.max(0.0, equipmentStats.getStat(player, ItemCapability.SPEED));

        if (speed > 0.0) {
            movementSpeed.addModifier(
                    new AttributeModifier(
                            speedKey,
                            speed / 100.0,
                            AttributeModifier.Operation.ADD_SCALAR
                    )
            );
        }

        // Silent world pace from Aetherion Lv.3+ — fixed once, does NOT scale with further levels.
        if (accountLevel(player) >= 3) {
            movementSpeed.addModifier(
                    new AttributeModifier(
                            paceKey,
                            0.36,
                            AttributeModifier.Operation.ADD_SCALAR
                    )
            );
        }

        if (speed <= 0.0 && accountLevel(player) < 3) {
            player.setWalkSpeed(0.2f);
            resetNmsWalkSpeed(player, 0.1f);
        } else {
            syncWalkSpeedForFov(player, movementSpeed.getValue());
        }
        updateJump(player);
        updateAttackSpeed(player);
    }

    private static AttributeInstance movementSpeedOf(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            return attr;
        }
        try {
            return player.getAttribute(Attribute.valueOf("MOVEMENT_SPEED"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void clearAetherionSpeedModifiers(AttributeInstance movementSpeed) {
        String ns = de.aetherion.items.AetherionItems.NAMESPACE;
        movementSpeed.getModifiers().stream()
                .filter(modifier -> {
                    var key = modifier.getKey();
                    if (key == null) {
                        return false;
                    }
                    if (!ns.equalsIgnoreCase(key.getNamespace())) {
                        return false;
                    }
                    String name = key.getKey();
                    return name.contains("speed") || name.contains("pace") || name.contains("movement");
                })
                .toList()
                .forEach(movementSpeed::removeModifier);
    }

    private static void resetNmsWalkSpeed(Player player, float walkingNms) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object abilities = handle.getClass().getMethod("getAbilities").invoke(handle);
            for (java.lang.reflect.Method method : abilities.getClass().getMethods()) {
                if (method.getName().equals("setWalkingSpeed") && method.getParameterCount() == 1) {
                    method.invoke(abilities, walkingNms);
                    break;
                }
            }
            try {
                handle.getClass().getMethod("onUpdateAbilities").invoke(handle);
            } catch (NoSuchMethodException ignored) {
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }
    private void updateJump(Player player) {
        AttributeInstance jump = jumpAttribute(player);
        if (jump == null) {
            return;
        }
        var jumpKey = ItemKeys.key("world_pace_jump");
        jump.getModifiers().stream()
                .filter(modifier -> jumpKey.equals(modifier.getKey()))
                .toList()
                .forEach(jump::removeModifier);
        if (accountLevel(player) < 3) {
            return;
        }
        // Tiny bump — readable in big maps, not bunny-hop.
        jump.addModifier(new AttributeModifier(
                jumpKey,
                0.055,
                AttributeModifier.Operation.ADD_NUMBER
        ));
    }

    private static int accountLevel(Player player) {
        try {
            de.aetherion.items.AetherionItems items = de.aetherion.items.AetherionItems.getInstance();
            if (items == null || items.getSkills() == null) {
                return 1;
            }
            return items.getSkills().accountLevel(player);
        } catch (NoClassDefFoundError ignored) {
            return 1;
        }
    }

    private static AttributeInstance jumpAttribute(Player player) {
        try {
            return player.getAttribute(Attribute.valueOf("JUMP_STRENGTH"));
        } catch (IllegalArgumentException ignored) {
            try {
                return player.getAttribute(Attribute.valueOf("GENERIC_JUMP_STRENGTH"));
            } catch (IllegalArgumentException ignoredAgain) {
                return null;
            }
        }
    }

    private void updateAttackSpeed(Player player) {
        AttributeInstance attack = player.getAttribute(attackSpeedAttribute());
        if (attack == null) {
            return;
        }
        var key = ItemKeys.key("thermal_attack_speed");
        attack.getModifiers().stream()
                .filter(modifier -> key.equals(modifier.getKey()))
                .toList()
                .forEach(attack::removeModifier);
        if (!de.aetherion.items.item.AccessoryItems.holdingOffhand(player, "thermal_core")) {
            return;
        }
        Long until = player.getPersistentDataContainer().get(
                ItemKeys.charmSuppressedUntil(),
                PersistentDataType.LONG
        );
        if (until != null && System.currentTimeMillis() < until) {
            return;
        }
        attack.addModifier(new AttributeModifier(key, 0.42, AttributeModifier.Operation.ADD_SCALAR));
    }

    private static Attribute attackSpeedAttribute() {
        try {
            return Attribute.valueOf("ATTACK_SPEED");
        } catch (IllegalArgumentException ignored) {
            return Attribute.GENERIC_ATTACK_SPEED;
        }
    }

    /**
     * Minecraft zooms FOV from movementSpeed / walkingSpeed.
     * Match walkingSpeed to the real attribute so high Speed stats
     * stay fast without the Hypixel-breaking fish-eye warp.
     */
    private void syncWalkSpeedForFov(Player player, double movementAttribute) {
        double vanilla = 0.1;
        double extra = Math.max(0.0, movementAttribute / vanilla - 1.0);
        double fovExtra = extra * 0.16;
        double walkingNms = fovExtra <= 0
                ? vanilla
                : movementAttribute / (1.0 + fovExtra);

        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object abilities = handle.getClass().getMethod("getAbilities").invoke(handle);
            for (java.lang.reflect.Method method : abilities.getClass().getMethods()) {
                if (method.getName().equals("setWalkingSpeed") && method.getParameterCount() == 1) {
                    method.invoke(abilities, (float) walkingNms);
                    break;
                }
            }
            try {
                handle.getClass().getMethod("onUpdateAbilities").invoke(handle);
            } catch (NoSuchMethodException ignored) {
            }
        } catch (ReflectiveOperationException ignored) {
            float bukkit = (float) Math.max(0.1, Math.min(1.0, walkingNms * 2.0));
            player.setWalkSpeed(bukkit);
        }
    }

    private enum ApplyMode {
        NORMAL,
        FILL,
        RESTORE
    }
}
