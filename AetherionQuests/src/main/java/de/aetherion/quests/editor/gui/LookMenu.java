package de.aetherion.quests.editor.gui;

import de.aetherion.quests.editor.AppearancePreset;
import de.aetherion.quests.editor.CustomNpc;
import de.aetherion.quests.editor.NpcEditor;
import de.aetherion.quests.editor.TextInput;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * NPC workspace — Look tab: name tag (name + subtitle), skin, arms and outfit.
 */
public final class LookMenu {

    private static final int NAME = 19;
    private static final int SUBTITLE = 20;
    private static final int SKIN = 23;
    private static final int ARMS = 24;
    private static final int OUTFIT_LABEL = 27;
    private static final int[] OUTFITS = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42};

    private LookMenu() {
    }

    public static void open(Player player, String npcId) {
        CustomNpc npc = Frame.require(player, npcId);
        if (npc == null) {
            return;
        }
        NpcEditor editor = Frame.editor();
        Runnable reopen = () -> open(player, npcId);
        Menu menu = Frame.workspace(player, npc, Frame.Tab.LOOK, reopen);

        menu.set(NAME, EditorItems.icon(Material.NAME_TAG)
                .name("§e✎ Name")
                .text("Shown above the NPC's head and in front of every line it says.")
                .blank()
                .lore("§7Now: §f" + npc.getName())
                .blank()
                .click("Click", "to change")
                .build(), click -> editor.ask(click.player(), TextInput.builder("Name for " + npc.getName())
                .hint("Up to 32 characters.")
                .current(npc.getName())
                .max(32)
                .handler((p, text) -> {
                    String name = NpcEditor.colorSafe(text);
                    if (name.isBlank()) {
                        return "The name can't be empty.";
                    }
                    editor.change(p, editor.npc(npcId), "Rename to " + name, n -> n.setName(name));
                    return null;
                })
                .then(p -> open(p, npcId))
                .build()));

        EditorItems.Builder subtitle = EditorItems.icon(Material.OAK_SIGN)
                .name("§e✎ Subtitle")
                .text("Small gray line under the name, e.g. \"Blacksmith\".")
                .blank()
                .lore("§7Now: " + (npc.hasSubtitle() ? "§f" + npc.getSubtitle() : "§8none"))
                .blank()
                .click("Click", "to change");
        if (npc.hasSubtitle()) {
            subtitle.danger("Press Q", "to remove it");
        }
        menu.set(SUBTITLE, subtitle.build(), click -> {
            if (click.drop()) {
                if (editor.npc(npcId).hasSubtitle()) {
                    editor.change(click.player(), editor.npc(npcId), "Remove subtitle", n -> n.setSubtitle(""));
                }
                open(click.player(), npcId);
                return;
            }
            editor.ask(click.player(), TextInput.builder("Subtitle for " + npc.getName())
                    .hint("Up to 32 characters. Type §fnone §7to show no subtitle.")
                    .current(npc.hasSubtitle() ? npc.getSubtitle() : null)
                    .max(32)
                    .handler((p, text) -> {
                        String value = text.equalsIgnoreCase("none") ? "" : NpcEditor.colorSafe(text);
                        editor.change(p, editor.npc(npcId), value.isEmpty() ? "Remove subtitle" : "Subtitle: " + value,
                                n -> n.setSubtitle(value));
                        return null;
                    })
                    .then(p -> open(p, npcId))
                    .build());
        });

        EditorItems.Builder skin = EditorItems.icon(Material.PLAYER_HEAD)
                .skull(npc.getSkinUsername())
                .name("§e✎ Skin")
                .text("Any Minecraft username — the NPC wears that player's skin.")
                .blank()
                .lore("§7Now: §f" + npc.getSkinUsername() + (npc.usesOutfitSkin() ? " §8(outfit default)" : ""))
                .blank()
                .click("Click", "to type a username");
        if (!npc.usesOutfitSkin()) {
            skin.danger("Press Q", "to go back to the outfit's skin");
        }
        menu.set(SKIN, skin.build(), click -> {
            if (click.drop()) {
                CustomNpc live = editor.npc(npcId);
                if (!live.usesOutfitSkin()) {
                    editor.change(click.player(), live, "Use outfit skin", n -> {
                        n.setSkinUsername(n.getPreset().skinUsername());
                        n.setSlim(n.getPreset().slim());
                    });
                }
                open(click.player(), npcId);
                return;
            }
            editor.ask(click.player(), TextInput.builder("Skin for " + npc.getName())
                    .hint("Type a Minecraft username (3–16 letters, digits or _).")
                    .hint("The skin loads a moment after you confirm.")
                    .current(npc.getSkinUsername())
                    .max(16)
                    .handler((p, text) -> {
                        String username = text.trim();
                        if (!username.matches("[A-Za-z0-9_]{3,16}")) {
                            return "That isn't a valid Minecraft username.";
                        }
                        editor.change(p, editor.npc(npcId), "Skin: " + username, n -> n.setSkinUsername(username));
                        return null;
                    })
                    .then(p -> open(p, npcId))
                    .build());
        });

        menu.set(ARMS, EditorItems.icon(Material.ARMOR_STAND)
                .name("§eArms: §f" + (npc.isSlim() ? "Slim" : "Wide"))
                .text("Match the skin: slim (Alex-style, 3px) or wide (Steve-style, 4px) arms.")
                .blank()
                .click("Click", "to switch to " + (npc.isSlim() ? "wide" : "slim"))
                .build(), click -> {
            CustomNpc live = editor.npc(npcId);
            boolean slim = !live.isSlim();
            editor.change(click.player(), live, slim ? "Slim arms" : "Wide arms", n -> n.setSlim(slim));
            open(click.player(), npcId);
        });

        menu.set(OUTFIT_LABEL, EditorItems.icon(EditorItems.dyed(Material.LEATHER_CHESTPLATE, npc.getPreset().color()))
                .name("§6§lOutfit ▸")
                .text("Clothes and the item in the NPC's hand.")
                .blank()
                .lore("§7Now: §f" + npc.getPreset().label())
                .build());
        AppearancePreset[] presets = AppearancePreset.values();
        for (int i = 0; i < presets.length && i < OUTFITS.length; i++) {
            AppearancePreset preset = presets[i];
            boolean worn = preset == npc.getPreset();
            EditorItems.Builder icon = EditorItems.icon(EditorItems.dyed(Material.LEATHER_CHESTPLATE, preset.color()))
                    .name((worn ? "§a✔ " : "§e") + preset.label())
                    .lore("§7Holds: §f" + preset.handName(),
                            "§7Default skin: §f" + preset.skinUsername())
                    .glow(worn)
                    .blank();
            if (worn) {
                icon.lore("§a▸ Wearing this");
            } else {
                icon.click("Click", "to wear this");
                if (!npc.usesOutfitSkin()) {
                    icon.lore("§8Your custom skin stays.");
                }
            }
            menu.set(OUTFITS[i], icon.build(), worn ? null : click -> {
                editor.change(click.player(), editor.npc(npcId), "Outfit: " + preset.label(), n -> {
                    boolean defaultSkin = n.usesOutfitSkin();
                    n.setPreset(preset);
                    if (defaultSkin) {
                        n.setSkinUsername(preset.skinUsername());
                        n.setSlim(preset.slim());
                    }
                });
                open(click.player(), npcId);
            });
        }

        Frame.help(menu, reopen,
                "§7How the NPC looks in the world.",
                "",
                "§fName §7and §fSubtitle §7float above its head.",
                "§fSkin §7copies any player's skin by name.",
                "§fOutfit §7sets clothes + held item; it keeps",
                "§7a custom skin you picked.",
                "",
                "§8Changes show in the world right away.");
        Frame.show(menu, player);
    }
}
