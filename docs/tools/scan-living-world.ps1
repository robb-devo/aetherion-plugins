<#
  Read-only: copies a snapshot of the main island (map files, entity files, NPC and prop placement files) and a list
  of the plugin jars on every backend to IdeaProjects\_deploy\scan-living-world\, so the cloud session can fit NPC
  routes, props and buildings to the real ground. Nothing on the server changes: the copies are made in a temporary
  folder under /tmp, which is removed again at the end.
      powershell -NoProfile -ExecutionPolicy Bypass -File .\scan-living-world.ps1
  No passwords or keys in here: ssh uses your existing alias, the server folders come from aetherion-ops\docs.
#>
$ErrorActionPreference = 'Stop'
$Ssh = 'aetherion-hetzner'
$Ops = Join-Path $env:USERPROFILE 'IdeaProjects\aetherion-ops'
$Out = Join-Path $env:USERPROFILE 'IdeaProjects\_deploy\scan-living-world'
$Ts  = Get-Date -Format 'yyyyMMdd-HHmmss'
$Tmp = "/tmp/aeth-scan-$Ts"

function Die([string]$t) { Write-Host "STOP: $t" -ForegroundColor Red; exit 1 }

$serversMd = Join-Path $Ops 'docs\SERVERS.md'
if (-not (Test-Path $serversMd)) { Die "not found: $serversMd" }
$md   = Get-Content $serversMd -Raw
$base = [regex]::Match($md, '\|\s*Servers\s*\|\s*`([^`<]+)<uuid>`').Groups[1].Value
if (-not $base) { Die 'could not read the servers folder from aetherion-ops\docs\SERVERS.md' }
$servers = [ordered]@{}
foreach ($pair in @(@('hub', 'Hub'), @('mmor', 'MMO-R'), @('mmod', 'MMO-D'), @('mmoc', 'MMO-C'), @('mmoi', 'MMO-I'), @('mmom', 'MMO-M'))) {
    $m = [regex]::Match($md, '\|\s*' + [regex]::Escape($pair[1]) + ' uuid\s*\|\s*`([0-9a-f-]{36})`')
    if ($m.Success) { $servers[$pair[0]] = $base + $m.Groups[1].Value }
}
if (-not $servers.Contains('mmor')) { Die 'could not read the mmo-r folder from aetherion-ops\docs\SERVERS.md' }

if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$work = Join-Path $env:TEMP "aeth-scan-$Ts"
New-Item -ItemType Directory -Force -Path $work | Out-Null

$envJson = [ordered]@{ ts = $Ts; servers = $servers } | ConvertTo-Json -Depth 4
[IO.File]::WriteAllText((Join-Path $work 'env.json'), $envJson)

$scanPy = @'
#!/usr/bin/env python3
"""Living world scan, server side. Read-only: packs map and placement files into ./out for download."""
import glob
import hashlib
import json
import os
import tarfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
with open(os.path.join(HERE, 'env.json'), encoding='utf-8-sig') as f:
    ENV = json.load(f)
SRV = ENV['servers']
OUT = os.path.join(HERE, 'out')
os.makedirs(OUT, exist_ok=True)
PART_LIMIT = 280 * 1024 * 1024
FILE_LIMIT = 4 * 1024 * 1024


def sha1(path):
    h = hashlib.sha1()
    with open(path, 'rb') as f:
        for block in iter(lambda: f.read(1 << 20), b''):
            h.update(block)
    return h.hexdigest()


def stamp(path):
    return time.strftime('%Y-%m-%d %H:%M', time.localtime(os.path.getmtime(path)))


info = {'taken': time.strftime('%Y-%m-%d %H:%M:%S'), 'servers': {}, 'missing': [], 'skipped': []}
for alias, root in SRV.items():
    entry = {'jars': {}, 'plugin_dirs': [], 'worlds': []}
    plugins = os.path.join(root, 'plugins')
    if os.path.isdir(plugins):
        for p in sorted(glob.glob(os.path.join(plugins, '*.jar'))):
            entry['jars'][os.path.basename(p)] = {'sha1': sha1(p)[:8], 'bytes': os.path.getsize(p), 'mtime': stamp(p)}
        entry['plugin_dirs'] = sorted(d for d in os.listdir(plugins) if os.path.isdir(os.path.join(plugins, d)))
    if os.path.isdir(root):
        for d in sorted(os.listdir(root)):
            if os.path.isfile(os.path.join(root, d, 'level.dat')):
                reg = os.path.join(root, d, 'region')
                files = glob.glob(os.path.join(reg, '*.mca')) if os.path.isdir(reg) else []
                entry['worlds'].append({'name': d, 'regions': len(files), 'bytes': sum(os.path.getsize(x) for x in files)})
    info['servers'][alias] = entry

