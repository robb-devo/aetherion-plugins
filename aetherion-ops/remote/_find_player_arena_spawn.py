import json
import ssl
import urllib.request
from pathlib import Path
import gzip
import struct
import os

ctx = ssl._create_unverified_context()
creds = json.loads(Path("/var/opt/minecraft/crafty/crafty-4/app/config/default-creds.txt").read_text())
mmor = Path("/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383")

def call(method, url, data=None, tok=None):
    h = {"Content-Type": "application/json"}
    if tok:
        h["Authorization"] = f"Bearer {tok}"
    b = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(url, data=b, headers=h, method=method)
    with urllib.request.urlopen(req, context=ctx, timeout=30) as resp:
        return json.loads(resp.read().decode())

try:
    tok = call("POST", "https://127.0.0.1:8443/api/v2/auth/login", {
        "username": creds["username"],
        "password": creds["password"],
    })["data"]["token"]
    print("token ok", tok[:12])
    servers = call("GET", "https://127.0.0.1:8443/api/v2/servers", tok=tok)["data"]
    for s in servers:
        print("S", s.get("server_name"), s.get("server_id"), list(s.keys())[:12])
    mm = next(s for s in servers if s.get("server_id") == "a28d676a-03ef-40f1-9ac7-7a21c2ef6383" or s.get("server_name") == "MMO-R")
    sid = mm["server_id"]
    for path in (
        f"https://127.0.0.1:8443/api/v2/servers/{sid}/stdin",
        f"https://127.0.0.1:8443/api/v2/servers/{sid}/action/stdin",
        f"https://127.0.0.1:8443/api/v2/servers/{sid}/console",
    ):
        for payload in (
            {"command": "mv spawn bloodstone"},
            {"cmd": "mv spawn bloodstone"},
            {"input": "mv spawn bloodstone"},
        ):
            try:
                out = call("POST", path, payload, tok=tok)
                print("CMD OK", path, payload, out)
            except Exception as e:
                print("CMD fail", path, type(e).__name__, e)
except Exception as e:
    print("API fail", type(e).__name__, e)

# Scan online player NBT for anyone in bloodstone
players = mmor / "bloodstone" / "playerdata"
if players.exists():
    for f in players.glob("*.dat"):
        print("playerdata", f.name, f.stat().st_mtime)

# Also Essentials userdata last-location
eu = mmor / "plugins" / "Essentials" / "userdata"
if eu.exists():
    for f in sorted(eu.glob("*.yml"), key=lambda p: -p.stat().st_mtime)[:8]:
        t = f.read_text(encoding="utf-8", errors="replace")
        if "bloodstone" in t:
            print("ESS", f.name)
            for line in t.splitlines():
                if any(k in line for k in ("world:", "x:", "y:", "z:", "yaw:", "pitch:", "last-location", "logout", "bloodstone")):
                    print(" ", line)
            print("---")
