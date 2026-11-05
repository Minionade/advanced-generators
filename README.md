[![](https://cf.way2muchnoise.eu/full_advanced-generators-java-edition_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/advanced-generators-java-edition/)
[![](https://cf.way2muchnoise.eu/versions/advanced-generators-java-edition.svg)](https://www.curseforge.com/minecraft/mc-mods/advanced-generators-java-edition/)

# Advanced Generators

An unofficial Java port of bdew's *Advanced Generators* for Minecraft 1.21.1, maintained by Minionade. Supports Fabric and NeoForge.

CurseForge: [Advanced Generators — Java Edition](https://www.curseforge.com/minecraft/mc-mods/advanced-generators-java-edition/)

Original mod by bdew: [Advanced Generators](https://www.curseforge.com/minecraft/mc-mods/advanced-generators)

## Upgrading from the 1.20.1 version

Worlds and settings from the original Forge 1.20.1 mod carry over (checked on NeoForge against a world written by the real 1.20.1 mod):

- **Machines** saved by the 1.20.1 mod (controllers and modules, including contents, connections, output settings,
  control and sensor modules) are converted when their chunk is first loaded. Block and fluid names are unchanged.
- **Settings**: if there is no `config/advgenerators.json` but the old `config/advgenerators-common.toml` exists, its
  values are imported on first start. The old file is left untouched.

Back up your world before upgrading, as with any version jump.

## Differences from the original mod

- **Fabric and NeoForge** are both supported. On Fabric only the EU output is offered (via Tech Reborn Energy);
  NeoForge has the FE Generator, and EU outputs through Modern Industrialization and IC2: Nostalgia.
- IC2 Classic support is not included for now.

## Integrations (NeoForge)

| Mod | What you get |
|---|---|
| BuildCraft Community Edition | MJ Output module; BuildCraft's fuels burn in the fuel turbine |
| IC2: Nostalgia | EU outputs (LV to IV) as IC2 energy sources; IC2 fuels; IC2 material recipes for upgrades |
| Modern Industrialization | EU outputs for its cables; its fuels burn in the fuel turbine (1 EU = 4 FE) |
| Mekanism | Joules Emitter; gas input; hydrogen and ethene fuels |
| Electrodynamics | Electrodynamics Joules Emitter |
| Create | Kinetic Converter: FE in, rotation out (wrench turns its axis). 256 RPM, 256 SU and 100 FE/t by default |
| Immersive Engineering, Immersive Petroleum, Thermal, EnderIO, Forestry, TFMG, Ad Astra | Their fuels burn in the fuel turbine |

## Fuel settings

`config/advgenerators.json` has two fuel settings. `fuelMultiplier` scales every fuel value. `fuelValues` adds or
replaces values in FE per mB, as `"fluid_id=fe_per_mb"` or `"#fluid_tag=fe_per_mb"` (for example
`"minecraft:lava=200"`); the first matching entry wins and beats fuel recipes. Fuels can also be added with a data pack
recipe of type `advgenerators:liquid_fuel`. A value of `0` turns that fuel off. A `[Fuels]` section in an old `advgenerators-common.toml` is imported too.

Other settings for the integrations: `euPerFe` (EU per FE for every EU output, 0.25 by default, the original's IC2 "Ratio"), `joulesPerFe` (Mekanism, 2.5 by default, the same as Mekanism's own conversion),
`electrodynamicsJoulesPerFe`, `electrodynamicsVoltage`, and `createFePerTick`, `createSpeedRpm`,
`createStressCapacity`, `createBuffer`.

## Using a wrench

Any item tagged as a wrench (`c:tools/wrench`, `c:wrenches`, `forge:wrenches`, ...) works on the machine blocks: on
sensors and EU outputs it turns them to face the clicked side; on the other output modules it forces the clicked face to
be an output (or takes that back), even when nothing is there to receive. Right click steps backwards through the
choices in the control and sensor module screens.
