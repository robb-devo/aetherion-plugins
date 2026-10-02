#!/usr/bin/env python3
"""Crafty API helper. Reads local panel creds. No secrets in git."""
from __future__ import annotations

import json
import ssl
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

CTX = ssl._create_unverified_context()
BASE = "https://127.0.0.1:8443/api/v2"
CREDS = Path("/var/opt/minecraft/crafty/crafty-4/app/config/default-creds.txt")
DB = Path("/var/opt/minecraft/crafty/crafty-4/app/config/db/crafty.sqlite")
MMOR = "a28d676a-03ef-40f1-9ac7-7a21c2ef6383"
ALIASES = {
    "mmor": MMOR,
    "mmo-r": MMOR,
    "mmo_r": MMOR,
}


def call(method: str, url: str, data=None, token: str | None = None, timeout: int = 30):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    with urllib.request.urlopen(req, context=CTX, timeout=timeout) as resp:
        return json.loads(resp.read().decode())


def login() -> str:
    creds = json.loads(CREDS.read_text(encoding="utf-8"))
    out = call("POST", f"{BASE}/auth/login", {"username": creds["username"], "password": creds["password"]})
    return out["data"]["token"]


def resolve_id(key: str) -> str:
    k = key.lower()
    if k in ALIASES:
        return ALIASES[k]
    if Path(f"/var/opt/minecraft/crafty/servers/{key}").is_dir():
        return key
    import sqlite3

    con = sqlite3.connect(str(DB))
    row = con.execute(
        "SELECT server_id FROM servers WHERE lower(server_name)=lower(?) LIMIT 1",
        (key,),
    ).fetchone()
    con.close()
    if not row:
        raise SystemExit(f"unknown server: {key}")
    return row[0]


def action(token: str, sid: str, name: str) -> bool:
    url = f"{BASE}/servers/{sid}/action/{name}"
    try:
        out = call("POST", url, {}, token=token, timeout=60)
        print(f"crafty action {name} -> {json.dumps(out)[:300]}")
        return True
    except Exception as e:
        print(f"crafty action {name} fail: {type(e).__name__}: {e}")
        return False


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print("usage: ae-crafty-action.py <stop|start|restart> <alias> [now]")
        return 2
    op, key = argv[0], argv[1]
    mode = argv[2] if len(argv) > 2 else ""
    token = login()
    sid = resolve_id(key)
    if op == "stop":
        # Prefer panel stop; 'now' is handled by caller kill fallback
        ok = action(token, sid, "stop_server") or action(token, sid, "stop") or action(token, sid, "shutdown")
        return 0 if ok else 1
    if op == "start":
        ok = action(token, sid, "start_server") or action(token, sid, "start")
        return 0 if ok else 1
    if op == "restart":
        action(token, sid, "stop_server") or action(token, sid, "stop")
        time.sleep(2 if mode in ("now", "force") else 12)
        ok = action(token, sid, "start_server") or action(token, sid, "start")
        return 0 if ok else 1
    print(f"unknown op {op}")
    return 2


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv[1:]))
    except urllib.error.URLError as e:
        print(f"CRAFTY_API_DOWN: {e}")
        raise SystemExit(1)
