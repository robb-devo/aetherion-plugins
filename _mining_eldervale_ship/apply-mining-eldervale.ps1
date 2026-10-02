<#
.SYNOPSIS
  Lands the Mining Eldervale build into the local worktree, additively and guarded.

.DESCRIPTION
  For every file in MANIFEST.tsv:
    * NEW files are only written if they don't exist yet.
    * Changed files are only overwritten if the file on disk is exactly the version this build
      was made against (SHA-256 match). A file someone else edited since is reported as CONFLICT
      and left alone, never overwritten, unless you pass -Force (then a .bak-mining copy is kept).
  DevMenu.java (the Hypixel IA) is NOT in the manifest. Its splice is in docs/MINING_ELDERVALE.md, section 4.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\apply-mining-eldervale.ps1 -DryRun
  powershell -ExecutionPolicy Bypass -File .\apply-mining-eldervale.ps1
#>
param(
    [string]$Worktree = (Join-Path $PSScriptRoot '..\.claude\worktrees\mining-eldervale-progression-65660c'),
    [switch]$DryRun,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$Worktree = (Resolve-Path $Worktree).Path
$manifest = Join-Path $PSScriptRoot 'MANIFEST.tsv'
$source = Join-Path $PSScriptRoot 'files'
Write-Host "Mining Eldervale ship -> $Worktree" -ForegroundColor Cyan
if ($DryRun) { Write-Host "(dry run: nothing is written)" -ForegroundColor Yellow }

$written = 0; $same = 0; $conflicts = @()
foreach ($line in Get-Content $manifest) {
    if ($line.StartsWith('#') -or [string]::IsNullOrWhiteSpace($line)) { continue }
    $cols = $line -split "`t"
    $rel = $cols[0]; $base = $cols[1]; $new = $cols[2]; $note = if ($cols.Count -gt 3) { $cols[3] } else { '' }
    $src = Join-Path $source ($rel -replace '/', '\')
    $dst = Join-Path $Worktree ($rel -replace '/', '\')
    if (-not (Test-Path $src)) { Write-Host "  MISSING IN SHIP  $rel" -ForegroundColor Red; $conflicts += $rel; continue }
    $exists = Test-Path $dst
    $current = if ($exists) { (Get-FileHash -Algorithm SHA256 $dst).Hash.ToLower() } else { $null }
    $action = $null
    if ($exists -and $current -eq $new) { $same++; continue }
    elseif (-not $exists -and $base -eq 'NEW') { $action = 'new' }
    elseif ($exists -and $current -eq $base) { $action = 'update' }
    elseif ($exists -and $note -eq 'AMETHYST_EDIT') {
        $text = Get-Content -Raw $dst
        if ($text -match 'getCommand\("amethyst"\)' -and $text -notmatch 'MineIsle') { $action = 'update (keeps your /amethyst edit)' }
    }
    if (-not $action) {
        if ($Force) { $action = 'FORCED' } else {
            Write-Host "  CONFLICT  $rel  (changed on disk since this build; left untouched)" -ForegroundColor Red
            $conflicts += $rel; continue
        }
    }
    Write-Host ("  {0,-8} {1}" -f $action, $rel)
    if (-not $DryRun) {
        $dir = Split-Path $dst -Parent
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
        if ($action -eq 'FORCED' -and $exists) { Copy-Item $dst "$dst.bak-mining" -Force }
        Copy-Item $src $dst -Force
    }
    $written++
}
Write-Host ""
Write-Host ("{0} file(s) {1}, {2} already up to date, {3} conflict(s)." -f $written, ($(if ($DryRun) { 'would be written' } else { 'written' })), $same, $conflicts.Count)
if ($conflicts.Count -gt 0) {
    Write-Host "Conflicts were NOT overwritten. Merge them by hand (or re-run with -Force to keep a .bak-mining copy)." -ForegroundColor Yellow
    exit 1
}
Write-Host "Next: paste the DevMenu IA splice (docs/MINING_ELDERVALE.md, section 4), then: mvn -DskipTests package" -ForegroundColor Green
exit 0
