# Prop generator (optional)

This is the small Python toolkit that authored the pack. You don't need it to use the schematics. It's here so a piece can be tweaked and re-exported without rebuilding it by hand.

Requirements: Python 3.10+ with `nbtlib`, `Pillow` and `numpy` (`pip install nbtlib pillow numpy`).

```
python build_all.py                 # rebuild all 18 -> ../schematics + ../previews
python build_all.py ae_boathouse    # rebuild one
python overview.py                  # refresh ../previews/_overview.png
python validate.py                  # re-check every .schem against the 1.21.1 registry
```

Builds are deterministic (fixed seeds), so the same code gives the same blocks every time.

## Layout

* `aeprops/core.py`: voxel `Build`, block-state validation against `blocks_1_21_1.json` (from the vanilla 1.21.1 data generator), vanilla neighbour logic (fence/wall/pane connections, stair corners), and the Sponge v3 writer.
* `aeprops/style.py`: Eldervale material mixes, roof generators (gable, hip, Blocky-Verse thatch), lanterns, braces, trees, and the support checker (flags plants without soil, unsupported lanterns and candles, and so on).
* `aeprops/render.py`: isometric preview renderer driven by vanilla blockstate and model JSON.
* `pieces/*.py`: one function per prop. Conventions: front faces south, y=0 is the ground layer, and the anchor defaults to the footprint centre.

## Previews need the vanilla textures

The renderer reads blockstates, models and textures from the 1.21.1 client jar. They aren't shipped here. Extract them once into `tools/mc`:

```
cd tools
mkdir mc && cd mc
tar -xf "%APPDATA%\.minecraft\versions\1.21.1\1.21.1.jar" assets/minecraft/blockstates assets/minecraft/models/block assets/minecraft/textures/block assets/minecraft/textures/entity
```

Or set `AE_MC_ASSETS` to any folder that contains `blockstates/`, `models/` and `textures/`.

To regenerate the block registry from the full jar data generator (Java 21):

```
java -cp "<1.21.1 client jar + its libraries>" net.minecraft.data.Main --reports --output gen
```

Then point `AE_BLOCKS_JSON` at `gen/reports/blocks.json`.

## Island highlight templates (this ship)

`aeg/starters.py` (4 starter isles) and `aeg/structures.py` (structures + guild project sites/stages) use the same
toolkit. Conventions: y 0 = ground layer, anchor = ground-layer centre, front = south.

```
python build_aeg.py            # -> out/templates/*.schem
python build_aeg.py --render   # + out/previews (needs AE_MC_ASSETS, see above)
python verify_pads.py          # hut/housing fit each marked pad in 4 rotations, spawns are safe
```
Copy changed `.schem` files into `AetherionGuilds/src/main/resources/templates/` (bundled) or straight into the
server's `plugins/AetherionGuilds/templates/` and run `/island admin reload-templates`.
