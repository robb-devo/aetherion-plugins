package de.aetherion.items.listener;

import de.aetherion.core.AetherEntities;
import de.aetherion.items.combat.UndeadCombat;
import de.aetherion.items.core.ItemKeys;
import de.aetherion.items.manager.ActiveEquipmentStats;
import de.aetherion.items.manager.ItemManager;
import de.aetherion.items.model.ItemCapability;
import de.aetherion.items.world.WildlifeLooks;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


/*
 * =========================================================
 * AETHERION DAMAGE LISTENER
 * =========================================================
 *
 * Zuständig für:
 *
 * - Aetherion Damage
 * - Redstone Attack Spread
 *
 *
 * =========================================================
 * ACTIVE EQUIPMENT
 * =========================================================
 *
 * Damage und Attack Spread werden ausschließlich aus
 * aktuell aktivem Aetherion-Equipment gelesen.
 *
 * Active Equipment:
 *
 * - Mainhand
 * - Helmet
 * - Chestplate
 * - Leggings
 * - Boots
 *
 * Es werden nur Stats berücksichtigt, die das jeweilige
 * Item laut ItemProfile tatsächlich besitzt.
 *
 *
 * =========================================================
 * DAMAGE
 * =========================================================
 *
 * Der Damage-Wert wird über ActiveEquipmentStats
 * ermittelt.
 *
 * Dadurch gilt:
 *
 * Kein Aetherion Equipment
 * -> kein Aetherion Damage
 *
 * Aetherion Weapon in Mainhand
 * -> Damage aktiv
 *
 * Aetherion Armor mit Damage
 * -> Damage ebenfalls aktiv
 *
 * Mehrere aktive Items
 * -> Damage wird addiert.
 *
 *
 * =========================================================
 * REDSTONE ATTACK SPREAD
 * =========================================================
 *
 * Attack Spread erzeugt zusätzliche Treffer auf
 * andere gültige lebende Entities.
 *
 * Beispiel:
 *
 * Attack Spread 0
 * -> nur ursprüngliches Ziel
 *
 * Attack Spread 100
 * -> 1 zusätzliches Ziel garantiert
 *
 * Attack Spread 250
 * -> 2 zusätzliche Ziele garantiert
 * -> 50% Chance auf ein drittes
 *
 *
 * Das ursprüngliche Ziel zählt NICHT als Spread-Ziel.
 *
 *
 * =========================================================
 * GÜLTIGE SPREAD-ZIELE
 * =========================================================
 *
 * Erlaubt:
 *
 * - lebende Entities
 *
 *
 * Ausgeschlossen:
 *
 * - Spieler
 * - Armor Stands
 * - NPCs
 * - invulnerable Entities
 * - tote Entities
 * - ungültige Entities
 * - ursprüngliches Ziel
 *
 *
 * =========================================================
 * SPREAD DAMAGE
 * =========================================================
 *
 * Jeder zusätzliche Treffer verursacht exakt denselben
 * Aetherion-Damage-Wert wie der ursprüngliche Treffer.
 *
 *
 * =========================================================
 * RECURSION
 * =========================================================
 *
 * Ein Spread-Treffer darf selbst keinen weiteren
 * Attack Spread auslösen.
 *
 * =========================================================
 */
public class DamageListener implements Listener {


    /*
     * =========================================================
     * ACTIVE EQUIPMENT STATS
     * =========================================================
     */

    private final ItemManager itemManager;

    private final ActiveEquipmentStats equipmentStats;


    /*
     * =========================================================
     * RECURSION PROTECTION
     * =========================================================
     *
     * Enthält Spieler, deren zusätzlicher
     * Attack-Spread-Schaden gerade verarbeitet wird.
     *
     * Dadurch löst ein Spread-Treffer keinen weiteren
     * Spread aus.
     *
     */

    private final Set<UUID> spreadProcessing =
            new HashSet<>();


    /*
     * =========================================================
     * SPREAD RADIUS
     * =========================================================
     *
     * Für die erste Implementierung bewusst großzügig.
     *
     * Balancing kommt später.
     *
     */

