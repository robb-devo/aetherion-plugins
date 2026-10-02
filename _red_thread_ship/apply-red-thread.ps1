<#
.SYNOPSIS
  Lands the soft red thread pass (early game + skill isles, AetherionQuests only) into a worktree, additively and guarded.

.DESCRIPTION
  Files come from red-thread-files.zip (CRLF). For every row of MANIFEST.tsv:
    * NEW files are only written if they don't exist yet (existing = CONFLICT unless identical).
    * Changed files are only overwritten when the file on disk is exactly the version this build was
      made against (SHA-256, CRLF — or the same content with LF line endings).
      Anything else is reported as CONFLICT and left alone, unless you pass -Force
      (then a .bak-redthread copy is kept next to it).
  Nothing is deleted. Nothing outside AetherionQuests/ and docs/RED_THREAD.md is touched.

  Default target: the live-lineage worktree .claude\worktrees\mining-eldervale-progression-65660c
  (this folder sits in C:\Users\Robbi\IdeaProjects\_red_thread_ship).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\apply-red-thread.ps1 -DryRun
  powershell -ExecutionPolicy Bypass -File .\apply-red-thread.ps1
  powershell -ExecutionPolicy Bypass -File .\apply-red-thread.ps1 -Worktree C:\path\to\other\tree
#>
param(
    [string]$Worktree = (Join-Path $PSScriptRoot '..\.claude\worktrees\mining-eldervale-progression-65660c'),
    [switch]$DryRun,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$Worktree = (Resolve-Path $Worktree).Path
$manifest = Join-Path $PSScriptRoot 'MANIFEST.tsv'
$zip = Join-Path $PSScriptRoot 'red-thread-files.zip'
Write-Host "Red thread ship -> $Worktree" -ForegroundColor Cyan
if ($DryRun) { Write-Host "(dry run: nothing is written)" -ForegroundColor Yellow }

$stage = Join-Path ([System.IO.Path]::GetTempPath()) ('redthread-ship-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
Expand-Archive -Path $zip -DestinationPath $stage -Force

function Get-Sha([byte[]]$bytes) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return ([System.BitConverter]::ToString($sha.ComputeHash($bytes)) -replace '-', '').ToLower() }
    finally { $sha.Dispose() }
}

function Get-LfSha([string]$path) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $list = [System.Collections.Generic.List[byte]]::new($bytes.Length)
    for ($i = 0; $i -lt $bytes.Length; $i++) {
        if ($bytes[$i] -eq 13 -and ($i + 1) -lt $bytes.Length -and $bytes[$i + 1] -eq 10) { continue }
        $list.Add($bytes[$i])
    }
    return Get-Sha $list.ToArray()
}

$allowed = @('AetherionQuests/', 'docs/RED_THREAD.md')
$written = 0; $same = 0; $conflicts = @()
try {
    foreach ($line in Get-Content $manifest) {
        if ($line.StartsWith('#') -or [string]::IsNullOrWhiteSpace($line)) { continue }
        $cols = $line -split "`t"
        $rel = $cols[0]; $base = $cols[1]; $new = $cols[2]; $baseLf = if ($cols.Count -gt 4) { $cols[4] } else { '' }
        if (-not ($allowed | Where-Object { $rel.StartsWith($_) })) {
            Write-Host "  OUT OF SCOPE  $rel (skipped)" -ForegroundColor Red; $conflicts += $rel; continue
        }
        $src = Join-Path $stage ($rel -replace '/', '\')
        $dst = Join-Path $Worktree ($rel -replace '/', '\')
        if (-not (Test-Path -LiteralPath $src)) { Write-Host "  MISSING IN SHIP  $rel" -ForegroundColor Red; $conflicts += $rel; continue }
        $exists = Test-Path -LiteralPath $dst
        $current = if ($exists) { (Get-FileHash -Algorithm SHA256 -LiteralPath $dst).Hash.ToLower() } else { $null }
        $action = $null
        if ($exists -and $current -eq $new) { $same++; continue }
        elseif ($exists -and (Get-LfSha $dst) -eq (Get-LfSha $src)) { $same++; continue }
        elseif (-not $exists -and $base -eq 'NEW') { $action = 'new' }
        elseif ($exists -and $base -ne 'NEW' -and $current -eq $base) { $action = 'update' }
        elseif ($exists -and $base -ne 'NEW' -and $baseLf -and (Get-LfSha $dst) -eq $baseLf) { $action = 'update (LF base)' }
        if (-not $action) {
            if ($Force) { $action = 'FORCED' } else {
                $why = if (-not $exists) { 'missing on disk' } elseif ($base -eq 'NEW') { 'already exists' } else { 'changed on disk since this build' }
                Write-Host "  CONFLICT  $rel  ($why; left untouched)" -ForegroundColor Red
                $conflicts += $rel; continue
            }
        }
        Write-Host ("  {0,-16} {1}" -f $action, $rel)
        if (-not $DryRun) {
            $dir = Split-Path $dst -Parent
            if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
            if ($action -eq 'FORCED' -and $exists) { Copy-Item -LiteralPath $dst "$dst.bak-redthread" -Force }
            Copy-Item -LiteralPath $src $dst -Force
        }
        $written++
    }
}
finally {
    Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue
}

Write-Host ""
$verb = if ($DryRun) { 'would write' } else { 'written' }
Write-Host ("{0}: {1}   already identical: {2}   conflicts: {3}" -f $verb, $written, $same, $conflicts.Count) -ForegroundColor Cyan
if ($conflicts.Count -gt 0) {
    Write-Host "Conflicting files were NOT touched. Merge them by hand from patches\ or re-run with -Force (keeps .bak-redthread)." -ForegroundColor Yellow
    exit 2
}
if (-not $DryRun) {
    Write-Host "Next: mvn -o -DskipTests -pl AetherionQuests -am package   ·   python docs\dialogs\tools\validate_de_overlay.py   (no deploy from here)" -ForegroundColor Green
}
