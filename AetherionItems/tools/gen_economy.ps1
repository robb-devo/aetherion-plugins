# Generates Aetherion economy.yml from the documented curve.
$ErrorActionPreference = "Stop"
$out = "C:\Users\Robbi\IdeaProjects\AetherionItems\src\main\resources\economy.yml"

function RoundUp([double]$x) { [Math]::Max(1, [int][Math]::Round($x)) }
function Compressed([int]$unit) {
  $raw = [Math]::Max(1, $unit) * 128
  return [Math]::Max($raw + 1, (RoundUp ($raw * 1.4)))
}
function Compacted([int]$comp) {
  $packed = [Math]::Max(1, $comp) * 128
  return [Math]::Max($packed + 1, (RoundUp ($packed * 1.5)))
}
function Refined([int]$d) { [Math]::Max($d + 1, (RoundUp ($d * 4.0))) }
function Craft([long]$sum, [double]$m) { [Math]::Max(1, (RoundUp ($sum * $m))) }

$MARGIN = @{ 1 = 1.20; 2 = 1.30; 3 = 1.45; 4 = 1.65; 5 = 1.90 }
$CHARM = @{ 1 = 1.25; 2 = 1.45; 3 = 1.70 }
$BOOSTER = 1.35
$MATERIAL = 1.55

$VANILLA = [ordered]@{
  COBBLESTONE = 1; DIRT = 1; NETHERRACK = 1; GRAVEL = 1; SAND = 1
  OAK_LOG = 1; BIRCH_LOG = 1; SPRUCE_LOG = 1; OAK_PLANKS = 1; STICK = 1
  COAL = 2; RAW_IRON = 2; IRON_INGOT = 3; RAW_GOLD = 3; GOLD_INGOT = 4; GOLD_NUGGET = 1
  RAW_COPPER = 1; COPPER_INGOT = 1; REDSTONE = 1; LAPIS_LAZULI = 2
  DIAMOND = 8; EMERALD = 6; ANCIENT_DEBRIS = 10; NETHERITE_SCRAP = 12
  NETHERITE_INGOT = 25; NETHERITE_BLOCK = 225
  WHEAT = 1; CARROT = 1; POTATO = 1; SUGAR_CANE = 1; NETHER_WART = 2; HAY_BLOCK = 9; BREAD = 1
  ARROW = 1; BONE = 1; ROTTEN_FLESH = 1; ENDER_PEARL = 4; GUNPOWDER = 2; STRING = 1
  LEATHER = 2; FEATHER = 1; COD = 1; SALMON = 2; PUFFERFISH = 3; PRISMARINE_SHARD = 2
  AMETHYST_CLUSTER = 5; GLOWSTONE = 2; IRON_BOOTS = 12
  IRON_BLOCK = 27; GOLD_BLOCK = 36; DIAMOND_BLOCK = 72; EMERALD_BLOCK = 54
  COAL_BLOCK = 18; REDSTONE_BLOCK = 9; LAPIS_BLOCK = 18; COPPER_BLOCK = 9
}

$RESOURCES = [ordered]@{
  cobblestone = "COBBLESTONE"; coal = "COAL"; raw_iron = "IRON_INGOT"; raw_gold = "GOLD_INGOT"
  raw_copper = "COPPER_INGOT"; redstone = "REDSTONE"; lapis = "LAPIS_LAZULI"
  diamond = "DIAMOND"; emerald = "EMERALD"; oak_log = "OAK_LOG"; leather = "LEATHER"
  bone = "BONE"; string = "STRING"; wheat = "WHEAT"; carrot = "CARROT"; potato = "POTATO"
  gunpowder = "GUNPOWDER"; feather = "FEATHER"; rotten_flesh = "ROTTEN_FLESH"
  cod = "COD"; salmon = "SALMON"; pufferfish = "PUFFERFISH"; birch_log = "BIRCH_LOG"
  spruce_log = "SPRUCE_LOG"; sugar_cane = "SUGAR_CANE"; nether_wart = "NETHER_WART"
  prismarine_shard = "PRISMARINE_SHARD"
}
$REFINEABLE = @("wheat","carrot","potato")

