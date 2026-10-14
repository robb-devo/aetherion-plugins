package net.aetherion.samuraiboss.render.fx;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

/**
 * Block-"Farben" für Effekte. Mit voller Helligkeit (Brightness 15) wirken Concrete-Blöcke
 * wie Licht, gefärbtes Glas wie leuchtende, durchscheinende Energie.
 */
public final class Palette {

    public static BlockData LIGHT;          // reines Weiß
    public static BlockData PINK;           // kräftiges Rosa
    public static BlockData PINK_SOFT;      // Blütenrosa (Wolle)
    public static BlockData PETAL_WHITE;    // weiße Blüte
    public static BlockData PINK_GLASS;     // rosa Leuchtglas
    public static BlockData WHITE_GLASS;
    public static BlockData RED_GLASS;      // Warn-Rot (Telegrafie)
    public static BlockData RED;
    public static BlockData MAGENTA;
    public static BlockData MAGENTA_GLASS;
    public static BlockData BLACK_GLASS;
    public static BlockData PURPLE_GLASS;
    public static BlockData PURPLE;
    public static BlockData STEEL;
    public static BlockData GOLD;
    public static BlockData CHERRY_LEAVES;
    public static BlockData CRIMSON_GLASS;

    private Palette() {
    }

    public static void init() {
        LIGHT = Bukkit.createBlockData("minecraft:white_concrete");
        PINK = Bukkit.createBlockData("minecraft:pink_concrete");
        PINK_SOFT = Bukkit.createBlockData("minecraft:pink_wool");
        PETAL_WHITE = Bukkit.createBlockData("minecraft:white_wool");
        PINK_GLASS = Bukkit.createBlockData("minecraft:pink_stained_glass");
        WHITE_GLASS = Bukkit.createBlockData("minecraft:white_stained_glass");
        RED_GLASS = Bukkit.createBlockData("minecraft:red_stained_glass");
        RED = Bukkit.createBlockData("minecraft:red_concrete");
        MAGENTA = Bukkit.createBlockData("minecraft:magenta_concrete");
        MAGENTA_GLASS = Bukkit.createBlockData("minecraft:magenta_stained_glass");
        BLACK_GLASS = Bukkit.createBlockData("minecraft:black_stained_glass");
        PURPLE_GLASS = Bukkit.createBlockData("minecraft:purple_stained_glass");
        PURPLE = Bukkit.createBlockData("minecraft:purple_concrete");
        STEEL = Bukkit.createBlockData("minecraft:iron_block");
        GOLD = Bukkit.createBlockData("minecraft:gold_block");
        CHERRY_LEAVES = Bukkit.createBlockData("minecraft:cherry_leaves[persistent=true]");
        CRIMSON_GLASS = Bukkit.createBlockData("minecraft:red_stained_glass");
    }
}
