"""Screenshots from the real game: drives the headless test client on the staging box (see tools/stage).

  python realshots.py <dump dir> <out.png> shot ...          (same shot syntax as shots.py)

Each shot teleports the staging camera, presses F2 in the client, and the pictures come back as one contact
sheet (and as single files next to it, in <out>.d/). Needs two environment variables (the staging box is private,
see the ops notes): STAGE_HOST = ssh name of the box, STAGE_CLIENT_DIR = folder of the headless client there, with
its `cam.sh start` running.
"""
import os
import subprocess
import sys

from PIL import Image, ImageDraw

import shots

HOST = os.environ.get("STAGE_HOST", "")
REMOTE = os.environ.get("STAGE_CLIENT_DIR", "")


def run(cmd):
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout


def main():
    if not HOST or not REMOTE:
        sys.exit("set STAGE_HOST and STAGE_CLIENT_DIR first (see the docstring)")
    dump, out = sys.argv[1], sys.argv[2]
    wait = "7"
    args = []
    for a in sys.argv[3:]:
        if a.startswith("--wait="):
            wait = a.split("=", 1)[1]
        else:
            args.append(a)
    ground = shots.Ground(dump)
    lines = []
    names = []
    for a in args:
        name, x, y, z, yaw, pitch, fov = shots.parse(a, ground)
        names.append(name)
        lines.append("%s %.2f %.2f %.2f %.1f %.1f %s" % (name, x, y - 1.62, z, yaw, pitch, wait))
    folder = out + ".d"
    os.makedirs(folder, exist_ok=True)
    listing = os.path.join(folder, "batch.txt")
    with open(listing, "w", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    run(["scp", "-q", listing, "%s:%s/batch.txt" % (HOST, REMOTE)])
    print(run(["ssh", HOST, "cd %s && rm -f shots/*.png && ./cam.sh batch batch.txt" % REMOTE]).strip())
    run(["scp", "-q", "%s:%s/shots/*.png" % (HOST, REMOTE), folder + "/"])
    cols, w, h = 2, 800, 450
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * w, rows * h), (20, 20, 20))
    for i, name in enumerate(names):
        path = os.path.join(folder, name + ".png")
        if not os.path.isfile(path):
            continue
        tile = Image.open(path).convert("RGB").resize((w, h), Image.LANCZOS)
        sheet.paste(tile, ((i % cols) * w, (i // cols) * h))
        d = ImageDraw.Draw(sheet)
        d.text(((i % cols) * w + 8, (i // cols) * h + 6), name, fill=(255, 255, 255), stroke_width=2,
               stroke_fill=(0, 0, 0))
    sheet.save(out)


if __name__ == "__main__":
    main()
