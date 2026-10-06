#!/usr/bin/env python3
"""
P5 boss-HP re-band helper (design docs/PROGRESSION_DESIGN_OPUS.md §3.3 / §3.4 L5).

Reads plugins/BossEngine/fight-log.csv (written by BossEngine's BossFightLog since P0) and suggests a
YAML `attributes.max-health` per boss so the *home* stage hits its TTK target. It never edits YAML:
it prints a report (and optionally a markdown file) for a human to apply.

    python docs/tools/fight_log_report.py path/to/fight-log.csv [--md out.md] [--min-fights 5]

How a suggestion is made (per boss):
  1. Per-player DPS of each fight = party_dps / party_size (fights with 0 s / 0 dmg are skipped).
  2. Use the median over that boss's fights (robust to one whale or one AFK).
  3. Target live HP = median per-player DPS x target players x target TTK (band below).
  4. Undo the open-world raid scale (+35% HP per extra player at spawn, BossManager.applyRaidScale)
     to get the YAML base HP:  base = live / (1 + 0.35 x (players - 1)).  Dungeon bosses: no raid scale.
  5. Entry band: report only, never suggest a change (design: "Entry keep").
Observed TTK already contains uptime (shield windows, phases), so no extra uptime factor is applied.
"""

from __future__ import annotations

import argparse
import csv
import statistics
from collections import defaultdict
from pathlib import Path

RAID_HP_PER_EXTRA = 0.35  # BossInstance.applyRaidScale

# band -> (target TTK seconds low, high, target players)   design §3.3 "Target TTK at home"
BANDS = {
    "entry": (40, 75, 1),
    "mid": (60, 120, 1),
    "high": (120, 180, 2),
    "spectacle": (180, 300, 3),
    "pinnacle": (300, 480, 4),
}

# boss template id -> band   design §3.3 "Home bosses" + §5 table (edit as the roster changes)
BOSS_BAND = {
    "mcnugget": "entry", "hollow_lurker": "entry", "bridge_troll": "entry", "skuldugery": "entry",
    "ashen_sheath": "entry",  # D5: kept at 5.2k for now; telemetry decides
    "dungeon_sentinel": "mid", "squidward": "mid", "aether_colossus": "mid", "pathwarden": "mid",
    "world_eater_unbroken": "mid",
    "world_eater": "high", "helios_herald": "high", "helios_requiem": "high", "ashen_chainwarden": "high",
    "cinder_herald": "high", "dungeon_frostbound": "high",
    "hanging_saint": "spectacle", "hollow_sun": "spectacle", "sparky": "spectacle", "baron_von_wurm": "spectacle",
    "insolvent_wither": "spectacle", "sir_balthazar": "spectacle", "lobby_cleaner": "spectacle",
    "eggquelizer": "spectacle", "dungeon_aetherion": "spectacle",
    "aetherion": "pinnacle",
}

DUNGEON_BOSSES = {"dungeon_sentinel", "dungeon_frostbound", "dungeon_aetherion"}


def num(row: dict, key: str, default: float = 0.0) -> float:
    try:
        return float(row.get(key, "") or default)
    except ValueError:
        return default


def load(path: Path) -> dict[str, list[dict]]:
    fights: dict[str, list[dict]] = defaultdict(list)
    with path.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            if num(row, "fight_seconds") <= 0 or num(row, "total_damage") <= 0:
                continue
            fights[row.get("boss_id", "").lower()].append(row)
    return fights


def report(fights: dict[str, list[dict]], min_fights: int) -> list[str]:
    lines = [
        "| Boss | Band | Fights | Median TTK (s) | Median party | Median DPS / player | Base HP (YAML) | Target TTK | Suggested base HP | Change |",
        "|---|---|---:|---:|---:|---:|---:|---|---:|---:|",
    ]
    for boss in sorted(fights, key=lambda b: (list(BANDS).index(BOSS_BAND.get(b, "mid")), b)):
        rows = fights[boss]
        band = BOSS_BAND.get(boss, "?")
        ttk = statistics.median(num(r, "fight_seconds") for r in rows)
        party = statistics.median(max(1.0, num(r, "party_size", 1)) for r in rows)
        per_player = statistics.median(num(r, "party_dps") / max(1.0, num(r, "party_size", 1)) for r in rows)
        base_hp = statistics.median(num(r, "base_hp") or num(r, "boss_max_hp") for r in rows)
        if band not in BANDS:
            lines.append(f"| {boss} | ? | {len(rows)} | {ttk:.0f} | {party:.0f} | {per_player:.0f} | {base_hp:,.0f} | — | — | add to BOSS_BAND |")
            continue
        low, high, players = BANDS[band]
        target = (low + high) / 2.0
        if len(rows) < min_fights:
            lines.append(f"| {boss} | {band} | {len(rows)} | {ttk:.0f} | {party:.0f} | {per_player:.0f} | {base_hp:,.0f} | {low}–{high} s @{players} | — | need ≥{min_fights} fights |")
            continue
        live = per_player * players * target
        scale = 1.0 if boss in DUNGEON_BOSSES else 1.0 + RAID_HP_PER_EXTRA * (players - 1)
        suggested = round(live / scale, -2)
        if band == "entry":
            change = "keep (Entry)"
        else:
            change = f"×{suggested / base_hp:.2f}" if base_hp > 0 else "—"
        lines.append(
            f"| {boss} | {band} | {len(rows)} | {ttk:.0f} | {party:.0f} | {per_player:.0f} | {base_hp:,.0f} "
            f"| {low}–{high} s @{players} | {suggested:,.0f} | {change} |"
        )
    return lines


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("csv", type=Path)
    parser.add_argument("--md", type=Path, help="also write the table to this markdown file")
    parser.add_argument("--min-fights", type=int, default=5)
    args = parser.parse_args()
    fights = load(args.csv)
    if not fights:
        print("No usable fights in", args.csv)
        return
    table = report(fights, args.min_fights)
    print("\n".join(table))
    if args.md:
        args.md.write_text("# Boss HP suggestions from fight-log\n\n" + "\n".join(table) + "\n", encoding="utf-8")
        print("Wrote", args.md)


if __name__ == "__main__":
    main()
