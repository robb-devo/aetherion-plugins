import json, pickle, sys, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import render as R
from world import World, X0, Z0, NX, NZ

W = World()
W.a = np.load('out/world.npy')
W.pal = json.load(open('out/palette.json'))
W.idx = {s: i for i, s in enumerate(W.pal)}
meta = pickle.load(open('out/meta.pkl', 'rb'))
A = meta['anchors']

FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FR = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'


def labeled_map(path, scale=3):
    I, _ = R.topdown(W, scale)
    I = I.convert('RGB')
    pad_top, pad_r = 70, 300
    canvas = Image.new('RGB', (I.width + pad_r, I.height + pad_top), (14, 16, 22))
    canvas.paste(I, (0, pad_top))
    d = ImageDraw.Draw(canvas)
    fT = ImageFont.truetype(FB, 30)
    fD = ImageFont.truetype(FB, 19)
    fS = ImageFont.truetype(FR, 14)
    fL = ImageFont.truetype(FR, 15)

    def P(x, z):
        return ((x - X0 + 0.5) * scale, (z - Z0 + 0.5) * scale + pad_top)

    d.text((16, 18), 'ANKERHOLD — Aetherion Main Hub (proposal)', font=fT, fill=(235, 225, 200))
    # grid
    for v in range(-150, 200, 50):
        x, _ = P(v, Z0)
        d.line([(x, pad_top), (x, pad_top + I.height)], fill=(255, 255, 255, 40), width=1)
        d.text((x + 3, pad_top + 3), str(v), font=fS, fill=(200, 200, 200))
        _, y = P(X0, v)
        d.line([(0, y), (I.width, y)], fill=(255, 255, 255), width=1)
        d.text((3, y + 2), str(v), font=fS, fill=(200, 200, 200))
    districts = [('ANKER HARBOUR', 102, 52), ('THE CAPITAL', -22, 46), ('WHISPERWOOD', 88, -128),
                 ('ORE RIDGE', 62, 140), ('CLUCKSWORTH FIELDS', -104, 152), ('MOUNT SKYREACH', -62, -158),
                 ('THE BORDERLANDS', -128, -86), ('COLOSSEUM', -150, -168)]
    for name, x, z in districts:
        px, py = P(x, z)
        w = d.textlength(name, font=fD)
        d.text((px - w / 2 + 2, py + 2), name, font=fD, fill=(0, 0, 0))
        d.text((px - w / 2, py), name, font=fD, fill=(255, 238, 190))
    kinds = [('npc.', (255, 150, 40), 'quest / board NPC dock'), ('spawn.', (60, 230, 255), 'spawn camp'),
             ('pad.', (110, 255, 110), 'slime pad / landing'), ('portal.', (200, 110, 255), 'portal (farm isle / dungeon)'),
             ('bell.', (255, 220, 60), 'bell (Origin-light)'), ('vista.', (255, 255, 255), 'vista'),
             ('waystone.', (90, 170, 255), 'glowcap waystone'), ('updraft.', (160, 255, 230), 'updraft / glide'),
             ('glide.', (160, 255, 230), None)]
    for pre, col, _ in kinds:
        for k, v in A.items():
            if k.startswith(pre):
                px, py = P(v['x'], v['z'])
                r = 5 if pre != 'npc.' else 4
                d.ellipse([px - r, py - r, px + r, py + r], fill=col, outline=(0, 0, 0))
    # key labels
    lab = {'npc.egon': 'Egon', 'npc.ledger': 'Ledger', 'npc.vex': 'Vex', 'npc.lumberjack': 'Lumberjack',
           'npc.quartermaster': 'QM forge', 'npc.foreman': 'Foreman', 'npc.booster_tutor': 'Temper', 'npc.farmer': 'Farmer',
           'npc.lark': 'Lark', 'npc.fisher': 'Tackle', 'npc.forage_pad_guide': 'Twig', 'npc.farm_isle_guide': 'Harrow',
           'npc.surveyor': 'Surveyor', 'npc.dungeon_gate': 'Threshold', 'npc.arena_proctor': 'Proctor',
           'npc.rite_keeper': 'Rite Warden', 'npc.isle_clerk': 'Deed', 'npc.vince': 'Vince', 'spawn.harbour': 'ARRIVAL',
           'landmark.fallen_anchor': 'Fallen Anchor', 'landmark.tallybell': 'Tallybell', 'landmark.lighthouse': 'Lighthouse',
           'pad.origin_to_mining': 'Mining pad ↓', 'pad.origin_to_forage': 'Forage pad →',
           'portal.farm_isle': 'Farm Isle portal ↙', 'landmark.mountain_gate': 'Mountain Gate',
           'vista.skyreach_summit': 'Summit', 'vista.the_keel': 'The Keel', 'landmark.the_scar': 'The Scar',
           'landmark.windmill': 'Windmill', 'landmark.shaft_no1': 'Shaft No.1', 'npc.craftsman': 'Craftsman'}
    OFF = {'npc.craftsman': (-95, -10), 'landmark.the_scar': (6, -22), 'npc.ledger': (-62, -22),
           'landmark.fallen_anchor': (6, 4), 'vista.the_keel': (8, 0), 'landmark.lighthouse': (-30, 14),
           'landmark.windmill': (8, -14)}
    for k, t in lab.items():
        if k not in A:
            continue
        v = A[k]
        px, py = P(v['x'], v['z'])
        ox, oy = OFF.get(k, (8, -8))
        d.text((px + ox + 1, py + oy + 1), t, font=fL, fill=(0, 0, 0))
        d.text((px + ox, py + oy), t, font=fL, fill=(255, 255, 255))
    # tutorial loop
    loop = ['npc.egon', 'npc.lumberjack', 'npc.egon', 'npc.quartermaster', 'region.ore_ridge_coal', 'npc.craftsman',
            'npc.foreman', 'npc.booster_tutor', 'npc.ledger', 'npc.farmer', 'npc.lark', 'npc.ledger']
    pts = [P(A[k]['x'], A[k]['z']) for k in loop if k in A]
    for a, b in zip(pts[:-1], pts[1:]):
        d.line([a, b], fill=(255, 90, 90), width=2)
    # legend
    lx = I.width + 18
    ly = pad_top + 10
    d.text((lx, ly), 'Legend', font=fD, fill=(235, 225, 200))
    ly += 32
    for pre, col, txt in kinds:
        if not txt:
            continue
        d.ellipse([lx, ly + 3, lx + 12, ly + 15], fill=col, outline=(0, 0, 0))
        d.text((lx + 20, ly), txt, font=fL, fill=(230, 230, 230))
        ly += 24
    d.line([(lx, ly + 10), (lx + 14, ly + 10)], fill=(255, 90, 90), width=3)
    d.text((lx + 20, ly + 2), 'Harbour Hour loop', font=fL, fill=(230, 230, 230))
    ly += 40
    info = meta['info']
    for line in [f"Footprint {info['width']} × {info['length']}", f"Height {info['height']} (y {info['min'][1]}..{info['max'][1]})",
                 'Hub centre = world 0 63 0', 'N ↑ (−Z)', '', 'Isle directions:', ' Forage Isle → ENE', ' Mining Eldervale ↓ S',
                 ' Farm Isle ↙ SW', ' Fishing Eldervale ↖ NW']:
        d.text((lx, ly), line, font=fL, fill=(210, 210, 210))
        ly += 21
    canvas.save(path, optimize=True)
    return canvas


