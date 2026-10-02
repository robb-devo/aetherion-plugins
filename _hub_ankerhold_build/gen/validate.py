"""Validate every palette state against Minecraft 1.21.1 block definitions (PrismarineJS minecraft-data)."""
import json
from post import parse

import os
BL = {b["name"]: b for b in json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "blocks_1_21_1.json")))}


def check_state(state):
    base, props = parse(state)
    name = base.replace("minecraft:", "")
    if name not in BL:
        return f"unknown block {name}"
    b = BL[name]
    defs = {s["name"]: s for s in b.get("states", [])}
    for k, v in props.items():
        if k not in defs:
            return f"{name}: unknown property {k}"
        d = defs[k]
        if d["type"] == "bool":
            if v not in ("true", "false"):
                return f"{name}: {k}={v} not bool"
        elif d["type"] == "int":
            vals = d.get("values") or [str(i) for i in range(d.get("num_values", 0))]
            if str(v) not in [str(x) for x in vals]:
                return f"{name}: {k}={v} not in {vals}"
        elif d["type"] == "enum":
            if v not in d["values"]:
                return f"{name}: {k}={v} not in {d['values']}"
    missing = [k for k in defs if k not in props]
    return ("MISSING " + ",".join(missing)) if (missing and props) else None


def validate_palette(pal):
    bad, partial = [], []
    for s in pal:
        r = check_state(s)
        if r and r.startswith("MISSING"):
            partial.append((s, r))
        elif r:
            bad.append((s, r))
    return bad, partial


if __name__ == "__main__":
    from build import run
    W, T, P = run()
    bad, partial = validate_palette(W.pal)
    print("BAD", len(bad))
    for s, r in bad:
        print("  ", r, "|", s)
    print("PARTIAL (missing props, defaults used)", len(partial))
    for s, r in partial[:40]:
        print("  ", r, "|", s)
