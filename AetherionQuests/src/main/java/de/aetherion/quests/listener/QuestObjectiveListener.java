package de.aetherion.quests.listener;


import de.aetherion.core.AetherKeys;
import de.aetherion.quests.AetherionQuests;
import de.aetherion.quests.manager.QuestManager;
import de.aetherion.quests.model.Objective;
import de.aetherion.quests.model.ObjectiveType;
import de.aetherion.quests.model.Quest;
import de.aetherion.quests.model.QuestState;
import de.aetherion.quests.util.ObjectiveMatcher;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;


public class QuestObjectiveListener implements Listener {


    private final QuestManager questManager;


    public QuestObjectiveListener(
            QuestManager questManager
    ) {

        this.questManager = questManager;
        bindPetCaught();
    }


    private void bindPetCaught() {
        try {
            Class<? extends org.bukkit.event.Event> type = Class
                    .forName("de.aetherion.aethermobs.event.PetCaughtEvent")
                    .asSubclass(org.bukkit.event.Event.class);
            org.bukkit.Bukkit.getPluginManager().registerEvent(
                    type,
                    this,
                    EventPriority.MONITOR,
                    (listener, event) -> {
                        try {
                            Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                            String petId = String.valueOf(event.getClass().getMethod("getPetId").invoke(event));
                            onPetCaught(player, petId);
                        } catch (ReflectiveOperationException ignored) {
                        }
                    },
                    AetherionQuests.getInstance(),
                    true
            );
        } catch (ClassNotFoundException ignored) {
        }
    }


