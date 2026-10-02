<#
.SYNOPSIS
  Lands the second Prop Wand pass (22 new schematics + PropCatalog entries) into a Hub checkout, guarded.

.DESCRIPTION
  Writes ONLY these allowlisted paths under <checkout>\AetherionHub\src\main\:
    java\de\aetherion\hub\prop\PropCatalog.java      (22 entries appended, javadoc count 18 -> 40)
    resources\props\<22 new ids>.schem                (new files)
  Nothing else is ever written. The 18 existing schems, PropWand.java and PropWandListener.java are
  hash-checked (report only) and never touched.

  Guard, per file (same rules as the previous ships):
    * PropCatalog.java is only replaced if it is EXACTLY the 18-prop version this was built against:
      raw SHA-256 or SHA-256 after CRLF -> LF. The file keeps its line-ending style.
    * A new .schem is only written if the path does not exist yet.
    * A file that already equals the new version is skipped, so re-running is safe.
    * Anything else is a CONFLICT and left alone (-Force overwrites and keeps a .bak-moreprops copy).
    * All-or-nothing per checkout: if anything conflicts, nothing is written to that checkout.

  Default target: the live-lineage worktree (.claude\worktrees\mining-eldervale-progression-65660c), the
  same default as the Main Island ship. -AlsoMainCheckout lands the same files in IdeaProjects\AetherionHub
  too (both lineages carry the identical 18-prop catalog). -Worktree points somewhere else.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\apply-more-props.ps1 -DryRun
  powershell -ExecutionPolicy Bypass -File .\apply-more-props.ps1
  powershell -ExecutionPolicy Bypass -File .\apply-more-props.ps1 -AlsoMainCheckout -DryRun
  powershell -ExecutionPolicy Bypass -File .\apply-more-props.ps1 -Compile     # apply, then mvn compile the Hub
#>
param(
    [string]$Worktree = (Join-Path $PSScriptRoot '..\.claude\worktrees\mining-eldervale-progression-65660c'),
    [switch]$AlsoMainCheckout,
    [switch]$DryRun,
    [switch]$Force,
    [switch]$Compile
)

$ErrorActionPreference = 'Stop'
$sha = [System.Security.Cryptography.SHA256]::Create()
$latin1 = [System.Text.Encoding]::GetEncoding(28591)
$sep = [string][System.IO.Path]::DirectorySeparatorChar

# Hard allowlist. A manifest line that tries to write anything else is refused.
$ids = @(
    'ae_prop_fallen_log', 'ae_prop_mossy_boulders', 'ae_prop_lobster_pots', 'ae_prop_ore_carts', 'ae_prop_signpost',
    'ae_prop_flower_cart', 'ae_prop_overlook_bench', 'ae_prop_timber_stack', 'ae_prop_quay_capstan',
    'ae_prop_channel_marker', 'ae_prop_anchor_monument', 'ae_fishing_pier', 'ae_forest_standing_stones',
    'ae_harbour_lighthouse', 'ae_harbour_warehouse', 'ae_moored_sloop', 'ae_fishing_net_loft', 'ae_mine_tipple',
    'ae_mine_smelter', 'ae_forest_treehouse', 'ae_edge_windmill', 'ae_edge_broken_bridge')
$allowed = New-Object 'System.Collections.Generic.HashSet[string]'
[void]$allowed.Add('AetherionHub/src/main/java/de/aetherion/hub/prop/PropCatalog.java')
foreach ($id in $ids) { [void]$allowed.Add("AetherionHub/src/main/resources/props/$id.schem") }

function Get-Hashes([byte[]]$bytes, [bool]$text) {
    $raw = ([BitConverter]::ToString($sha.ComputeHash($bytes)) -replace '-', '').ToLower()
    if (-not $text) { return @($raw, $raw) }
    $normal = $latin1.GetBytes($latin1.GetString($bytes).Replace("`r`n", "`n"))
    $lf = ([BitConverter]::ToString($sha.ComputeHash($normal)) -replace '-', '').ToLower()
    return @($raw, $lf)
}

$rows = @()
foreach ($line in Get-Content -LiteralPath (Join-Path $PSScriptRoot 'MANIFEST.tsv')) {
    if ($line.StartsWith('#') -or [string]::IsNullOrWhiteSpace($line)) { continue }
    $c = $line -split "`t"
    $rows += [pscustomobject]@{ Rel = $c[0]; Src = $c[1]; BaseRaw = $c[2]; BaseLf = $c[3]; NewRaw = $c[4]; NewLf = $c[5]
                                Text = ($c[6] -eq 'text'); Action = $c[7] }
}

