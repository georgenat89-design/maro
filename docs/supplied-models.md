# Supplied character models

Enable Visuals > Custom Model and choose SpongeBob, LeBron James, Chun-Li, Skeleton,
Jeffrey Epstein, Donald Trump, Michael Jackson, Roblox Bacon Hair, Verity, Minion or Travis Scott in the
Character Wardrobe. The actual supplied meshes/textures are included. The wardrobe
supports live rotation, self/other-player selections, held items, first-person arms and
Low/Medium/High detail. The module starts disabled; its default selection is SpongeBob.

The built-in catalog includes these eleven supplied files. Existing unrelated client
features remain unchanged. User GLB imports still load from config/maro/characters.

SpongeBob preserves the face/body as a single part and assigns whole connected arms and
legs, lowering the source T-pose to a neutral stance. Its 2,558 source triangles are
rendered double-sided, retaining tiled UVs. Its portable GLB includes a walk cycle.
LeBron's textures and mesh are retained; the unparented basketball prop is omitted from
the wearable model. Chun-Li uses the supplied skeleton and three baked detail levels.
Skeleton retains the supplied bone geometry and diffuse material colors. Its unrigged
source is separated into complete anatomical arm/hand shells, so pieces of the ribs and
pelvis no longer appear beside the first-person wrists. All 20 arm shells, including the
thin finger bones, retain their source geometry at all three detail levels. The grip is
in the palm, with shoulder/elbow pivots aligned to the arm bones. The original source has no animation rig.
Jeffrey Epstein and Donald Trump preserve the supplied skins, materials and source rigs.
Michael Jackson preserves the supplied mesh and blue-shirt appearance, with limb groups
generated from its geometry. Roblox Bacon Hair keeps its supplied texture atlas and Mixamo rig, with
head/limb animation, held tools, first-person arms and three ready-to-render detail levels.
These characters follow Maro's Minecraft movement and
item poses. The portable Trump GLB retains its supplied running animation for 3D editors;
the baked in-game character uses the shared movement renderer.
The supplied Trump FBX export's inverted body scale and normals are repaired against
its original object scales so its body, face, hair and skeleton align correctly.
Conversion fixes the importer's vertex-index map type and UV-seam welding key.

Attributions/licenses are included in THIRD_PARTY_NOTICES.md and LICENSES. Source model
files and reproducible converters are in tools/model-sources and tools/import_spongebob.py,
tools/pack_character_archive.py, tools/BakeCharacter.java and tools/BakeSkeleton.java. The portable GLBs are kept
in source but excluded from the runtime JAR, which contains the ready-to-render ANC data.

Custom Model uses the actual camera's detached state to decide whether to show your
full body. Freecam therefore displays your selected character and held tool even when
the saved perspective is First Person. Nametags > Self uses the same camera check.
Leaving Freecam restores the normal first-person body/hand behavior.

Cosmetic pickaxes attach at their lower-handle grip rather than the mesh center.
The item transform compensates Minecraft's half-block model-centering offset, with
separate left/right-hand correction. Resizing a pickaxe keeps its grip in the same
palm position. This also centers inventory icons correctly; direct first-person swing
and gallery preview transforms are preserved. Native checks cover all five tools,
both hands, three sizes, and supplied characters with cosmetic and vanilla tools.

The wardrobe now has live minus/plus Model Size controls (50–200%, in 5% steps).
Click the percentage to reset to 100%. The same saved setting is available in module
settings. Third-person held items automatically follow the character's hands and scale
with the body while its feet stay planted. Nametags, their armor row and held-item
captions follow the selected character's scaled height, with vanilla behavior for
players who have no replacement model. First-person camera arms/items keep their
existing camera-space sizing. The replacement is visual; player hitboxes stay unchanged.

Stray torso faces below the hips are reassigned to their matching legs while the
character loads on its background worker. Imported shared borders at the waist, neck
and shoulders are welded once. A short blend makes these border vertices
follow the torso during limb motion, closing the gaps between separately animated mesh
sections; matching normals are joined too. Broader hinge bands and continuous skin
weights smooth the elbow and knee bends. Size edits reuse the loaded mesh and atlas.
Production-client checks replay deferred render commands for the original eight characters and Travis Scott in
walk, crouch and swing poses at all three detail levels, check coincident borders, finite vertices and normalized
normals, and verify 50/100/200% body/weapon transforms for both hands.

Verity and Minion are imported from the new supplied ZIPs. Verity keeps its original
single-piece shape without fabricated arms/legs; the fallback importer now assigns
non-humanoid geometry to the body correctly. Minion uses geometry-based arm/leg
groups, original colors/textures and three bounded detail levels. JPEG textures are
embedded with their correct glTF MIME type.

Nametag, armor and item captions use the rendered head transform each frame,
following the character in elytra flight, swimming, crouching and size changes.
Headless designs use the body bounds. Hidden/culled players use pose-height fallback.

Travis Scott retains the supplied Fortnite-style mesh, body/head/accessory textures
and skeletal limb assignments. The original 275-joint rig is used offline to prepare
head, arms, hands and legs for the shared movement renderer. Low/Medium/High geometry
and a bounded texture atlas are baked before shipping; selecting or resizing the model
does not re-import the source. The portable GLB preserves the supplied animation.
Choose Travis Scott for yourself or other selected players in the Character Wardrobe.

Undead Fuhrer is imported from the supplied Slender Fortress ZIP. Its 66-joint
ValveBiped rig, body/head textures, elbows, knees and hands are retained. Three
bounded detail levels are baked offline; select it for yourself or other players
in the Character Wardrobe. Attribution is in LICENSES/undead_fuhrer-CC-BY-4.0.txt.
