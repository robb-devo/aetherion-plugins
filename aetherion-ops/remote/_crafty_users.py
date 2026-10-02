import sqlite3
import json
from pathlib import Path

db = Path("/var/opt/minecraft/crafty/crafty-4/app/config/db/crafty.sqlite")
con = sqlite3.connect(str(db))
print("tables:", [r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")])
for t in ("users", "user", "accounts"):
    try:
        cols = [r[1] for r in con.execute(f"PRAGMA table_info({t})")]
        print(t, cols)
        for row in con.execute(f"SELECT * FROM {t} LIMIT 5"):
            print(" ", row)
    except Exception as e:
        print(t, e)
