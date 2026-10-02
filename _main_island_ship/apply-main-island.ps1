<#
.SYNOPSIS
  Lands the Main Island / Origin Hub overhaul into a checkout, additively and guarded.

.DESCRIPTION
  For every file in MANIFEST.tsv (and MANIFEST-items.tsv with -WithItemsTile):
    * NEW files are only written if they don't exist yet.
    * Changed files are only overwritten if the file on disk is exactly the version this build was made
      against. "Exactly" is checked twice: raw SHA-256, and SHA-256 after CRLF -> LF, so a checkout with
      other line endings (autocrlf) still matches. Anything else is a CONFLICT and is left alone,
      unless you pass -Force (then a .bak-origin copy is kept next to it).
    * A file that already matches the new version (either hash) is skipped: re-running is safe.

  Default target is the live-lineage worktree (.claude\worktrees\mining-eldervale-progression-65660c):
  the live Hub jar was built from it. MANIFEST.tsv touches AetherionHub + two docs only.

  -WithItemsTile also lands the optional DEV tile (Items DevHubs/DevMenu, WORLDS slot 39 -> /origin dev).
  Its base is the Items DEV files AFTER the Foraging Eldervale ship; without that ship they CONFLICT
  (harmless: nothing is written, the Hub part is independent).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\apply-main-island.ps1 -DryRun
  powershell -ExecutionPolicy Bypass -File .\apply-main-island.ps1
  powershell -ExecutionPolicy Bypass -File .\apply-main-island.ps1 -WithItemsTile -DryRun
  powershell -ExecutionPolicy Bypass -File .\apply-main-island.ps1 -Worktree C:\path\to\other\checkout -DryRun
#>
param(
    [string]$Worktree = (Join-Path $PSScriptRoot '..\.claude\worktrees\mining-eldervale-progression-65660c'),
    [switch]$WithItemsTile,
    [switch]$DryRun,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$Worktree = (Resolve-Path $Worktree).Path
$source = Join-Path $PSScriptRoot 'files'
$sha = [System.Security.Cryptography.SHA256]::Create()
$latin1 = [System.Text.Encoding]::GetEncoding(28591)
$manifests = @('MANIFEST.tsv')
if ($WithItemsTile) { $manifests += 'MANIFEST-items.tsv' }

function Get-Hashes([string]$path, [bool]$text) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $raw = ([BitConverter]::ToString($sha.ComputeHash($bytes)) -replace '-', '').ToLower()
    if (-not $text) { return @($raw, $raw) }
    # Byte-exact CRLF -> LF (latin1 round-trips every byte), then hash again.
    $normal = $latin1.GetBytes($latin1.GetString($bytes).Replace("`r`n", "`n"))
    $lf = ([BitConverter]::ToString($sha.ComputeHash($normal)) -replace '-', '').ToLower()
    return @($raw, $lf)
}

Write-Host "Main Island / Origin ship -> $Worktree" -ForegroundColor Cyan
Write-Host ("Manifests: {0}" -f ($manifests -join ', '))
if ($DryRun) { Write-Host "(dry run: nothing is written)" -ForegroundColor Yellow }

$written = 0; $same = 0; $conflicts = @()
foreach ($m in $manifests) {
    foreach ($line in Get-Content (Join-Path $PSScriptRoot $m)) {
        if ($line.StartsWith('#') -or [string]::IsNullOrWhiteSpace($line)) { continue }
        $cols = $line -split "`t"
        $rel = $cols[0]; $baseRaw = $cols[1]; $baseLf = $cols[2]; $newRaw = $cols[3]; $newLf = $cols[4]
        $text = $cols[5] -eq 'text'
        $src = Join-Path $source ($rel -replace '/', '\')
        $dst = Join-Path $Worktree ($rel -replace '/', '\')
        if (-not (Test-Path -LiteralPath $src)) { Write-Host "  MISSING IN SHIP  $rel" -ForegroundColor Red; $conflicts += $rel; continue }
        $shipHashes = Get-Hashes $src $text
        if ($shipHashes[0] -ne $newRaw) { Write-Host "  SHIP FILE CHANGED  $rel (manifest mismatch - rebuild the ship)" -ForegroundColor Red; $conflicts += $rel; continue }
        $exists = Test-Path -LiteralPath $dst
        $action = $null
        if ($exists) {
            $cur = Get-Hashes $dst $text
            if ($cur[0] -eq $newRaw -or $cur[1] -eq $newLf) { $same++; continue }
            if ($baseRaw -ne 'NEW' -and ($cur[0] -eq $baseRaw -or $cur[1] -eq $baseLf)) { $action = 'update' }
        } elseif ($baseRaw -eq 'NEW') {
            $action = 'new'
        }
        if (-not $action) {
            if ($Force) { $action = 'FORCED' } else {
                $why = if ($exists) { 'changed on disk since this build' } else { 'expected to exist (base file missing)' }
                Write-Host "  CONFLICT  $rel  ($why; left untouched)" -ForegroundColor Red
                $conflicts += $rel; continue
            }
        }
        Write-Host ("  {0,-8} {1}" -f $action, $rel)
        if (-not $DryRun) {
            $dir = Split-Path $dst -Parent
            if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
            if ($action -eq 'FORCED' -and $exists) { Copy-Item -LiteralPath $dst "$dst.bak-origin" -Force }
            Copy-Item -LiteralPath $src $dst -Force
        }
        $written++
    }
}
Write-Host ""
Write-Host ("{0} file(s) {1}, {2} already up to date, {3} conflict(s)." -f $written, ($(if ($DryRun) { 'would be written' } else { 'written' })), $same, $conflicts.Count)
if ($conflicts.Count -gt 0) {
    Write-Host "Conflicts were NOT overwritten. Merge them by hand (patches\*.diff shows every change), or re-run with -Force to keep a .bak-origin copy." -ForegroundColor Yellow
    exit 1
}
Write-Host "Next: mvn -DskipTests -pl AetherionHub -am package (from $Worktree), then the jar surface-diff - see APPLY.md." -ForegroundColor Green
exit 0
