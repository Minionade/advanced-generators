# Basic gameplay and Scala parity review

Reviewed Java baseline: `5d95ee7` on `1.21.1`.
Original Scala baseline: `fe4d5a5dc4e0020c0e0454fd9924e1ca751199ad` (the parent of the Java port commit).

## Findings addressed

- Heat-exchanger sensors tested the sensor module's kind and buffers. They now inspect their connected controller. This restores solid/fluid detection and the exchanger-specific fill-condition ordering from `SensorResource.scala`.
- Removing fuel tanks left excess fuel stored. The fuel controller now clamps contents to the recalculated capacity, matching `TileFuelTurbineController.onModulesChanged`.
- NeoForge resource adapters changed fractional quantities before truncating the result to integers. A nearly full exchanger could accept part of an item while returning the entire item, or discard fractional fluid during extraction. Whole-unit transfers now round before mutation and retain fractions.
- NeoForge automatic fluid output inserted a selected fluid but drained by amount alone. If the receiver rejected the first buffer and accepted the second, the wrong fluid could be removed. It now drains the accepted fluid explicitly.
- Fabric item/fluid adapters copied buffer contents at creation and overwrote the buffer at commit, losing fractional output and potentially restoring stale contents when cached. They now expose live state and share transaction snapshots across the controller's ports. Mixed item/fluid buffers cannot be overwritten by the opposite adapter.
- Fabric energy adapters similarly held stale balances. Energy reads and extraction now use the live balance; nested rollback restores both resources and output accounting. Automatic energy push includes source and destination in one transaction.
- Fabric fluid-output statistics were recorded in droplets rather than mB. The recorded quantity now divides by 81.

## Second-pass findings addressed

- `UpgradeKit.scala` explicitly reconnects the replacement module. The Java kit now does this before notifying neighbours, so upgrading a turbine/capacitor used as a connecting block does not strand the rest of the structure. The kit is not consumed if block replacement fails.
- BDLib's optional output handlers resolve the current controller, redstone mode and selected output on every operation. Cached Java handlers captured those choices. Both loaders now check live connection and buffer permissions for each transfer; the Fabric combined handler retains both exchanger output views so switching selections also exposes the newly selected output.
- Unregistered output faces previously fell back to ALWAYS mode. They now refuse transfer, matching BDLib's requirement for an assigned output configuration and its six-face limit. Excess faces are not repeatedly registered and removed every tick.
- Fabric syngas automation exposed unrestricted inventory extraction. It is now insert-only, matching the Scala `InventoryItemHandler`.
- Controller removal now blocks new connections during neighbour callbacks, matching BDLib's `acceptNewModules = false` behavior.
- Menu validity now checks block-entity identity, removal and dimension, not only distance. Open menus cannot keep editing a broken/replaced machine.
- NeoForge amount-only fluid extraction skips a buffer containing less than one mB, allowing a usable second buffer to be drained. Item extraction also respects the item's maximum stack size.

## Comparison coverage

Read and compared the fuel, steam, syngas and exchanger controller tick logic, sensor definitions, resource adapters, controller/module discovery and upgrade handling. The core fuel-efficiency formula, strict fuel burn thresholds, steam drag/spin-up equations, syngas heat/carbon/steam steps and exchanger processing order match the inspected Scala implementation. This was a basic code review, not exhaustive parity certification. Optional energy integrations, every recipe, chunk unload/reload behavior and all GUI interactions were not runtime-tested.

## Third pass: chunk lifecycle

This pass starts from the merged `1.21.1` commit `1be9c10`.

- Controllers now persist module positions and types. Discovery can traverse known unloaded modules without requesting their chunks, keeping capacity and loaded branches connected. A loaded missing/replaced module still breaks that path and reduces capacity normally.
- Module-derived operating values, required-module checks and connection limits use the remembered membership. Unloading capacitors cannot grant additional capacitor slots.
- A neighbour update no longer clears a module's saved link or forced output faces while its controller is unloaded.
- Loaded output modules reached through an unloaded bridge retain their connection and output settings.
- BDLib's saved `modules` positions are imported even before their types can be identified. Early Java saves without membership retain their stored fuel/energy (including fractional energy) until discovery can inspect the structure's entire boundary. Recovery remains pending across saves. Once membership is known, normal capacity limits resume; if the modules really are gone, excess contents are clamped. New module connections wait while membership is incomplete so missing information cannot bypass module limits.

