<#
  Aetherion: the exile map alone in the void, a smooth arrival ride, the weapon tempo shown on every weapon.

  Run it once from the checkout (no hosts or ids in here: ssh alias and the server-side ops folder are parameters,
  server folders come from your aetherion-ops\docs\SERVERS.md):
      powershell -ExecutionPolicy Bypass -File .\tools\deploy\deploy-exile-void.ps1 -Ssh <alias> -RemoteOps <folder> -Yes

  What it does, in order:
    1. Checks ssh, git, Maven, Java 21.
    2. Fresh checkout of GitHub main in IdeaProjects\_deploy (must contain the exile void merge), builds Core, Items, Quests.
    3. Uploads jars, remote.py and exilevoid.py to a staging folder.
       -- go live? --
    4. Stops all five backends. Swaps the jars where a server already runs them (backups kept, one jar per plugin):
         AetherionCore   -> hub, mmo-r, mmo-d, mmo-c, mmo-i
         AetherionItems  -> mmo-r, mmo-d, mmo-c, mmo-i
         AetherionQuests -> mmo-r, mmo-d, mmo-c, mmo-i
       mmo-c: full copy of the exile region files, then every chunk outside the map (exil_gesamt, x -160..199,
       z -190..195) goes and the old bedrock/deepslate slab under it (y -64..-34) turns to air.
    5. Starts all five. The proxy is not touched.
    6. Waits until all five answer, prints boot errors and chunk errors on mmo-c.
    7. ae.ps1 status.
#>
param([switch]$Yes, [Parameter(Mandatory = $true)][string]$Ssh, [Parameter(Mandatory = $true)][string]$RemoteOps)
$ErrorActionPreference = 'Stop'

$Expect   = '8f44510903c4739993ad769e3ed36d217a67ef0a'                                  # exile void commit; GitHub main must contain it
$RepoUrl  = 'https://github.com/robb-devo/aetherion-plugins.git'
$Ops      = Join-Path $env:USERPROFILE 'IdeaProjects\aetherion-ops'
$AePs1    = Join-Path $Ops 'bin\ae.ps1'
$Work     = Join-Path $env:USERPROFILE 'IdeaProjects\_deploy'
$Src      = Join-Path $Work 'aetherion-plugins'
$Ts       = Get-Date -Format 'yyyyMMdd-HHmmss'
$Out      = Join-Path $Work "exile-void-$Ts"
$Stage    = "$RemoteOps/staging/exile-void-$Ts"
$Modules  = @('AetherionCore', 'AetherionItems', 'AetherionQuests')
$Backends = @('hub', 'mmor', 'mmod', 'mmoc', 'mmoi')
$Here     = Split-Path -Parent $MyInvocation.MyCommand.Path

New-Item -ItemType Directory -Force -Path (Join-Path $Out 'jars') | Out-Null
Start-Transcript -Path (Join-Path $Out 'deploy.log') | Out-Null

function Step([string]$t) { Write-Host ''; Write-Host "== $t" -ForegroundColor Cyan }
function Die([string]$t) {
    Write-Host "STOP: $t" -ForegroundColor Red
    Write-Host "Log: $Out\deploy.log"
    Stop-Transcript | Out-Null
    exit 1
}
function Check([string]$what) { if ($LASTEXITCODE -ne 0) { Die "$what failed (exit $LASTEXITCODE)" } }
function Ask([string]$q) {
    if ($Yes) { Write-Host "$q -> yes (-Yes)"; return $true }
    $a = Read-Host "$q [y/N]"
    return ($a -match '^(y|yes|j|ja)$')
}
function Remote([string[]]$a) {
    & ssh $Ssh python3 "$Stage/remote.py" @a | Out-Host
    Check "server step '$($a[0])'"
}
function RemoteSoft([string[]]$a) {
    # Once servers are down nothing may stop the script before they are started again: warn and go on.
    & ssh $Ssh python3 "$Stage/remote.py" @a | Out-Host
    if ($LASTEXITCODE -ne 0) { Write-Host "WARNING: server step '$($a[0])' exited $LASTEXITCODE (see above), going on" -ForegroundColor Yellow }
}
function Ae([string[]]$a) {
    # ae.ps1 in its own PowerShell and its own folder, like you run it by hand.
    Push-Location (Split-Path $AePs1)
    try { & powershell -NoProfile -ExecutionPolicy Bypass -File $AePs1 @a | Out-Host } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { Write-Host "ae.ps1 $($a -join ' ') exited $LASTEXITCODE" -ForegroundColor Yellow }
}
function Grab([string]$text, [string]$rx, [string]$what) {
    $m = [regex]::Match($text, $rx)
    if (-not $m.Success) { Die "could not read $what from aetherion-ops\docs" }
    return $m.Groups[1].Value
}
function NoStderrStop([scriptblock]$sb) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $sb } finally { $ErrorActionPreference = $old }
}

