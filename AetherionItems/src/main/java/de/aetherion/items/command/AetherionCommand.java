package de.aetherion.items.command;

import de.aetherion.items.AetherionItems;
import de.aetherion.items.item.CustomItem;
import de.aetherion.items.menu.dev.DevMenu;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AetherionCommand implements CommandExecutor {

    private final CustomItem customItem;

    public AetherionCommand(CustomItem customItem) {
        this.customItem = customItem;
    }


    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!(sender instanceof Player player)) {
            return true;
        }
        if (!player.hasPermission("aetherion.dev")) {
            player.sendMessage("§cDEV only.");
            return true;
        }


        /*
         * =====================================================
         * NORMALIZE COMMAND
         * =====================================================
         */

        String input;

        if (
                label.equalsIgnoreCase("aetherionitems")
        ) {

            if (args.length == 0) {

                player.getInventory().addItem(
                        customItem.create()
                );

                return true;
            }

            input = args[0].toLowerCase();

        } else {

            input = label.toLowerCase();
        }


        if (input.equals("dev") || input.equals("devmenu") || input.equals("adev")) {
            DevMenu menu = AetherionItems.getInstance().getDevMenu();
            if (menu == null || !DevMenu.canUse(player)) {
                player.sendMessage("§cDEV only.");
                return true;
            }
            menu.open(player);
            return true;
        }


        /*
         * =====================================================
         * KIT 1
         * =====================================================
         */

        if (
                input.equals("kit")
                        ||
                        input.equals("kit1")
        ) {

            player.getInventory().addItem(
                    customItem.createGodPickaxe()
            );

            player.getInventory().addItem(
                    customItem.createGodAxe()
            );

            player.getInventory().addItem(
                    customItem.createGodSword()
            );

            player.getInventory().addItem(
                    customItem.createGodHelmet()
            );

            player.getInventory().addItem(
                    customItem.createGodChestplate()
            );

            player.getInventory().addItem(
                    customItem.createGodLeggings()
            );

            player.getInventory().addItem(
                    customItem.createGodBoots()
            );

            return true;
        }


        /*
         * =====================================================
         * KIT 2
         * =====================================================
         */

        if (
                input.equals("kit2")
        ) {

            player.getInventory().addItem(
                    customItem.createGod2Pickaxe()
            );

            player.getInventory().addItem(
                    customItem.createGod2Axe()
            );

            player.getInventory().addItem(
                    customItem.createGod2Sword()
            );

            player.getInventory().addItem(
                    customItem.createGod2Helmet()
            );

            player.getInventory().addItem(
                    customItem.createGod2Chestplate()
            );

            player.getInventory().addItem(
                    customItem.createGod2Leggings()
            );

            player.getInventory().addItem(
                    customItem.createGod2Boots()
            );

            return true;
        }


        /*
         * =====================================================
         * COMBAT SET
         * =====================================================
         *
         * /aetherionitems combat
         *
         * Gives the complete Combat I armor set.
         *
         */

        if (
                input.equals("combat")
        ) {

            player.getInventory().addItem(
                    customItem.createCombatHelmet()
            );

            player.getInventory().addItem(
                    customItem.createCombatChestplate()
            );

            player.getInventory().addItem(
                    customItem.createCombatLeggings()
            );

            player.getInventory().addItem(
                    customItem.createCombatBoots()
            );

            return true;
        }


        /*
         * =====================================================
         * COMBAT SET II
         * =====================================================
         *
         * /aetherionitems combat2
         *
         * Gives the complete Combat II armor set.
         *
         */

        if (
                input.equals("combat2")
        ) {

            player.getInventory().addItem(
                    customItem.createCombatHelmet2()
            );

            player.getInventory().addItem(
                    customItem.createCombatChestplate2()
            );

            player.getInventory().addItem(
                    customItem.createCombatLeggings2()
            );

            player.getInventory().addItem(
                    customItem.createCombatBoots2()
            );

            return true;
        }


        if (input.equals("catcher")) {
            player.getInventory().addItem(customItem.createCatcherHelmet());
            player.getInventory().addItem(customItem.createCatcherChestplate());
            player.getInventory().addItem(customItem.createCatcherLeggings());
            player.getInventory().addItem(customItem.createCatcherBoots());
            return true;
        }


        /*
         * =====================================================
         * COMBAT WEAPON
         * =====================================================
         *
         * /aetherionitems combatsword
         *
         */

        if (
                input.equals("combatsword")
        ) {

            player.getInventory().addItem(
                    customItem.createCombatSword()
            );

            return true;
        }


        /*
         * =====================================================
         * COMBAT WEAPON II
         * =====================================================
         *
         * /aetherionitems combatsword2
         *
         */

        if (
                input.equals("combatsword2")
        ) {

            player.getInventory().addItem(
                    customItem.createCombatSword2()
            );

            return true;
        }


        /*
         * =====================================================
         * SHORTBOW
         * =====================================================
         *
         * /aetherionitems shortbow
         *
         */

        if (
                input.equals("shortbow")
                        || input.equals("skuldugery")
                        || input.equals("skuldugeryshortbow")
        ) {

            player.getInventory().addItem(
                    customItem.createSkuldugeryShortbow()
            );

            return true;
        }


        if (
                input.equals("aetherblade")
                        || input.equals("blade")
        ) {

            player.getInventory().addItem(
                    customItem.createAetherblade()
            );

            return true;
        }


        if (
                input.equals("bridgedaxe")
                        || input.equals("bridged_axe")
                        || input.equals("bridgeaxe")
        ) {

            player.getInventory().addItem(
                    customItem.createBridgedAxe()
            );

            return true;
        }


        if (
                input.equals("warpedblade")
                        || input.equals("warped_blade")
                        || input.equals("warped")
        ) {

            player.getInventory().addItem(
                    customItem.createWarpedBlade()
            );

            return true;
        }


        if (
                input.equals("dungeoncore")
                        || input.equals("dungeon_core")
                        || input.equals("core")
        ) {
            player.getInventory().addItem(customItem.createDungeonCore());
            return true;
        }

        if (input.equals("dungeon_core_2") || input.equals("dungeoncore2") || input.equals("core2")) {
            player.getInventory().addItem(customItem.createDungeonCore2());
            return true;
        }

        if (input.equals("dungeon_core_3") || input.equals("dungeoncore3") || input.equals("core3")) {
            player.getInventory().addItem(customItem.createDungeonCore3());
            return true;
        }

        if (
                input.equals("dungeonvestige")
                        || input.equals("dungeon_vestige")
                        || input.equals("dungeonarmor")
        ) {
            player.getInventory().addItem(customItem.createDungeonVestigeHelmet());
            player.getInventory().addItem(customItem.createDungeonVestigeChestplate());
            player.getInventory().addItem(customItem.createDungeonVestigeLeggings());
            player.getInventory().addItem(customItem.createDungeonVestigeBoots());
            return true;
        }


        if (
                input.equals("squidsboot")
                        || input.equals("squids_boot")
                        || input.equals("squidboot")
                        || input.equals("squidward")
        ) {

            player.getInventory().addItem(
                    customItem.createSquidsBoot()
            );

            return true;
        }


        if (
                input.equals("aetherionset")
                        || input.equals("aetherionarmor")
                        || input.equals("aetherionkit")
        ) {

            player.getInventory().addItem(customItem.createAetherionHelmet());
            player.getInventory().addItem(customItem.createAetherionChestplate());
            player.getInventory().addItem(customItem.createAetherionLeggings());
            player.getInventory().addItem(customItem.createAetherionBoots());
            return true;
        }


        if (
                input.equals("voidstick")
                        || input.equals("aetherionstick")
                        || input.equals("aetherion_void_stick")
        ) {

            player.getInventory().addItem(customItem.createAetherionVoidStick());
            return true;
        }


        if (input.equals("aetherionhelmet")) {
            player.getInventory().addItem(customItem.createAetherionHelmet());
            return true;
        }
        if (input.equals("aetherionchestplate") || input.equals("aetherionchest")) {
            player.getInventory().addItem(customItem.createAetherionChestplate());
            return true;
        }
        if (input.equals("aetherionleggings") || input.equals("aetherionlegs")) {
            player.getInventory().addItem(customItem.createAetherionLeggings());
            return true;
        }
        if (input.equals("aetherionboots")) {
            player.getInventory().addItem(customItem.createAetherionBoots());
            return true;
        }


        /*
         * =====================================================
         * MINING SET
         * =====================================================
         *
         * /aetherionitems mining
         *
         * Gives the complete Mining I armor set.
         *
         */

        if (
                input.equals("mining")
        ) {

            player.getInventory().addItem(
                    customItem.createMiningHelmet()
            );

            player.getInventory().addItem(
                    customItem.createMiningChestplate()
            );

            player.getInventory().addItem(
                    customItem.createMiningLeggings()
            );

            player.getInventory().addItem(
                    customItem.createMiningBoots()
            );

            return true;
        }


        /*
         * =====================================================
         * MINING SET II
         * =====================================================
         *
         * /aetherionitems mining2
         *
         * Gives the complete Mining II armor set.
         *
         */

        if (
                input.equals("mining2")
        ) {

            player.getInventory().addItem(
                    customItem.createMiningHelmet2()
            );

            player.getInventory().addItem(
                    customItem.createMiningChestplate2()
            );

            player.getInventory().addItem(
                    customItem.createMiningLeggings2()
            );

            player.getInventory().addItem(
                    customItem.createMiningBoots2()
            );

            return true;
        }


        /*
         * =====================================================
         * MINING PICKAXE
         * =====================================================
         *
         * /aetherionitems miningpickaxe
         *
         */

        if (
                input.equals("miningpickaxe")
        ) {

            player.getInventory().addItem(
                    customItem.createMiningPickaxe()
            );

            return true;
        }


        /*
         * =====================================================
         * MINING PICKAXE II
         * =====================================================
         *
         * /aetherionitems miningpickaxe2
         *
         */

        if (
                input.equals("miningpickaxe2")
        ) {

            player.getInventory().addItem(
                    customItem.createMiningPickaxe2()
            );

            return true;
        }


        /*
         * =====================================================
         * TOOLS
         * =====================================================
         */

        switch (input) {

            case "pickaxe" -> {

                player.getInventory().addItem(
                        customItem.createSimplePickaxe()
                );

                return true;
            }

            case "axe" -> {

                player.getInventory().addItem(
                        customItem.createSimpleAxe()
                );

                return true;
            }

            case "sword" -> {

                player.getInventory().addItem(
                        customItem.createSimpleSword()
                );

                return true;
            }

            case "hoe" -> {

                player.getInventory().addItem(
                        customItem.createSimpleHoe()
                );

                return true;
            }


            /*
             * =================================================
             * SIMPLE ARMOR
             * =================================================
             */

            case "helmet" -> {

                player.getInventory().addItem(
                        customItem.createSimpleHelmet()
                );

                return true;
            }

            case "chestplate" -> {

                player.getInventory().addItem(
                        customItem.createSimpleChestplate()
                );

                return true;
            }

            case "leggings" -> {

                player.getInventory().addItem(
                        customItem.createSimpleLeggings()
                );

                return true;
            }

            case "boots" -> {

                player.getInventory().addItem(
                        customItem.createSimpleBoots()
                );

                return true;
            }


            /*
             * =================================================
             * RECIPE BOOK
             * =================================================
             */

            case "recipebook" -> {

                player.getInventory().addItem(
                        customItem.createRecipeBook()
                );

                return true;
            }


            /*
             * =================================================
             * BOOSTER
             * =================================================
             */

            case "booster" -> {

                if (args.length < 2) {

                    player.sendMessage(
                            "§cUsage: /aetherionitems booster <coal|iron|gold|diamond|emerald|redstone|lapis|glowstone|wheat|oak|birch>"
                    );

                    return true;
                }

                switch (args[1].toLowerCase()) {

                    case "coal" ->
                            player.getInventory().addItem(
                                    customItem.createCoalBooster()
                            );

                    case "iron" ->
                            player.getInventory().addItem(
                                    customItem.createIronBooster()
                            );

                    case "gold" ->
                            player.getInventory().addItem(
                                    customItem.createGoldBooster()
                            );

                    case "diamond" ->
                            player.getInventory().addItem(
                                    customItem.createDiamondBooster()
                            );

                    case "emerald" ->
                            player.getInventory().addItem(
                                    customItem.createEmeraldBooster()
                            );

                    case "redstone" ->
                            player.getInventory().addItem(
                                    customItem.createRedstoneBooster()
                            );

                    case "lapis" ->
                            player.getInventory().addItem(
                                    customItem.createLapisBooster()
                            );

                    case "glowstone" ->
                            player.getInventory().addItem(
                                    customItem.createGlowstoneBooster()
                            );

                    case "wheat" ->
                            player.getInventory().addItem(
                                    customItem.createWheatBooster()
                            );

                    case "carrot", "harvest" ->
                            player.getInventory().addItem(
                                    customItem.createCarrotBooster()
                            );

                    case "oak", "critdamage", "critdmg" ->
                            player.getInventory().addItem(
                                    customItem.createOakBooster()
                            );

                    case "birch", "critchance", "crit" ->
                            player.getInventory().addItem(
                                    customItem.createBirchBooster()
                            );

                    default ->
                            player.sendMessage(
                                    "§cUnknown Booster. Use coal, iron, gold, diamond, emerald, redstone, lapis, glowstone, wheat, carrot, oak or birch."
                            );
                }

                return true;
            }


            /*
             * =================================================
             * UNKNOWN
             * =================================================
             */

            default -> {

                player.sendMessage(
                        "§cUnknown Aetherion Item."
                );

                return true;
            }
        }
    }
}