def views(path):
    cs = R.palette_colors(W, thin_solid=False)
    sy = int(A['vista.skyreach_summit']['y'])
    V = [('Arrival — east pier, facing west', (166.5, 66.6, 8.5), 90, 1, 100),
         ('Aerial from the south-east', (300, 190, 280), 135, 27, 60),
         ('The Borderlands & the Fallen Anchor', (-92, 80, -12), 95, -8, 95),
         ('Grand Stair → Mountain Gate', (-10.5, 80.6, -30.5), 180, -14, 90),
         ('From Skyreach Summit', (-50.5, sy + 2.6, -112.5), -25, 20, 95),
         ('Under the island', (60, -10, 240), 160, -16, 80)]
    tiles = []
    for title, eye, yaw, pitch, fov in V:
        im = R.raycast(W, eye, yaw, pitch, fov=fov, w=800, h=450, maxd=750, cols_solid=cs)
        dd = ImageDraw.Draw(im)
        f = ImageFont.truetype(FB, 20)
        dd.rectangle([0, 0, 800, 34], fill=(0, 0, 0))
        dd.text((10, 6), title, font=f, fill=(255, 240, 200))
        tiles.append(im)
        print('view', title)
    sheet = Image.new('RGB', (1600, 1350), (10, 10, 14))
    for i, im in enumerate(tiles):
        sheet.paste(im, ((i % 2) * 800, (i // 2) * 450))
    sheet.save(path, quality=88)
    for i, im in enumerate(tiles):
        im.save(f'out/hero_{i}.png')


if __name__ == '__main__':
    which = sys.argv[1:] or ['map', 'views']
    if 'map' in which:
        labeled_map('out/HUB_ANKERHOLD_map.png')
    if 'views' in which:
        views('out/HUB_ANKERHOLD_views.jpg')
