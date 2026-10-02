#Requires -Version 5.1
<#
.SYNOPSIS
  Helios Requiem Polish Pass 2 -> MMO-R (Hetzner). Deploy, graceful restart, verify, record the branch.

  1. Checks the prebuilt BossEngine-1.0.0.jar next to this script (sha256).
  2. ae.ps1 deploy-jar mmor ... -NoRestart   (upload + remapper clear)
  3. ae-stop.sh mmor (graceful, LOCKED countdown) + ae-start.sh mmor --wait   (hung waiter is killed after 5 min)
  4. Polls logs/latest.log (since the START marker) for "[Helios] Ready".
  5. Records the source: in _wt_helios_polish, stashes the uncommitted live patches (they are commit 1),
     creates branch claude/helios-polish-pass2 and applies both patches (git am --3way). Non-fatal.

  Run:  powershell -ExecutionPolicy Bypass -File C:\Users\Robbi\IdeaProjects\helios-pass2\ship-helios-pass2.ps1
  Options:  -SkipDeploy (git only)   -SkipGit (deploy only)
#>
[CmdletBinding()]
param(
  [switch]$SkipDeploy,
  [switch]$SkipGit
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Join-Path $here 'BossEngine-1.0.0.jar'
$expectedSha = '50b851e5153276272fd6a2d3f1cbc7acd797f81785917f6029de262f817b43d5'
$ops = 'C:\Users\Robbi\IdeaProjects\aetherion-ops\bin'
$wt = 'C:\Users\Robbi\IdeaProjects\_wt_helios_polish'
$hostAlias = 'aetherion-hetzner'
$log = '/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383/logs/latest.log'

function Step($msg) { Write-Host ""; Write-Host "== $msg" -ForegroundColor Cyan }

if (-not $SkipDeploy) {
  Step 'jar check'
  if (-not (Test-Path $jar)) { throw "missing $jar" }
  $sha = (Get-FileHash $jar -Algorithm SHA256).Hash.ToLower()
  if ($sha -ne $expectedSha) { throw "jar sha256 mismatch: $sha (expected $expectedSha)" }
  Write-Host "ok $sha ($((Get-Item $jar).Length) bytes)"

  Step 'deploy BossEngine-1.0.0.jar to MMO-R (no restart yet)'
  & (Join-Path $ops 'ae.ps1') deploy-jar mmor -Jar $jar -DestName 'BossEngine-1.0.0.jar' -NoRestart

  Step 'graceful stop + start --wait (max 5 min)'
  $remote = '"bash /root/aetherion-ops/ae-stop.sh mmor; sleep 2; bash /root/aetherion-ops/ae-start.sh mmor --wait; echo START_SCRIPT_DONE"'
  $p = Start-Process -FilePath 'ssh' -ArgumentList @($hostAlias, $remote) -NoNewWindow -PassThru
  if (-not $p.WaitForExit(300000)) {
    Write-Host 'start waiter still running after 5 min: killing it (the server keeps running)' -ForegroundColor Yellow
    try { $p.Kill() } catch { }
  }

  Step 'verify [Helios] Ready (since the last START marker)'
  $ready = $false
  $probe = "tac $log | sed '/=== START/q' | grep -a -E '\[Helios\] (Ready|Updated|Could not)|Done \(|BossEngine.*(Error|Exception)' | tac"
  for ($i = 0; $i -lt 24 -and -not $ready; $i++) {
    $out = & ssh $hostAlias $probe 2>$null
    if ($out -match '\[Helios\] Ready') { $ready = $true; $out | ForEach-Object { Write-Host $_ } }
    else { Start-Sleep -Seconds 10 }
  }
  if ($ready) {
    Write-Host 'MMO-R is up with Helios Pass 2.' -ForegroundColor Green
  } else {
    Write-Host 'No "[Helios] Ready" yet. Last Helios / error lines:' -ForegroundColor Yellow
    & ssh $hostAlias "grep -a -E 'Helios|BossEngine' $log | tail -n 25"
  }
}

if (-not $SkipGit) {
  Step "record the source on branch claude/helios-polish-pass2 in $wt"
  try {
    Push-Location $wt
    $cur = (& git rev-parse --abbrev-ref HEAD).Trim()
    if ($cur -eq 'claude/helios-polish-pass2') {
      Write-Host 'already on claude/helios-polish-pass2: nothing to do'
    } else {
      $ident = @()
      if (-not (& git config user.email)) { $ident = @('-c', 'user.name=Robbi', '-c', 'user.email=ropebe51@gmail.com') }
      $dirty = & git status --porcelain --untracked-files=no
      if ($dirty) {
        # These are the live-only patches; commit 1 of the pass records them in source.
        & git @ident stash push -m 'pre-pass2 live patches (recorded as pass2 commit 1)'
        if ($LASTEXITCODE -ne 0) { throw 'git stash failed' }
      }
      & git checkout -b claude/helios-polish-pass2
      if ($LASTEXITCODE -ne 0) { throw 'git checkout -b failed' }
      $patches = Get-ChildItem $here -Filter '*.patch' | Sort-Object Name | ForEach-Object { $_.FullName }
      & git @ident am --3way @patches
      if ($LASTEXITCODE -ne 0) { & git am --abort; throw 'git am failed (aborted; your stash is kept: git stash list)' }
      & git log --oneline -3
      Write-Host 'Branch ready. Push when you like: git push -u origin claude/helios-polish-pass2' -ForegroundColor Green
    }
  } catch {
    Write-Host "git step skipped: $_" -ForegroundColor Yellow
  } finally {
    Pop-Location
  }
}
