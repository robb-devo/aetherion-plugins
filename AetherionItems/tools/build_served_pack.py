#!/usr/bin/env python3
"""Build the served resource pack: the live zip plus the HUD and menu art from AetherionItems/resourcepack.

    python build_served_pack.py <live.zip> <out.zip> <vanilla 1.21.1 client jar>

AetherionItems/resourcepack is only the source of the HUD and GUI art; its item models and textures are
stale, so they are never copied. This takes the zip that is live now, swaps in exactly the paths in COPY,
drops the ones in DROP, merges the menu icon overrides into the item models (merge_gui_overrides.py), makes
it safe for clients newer than 1.21.1 (newer_clients.py) and writes a deterministic zip. Then: upload it over
the served zip, ``aepack reload`` + ``aepack push`` on the proxy. The backends advertise no pack; the proxy sends it once per session.
"""
import hashlib
import os
import shutil
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_PACK = os.path.normpath(os.path.join(HERE, '..', 'resourcepack'))
sys.path.insert(0, HERE)
import merge_gui_overrides  # noqa: E402
import newer_clients  # noqa: E402

# Owned by the generators in this folder (gen_carrier.py, gen_gui_icons.py, gen_gui_chrome.py).
COPY = (
    'assets/aetherion/font/carrier.json',
    'assets/aetherion/textures/font/carrier',
    'assets/minecraft/shaders/core/rendertype_text.vsh',
    'assets/minecraft/shaders/core/rendertype_text.json',
    'assets/minecraft/shaders/core/position_tex.vsh',
    'assets/minecraft/shaders/core/position_tex.fsh',
    'assets/minecraft/textures/gui/sprites/boss_bar/blue_background.png',
    'assets/aetherion/textures/item/gui',
    'assets/aetherion/models/item/gui',
    # The Manager's book pages and their caption fonts (gen_gui_chrome.py: render + write_caption_fonts).
    'assets/minecraft/textures/font/gui_aetherion_manager.png',
    'assets/minecraft/textures/font/gui_aetherion_stats.png',
    'assets/minecraft/textures/font/gui_aetherion_spawns.png',
    'assets/aetherion/font/mgr_caption_1.json',
    'assets/aetherion/font/mgr_caption_2.json',
    'assets/aetherion/font/mgr_caption_3.json',
    # Display-effect glyphs (gen_vfx_art.py), drawn by DisplayVfx.
    'assets/aetherion/font/vfx.json',
    'assets/aetherion/textures/font/vfx',
)
# The ActionBar status panel the carrier replaced.
DROP = (
    'assets/aetherion/font/panel.json',
    'assets/aetherion/textures/font/panel',
)
STAMP = (2026, 1, 1, 0, 0, 0)


def main(live, out, vanilla):
    work = tempfile.mkdtemp(prefix='aepack-')
    try:
        with zipfile.ZipFile(live) as z:
            z.extractall(work)
        for rel in DROP:
            path = os.path.join(work, rel)
            if os.path.isdir(path):
                shutil.rmtree(path)
            elif os.path.exists(path):
                os.remove(path)
        for rel in COPY:
            src = os.path.join(REPO_PACK, rel)
            dst = os.path.join(work, rel)
            if os.path.isdir(src):
                shutil.rmtree(dst, ignore_errors=True)
                shutil.copytree(src, dst)
            else:
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copyfile(src, dst)
        merge_gui_overrides.main(work, vanilla)
        newer_clients.main(work)
        files = []
        for root, _, names in os.walk(work):
            for name in names:
                full = os.path.join(root, name)
                files.append(os.path.relpath(full, work).replace(os.sep, '/'))
        with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for rel in sorted(files):
                info = zipfile.ZipInfo(rel, STAMP)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                with open(os.path.join(work, rel), 'rb') as f:
                    z.writestr(info, f.read())
    finally:
        shutil.rmtree(work, ignore_errors=True)
    with open(out, 'rb') as f:
        sha1 = hashlib.sha1(f.read()).hexdigest()
    print('%s  %d files  sha1 %s' % (out, len(files), sha1))


if __name__ == '__main__':
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3])
