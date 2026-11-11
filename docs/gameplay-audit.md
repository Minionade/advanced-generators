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

## Comparison coverage

Read and compared the fuel, steam, syngas and exchanger controller tick logic, sensor definitions, resource adapters, controller/module discovery and upgrade handling. The core fuel-efficiency formula, strict fuel burn thresholds, steam drag/spin-up equations, syngas heat/carbon/steam steps and exchanger processing order match the inspected Scala implementation. This was a basic code review, not exhaustive parity certification. Optional energy integrations, every recipe, chunk unload/reload behavior and all GUI interactions were not runtime-tested.

## Validation

- Seven existing `EngineMathTest` JUnit tests passed using the Java 21 compiler and JUnit Console directly, including the reference steam-turbine ramp.
- Added resource-buffer regression tests for whole-unit insertion/extraction and preserving fractional remainders.
- Added Fabric transaction regression tests for shared state, nested rollback, outer rollback after a nested commit, and cached energy-port balances.
- `git diff --check` passed.
- Full Gradle compilation and tests remain **unverified**. After resolving Gradle/plugins through the environment proxy, Loom failed during configuration: `CurrentPlatform.isUnixDomainSocketsSupported` throws `java.net.SocketException: Operation not permitted`. No repository build configuration was changed to bypass that restriction.

Before merging, run `./gradlew :common:test :fabric:test :neoforge:compileJava :fabric:build :neoforge:build` in a normal Java 21 development environment.

## In-game smoke checks still required

1. On each loader, run all four controllers, then reload the world and reconnect modules across a chunk boundary.
2. Exercise exchanger sensors for empty/solid/fluid, each fill threshold, not-full and full.
3. Fill a fuel turbine's external tanks and remove a tank; check the new stored amount and capacity.
4. On NeoForge, insert into nearly full exchanger buffers and extract fractional recipe outputs repeatedly. Check that source plus destination totals are conserved.
5. With both fluid outputs enabled, attach a receiver accepting only the second output. Check that the first fluid is retained.
6. On Fabric, cache pipe connections, allow more material/energy to accumulate, and transfer through multiple ports. Check partial acceptance, reconnects, transaction rollback and fractional obsidian output.