This intentionally distinguishes unloading from removal. It does not reproduce BDLib's tendency to discard unloaded positions during a forced validation; it preserves the original capacities and module behavior during ordinary unloading while avoiding that data-loss case.

## Validation

- **61 common-module JUnit tests passed**, including engine arithmetic, fractional-resource accounting, legacy save/config conversion, recipe roles, GUI value handling, resource references and preservation of membership from a real Scala save.
- **19 Fabric JUnit tests passed**: the seven previous transfer/permission tests plus twelve shared multiblock lifecycle regressions. The new cases cover unloaded storage and bridges, save/reload, real removal, output settings, module limits, upgrades, neighbour updates and recovery of early Java/Scala saves. World lookups fail the tests if they request a block entity in an unloaded chunk.
- All current **common and Fabric Java sources compiled** directly with Java 21 against Minecraft 1.21.1 and the configured APIs.
- `git diff --check` passed.

These are isolated Java compiler/JUnit checks, **not a Gradle build or an in-game test**. The check environment remapped the official Minecraft jar and the published Architectury/Fabric/Energy APIs to Mojang names. Its test-only Minecraft jar exposes `BlockEntityType.BlockEntitySupplier` and the `LiquidBlock` constructor, as required by the existing mod source; it uses Mockito's subclass mock maker to avoid agent attachment in this environment. No dependency jars or test-environment modifications are shipped with this PR.

Full Gradle builds remain blocked here. The earlier environment reached Loom's `CurrentPlatform.isUnixDomainSocketsSupported` and failed with `java.net.SocketException: Operation not permitted`. In this pass, the wrapper could not download Gradle (`java.net.SocketException: Network is unreachable`), including with the supplied proxy configured. NeoForge source changes have been reviewed but have not been compiled against a complete NeoForge development runtime. Do not treat the direct Fabric compilation as validation of loader remapping, packaging or optional integrations.

Before merging, run `./gradlew :common:test :fabric:test :neoforge:compileJava :fabric:build :neoforge:build` in a normal Java 21 development environment.

The previously identified partial-unloading resource-loss path is addressed by the third pass. Its isolated tests model chunk visibility; actual server chunk scheduling still needs an in-game check on both loaders.

## In-game smoke checks still required

1. On each loader, run all four controllers, then reload the world and reconnect modules across a chunk boundary.
2. Exercise exchanger sensors for empty/solid/fluid, each fill threshold, not-full and full.
3. Fill a fuel turbine's external tanks and remove a tank; check the new stored amount and capacity.
4. On NeoForge, insert into nearly full exchanger buffers and extract fractional recipe outputs repeatedly. Check that source plus destination totals are conserved.
5. With both fluid outputs enabled, attach a receiver accepting only the second output. Check that the first fluid is retained.
6. On Fabric, cache pipe connections, allow more material/energy to accumulate, and transfer through multiple ports. Check partial acceptance, reconnects, transaction rollback and fractional obsidian output.

7. Upgrade a turbine and capacitor that connect other modules to the controller; verify the entire branch remains connected.
8. Without breaking pipes, toggle redstone modes and hot/cold selections, then remove or replace the controller. Verify old handlers cannot keep transferring.
9. Register six output faces and connect a seventh receiver. Only registered faces should transfer; rotating a directional EU output must disable the old face.
10. Try pulling carbon items out through a Fabric syngas item input, and break a machine while its menu is open.
11. Fill a machine across a chunk boundary, unload only its tanks/capacitors, then save and reload. Check fuel, whole/fractional energy, output settings and module limits. Repeat with an unloaded connecting bridge; then actually break that bridge and verify disconnected capacity is removed.
12. Load an early Java or Scala save with part of the machine unloaded, save again, then load all relevant chunks. Verify resources survive recovery and adding modules works once membership is known.
