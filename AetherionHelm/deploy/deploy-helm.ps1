<#
  Aetherion Helm: puts the staff panel live on the Hetzner host.

  Run it from any folder:
      powershell -ExecutionPolicy Bypass -File .\deploy-helm.ps1
      powershell -ExecutionPolicy Bypass -File .\deploy-helm.ps1 -CloudflareToken <token>

  What it does, in order. It asks before anything that players would notice.
    1. Checks ssh (aetherion-hetzner), scp and ae.ps1.
    2. Downloads the latest "aetherion-helm" release from GitHub (gh if logged in, else your git login),
       or takes the files from -AssetsDir.
    3. Creates passwords for Robb and Shuminite (only used if the accounts do not exist yet).
    4. Uploads everything to a staging folder and runs install.sh there: panel service, web app, agent plugins
       on all six servers (one jar each), accounts, and the Cloudflare tunnel for panel.donnernet.de
       (with -CloudflareToken, or after a yes it prints a Cloudflare login link). The main site is not touched.
    5. Shows the passwords once (they are not written to the log).        -- asks: restart now? --
    6. One graceful restart of every server so the agents load, the proxy last.
  The log is in IdeaProjects\_deploy\helm-<time>\deploy.log.

  No passwords, hosts or keys in here: ssh uses your existing alias, the installer reads the server folders on the host.
#>
param(
    [string]$CloudflareToken = '',
    [string]$AssetsDir = '',
    [string]$PanelHost = 'panel.donnernet.de',
    [switch]$NoRestart
)
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Repo    = 'robb-devo/aetherion-plugins'
$Tag     = 'aetherion-helm'
$Ssh     = 'aetherion-hetzner'
$Ops     = Join-Path $env:USERPROFILE 'IdeaProjects\aetherion-ops'
$AePs1   = Join-Path $Ops 'bin\ae.ps1'
$Work    = Join-Path $env:USERPROFILE 'IdeaProjects\_deploy'
$Ts      = Get-Date -Format 'yyyyMMdd-HHmmss'
$Out     = Join-Path $Work "helm-$Ts"
$Files   = Join-Path $Out 'files'
$Stage   = "/root/aetherion-ops/staging/helm-$Ts"
$Need    = @('helm-gateway.jar', 'AetherionHelm.jar', 'AetherionHelmProxy.jar', 'helm-web.zip', 'install.sh', 'helm-ctl', 'aetherion-helm.service')
$Accounts = @(
    @{ User = 'robb';      Display = 'Robb';      Role = 'owner';   Mc = 'A3therion' },
    @{ User = 'shuminite'; Display = 'Shuminite'; Role = 'coowner'; Mc = 'Shuminite' }
)
# backends first, the proxy last: its restart is the one players notice
$RestartOrder = @('hub', 'mmor', 'mmod', 'mmoc', 'mmoi', 'proxy')

New-Item -ItemType Directory -Force -Path $Files | Out-Null
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
    $a = Read-Host "$q [y/N]"
    return ($a -match '^(y|yes|j|ja)$')
}
function Ae([string[]]$a) {
    # ae.ps1 in its own PowerShell and its own folder, like you run it by hand.
    Push-Location (Split-Path $AePs1)
    try { & powershell -NoProfile -ExecutionPolicy Bypass -File $AePs1 @a | Out-Host } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { Write-Host "ae.ps1 $($a -join ' ') exited $LASTEXITCODE" -ForegroundColor Yellow }
}
function NoStderrStop([scriptblock]$sb) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $sb } finally { $ErrorActionPreference = $old }
}
function NewPassword() {
    # 4 groups of 4 from an alphabet without look-alikes (no 0/O, 1/l/I), e.g. Kx7m-Pq3v-Rt9w-Hb2n
    $abc = 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 64
    $rng.GetBytes($bytes)
    $chars = for ($i = 0; $i -lt 16; $i++) { $abc[$bytes[$i] % $abc.Length] }
    $s = -join $chars
    return '{0}-{1}-{2}-{3}' -f $s.Substring(0, 4), $s.Substring(4, 4), $s.Substring(8, 4), $s.Substring(12, 4)
}
function WriteLf([string]$path, [string[]]$lines) {
    # LF and no BOM, the server reads these with bash
    [IO.File]::WriteAllText($path, (($lines -join "`n") + "`n"), (New-Object Text.UTF8Encoding($false)))
}

