<#
  Floor 1 rooms + Warden's Prison gear -> worktree (remote tools cannot write inside .claude).
  Guarded: a java file is only replaced if it is exactly the version this was built on; on any
  conflict nothing is written (-Force overrides). Overwritten files and the retired Floor 1
  templates (f1_prison_*, f1_great_cross_hall, f1_sunken_blocks) go to .\backup\<time>\.

  powershell -ExecutionPolicy Bypass -File .\apply.ps1 -DryRun
  powershell -ExecutionPolicy Bypass -File .\apply.ps1
  then: mvn -DskipTests -pl AetherionDungeons,AetherionItems -am package
#>
param(
    [string]$Target = (Join-Path $PSScriptRoot '..\.claude\worktrees\mining-eldervale-progression-65660c'),
    [switch]$DryRun,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$Target = (Resolve-Path $Target).Path
if (-not (Test-Path (Join-Path $Target 'AetherionDungeons\pom.xml'))) { throw "No AetherionDungeons module under $Target" }
Write-Host "Floor 1 rooms + gear -> $Target" -ForegroundColor Cyan
if ($DryRun) { Write-Host "(dry run: nothing is written)" -ForegroundColor Yellow }

function Sha([string]$path) { (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLower() }

$plan = @(); $conflicts = @(); $same = 0
foreach ($line in Get-Content (Join-Path $PSScriptRoot 'MANIFEST.tsv')) {
    if ($line.StartsWith('#') -or [string]::IsNullOrWhiteSpace($line)) { continue }
    $c = $line -split "`t"
    $src = Join-Path $PSScriptRoot ($c[0] -replace '/', '\')
    $dst = Join-Path $Target ($c[1] -replace '/', '\')
    $base = $c[2]; $new = $c[3]
    if ((Sha $src) -ne $new) { throw "Drop file damaged: $($c[0])" }
    $exists = Test-Path -LiteralPath $dst
    $cur = if ($exists) { Sha $dst } else { $null }
    if ($exists -and $cur -eq $new) { $same++; continue }
    $ok = ($base -eq 'ANY') -or ($base -eq 'NEW' -and -not $exists) -or ($exists -and $cur -eq $base)
    if (-not $ok -and -not $Force) {
        $conflicts += $c[1]
        Write-Host "  CONFLICT  $($c[1])  (changed since this build)" -ForegroundColor Red
        continue
    }
    $plan += [pscustomobject]@{ Rel = $c[1]; Src = $src; Dst = $dst; Exists = $exists; Tag = $(if (-not $exists) { 'new' } elseif ($ok) { 'update' } else { 'FORCED' }) }
}

$floor1 = Join-Path $Target 'AetherionDungeons\src\main\resources\structures\floor1'
$retired = @()
if (Test-Path $floor1) {
    $retired = Get-ChildItem -LiteralPath $floor1 -Filter '*.nbt' | Where-Object { $_.Name -like 'f1_prison_*' -or $_.Name -in @('f1_great_cross_hall.nbt', 'f1_sunken_blocks.nbt') }
}

if ($conflicts.Count -gt 0) {
    Write-Host "$($conflicts.Count) conflict(s). NOTHING was written. Merge by hand or re-run with -Force." -ForegroundColor Yellow
    exit 1
}

$backup = Join-Path $PSScriptRoot ("backup\" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
foreach ($p in $plan) {
    Write-Host ("  {0,-7} {1}" -f $p.Tag, $p.Rel)
    if ($DryRun) { continue }
    if ($p.Exists) {
        $bak = Join-Path $backup ($p.Rel -replace '/', '\')
        New-Item -ItemType Directory -Force -Path (Split-Path $bak -Parent) | Out-Null
        Copy-Item -LiteralPath $p.Dst -Destination $bak -Force
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $p.Dst -Parent) | Out-Null
    Copy-Item -LiteralPath $p.Src -Destination $p.Dst -Force
}
foreach ($f in $retired) {
    Write-Host ("  retire  {0}" -f $f.Name)
    if ($DryRun) { continue }
    $dir = Join-Path $backup 'retired-floor1'
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Move-Item -LiteralPath $f.FullName -Destination (Join-Path $dir $f.Name) -Force
}
Write-Host ("{0} written, {1} retired, {2} already up to date." -f $plan.Count, $retired.Count, $same)
if (-not $DryRun -and ($plan.Count + $retired.Count) -gt 0) { Write-Host "Backups: $backup" }
Write-Host "Next: mvn -DskipTests -pl AetherionDungeons,AetherionItems -am package" -ForegroundColor Green
exit 0
