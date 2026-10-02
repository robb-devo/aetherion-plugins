<#
.SYNOPSIS
  Lands the Island / Quarry / Guild highlight (AetherionGuilds only) into a checkout, hash-guarded.

.DESCRIPTION
  Writes ONLY the paths listed in MANIFEST.tsv, and only if each one is under
    AetherionGuilds\src\main\          (Java, config.yml, templates\*.schem)
  or is docs\ISLAND_QUARRY_HIGHLIGHT.md. Nothing else is ever written. No Items, Core, Hub, Quests file.

  Guard, per file (same rules as the previous ships):
    * The ship copy must match its manifest hash, else SHIP FILE CHANGED (refused).
    * A changed file is only replaced if it is EXACTLY the version this was built against
      (raw SHA-256, or SHA-256 after CRLF -> LF). The file keeps its own line endings.
    * A NEW file is only written if nothing exists at that path.
    * A file that already equals the new version is skipped, so re-running is safe.
    * Anything else is a CONFLICT and left alone (-Force overwrites and keeps a .bak-islandhl copy).
    * All-or-nothing per checkout: with any conflict, nothing is written to that checkout.

  Default target: the live-lineage worktree (.claude\worktrees\mining-eldervale-progression-65660c), like the
  previous ships. Its AetherionGuilds is byte-identical to IdeaProjects\AetherionGuilds, so -AlsoMainCheckout
  lands the same files there too. -Worktree points somewhere else.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\apply-island-highlight.ps1 -DryRun
  powershell -ExecutionPolicy Bypass -File .\apply-island-highlight.ps1
  powershell -ExecutionPolicy Bypass -File .\apply-island-highlight.ps1 -AlsoMainCheckout -DryRun
  powershell -ExecutionPolicy Bypass -File .\apply-island-highlight.ps1 -Compile   # apply, then mvn compile Guilds
#>
param(
    [string]$Worktree = (Join-Path $PSScriptRoot '..\.claude\worktrees\mining-eldervale-progression-65660c'),
    [switch]$AlsoMainCheckout,
    [switch]$DryRun,
    [switch]$Force,
    [switch]$Compile
)

$ErrorActionPreference = 'Stop'
$sha = [System.Security.Cryptography.SHA256]::Create()
$latin1 = [System.Text.Encoding]::GetEncoding(28591)
$sep = [string][System.IO.Path]::DirectorySeparatorChar

function Get-Hashes([byte[]]$bytes, [bool]$text) {
    $raw = ([BitConverter]::ToString($sha.ComputeHash($bytes)) -replace '-', '').ToLower()
    if (-not $text) { return @($raw, $raw) }
    $normal = $latin1.GetBytes($latin1.GetString($bytes).Replace("`r`n", "`n"))
    $lf = ([BitConverter]::ToString($sha.ComputeHash($normal)) -replace '-', '').ToLower()
    return @($raw, $lf)
}

function Test-Allowed([string]$rel) {
    if ($rel.Contains('..') -or $rel.Contains(':')) { return $false }
    if ($rel -eq 'docs/ISLAND_QUARRY_HIGHLIGHT.md') { return $true }
    return $rel.StartsWith('AetherionGuilds/src/main/')
}

$rows = @()
foreach ($line in Get-Content -LiteralPath (Join-Path $PSScriptRoot 'MANIFEST.tsv')) {
    if ($line.StartsWith('#') -or [string]::IsNullOrWhiteSpace($line)) { continue }
    $c = $line -split "`t"
    $rows += [pscustomobject]@{ Rel = $c[0]; Src = $c[1]; BaseRaw = $c[2]; BaseLf = $c[3]; NewRaw = $c[4]; NewLf = $c[5]
                                Text = ($c[6] -eq 'text'); Action = $c[7] }
}