$Comp = @{}; $Compd = @{}; $RefinedMap = @{}
foreach ($key in $RESOURCES.Keys) {
  $unit = [int]$VANILLA[$RESOURCES[$key]]
  $compVal = Compressed $unit
  $compdVal = Compacted $compVal
  $Comp[$key] = $compVal
  $Compd[$key] = $compdVal
  if ($REFINEABLE -contains $key) { $RefinedMap[$key] = Refined $compdVal }
}

function V([string]$m) { [int]$VANILLA[$m] }

$items = [ordered]@{}
foreach ($key in $RESOURCES.Keys) {
  $items["compressed_$key"] = $Comp[$key]
  $items["compacted_$key"] = $Compd[$key]
  if ($REFINEABLE -contains $key) { $items["refined_$key"] = $RefinedMap[$key] }
  $items["quarry_$key"] = 450
}
$items["quarry_core_shard"] = 180
$items["quarry_core"] = 1400
$items["quarry_compressor"] = 450
$items["quarry_compactor"] = 1400

# Combat armor
$combatT1 = @{ helmet = 5; chestplate = 8; leggings = 7; boots = 4 }
foreach ($p in $combatT1.Keys) {
  $items["combat_$p"] = Craft ([long]($combatT1[$p] * (V "COBBLESTONE"))) $MARGIN[1]
}
foreach ($p in $combatT1.Keys) {
  $items["combat_${p}_2"] = Craft ([long]($items["combat_$p"] + 8 * (V "IRON_INGOT"))) $MARGIN[2]
}
foreach ($p in $combatT1.Keys) {
  $items["combat_${p}_3"] = Craft ([long]($items["combat_${p}_2"] + 4 * $Comp["bone"] + 4 * $Comp["rotten_flesh"])) $MARGIN[3]
}
foreach ($p in $combatT1.Keys) {
  $items["combat_${p}_4"] = Craft ([long]($items["combat_${p}_3"] + 4 * $Compd["raw_gold"] + 4 * $Compd["bone"])) $MARGIN[4]
}
foreach ($p in $combatT1.Keys) {
  $items["combat_${p}_5"] = Craft ([long]($items["combat_${p}_4"] + 4 * $Compd["diamond"] + 4 * $Compd["emerald"])) $MARGIN[5]
}

$items["combat_sword"] = Craft ([long](2 * (V "COBBLESTONE") + (V "STICK"))) $MARGIN[1]
$items["combat_sword_2"] = Craft ([long]($items["combat_sword"] + 8 * (V "IRON_INGOT"))) $MARGIN[2]
$items["combat_sword_3"] = Craft ([long]($items["combat_sword_2"] + 4 * $Comp["bone"] + 4 * $Comp["rotten_flesh"])) $MARGIN[3]
$items["combat_sword_4"] = Craft ([long]($items["combat_sword_3"] + 4 * $Compd["raw_gold"] + 4 * $Compd["bone"])) $MARGIN[4]
$items["combat_sword_5"] = Craft ([long]($items["combat_sword_4"] + 4 * $Compd["diamond"] + 4 * $Compd["emerald"])) $MARGIN[5]

# Mining armor
$miningT1 = @{ helmet = 5; chestplate = 8; leggings = 7; boots = 4 }
foreach ($p in $miningT1.Keys) {
  $items["mining_$p"] = Craft ([long]($miningT1[$p] * (V "COPPER_INGOT"))) $MARGIN[1]
}
foreach ($p in $miningT1.Keys) {
  $items["mining_${p}_2"] = Craft ([long]($items["mining_$p"] + 8 * (V "IRON_INGOT"))) $MARGIN[2]
}
foreach ($p in $miningT1.Keys) {
  $items["mining_${p}_3"] = Craft ([long]($items["mining_${p}_2"] + 4 * $Comp["raw_iron"] + 4 * $Comp["coal"])) $MARGIN[3]
}
foreach ($p in $miningT1.Keys) {
  $items["mining_${p}_4"] = Craft ([long]($items["mining_${p}_3"] + 4 * $Compd["raw_copper"] + 4 * $Compd["coal"])) $MARGIN[4]
}
foreach ($p in $miningT1.Keys) {
  $items["mining_${p}_5"] = Craft ([long]($items["mining_${p}_4"] + 4 * $Compd["diamond"] + 4 * $Compd["redstone"])) $MARGIN[5]
}

