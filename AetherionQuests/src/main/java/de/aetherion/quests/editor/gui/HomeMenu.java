package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.NpcCheck;
import de.aetherion.quests.editor.NpcEditor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Studio home: every editor NPC (nearest first) + "New NPC". The one place to pick what to work on.
 */
public final class HomeMenu {

    private static final int FIRST = 9;
    private static final int PER_PAGE = 36;

    private HomeMenu() {
    }

    public static void open(Player player) {
        NpcEditor editor = NpcEditor.get();
        open(player, editor == null ? 0 : editor.sessions().of(player).homePage());
    }

    public static void open(Player player, int requestedPage) {
        NpcEditor editor = NpcEditor.get();
        if (editor == null) {
            return;
        }
        List<CustomNpc> npcs = sorted(editor, player);
        int pages = Math.max(1, (npcs.size() + PER_PAGE - 1) / PER_PAGE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        editor.sessions().of(player).setHomePage(page);

        String title = npcs.isEmpty() ? "§8NPC Studio" : "§8NPC Studio §7· " + npcs.size() + " NPC" + (npcs.size() == 1 ? "" : "s");
        Menu menu = Menu.of(6, title);

        CustomNpc nearest = editor.service().nearby(player, 16);
        if (nearest != null) {
            String id = nearest.getId();
            menu.set(2, EditorItems.icon(Material.COMPASS)
                    .name("§bNearest: §f" + nearest.getName())
                    .lore("§7" + distance(player, nearest) + " away")
                    .blank()
                    .click("Click", "to open it")
                    .build(), click -> OverviewMenu.open(click.player(), id));
        }
        menu.set(4, EditorItems.icon(Material.LIME_DYE)
                .name("§a§l+ New NPC")
                .text("Pick a starting point, give it a name — it appears right where you're standing.")
                .blank()
                .click("Click", "to create one")
                .build(), click -> TemplateMenu.open(click.player()));
        menu.set(6, EditorItems.icon(Material.BLAZE_ROD)
                .name("§6Get the wand")
                .lore("§7Right-click §fair §8→ §7this studio",
                        "§7Right-click §fan NPC §8→ §7edit it",
                        "§7Sneak-right-click §fan NPC §8→ §7talk to it")
                .blank()
                .click("Click", "to get it")
                .build(), click -> editor.giveWand(click.player()));

        if (npcs.isEmpty()) {
            menu.set(22, EditorItems.icon(Material.WRITABLE_BOOK)
                    .name("§fNo NPCs yet")
                    .text("Your NPCs will show up here — the closest one first.")
                    .blank()
                    .click("Click", "to create your first NPC")
                    .build(), click -> TemplateMenu.open(click.player()));
        } else {
            int start = page * PER_PAGE;
            for (int i = 0; i < PER_PAGE && start + i < npcs.size(); i++) {
                CustomNpc npc = npcs.get(start + i);
                String id = npc.getId();
                menu.set(FIRST + i, card(editor, player, npc, start + i == 0 && nearest != null), click -> {
                    if (click.shift()) {
                        CustomNpc target = editor.npc(id);
                        click.player().closeInventory();
                        editor.teleportTo(click.player(), target);
                        return;
                    }
                    OverviewMenu.open(click.player(), id);
                });
            }
        }

        Frame.undo(menu, player, null, () -> open(player));
        Frame.pages(menu, page, pages, next -> open(player, next));
        Frame.close(menu);
        Frame.help(menu, () -> open(player),
                "§7This is your NPC list — nearest first.",
                "",
                "§e+ New NPC §7creates one where you stand.",
                "§eClick §7an NPC to edit it,",
                "§eShift-click §7to teleport to it.",
                "",
                "§8Story NPCs (Egon, Twig, …) are protected",
                "§8and never listed here.");
        Frame.show(menu, player);
    }

    private static ItemStack card(NpcEditor editor, Player player, CustomNpc npc, boolean nearest) {
        EditorItems.Builder icon = EditorItems.icon(Material.PLAYER_HEAD)
                .skull(npc.getSkinUsername())
                .name("§b§l" + npc.getName());
        if (npc.hasSubtitle()) {
            icon.lore("§7" + npc.getSubtitle());
        }
        icon.blank();
        String first = npc.firstLine();
        icon.lore(EditorItems.BULLET + (first == null ? "§8Says nothing yet" : "§f" + EditorItems.quote(first, 30)));
        icon.lore(EditorItems.BULLET + "§7" + EditorItems.plural(npc.pages().size(), "page") + " §8· §7" + Frame.replies(npc.totalChoices()));
        if (npc.hasLinkedQuest()) {
            icon.lore(EditorItems.BULLET + "§aQuest: §f" + editor.quests().title(npc.getLinkedQuestId()));
        }
        icon.lore(EditorItems.BULLET + where(player, npc) + (nearest ? " §a(nearest)" : ""));
        List<NpcCheck.Issue> issues = NpcCheck.run(npc, editor.quests());
        long problems = NpcCheck.problems(issues);
        if (problems > 0) {
            icon.lore(EditorItems.BULLET + "§c✖ " + EditorItems.plural((int) problems, "problem") + " to fix");
        } else if (!issues.isEmpty()) {
            icon.lore(EditorItems.BULLET + "§e⚠ " + EditorItems.plural(issues.size(), "thing") + " to check");
        }
        if (npc.getEditedBy() != null) {
            icon.blank().lore("§8Last edit: " + npc.getEditedBy() + ", " + EditorItems.ago(npc.getEditedAt()));
        }
        return icon.blank()
                .click("Click", "to open")
                .click("Shift-click", "to teleport there")
                .build();
    }

    private static String where(Player player, CustomNpc npc) {
        Location at = npc.location();
        if (npc.getWorld() == null) {
            return "§cNot placed yet";
        }
        if (at == null) {
            return "§cWorld '" + npc.getWorld() + "' isn't loaded";
        }
        if (at.getWorld().equals(player.getWorld())) {
            return "§7" + distance(player, npc) + " away";
        }
        return "§7In §f" + npc.getWorld();
    }

    static String distance(Player player, CustomNpc npc) {
        Location at = npc.location();
        if (at == null || !at.getWorld().equals(player.getWorld())) {
            return "far";
        }
        return Math.round(at.distance(player.getLocation())) + "m";
    }

    /** Same world nearest-first, then everything else by name. */
    private static List<CustomNpc> sorted(NpcEditor editor, Player player) {
        List<CustomNpc> here = new ArrayList<>();
        List<CustomNpc> elsewhere = new ArrayList<>();
        for (CustomNpc npc : editor.storage().all()) {
            Location at = npc.location();
            if (at != null && at.getWorld().equals(player.getWorld())) {
                here.add(npc);
            } else {
                elsewhere.add(npc);
            }
        }
        Location me = player.getLocation();
        here.sort(Comparator.comparingDouble(npc -> npc.location().distanceSquared(me)));
        elsewhere.sort(Comparator.comparing(npc -> String.valueOf(npc.getName()).toLowerCase(Locale.ROOT)));
        List<CustomNpc> out = new ArrayList<>(here);
        out.addAll(elsewhere);
        return out;
    }
}
