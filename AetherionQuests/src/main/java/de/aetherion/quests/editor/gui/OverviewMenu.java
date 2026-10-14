package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.NpcCheck;
import de.aetherion.quests.editor.NpcEditor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * NPC workspace — Overview tab: what players see, whether it's ready, where it stands, and the NPC-level actions.
 */
public final class OverviewMenu {

    private static final int PREVIEW = 20;
    private static final int HEALTH = 22;
    private static final int WHERE = 24;
    private static final int MOVE_HERE = 37;
    private static final int MOVE_BLOCK = 38;
    private static final int FACE_ME = 39;
    private static final int DUPLICATE = 41;
    private static final int DELETE = 43;

    private OverviewMenu() {
    }

    public static void open(Player player, String npcId) {
        CustomNpc npc = Frame.require(player, npcId);
        if (npc == null) {
            return;
        }
        NpcEditor editor = Frame.editor();
        Runnable reopen = () -> open(player, npcId);
        Menu menu = Frame.workspace(player, npc, Frame.Tab.OVERVIEW, reopen);

        menu.set(PREVIEW, preview(player, npc), click -> {
            CustomNpc live = editor.npc(npcId);
            if (click.shift()) {
                click.player().closeInventory();
                editor.runtime().talk(click.player(), live);
            } else {
                editor.preview(click.player(), live, null, null, reopen);
            }
        });

        List<NpcCheck.Issue> issues = NpcCheck.run(npc, editor.quests());
        menu.set(HEALTH, health(issues), issues.isEmpty() ? null : click -> jumpTo(click.player(), npcId, issues.get(0)));

        menu.set(WHERE, where(player, npc), npc.location() == null ? null : click -> {
            click.player().closeInventory();
            editor.teleportTo(click.player(), editor.npc(npcId));
        });

        menu.set(MOVE_HERE, EditorItems.icon(Material.ENDER_PEARL)
                .name("§bMove here")
                .text("Puts the NPC exactly where you're standing, facing where you face.")
                .blank().click("Click", "to move it")
                .build(), click -> {
            editor.moveHere(click.player(), editor.npc(npcId));
            open(click.player(), npcId);
        });
        menu.set(MOVE_BLOCK, EditorItems.icon(Material.TARGET)
                .name("§bMove to where I'm looking")
                .text("Stands the NPC on the block in your crosshair (up to 24 blocks), facing you.")
                .blank().click("Click", "to move it")
                .build(), click -> {
            editor.moveToTarget(click.player(), editor.npc(npcId));
            open(click.player(), npcId);
        });
        menu.set(FACE_ME, EditorItems.icon(Material.ARMOR_STAND)
                .name("§bFace me")
                .text("Turns the NPC toward you. (Up close it always turns to look at players.)")
                .blank().click("Click", "to turn it")
                .build(), click -> {
            editor.lookAt(click.player(), editor.npc(npcId));
            open(click.player(), npcId);
        });
        menu.set(DUPLICATE, EditorItems.icon(Material.PAPER).amount(2)
                .name("§eDuplicate")
                .text("Makes a copy — same look, dialogue and quest — standing where you are.")
                .blank().click("Click", "to copy it")
                .build(), click -> {
            CustomNpc copy = editor.duplicate(click.player(), editor.npc(npcId));
            if (copy != null) {
                open(click.player(), copy.getId());
            }
        });
        menu.set(DELETE, EditorItems.icon(Material.LAVA_BUCKET)
                .name("§cDelete NPC")
                .text("Removes it from the world and from the studio. You'll be asked first.")
                .blank().click("Click", "to delete…")
                .build(), click -> ConfirmMenu.deleteNpc(click.player(), npcId, reopen));

        Frame.help(menu, reopen,
                "§7The summary of this NPC.",
                "",
                "§f▶ Preview §7plays the chat to you",
                "§7safely — commands and quests are only",
                "§7described. §eShift-click §7to talk for real.",
                "",
                "§7The tabs on top edit §fLook§7, §fDialogue",
                "§7and §fQuest§7. Everything saves instantly.");
        Frame.show(menu, player);
    }