$items["simple_pickaxe"] = Craft ([long](3 * (V "COBBLESTONE") + 2 * (V "STICK"))) $MARGIN[1]
$items["mining_pickaxe"] = Craft ([long]($items["simple_pickaxe"] + 8 * (V "COAL"))) $MARGIN[1]
$items["mining_pickaxe_2"] = Craft ([long]($items["mining_pickaxe"] + 4 * (V "IRON_INGOT") + 4 * (V "COAL"))) $MARGIN[2]
$items["mining_pickaxe_3"] = Craft ([long]($items["mining_pickaxe_2"] + 4 * $Comp["raw_copper"] + 4 * $Comp["raw_iron"])) $MARGIN[3]
$items["mining_pickaxe_4"] = Craft ([long]($items["mining_pickaxe_3"] + 4 * $Compd["raw_copper"] + 4 * $Compd["coal"])) $MARGIN[4]
$items["mining_pickaxe_5"] = Craft ([long]($items["mining_pickaxe_4"] + 4 * $Compd["diamond"] + 4 * $Compd["redstone"])) $MARGIN[5]
$items["vein_siphon"] = Craft ([long]($items["mining_pickaxe_5"] + 4 * $Compd["diamond"] + 4 * $Compd["emerald"])) $MARGIN[5]

# Farming
$farmT1 = @{ helmet = 5; chestplate = 8; leggings = 7; boots = 4 }
foreach ($p in $farmT1.Keys) {
  $items["farming_$p"] = Craft ([long]($farmT1[$p] * (V "WHEAT"))) $MARGIN[1]
}
$items["farming_hoe"] = Craft ([long](2 * (V "WHEAT") + 2 * (V "STICK"))) $MARGIN[1]
foreach ($p in @("helmet","chestplate","leggings","boots","hoe")) {
  $items["farming_${p}_2"] = Craft ([long]($items["farming_$p"] + 8 * (V "CARROT"))) $MARGIN[2]
}
foreach ($p in @("helmet","chestplate","leggings","boots")) {
  $items["farming_${p}_3"] = Craft ([long]($items["farming_${p}_2"] + 8 * $Comp["potato"])) $MARGIN[3]
}
$items["farming_hoe_3"] = Craft ([long]($items["farming_hoe_2"] + 8 * $Comp["wheat"])) $MARGIN[3]
foreach ($p in @("helmet","chestplate","leggings","boots")) {
  $items["farming_${p}_4"] = Craft ([long]($items["farming_${p}_3"] + 4 * $Compd["sugar_cane"] + 4 * $Compd["carrot"])) $MARGIN[4]
}
$items["farming_hoe_4"] = Craft ([long]($items["farming_hoe_3"] + 4 * $Compd["sugar_cane"] + 4 * $Compd["wheat"])) $MARGIN[4]
foreach ($p in @("helmet","chestplate","leggings","boots")) {
  $items["farming_${p}_5"] = Craft ([long]($items["farming_${p}_4"] + 4 * $Compd["nether_wart"] + 4 * $Compd["potato"])) $MARGIN[5]
}
$items["farming_hoe_5"] = Craft ([long]($items["farming_hoe_4"] + 4 * $Compd["nether_wart"] + 4 * $Compd["wheat"])) $MARGIN[5]

