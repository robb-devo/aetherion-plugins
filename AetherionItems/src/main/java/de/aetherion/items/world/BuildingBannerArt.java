package de.aetherion.items.world;

import org.bukkit.map.MapPalette;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * Renders a wide Aetherion-style building banner (7 maps × 128px = 896×128).
 * One continuous rectangle — no pill ends — with huge readable titles.
 */
final class BuildingBannerArt {

    /** Bump when art changes so maps get rebuilt. */
    static final int ART_VERSION = 5;

    static final int PANELS = 7;
    static final int PANEL = 128;
    static final int WIDTH = PANELS * PANEL;
    static final int HEIGHT = PANEL;

    private static final Map<BuildingBannerKind, byte[][]> CACHE = new EnumMap<>(BuildingBannerKind.class);

    private BuildingBannerArt() {
    }

    static byte[][] image(BuildingBannerKind kind) {
        return CACHE.computeIfAbsent(kind, BuildingBannerArt::paint);
    }

    static void blit(byte[][] full, int panelIndex, org.bukkit.map.MapCanvas canvas) {
        int ox = panelIndex * PANEL;
        for (int x = 0; x < PANEL; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                canvas.setPixel(x, y, full[ox + x][y]);
            }
        }
    }

    private static byte[][] paint(BuildingBannerKind kind) {
        byte[][] px = new byte[WIDTH][HEIGHT];
        byte voidC = c(8, 4, 14);
        byte deep = c(32, 14, 56);
        byte mid = c(78, 32, 128);
        byte bright = c(148, 72, 220);
        byte hot = c(210, 140, 255);
        byte tip = c(245, 220, 255);
        byte gold = c(230, 196, 96);
        byte line = c(150, 90, 210);
        byte ink = c(250, 236, 255);

        // Full-width body (no capsule / pill fade at the ends)
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                double cy = Math.abs(y - 64) / 64.0;
                if (cy < 0.55) {
                    px[x][y] = mid;
                } else if (cy < 0.78) {
                    px[x][y] = deep;
                } else {
                    px[x][y] = voidC;
                }
            }
        }

        // Continuous flame rails, edge to edge
        for (int x = 0; x < WIDTH; x++) {
            double wave = Math.sin(x * 0.09) * 5 + Math.sin(x * 0.033 + 1.7) * 4 + Math.sin(x * 0.18) * 2;
            int topH = (int) Math.round(16 + wave);
            int botH = (int) Math.round(14 + Math.sin(x * 0.12 + 0.8) * 4 + Math.sin(x * 0.045) * 3);
            for (int y = 0; y < topH && y < HEIGHT; y++) {
                double t = y / (double) Math.max(1, topH);
                px[x][y] = flameColor(t, deep, mid, bright, hot, tip);
            }
            for (int y = 0; y < botH && y < HEIGHT; y++) {
                double t = y / (double) Math.max(1, botH);
                px[x][HEIGHT - 1 - y] = flameColor(t, deep, mid, bright, hot, tip);
            }
            if ((x * 37 + 11) % 23 == 0) {
                int sy = 6 + (x * 13) % 12;
                if (sy < HEIGHT) {
                    px[x][sy] = tip;
                }
            }
            if ((x * 19 + 3) % 27 == 0) {
                int sy = HEIGHT - 8 - (x * 7) % 12;
                if (sy >= 0) {
                    px[x][sy] = hot;
                }
            }
        }

        // Only top/bottom rails — no vertical end caps (those looked like weird pill tips)
        for (int x = 0; x < WIDTH; x++) {
            px[x][0] = gold;
            px[x][1] = line;
            px[x][HEIGHT - 1] = gold;
            px[x][HEIGHT - 2] = line;
        }

        if (kind == BuildingBannerKind.AUCTION_BAZAAR) {
            drawBigTitle(px, new String[]{"AH & BZ"}, ink, gold, bright, mid);
        } else {
            drawBigTitle(px, new String[]{kind.title().toUpperCase()}, ink, gold, bright, mid);
        }

        return px;
    }

    private static byte flameColor(double t, byte deep, byte mid, byte bright, byte hot, byte tip) {
        if (t < 0.25) {
            return tip;
        }
        if (t < 0.45) {
            return hot;
        }
        if (t < 0.7) {
            return bright;
        }
        if (t < 0.88) {
            return mid;
        }
        return deep;
    }

    private static void drawBigTitle(byte[][] px, String[] lines, byte ink, byte gold, byte bright, byte mid) {
        int maxW = WIDTH - 32;
        int lineCount = lines.length;
        int gap = lineCount > 1 ? 6 : 0;
        int maxH = lineCount > 1 ? 42 : 92;
        int scale = Integer.MAX_VALUE;
        for (String line : lines) {
            scale = Math.min(scale, fitScale(line, maxW, maxH));
        }
        int textH = 7 * scale;
        int blockH = lineCount * textH + (lineCount - 1) * gap;
        int y = Math.max(14, (HEIGHT - blockH) / 2);
        for (String line : lines) {
            drawCentered(px, line, y, scale, ink, gold);
            y += textH + gap;
        }
        String widest = lines[0];
        for (String line : lines) {
            if (measure(line, scale) > measure(widest, scale)) {
                widest = line;
            }
        }
        int uw = measure(widest, scale);
        int ux = Math.max(4, (WIDTH - uw) / 2);
        int uy = Math.min(HEIGHT - 5, y - gap + 1);
        for (int x = ux; x < ux + uw && x < WIDTH; x++) {
            px[x][uy] = bright;
            if (uy + 1 < HEIGHT) {
                px[x][uy + 1] = mid;
            }
        }
    }

    private static int fitScale(String text, int maxW, int maxH) {
        int scale = Math.max(1, maxH / 7);
        while (scale > 1 && measure(text, scale) > maxW) {
            scale--;
        }
        return Math.max(4, scale);
    }

    private static void drawCentered(byte[][] px, String text, int y, int scale, byte ink, byte glow) {
        int w = measure(text, scale);
        int x = Math.max(4, (WIDTH - w) / 2);
        int g = Math.max(2, scale / 3);
        stamp(px, x - g, y, text, scale, glow);
        stamp(px, x + g, y, text, scale, glow);
        stamp(px, x, y - g, text, scale, glow);
        stamp(px, x, y + g, text, scale, glow);
        stamp(px, x, y, text, scale, ink);
    }

    private static int measure(String text, int scale) {
        int w = 0;
        int gap = Math.max(2, scale);
        for (int i = 0; i < text.length(); i++) {
            if (i > 0) {
                w += gap;
            }
            w += charWidth(text.charAt(i), scale);
        }
        return w;
    }

    private static void stamp(byte[][] px, int x, int y, String text, int scale, byte color) {
        int cursor = x;
        int gap = Math.max(2, scale);
        for (int i = 0; i < text.length(); i++) {
            cursor += blitChar(px, cursor, y, text.charAt(i), scale, color) + gap;
        }
    }

    private static int charWidth(char ch, int scale) {
        if (ch == ' ') {
            return 3 * scale;
        }
        byte[][] g = glyph(ch);
        if (g == null) {
            return 4 * scale;
        }
        return g.length * scale;
    }

    private static int blitChar(byte[][] px, int ox, int oy, char ch, int scale, byte color) {
        if (ch == ' ') {
            return 3 * scale;
        }
        byte[][] g = glyph(ch);
        if (g == null) {
            return 4 * scale;
        }
        if (px != null) {
            for (int gx = 0; gx < g.length; gx++) {
                for (int gy = 0; gy < g[gx].length; gy++) {
                    if (g[gx][gy] == 0) {
                        continue;
                    }
                    for (int sx = 0; sx < scale; sx++) {
                        for (int sy = 0; sy < scale; sy++) {
                            int x = ox + gx * scale + sx;
                            int y = oy + gy * scale + sy;
                            if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
                                px[x][y] = color;
                            }
                        }
                    }
                }
            }
        }
        return g.length * scale;
    }

    private static byte[][] glyph(char ch) {
        return switch (Character.toUpperCase(ch)) {
            case 'A' -> g("01110", "10001", "10001", "11111", "10001", "10001", "10001");
            case 'B' -> g("11110", "10001", "10001", "11110", "10001", "10001", "11110");
            case 'C' -> g("01111", "10000", "10000", "10000", "10000", "10000", "01111");
            case 'D' -> g("11110", "10001", "10001", "10001", "10001", "10001", "11110");
            case 'E' -> g("11111", "10000", "10000", "11110", "10000", "10000", "11111");
            case 'F' -> g("11111", "10000", "10000", "11110", "10000", "10000", "10000");
            case 'G' -> g("01111", "10000", "10000", "10111", "10001", "10001", "01110");
            case 'H' -> g("10001", "10001", "10001", "11111", "10001", "10001", "10001");
            case 'I' -> g("111", "010", "010", "010", "010", "010", "111");
            case 'J' -> g("00111", "00010", "00010", "00010", "00010", "10010", "01100");
            case 'K' -> g("10001", "10010", "10100", "11000", "10100", "10010", "10001");
            case 'L' -> g("10000", "10000", "10000", "10000", "10000", "10000", "11111");
            case 'M' -> g("10001", "11011", "10101", "10101", "10001", "10001", "10001");
            case 'N' -> g("10001", "11001", "10101", "10011", "10001", "10001", "10001");
            case 'O' -> g("01110", "10001", "10001", "10001", "10001", "10001", "01110");
            case 'P' -> g("11110", "10001", "10001", "11110", "10000", "10000", "10000");
            case 'Q' -> g("01110", "10001", "10001", "10001", "10101", "10010", "01101");
            case 'R' -> g("11110", "10001", "10001", "11110", "10100", "10010", "10001");
            case 'S' -> g("01111", "10000", "10000", "01110", "00001", "00001", "11110");
            case 'T' -> g("11111", "00100", "00100", "00100", "00100", "00100", "00100");
            case 'U' -> g("10001", "10001", "10001", "10001", "10001", "10001", "01110");
            case 'V' -> g("10001", "10001", "10001", "10001", "10001", "01010", "00100");
            case 'W' -> g("10001", "10001", "10001", "10101", "10101", "11011", "10001");
            case 'X' -> g("10001", "10001", "01010", "00100", "01010", "10001", "10001");
            case 'Y' -> g("10001", "10001", "01010", "00100", "00100", "00100", "00100");
            case 'Z' -> g("11111", "00001", "00010", "00100", "01000", "10000", "11111");
            case '&' -> g("01100", "10010", "10100", "01000", "10101", "10010", "01101");
            case '·', '.' -> g("0", "0", "0", "0", "0", "1", "0");
            case '✦', '*' -> g("00100", "00100", "11111", "00100", "00100", "01010", "10001");
            case '-' -> g("00000", "00000", "00000", "11111", "00000", "00000", "00000");
            case '/' -> g("00001", "00010", "00100", "01000", "10000", "00000", "00000");
            default -> null;
        };
    }

    private static byte[][] g(String... rows) {
        int h = rows.length;
        int w = rows[0].length();
        byte[][] out = new byte[w][h];
        for (int y = 0; y < h; y++) {
            String row = rows[y];
            for (int x = 0; x < w; x++) {
                out[x][y] = (byte) (row.charAt(x) == '1' ? 1 : 0);
            }
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    private static byte c(int r, int g, int b) {
        return MapPalette.matchColor(new Color(r, g, b));
    }
}