# ------------------------------------------------------------------------------------------------ 1. tools
Step '1/7  Checking tools'
if (-not (Test-Path $AePs1)) { Die "ae.ps1 not found at $AePs1" }
$serversMd = Get-Content (Join-Path $Ops 'docs\SERVERS.md') -Raw
$base = Grab $serversMd '\|\s*Servers\s*\|\s*`([^`<]+)<uuid>`' 'the servers folder'
$dirs = [ordered]@{
    hub  = $base + (Grab $serversMd '\|\s*Hub uuid\s*\|\s*`([0-9a-f-]{36})`' 'the hub id')
    mmor = $base + (Grab $serversMd '\|\s*MMO-R uuid\s*\|\s*`([0-9a-f-]{36})`' 'the mmo-r id')
    mmod = $base + (Grab $serversMd '\|\s*MMO-D uuid\s*\|\s*`([0-9a-f-]{36})`' 'the mmo-d id')
    mmoc = $base + (Grab $serversMd '\|\s*MMO-C uuid\s*\|\s*`([0-9a-f-]{36})`' 'the mmo-c id')
    mmoi = $base + (Grab $serversMd '\|\s*MMO-I uuid\s*\|\s*`([0-9a-f-]{36})`' 'the mmo-i id')
}

& ssh -o BatchMode=yes -o ConnectTimeout=20 $Ssh true
Check "ssh $Ssh"
foreach ($t in @('git', 'scp')) { if (-not (Get-Command $t -ErrorAction SilentlyContinue)) { Die "$t is not on PATH" } }

