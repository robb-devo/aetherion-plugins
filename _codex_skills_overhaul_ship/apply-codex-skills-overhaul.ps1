<#
.SYNOPSIS
  Lands the Codex + Skills overhaul (Collection, Bestiary, Skills) into a checkout, additively and guarded.

.DESCRIPTION
  For every file in MANIFEST.tsv:
    * NEW files are only written if they don't exist yet.
    * Changed files are only overwritten if the file on disk is exactly the version this build was made
      against. "Exactly" is checked twice: raw SHA-256, and SHA-256 after CRLF -> LF, so a checkout with
      other line endings (autocrlf) still matches. Anything else is a CONFLICT and is left alone,
      unless you pass -Force (then a .bak-codex copy is kept next to it).
    * A file that already matches the new version (either hash) is skipped: re-running is safe.

  Default target is the live-lineage worktree (.claude\worktrees\mining-eldervale-progression-65660c):
  the live Items jar was built from it, so the Items jar built after this apply is
  "live + the Codex/Skills overhaul" and nothing else. Items only: no other plugin changes.

  Windows PowerShell 5.1 can't see paths longer than 260 characters. The longest file here is
  ~85 characters below the checkout root, so any checkout path under ~165 characters is fine
  (the default target is ~75).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\apply-codex-skills-overhaul.ps1 -DryRun
  powershell -ExecutionPolicy Bypass -File .\apply-codex-skills-overhaul.ps1
  powershell -ExecutionPolicy Bypass -File .\apply-codex-skills-overhaul.ps1 -Worktree C:\path\to\other\checkout -DryRun
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
$sha = [System.Security.Cryptography.SHA256]::Create()
$latin1 = [System.Text.Encoding]::GetEncoding(28591)

function Get-Hashes([string]$path, [bool]$text) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $raw = ([BitConverter]::ToString($sha.ComputeHash($bytes)) -replace '-', '').ToLower()
    if (-not $text) { return @($raw, $raw) }
    # Byte-exact CRLF -> LF (latin1 round-trips every byte), then hash again.
    $normal = $latin1.GetBytes($latin1.GetString($bytes).Replace("`r`n", "`n"))
    $lf = ([BitConverter]::ToString($sha.ComputeHash($normal)) -replace '-', '').ToLower()
    return @($raw, $lf)
}

Write-Host "Codex + Skills overhaul ship -> $Worktree" -ForegroundColor Cyan
if ($DryRun) { Write-Host "(dry run: nothing is written)" -ForegroundColor Yellow }

$written = 0; $same = 0; $conflicts = @()
foreach ($line in Get-Content $manifest) {
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
        if ($action -eq 'FORCED' -and $exists) { Copy-Item -LiteralPath $dst "$dst.bak-codex" -Force }
        Copy-Item -LiteralPath $src $dst -Force
    }
    $written++
}
Write-Host ""
Write-Host ("{0} file(s) {1}, {2} already up to date, {3} conflict(s)." -f $written, ($(if ($DryRun) { 'would be written' } else { 'written' })), $same, $conflicts.Count)
if ($conflicts.Count -gt 0) {
    Write-Host "Conflicts were NOT overwritten. Merge them by hand (patches\codex-skills-overhaul.diff shows every change), or re-run with -Force to keep a .bak-codex copy." -ForegroundColor Yellow
    exit 1
}
Write-Host "Next: mvn -DskipTests package (from $Worktree), then the jar surface-diff - see APPLY.md." -ForegroundColor Green
exit 0
