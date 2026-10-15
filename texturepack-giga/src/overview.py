"""Overview grid of every GIGA icon: 3x art on a slot-grey tile with the name under it.

    python overview.py out_prefix [per_sheet]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

import registry  # noqa: E402


def main():
    prefix = sys.argv[1]
    per = int(sys.argv[2]) if len(sys.argv) > 2 else 64
    reg = registry.all_icons()
    names = [n for n in reg if not n.startswith('basetest_')]
    font = ImageFont.load_default()
    cols, cell, lab = 8, 96, 12
    for s in range(0, len(names), per):
        chunk = names[s:s + per]
        rows = (len(chunk) + cols - 1) // cols
        im = Image.new('RGBA', (cols * (cell + 8), rows * (cell + lab + 8)), (24, 22, 30, 255))
        d = ImageDraw.Draw(im)
        for i, n in enumerate(chunk):
            x, y = (i % cols) * (cell + 8) + 4, (i // cols) * (cell + lab + 8) + 4
            d.rectangle((x, y, x + cell - 1, y + cell - 1), fill=(139, 139, 139))
            art = reg[n]().render().resize((cell, cell), Image.NEAREST)
            im.alpha_composite(art, (x, y))
            d.text((x, y + cell + 1), n[:17], fill=(225, 218, 240), font=font)
        im.save(f'{prefix}_{s // per + 1}.png')
        print(f'{prefix}_{s // per + 1}.png', len(chunk))


if __name__ == '__main__':
    main()