# Small placement files (top level of each data folder only, nothing with player data or secrets).
META = [
    ('mmor', 'plugins/AetherionQuests', ['npcs.yml', 'island-cast.yml', 'story-flags.yml', 'custom*.yml']),
    ('mmor', 'plugins/AetherionHub', ['*.yml']),
    ('mmor', 'plugins/FancyNpcs', ['npcs.yml']),
    ('mmor', 'plugins/AetherionMining', ['*.yml']),
    ('mmor', 'plugins/AetherionFarming', ['*.yml']),
    ('mmor', 'plugins/AetherionFishing', ['*.yml']),
    ('mmor', 'plugins/AetherionForaging', ['*.yml']),
    ('mmoc', 'plugins/AetherionQuests', ['npcs.yml', 'island-cast.yml']),
    ('mmom', 'plugins/AetherionMining', ['*.yml']),
    ('mmom', 'plugins/FancyNpcs', ['npcs.yml']),
]
count = 0
with tarfile.open(os.path.join(OUT, 'meta.tgz'), 'w:gz') as tar:
    for alias, rel, patterns in META:
        root = SRV.get(alias)
        if not root:
            continue
        folder = os.path.join(root, rel)
        for pattern in patterns:
            for path in sorted(glob.glob(os.path.join(folder, pattern))):
                if not os.path.isfile(path):
                    continue
                if os.path.getsize(path) > FILE_LIMIT:
                    info['skipped'].append(alias + '/' + rel + '/' + os.path.basename(path))
                    continue
                tar.add(path, arcname=alias + '/' + rel + '/' + os.path.basename(path))
                count += 1
    # Region list of the main world, so the far isles can be found without copying them.
    main = os.path.join(SRV['mmor'], 'world', 'region')
    info['mmor_world_regions'] = {os.path.basename(p): os.path.getsize(p) for p in sorted(glob.glob(os.path.join(main, '*.mca')))}
    blob = json.dumps(info, indent=1).encode('utf-8')
    with open(os.path.join(OUT, 'info.json'), 'wb') as f:
        f.write(blob)
    tar.add(os.path.join(OUT, 'info.json'), arcname='info.json')
os.remove(os.path.join(OUT, 'info.json'))

# The main island (x -526..458, z -616..364) and the Veil Quay east of it: blocks and entities.
wanted = []
for kind in ('region', 'entities'):
    for rx in (-2, -1, 0, 1):
        for rz in (-2, -1, 0, 1):
            path = os.path.join(SRV['mmor'], 'world', kind, 'r.%d.%d.mca' % (rx, rz))
            if os.path.isfile(path) and os.path.getsize(path) > 0:
                wanted.append((path, 'mmor/world/%s/r.%d.%d.mca' % (kind, rx, rz)))
            else:
                info['missing'].append('world/%s/r.%d.%d.mca' % (kind, rx, rz))
part, size, tar = 0, 0, None
for path, name in wanted:
    n = os.path.getsize(path)
    if tar is None or size + n > PART_LIMIT:
        if tar is not None:
            tar.close()
        part += 1
        size = 0
        tar = tarfile.open(os.path.join(OUT, 'world-%d.tar' % part), 'w')
    tar.add(path, arcname=name)
    size += n
if tar is not None:
    tar.close()

total = sum(os.path.getsize(os.path.join(OUT, x)) for x in os.listdir(OUT))
print('scan packed: %d placement files, %d map files in %d part(s), %.1f MB' % (count, len(wanted), part, total / 1048576.0))
'@
[IO.File]::WriteAllText((Join-Path $work 'scan.py'), $scanPy.Replace("`r`n", "`n"))

Write-Host "== 1/3  Packing on the server (read-only)"
& ssh -o BatchMode=yes -o ConnectTimeout=20 $Ssh "mkdir -p $Tmp"
if ($LASTEXITCODE -ne 0) { Die "ssh $Ssh failed ($LASTEXITCODE). Is the ssh alias set up on this PC?" }
& scp -q (Join-Path $work 'env.json') (Join-Path $work 'scan.py') "${Ssh}:$Tmp/"
if ($LASTEXITCODE -ne 0) { Die "upload failed ($LASTEXITCODE)" }
& ssh $Ssh "python3 $Tmp/scan.py"
if ($LASTEXITCODE -ne 0) { & ssh $Ssh "rm -rf $Tmp"; Die "packing on the server failed ($LASTEXITCODE)" }

Write-Host "== 2/3  Downloading"
& scp -q "${Ssh}:$Tmp/out/*" "$Out\"
$dl = $LASTEXITCODE

Write-Host "== 3/3  Removing the temporary copies on the server"
& ssh $Ssh "rm -rf $Tmp"
Remove-Item -Recurse -Force $work
if ($dl -ne 0) { Die "download failed ($dl)" }

$files = Get-ChildItem $Out -File
$mb = [math]::Round((($files | Measure-Object Length -Sum).Sum) / 1MB, 1)
Write-Host "scan ready: $Out ($($files.Count) files, $mb MB)"
foreach ($f in $files) { Write-Host ("  {0}  {1} MB" -f $f.Name, [math]::Round($f.Length / 1MB, 1)) }
