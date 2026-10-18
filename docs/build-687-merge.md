# Build 687 and Fake Pay merge

Historical merge record. The later [build 690 update](build-690-merge.md)
replaces this version of Fake Pay and expands the character roster.

The combined client retains the local Custom Model, Auto Replenish, Trident Boost,
Anubis TPA/inspect, Spotify/Jump Circle, tracer and centered Staff Watch changes.

## Imported changes

- Build 687: tag `build-687`, commit `c7837d644d282d9b6a0584e6c2eacc0b774083ae`.
  Includes Texture Packs, its picker and native checks, Better Textures integration,
  local pack icons, preservation of changes from Minecraft's own resource pack menu,
  and hover controls that leave room for pack names.
- Fake Pay: source from `885c9f52373747abe435fae56603158e55a291d8`, checked against
  the supplied `maro-mc1.21.11-1.0.0+local.jar`. Only Fake Pay and its native checks
  are imported from that branch. The module displays local chat messages.

Reference JAR SHA-256 hashes:

```text
build-687: f08ab05858a7c698092246816aef56847b3c7eeec9a9257b72375fa845b0938e
Fake Pay: dfdcd4df9e5664eb42e3431952e9d3592a0b006f8166915e62c26523fba939cc
```

## Native validation

Run with Java 21 and an optional Meteor companion JAR:

```powershell
./gradlew.bat build runProductionClientGameTest `
  -PtexturePacksTestOnly=true -PfakePayTestOnly=true `
  -PcustomModelTestOnly=true -PhandShaderTestOnly=true -PstaffTestOnly=true `
  '-PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar' --console=plain
```

This checks pack discovery, icons, order, toggling and restoration using two temporary
resource packs; keeps CJ active through real resource reloads and verifies that its GPU
texture is recreated while its mesh stays cached; renders all 16 character choices and
a GLB import; checks Fake Pay formats, invalid input and one-shot activation; and runs
the Hand Shader and centered Staff Watch checks. Test assets are excluded from the
production JAR.

Validated locally on October 11, 2026: the combined build and native production-client
checks passed with Meteor Client 1.21.11-85 in 1 minute 8 seconds. Screenshots confirm
the pack picker, CJ after resource reload, Fake Pay chat and centered Staff Watch.
