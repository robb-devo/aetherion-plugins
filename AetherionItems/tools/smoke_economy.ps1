$ErrorActionPreference = "Stop"
$yml = Get-Content "C:\Users\Robbi\IdeaProjects\AetherionItems\src\main\resources\economy.yml" -Raw
function Val([string]$id) {
  $pattern = "(?m)^\s+$([regex]::Escape($id)): (\d+)"
  $m = [regex]::Match($yml, $pattern)
  if ($m.Success) { return [long]$m.Groups[1].Value }
  return -1
}
$samples = @(
  "compressed_cobblestone","compressed_raw_iron","compacted_diamond","refined_wheat",
  "combat_helmet","combat_helmet_3","combat_helmet_5","farming_helmet_5",
  "charm_combat","charm_combat_3","coal_booster","compacted_diamond_chestplate",
  "aetherblade","aetherion_helmet","god_sword"
)
Write-Host "=== Listed / Buyback 32% / Silas x8 ==="
foreach ($id in $samples) {
  $v = Val $id
  $bb = [Math]::Max(1, [Math]::Round($v * 0.32))
  $fence = $v * 8
  Write-Host ("{0,-32} listed={1,12}  buyback={2,12}  silas={3,14}" -f $id, $v, $bb, $fence)
}
$h1 = Val "combat_helmet"; $h5 = Val "combat_helmet_5"
Write-Host ""
Write-Host "Combat helm T1=$h1 T5=$h5 ascending=$($h1 -lt $h5)"
$mats = (Val "compacted_diamond") * 8
$chest = Val "compacted_diamond_chestplate"
Write-Host "Diamond chest mats=$mats listed=$chest ratio=$([Math]::Round($chest / $mats, 2))"
$iron = Val "compressed_raw_iron"
Write-Host "Iron compressed=$iron (expect 538, was buggy 180)"
$farm = Val "compressed_wheat"
Write-Host "Wheat compressed=$farm (same formula as mining unit1=179)"
Write-Host "Aetherion helm=$(Val 'aetherion_helmet') above combat T5=$h5 ? $((Val 'aetherion_helmet') -gt $h5)"
Write-Host "God sword=$(Val 'god_sword') above aetherion chest=$(Val 'aetherion_chestplate') ? $((Val 'god_sword') -gt (Val 'aetherion_chestplate'))"