    private static final double SPREAD_RADIUS = 16.0;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public DamageListener(
            ItemManager itemManager
    ) {

        this.itemManager = itemManager;
        this.equipmentStats =
                new ActiveEquipmentStats(
                        itemManager
                );

    }


    /*
     * =========================================================
     * INCOMING DEFENSE
     * =========================================================
     *
     * Runs after outgoing Aetherion damage so PvP
     * still uses the Aetherion hit value.
     *
     * Reduction: 100 / (100 + defense * 0.85)
     * Combat III (~280 def) takes about 30% incoming
     * Combat V (~1120 def) takes about 10% incoming
     */

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onIncomingDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Byte trueDamage = player.getPersistentDataContainer().get(
                ItemKeys.trueDamage(),
                org.bukkit.persistence.PersistentDataType.BYTE
        );
        if ((trueDamage != null && trueDamage == 1) || de.aetherion.items.combat.IncomingHits.isRaw()) {
            return;
        }

        if (isUnmitigatedCause(event.getCause())) {
            return;
        }

        double defense = equipmentStats.getStat(player, ItemCapability.DEFENSE);
        de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
        if (plugin != null && plugin.xpBoost() != null) {
            defense *= plugin.xpBoost().defenseMultiplier(player);
        }

        // Borderlands tiers punch through a cut of Defense so T2 gear cannot trivialise them.
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity source = UndeadCombat.attackerOf(byEntity.getDamager());
            if (source instanceof LivingEntity living) {
                defense *= (1.0 - WildlifeLooks.armorPenetration(living));
            }
        }

        if (defense > 0.0) {
            event.setDamage(event.getDamage() * (100.0 / (100.0 + defense * 0.85)));
        }

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity source = UndeadCombat.attackerOf(byEntity.getDamager());
            if (UndeadCombat.isUndead(source)) {
                double resist = Math.min(70.0, Math.max(0.0,
                        equipmentStats.getStat(player, ItemCapability.UNDEAD_RESIST)));
                if (resist > 0.0) {
                    event.setDamage(event.getDamage() * (1.0 - resist / 100.0));
                }
            }
        }
    }

    private boolean isUnmitigatedCause(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.VOID
                || cause == EntityDamageEvent.DamageCause.KILL
                || cause == EntityDamageEvent.DamageCause.SUICIDE
                || cause == EntityDamageEvent.DamageCause.WORLD_BORDER
                || cause == EntityDamageEvent.DamageCause.STARVATION;
    }


    /*
     * =========================================================
     * DAMAGE EVENT
     * =========================================================
     */

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = false
    )
    public void onEntityDamage(
            EntityDamageByEntityEvent event
    ) {

        // Ore Troll: flat pickaxe rarity damage only — ignore combat DAMAGE / crit / undead.
        if (de.aetherion.items.mining.OreTrollListener.isOreTroll(event.getEntity())) {
            return;
        }

        /*
         * =====================================================
         * NUR SPIELERANGRIFFE
         * =====================================================
         */

        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            applyShortbowProjectile(event, projectile);
            return;
        }

        if (de.aetherion.items.combat.ScriptedHits.isActive()) {
            return;
        }

        if (!(event.getDamager() instanceof Player player)) {
            return;
        }


        /*
         * Pet lasers, explosions and other non-melee hits
         * must keep their own damage. Aetherion weapon stats
         * only apply to real player attacks.
         */

        EntityDamageEvent.DamageCause cause =
                event.getCause();

        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }

        /*
         * Shortbows deal damage through their arrows, not melee swings.
         */
        if (itemManager.isShortbow(player.getInventory().getItemInMainHand())
                || itemManager.isLongbow(player.getInventory().getItemInMainHand())
                || itemManager.isCustomCrossbow(player.getInventory().getItemInMainHand())
                || itemManager.isWand(player.getInventory().getItemInMainHand())) {
            return;
        }

        // WorldGuard often cancels wildlife hits before our damage rewrite.
        // Reclaim those swings so Attack Spread / weapon damage still apply.
        if (event.isCancelled()) {
            if (!(event.getEntity() instanceof LivingEntity living)
                    || !WildlifeCombatListener.allowsPlayerHit(player, living)) {
                return;
            }
            event.setCancelled(false);
        }


        /*
         * =====================================================
         * RECURSION SCHUTZ
         * =====================================================
         *
         * Wenn dieser Treffer selbst durch Attack Spread
         * entstanden ist, darf daraus kein weiterer
         * Attack Spread entstehen.
         *
         */

        if (spreadProcessing.contains(
                player.getUniqueId()
        )) {

            return;

        }


        /*
         * =====================================================
         * URSPRÜNGLICHES ZIEL
         * =====================================================
         */

        Entity originalTarget =
                event.getEntity();


        /*
         * =====================================================
         * AETHERION DAMAGE LESEN
         * =====================================================
         *
         * ActiveEquipmentStats prüft automatisch:
         *
         * - Mainhand
         * - Helmet
         * - Chestplate
         * - Leggings
         * - Boots
         *
         * und berücksichtigt nur Aetherion-Items mit
         * entsprechender DAMAGE Capability.
         *
         */

        double damage =
                equipmentStats.getStat(
                        player,
                        ItemCapability.DAMAGE
                );


        /*
         * =====================================================
         * KEIN AETHERION DAMAGE
         * =====================================================
         *
         * Kein aktiver Damage-Stat bedeutet:
         *
         * Minecraft Damage bleibt unverändert.
         *
         */

        if (damage <= 0) {
            return;
        }


        double critChance =
                equipmentStats.getStat(
                        player,
                        ItemCapability.CRIT_CHANCE
                );

        double critDamage =
                equipmentStats.getStat(
                        player,
                        ItemCapability.CRIT_DAMAGE
                );

        if (critChance > 0.0 && Math.random() * 100.0 < critChance) {
            damage *= 1.0 + Math.max(0.0, critDamage) / 100.0;
            de.aetherion.items.combat.DamageNumbers.markCrit(player);
        }


        /*
         * =====================================================
         * AETHERION DAMAGE SETZEN
         * =====================================================
         *
         * Der normale Minecraft-Schaden wird durch den
         * aktiven Aetherion Damage ersetzt.
         *
         */

        de.aetherion.items.AetherionItems plugin = de.aetherion.items.AetherionItems.getInstance();
        if (plugin != null && plugin.xpBoost() != null) {
            damage *= plugin.xpBoost().damageMultiplier(player);
        }

        event.setDamage(
                applyUndeadBonus(player, originalTarget, damage)
        );
        applyOnHitEffects(player, originalTarget);


        /*
         * =====================================================
         * ATTACK SPREAD LESEN
         * =====================================================
         *
         * Ebenfalls ausschließlich aus aktivem
         * Aetherion-Equipment.
         *
         */

        double attackSpread =
                equipmentStats.getStat(
                        player,
                        ItemCapability.ATTACK_SPREAD
                );


        /*
         * =====================================================
         * KEIN ATTACK SPREAD
         * =====================================================
         */

        if (attackSpread <= 0) {
            return;
        }


        /*
         * =====================================================
         * ANZAHL ZUSÄTZLICHER ZIELE
         * =====================================================
         *
         * Gleiche Logik wie beim Emerald Spread:
         *
         * 30
         * -> 30% Chance auf 1 Ziel
         *
         * 100
         * -> 1 Ziel garantiert
         *
         * 130
         * -> 1 garantiert
         * -> 30% Chance auf ein weiteres
         *
         * 250
         * -> 2 garantiert
         * -> 50% Chance auf ein weiteres
         *
         */

        int guaranteedTargets =
                (int) Math.floor(
                        attackSpread / 100.0
                );


        double remainingChance =
                attackSpread
                        - (
                        guaranteedTargets
                                * 100.0
                );


        // 100 AS = 1 extra target, 200 = 2, leftover % rolls one more.
        int additionalTargets =
                guaranteedTargets;


        /*
         * =====================================================
         * RESTCHANCE
         * =====================================================
         */

        if (
                remainingChance > 0
                        && Math.random() * 100.0
                        < remainingChance
        ) {

            additionalTargets++;

        }


        /*
         * =====================================================
         * NICHTS ZU TUN
         * =====================================================
         */

        if (additionalTargets <= 0) {
            return;
        }


        /*
         * =====================================================
         * ZUSÄTZLICHE ZIELE SUCHEN
         * =====================================================
         */

        Set<LivingEntity> targets =
                findAttackSpreadTargets(
                        originalTarget,
                        player,
                        additionalTargets
                );


        /*
         * =====================================================
         * KEINE ZIELE
         * =====================================================
         */

        if (targets.isEmpty()) {
            return;
        }


        /*
         * =====================================================
         * RECURSION SCHUTZ AKTIVIEREN
         * =====================================================
         */

        UUID playerId =
                player.getUniqueId();


        spreadProcessing.add(
                playerId
        );


        try {

            /*
             * =================================================
             * SPREAD-ZIELE TREFFEN
             * =================================================
             */

            for (LivingEntity target : targets) {


                /*
                 * =============================================
                 * ZIEL NOCHMALS PRÜFEN
                 * =============================================
                 */

                if (!isValidAttackSpreadTarget(
                        target,
                        originalTarget,
                        player
                )) {

                    continue;

                }


                /*
                 * =============================================
                 * EXAKT DENSELBEN AETHERION DAMAGE VERWENDEN
                 * =============================================
                 */

                target.damage(
                        applyUndeadBonus(player, target, damage),
                        player
                );
                applyOnHitEffects(player, target);

            }

        } finally {

            /*
             * =================================================
             * RECURSION SCHUTZ ENTFERNEN
             * =================================================
             */

            spreadProcessing.remove(
                    playerId
            );

        }

    }


    /*
     * =========================================================
     * ATTACK SPREAD TARGETS FINDEN
     * =========================================================
     *
     * Sucht lebende Entities in einem großzügigen Radius
     * um das ursprüngliche Ziel.
     *
     */

    private Set<LivingEntity> findAttackSpreadTargets(
            Entity originalTarget,
            Player player,
            int requiredTargets
    ) {

        Set<LivingEntity> targets =
                new java.util.LinkedHashSet<>();

        if (
                originalTarget == null
                        || !originalTarget.isValid()
                        || requiredTargets <= 0
        ) {

            return targets;

        }

        Location origin = originalTarget.getLocation();
        java.util.List<LivingEntity> candidates = new java.util.ArrayList<>();

        for (
                Entity entity :
                originalTarget.getNearbyEntities(
                        SPREAD_RADIUS,
                        SPREAD_RADIUS,
                        SPREAD_RADIUS
                )
        ) {

            if (!(entity instanceof LivingEntity livingEntity)) {
                continue;
            }

            if (
                    !isValidAttackSpreadTarget(
                            livingEntity,
                            originalTarget,
                            player
                    )
            ) {
                continue;
            }

            candidates.add(livingEntity);
        }

        candidates.sort(java.util.Comparator.comparingDouble(
                candidate -> candidate.getLocation().distanceSquared(origin)
        ));

        for (LivingEntity candidate : candidates) {
            targets.add(candidate);
            if (targets.size() >= requiredTargets) {
                break;
            }
        }

        return targets;

    }


    /*
     * =========================================================
     * ATTACK SPREAD TARGET VALIDIERUNG
     * =========================================================
     */

    private boolean isValidAttackSpreadTarget(
            LivingEntity target,
            Entity originalTarget,
            Player player
    ) {

        /*
         * =====================================================
         * NULL
         * =====================================================
         */

        if (target == null) {
            return false;
        }


        /*
         * =====================================================
         * URSPRÜNGLICHES ZIEL
         * =====================================================
         *
         * Das ursprüngliche Ziel darf niemals als
         * Spread-Ziel gezählt werden.
         *
         */

        if (
                target.equals(
                        originalTarget
                )
        ) {

            return false;

        }


        /*
         * =====================================================
         * SPIELER
         * =====================================================
         */

        if (target instanceof Player) {
            return false;
        }


        /*
         * =====================================================
         * ARMOR STAND
         * =====================================================
         */

        if (target instanceof ArmorStand) {
            return false;
        }


        /*
         * =====================================================
         * TOT
         * =====================================================
         */

        if (target.isDead()) {
            return false;
        }


        /*
         * =====================================================
         * NICHT MEHR GÜLTIG
         * =====================================================
         */

        if (!target.isValid()) {
            return false;
        }


        /*
         * =====================================================
         * INVULNERABLE
         * =====================================================
         *
         * Invulnerable Entities dürfen nicht
         * als Spread-Ziele verwendet werden.
         *
         */

        if (target.isInvulnerable()) {
            return false;
        }


        /*
         * =====================================================
         * NPC
         * =====================================================
         *
         * Viele NPC-Systeme, insbesondere Citizens,
         * verwenden die Metadata "NPC".
         *
         */

        if (target.hasMetadata("NPC")) {
            return false;
        }
        if (AetherEntities.isPet(target) || AetherEntities.isSetMinion(target)) {
            return false;
        }


        /*
         * =====================================================
         * EIGENER SPIELER
         * =====================================================
         */

        if (
                target.getUniqueId()
                        .equals(
                                player.getUniqueId()
                        )
        ) {

            return false;

        }


        /*
         * =====================================================
         * GÜLTIG
         * =====================================================
         */

        return true;

    }


    /*
     * =========================================================
     * SHORTBOW PROJECTILE
     * =========================================================
     *
     * Arrows spawned by ShortbowListener carry the rolled
     * Aetherion damage on their PDC.
     */

    private void applyShortbowProjectile(
            EntityDamageByEntityEvent event,
            org.bukkit.entity.Projectile projectile
    ) {

        Byte tagged = projectile.getPersistentDataContainer().get(
                ItemKeys.shortbow(),
                org.bukkit.persistence.PersistentDataType.BYTE
        );

        if (tagged == null || tagged != 1) {
            return;
        }

        if (!(projectile.getShooter() instanceof Player player)) {
            return;
        }

        Double stored = projectile.getPersistentDataContainer().get(
                ItemKeys.damage(),
                org.bukkit.persistence.PersistentDataType.DOUBLE
        );

        double rolled = stored == null ? 0.0 : stored;

        if (rolled <= 0) {
            return;
        }

        event.setDamage(applyUndeadBonus(player, event.getEntity(), rolled));

        if (spreadProcessing.contains(player.getUniqueId())) {
            return;
        }

        double attackSpread = equipmentStats.getStat(player, ItemCapability.ATTACK_SPREAD);

        if (attackSpread <= 0) {
            return;
        }

        int guaranteedTargets = (int) Math.floor(attackSpread / 100.0);
        double remainingChance = attackSpread - (guaranteedTargets * 100.0);
        int additionalTargets = guaranteedTargets;

        if (remainingChance > 0 && Math.random() * 100.0 < remainingChance) {
            additionalTargets++;
        }

        if (additionalTargets <= 0) {
            return;
        }

        Entity originalTarget = event.getEntity();
        Set<LivingEntity> targets = findAttackSpreadTargets(
                originalTarget,
                player,
                additionalTargets
        );

        if (targets.isEmpty()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        spreadProcessing.add(playerId);

        try {
            for (LivingEntity target : targets) {
                if (!isValidAttackSpreadTarget(target, originalTarget, player)) {
                    continue;
                }
                target.damage(applyUndeadBonus(player, target, rolled), player);
            }
        } finally {
            spreadProcessing.remove(playerId);
        }
    }

    private double applyUndeadBonus(Player player, Entity target, double damage) {
        if (!UndeadCombat.isUndead(target) || damage <= 0.0) {
            return damage;
        }
        double bonus = Math.min(80.0, Math.max(0.0,
                equipmentStats.getStat(player, ItemCapability.UNDEAD_DAMAGE)));
        if (bonus <= 0.0) {
            return damage;
        }
        return damage * (1.0 + bonus / 100.0);
    }

    private void applyOnHitEffects(Player player, Entity target) {
        if (!(target instanceof LivingEntity living) || living instanceof Player) {
            return;
        }
        int poisonTicks = itemManager.getPoisonTicks(player.getInventory().getItemInMainHand());
        if (poisonTicks <= 0) {
            return;
        }
        living.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.POISON,
                poisonTicks,
                0,
                false,
                true,
                true
        ));
    }

}