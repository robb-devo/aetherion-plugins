"""Composite a Manager chrome glyph the way the client draws it (GUI scale 2) with items in its slots.

The glyph's GUI origin sits at bitmap (40, 10): container slots at (8+18c, 18+18r), player inventory
at 103+18r+(rows-4)*18, hotbar at 161+(rows-4)*18 (vanilla ChestMenu).
"""
from PIL import Image, ImageDraw

OX, OY = 40, 10


def compose(chrome, rows, items, scale=2, title_bg=(26, 24, 30)):
    """items: {(section, index): PIL 32x32 icon}; section 'c' container, 'i' inventory, 'h' hotbar."""
    w, h = 176, 114 + 18 * rows
    pad = 14
    W, H = (w + 2 * pad) * scale, (h + 2 * pad + 8) * scale
    out = Image.new('RGBA', (W, H), title_bg + (255,))
    # the world behind the GUI: dim checker so transparent bleed shows
    d = ImageDraw.Draw(out)
    for y in range(0, H, 16):
        for x in range(0, W, 16):
            if (x // 16 + y // 16) % 2:
                d.rectangle((x, y, x + 15, y + 15), fill=(34, 32, 40))
    gx, gy = pad, pad + 8   # GUI origin in preview units
    big = chrome.resize((chrome.width * scale, chrome.height * scale), Image.NEAREST)
    out.alpha_composite(big, ((gx - OX) * scale, (gy - OY) * scale))
    off = (rows - 4) * 18
    for (sec, idx), icon in items.items():
        c = idx % 9
        r = idx // 9
        if sec == 'c':
            x, y = 8 + 18 * c, 18 + 18 * r
        elif sec == 'i':
            x, y = 8 + 18 * c, 103 + 18 * r + off
        else:
            x, y = 8 + 18 * c, 161 + off
        ic = icon.resize((16 * scale, 16 * scale), Image.NEAREST)
        out.alpha_composite(ic, ((gx + x) * scale, (gy + y) * scale))
    return out
