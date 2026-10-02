from pathlib import Path

p = Path("/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383/plugins/Multiverse-Core/worlds.yml")
text = p.read_text(encoding="utf-8")
if "minecraft:ashen_void:" in text:
    print("already present")
    raise SystemExit(0)

block = """minecraft:ashen_void:
  adjust-spawn: false
  alias: Ashen Void
  allow-advancement-grant: true
  allow-flight: true
  allow-weather: false
  anchor-respawn: true
  auto-heal: true
  auto-load: true
  bed-respawn: true
  biome: ''
  difficulty: hard
  entry-fee:
    enabled: false
    amount: 0.0
    currency: '@vault-economy'
  gamemode: survival
  generator: ''
  hidden: false
  hunger: false
  keep-spawn-in-memory: true
  meta: {}
  player-limit: -1
  portal-form: all
  pvp: true
  respawn-world: ''
  scale: 1.0
  spawn-location:
    ==: MVSpawnLocation
    x: 0.5
    y: 68.0
    z: 0.5
    pitch: 0.0
    yaw: 0.0
  spawning:
    monster:
      spawn: false
      tick-rate: '@unset'
      spawn-limit: '@unset'
      exceptions: []
    animal:
      spawn: false
      tick-rate: '@unset'
      spawn-limit: '@unset'
      exceptions: []
    water_animal:
      spawn: false
      tick-rate: '@unset'
      spawn-limit: '@unset'
      exceptions: []
    water_ambient:
      spawn: false
      tick-rate: '@unset'
      spawn-limit: '@unset'
      exceptions: []
    water_underground_creature:
      spawn: false
      tick-rate: '@unset'
      spawn-limit: '@unset'
      exceptions: []
    ambient:
      spawn: false
      tick-rate: '@unset'
      spawn-limit: '@unset'
      exceptions: []
    axolotl:
      spawn: false
      tick-rate: '@unset'
      spawn-limit: '@unset'
      exceptions: []
    misc:
      spawn: false
      tick-rate: '@unset'
      spawn-limit: '@unset'
      exceptions: []
  world-blacklist: []
  read-only:
    environment: normal
    generator-settings: ''
    legacy-world-name: ashen_void
    seed: 0
  version: 1.3
"""
if not text.endswith("\n"):
    text += "\n"
p.write_text(text + "\n" + block, encoding="utf-8")
print("MV worlds.yml: minecraft:ashen_void appended")
