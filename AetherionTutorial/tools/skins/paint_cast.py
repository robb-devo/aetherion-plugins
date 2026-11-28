#!/usr/bin/env python3
"""
The faces of Morningbell.

Fifteen original skins (seven teachers, eight islanders), painted from specs with the repo's NPC skin painter (docs/npc/skins/paint_skins.py: simple
layers, no third-party textures). The PNGs go into the plugin jar (src/main/resources/skins/<id>.png); the plugin
has them signed once through FancyNpcs and keeps the signatures (isle/Skins.java).

Run from anywhere:  python paint_cast.py [--preview cast.png]
"""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
OUT = os.path.join(REPO, "AetherionTutorial", "src", "main", "resources", "skins")

spec = importlib.util.spec_from_file_location("paint_skins", os.path.join(REPO, "docs", "npc", "skins", "paint_skins.py"))
ps = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ps)

# Who they are decides what they wear. Colours repeat the island: belfry red, bell gold, sea blue, quarry grey.
CAST = {
    # Tolly, the ferry hand: a boy of the harbour, striped jersey, red cap, always grinning.
    "tolly": dict(skin="light", eyes="green", hair="d0a050", hair_style="messy", freckles=True, mouth="grin",
                  hat=dict(kind="beanie", color="b8322a", rows=2),
                  torso=dict(shirt="e8e4d8", pattern="stripes", pattern_color="2e5a8a", stripe_sleeves=True,
                             sleeve="e8e4d8", sleeve_len=7, scarf="b8322a", belt="5a3a22", belt_row=10),
                  legs=dict(pants="3a4a66", boots="4a3020", boot_h=3, patch="56688a")),
    # Oda, the bellkeeper: old, kind, the red of her belfry, a small gold bell on a chain.
    "oda": dict(slim=True, skin="pale", eyes="grey", hair="dcdad2", hair_style="bun", fringe=1, rosy=True,
                wrinkles=True, face_acc="glasses", glasses_color="8a6a2a", mouth="smirk",
                torso=dict(shirt="f0ece2", over="coat", over_color="8a2a26", lapel=True, collar="ffffff",
                           coat_cuff="e8c860", necklace="e8c860", pendant="f2d040", belt="5a1c1a", belt_row=8,
                           belt_overlay=True),
                legs=dict(pants="3a2a2a", boots="2a1c18", boot_h=2, dress="7a2420", dress_len=8)),
    # Burr, the charcoal burner: broad, sooty, leather apron, a beard the kiln has singed.
    "burr": dict(skin="tan", eyes="dark", hair="2a221c", hair_style="short", beard="full", beard_color="2e2620",
                 soot=True, mouth="flat",
                 hat=dict(kind="flatcap", color="3a342e", rows=2),
                 torso=dict(shirt="7a5a3a", sleeve="7a5a3a", sleeve_len=5, over="apron", over_color="3a2c24",
                            apron_top=3, apron_pocket=True, gloves="2a221c", stains="161412",
                            stain_px=((2, 5), (5, 8), (6, 4), (1, 9)), belt="1e1812", belt_row=7, belt_overlay=True),
                 legs=dict(pants="3c3832", boots="241a14", boot_h=4, patch="4a443c")),
    # Grett, the quarry master: wiry, a yellow kerchief against the dust, rust vest, stone grey everywhere else.
    "grett": dict(slim=True, skin="warm", eyes="brown", hair="8a4a24", hair_style="braid", freckles=True,
                  mouth="smirk", soot=True,
                  hat=dict(kind="bandana", color="d8a82a", rows=2, dot="f2e6b0"),
                  torso=dict(shirt="7a7a7c", sleeve="7a7a7c", sleeve_len=4, over="vest", over_color="a8562a",
                             gloves="6a4a30", belt="2a2016", belt_row=9, stains="4a4642", bracers="5a4030"),
                  legs=dict(pants="5a5a62", boots="3a2a1c", boot_h=4, patch="6c6c74")),
    # Ansel, the smith: bare forearms, a red shirt under the scorched apron, quick to laugh.
    "ansel": dict(skin="brown", eyes="amber", hair="1a1410", hair_style="bald", beard="goatee",
                  beard_color="1a1410", soot=True, mouth="grin",
                  torso=dict(shirt="a8322a", sleeve="a8322a", sleeve_len=3, over="apron", over_color="4a3020",
                             apron_top=2, apron_pocket=True, bracers="6a4a30", stains="1e1a16",
                             belt="241812", belt_row=7, belt_overlay=True),
                  legs=dict(pants="38322e", boots="1e1612", boot_h=4)),
    # Hilde, sergeant of the Old Watch: straight back, green coat with brass, a scar she does not talk about.
    "hilde": dict(slim=True, skin="light", eyes="blue", hair="c8b48a", hair_style="braid", scar=True,
                  eyes_style="narrow", mouth="flat",
                  torso=dict(shirt="e8e4d8", over="coat", over_color="3f6a3a", buttons="e8c040", belt="2a2016",
                             belt_row=8, belt_overlay=True, epaulettes="e8c040", coat_cuff="e8c040",
                             gloves="2a2016", collar="f2f2f2"),
                  legs=dict(pants="2c302a", boots="161412", boot_h=5, stripe="3f6a3a")),
    # Captain Wim: forty years on the ferry, navy coat, white beard, a pipe that is never lit on deck.
    "wim": dict(skin="warm", eyes="blue", hair="e4e2da", hair_style="short", beard="full", beard_color="ecebe4",
                rosy=True, wrinkles=True, pipe=True, mouth="grin",
                hat=dict(kind="officer", color="1f3a5a", band="14263c", rows=2, badge="f0c040"),
                torso=dict(shirt="f0ece2", over="coat", over_color="243e66", lapel=True, buttons="f0c040",
                           coat_cuff="f0c040", collar="ffffff", scarf="b8322a"),
                legs=dict(pants="e0dccc", boots="15171c", boot_h=4)),
    # ---- the islanders (no lessons, a line or two each) ----
    # Marla, the baker: flour to the elbows.
    "marla": dict(slim=True, skin="light", eyes="brown", hair="7a4a24", hair_style="bun", rosy=True, mouth="grin",
                  hat=dict(kind="bandana", color="f2efe6", rows=2, dot="f2efe6"),
                  torso=dict(shirt="d8a070", sleeve="d8a070", sleeve_len=5, over="apron", over_color="f0ece2",
                             apron_top=3, apron_pocket=True, stains="ffffff", stain_px=((2, 6), (5, 8), (4, 5))),
                  legs=dict(pants="6a4a34", boots="3a2a1e", boot_h=2, dress="8a5a3a", dress_len=7)),
    # Osk, the innkeeper: wine-red waistcoat, a moustache he is proud of.
    "osk": dict(skin="warm", eyes="dark", hair="5a3a22", hair_style="short", beard="moustache", beard_color="5a3a22",
                rosy=True, mouth="grin",
                torso=dict(shirt="ece4d0", sleeve="ece4d0", sleeve_len=9, over="vest", over_color="6a2428",
                           buttons="e8c040", belt="2a1a12", belt_row=9),
                legs=dict(pants="3a342e", boots="1e1612", boot_h=3)),
    # Pim, the market woman: straw hat, green bodice, sharp eyes.
    "pim": dict(slim=True, skin="tan", eyes="green", hair="2a1e16", hair_style="braid", mouth="smirk",
                hat=dict(kind="wide", color="e0c070", band="3f7a45", rows=2),
                torso=dict(shirt="f0e8d0", sleeve="f0e8d0", sleeve_len=6, over="vest", over_color="3f7a45",
                           sash="c8a030"),
                legs=dict(pants="4a3a2a", boots="2a1e16", boot_h=2, dress="7a5a30", dress_len=8)),
    # Nell, the fishwife: blue kerchief, oilcloth apron, hands like rope.
    "nell": dict(slim=True, skin="light", eyes="blue", hair="c86a2a", hair_style="messy", freckles=True,
                 mouth="grin",
                 hat=dict(kind="bandana", color="2e5a8a", rows=2, dot="f2f2f2"),
                 torso=dict(shirt="9aa8b0", sleeve="9aa8b0", sleeve_len=4, over="apron", over_color="5a6a72",
                            apron_top=3, stains="8ab0b8", gloves="e0e0e8"),
                 legs=dict(pants="3a4650", boots="1e2a32", boot_h=5)),
    # Hob, the farmer of Downs Farm: straw in his teeth, blue overalls.
    "hob": dict(skin="tan", eyes="blue", hair="c8a060", hair_style="short", beard="stubble", beard_color="a88040",
                rosy=True, straw=True,
                hat=dict(kind="wide", color="d8b860", band="6a4a2a", rows=2),
                torso=dict(shirt="b8483a", pattern="check", pattern_color="e8d8b0", sleeve="b8483a", sleeve_len=6,
                           over="overalls", over_color="46628a", gloves="a8844e"),
                legs=dict(pants="46628a", boots="5a3a22", boot_h=3, patch="5e7aa2")),
    # Wren, the shepherd: a wool vest she knitted herself, hair the wind has given up on.
    "wren": dict(slim=True, skin="light", eyes="green", hair="e0c890", hair_style="long", freckles=True,
                 mouth="smirk",
                 hat=dict(kind="flatcap", color="5a7a4a", rows=2),
                 torso=dict(shirt="e8e0c8", sleeve="e8e0c8", sleeve_len=7, over="vest", over_color="d8d0bc",
                            scarf="5a7a4a", belt="5a3a22", belt_row=9),
                 legs=dict(pants="7a6a4a", boots="4a3020", boot_h=4, boot_cuff="d8d0bc")),
    # Tam, the miller: everything on him is a shade paler than it was.
    "tam": dict(skin="pale", eyes="grey", hair="c4c0b8", hair_style="crop", beard="full", beard_color="d0ccc4",
                mouth="flat",
                hat=dict(kind="beanie", color="e4e0d6", rows=2),
                torso=dict(shirt="e6e2d8", sleeve="e6e2d8", sleeve_len=6, over="apron", over_color="d2ccbe",
                           apron_top=3, stains="ffffff", stain_px=((1, 5), (3, 8), (6, 6), (5, 4)), belt="8a7a5a",
                           belt_row=7, belt_overlay=True),
                legs=dict(pants="9a9484", boots="5a4a3a", boot_h=3)),
    # Brack, the lighthouse keeper: forty years of salt wind in one face.
    "brack": dict(skin="warm", eyes="grey", hair="dcdad2", hair_style="long", beard="full", beard_color="e6e4dc",
                  wrinkles=True, frown=True, mouth="flat",
                  hat=dict(kind="beanie", color="243040", rows=2),
                  torso=dict(shirt="3a4654", over="coat", over_color="2a3440", buttons="c8a030", scarf="c8a030",
                             coat_cuff="2a3440"),
                  legs=dict(pants="2a2e34", boots="14161a", boot_h=4)),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    tiles = []
    for npc_id, raw in CAST.items():
        look = {k: v for k, v in raw.items() if v is not None}
        img = ps.paint_npc("morningbell_" + npc_id, look)
        img.save(os.path.join(OUT, npc_id + ".png"))
        tiles.append((npc_id, ps.front_preview(img, look.get("slim", False), scale=8)))
    if "--preview" in sys.argv:
        from PIL import Image, ImageDraw
        path = sys.argv[sys.argv.index("--preview") + 1]
        tw = max(t.width for _, t in tiles)
        th = max(t.height for _, t in tiles) + 16
        sheet = Image.new("RGBA", (len(tiles) * tw, th), (30, 32, 36, 255))
        d = ImageDraw.Draw(sheet)
        for i, (name, t) in enumerate(tiles):
            sheet.alpha_composite(t, (i * tw, 0))
            d.text((i * tw + 6, t.height + 2), name, fill=(230, 230, 230, 255))
        sheet.save(path)
    print("painted", len(tiles), "skins ->", OUT)


if __name__ == "__main__":
    main()