# ------------------------------------------------------------------------------------------------ 1. tools
Step '1/6  Checking tools'
foreach ($t in @('ssh', 'scp')) { if (-not (Get-Command $t -ErrorAction SilentlyContinue)) { Die "$t is not on PATH (Windows: Settings > Optional features > OpenSSH Client)" } }
& ssh -o BatchMode=yes -o ConnectTimeout=20 $Ssh true
Check "ssh $Ssh"
$haveAe = Test-Path $AePs1
if (-not $haveAe) { Write-Host "note: ae.ps1 not found at $AePs1, the restart step will be skipped" -ForegroundColor Yellow }
Write-Host "ok: ssh $Ssh, scp"

# ------------------------------------------------------------------------------------------------ 2. release files
Step "2/6  Release '$Tag' from GitHub"
if ($AssetsDir) {
    foreach ($f in $Need + @('aetherion-helm.apk')) {
        $p = Join-Path $AssetsDir $f
        if (Test-Path $p) { Copy-Item $p $Files }
    }
    Write-Host "taken from $AssetsDir"
} else {
    $viaGh = $false
    if (Get-Command gh -ErrorAction SilentlyContinue) {
        NoStderrStop { & gh auth status *> $null }
        if ($LASTEXITCODE -eq 0) {
            NoStderrStop { & gh release download $Tag -R $Repo -D $Files --clobber | Out-Host }
            $viaGh = ($LASTEXITCODE -eq 0)
        }
    }
    if (-not $viaGh) {
        # the GitHub login git already uses (Git Credential Manager), read without saving it anywhere
        $env:GIT_TERMINAL_PROMPT = '0'
        $cred = NoStderrStop { "protocol=https`nhost=github.com`n`n" | & git credential fill 2>$null }
        $token = ($cred | Where-Object { $_ -like 'password=*' } | Select-Object -First 1)
        if (-not $token) { Die 'no GitHub login found (log in with "gh auth login", or pass -AssetsDir with the release files)' }
        $token = $token.Substring(9)

        Add-Type -AssemblyName System.Net.Http
        $handler = New-Object System.Net.Http.HttpClientHandler
        $handler.AllowAutoRedirect = $false
        $http = New-Object System.Net.Http.HttpClient($handler)
        $http.Timeout = [TimeSpan]::FromMinutes(10)
        $http.DefaultRequestHeaders.UserAgent.ParseAdd('aetherion-helm-deploy')
        function Get-Api([string]$url, [string]$accept) {
            $req = New-Object System.Net.Http.HttpRequestMessage('GET', $url)
            $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token)
            $req.Headers.Accept.ParseAdd($accept)
            return $http.SendAsync($req).GetAwaiter().GetResult()
        }
        $r = Get-Api "https://api.github.com/repos/$Repo/releases/tags/$Tag" 'application/vnd.github+json'
        if (-not $r.IsSuccessStatusCode) { Die "release '$Tag' not found on GitHub ($([int]$r.StatusCode)); is the build finished?" }
        $release = $r.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
        foreach ($asset in $release.assets) {
            if ($asset.name -eq 'deploy-helm.ps1') { continue }
            $r = Get-Api $asset.url 'application/octet-stream'
            if ([int]$r.StatusCode -ge 300 -and [int]$r.StatusCode -lt 400) {
                # the file itself sits behind a signed link that must be fetched without the GitHub token
                $r = (New-Object System.Net.Http.HttpClient).GetAsync($r.Headers.Location).GetAwaiter().GetResult()
            }
            if (-not $r.IsSuccessStatusCode) { Die "download of $($asset.name) failed ($([int]$r.StatusCode))" }
            [IO.File]::WriteAllBytes((Join-Path $Files $asset.name), $r.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult())
            Write-Host ("  {0,-26} {1,8:N1} MB" -f $asset.name, ($asset.size / 1MB))
        }
        Write-Host "release: $($release.name)"
    }
}
foreach ($f in $Need) { if (-not (Test-Path (Join-Path $Files $f))) { Die "$f is missing in the release files" } }

# ------------------------------------------------------------------------------------------------ 3. accounts
Step '3/6  Accounts'
$passwords = @{}
$lines = foreach ($a in $Accounts) {
    $pw = NewPassword
    $passwords[$a.User] = $pw
    '{0}|{1}|{2}|{3}|{4}' -f $a.User, $a.Display, $a.Role, $a.Mc, $pw
}
$accountsFile = Join-Path $Files 'accounts.txt'
WriteLf $accountsFile $lines
Write-Host "robb (Owner) and shuminite (Co-Owner); existing accounts keep their password"

