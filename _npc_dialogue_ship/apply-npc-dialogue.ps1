<#
.SYNOPSIS
  Lands the NPC / dialogue overhaul (AetherionQuests only) into the local worktree, additively and guarded.

.DESCRIPTION
  Files come from npc-dialogue-files.zip (CRLF). For every row of MANIFEST.tsv:
    * NEW files are only written if they don't exist yet (existing = CONFLICT unless identical).
    * Changed files are only overwritten when the file on disk is exactly the version this build was
      made against (SHA-256, CRLF — or the same content with LF line endings).
      Anything else is reported as CONFLICT and left alone, unless you pass -Force
      (then a .bak-npc copy is kept next to it).
  Nothing is deleted. Nothing outside AetherionQuests/ and docs/npc/ is touched.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\apply-npc-dialogue.ps1 -DryRun
  powershell -ExecutionPolicy Bypass -File .\apply-npc-dialogue.ps1
#>
param(
    [string]$Worktree = (Join-Path $PSScriptRoot '..\.claude\worktrees\mining-eldervale-progression-65660c'),
    [switch]$DryRun,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$Worktree = (Resolve-Path $Worktree).Path
$manifest = Join-Path $PSScriptRoot 'MANIFEST.tsv'
$zip = Join-Path $PSScriptRoot 'npc-dialogue-files.zip'
Write-Host "NPC dialogue ship -> $Worktree" -ForegroundColor Cyan
if ($DryRun) { Write-Host "(dry run: nothing is written)" -ForegroundColor Yellow }

$stage = Join-Path ([System.IO.Path]::GetTempPath()) ('npc-ship-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
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

$written = 0; $same = 0; $conflicts = @()
try {
    foreach ($line in Get-Content $manifest) {
        if ($line.StartsWith('#') -or [string]::IsNullOrWhiteSpace($line)) { continue }
        $cols = $line -split "`t"
        $rel = $cols[0]; $base = $cols[1]; $new = $cols[2]; $baseLf = if ($cols.Count -gt 4) { $cols[4] } else { '' }
        $src = Join-Path $stage ($rel -replace '/', '\')
        $dst = Join-Path $Worktree ($rel -replace '/', '\')
        if (-not (Test-Path -LiteralPath $src)) { Write-Host "  MISSING IN SHIP  $rel" -ForegroundColor Red; $conflicts += $rel; continue }
        $exists = Test-Path -LiteralPath $dst
        $current = if ($exists) { (Get-FileHash -Algorithm SHA256 -LiteralPath $dst).Hash.ToLower() } else { $null }
        $action = $null
        if ($exists -and $current -eq $new) { $same++; continue }
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
            if ($action -eq 'FORCED' -and $exists) { Copy-Item -LiteralPath $dst "$dst.bak-npc" -Force }
            Copy-Item -LiteralPath $src $dst -Force
        }
        $written++
    }
}
finally {
    Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host ("{0} file(s) {1}, {2} already up to date, {3} conflict(s)." -f $written, ($(if ($DryRun) { 'would be written' } else { 'written' })), $same, $conflicts.Count)
if ($conflicts.Count -gt 0) {
    Write-Host "Conflicts were NOT overwritten. Options: merge by hand, 'git apply --3way patches\*.patch', or re-run with -Force (keeps .bak-npc)." -ForegroundColor Yellow
    exit 1
}
Write-Host "Next: mvn -DskipTests package   (or deploy jar\AetherionQuests-1.0.0.jar - same source)" -ForegroundColor Green
exit 0
