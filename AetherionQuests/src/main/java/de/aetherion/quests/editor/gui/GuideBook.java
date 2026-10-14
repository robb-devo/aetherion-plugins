package de.aetherion.quests.editor.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Written guides for the Opus NPC editor — full EN + DE walkthroughs.
 * Not FancyNpcs /npc. Command is always {@code /aethernpc}.
 */
public final class GuideBook {

    private GuideBook() {
    }

    public static void giveBoth(Player player) {
        give(player, english());
        give(player, german());
        player.sendMessage("§aNPC Editor guides §7(EN + DE) added to your inventory.");
        player.sendMessage("§8Open the books — they explain the whole process.");
    }

    public static ItemStack english() {
        return book(
                "§bNPC Editor Guide",
                "Aetherion",
                "EN",
                enPages()
        );
    }

    public static ItemStack german() {
        return book(
                "§bNPC-Editor Handbuch",
                "Aetherion",
                "DE",
                dePages()
        );
    }

    private static void give(Player player, ItemStack book) {
        var leftover = player.getInventory().addItem(book);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(stack ->
                    player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
    }

    private static ItemStack book(String title, String author, String generation, List<String> pages) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta != null) {
            meta.setTitle(stripColor(title));
            meta.setAuthor(author);
            meta.setGeneration(BookMeta.Generation.ORIGINAL);
            meta.setDisplayName(title + " §8(" + generation + ")");
            meta.setLore(List.of(
                    "§7Aetherion Opus NPC studio",
                    "§8Language: §f" + generation,
                    "§7Command: §f/aethernpc"
            ));
            meta.setPages(pages);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String stripColor(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    private static List<String> enPages() {
        List<String> p = new ArrayList<>();
        p.add("""
                §lAetherion NPC Editor
                §8—————————————
                §0This is the §lOpus studio§0.
                Command: §9/aethernpc
                Alias: §9/npceditor

                §cNOT§0 FancyNpcs §9/npc§0.
                That plugin is only skins/
                bodies for living cast.

                Need permission:
                §9aetherion.npc.editor
                """);
        p.add("""
                §lWhat this is for
                §8—————————————
                Create §lmoderator NPCs§0:
                talking FancyNPCs with
                dialogue pages, choices,
                and optional quest links.

                §cDoes NOT edit story NPCs§0
                (Egon, Twig, Miss Canopy…).
                Those live in §9npcs.yml§0 and
                stay locked to this tool.
                """);
        p.add("""
                §lOpen the editor
                §8—————————————
                • DEV → Admin → §9NPC Editor
                • Content Kit → §9NPC Editor
                • Chat: §9/aethernpc
                • Or get the §6wand§0 and
                  right-click air

                Main menu buttons:
                Create · Nearby · List ·
                Wand · Help · Guides
                """);
        p.add("""
                §l1) Create an NPC
                §8—————————————
                Click §aCreate NPC§0 (or
                §9/aethernpc create§0).

                Type a §ldisplay name§0 in chat.
                Cancel with §9cancel§0.

                The NPC appears at your
                feet and the §lEdit§0 menu opens.

                Tip: stand where you want
                them before creating.
                """);
        p.add("""
                §l2) Edit menu
                §8—————————————
                §eRename§0 — display name
                §eSubtitle§0 — small line under
                §6Appearance§0 — preset + skin
                §dDialogue§0 — talk pages
                §aQuest link§0 — existing quest
                §bMove here§0 — to your feet
                §bLook at me§0 — face you
                §eDuplicate§0 — clone beside you
                §cDelete§0 — asks first
                """);
        p.add("""
                §l3) Appearance
                §8—————————————
                Pick a clothing §lpreset§0 and
                a §lskin username§0 (player look).

                FancyNpcs renders the body.
                If a skin fails to load, you
                still get a usable NPC —
                retry skin later.
                """);
        p.add("""
                §l4) Dialogue
                §8—————————————
                Build §lpages§0 of text.
                Each page can have §lchoices§0
                that jump to other pages
                or run actions.

                Type lines in chat when
                prompted. Use §9cancel§0 to abort
                a prompt.

                Set which page is the
                §lstart§0 page for first talk.
                """);
        p.add("""
                §l5) Quest link
                §8—————————————
                Optional: attach an
                §lexisting§0 quest id so the
                NPC can offer that quest.

                This does §lnot§0 invent new
                quests in the YAML — it
                only §llinks§0 what already
                exists in Quests.

                Clear the link if you only
                want dialogue.
                """);
        p.add("""
                §l6) Wand
                §8—————————————
                §6Blaze rod§0 from the menu:
                • Right-click air → main menu
                • Right-click editor NPC → edit
                • Sneak + right-click → delete
                  confirm

                Story cast NPCs are never
                deleted by this wand.
                """);
        p.add("""
                §l7) List / nearby / move
                §8—————————————
                §6List§0 — every editor NPC
                you created (paged).

                §eNearby§0 — edit closest within
                8 blocks.

                §9/aethernpc move§0 — teleport
                that NPC to you.

                §9duplicate§0 — clone next to you.
                """);
        p.add("""
                §lCommands cheat-sheet
                §8—————————————
                §9/aethernpc
                /aethernpc create [name]
                /aethernpc edit|nearby
                /aethernpc list
                /aethernpc move|duplicate
                /aethernpc delete
                /aethernpc wand|help|guide

                Alias: §9/npceditor
                """);
        p.add("""
                §lStorage & safety
                §8—————————————
                Saved to:
                §9plugins/AetherionQuests/
                  editor-npcs.yml

                Survives restart.
                Jar never overwrites it.

                Story cast: §9npcs.yml§0
                — separate, protected.

                Permission is §lnot§0 the same
                as §9aetherionquests.admin§0.
                """);
        p.add("""
                §lCommon mistakes
                §8—————————————
                • Using Fancy §9/npc§0 — wrong
                  tool (skins only world).
                • Trying to edit story NPCs
                  — use quest/admin tools.
                • Empty dialogue — players
                  get nothing useful.
                • Linking a quest id that
                  does not exist.

                When stuck: §9/aethernpc help
                or re-open this book.
                """);
        p.add("""
                §lQuick happy path
                §8—————————————
                1. §9/aethernpc
                2. Create → type name
                3. Appearance → look good
                4. Dialogue → 1–2 pages
                5. Optional quest link
                6. Move / look at me
                7. Talk to them in-world

                You’re done. Have fun.
                — Aetherion
                """);
        return p;
    }

    private static List<String> dePages() {
        List<String> p = new ArrayList<>();
        p.add("""
                §lAetherion NPC-Editor
                §8—————————————
                Das ist das §lOpus-Studio§0.
                Befehl: §9/aethernpc
                Alias: §9/npceditor

                §cNICHT§0 FancyNpcs §9/npc§0.
                Das Plugin macht nur Skins/
                Körper für den Story-Cast.

                Permission:
                §9aetherion.npc.editor
                """);
        p.add("""
                §lWofür ist das?
                §8—————————————
                Moderator-NPCs erstellen:
                FancyNPCs mit Dialog-Seiten,
                Auswahl-Buttons und
                optionaler Quest-Verknüpfung.

                §cStory-NPCs werden hier
                NICHT bearbeitet§0
                (Egon, Twig, Miss Canopy…).
                Die stehen in §9npcs.yml§0 und
                bleiben für dieses Tool tabu.
                """);
        p.add("""
                §lEditor öffnen
                §8—————————————
                • DEV → Admin → §9NPC Editor
                • Content Kit → §9NPC Editor
                • Chat: §9/aethernpc
                • Oder §6Stab§0 holen und
                  Luft rechtsklicken

                Hauptmenü:
                Erstellen · In der Nähe ·
                Liste · Stab · Hilfe · Guides
                """);
        p.add("""
                §l1) NPC erstellen
                §8—————————————
                §aCreate NPC§0 klicken (oder
                §9/aethernpc create§0).

                §lAnzeigenamen§0 in den Chat
                tippen. Abbruch: §9cancel§0.

                Der NPC spawnt an deinen
                Füßen → §lEdit§0-Menü öffnet
                sich.

                Tipp: vorher hinstehen,
                wo er stehen soll.
                """);
        p.add("""
                §l2) Edit-Menü
                §8—————————————
                §eRename§0 — Anzeigename
                §eSubtitle§0 — kleine Zeile
                §6Appearance§0 — Outfit + Skin
                §dDialogue§0 — Gesprächsseiten
                §aQuest link§0 — bestehende Quest
                §bMove here§0 — zu dir
                §bLook at me§0 — schaut dich an
                §eDuplicate§0 — Klon neben dir
                §cDelete§0 — fragt vorher
                """);
        p.add("""
                §l3) Aussehen
                §8—————————————
                §lPreset§0 (Kleidung) wählen und
                §lSkin-Username§0 setzen
                (Spieler-Optik).

                FancyNpcs rendert den Body.
                Lädt der Skin nicht, bleibt
                der NPC nutzbar — Skin
                später nochmal setzen.
                """);
        p.add("""
                §l4) Dialog
                §8—————————————
                Baue §lSeiten§0 mit Text.
                Seiten können §lChoices§0 haben,
                die zu anderen Seiten
                springen oder Aktionen
                auslösen.

                Texte tippst du im Chat,
                wenn gefragt. §9cancel§0 bricht
                die Eingabe ab.

                Lege die §lStartseite§0 fest
                für das erste Gespräch.
                """);
        p.add("""
                §l5) Quest verknüpfen
                §8—————————————
                Optional: eine §lbestehende§0
                Quest-ID anhängen, damit
                der NPC die Quest anbieten
                kann.

                Es werden §lkeine§0 neuen
                Quests erfunden — nur
                §lverlinkt§0, was schon in
                Quests existiert.

                Link löschen = nur Dialog.
                """);
        p.add("""
                §l6) Stab (Wand)
                §8—————————————
                §6Lohenrute§0 aus dem Menü:
                • Rechtsklick Luft → Menü
                • Rechtsklick Editor-NPC
                  → bearbeiten
                • Schleichen + Rechtsklick
                  → Löschen bestätigen

                Story-Cast wird damit
                niemals gelöscht.
                """);
        p.add("""
                §l7) Liste / Nähe / Move
                §8—————————————
                §6List§0 — alle Editor-NPCs,
                die du erstellt hast.

                §eNearby§0 — nächster in
                8 Blöcken bearbeiten.

                §9/aethernpc move§0 — NPC zu
                dir teleportieren.

                §9duplicate§0 — Klon neben dir.
                """);
        p.add("""
                §lBefehle
                §8—————————————
                §9/aethernpc
                /aethernpc create [name]
                /aethernpc edit|nearby
                /aethernpc list
                /aethernpc move|duplicate
                /aethernpc delete
                /aethernpc wand|help|guide

                Alias: §9/npceditor
                """);
        p.add("""
                §lSpeicher & Sicherheit
                §8—————————————
                Gespeichert in:
                §9plugins/AetherionQuests/
                  editor-npcs.yml

                Überlebt Restarts.
                Jar überschreibt das nie.

                Story-Cast: §9npcs.yml§0
                — getrennt, geschützt.

                Permission ≠ 
                §9aetherionquests.admin§0.
                """);
        p.add("""
                §lTypische Fehler
                §8—————————————
                • Fancy §9/npc§0 benutzen —
                  falsches Tool.
                • Story-NPCs editieren
                  wollen — geht hier nicht.
                • Leerer Dialog — Spieler
                  kriegt nichts Sinnvolles.
                • Quest-ID, die es nicht
                  gibt, verlinken.

                Hilfe: §9/aethernpc help
                oder dieses Buch nochmal.
                """);
        p.add("""
                §lSchnellweg
                §8—————————————
                1. §9/aethernpc
                2. Create → Name tippen
                3. Appearance → gut aussehen
                4. Dialogue → 1–2 Seiten
                5. Optional Quest link
                6. Move / Look at me
                7. In-world ansprechen

                Fertig. Viel Spaß.
                — Aetherion
                """);
        return p;
    }
}
