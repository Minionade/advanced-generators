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
  NeoForge has the FE Generator, and EU outputs through Modern Industrialization.
- **MJ output** for BuildCraft (when BuildCraft is installed).
- IC2 Classic support is not included for now.
