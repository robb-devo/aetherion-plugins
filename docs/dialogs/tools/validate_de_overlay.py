"""Coverage + safety check for AetherionQuests' German overlay (lang/de.yml).

Run from the repo root:  python docs/dialogs/tools/validate_de_overlay.py [--base <git-rev>]

Checks
  1. de.yml parses; every key of the base revision's de.yml still exists (additive only).
  2. Every key the Java code asks LangPack for has a German entry
     (say / sayLines / ui / msg / dialogs / completed + TalkUx ui(...)).
  3. Every intro id in DialogManager#getDialogLines and every completed case has DE.
  4. CastBook: each English voice field has a German mirror (Borderlands NPCs excepted).
  5. Banter: same script count and beat count per script as CastBook.
  6. No {player} token in shared barks (idle / banter): multi-viewer barks can't fill it.
  7. Bubble budget: German lines over MAX_VISIBLE characters are listed as warnings.
  8. Every registered quest has a German title + description.
Exit code 1 on any hard failure (1-6, 8). Warnings (7) never fail the run.
"""
import re
import subprocess
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[3]
Q = ROOT / "AetherionQuests" / "src" / "main"
JAVA = Q / "java" / "de" / "aetherion" / "quests"
DE_YML = Q / "resources" / "lang" / "de.yml"
MAX_VISIBLE = 95
# Owned by the Hub / Borderlands pass: their EN + DE stay untouched here.
CLAIMED_CAST = {"rite_keeper", "arena_proctor"}

fails, warns = [], []


def visible(s):
    return len(re.sub(r"§.", "", s))


def get(tree, dotted):
    node = tree
    for part in dotted.split("."):
        if not isinstance(node, dict) or part not in node:
            return None
        node = node[part]
    return node


def leaves(node, prefix=""):
    if isinstance(node, dict):
        for k, v in node.items():
            yield from leaves(v, f"{prefix}{k}.")
    else:
        yield prefix[:-1], node


def java(rel):
    return (JAVA / rel).read_text(encoding="utf-8")


de = yaml.safe_load(DE_YML.read_text(encoding="utf-8"))

# ---------------------------------------------------------------- 1. additive vs base
base_rev = sys.argv[sys.argv.index("--base") + 1] if "--base" in sys.argv else "HEAD"
try:
    base_text = subprocess.run(
        ["git", "show", f"{base_rev}:AetherionQuests/src/main/resources/lang/de.yml"],
        cwd=ROOT, capture_output=True, text=True, encoding="utf-8", check=True).stdout
    base = yaml.safe_load(base_text)
    for path, value in leaves(base):
        now = get(de, path)
        if now is None:
            fails.append(f"[additive] base key missing: {path}")
        elif isinstance(value, list) and isinstance(now, dict):
            # list → variant section is allowed when every variant is a list
            if not all(isinstance(v, list) and v for v in now.values()):
                fails.append(f"[additive] {path} became a section with non-list variants")
except subprocess.CalledProcessError:
    warns.append(f"[additive] could not read de.yml at {base_rev}; skipped")

# ---------------------------------------------------------------- 2. keys used by Java
src = {p: p.read_text(encoding="utf-8") for p in JAVA.rglob("*.java")}
patterns = [
    (r'LangPack\.say\(\s*\w+(?:\(\))?,\s*"([^"]+)"', "talk.{}", str),
    (r'LangPack\.sayLines\(\s*\w+,\s*"([^"]+)"', "talk.{}", list),
    (r'LangPack\.ui\(\s*[\w.()]+,\s*"([^"]+)"', "ui.{}", str),
    (r'LangPack\.msg\(\s*\w+,\s*"([^"]+)"', "msg.{}", str),
    (r'LangPack\.dialogs\(\s*\w+,\s*"([^"]+)"', "dialogs.{}", list),
    (r'LangPack\.completed\(\s*\w+,\s*"([^"]+)"', "completed.{}", list),
]
for path, text in src.items():
    for rx, fmt, kind in patterns:
        for key in re.findall(rx, text, flags=re.S):
            if key.endswith("."):
                continue  # dynamic prefix ("ledger.desk." + slug …) — checked explicitly below
            dotted = fmt.format(key.lower())
            value = get(de, dotted)
            if value is None:
                fails.append(f"[java] {path.name}: no DE for {dotted}")
            elif kind is str and not isinstance(value, str):
                fails.append(f"[java] {path.name}: {dotted} should be a string")
            elif kind is list and not isinstance(value, (list, dict)):
                fails.append(f"[java] {path.name}: {dotted} should be a list")
talk_ux = src[JAVA / "talk" / "TalkUx.java"]
for key in re.findall(r'(?<![\w.])ui\(\s*[\w.()]+,\s*"([^"]+)"', talk_ux):
    if key.endswith("."):
        continue  # the helper itself: LangPack.ui(player, "talk_ux." + key, …)
    if not isinstance(get(de, f"ui.talk_ux.{key}"), str):
        fails.append(f"[java] TalkUx: no DE for ui.talk_ux.{key}")
for extra in ("ui.talk_ux.accept_default", "ui.talk_ux.decline_default", "ui.talk_ux.bye"):
    if not isinstance(get(de, extra), str):
        fails.append(f"[java] no DE for {extra}")
