import os, sys
from PIL import Image, ImageDraw, ImageFont
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from build_more import NEW
OUT = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("AE_OUT", os.path.dirname(HERE))
cw, ch, cols = 560, 470, 4
rows = (len(NEW) + cols - 1) // cols
sheet = Image.new("RGB", (cw * cols, ch * rows + 60), (236, 238, 232))
d = ImageDraw.Draw(sheet)
f1 = ImageFont.load_default()
d.text((20, 16), f"Aetherion prop pack - second pass, {len(NEW)} new schematics (front = south, seen from the SE)",
       fill=(40, 44, 40), font=f1)
for i, n in enumerate(NEW):
    im = Image.open(f"{OUT}/previews/front/{n}.png")
    im.thumbnail((cw - 16, ch - 16), Image.LANCZOS)
    x = (i % cols) * cw + (cw - im.width) // 2
    y = 60 + (i // cols) * ch + (ch - im.height) // 2
    sheet.paste(im, (x, y))
sheet.save(f"{OUT}/previews/_overview_more.png")
print(sheet.size)