# Foraging
foreach ($p in @("helmet","chestplate","leggings","boots")) {
  $n = @{ helmet = 5; chestplate = 8; leggings = 7; boots = 4 }[$p]
  $items["foraging_$p"] = Craft ([long]($n * (V "SPRUCE_LOG"))) $MARGIN[1]
}
$items["foraging_axe"] = Craft ([long](3 * (V "SPRUCE_LOG") + 2 * (V "STICK"))) $MARGIN[1]
foreach ($p in @("helmet","chestplate","leggings","boots","axe")) {
  $items["foraging_${p}_2"] = Craft ([long]($items["foraging_$p"] + 8 * (V "BIRCH_LOG"))) $MARGIN[2]
  $items["foraging_${p}_3"] = Craft ([long]($items["foraging_${p}_2"] + 8 * $Comp["oak_log"])) $MARGIN[3]
}
foreach ($p in @("helmet","chestplate","leggings","boots","axe")) {
  $items["foraging_${p}_4"] = Craft ([long]($items["foraging_${p}_3"] + 4 * $Compd["spruce_log"] + 4 * $Compd["birch_log"])) $MARGIN[4]
  $items["foraging_${p}_5"] = Craft ([long]($items["foraging_${p}_4"] + 4 * $Compd["oak_log"] + 4 * $Compd["birch_log"])) $MARGIN[5]
}

# Fishing
foreach ($p in @("helmet","chestplate","leggings","boots")) {
  $n = @{ helmet = 5; chestplate = 8; leggings = 7; boots = 4 }[$p]
  $items["fishing_$p"] = Craft ([long]($n * (V "COD"))) $MARGIN[1]
}
$items["fishing_rod"] = Craft ([long](2 * (V "COD") + 2 * (V "STICK") + (V "STRING"))) $MARGIN[1]
foreach ($p in @("helmet","chestplate","leggings","boots","rod")) {
  $items["fishing_${p}_2"] = Craft ([long]($items["fishing_$p"] + 8 * (V "SALMON"))) $MARGIN[2]
  $items["fishing_${p}_3"] = Craft ([long]($items["fishing_${p}_2"] + 8 * $Comp["pufferfish"])) $MARGIN[3]
}
foreach ($p in @("helmet","chestplate","leggings","boots","rod")) {
  $items["fishing_${p}_4"] = Craft ([long]($items["fishing_${p}_3"] + 4 * $Compd["prismarine_shard"] + 4 * $Compd["salmon"])) $MARGIN[4]
  $items["fishing_${p}_5"] = Craft ([long]($items["fishing_${p}_4"] + 4 * $Compd["prismarine_shard"] + 4 * $Compd["pufferfish"])) $MARGIN[5]
}

# Charms
$charmT1 = @{
  charm_combat = 4 * (V "BONE"); charm_mining = 4 * (V "COAL"); charm_foraging = 4 * (V "OAK_LOG")
  charm_farming = 4 * (V "WHEAT"); charm_fishing = 4 * (V "COD"); charm_utility = 4 * (V "IRON_INGOT")
}
foreach ($k in $charmT1.Keys) { $items[$k] = Craft ([long]$charmT1[$k]) $CHARM[1] }
$charmT2 = @{
  charm_combat_2 = @("charm_combat","bone"); charm_mining_2 = @("charm_mining","coal")
  charm_foraging_2 = @("charm_foraging","oak_log"); charm_farming_2 = @("charm_farming","wheat")
  charm_fishing_2 = @("charm_fishing","cod"); charm_utility_2 = @("charm_utility","raw_iron")
}
foreach ($k in $charmT2.Keys) {
  $prev = $charmT2[$k][0]; $mat = $charmT2[$k][1]
  $items[$k] = Craft ([long]($items[$prev] + 8 * $Comp[$mat])) $CHARM[2]
}
$charmT3 = @{
  charm_combat_3 = @("charm_combat_2","bone"); charm_mining_3 = @("charm_mining_2","coal")
  charm_foraging_3 = @("charm_foraging_2","oak_log"); charm_farming_3 = @("charm_farming_2","wheat")
  charm_fishing_3 = @("charm_fishing_2","cod"); charm_utility_3 = @("charm_utility_2","raw_iron")
}
foreach ($k in $charmT3.Keys) {
  $prev = $charmT3[$k][0]; $mat = $charmT3[$k][1]
  $items[$k] = Craft ([long]($items[$prev] + 8 * $Compd[$mat])) $CHARM[3]
}
$items["charm_shiny"] = Craft ([long](4 * $Comp["diamond"] + 4 * $Comp["emerald"])) $CHARM[2]
$items["charm_forge"] = Craft ([long](4 * $Compd["raw_iron"] + 4 * $Comp["coal"])) $CHARM[3]
$items["charm_estate"] = Craft ([long](4 * $Compd["emerald"] + 4 * $Comp["raw_gold"])) $CHARM[3]

