package net.aetherion.samuraiboss.boss;

import net.aetherion.samuraiboss.Settings;
import net.aetherion.samuraiboss.render.PackItems;
import net.aetherion.samuraiboss.render.model.Skin;
import org.bukkit.Color;
import org.bukkit.entity.Display;

import static net.aetherion.samuraiboss.boss.SamuraiModel.*;

/**
 * Die Erscheinungsformen des Kensei:
 * <ul>
 *   <li><b>normal</b> – Phase 1: schwarzer Lack, rote Seidenschnürung, Gold, weißes Haar</li>
 *   <li><b>storm</b> – Phase 2: ohne Helm/Banner (weggerissen), die Klinge glüht rosa</li>
 *   <li><b>ghost</b> – Phase 3: Rüstung zerbrochen, Körper aus leuchtendem Kristall, rote Maske</li>
 *   <li><b>shadow</b> – Schattenklone: dunkles Glas mit violettem Schimmer</li>
 *   <li><b>afterimage</b> – Nachbilder bei Sprints: rosa Glas</li>
 * </ul>
 */
public final class SamuraiSkins {

    public static final Color SAKURA = Color.fromRGB(255, 160, 190);
    public static final Color SHADOW_GLOW = Color.fromRGB(140, 70, 210);

    public final Skin normal;
    public final Skin storm;
    public final Skin ghost;
    public final Skin shadow;
    public final Skin afterimage;

    public SamuraiSkins(Settings s) {
        Skin.Builder n = Skin.builder("normal")
                .fallback("minecraft:black_concrete")
                .block("hakama", "minecraft:gray_wool")
                .block("robe", "minecraft:white_wool")
                .block("armor", "minecraft:black_concrete")
                .block("armor2", "minecraft:polished_blackstone")
                .block("lace", "minecraft:red_wool")
                .block("obi", "minecraft:red_concrete")
                .block("gold", "minecraft:gold_block")
                .block("sakura", "minecraft:pink_wool")
                .block("skin", "minecraft:white_terracotta")
                .block("mask", "minecraft:red_concrete")
                .block("hair", "minecraft:white_wool")
                .block("eyes", "minecraft:white_concrete")
                .block("sleeve", "minecraft:black_wool")
                .block("kote", "minecraft:black_concrete")
                .block("glove", "minecraft:brown_terracotta")
                .block("grip", "minecraft:black_wool")
                .block("guard", "minecraft:polished_blackstone")
                .block("steel", "minecraft:iron_block")
                .block("edge", "minecraft:snow_block")
                .block("saya", "minecraft:black_concrete")
                .block("pole", "minecraft:dark_oak_planks")
                .block("flag", "minecraft:white_wool");
        if (s.packEnabled) {
            n.item("katana_item", PackItems.item(s, "katana"))
                    .item("saya_item", PackItems.item(s, "saya"))
                    .item("hilt_item", PackItems.item(s, "hilt"));
        }
        normal = n.build();

        storm = normal.derive("storm")
                .hide(T_HELMET, T_BANNER)
                .block("edge", "minecraft:pink_concrete")
                .glow(SAKURA, T_BLADE)
                .build();

        Skin.Builder g = normal.derive("ghost")
                .hide(T_HELMET, T_BANNER, T_PLATE, T_FEET)
                .block("hakama", "minecraft:pink_stained_glass")
                .block("robe", "minecraft:white_stained_glass")
                .block("sleeve", "minecraft:pink_stained_glass")
                .block("skin", "minecraft:white_stained_glass")
                .block("glove", "minecraft:pink_stained_glass")
                .block("kote", "minecraft:magenta_stained_glass")
                .block("hair", "minecraft:pink_wool")
                .block("eyes", "minecraft:magenta_concrete")
                .block("steel", "minecraft:white_stained_glass")
                .block("edge", "minecraft:pink_concrete")
                .block("grip", "minecraft:magenta_stained_glass")
                .block("saya", "minecraft:black_stained_glass")
                .block("obi", "minecraft:red_stained_glass")
                .brightness(new Display.Brightness(15, 15))
                .glow(SAKURA, T_BLADE);
        if (s.packEnabled) g.item("katana_item", PackItems.item(s, "katana-ghost"));
        ghost = g.build();

        shadow = Skin.builder("shadow")
                .fallback("minecraft:black_stained_glass")
                .block("eyes", "minecraft:purple_concrete")
                .block("edge", "minecraft:purple_concrete")
                .block("mask", "minecraft:purple_stained_glass")
                .block("gold", "minecraft:purple_stained_glass")
                .block("hair", "minecraft:gray_stained_glass")
                .hide(T_BANNER, T_HELMET)
                .brightness(new Display.Brightness(6, 6))
                .glow(SHADOW_GLOW)
                .build();

        afterimage = Skin.builder("afterimage")
                .fallback("minecraft:pink_stained_glass")
                .block("steel", "minecraft:white_stained_glass")
                .block("edge", "minecraft:white_stained_glass")
                .block("hair", "minecraft:white_stained_glass")
                .brightness(new Display.Brightness(15, 15))
                .build();
    }
}