    private void onPetCaught(Player player, String petId) {
        if (player == null) {
            return;
        }
        boolean pocketCatch = false;
        for (Quest quest : questManager.getQuests()) {
            if (!isActive(player, quest)) {
                continue;
            }
            for (Objective objective : quest.getObjectives()) {
                if (objective == null || objective.getType() != ObjectiveType.CATCH) {
                    continue;
                }
                String target = objective.getTarget();
                if (target == null
                        || target.isEmpty()
                        || target.equalsIgnoreCase("ANY")
                        || (petId != null && target.equalsIgnoreCase(petId))) {
                    int before = questManager.getProgress(player, quest.getId(), objective.getTarget());
                    addProgress(player, quest, objective);
                    if ("pocket_zoo".equalsIgnoreCase(quest.getId())
                            && before < objective.getAmount()
                            && questManager.getProgress(player, quest.getId(), objective.getTarget())
                            >= objective.getAmount()) {
                        pocketCatch = true;
                    }
                }
            }
        }
        if (pocketCatch) {
            // Part 1 done — quest bar pins Lark; give catch FX a beat before the nudge.
            de.aetherion.quests.ui.QuestHint.clear(player);
            de.aetherion.quests.ui.QuestProgressDisplay.showProgress(player, questManager);
            AetherionQuests plugin = AetherionQuests.getInstance();
            Runnable nudge = () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.sendMessage("§6Lark §8» §fNice throw. Come talk to me — equip lesson is next.");
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "→ Lark · equip lesson",
                        net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
                ));
                de.aetherion.quests.ui.QuestProgressDisplay.showProgress(player, questManager);
            };
            if (plugin != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, nudge, 35L);
            } else {
                nudge.run();
            }
        }
    }



    /*
     * =========================================================
     * BLOCK BREAK
     * =========================================================
     *
     * MINE
     * BREAK
     * HARVEST
     *
     * Alle drei werden zentral über BlockBreakEvent verarbeitet.
     */

    @EventHandler(
            // HIGH: runs before AetherionMining's MONITOR seal/cancel so MINE progress still counts.
            priority = EventPriority.HIGH,
            ignoreCancelled = true
    )
    public void onBlockBreak(
            BlockBreakEvent event
    ) {


        Player player =
                event.getPlayer();

        Block block =
                event.getBlock();

        Material material =
                block.getType();


        /*
         * =====================================================
         * ALLE AKTIVEN QUESTS
         * =====================================================
         */

        for (Quest quest :
                questManager.getQuests()) {


            if (!isActive(
                    player,
                    quest
            )) {

                continue;

            }


            for (Objective objective :
                    quest.getObjectives()) {


                if (objective == null) {

                    continue;

                }


                ObjectiveType type =
                        objective.getType();


                if (type == null) {

                    continue;

                }


                /*
                 * =================================================
                 * MINE
                 * =================================================
                 *
                 * Beispiel:
                 *
                 * MINE
                 * COAL_ORE
                 * 45
                 */

                if (type == ObjectiveType.MINE) {

                    if (ObjectiveMatcher.matchesBlock(
                            material,
                            objective.getTarget()
                    )) {

                        addProgress(
                                player,
                                quest,
                                objective
                        );

                    }

                    continue;

                }


                /*
                 * =================================================
                 * BREAK
                 * =================================================
                 *
                 * Beispiel:
                 *
                 * BREAK
                 * OAK_LOG
                 * 20
                 */

                if (type == ObjectiveType.BREAK) {

                    if (ObjectiveMatcher.matchesBlock(
                            material,
                            objective.getTarget()
                    )) {

                        addProgress(
                                player,
                                quest,
                                objective
                        );

                    }

                    continue;

                }


                /*
                 * =================================================
                 * HARVEST
                 * =================================================
                 *
                 * Wird zunächst ebenfalls über BlockBreakEvent
                 * erkannt.
                 *
                 * Beispiel:
                 *
                 * HARVEST
                 * WHEAT
                 * 15
                 */

                if (type == ObjectiveType.HARVEST) {

                    if (ObjectiveMatcher.matchesBlock(
                            material,
                            objective.getTarget()
                    )) {

                        addProgress(
                                player,
                                quest,
                                objective
                        );

                    }

                }

            }

        }

        syncDeliverLater(player);

    }



    /*
     * =========================================================
     * ENTITY KILL
     * =========================================================
     *
     * ObjectiveType.KILL
     *
     * Beispiel:
     *
     * KILL
     * ZOMBIE
     * 10
     *
     * Zusätzlich:
     *
     * KILL
     * ANY
     * 10
     *
     * zählt jeden getöteten Mob.
     */

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onEntityDeath(
            EntityDeathEvent event
    ) {


        Player killer =
                findKiller(
                        event
                );


        Entity entity =
                event.getEntity();

        String bossId = bossId(entity);

        java.util.Set<Player> candidates = new java.util.HashSet<>();
        if (killer != null) {
            candidates.add(killer);
        }

        if (bossId != null && entity.getWorld() != null) {
            for (Player nearby : entity.getWorld().getPlayers()) {
                if (nearby.getLocation().distanceSquared(entity.getLocation()) <= 48 * 48) {
                    candidates.add(nearby);
                }
            }
        }

        if (candidates.isEmpty()) {
            return;
        }


        EntityType entityType =
                entity.getType();


        for (Player player : candidates) {

        for (Quest quest :
                questManager.getQuests()) {


            if (!isActive(
                    player,
                    quest
            )) {

                continue;

            }


            for (Objective objective :
                    quest.getObjectives()) {


                if (objective == null) {

                    continue;

                }


                if (objective.getType()
                        != ObjectiveType.KILL) {

                    continue;

                }


                String target =
                        objective.getTarget();


                if (target == null
                        || target.isEmpty()) {

                    continue;

                }


                /*
                 * =================================================
                 * ANY MOB
                 * =================================================
                 */

                if (target.equalsIgnoreCase("ANY")) {

                    addProgress(
                            player,
                            quest,
                            objective
                    );

                    continue;

                }


                if (!matchesKillTarget(entity, entityType, target)) {
                    continue;
                }


                addProgress(
                        player,
                        quest,
                        objective
                );

            }

        }

        }

    }



    /*
     * =========================================================
     * CRAFT
     * =========================================================
     *
     * ObjectiveType.CRAFT
     *
     * Beispiel:
     *
     * CRAFT
     * IRON_SWORD
     * 1
     */

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onCraft(
            CraftItemEvent event
    ) {


        if (!(event.getWhoClicked()
                instanceof Player player)) {

            return;

        }


        ItemStack result =
                event.getCurrentItem();


        if (result == null) {

            return;

        }


        Material material =
                result.getType();


        int amount =
                result.getAmount();


        for (Quest quest :
                questManager.getQuests()) {


            if (!isActive(
                    player,
                    quest
            )) {

                continue;

            }


            for (Objective objective :
                    quest.getObjectives()) {


                if (objective == null) {

                    continue;

                }


                if (objective.getType()
                        != ObjectiveType.CRAFT) {

                    continue;

                }


                if (!matchesMaterial(
                        material,
                        objective.getTarget()
                )) {

                    continue;

                }


                addProgress(
                        player,
                        quest,
                        objective,
                        amount
                );

            }

        }

    }



    /*
     * =========================================================
     * COLLECT
     * =========================================================
     *
     * ObjectiveType.COLLECT
     *
     * Wird ausgelöst, wenn ein Spieler ein Item
     * tatsächlich aufsammelt.
     *
     * Beispiel:
     *
     * COLLECT
     * DIAMOND
     * 10
     */

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onItemPickup(
            EntityPickupItemEvent event
    ) {


        if (!(event.getEntity()
                instanceof Player player)) {

            return;

        }


        ItemStack item =
                event.getItem()
                        .getItemStack();


        if (item == null) {

            return;

        }


        Material material =
                item.getType();


        int amount =
                item.getAmount();


        for (Quest quest :
                questManager.getQuests()) {


            if (!isActive(
                    player,
                    quest
            )) {

                continue;

            }


            for (Objective objective :
                    quest.getObjectives()) {


                if (objective == null) {

                    continue;

                }


                if (objective.getType()
                        != ObjectiveType.COLLECT) {

                    continue;

                }


                if (!matchesMaterial(
                        material,
                        objective.getTarget()
                )) {

                    continue;

                }


                addProgress(
                        player,
                        quest,
                        objective,
                        amount
                );

            }

        }

        syncDeliverLater(player);

    }



    /*
     * =========================================================
     * FISH
     * =========================================================
     *
     * ObjectiveType.FISH
     *
     * Beispiel:
     *
     * FISH
     * COD
     * 5
     *
     * Oder:
     *
     * FISH
     * ANY
     * 5
     *
     * ANY zählt jeden erfolgreichen Fang.
     */

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onFish(
            PlayerFishEvent event
    ) {


        if (event.getState()
                != PlayerFishEvent.State.CAUGHT_FISH) {

            return;

        }


        Player player =
                event.getPlayer();


        ItemStack caughtItem =
                null;


        if (event.getCaught()
                instanceof org.bukkit.entity.Item itemEntity) {

            caughtItem =
                    itemEntity.getItemStack();

        }


        for (Quest quest :
                questManager.getQuests()) {


            if (!isActive(
                    player,
                    quest
            )) {

                continue;

            }


            for (Objective objective :
                    quest.getObjectives()) {


                if (objective == null) {

                    continue;

                }


                if (objective.getType()
                        != ObjectiveType.FISH) {

                    continue;

                }


                String target =
                        objective.getTarget();


                /*
                 * =================================================
                 * ANY FANG
                 * =================================================
                 */

                if (target == null
                        || target.isEmpty()
                        || target.equalsIgnoreCase("ANY")) {

                    addProgress(
                            player,
                            quest,
                            objective
                    );

                    continue;

                }


                /*
                 * =================================================
                 * KONKRETES ITEM
                 * =================================================
                 */

                if (caughtItem == null) {

                    continue;

                }


                if (!matchesMaterial(
                        caughtItem.getType(),
                        target
                )) {

                    continue;

                }


                addProgress(
                        player,
                        quest,
                        objective
                );

            }

        }

    }



    /*
     * =========================================================
     * USE
     * =========================================================
     *
     * ObjectiveType.USE
     *
     * Reagiert auf:
     *
     * RIGHT_CLICK_BLOCK
     * RIGHT_CLICK_AIR
     * LEFT_CLICK_BLOCK
     * LEFT_CLICK_AIR
     *
     * Beispiel:
     *
     * USE
     * CHEST
     * 1
     *
     * oder:
     *
     * USE
     * DIAMOND_PICKAXE
     * 1
     */

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onUse(
            PlayerInteractEvent event
    ) {


        Action action =
                event.getAction();


        if (action != Action.RIGHT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR) {

            return;

        }


        Player player =
                event.getPlayer();


        Material targetMaterial =
                null;


        /*
         * =====================================================
         * BLOCK BENUTZT
         * =====================================================
         */

        if (event.getClickedBlock() != null) {

            targetMaterial =
                    event.getClickedBlock()
                            .getType();

        }


        /*
         * =====================================================
         * ITEM BENUTZT
         * =====================================================
         */

        if (targetMaterial == null
                && event.getItem() != null) {

            targetMaterial =
                    event.getItem()
                            .getType();

        }


        if (targetMaterial == null) {

            return;

        }


        for (Quest quest :
                questManager.getQuests()) {


            if (!isActive(
                    player,
                    quest
            )) {

                continue;

            }


            for (Objective objective :
                    quest.getObjectives()) {


                if (objective == null) {

                    continue;

                }


                if (objective.getType()
                        != ObjectiveType.USE) {

                    continue;

                }


                if (!matchesMaterial(
                        targetMaterial,
                        objective.getTarget()
                )) {

                    continue;

                }


                addProgress(
                        player,
                        quest,
                        objective
                );

            }

        }

    }



    /*
     * =========================================================
     * QUEST ACTIVE?
     * =========================================================
     */

    private boolean isActive(
            Player player,
            Quest quest
    ) {

        if (quest == null) {
            return false;
        }

        Quest tracked = questManager.getTrackedQuest(player);

        if (tracked != null) {
            return tracked.getId().equalsIgnoreCase(quest.getId());
        }

        return questManager.getQuestState(player, quest) == QuestState.ACTIVE;

    }


    private boolean matchesKillTarget(Entity entity, EntityType entityType, String target) {
        if (target == null || target.isBlank()) {
            return false;
        }

        if (target.equalsIgnoreCase("BORDERLANDS")
                || target.equalsIgnoreCase("BORDERLANDS_MOB")
                || target.equalsIgnoreCase("BORDERLANDS_HOSTILE")) {
            return isBorderlandsHostileKill(entity);
        }

        if (target.equalsIgnoreCase("HOSTILE") || target.equalsIgnoreCase("MONSTER")) {
            return entity instanceof org.bukkit.entity.Monster;
        }

        if (entityType != null && entityType.name().equalsIgnoreCase(target)) {
            return true;
        }

        String bossId = bossId(entity);
        if (bossId == null) {
            return false;
        }

        return bossId.equalsIgnoreCase(target)
                || ("boss_" + bossId).equalsIgnoreCase(target)
                || ("boss:" + bossId).equalsIgnoreCase(target);
    }

    private boolean isBorderlandsHostileKill(Entity entity) {
        if (!(entity instanceof org.bukkit.entity.Monster)) {
            return false;
        }
        org.bukkit.Location at = entity.getLocation();
        try {
            org.bukkit.plugin.Plugin items = org.bukkit.Bukkit.getPluginManager().getPlugin("AetherionItems");
            if (items instanceof de.aetherion.items.AetherionItems aetherionItems) {
                de.aetherion.items.world.MobZoneService mobs = aetherionItems.getMobZones();
                if (mobs != null && mobs.containsBorderlands(at)) {
                    return true;
                }
                de.aetherion.items.world.AreaService areas = aetherionItems.getAreas();
                if (areas != null && areas.isType(at, de.aetherion.items.world.AreaType.BORDERLANDS)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }


    private String bossId(Entity entity) {
        if (entity == null) {
            return null;
        }

        return entity.getPersistentDataContainer().get(AetherKeys.BOSS_ID, PersistentDataType.STRING);
    }



    /*
     * =========================================================
     * MATERIAL MATCH
     * =========================================================
     *
     * Vergleicht ein Objective-Target mit
     * einem Bukkit-Material.
     */

    private boolean matchesMaterial(
            Material material,
            String target
    ) {


        if (material == null
                || target == null
                || target.isEmpty()) {

            return false;

        }


        return ObjectiveMatcher.matchesBlock(material, target)
                || ObjectiveMatcher.matchesItem(material, target);

    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        syncDeliverLater(event.getPlayer());
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            syncDeliverLater(player);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            syncDeliverLater(player);
        }
    }


    private Player findKiller(EntityDeathEvent event) {

        Player killer = event.getEntity().getKiller();

        if (killer != null) {
            return killer;
        }

        org.bukkit.event.entity.EntityDamageEvent cause = event.getEntity().getLastDamageCause();
        if (cause != null) {
            Entity causing = cause.getDamageSource().getCausingEntity();
            if (causing instanceof Player player) {
                return player;
            }
            Player owner = helperOwner(causing);
            if (owner != null) {
                return owner;
            }
        }

        if (event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent byEntity) {

            if (byEntity.getDamager() instanceof Player player) {
                return player;
            }

            if (byEntity.getDamager() instanceof Projectile projectile
                    && projectile.getShooter() instanceof Player player) {
                return player;
            }

            Player owner = helperOwner(byEntity.getDamager());
            if (owner != null) {
                return owner;
            }

        }

        return null;

    }

    private Player helperOwner(Entity entity) {
        if (entity == null) {
            return null;
        }
        String owner = entity.getPersistentDataContainer().get(
                AetherKeys.SET_MINION_OWNER,
                PersistentDataType.STRING
        );
        if (owner == null || owner.isBlank()) {
            return null;
        }
        try {
            return org.bukkit.Bukkit.getPlayer(java.util.UUID.fromString(owner));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }


    private void syncDeliverLater(Player player) {

        AetherionQuests plugin = AetherionQuests.getInstance();

        if (plugin == null || player == null) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                questManager.syncDeliverProgress(player);
            }
        });

    }



    /*
     * =========================================================
     * PROGRESS +1
     * =========================================================
     */

    private void addProgress(
            Player player,
            Quest quest,
            Objective objective
    ) {

        addProgress(
                player,
                quest,
                objective,
                1
        );

    }



    /*
     * =========================================================
     * PROGRESS + AMOUNT
     * =========================================================
     */

    private void addProgress(
            Player player,
            Quest quest,
            Objective objective,
            int amount
    ) {


        if (amount <= 0) {

            return;

        }


        questManager.addProgress(
                player,
                quest.getId(),
                objective.getTarget(),
                amount
        );

    }

}