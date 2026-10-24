# InsomniacServerPatches

A small NeoForge add-on for server-side fixes in Insomniac Gaming's Minecraft
1.21.1 modpack. Each fix lives in its own package so future fixes can be added
without changing the identity of the mod.

## Version 1.0.0

Requires NeoForge 21.1.x and Tensura Horizons 0.31. Blocks both stages of
Horizons' Sovereign awakening path:

- Automatic Sovereign Vessel acquisition is suppressed.
- True Sovereign awakening through the status menu or `/crosshorizon` returns
  false and tells the player that the awakening is disabled.

The patch does not remove existing Sovereign progress, skills, or saved data.
It does not change the separate Sovereign skill from Tensura Lower.

## Install

Place `InsomniacServerPatches-1.0.0.jar` in the server's `mods/` directory and
restart the server. The mod can also be included in a client modpack, including
for integrated singleplayer, but only the server's copy controls a dedicated
server. Keep the original Tensura Horizons JAR intact. To remove this patch,
remove this add-on JAR and restart.

## Build

Use Java 21 and run `./gradlew clean build`. The release JAR is under
`build/libs/`. The build does not bundle Tensura Horizons or any of its assets.

For the disposable server and client startup checks, see [CI setup](ci/README.md).
The CI-only `InsomniacTitleProbe` is built separately and is never included in
the published mod JAR.

Before shipping with a new Horizons version, recheck the two target method
signatures and the awakening flow. The dependency range deliberately permits
only Horizons 0.31.x.
