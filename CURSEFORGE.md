## Features

*   Fuel turbines that burn liquid fuels from Advanced Generators and many other mods
*   Steam turbines with configurable turbine and capacitor modules
*   Syngas production from solid fuels
*   Heat exchangers for processing fluids and transferring heat
*   Free-form multiblocks assembled from controllers and machine modules
*   Item and fluid input and output modules
*   Control and sensor modules for redstone automation
*   Wrench support: turn sensors and EU outputs, and force output faces on the other output modules
*   JEI recipe displays
*   FE output, with additional energy outputs enabled by supported integrations
*   Worlds and settings from the original 1.20.1 mod are converted on first load

## Requirements

*   Minecraft **1.21.1**
*   Java **21**
*   **Fabric:** Fabric Loader, Fabric API, and Architectury API
*   **NeoForge:** NeoForge and Architectury API

## Energy outputs

FE output is available on NeoForge. Every other output appears when its mod is installed.

| Output | Mod | Fabric | NeoForge |
|---|---|---|---|
| EU (LV to IV) | Tech Reborn | ✔ | |
| EU (LV to IV) | Modern Industrialization | | ✔ |
| EU (LV to IV) | IC2: Nostalgia | | ✔ |
| Joules | Mekanism | | ✔ |
| Joules | Electrodynamics | | ✔ |
| MJ | BuildCraft Community Edition | | ✔ |
| Rotation (SU) | Create | | ✔ |

Create's **Kinetic Converter** is a standalone block: it turns FE into rotation (256 RPM, 256 SU and 100 FE/t by default) and a wrench turns its axis.

## Fuels

The fuel turbine burns fuels from Advanced Generators (syngas) and from these mods when they are installed: BuildCraft Community Edition, Modern Industrialization, Mekanism, IC2: Nostalgia, Immersive Engineering, Immersive Petroleum, Electrodynamics, EnderIO, Forestry, TFMG, Ad Astra and Thermal.

Fuel values can be changed in `config/advgenerators.json`: `fuelMultiplier` scales everything, and `fuelValues` adds or replaces values in FE per mB (for example `"minecraft:lava=200"` or `"#c:fuel_light=3000"`; `0` turns a fuel off). Data packs can add fuels with the `advgenerators:liquid_fuel` recipe type.

## Compatibility

These integrations are optional. Install the matching mod to enable its features.

[![WORKS WITH JEI](<https://img.shields.io/static/v1?style=for-the-badge&label=WORKS WITH&message=JEI&color=72954d&labelColor=252b35&logo=curseforge&logoColor=white>)](https://www.curseforge.com/minecraft/mc-mods/jei) [![WORKS WITH Tech Reborn](<https://img.shields.io/static/v1?style=for-the-badge&label=WORKS WITH&message=Tech Reborn&color=72954d&labelColor=252b35&logo=curseforge&logoColor=white>)](https://www.curseforge.com/minecraft/mc-mods/techreborn) [![WORKS WITH Mekanism](<https://img.shields.io/static/v1?style=for-the-badge&label=WORKS WITH&message=Mekanism&color=72954d&labelColor=252b35&logo=curseforge&logoColor=white>)](https://www.curseforge.com/minecraft/mc-mods/mekanism) [![WORKS WITH Modern Industrialization](<https://img.shields.io/static/v1?style=for-the-badge&label=WORKS WITH&message=Modern Industrialization&color=72954d&labelColor=252b35&logo=curseforge&logoColor=white>)](https://www.curseforge.com/minecraft/mc-mods/modern-industrialization) [![WORKS WITH BuildCraft Community Edition](<https://img.shields.io/static/v1?style=for-the-badge&label=WORKS WITH&message=BuildCraft Community Edition&color=72954d&labelColor=252b35&logo=curseforge&logoColor=white>)](https://www.curseforge.com/minecraft/mc-mods/buildcraft-community-edition) [![WORKS WITH IC2: Nostalgia](<https://img.shields.io/static/v1?style=for-the-badge&label=WORKS WITH&message=IC2: Nostalgia&color=72954d&labelColor=252b35&logo=curseforge&logoColor=white>)](https://www.curseforge.com/minecraft/mc-mods/industrial-craft-2-nostalgia) [![WORKS WITH Electrodynamics](<https://img.shields.io/static/v1?style=for-the-badge&label=WORKS WITH&message=Electrodynamics&color=72954d&labelColor=252b35&logo=curseforge&logoColor=white>)](https://www.curseforge.com/minecraft/mc-mods/electrodynamics) [![WORKS WITH Create](<https://img.shields.io/static/v1?style=for-the-badge&label=WORKS WITH&message=Create&color=72954d&labelColor=252b35&logo=curseforge&logoColor=white>)](https://www.curseforge.com/minecraft/mc-mods/create)

## Original Authors and Port Credits

The original _Advanced Generators_ was created by **bdew** and the original project’s contributors. The Minecraft 1.21.1 Fabric and NeoForge port is maintained by **Minionade**. This is an unofficial port and is not affiliated with or endorsed by bdew.

Licensed under the MIT License.  
[Port source code](https://github.com/Minionade/advanced-generators) · [Original CurseForge project](https://www.curseforge.com/minecraft/mc-mods/advanced-generators)
