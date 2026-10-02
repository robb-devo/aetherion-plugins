"""Build only the second-pass props (_more_props_ship) -> $AE_OUT/schematics + $AE_OUT/previews.

    python build_more.py                 # all new pieces
    python build_more.py ae_edge_windmill   # one piece
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import pieces  # noqa: E402
import build_all  # noqa: E402

FIRST_PASS = {
    "ae_prop_lantern_post", "ae_prop_notice_board", "ae_prop_cargo_stack", "ae_prop_fish_rack", "ae_prop_ore_heap",
    "ae_prop_wayside_shrine", "ae_prop_well", "ae_prop_forest_campsite", "ae_mine_mouth", "ae_mine_headframe",
    "ae_market_stall", "ae_dock_crane", "ae_harbour_watchtower", "ae_boathouse", "ae_stone_bridge",
    "ae_cliff_overlook", "ae_forest_ruin_gate", "ae_ranger_hut"}
NEW = [n for n in pieces.PIECES if n not in FIRST_PASS]

if __name__ == "__main__":
    want = [a for a in sys.argv[1:] if not a.startswith("--")]
    bad = [w for w in want if w not in NEW]
    if bad:
        sys.exit(f"not a second-pass piece: {bad}")
    build_all.run(want or NEW)
