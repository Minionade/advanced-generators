### 2.0.3

* Machines drop themselves again: loot tables, the pickaxe tag and the original block strength were missing
* Wrench support: turns sensors and EU outputs, and forces output faces on the other output modules (also read from 1.20.1 saves)
* Right clicking an unconnected module says so in chat; control and sensor modules only open when connected
* Right click steps backwards through control actions and sensor choices; Esc in the output screen goes back to the machine screen
* Sensors give a strong redstone signal like the original; the fluid input and output modules take buckets
* EU per FE is a setting again (`euPerFe`, 0.25, the original's IC2 ratio) and applies to every EU output; a fuel value of 0 in `fuelValues` turns that fuel off
* New: Mekanism Joules Emitter, Electrodynamics Joules Emitter, Create Kinetic Converter
* New: IC2: Nostalgia EU outputs, IC2 fuels and the IC2 upgrade recipes are back
* New: 48 more fuels (Ad Astra, Electrodynamics, EnderIO, Forestry, IC2, Immersive Engineering and Petroleum, TFMG) and the `fuelMultiplier` / `fuelValues` settings
* Fabric: fixed a server crash when an EU output pushed energy into a Tech Reborn machine; the Tech Reborn wrench now works on the machine blocks
* Removed the Singularity Iteration integration and all Photoshop source files from the jar

### 2.0.2

* Fuel turbine burns BuildCraft (BCCE) and Modern Industrialization fuels, read from those mods' own fuel data (MI at 1 EU = 4 FE); they are listed in JEI too
* EU outputs connect to Modern Industrialization cables again, and stay connected after a world reload (MI cables drop a connection for good if the machine looks unavailable while chunks load)
* BuildCraft MJ output only shows output faces where something can receive MJ, instead of on every free face
* Output screen: MJ/EU/FE units show properly instead of raw translation keys, and the text no longer overlaps the buttons
* Removed the per-face FE output limit button

### 2.0.0 (alpha)

* Java port for Minecraft 1.21.1 on Fabric and NeoForge; no Scala or BDLib needed
* Worlds and settings from the 1.20.1 version are converted on first load
* Heat exchanger recipe inputs fixed: hot fluids (lava) feed the hot input, water the cold input
* Steam turbine spin-up and exchanger item buffer size match the original again
* Machine screens match the original: tank and energy gauges, info panel, output, control and sensor screens
* Fixed machine data not syncing correctly to the client (wrong fluids and energy bars in screens)
* Multiblock border fixed: correct colour and lighting, output arrows the right way round, updates live, cheaper to draw
* Machine items show the connected-texture frame again; sensor and controller models and textures restored
* Machines join the first eligible controller, as in the original
* Original block names restored; the FE output is now called "FE Generator" (not offered on Fabric)
* New: BuildCraft MJ output, Tech Reborn / Modern Industrialization EU outputs

### 1.6.0

* Updated to MC 1.20.1

### 1.5.0

* Updated to MC 1.19.3
