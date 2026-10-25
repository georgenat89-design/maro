# Rendering and model appearance

The local 1.0.1 update centers Lucide icons and control labels on their visible
outlines. Icons are rasterized at their final screen size with grayscale
antialiasing, including resized HUDs and the projected menu. Text retains its
antialiased edge coverage, and the module-settings title shares the header
controls' vertical center.

Skinned character textures now repeat like the bundled source glTF samplers.
This fixes the gray bodies and stretched texture strips caused by clamping
repeated UV coordinates. Original image bytes have been restored for every
bundled character; rigs, geometry, UVs, normals, weights and animation clips are
unchanged. The offline baking tool preserves PNG/JPEG originals, with an 8192
pixel limit. Mipmap reduction averages in linear light with alpha weighting,
keeping distant colors and transparent edges clear. A texture decode failure
rejects that model instead of silently drawing it with a white texture.

Trident Boost and Air Stuck, including their movement and trident hooks, have
been removed. The following update also removes No Fall and its packet hooks.

## Validation

Use `gradlew.bat` on Windows. Each production test runs in disposable local
worlds and saves screenshots under `run/screenshots`.

```text
gradlew.bat remapJar remapGametestJar
gradlew.bat runProductionClientGameTest -PrenderQualityTestOnly=true
gradlew.bat runProductionClientGameTest -PcharacterTestOnly=true
gradlew.bat runProductionClientGameTest -PmenuTestOnly=true
gradlew.bat runProductionClientGameTest -PtypographyTestOnly=true
gradlew.bat runProductionClientGameTest -PbaseCounterTestOnly=true
gradlew.bat runProductionClientGameTest -PbaseEspTestOnly=true
python tools/restore_character_textures.py
```

The icon test measures all 104 rendered glyphs at GUI scales 2/3/4 and a 0.73
resize. Character checks exercise every bundled source/cache model through
movement and emotes, then capture all twelve character pose sheets. SpongeBob's
sheet must contain a substantial yellow body region. The typography tour
visits every client page, module editor and settings section.
