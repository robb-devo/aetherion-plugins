<#
.SYNOPSIS
  Lands "Dungeon Floor 1 elevate + transfer sync" into a source tree, additively and guarded.

.DESCRIPTION
  MANIFEST.tsv rows:
    SHIP  file from dungeon-f1-files.zip. Written only if the file on disk is exactly the version this
          build was made against (CRLF or LF), or - for rows marked MAIN - the main tree's current copy
          (TransferSnapshotStore / HubPortalBridge / InstanceManager: the new versions already carry
          the main tree's uncommitted changes). NEW files are only written if absent.
    SYNC  copied from the main tree (the source of the live Dungeons jar) into the target, only while
          the target still holds the committed base. No-op when the target IS the main tree.
  Plus an anchored 2-line AetherionQuests patch (QuestManager.forgetPlayer + call on quit), idempotent.

  Nothing is written if any row conflicts (unless -Force). Every overwritten file is backed up to
  .\backup\<timestamp>\ first. Floors 2/3, signature weapons, Talk UX, ranks, Island/Guilds: untouched.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\apply-dungeon-f1.ps1 -DryRun
  powershell -ExecutionPolicy Bypass -File .\apply-dungeon-f1.ps1
  powershell -ExecutionPolicy Bypass -File .\apply-dungeon-f1.ps1 -Target ..     # land in the main tree instead
#>
param(
    [string]$Target = (Join-Path $PSScriptRoot '..\.claude\worktrees\mining-eldervale-progression-65660c'),
    [string]$MainTree = (Join-Path $PSScriptRoot '..'),
    [switch]$DryRun,
    [switch]$Force,
    [switch]$SkipQuests
)

$ErrorActionPreference = 'Stop'
$Target = (Resolve-Path $Target).Path
$MainTree = (Resolve-Path $MainTree).Path
foreach ($root in @($Target, $MainTree)) {
    if (-not (Test-Path (Join-Path $root 'AetherionDungeons\pom.xml'))) { throw "No AetherionDungeons module under $root" }
}
$sameTree = ($Target.TrimEnd('\') -ieq $MainTree.TrimEnd('\'))
Write-Host "Dungeon F1 ship -> $Target" -ForegroundColor Cyan
Write-Host "Main tree (SYNC source) = $MainTree$(if ($sameTree) { '  (same as target)' })"
if ($DryRun) { Write-Host "(dry run: nothing is written)" -ForegroundColor Yellow }

function Sha([string]$path) { (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLower() }

# ---- unpack + verify payload
$zip = Join-Path $PSScriptRoot 'dungeon-f1-files.zip'
$tmp = Join-Path ([IO.Path]::GetTempPath()) ("dungeon-f1-" + [Guid]::NewGuid().ToString('N'))
Expand-Archive -LiteralPath $zip -DestinationPath $tmp -Force

$rows = @()
foreach ($line in Get-Content (Join-Path $PSScriptRoot 'MANIFEST.tsv')) {
    if ($line.StartsWith('#') -or [string]::IsNullOrWhiteSpace($line)) { continue }
    $c = $line -split "`t"
    $rows += [pscustomobject]@{ Kind = $c[0]; Rel = $c[1]; BaseCrlf = $c[2]; BaseLf = $c[3]; New = $c[4]; Note = $(if ($c.Count -gt 5) { $c[5] } else { '' }) }
}

$plan = @(); $conflicts = @(); $same = 0
foreach ($r in $rows) {
    $relWin = $r.Rel -replace '/', '\'
    $dst = Join-Path $Target $relWin
    $mainFile = Join-Path $MainTree $relWin
    $exists = Test-Path -LiteralPath $dst
    $cur = if ($exists) { Sha $dst } else { $null }
    $atBase = $exists -and ($cur -eq $r.BaseCrlf -or $cur -eq $r.BaseLf)
    if ($r.Kind -eq 'SHIP') {
        $src = Join-Path $tmp $relWin
        if (-not (Test-Path -LiteralPath $src) -or (Sha $src) -ne $r.New) { throw "Ship payload damaged: $($r.Rel)" }
        if ($exists -and $cur -eq $r.New) { $same++; continue }
        $action = $null
        if (-not $exists -and $r.BaseCrlf -eq 'NEW') { $action = 'new' }
        elseif ($atBase) { $action = 'update' }
        elseif ($exists -and $r.Note -eq 'MAIN' -and (Test-Path -LiteralPath $mainFile) -and $cur -eq (Sha $mainFile)) { $action = 'update (main copy)' }
    } else {
        if (-not (Test-Path -LiteralPath $mainFile)) { throw "SYNC source missing in main tree: $mainFile" }
        $src = $mainFile
        $mainSha = Sha $mainFile
        if ($exists -and $cur -eq $mainSha) { $same++; continue }
        $action = $null
        if (-not $exists -and $r.BaseCrlf -eq 'NEW') { $action = 'sync new' }
        elseif ($atBase) { $action = 'sync' }
    }
    if (-not $action) {
        if ($Force) { $action = 'FORCED' }
        else { $conflicts += $r.Rel; Write-Host "  CONFLICT  $($r.Rel)  (changed on disk since this build)" -ForegroundColor Red; continue }
    }
    $plan += [pscustomobject]@{ Action = $action; Rel = $r.Rel; Src = $src; Dst = $dst; Exists = $exists }
}

if ($conflicts.Count -gt 0) {
    Remove-Item -Recurse -Force $tmp
    Write-Host ""
    Write-Host "$($conflicts.Count) conflict(s). NOTHING was written. Merge by hand or re-run with -Force (backups are kept)." -ForegroundColor Yellow
    exit 1
}

$script:wrote = $false
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $PSScriptRoot "backup\$stamp"
foreach ($p in $plan) {
    Write-Host ("  {0,-19} {1}" -f $p.Action, $p.Rel)
    if ($DryRun) { continue }
    if ($p.Exists) {
        $script:wrote = $true
        $bak = Join-Path $backup ($p.Rel -replace '/', '\')
        New-Item -ItemType Directory -Force -Path (Split-Path $bak -Parent) | Out-Null
        Copy-Item -LiteralPath $p.Dst -Destination $bak -Force
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $p.Dst -Parent) | Out-Null
    Copy-Item -LiteralPath $p.Src -Destination $p.Dst -Force
}
Remove-Item -Recurse -Force $tmp
Write-Host ("{0} file(s) {1}, {2} already up to date." -f $plan.Count, $(if ($DryRun) { 'would be written' } else { 'written' }), $same)

# ---- AetherionQuests: drop the in-memory quest mirror on quit (so a character coming back from the
#      other backend is read from the synced files, not from this server's stale cache).
function Edit-Anchored([string]$path, [string]$already, [string[]]$requires, [string]$pattern, [scriptblock]$replace) {
    $name = Split-Path $path -Leaf
    if (-not (Test-Path -LiteralPath $path)) { Write-Host "  Quests: $name not found - skipped" -ForegroundColor Yellow; return }
    $bytes = [IO.File]::ReadAllBytes($path)
    $bom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    $text = [Text.Encoding]::UTF8.GetString($bytes, $(if ($bom) { 3 } else { 0 }), $bytes.Length - $(if ($bom) { 3 } else { 0 }))
    if ($text.Contains($already)) { Write-Host "  Quests: $name already patched"; return }
    foreach ($req in $requires) {
        if ($text -notmatch $req) { Write-Host "  Quests: $name anchor '$req' not found - skipped (optional patch)" -ForegroundColor Yellow; return }
    }
    $rx = New-Object System.Text.RegularExpressions.Regex($pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if ($rx.Matches($text).Count -ne 1) { Write-Host "  Quests: $name anchor not unique/missing - skipped (optional patch)" -ForegroundColor Yellow; return }
    $nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $m = $rx.Match($text)
    $out = $text.Substring(0, $m.Index) + [string](& $replace $m $nl) + $text.Substring($m.Index + $m.Length)
    Write-Host "  patch               $($path.Substring($Target.Length + 1))"
    if ($DryRun) { return }
    $script:wrote = $true
    $bak = Join-Path $backup ($path.Substring($Target.Length + 1))
    New-Item -ItemType Directory -Force -Path (Split-Path $bak -Parent) | Out-Null
    Copy-Item -LiteralPath $path -Destination $bak -Force
    $enc = New-Object System.Text.UTF8Encoding($bom)
    [IO.File]::WriteAllText($path, $out, $enc)
}

if (-not $SkipQuests) {
    $qm = Join-Path $Target 'AetherionQuests\src\main\java\de\aetherion\quests\manager\QuestManager.java'
    Edit-Anchored $qm 'void forgetPlayer(' @('Map<UUID,\s*PlayerQuestData>\s+playerData', 'import java\.util\.(UUID|\*);') `
        '^([ \t]*)public void resetAllQuests\(Player player\)\s*\{' {
            param($m, $nl)
            $i = $m.Groups[1].Value
            "${i}public void forgetPlayer(UUID uuid) {$nl${i}    if (uuid != null) {$nl${i}        playerData.remove(uuid);$nl${i}    }$nl${i}}$nl$nl" + $m.Value
        }
    $pj = Join-Path $Target 'AetherionQuests\src\main\java\de\aetherion\quests\listener\PlayerJoinListener.java'
    Edit-Anchored $pj 'forgetPlayer(' @('QuestManager\s+questManager') `
        '^([ \t]*)([^\r\n]*plugin\.getPlayerQuestStorage\(\)\.unload\(event\.getPlayer\(\)\.getUniqueId\(\)\);[^\r\n]*)(\r?\n)' {
            param($m, $nl)
            $i = $m.Groups[1].Value
            $m.Value + "${i}questManager.forgetPlayer(event.getPlayer().getUniqueId());" + $m.Groups[3].Value
        }
}

Write-Host ""
Write-Host "Next (from $Target):" -ForegroundColor Green
Write-Host "  mvn -DskipTests -pl AetherionDungeons,AetherionQuests -am package"
Write-Host "  deploy AetherionDungeons.jar -> mmo-r + mmo-d, AetherionQuests.jar -> mmo-r + mmo-d (Core/Items: no), restart both"
if ($script:wrote) { Write-Host "Backups of overwritten files: $backup" }
exit 0