# Catcher
foreach ($p in @("helmet","chestplate","leggings","boots")) {
  $n = @{ helmet = 5; chestplate = 8; leggings = 7; boots = 4 }[$p]
  $items["catcher_$p"] = Craft ([long]($n * (V "STRING") + [Math]::Floor($n * (V "IRON_INGOT") / 2))) $MARGIN[1]
}
$items["catcher_gaff"] = Craft ([long](3 * (V "IRON_INGOT") + 2 * (V "STICK") + (V "STRING"))) $MARGIN[1]
foreach ($p in @("helmet","chestplate","leggings","boots","gaff")) {
  $items["catcher_${p}_2"] = Craft ([long]($items["catcher_$p"] + 8 * $Comp["string"])) $MARGIN[3]
  $items["catcher_${p}_3"] = Craft ([long]($items["catcher_${p}_2"] + 8 * $Compd["leather"])) $MARGIN[4]
}

# Boosters
$boosters = @{
  coal_booster = @("COAL_BLOCK",9); iron_booster = @("IRON_BLOCK",9); gold_booster = @("GOLD_BLOCK",9)
  diamond_booster = @("DIAMOND_BLOCK",9); emerald_booster = @("EMERALD_BLOCK",9)
  redstone_booster = @("REDSTONE_BLOCK",9); lapis_booster = @("LAPIS_BLOCK",9)
  glowstone_booster = @("GLOWSTONE",9); wheat_booster = @("HAY_BLOCK",9)
  oak_booster = @("OAK_LOG",9); birch_booster = @("BIRCH_LOG",9)
}
foreach ($k in $boosters.Keys) {
  $items[$k] = Craft ([long]($boosters[$k][1] * (V $boosters[$k][0]))) $BOOSTER
}
$items["carrot_booster"] = Craft ([long](9 * 32 * (V "CARROT"))) $BOOSTER