$installArgs = "--host $PanelHost"
if ($CloudflareToken) {
    WriteLf (Join-Path $Files 'cf.token') @($CloudflareToken.Trim())
    $installArgs += " --cf-token-file $Stage/cf.token"
} else {
    Write-Host ''
    Write-Host "Cloudflare: the panel needs $PanelHost. The installer can set that up now: it prints a login link," -ForegroundColor Yellow
    Write-Host 'you open it, log in at Cloudflare and pick donnernet.de. Only that one subdomain is created.' -ForegroundColor Yellow
    if (Ask 'Set up the Cloudflare tunnel now?') { $installArgs += ' --tunnel' }
}

# ------------------------------------------------------------------------------------------------ 4. upload + install
Step '4/6  Upload and install on the server'
& ssh $Ssh "mkdir -p $Stage && chmod 700 $Stage"
Check 'create staging folder'
$upload = Get-ChildItem $Files -File | ForEach-Object { $_.FullName }
& scp -q @upload "${Ssh}:$Stage/"
$scpExit = $LASTEXITCODE
# local copies of secrets go right away, uploaded or not
Remove-Item $accountsFile -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $Files 'cf.token') -Force -ErrorAction SilentlyContinue
if ($scpExit -ne 0) { Die "upload failed (exit $scpExit)" }

& ssh -t $Ssh "sed -i 's/\r`$//' $Stage/install.sh $Stage/helm-ctl && bash $Stage/install.sh $installArgs"
$installExit = $LASTEXITCODE
$result = NoStderrStop { & ssh $Ssh "cat $Stage/result.txt 2>/dev/null; rm -f $Stage/accounts.txt $Stage/cf.token" }
if ($installExit -ne 0) { Die "install.sh failed (exit $installExit), see the output above" }
if (-not ($result -match '^HEALTH ok')) { Die 'the panel service did not answer after the install' }

# ------------------------------------------------------------------------------------------------ 5. passwords
Step '5/6  Logins'
$created = @($result | Where-Object { $_ -match '^ACCOUNT created ' } | ForEach-Object { ($_ -split ' ')[2] })
$failed = @($result | Where-Object { $_ -match '^ACCOUNT failed ' } | ForEach-Object { ($_ -split ' ')[2] })
foreach ($u in $failed) { Write-Host "account $u could not be created, see the installer output" -ForegroundColor Red }
if ($created.Count -gt 0) {
    # not in the log: the transcript pauses while the passwords are on screen
    Stop-Transcript | Out-Null
    Write-Host ''
    Write-Host "  https://$PanelHost" -ForegroundColor Green
    foreach ($u in $created) { Write-Host ('  {0,-10} {1}' -f $u, $passwords[$u]) -ForegroundColor Green }
    Write-Host ''
    Write-Host '  Shown only this once. Note them now; Shuminite gets hers from you.'
    Write-Host '  Both set up 2FA at the first login (any authenticator app).'
    Read-Host '  Press Enter once you have noted them' | Out-Null
    Clear-Host
    Start-Transcript -Path (Join-Path $Out 'deploy.log') -Append | Out-Null
    Write-Host "passwords shown for: $($created -join ', ')"
} else {
    Write-Host 'accounts already existed, passwords unchanged'
}
$passwords.Clear()

$tunnel = ($result | Where-Object { $_ -match '^TUNNEL ' }) -join ' '
if ($tunnel -match 'foreign') {
    Write-Host "Cloudflare: a tunnel that is not the panel's already runs on the server and was left alone." -ForegroundColor Yellow
    Write-Host "  In the Cloudflare dashboard (Zero Trust > Networks > Tunnels > that tunnel > Public hostnames) add: $PanelHost -> http://localhost:8787" -ForegroundColor Yellow
} elseif ($tunnel -match 'active') {
    Write-Host "Cloudflare tunnel runs; $PanelHost is usually reachable within a minute."
} else {
    Write-Host "Cloudflare tunnel not set up yet: run this script again with -CloudflareToken, or answer yes at the tunnel question." -ForegroundColor Yellow
}

# ------------------------------------------------------------------------------------------------ 6. restart
Step '6/6  Restart so the agents load'
if ($NoRestart -or -not $haveAe) {
    Write-Host 'skipped; the agents load at the next restart of each server.'
} elseif (Ask 'Restart all servers now (graceful countdown each, the proxy last)?') {
    foreach ($s in $RestartOrder) { Ae @('restart', $s) }
    Ae @('status')
} else {
    Write-Host 'skipped; the agents load at the next restart of each server.'
}

Write-Host ''
Write-Host "Done. Panel: https://$PanelHost   App: Settings > App in the panel, or the APK from the GitHub release." -ForegroundColor Green
Write-Host "Log: $Out\deploy.log"
Stop-Transcript | Out-Null
