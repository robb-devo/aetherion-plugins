#Requires -Version 5.1
<#
.SYNOPSIS
  Fast Aetherion Hetzner / Crafty ops from Windows.

  Canonical path (always the same):
    build → scp jar → install on server → stop → start --wait

.EXAMPLE
  .\ae.ps1 status
  .\ae.ps1 deploy-items          # build + upload + restart (default)
  .\ae.ps1 deploy-items -NoRestart
  .\ae.ps1 start mmor
  .\ae.ps1 stop mmor
  .\ae.ps1 stop mmor -Now
  .\ae.ps1 restart mmor
#>
[CmdletBinding()]
param(
  [Parameter(Position = 0, Mandatory = $true)]
  [ValidateSet('status', 'deploy-items', 'deploy-hub', 'deploy-jar', 'start', 'stop', 'restart', 'ssh', 'sync-ops')]
  [string]$Command,

  [Parameter(Position = 1)]
  [string]$Target = 'mmor',

  [string]$Jar,
  [string]$DestName,
  [switch]$Restart,      # legacy alias — deploy always restarts unless -NoRestart
  [switch]$NoRestart,
  [switch]$Now,
  [switch]$Wait,
  [string[]]$SshArgs
)

$ErrorActionPreference = 'Stop'
$HostAlias = 'aetherion-hetzner'
$ItemsRoot = 'C:\Users\Robbi\IdeaProjects\AetherionItems'
$HubRoot = 'C:\Users\Robbi\IdeaProjects\AetherionHub'
$OpsRemote = 'C:\Users\Robbi\IdeaProjects\aetherion-ops\remote'

function Invoke-AeSsh {
  param([Parameter(Mandatory)][string]$RemoteCommand)
  & ssh $HostAlias $RemoteCommand
  if ($LASTEXITCODE -ne 0) { throw "ssh failed ($LASTEXITCODE): $RemoteCommand" }
}

function Invoke-AeScp {
  param([Parameter(Mandatory)][string]$Local, [Parameter(Mandatory)][string]$Remote)
  & scp $Local "${HostAlias}:$Remote"
  if ($LASTEXITCODE -ne 0) { throw "scp failed ($LASTEXITCODE): $Local -> $Remote" }
}

function Invoke-AeRestart {
  param([string]$Server = 'mmor')
  $mode = if ($Now) { 'now' } else { '' }
  Write-Host "stop $Server..."
  Invoke-AeSsh "bash /root/aetherion-ops/ae-stop.sh $Server $mode"
  Write-Host "start $Server..."
  Invoke-AeSsh "bash /root/aetherion-ops/ae-start.sh $Server --wait"
}

function Invoke-AeDeployJar {
  param(
    [Parameter(Mandatory)][string]$LocalJar,
    [Parameter(Mandatory)][string]$DestName,
    [string]$Server = 'mmor',
    [bool]$DoRestart = $true
  )
  if (-not (Test-Path $LocalJar)) { throw "missing $LocalJar" }
  Write-Host ("upload {0} ({1} bytes)" -f (Split-Path $LocalJar -Leaf), (Get-Item $LocalJar).Length)
  Invoke-AeScp $LocalJar "/tmp/$DestName"
  Invoke-AeSsh "bash /root/aetherion-ops/ae-deploy-jar.sh $Server /tmp/$DestName $DestName"
  if ($DoRestart) {
    Invoke-AeRestart -Server $Server
  } else {
    Write-Host 'JAR deployed (no restart).'
  }
}

switch ($Command) {
  'ssh' {
    if ($SshArgs) { & ssh $HostAlias @SshArgs } else { & ssh $HostAlias }
  }
  'sync-ops' {
    Write-Host 'sync remote ops scripts...'
    Invoke-AeSsh 'mkdir -p /root/aetherion-ops'
    foreach ($name in @('ae-lib.sh', 'ae-stop.sh', 'ae-start.sh', 'ae-deploy-jar.sh', 'ae-status.sh', 'ae-crafty-action.py')) {
      $local = Join-Path $OpsRemote $name
      if (Test-Path $local) {
        # Always store LF locally before scp (Windows editors reintroduce CRLF).
        $raw = [IO.File]::ReadAllText($local) -replace "`r`n", "`n" -replace "`r", "`n"
        $tmp = Join-Path $env:TEMP ("ae-ops-" + $name)
        $utf8 = New-Object System.Text.UTF8Encoding $false
        [IO.File]::WriteAllText($tmp, $raw, $utf8)
        Invoke-AeScp $tmp "/root/aetherion-ops/$name"
      }
    }
    Invoke-AeSsh "chmod +x /root/aetherion-ops/ae-*.sh; sed -i 's/\r`$//' /root/aetherion-ops/ae-*.sh /root/aetherion-ops/ae-*.py 2>/dev/null || true"
    Write-Host 'ops synced'
  }
  'status' {
    Invoke-AeSsh 'bash /root/aetherion-ops/ae-status.sh'
  }
  'start' {
    Invoke-AeSsh "bash /root/aetherion-ops/ae-start.sh $Target --wait"
  }
  'stop' {
    $mode = if ($Now) { 'now' } else { '' }
    Invoke-AeSsh "bash /root/aetherion-ops/ae-stop.sh $Target $mode"
  }
  'restart' {
    Invoke-AeRestart -Server $Target
  }
  'deploy-items' {
    Push-Location $ItemsRoot
    try {
      Write-Host 'mvn package...'
      & mvn -q -DskipTests package
      if ($LASTEXITCODE -ne 0) { throw "mvn failed ($LASTEXITCODE)" }
      $built = Join-Path $ItemsRoot 'target\AetherionItems-1.0.0.jar'
      if (-not (Test-Path $built)) { throw "missing $built" }
      Write-Host ("local jar {0} bytes {1}" -f (Get-Item $built).Length, (Get-Item $built).LastWriteTime)
      $doRestart = -not $NoRestart
      Invoke-AeDeployJar -LocalJar $built -DestName 'AetherionItems-1.0.0.jar' -Server 'mmor' -DoRestart $doRestart
    } finally { Pop-Location }
  }
  'deploy-hub' {
    Push-Location $HubRoot
    try {
      Write-Host 'mvn package (Hub)...'
      & mvn -q -DskipTests package
      if ($LASTEXITCODE -ne 0) { throw "mvn failed ($LASTEXITCODE)" }
      $built = Get-ChildItem (Join-Path $HubRoot 'target') -Filter 'AetherionHub*.jar' |
        Where-Object { $_.Name -notmatch 'original|sources|javadoc' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
      if (-not $built) { throw 'missing Hub jar in target/' }
      $doRestart = -not $NoRestart
      Invoke-AeDeployJar -LocalJar $built.FullName -DestName $built.Name -Server 'mmor' -DoRestart $doRestart
    } finally { Pop-Location }
  }
  'deploy-jar' {
    if (-not $Jar) { throw 'deploy-jar requires -Jar path' }
    $name = if ($DestName) { $DestName } else { Split-Path $Jar -Leaf }
    $doRestart = -not $NoRestart
    Invoke-AeDeployJar -LocalJar $Jar -DestName $name -Server $Target -DoRestart $doRestart
  }
}