# Material progression
$items["compressed_oak_chestplate"] = Craft ([long](8 * $Comp["oak_log"] + (V "LEATHER"))) $MATERIAL
$items["compacted_timber_axe"] = Craft ([long](3 * $Compd["oak_log"] + 2 * (V "STICK"))) $MATERIAL
$items["compressed_stone_pickaxe"] = Craft ([long](3 * $Comp["cobblestone"] + 2 * (V "STICK"))) $MATERIAL
$items["compacted_cobble_hammer"] = Craft ([long](2 * $Compd["cobblestone"] + 2 * (V "STICK"))) $MATERIAL
$items["compressed_coal_ring"] = Craft ([long](8 * $Comp["coal"] + (V "GOLD_NUGGET"))) $MATERIAL
$items["copper_sword"] = Craft ([long](2 * $Comp["raw_copper"] + (V "STICK"))) $MATERIAL
$items["compressed_gold_sword"] = Craft ([long](2 * $Comp["raw_gold"] + (V "STICK"))) $MATERIAL
$items["compacted_midas_dagger"] = Craft ([long](3 * $Compd["raw_gold"] + $items["compressed_gold_sword"] + (V "STICK"))) $MATERIAL
$items["redstone_infused_boots"] = Craft ([long](6 * $Compd["redstone"] + (V "IRON_BOOTS"))) $MATERIAL
$items["lapis_pendant"] = Craft ([long](8 * $Comp["lapis"] + (V "AMETHYST_CLUSTER"))) $MATERIAL
$items["compacted_diamond_chestplate"] = Craft ([long](8 * $Compd["diamond"])) $MATERIAL
$items["compacted_diamond_sword"] = Craft ([long]($Compd["diamond"] + (V "NETHERITE_SCRAP") + (V "STICK"))) $MATERIAL
$items["emerald_crown"] = Craft ([long](5 * $Compd["emerald"] + 2 * (V "GOLD_INGOT"))) $MATERIAL
$items["compacted_emerald_scythe"] = Craft ([long](2 * $Compd["emerald"] + $Compd["diamond"] + (V "STICK"))) $MATERIAL
$items["compacted_iron_pickaxe"] = Craft ([long](3 * $Compd["raw_iron"] + 2 * (V "STICK"))) $MATERIAL
$items["compacted_diamond_pickaxe"] = Craft ([long](3 * $Compd["diamond"] + $Compd["cobblestone"] + (V "STICK"))) $MATERIAL
$items["blueprint_upgrade_stone_2"] = Craft ([long](8 * $Compd["raw_iron"] + $Comp["diamond"])) $MATERIAL
$items["blueprint_upgrade_stone_3"] = Craft ([long](6 * $Compd["diamond"] + (V "NETHERITE_SCRAP") + 2 * $Compd["raw_gold"])) 1.65
$items["blueprint_upgrade_stone_4"] = Craft ([long](6 * $Compd["diamond"] + 2 * (V "ANCIENT_DEBRIS") + (V "NETHERITE_BLOCK"))) 1.80

# Starters / side
$items["simple_longbow"] = Craft ([long](3 * (V "STICK") + 3 * (V "STRING"))) $MARGIN[1]
$items["simple_shortbow"] = Craft ([long](2 * (V "STICK") + 2 * (V "STRING"))) $MARGIN[1]
$items["wooden_rod"] = Craft ([long](2 * (V "STICK") + 2 * (V "STRING"))) $MARGIN[1]
$items["splinter_glaive"] = 18000
$items["ashen_cleaver"] = 45000
$items["bone_knife"] = Craft ([long](2 * $Comp["bone"] + (V "STICK"))) $MARGIN[2]
$items["venom_dagger"] = Craft ([long](2 * $Comp["string"] + $Comp["gunpowder"] + (V "STICK"))) $MARGIN[2]
$items["iron_longbow"] = Craft ([long](3 * (V "IRON_INGOT") + 3 * (V "STRING"))) $MARGIN[2]
$items["reinforced_shortbow"] = Craft ([long](2 * $Comp["raw_iron"] + 2 * (V "STRING"))) $MARGIN[3]
$items["copper_rod"] = Craft ([long](2 * $Comp["raw_copper"] + 2 * (V "STRING"))) $MARGIN[2]
$items["frost_shard"] = Craft ([long](4 * $Comp["prismarine_shard"] + $Comp["cod"])) $MARGIN[2]
$items["mender_staff"] = Craft ([long](2 * $Comp["wheat"] + $Comp["carrot"] + (V "STICK"))) $MARGIN[3]
$items["ember_rod"] = Craft ([long](3 * $Comp["coal"] + 2 * (V "STICK"))) $MARGIN[3]
$items["reinforced_pickaxe"] = Craft ([long](3 * $Comp["raw_iron"] + $Comp["coal"] + 2 * (V "STICK"))) $MARGIN[3]
$items["obsidian_maul"] = Craft ([long](3 * $Compd["cobblestone"] + 2 * (V "STICK"))) $MARGIN[4]

