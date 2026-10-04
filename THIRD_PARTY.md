# Bundled source and assets

- Anti Vanish: adapted from Anubis by 4ldenz, recovered from the user-provided `Anubis Client Beta 0.9.8.jar` (embedded metadata `0.8.12+mc1.21.11`). Its detection logic, text helpers and shard lookup are in `dev.maro.anubis` under GPL-3.0-only; original license in `LICENSES/GPL-3.0-Anubis.txt`. Modified for Maro's client-thread packet hooks, HUD, notifications and editable staff names on 2026-10-04. Anubis's Staff List module is not included. Distributions containing the adapted code are covered by GPL-3.0-only; original MIT notices for Maro/Nathan components remain intact. Full corresponding Maro source is supplied alongside the built jar.

- Nathan 1.21.11 module source: `dev.maro.nathan`, adapted from the user's current Nathan source. Its MIT notice is retained in `LICENSE-Nathan`. Per-file notices remain attached to their respective source files; SpawnerProtect carries its original All-Rights-Reserved attribution.
- Logical Zoom code: retained MIT notice in `LICENSES/MIT-LogicalZoom.txt`.
- Poppins font: SIL Open Font License in `LICENSES/OFL-1.1-Poppins.txt`.
- Barlow Condensed font: SIL Open Font License in `LICENSES/BarlowCondensed-OFL.txt`.
- Inter font: original Maro notice in `src/main/resources/INTER_LICENSE.txt`.
- SnakeYAML 2.7: Apache License 2.0, bundled as a nested dependency with its original license notices. Used with safe loading to read LRCLIB word timestamps.

The local `dev.maro.runtime` package implements the module APIs on Fabric and Maro. It does not bundle Meteor Client or Orbit binaries. The `nameeprotect` resource namespace is retained so existing Nathan shaders, textures, and fonts resolve correctly.