# Ledger in-world desk keys are built from the topic title.
for slug in ("aetherion_manager", "skills", "pets", "boosters"):
    for field, kind in (("label", str), ("echo", str), ("lines", list)):
        if not isinstance(get(de, f"talk.ledger.desk.{slug}.{field}"), kind):
            fails.append(f"[java] no DE for talk.ledger.desk.{slug}.{field}")
if not isinstance(get(de, "talk.ledger.desk.more"), str):
    fails.append("[java] no DE for talk.ledger.desk.more")

# ---------------------------------------------------------------- 3. intros + completed
dm = java("dialog/DialogManager.java")
intro_ids = set(re.findall(r'dialogId\.equalsIgnoreCase\(\s*"([a-z0-9_]+)"', dm))
intro_ids |= {"arena_proctor_vial", "arena_proctor_self", "arena_proctor_done", "fishing_before_egon",
              "tutorial_blocked", "ledger_blocked", "egon_kit_handoff", "egon_arrival_call", "egon_greet_near"}
for dialog_id in sorted(intro_ids):
    if get(de, f"dialogs.{dialog_id}") is None:
        fails.append(f"[dialogs] no DE for dialogs.{dialog_id}")
body = dm[dm.index("private String[] getCompletedLines"):dm.index("private String[] craftsmanIntroLines")]
for group in re.findall(r'case ((?:"[a-z_]+"(?:,\s*)?)+) ->', body):
    ids = re.findall(r'"([a-z_]+)"', group)
    if set(ids) & CLAIMED_CAST:
        continue
    if not any(get(de, f"completed.{i}") is not None for i in ids):
        fails.append(f"[completed] no DE for completed.{ids[0]}")

# ---------------------------------------------------------------- 4. CastBook mirror
cast_src = java("npc/CastBook.java")
static_block = cast_src[cast_src.index("static {"):cast_src.index("private static Voice voice(")]
field_map = {"accept": "accept", "decline": "decline", "onAccept": "on_accept", "onDecline": "on_decline",
             "returning": "returning", "greetNew": "greet_new", "greet": "greet", "greetGrad": "greet_grad",
             "idle": "idle", "farewell": "farewell"}
voices = re.split(r'\n\s*v = voice\("', static_block)[1:]
cast_de = de.get("cast", {})
for chunk in voices:
    npc = chunk[:chunk.index('"')]
    chunk = chunk.split("// ------------------------------------------------------------ NPC-to-NPC")[0]
    if npc in CLAIMED_CAST:
        continue
    entry = cast_de.get(npc)
    if entry is None:
        fails.append(f"[cast] no DE voice for {npc}")
        continue
    for en_field, de_field in field_map.items():
        if re.search(rf"\bv\.{en_field}\s*=", chunk) and de_field not in entry:
            fails.append(f"[cast] {npc}: missing {de_field}")
    en_topics = len(re.findall(r"\btopic\(v,", chunk))
    de_topics = len(entry.get("topics", []) or [])
    if en_topics != de_topics:
        fails.append(f"[cast] {npc}: {en_topics} EN topics vs {de_topics} DE")
    for field in ("idle",):
        for line in entry.get(field, []) or []:
            if "{player}" in line.lower():
                fails.append(f"[cast] {npc}.{field} uses {{player}} (shared bark): {line}")

# ---------------------------------------------------------------- 5. banter alignment
en_banter = re.findall(r"banter\(((?:new Beat\([^)]*\)[,\s]*)+)\);", static_block)
en_counts = [len(re.findall(r"new Beat\(", b)) for b in en_banter]
de_banter = de.get("banter", []) or []
if len(en_counts) != len(de_banter):
    fails.append(f"[banter] {len(en_counts)} EN scripts vs {len(de_banter)} DE")
for i, (n, beats) in enumerate(zip(en_counts, de_banter)):
    if len(beats) != n:
        fails.append(f"[banter] script {i}: {n} EN beats vs {len(beats)} DE")
    for line in beats:
        if "{player}" in line.lower():
            fails.append(f"[banter] script {i} uses {{player}}: {line}")

# ---------------------------------------------------------------- 7. bubble budget
for path, value in leaves(de):
    lines = value if isinstance(value, list) else [value]
    for line in lines:
        if isinstance(line, dict):
            line = " / ".join(str(x) for x in line.get("lines", []))
            continue
        if isinstance(line, str) and visible(line) > MAX_VISIBLE and not path.startswith("quests."):
            warns.append(f"[length] {path}: {visible(line)} chars — {line[:70]}…")

# ---------------------------------------------------------------- 8. quests
reg = java("quest/QuestRegistry.java")
quest_ids = set(re.findall(r'new Quest\(\s*"([a-z_]+)"', reg))
quest_ids |= set(re.findall(r'register(?:T2)?BossHunt\(\s*questManager,\s*"([a-z_]+)"', reg))
for qid in sorted(quest_ids):
    for field in ("title", "description"):
        if not isinstance(get(de, f"quests.{qid}.{field}"), str):
            fails.append(f"[quests] no DE {field} for {qid}")

# ---------------------------------------------------------------- report
print(f"de.yml: {sum(1 for _ in leaves(de))} leaf keys · {len(intro_ids)} intro ids · "
      f"{len(quest_ids)} quests · {len(cast_de)} DE voices · {len(de_banter)} banter scripts")
for w in warns:
    print("WARN ", w)
for f in fails:
    print("FAIL ", f)
print("OK" if not fails else f"{len(fails)} failure(s)")
sys.exit(1 if fails else 0)