foreach ($pair in @(
  @("rotten","rotten_flesh"), @("bone","bone"), @("webweave","string"),
  @("ironhide","leather"), @("healer","wheat")
)) {
  $prefix = $pair[0]; $mat = $pair[1]
  $items["${prefix}_helmet"] = Craft ([long](5 * $Comp[$mat])) $MARGIN[2]
  $items["${prefix}_chestplate"] = Craft ([long](8 * $Comp[$mat])) $MARGIN[2]
  $items["${prefix}_leggings"] = Craft ([long](7 * $Comp[$mat])) $MARGIN[2]
  $items["${prefix}_boots"] = Craft ([long](4 * $Comp[$mat])) $MARGIN[2]
}

foreach ($p in @("helmet","chestplate","leggings","boots")) {
  $n = @{ helmet = 5; chestplate = 8; leggings = 7; boots = 4 }[$p]
  $items["diving_$p"] = Craft ([long]($n * $Comp["prismarine_shard"])) $MARGIN[3]
}

$items["mining_sack"] = Craft ([long](8 * $Comp["leather"] + $Comp["coal"])) $MARGIN[2]
$items["farming_sack"] = Craft ([long](8 * $Comp["leather"] + $Comp["wheat"])) $MARGIN[2]
$items["foraging_sack"] = Craft ([long](8 * $Comp["leather"] + $Comp["oak_log"])) $MARGIN[2]
$items["fishing_sack"] = Craft ([long](8 * $Comp["leather"] + $Comp["cod"])) $MARGIN[2]
$items["combat_sack"] = Craft ([long](8 * $Comp["leather"] + $Comp["bone"])) $MARGIN[2]

$drops = [ordered]@{
  dungeon_core = 350000; dungeon_core_2 = 750000; dungeon_core_3 = 1500000
  dungeon_vestige_helmet = 55000; dungeon_vestige_chestplate = 65000
  dungeon_vestige_leggings = 60000; dungeon_vestige_boots = 50000
  dungeon_tank_helmet = 70000; dungeon_tank_chestplate = 85000
  dungeon_tank_leggings = 78000; dungeon_tank_boots = 65000
  dungeon_assassin_helmet = 70000; dungeon_assassin_chestplate = 85000
  dungeon_assassin_leggings = 78000; dungeon_assassin_boots = 65000
  dungeon_soldier_helmet = 70000; dungeon_soldier_chestplate = 85000
  dungeon_soldier_leggings = 78000; dungeon_soldier_boots = 65000
  dungeon_healer_helmet = 70000; dungeon_healer_chestplate = 85000
  dungeon_healer_leggings = 78000; dungeon_healer_boots = 65000
  dungeon_shaman_helmet = 70000; dungeon_shaman_chestplate = 85000
  dungeon_shaman_leggings = 78000; dungeon_shaman_boots = 65000
  dungeon_relic_t2_helmet = 140000; dungeon_relic_t2_chestplate = 165000
  dungeon_relic_t2_leggings = 150000; dungeon_relic_t2_boots = 130000
  dungeon_relic_t2_weapon = 175000
  dungeon_relic_t3_helmet = 280000; dungeon_relic_t3_chestplate = 330000
  dungeon_relic_t3_leggings = 300000; dungeon_relic_t3_boots = 260000
  dungeon_relic_t3_weapon = 350000
  dungeon_t2_sword = 220000; dungeon_t2_bow = 220000; dungeon_t2_wand = 220000
  dungeon_t3_sword = 440000; dungeon_t3_bow = 440000; dungeon_t3_wand = 440000
  aetherblade = 4500000; bridged_axe = 2400000; warped_blade = 1800000
  gravwell_cleaver = 2400000; staff_of_technical_difficulties = 3200000
  void_vacuum_charm = 2600000; thermal_core = 2800000
  pickaxe_core_of_the_burrower = 2700000; insolvent_ledger = 2900000
  squids_boot = 1600000; skuldugery_shortbow = 2800000; hollow_longbow = 2600000
  aetherion_void_stick = 6000000
  aetherion_helmet = 7000000; aetherion_chestplate = 9000000
  aetherion_leggings = 8000000; aetherion_boots = 6500000
  god_pickaxe = 15000000; god_axe = 15000000; god_sword = 15000000
  god_helmet = 15000000; god_chestplate = 15000000; god_leggings = 15000000; god_boots = 15000000
  god2_pickaxe = 30000000; god2_axe = 30000000; god2_sword = 30000000
  god2_helmet = 30000000; god2_chestplate = 30000000; god2_leggings = 30000000; god2_boots = 30000000
}
foreach ($k in $drops.Keys) { $items[$k] = $drops[$k] }

