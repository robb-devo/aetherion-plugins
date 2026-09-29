package de.aetherion.items.item;

import org.bukkit.ChatColor;

import java.util.List;
import java.util.Locale;

public final class ItemFlavor {

    private ItemFlavor() {
    }

    public static void inject(List<String> lore, String itemId) {
        if (lore == null || lore.isEmpty()) {
            return;
        }
        String joke = jokeFor(itemId);
        if (joke == null || containsLine(lore, joke)) {
            return;
        }
        int footer = findFooter(lore, itemId);
        if (footer < 0) {
            footer = dungeonIndex(lore);
        }
        if (footer < 0) {
            footer = lore.size();
        }
        lore.add(footer, joke);
        if (footer > 0 && lore.get(footer - 1) != null && !lore.get(footer - 1).isBlank()) {
            lore.add(footer, "");
        }
    }

    public static String jokeFor(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "beginner_pickaxe" -> "§7It mines. That is the entire personality.";
            case "simple_pickaxe" -> "§7The rocks are not impressed. Yet.";
            case "simple_axe" -> "§7Trees have started taking it personally.";
            case "simple_hoe" -> "§7Farming, but make it an insult.";
            case "weapon_schematic" -> "§7Eleven identities. One paper. No refunds.";
            case "dungeon_weapon_schematic" -> "§7Five callings. One paper. No pickaxe.";
            case "rotten_cleaver" -> "§7The zombies want their leftovers back.";
            case "webweave_fang" -> "§7A spider's I-told-you-so, with a handle.";
            case "catcher_gaff" -> "§7Off-hand hoe. Pets that skip the meeting get hooked.";
            case "simple_sword" -> "§7A sword in the same way a spoon is a weapon.";
            case "simple_helmet" -> "§7Fashioned from leftover confidence.";
            case "simple_chestplate" -> "§7Keeps the wind out. Barely the arrows.";
            case "simple_leggings" -> "§7Pants with a job description.";
            case "simple_boots" -> "§7Walking, now with a modest opinion of itself.";
            case "simple_longbow", "longbow" -> "§7Aims with hope and a short attention span.";
            case "simple_shortbow", "shortbow" -> "§7Hold the button. Pretend it was skill.";
            case "hollow_longbow" -> "§7Details later. Trauma now.";
            case "catcher_helmet" -> "§7The eggs can smell the desperation.";
            case "catcher_chestplate" -> "§7A hug that files catch reports.";
            case "catcher_leggings" -> "§7Running toward pets. Professionally.";
            case "catcher_boots" -> "§7Quiet steps. Loud intentions.";
            case "god2_pickaxe", "god2_axe" -> "§7A test tool that refuses to take itself seriously.";
            case "god2_sword" -> "§7A test weapon. The dummy still lost.";
            case "god2_helmet", "god2_chestplate", "god2_leggings", "god2_boots" ->
                    "§7Lab armor. Please do not file a ticket.";
            case "charm_shiny" -> "§7A pocket rumor that pets look better in this lighting.";
            case "charm_forge" -> "§7It files your cobble so you don't have to.";
            case "charm_estate" -> "§7Peasant souvenirs. I'll take them off the carpet.";
            case "voided_455" -> "§8One catalyst short. Then the inventory learned gravity.";
            case "warped_blade" -> "§7It skipped the hallway. The hallway filed a complaint.";
            case "gravwell_cleaver" -> "§7The Pathwarden's toll booth. Still accepting souls.";
            case "ashen_katana" -> "§7Sheathed until the grove asks. Then it does not ask.";
            case "staff_of_technical_difficulties" -> "§7The patch notes were a suggestion.";
            case "void_vacuum_charm" -> "§7Drops report to the hopper. You do not.";
            case "thermal_core" -> "§7Faster. Warmer. Slightly your problem.";
            case "pickaxe_core_of_the_burrower" -> "§7The quarry still wants a receipt.";
            case "insolvent_ledger" -> "§7Assets of the recently less alive.";
            case "skuldugery_shortbow" -> "§7Hold the button. Pretend it was skill.";
            case "aetherblade" -> "§7A mythical blade. Origin still in committee.";
            case "bridged_axe" -> "§7The troll wanted a toll. This was the receipt.";
            case "aetherion_void_stick" -> "§7It drinks first. You can have the leftovers.";
            case "squids_boot" -> "§7One boot. Several opinions. All damp.";
            case "bone_knife" -> "§7Calcium with a grudge.";
            case "obsidian_maul" -> "§7The door is you. The door hits back.";
            case "mender_staff" -> "§7Keep them alive. They will not thank you.";
            default -> {
                String packed = de.aetherion.items.economy.CompressedResource.flavorFor(id);
                yield packed != null ? packed : setJoke(id);
            }
        };
    }

    private static String setJoke(String id) {
        if (id.startsWith("combat_")) {
            return combatJoke(id, trailingTier(id));
        }
        if (id.startsWith("mining_")) {
            return miningJoke(id, trailingTier(id));
        }
        if (id.startsWith("farming_")) {
            return farmingJoke(id, trailingTier(id));
        }
        if (id.startsWith("foraging_")) {
            return foragingJoke(id, trailingTier(id));
        }
        if (id.startsWith("fishing_")) {
            return fishingJoke(id, trailingTier(id));
        }
        if (id.startsWith("diving_")) {
            return "§7The deep end sent a dress code.";
        }
        if (id.startsWith("charm_")) {
            return charmJoke(id, trailingTier(id));
        }
        return null;
    }

    private static String fishingJoke(String id, int tier) {
        boolean rod = id.contains("rod");
        return switch (tier) {
            case 5 -> rod
                    ? "§7The last cast. The ocean signed the NDA."
                    : "§7Mythic brine. Wear it like a verdict.";
            case 4 -> rod
                    ? "§7Fourth cast. The tide started a support group."
                    : "§7Legendary leather that remembers every swell.";
            case 3 -> rod
                    ? "§7It argues with the tide. The tide loses."
                    : "§7Epic brine. Wear it like a verdict.";
            case 2 -> rod
                    ? "§7The second cast. The first one filed a complaint."
                    : "§7Slightly more of an argument with waves.";
            default -> rod
                    ? "§7The polite way to start a rumor with fish."
                    : "§7Leather that smelled a harbor and committed.";
        };
    }

    private static String foragingJoke(String id, int tier) {
        boolean axe = id.contains("axe");
        return switch (tier) {
            case 5 -> axe
                    ? "§7The forest filed for bankruptcy. You declined."
                    : "§7Mythic bark. Wear it like a canopy.";
            case 4 -> axe
                    ? "§7Fourth swing. The stump started a podcast."
                    : "§7Legendary leather that remembers every ring.";
            case 3 -> axe
                    ? "§7The tree files an appeal. Denied."
                    : "§7Bark that learned to be armor.";
            case 2 -> axe
                    ? "§7Second swing. The first one was a warning."
                    : "§7Slightly more of an argument with a trunk.";
            default -> axe
                    ? "§7The polite way to start a forest."
                    : "§7Leather that smelled pine and committed.";
        };
    }

    private static String charmJoke(String id, int tier) {
        return switch (tier) {
            case 3 -> "§7The off hand got a promotion.";
            case 2 -> "§7A pocket rumor, upgraded.";
            default -> "§7Hold it like a side argument.";
        };
    }

    private static String farmingJoke(String id, int tier) {
        boolean hoe = id.contains("hoe");
        return switch (tier) {
            case 5 -> hoe
                    ? "§7The last harvest argument. The field signed."
                    : "§7Mythic hay. Wear it like a verdict.";
            case 4 -> hoe
                    ? "§7Fourth draft of the same rude rake."
                    : "§7Legendary dirt under the nails.";
            case 3 -> hoe
                    ? "§7It harvests the argument and the crop."
                    : "§7Epic hay. Wear it like a verdict.";
            case 2 -> hoe
                    ? "§7The second rake. The first one filed a complaint."
                    : "§7Slightly more of an argument with grass.";
            default -> hoe
                    ? "§7The polite way to start a field."
                    : "§7Leather that smelled a farm and committed.";
        };
    }

    private static String combatJoke(String id, int tier) {
        boolean sword = id.contains("sword");
        return switch (tier) {
            case 5 -> sword
                    ? "§7The last argument in a very long meeting."
                    : "§7At this point the mobs send flowers.";
            case 4 -> sword
                    ? "§7Sharper. Still rude."
                    : "§7The fourth draft. The first three exploded.";
            case 3 -> sword
                    ? "§7Diamond, but it still gossiped in the forge."
                    : "§7Diamond, but emotionally still chainmail.";
            case 2 -> sword
                    ? "§7Slightly more of a point than last week."
                    : "§7Slightly more of a fight than last week.";
            default -> sword
                    ? "§7It counts as a weapon. Barely."
                    : "§7Still legally considered a fight.";
        };
    }

    private static String miningJoke(String id, int tier) {
        boolean pick = id.contains("pickaxe");
        return switch (tier) {
            case 5 -> pick
                    ? "§7The quarry asked for a lawyer. You declined."
                    : "§7Endgame dirt. Wear it like you mean it.";
            case 4 -> pick
                    ? "§7Stone has started writing reviews."
                    : "§7The rocks unionized. You still clock in.";
            case 3 -> pick
                    ? "§7Diamond manners. Cobble childhood."
                    : "§7Looks expensive. Mines like it has a quota.";
            case 2 -> pick
                    ? "§7The second pick. The first one filed a complaint."
                    : "§7Slightly more of an argument with stone.";
            default -> pick
                    ? "§7The polite way to start a hole."
                    : "§7The rocks started a union. You declined.";
        };
    }

    private static int trailingTier(String id) {
        if (id.endsWith("_5")) {
            return 5;
        }
        if (id.endsWith("_4")) {
            return 4;
        }
        if (id.endsWith("_3")) {
            return 3;
        }
        if (id.endsWith("_2")) {
            return 2;
        }
        return 1;
    }

    private static int findFooter(List<String> lore, String itemId) {
        String[] markers = markersFor(itemId);
        for (String marker : markers) {
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains(marker)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String[] markersFor(String itemId) {
        String id = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
        if (id.startsWith("combat_")) {
            return new String[] {"Aetherion Combat Set"};
        }
        if (id.startsWith("mining_")) {
            return new String[] {"Aetherion Mining Set"};
        }
        if (id.startsWith("farming_")) {
            return new String[] {"Aetherion Farming Set"};
        }
        if (id.startsWith("foraging_")) {
            return new String[] {"Aetherion Foraging Set"};
        }
        if (id.startsWith("fishing_")) {
            return new String[] {"Aetherion Fishing Set"};
        }
        if (id.startsWith("diving_")) {
            return new String[] {"Aetherion Diving Set"};
        }
        if (id.startsWith("charm_")) {
            return new String[] {"Aetherion Charm"};
        }
        if (id.startsWith("catcher_")) {
            return new String[] {"Aetherion Catcher Set"};
        }
        if (id.startsWith("god2_")) {
            return new String[] {"GOD Kit 2 Test"};
        }
        if (id.contains("longbow")) {
            return new String[] {"Starter hunting bow", "Hollow Lurker"};
        }
        if (id.contains("shortbow")) {
            return new String[] {"Starter shortbow", "Skuldugery", "personal bow"};
        }
        if (id.contains("warped")) {
            return new String[] {"Hollow Lurker", "What the"};
        }
        if (id.contains("aetherblade")) {
            return new String[] {"Forged from the last dragon"};
        }
        if (id.contains("bridged") || id.contains("void")) {
            return new String[] {"Torn from", "Bridge Troll", "Aetherion's leftover"};
        }
        if (id.startsWith("compressed_") || id.startsWith("compacted_")) {
            return new String[] {"Trade value"};
        }
        return new String[] {
                "Starter Tool",
                "Basic Aetherion Tool",
                "Basic Aetherion Weapon",
                "Basic Armor",
                "Torn from",
                "personal bow",
                "Hollow Lurker"
        };
    }

    private static int dungeonIndex(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String plain = ChatColor.stripColor(lore.get(i) == null ? "" : lore.get(i)).toLowerCase(Locale.ROOT);
            if (plain.startsWith("dungeon:")
                    || plain.startsWith("infused:")
                    || plain.contains("dungeon-bound")
                    || plain.startsWith("lv ")
                    || plain.startsWith("dungeon core")
                    || plain.startsWith("dungeonized")
                    || (plain.contains("anvil +") && plain.contains("core"))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsLine(List<String> lore, String joke) {
        String needle = ChatColor.stripColor(joke);
        for (String line : lore) {
            if (needle.equals(ChatColor.stripColor(line))) {
                return true;
            }
        }
        return false;
    }
}