$targets = @()
if (Test-Path -LiteralPath $Worktree) { $targets += (Resolve-Path -LiteralPath $Worktree).Path }
else { Write-Host "Worktree not found: $Worktree" -ForegroundColor Red }
if ($AlsoMainCheckout) { $targets += (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path }
if ($targets.Count -eq 0) { exit 1 }

Write-Host "More Props ship (22 new Prop Wand schematics)" -ForegroundColor Cyan
if ($DryRun) { Write-Host "(dry run: nothing is written)" -ForegroundColor Yellow }

$totalConflicts = 0
foreach ($root in $targets) {
    Write-Host ""
    Write-Host "-> $root" -ForegroundColor Cyan
    if (-not (Test-Path -LiteralPath (Join-Path (Join-Path $root 'AetherionHub') 'pom.xml'))) {
        Write-Host "  no AetherionHub module here - skipped" -ForegroundColor Red; $totalConflicts++; continue
    }
    $same = 0; $conflicts = 0; $warn = 0; $plan = @()
    foreach ($r in $rows) {
        if ($r.Rel.Contains('..')) { Write-Host "  REFUSED  $($r.Rel)" -ForegroundColor Red; $conflicts++; continue }
        $dst = Join-Path $root ($r.Rel -replace '/', $sep)
        $exists = Test-Path -LiteralPath $dst
        if ($r.Action -eq 'keep') {
            if (-not $exists) { Write-Host "  note     $($r.Rel) is missing (existing prop file; not touched)" -ForegroundColor Yellow; $warn++; continue }
            $cur = Get-Hashes ([System.IO.File]::ReadAllBytes($dst)) $r.Text
            if (-not ($cur[0] -eq $r.BaseRaw -or $cur[1] -eq $r.BaseLf)) {
                Write-Host "  note     $($r.Rel) differs from the known original (not touched)" -ForegroundColor Yellow; $warn++
            }
            continue
        }
        if (-not $allowed.Contains($r.Rel)) { Write-Host "  NOT ALLOWLISTED  $($r.Rel) - refused" -ForegroundColor Red; $conflicts++; continue }
        $src = Join-Path $PSScriptRoot ($r.Src -replace '/', $sep)
        if (-not (Test-Path -LiteralPath $src)) { Write-Host "  MISSING IN SHIP  $($r.Src)" -ForegroundColor Red; $conflicts++; continue }
        $srcBytes = [System.IO.File]::ReadAllBytes($src)
        $shipH = Get-Hashes $srcBytes $r.Text
        if ($shipH[0] -ne $r.NewRaw) { Write-Host "  SHIP FILE CHANGED  $($r.Src) (manifest mismatch)" -ForegroundColor Red; $conflicts++; continue }
        $action = $null
        $outBytes = $srcBytes
        if ($exists) {
            $curBytes = [System.IO.File]::ReadAllBytes($dst)
            $cur = Get-Hashes $curBytes $r.Text
            if ($cur[0] -eq $r.NewRaw -or $cur[1] -eq $r.NewLf) { $same++; continue }
            if ($r.BaseRaw -ne 'NEW' -and ($cur[0] -eq $r.BaseRaw -or $cur[1] -eq $r.BaseLf)) {
                $action = 'update'
                # keep the file's own line endings (ship copy is CRLF)
                if ($r.Text -and -not $latin1.GetString($curBytes).Contains("`r`n")) {
                    $outBytes = $latin1.GetBytes($latin1.GetString($srcBytes).Replace("`r`n", "`n"))
                }
            }
        } elseif ($r.BaseRaw -eq 'NEW') {
            $action = 'new'
        }
        if (-not $action) {
            if ($Force) { $action = 'FORCED' } else {
                $why = if ($exists) { 'changed on disk since this build' } else { 'expected to exist (no Prop Wand in this checkout?)' }
                Write-Host "  CONFLICT  $($r.Rel)  ($why; left untouched)" -ForegroundColor Red
                $conflicts++; continue
            }
        }
        Write-Host ("  {0,-8} {1}" -f $action, $r.Rel)
        $plan += [pscustomobject]@{ Row = $r; Dst = $dst; Bytes = $outBytes; Action = $action; Exists = $exists }
    }
    # All-or-nothing per checkout: with any conflict, nothing is written there.
    if ($conflicts -gt 0) {
        Write-Host "  conflicts found - nothing written to this checkout" -ForegroundColor Red
    } elseif (-not $DryRun) {
        foreach ($p in $plan) {
            $dir = Split-Path $p.Dst -Parent
            if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
            if ($p.Action -eq 'FORCED' -and $p.Exists) { Copy-Item -LiteralPath $p.Dst "$($p.Dst).bak-moreprops" -Force }
            [System.IO.File]::WriteAllBytes($p.Dst, $p.Bytes)
            $after = Get-Hashes ([System.IO.File]::ReadAllBytes($p.Dst)) $p.Row.Text
            if (-not ($after[0] -eq $p.Row.NewRaw -or $after[1] -eq $p.Row.NewLf)) {
                Write-Host "  VERIFY FAILED after write: $($p.Row.Rel)" -ForegroundColor Red; $conflicts++
            }
        }
    }
    $written = if ($conflicts -gt 0 -and -not $DryRun) { 0 } else { $plan.Count }
    Write-Host ("  {0} file(s) {1}, {2} already up to date, {3} conflict(s), {4} note(s)." -f $written,
        $(if ($DryRun) { 'would be written' } else { 'written' }), $same, $conflicts, $warn)
    $totalConflicts += $conflicts

    if ($Compile -and -not $DryRun -and $conflicts -eq 0) {
        if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
            Write-Host "  -Compile: mvn not on PATH. Run in $root :  mvn -q -DskipTests -pl AetherionHub -am compile" -ForegroundColor Yellow
        } else {
            Write-Host "  mvn -q -DskipTests -pl AetherionHub -am compile" -ForegroundColor Cyan
            Push-Location $root
            try { & mvn -q -DskipTests -pl AetherionHub -am compile; $code = $LASTEXITCODE } finally { Pop-Location }
            if ($code -eq 0) { Write-Host "  compile OK" -ForegroundColor Green } else { Write-Host "  compile FAILED (exit $code)" -ForegroundColor Red; $totalConflicts++ }
        }
    }
}

Write-Host ""
if ($totalConflicts -gt 0) {
    Write-Host "Something was not written or failed - see above. Nothing outside the allowlist was touched." -ForegroundColor Yellow
    exit 1
}
Write-Host "Done. Next: build the Hub jar as usual, restart, then /propwand extract (new schems extract automatically; existing files are never overwritten)." -ForegroundColor Green
exit 0