# Build yml
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("# Aetherion listed coin values (Skyblock-feel).")
[void]$sb.AppendLine("# Formula (see EconomyCurve.java):")
[void]$sb.AppendLine("#   compressed = round(unit x 128 x 1.4)")
[void]$sb.AppendLine("#   compacted  = round(compressed x 128 x 1.5)")
[void]$sb.AppendLine("#   refined    = round(compacted x 4)")
[void]$sb.AppendLine("#   crafted    = round(ingredientSum x craftMargin)")
[void]$sb.AppendLine("# Margins: T1 1.20, T2 1.30, T3 1.45, T4 1.65, T5 1.90")
[void]$sb.AppendLine("#          charms 1.25/1.45/1.70, boosters 1.35, material 1.55")
[void]$sb.AppendLine("# Channels: Trader 100% resources, Gear buyback 32%, Silas x8, Bazaar 50-300%.")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("economy-version: 2")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("vanilla:")
foreach ($k in $VANILLA.Keys) { [void]$sb.AppendLine("  ${k}: $($VANILLA[$k])") }
[void]$sb.AppendLine("")
[void]$sb.AppendLine("items: {}")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("resources:")
$resourceKeys = @()
foreach ($key in $RESOURCES.Keys) {
  $resourceKeys += "compressed_$key","compacted_$key"
  if ($REFINEABLE -contains $key) { $resourceKeys += "refined_$key" }
  $resourceKeys += "quarry_$key"
}
$resourceKeys += @("quarry_core_shard","quarry_core","quarry_compressor","quarry_compactor")
foreach ($k in $resourceKeys) { [void]$sb.AppendLine("  ${k}: $($items[$k])") }

$dropKeys = @($drops.Keys)
$craftedKeys = @($items.Keys | Where-Object { $resourceKeys -notcontains $_ -and $dropKeys -notcontains $_ } | Sort-Object)
[void]$sb.AppendLine("")
[void]$sb.AppendLine("crafted:")
foreach ($k in $craftedKeys) { [void]$sb.AppendLine("  ${k}: $($items[$k])") }
[void]$sb.AppendLine("")
[void]$sb.AppendLine("drops:")
foreach ($k in $dropKeys) { [void]$sb.AppendLine("  ${k}: $($items[$k])") }

[System.IO.File]::WriteAllText($out, $sb.ToString())
Write-Host "Wrote $out ($($items.Count) items)"
Write-Host "cobble C=$($Comp['cobblestone']) D=$($Compd['cobblestone'])"
Write-Host "iron C=$($Comp['raw_iron']) D=$($Compd['raw_iron'])"
Write-Host "gold C=$($Comp['raw_gold']) D=$($Compd['raw_gold'])"
Write-Host "diamond C=$($Comp['diamond']) D=$($Compd['diamond'])"
Write-Host "wheat C=$($Comp['wheat']) D=$($Compd['wheat']) R=$($RefinedMap['wheat'])"
Write-Host "combat_helmet: $($items['combat_helmet']) -> $($items['combat_helmet_2']) -> $($items['combat_helmet_3']) -> $($items['combat_helmet_4']) -> $($items['combat_helmet_5'])"
Write-Host "farming_helmet: $($items['farming_helmet']) -> $($items['farming_helmet_5'])"
Write-Host "compacted_diamond_chestplate: $($items['compacted_diamond_chestplate'])"