    private static ItemStack preview(Player player, CustomNpc npc) {
        List<String> lore = new ArrayList<>();
        lore.add("§7What a player sees first:");
        lore.add("");
        CustomNpc.DialoguePage first = npc.page(npc.getStartPage());
        int shown = 0;
        if (first != null) {
            for (String line : first.spokenLines()) {
                if (shown++ >= 4) {
                    lore.add("§8  …");
                    break;
                }
                List<String> wrapped = EditorItems.wrap(line.replace("{player}", player.getName()), 30);
                for (int i = 0; i < wrapped.size(); i++) {
                    lore.add(i == 0 ? "§b" + npc.getName() + " §8⟫ §f" + wrapped.get(i) : "§f   " + wrapped.get(i));
                }
            }
            if (shown == 0) {
                lore.add("§8(the first page has no lines)");
            }
            StringBuilder buttons = new StringBuilder();
            for (CustomNpc.DialogueChoice choice : first.choices()) {
                String chip = "§8[§e" + EditorItems.truncate(choice.text(), 16) + "§8] ";
                if (buttons.length() + chip.length() > 60) {
                    lore.add(buttons.toString().trim());
                    buttons = new StringBuilder();
                }
                buttons.append(chip);
            }
            if (buttons.length() > 0) {
                lore.add(buttons.toString().trim());
            }
        }
        return EditorItems.icon(Material.ENDER_EYE)
                .name("§a▶ Preview conversation")
                .lore(lore)
                .blank()
                .click("Click", "to play it (safe — nothing runs)")
                .click("Shift-click", "to talk for real")
                .build();
    }

    private static ItemStack health(List<NpcCheck.Issue> issues) {
        if (issues.isEmpty()) {
            return EditorItems.icon(Material.LIME_DYE)
                    .name("§a✔ Ready for players")
                    .text("No broken replies, missing pages or unknown quests.")
                    .build();
        }
        long problems = NpcCheck.problems(issues);
        EditorItems.Builder icon = EditorItems.icon(problems > 0 ? Material.RED_DYE : Material.YELLOW_DYE)
                .name(problems > 0
                        ? "§c✖ " + EditorItems.plural((int) problems, "problem") + " to fix"
                        : "§e⚠ " + EditorItems.plural(issues.size(), "thing") + " to check");
        int shown = 0;
        for (NpcCheck.Issue issue : issues) {
            if (shown++ >= 6) {
                icon.lore("§8…and " + (issues.size() - 6) + " more");
                break;
            }
            List<String> wrapped = EditorItems.wrap(issue.text(), 34);
            for (int i = 0; i < wrapped.size(); i++) {
                icon.lore((i == 0 ? (issue.problem() ? "§c✖ " : "§e⚠ ") : "   ") + "§7" + wrapped.get(i));
            }
        }
        return icon.blank().click("Click", "to jump to the first one").build();
    }

    private static ItemStack where(Player player, CustomNpc npc) {
        Location at = npc.location();
        if (at == null) {
            return EditorItems.icon(Material.RECOVERY_COMPASS)
                    .name("§cNot placed")
                    .text(npc.getWorld() == null
                            ? "This NPC has no position yet — use Move here below."
                            : "Its world '" + npc.getWorld() + "' isn't loaded right now.")
                    .build();
        }
        EditorItems.Builder icon = EditorItems.icon(Material.COMPASS)
                .name("§bWhere it stands")
                .lore("§7World: §f" + at.getWorld().getName(),
                        "§7Position: §f" + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ());
        if (at.getWorld().equals(player.getWorld())) {
            icon.lore("§7Distance: §f" + HomeMenu.distance(player, npc));
        }
        return icon.blank().click("Click", "to teleport there").build();
    }

    /** Opens the screen where an issue can be fixed. */
    static void jumpTo(Player player, String npcId, NpcCheck.Issue issue) {
        switch (issue.where()) {
            case QUEST -> QuestMenu.open(player, npcId);
            case PAGE -> PageMenu.open(player, npcId, issue.pageId());
            case REPLY -> ReplyMenu.open(player, npcId, issue.pageId(), issue.index());
            default -> open(player, npcId);
        }
    }
}