$Mvn = $null
foreach ($n in @('mvn', 'mvn.cmd')) { $c = Get-Command $n -ErrorAction SilentlyContinue; if ($c) { $Mvn = $c.Source; break } }
if (-not $Mvn) {
    $roots = @("$env:ProgramFiles\JetBrains", "$env:LOCALAPPDATA\Programs", "$env:LOCALAPPDATA\JetBrains\Toolbox\apps")
    $found = foreach ($r in $roots) {
        if (Test-Path $r) { Get-ChildItem $r -Recurse -Depth 7 -Filter mvn.cmd -ErrorAction SilentlyContinue | Where-Object { $_.FullName -like '*\maven3\bin\mvn.cmd' } }
    }
    $Mvn = ($found | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
if (-not $Mvn) { Die 'Maven not found (neither on PATH nor bundled with IntelliJ)' }

function JavaMajor([string]$javaExe) {
    $v = NoStderrStop { & $javaExe -version 2>&1 | Out-String }
    $m = [regex]::Match($v, 'version "(\d+)')
    if ($m.Success) { return [int]$m.Groups[1].Value } else { return 0 }
}
$javaOk = $false
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { $javaOk = (JavaMajor "$env:JAVA_HOME\bin\java.exe") -ge 21 }
elseif (Get-Command java -ErrorAction SilentlyContinue) { $javaOk = (JavaMajor 'java') -ge 21 }
if (-not $javaOk) {
    $jdk = Get-ChildItem "$env:USERPROFILE\.jdks", "$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
        Where-Object { (Test-Path "$($_.FullName)\bin\java.exe") -and ((JavaMajor "$($_.FullName)\bin\java.exe") -ge 21) } |
        Select-Object -First 1
    if (-not $jdk) { Die 'No JDK 21+ found (set JAVA_HOME to one)' }
    $env:JAVA_HOME = $jdk.FullName
}
Write-Host "ok: ssh, git, $Mvn, Java 21+"

# ------------------------------------------------------------------------------------------------ 2. code + build
Step '2/7  Checkout of GitHub main and build'
if (-not (Test-Path (Join-Path $Src '.git'))) {
    & git clone --quiet $RepoUrl $Src
    Check 'git clone'
}
& git -C $Src fetch --quiet origin main
Check 'git fetch'
foreach ($c in @($Expect)) {
    NoStderrStop { & git -C $Src cat-file -e "$c^{commit}" 2>$null }
    if ($LASTEXITCODE -ne 0) { Die "commit $c is not on GitHub yet" }
    & git -C $Src merge-base --is-ancestor $c origin/main
    if ($LASTEXITCODE -ne 0) { Die "GitHub main does not contain $c yet: merge the pull request first" }
}
$extra = & git -C $Src log --oneline "$Expect..origin/main"
if ($extra) {
    Write-Host 'main has newer commits on top of the exile void merge; they go live too:' -ForegroundColor Yellow
    $extra | ForEach-Object { Write-Host "  $_" }
    if (-not (Ask 'Deploy main with these commits?')) { Die 'stopped before building' }
}
& git -C $Src -c advice.detachedHead=false checkout --quiet --force origin/main
Check 'git checkout'
& git -C $Src clean -fdxq
$head = (& git -C $Src rev-parse --short=8 HEAD)
Write-Host "building main $head (a few minutes)..."
& $Mvn -B -q -f (Join-Path $Src 'pom.xml') '-Dmaven.test.skip=true' -pl ($Modules -join ',') -am package
Check 'Maven build'
foreach ($m in $Modules) {
    $jar = Get-ChildItem (Join-Path $Src "$m\target") -Filter *.jar |
        Where-Object { $_.Name -notmatch '^original-|-sources|-javadoc|-tests' } | Select-Object -First 1
    if (-not $jar) { Die "no jar built for $m" }
    Copy-Item $jar.FullName (Join-Path $Out 'jars')
    Write-Host "  $($jar.Name)"
}

# ------------------------------------------------------------------------------------------------ 3. upload
Step '3/7  Upload to the staging folder'
$envJson = [ordered]@{ ts = $Ts; ops = $RemoteOps; servers = $dirs } | ConvertTo-Json -Depth 4
[IO.File]::WriteAllText((Join-Path $Out 'env.json'), $envJson)
Copy-Item (Join-Path $Here 'remote-exile-void.py') (Join-Path $Out 'remote.py')
Copy-Item (Join-Path $Src 'tools\deploy\exilevoid.py') (Join-Path $Out 'exilevoid.py')
& ssh $Ssh "mkdir -p $Stage/jars"
Check 'staging folder'
& scp -q (Join-Path $Out 'env.json') (Join-Path $Out 'remote.py') (Join-Path $Out 'exilevoid.py') "${Ssh}:$Stage/"
Check 'upload'
& scp -q (Get-ChildItem (Join-Path $Out 'jars') -Filter *.jar | ForEach-Object { $_.FullName }) "${Ssh}:$Stage/jars/"
Check 'jar upload'
Remote @('check')

Write-Host ''
Write-Host 'Next: stop all five backends; jar swap (backups kept); exile into the void (full copy kept); start all five.' -ForegroundColor Yellow
if (-not (Ask 'Go live now?')) { Die 'stopped before touching the network (staging stays on the server)' }

# ------------------------------------------------------------------------------------------------ 4. stop, jars, void
Step '4/7  Stop all five; jars; exile into the void'
foreach ($a in @('mmor', 'mmoi', 'mmod', 'mmoc', 'hub')) { Ae @('stop', $a) }
RemoteSoft @('jars')
RemoteSoft @('void')

# ------------------------------------------------------------------------------------------------ 5. start
Step '5/7  Start all five'
foreach ($a in @('hub', 'mmor', 'mmoi', 'mmod', 'mmoc')) { Ae @('start', $a) }

# ------------------------------------------------------------------------------------------------ 6. after boot
Step '6/7  Waiting for the five backends'
RemoteSoft @('wait', 'hub', 'mmor', 'mmoi', 'mmod', 'mmoc')
RemoteSoft @('after-boot')

# ------------------------------------------------------------------------------------------------ 7. status
Step '7/7  Status'
Ae @('status')
Write-Host ''
Write-Host "Done. main $head is live. Backups: $RemoteOps/backups/exile-void-$Ts (jars); exile: aetherion_exile.void2-backup-*." -ForegroundColor Green
Write-Host "Log: $Out\deploy.log"
Stop-Transcript | Out-Null