$targets = @()
if (Test-Path -LiteralPath $Worktree) { $targets += (Resolve-Path -LiteralPath $Worktree).Path }
else { Write-Host "Worktree not found: $Worktree" -ForegroundColor Yellow }
if ($AlsoMainCheckout) { $targets += (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path }
if ($targets.Count -eq 0) {
    Write-Host "No target. Use -AlsoMainCheckout or -Worktree <checkout root>." -ForegroundColor Red
    exit 1
}

Write-Host "Island / Quarry / Guild highlight ship ($($rows.Count) files, AetherionGuilds only)" -ForegroundColor Cyan
if ($DryRun) { Write-Host "(dry run: nothing is written)" -ForegroundColor Yellow }

$totalConflicts = 0
foreach ($root in $targets) {
    Write-Host ""
    Write-Host "-> $root" -ForegroundColor Cyan
    if (-not (Test-Path -LiteralPath (Join-Path (Join-Path $root 'AetherionGuilds') 'pom.xml'))) {
        Write-Host "  no AetherionGuilds module here - skipped" -ForegroundColor Red; $totalConflicts++; continue
    }
    $same = 0; $conflicts = 0; $plan = @()
    foreach ($r in $rows) {
        if (-not (Test-Allowed $r.Rel)) { Write-Host "  NOT ALLOWED  $($r.Rel) - refused" -ForegroundColor Red; $conflicts++; continue }
        $dst = Join-Path $root ($r.Rel -replace '/', $sep)
        $src = Join-Path $PSScriptRoot ($r.Src -replace '/', $sep)
        if (-not (Test-Path -LiteralPath $src)) { Write-Host "  MISSING IN SHIP  $($r.Src)" -ForegroundColor Red; $conflicts++; continue }
        $srcBytes = [System.IO.File]::ReadAllBytes($src)
        $shipH = Get-Hashes $srcBytes $r.Text
        if ($shipH[0] -ne $r.NewRaw) { Write-Host "  SHIP FILE CHANGED  $($r.Src) (manifest mismatch)" -ForegroundColor Red; $conflicts++; continue }
        $exists = Test-Path -LiteralPath $dst
        $action = $null
        $outBytes = $srcBytes
        if ($exists) {
            $curBytes = [System.IO.File]::ReadAllBytes($dst)
            $cur = Get-Hashes $curBytes $r.Text
            if ($cur[0] -eq $r.NewRaw -or $cur[1] -eq $r.NewLf) { $same++; continue }
            if ($r.BaseRaw -ne 'NEW' -and ($cur[0] -eq $r.BaseRaw -or $cur[1] -eq $r.BaseLf)) {
                $action = 'update'
                # keep the file's own line endings (ship copy is CRLF)
                if ($r.Text -and -not $latin1.GetString($curBytes).Contains("`r`n")) {
                    $outBytes = $latin1.GetBytes($latin1.GetString($srcBytes).Replace("`r`n", "`n"))
                }
            }
        } elseif ($r.BaseRaw -eq 'NEW') {
            $action = 'new'
        }
        if (-not $action) {
            if ($Force) { $action = 'FORCED' } else {
                $why = if ($exists) { 'changed on disk since this build' } else { 'expected to exist' }
                Write-Host "  CONFLICT  $($r.Rel)  ($why; left untouched)" -ForegroundColor Red
                $conflicts++; continue
            }
        }
        Write-Host ("  {0,-8} {1}" -f $action, $r.Rel)
        $plan += [pscustomobject]@{ Row = $r; Dst = $dst; Bytes = $outBytes; Action = $action; Exists = $exists }
    }
    if ($conflicts -gt 0) {
        Write-Host "  conflicts found - nothing written to this checkout" -ForegroundColor Red
    } elseif (-not $DryRun) {
        foreach ($p in $plan) {
            $dir = Split-Path $p.Dst -Parent
            if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
            if ($p.Action -eq 'FORCED' -and $p.Exists) { Copy-Item -LiteralPath $p.Dst "$($p.Dst).bak-islandhl" -Force }
            [System.IO.File]::WriteAllBytes($p.Dst, $p.Bytes)
            $after = Get-Hashes ([System.IO.File]::ReadAllBytes($p.Dst)) $p.Row.Text
            if (-not ($after[0] -eq $p.Row.NewRaw -or $after[1] -eq $p.Row.NewLf)) {
                Write-Host "  VERIFY FAILED after write: $($p.Row.Rel)" -ForegroundColor Red; $conflicts++
            }
        }
    }
    $written = if ($conflicts -gt 0 -and -not $DryRun) { 0 } else { $plan.Count }
    Write-Host ("  {0} file(s) {1}, {2} already up to date, {3} conflict(s)." -f $written,
        $(if ($DryRun) { 'would be written' } else { 'written' }), $same, $conflicts)
    $totalConflicts += $conflicts

    if ($Compile -and -not $DryRun -and $conflicts -eq 0) {
        if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
            Write-Host "  -Compile: mvn not on PATH. Run in $root :  mvn -q -DskipTests -pl AetherionGuilds -am compile" -ForegroundColor Yellow
        } else {
            Write-Host "  mvn -q -DskipTests -pl AetherionGuilds -am compile" -ForegroundColor Cyan
            Push-Location $root
            try { & mvn -q -DskipTests -pl AetherionGuilds -am compile; $code = $LASTEXITCODE } finally { Pop-Location }
            if ($code -eq 0) { Write-Host "  compile OK" -ForegroundColor Green } else { Write-Host "  compile FAILED (exit $code)" -ForegroundColor Red; $totalConflicts++ }
        }
    }
}

Write-Host ""
if ($totalConflicts -gt 0) {
    Write-Host "Something was not written or failed - see above. Nothing outside the allowlist was touched." -ForegroundColor Yellow
    exit 1
}
Write-Host "Done. Next: mvn -DskipTests -pl AetherionGuilds -am package, then deploy the Guilds jar and restart (not /reload)." -ForegroundColor Green
exit 0
