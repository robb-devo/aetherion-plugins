<#
.SYNOPSIS
  Feature-surface diff between two plugin jars (additive-ship check).

.DESCRIPTION
  Lists every class, resource, plugin.yml command and config.yml top-level key that exists in the
  OLD jar but not in the NEW one (MISSING = would be lost on ship) and what the NEW jar adds.
  Exit code 1 when anything is MISSING, so it can gate a deploy script.

.EXAMPLE
  .\jar-surface-diff.ps1 -Old .\live\AetherionMining.jar -New .\AetherionMining\target\AetherionMining-1.0.0.jar
#>
param(
    [Parameter(Mandatory = $true)][string]$Old,
    [Parameter(Mandatory = $true)][string]$New
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Read-Surface([string]$path) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $path))
    try {
        $classes = New-Object System.Collections.Generic.HashSet[string]
        $resources = New-Object System.Collections.Generic.HashSet[string]
        $commands = New-Object System.Collections.Generic.HashSet[string]
        $configKeys = New-Object System.Collections.Generic.HashSet[string]
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            if ($entry.FullName.EndsWith('.class')) {
                [void]$classes.Add(($entry.FullName -replace '\.class$', '' -replace '/', '.'))
            } elseif (-not $entry.FullName.StartsWith('META-INF/')) {
                [void]$resources.Add($entry.FullName)
            }
            if ($entry.FullName -eq 'plugin.yml' -or $entry.FullName -eq 'config.yml') {
                $reader = New-Object System.IO.StreamReader($entry.Open())
                $text = $reader.ReadToEnd()
                $reader.Close()
                if ($entry.FullName -eq 'plugin.yml') {
                    $inCommands = $false
                    foreach ($line in ($text -split "`r?`n")) {
                        if ($line -match '^commands:\s*$') { $inCommands = $true; continue }
                        if ($inCommands -and $line -match '^\S') { $inCommands = $false }
                        if ($inCommands -and $line -match '^  ([A-Za-z0-9_-]+):') { [void]$commands.Add($Matches[1]) }
                    }
                } else {
                    foreach ($line in ($text -split "`r?`n")) {
                        if ($line -match '^([A-Za-z0-9_-]+):') { [void]$configKeys.Add($Matches[1]) }
                    }
                }
            }
        }
        return @{ classes = $classes; resources = $resources; commands = $commands; config = $configKeys }
    } finally {
        $zip.Dispose()
    }
}

$a = Read-Surface $Old
$b = Read-Surface $New
$missingTotal = 0
foreach ($kind in 'classes', 'commands', 'config', 'resources') {
    $missing = @($a[$kind] | Where-Object { -not $b[$kind].Contains($_) } | Sort-Object)
    $added = @($b[$kind] | Where-Object { -not $a[$kind].Contains($_) } | Sort-Object)
    # Inner/anonymous classes are reported with their outer class; top-level misses matter most.
    Write-Host ("== {0}: {1} in old, {2} in new, MISSING {3}, added {4}" -f $kind, $a[$kind].Count, $b[$kind].Count, $missing.Count, $added.Count)
    foreach ($m in $missing) { Write-Host ("   MISSING  {0}" -f $m) -ForegroundColor Red }
    if ($added.Count -le 60) { foreach ($n in $added) { Write-Host ("   added    {0}" -f $n) -ForegroundColor DarkGreen } }
    $missingTotal += $missing.Count
}
if ($missingTotal -gt 0) {
    Write-Host "`nNOT SAFE TO SHIP: $missingTotal surface item(s) would disappear. Restore them first." -ForegroundColor Red
    exit 1
}
Write-Host "`nOK: new jar keeps the whole old surface." -ForegroundColor Green
exit 0
