# Bundled source and assets

- Nathan 1.21.11 module source: `dev.maro.nathan`, adapted from the user's current Nathan source. Its MIT notice is retained in `LICENSE-Nathan`. Per-file notices remain attached to their respective source files; SpawnerProtect carries its original All-Rights-Reserved attribution.
- Logical Zoom code: retained MIT notice in `LICENSES/MIT-LogicalZoom.txt`.
- Poppins font: SIL Open Font License in `LICENSES/OFL-1.1-Poppins.txt`.
- Barlow Condensed font: SIL Open Font License in `LICENSES/BarlowCondensed-OFL.txt`.
- Inter font: original Maro notice in `src/main/resources/INTER_LICENSE.txt`.
- SnakeYAML 2.7: Apache License 2.0, bundled as a nested dependency with its original license notices. Used with safe loading to read LRCLIB word timestamps.

The local `dev.maro.runtime` package implements the module APIs on Fabric and Maro. It does not bundle Meteor Client or Orbit binaries. The `nameeprotect` resource namespace is retained so existing Nathan shaders, textures, and fonts resolve correctly.
