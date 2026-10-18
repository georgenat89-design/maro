# Cosmetics: five 3D pickaxes

**Cosmetics → Pickaxe** contains only Driver, Merry Mint Axe (Minty), Leviathan Axe,
Cold Snap and Star Wand, plus None. The fourteen other 3D choices and all 37 older
flat pickaxe skins have been removed. Other cosmetic categories remain available.
Searching the module list for Custom Pickaxe, Fortnite, Minty or a model name finds
Cosmetics. Enabled legacy profiles for a retained model migrate into Cosmetics without
overwriting newer unified settings; removed models do not activate Cosmetics.

The meshes are reconstructed from the user's supplied recordings. Driver has a swept
club head, scored face, blue inset and perforated silver grip. Minty has diagonal candy
stripes, twisted black wire, colored bulbs and curved emissive flame layers. Leviathan
has a bearded axe head, rear spike, rune insets, fine engravings and a curved wooden
handle with leather bindings. Cold Snap is a frozen wooden branch with flowers and a
hanging cross-braced lantern. Star Wand has a beveled smiling star, tapered pink shaft,
blue spiral ribbon and bow. Geometry is cached once per tool. These remain recreated
models and motion curves; they are not the original Fortnite game meshes or animations.

Drag the full-resolution live preview to turn it, scroll over it to zoom, or use Front,
Back and Reset. **Swing** previews the motion and corresponding sound. **Options** opens
metal shine, optional outline, first/third-person visibility, inventory icons, swing
animation and sound volume. Pickaxe Swing uses a continuous windup, strike and recovery,
mirrored for the left hand. Disabling it restores the native hand motion.

Fifteen bundled Ogg samples are isolated from the five supplied recordings: equip,
swing and impact for each model. Source basenames, hashes and edit intervals are in
[pickaxe-audio-provenance.json](pickaxe-audio-provenance.json). Full screen recordings
are not distributed. `tools/build_pickaxe_sounds.py --references /path/to/recordings`
can reproduce the edits using imageio-ffmpeg. Pitch and timing are preserved, with
bounded gain and short edit fades. Real local swings and contacts trigger playback;
cooldowns and one voice per effect bound repeated events. Contacts immediately after
a swing do not double the recorded strike. Sound toggle, zero volume, disable and
session cleanup are retained. Minecraft master volume applies.

Only local rendering and audio change. Stack components, durability, mining speed,
attack timing and packets remain native. Explicit server item models take precedence.
Other players' tools keep their appearance. Streamproof's clean pass excludes these
models. The main-hand custom renderer defers to native rendering while using an item.

Native validation covers finite bounded meshes, all five gallery selections and held
models, continuous motion returning exactly to rest, left hand, third person,
inspection coexistence, outline/size/glint, player/item/context filters, restoration,
legacy migration, actual swing/mining sound hooks and toggle/volume/rate limits.

```sh
./gradlew.bat runProductionClientGameTest -PpickaxesTestOnly=true -PstreamproofTestOnly=true -PmeteorTestJar=/path/to/meteor.jar
```
