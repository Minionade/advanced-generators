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
