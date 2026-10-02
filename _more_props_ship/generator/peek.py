"""Quick look: render one piece big (front + back) without writing schems. usage: peek.py name [u] [rot]"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pieces
from build_all import with_context
from aeprops.render import Renderer
from aeprops.style import check_support
from PIL import Image
name = sys.argv[1]; u = int(sys.argv[2]) if len(sys.argv) > 2 else 24
rots = [int(r) for r in sys.argv[3].split(",")] if len(sys.argv) > 3 else [0, 2]
fn, info = pieces.PIECES[name]
b = fn(); b.resolve()
for w in check_support(b)[:30]: print("WARN", w)
ctx = b.meta.get("context", "grass")
blocks = with_context(b, ctx)
r = Renderer(u)
imgs = [r.render(blocks, rot=k, title=f"{name} rot{k}") for k in rots]
W = sum(i.width for i in imgs); H = max(i.height for i in imgs)
out = Image.new("RGB", (W, H), (236, 238, 232)); x = 0
for i in imgs: out.paste(i, (x, 0)); x += i.width
os.makedirs("/home/claude/work/peek", exist_ok=True)
p = f"/home/claude/work/peek/{name}.png"; out.save(p); print(p, b.size(), "anchor", b.anchor, "blocks", len(b.blocks))
