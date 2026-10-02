import os, sys
from PIL import Image, ImageDraw, ImageFont
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import pieces
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(HERE)
names = list(pieces.PIECES)
cw, ch, cols = 560, 470, 3
rows = (len(names) + cols - 1) // cols
sheet = Image.new("RGB", (cw * cols, ch * rows + 60), (236, 238, 232))
d = ImageDraw.Draw(sheet)
try:
    f1 = ImageFont.truetype("arialbd.ttf", 26)
except Exception:
    f1 = ImageFont.load_default()
d.text((20, 16), "Aetherion prop pack - 18 schematics (front = south, previews from the south-east)", fill=(40, 44, 40), font=f1)
for i, n in enumerate(names):
    im = Image.open(f"{OUT}/previews/front/{n}.png")
    im.thumbnail((cw - 16, ch - 16), Image.LANCZOS)
    x = (i % cols) * cw + (cw - im.width) // 2
    y = 60 + (i // cols) * ch + (ch - im.height) // 2
    sheet.paste(im, (x, y))
sheet.save(f"{OUT}/previews/_overview.png")
print(sheet.size)
