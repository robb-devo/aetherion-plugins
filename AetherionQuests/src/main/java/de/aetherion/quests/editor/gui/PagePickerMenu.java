package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.CustomNpc;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * "Which page?" — pick an existing page (or make a new one) instead of typing page ids.
 * Used by replies ("Continue talking") and by the quest stages.
 */
public final class PagePickerMenu {

    private static final int FIRST = 9;
    private static final int SLOTS = 36;

    /**
     * @param question     shown on the header card
     * @param current      currently chosen page id (highlighted), or null
     * @param defaultLabel when set, first tile picks "no page" (null) with this label
     * @param onPick       receives the chosen page id (null = the default tile)
     */
    public record Request(String question, String current, String defaultLabel,
                          BiConsumer<Player, String> onPick, Consumer<Player> onBack, String backLabel) {
    }

    private PagePickerMenu() {
    }

    public static void open(Player player, String npcId, Request request) {
        CustomNpc npc = Frame.require(player, npcId);
        if (npc == null) {
            return;
        }
        Menu menu = Frame.screen(Frame.title(npc.getName(), "Pick a page"));
        menu.set(4, EditorItems.icon(Material.BOOK)
                .name("§f" + request.question())
                .text("Click a page below — or make a new one.")
                .build());
        int slot = FIRST;
        if (request.defaultLabel() != null) {
            boolean selected = request.current() == null;
            menu.set(slot++, EditorItems.icon(Material.NETHER_STAR)
                    .name((selected ? "§a✔ " : "§f↺ ") + request.defaultLabel())
                    .glow(selected)
                    .blank()
                    .click("Click", "to use this")
                    .build(), click -> request.onPick().accept(click.player(), null));
        }
        for (CustomNpc.DialoguePage page : npc.orderedPages()) {
            if (slot >= FIRST + SLOTS - 1) {
                break;
            }
            boolean selected = page.id().equalsIgnoreCase(request.current());
            boolean first = npc.isStartPage(page.id());
            EditorItems.Builder icon = EditorItems.icon(first ? Material.WRITTEN_BOOK : Material.BOOK)
                    .name((selected ? "§a✔ " : first ? "§a★ " : "§e") + page.title())
                    .glow(selected);
            List<String> lines = page.spokenLines();
            for (int i = 0; i < lines.size() && i < 2; i++) {
                icon.lore("§f" + EditorItems.quote(lines.get(i).replace("{player}", player.getName()), 30));
            }
            if (lines.isEmpty()) {
                icon.lore("§8(no lines yet)");
            }
            icon.lore("§8" + Frame.replies(page.choices().size()));
            icon.blank().click("Click", selected ? "to keep it" : "to pick it");
            String pageId = page.id();
            menu.set(slot++, icon.build(), click -> request.onPick().accept(click.player(), pageId));
        }
        if (npc.pages().size() < CustomNpc.MAX_PAGES) {
            menu.set(slot, EditorItems.icon(Material.LIME_DYE)
                    .name("§a+ New page")
                    .text("Type a short title — the page is created and picked.")
                    .build(), click -> DialogueMenu.newPage(click.player(), npcId,
                    request.onPick(), p -> open(p, npcId, request)));
        }
        Frame.back(menu, request.backLabel(), () -> request.onBack().accept(player));
        Frame.help(menu, () -> open(player, npcId, request),
                "§7Pick the page to use.",
                "§a★ §7is the page conversations start on.",
                "§a+ New page §7makes one and picks it.");
        Frame.show(menu, player);
    }
}
