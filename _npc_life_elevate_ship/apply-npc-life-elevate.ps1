param(
  [string]$Root = "$env:USERPROFILE\IdeaProjects\.claude\worktrees\dialog-voice-german-localization-651358",
  [switch]$Force
)
# Copies the NPC life elevate into a repo root. Refuses if the targets are not the expected base (use -Force to override).
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$files = @{
  'AetherionQuests\src\main\java\de\aetherion\quests\npc\LivingNpcLife.java' = 'f19538a42b00b64694abbfb1e88f68b0a5b5aca352f8b76a1191efe9f7cb299e'
  'AetherionQuests\src\main\resources\config.yml'                           = 'bed0912cd778e85ede51e2a7050c613e2e5eec4539b9062c5ca67eb399e361ed'
}
foreach ($rel in $files.Keys) {
  $dst = Join-Path $Root $rel
  if (-not (Test-Path $dst)) { throw "Missing target: $dst" }
  $h = (Get-FileHash $dst -Algorithm SHA256).Hash.ToLower()
  if ($h -ne $files[$rel] -and -not $Force) { throw "Base mismatch for $rel (got $h). Re-run with -Force if you know it's fine." }
}
foreach ($rel in $files.Keys) {
  $src = Join-Path $here ("files\" + $rel)
  $dst = Join-Path $Root $rel
  Copy-Item $dst "$dst.pre-life-elevate.bak" -Force
  Copy-Item $src $dst -Force
  Write-Host "applied $rel"
}
Write-Host "Done. Build: mvn -pl AetherionQuests -am package   (not deployed)"
