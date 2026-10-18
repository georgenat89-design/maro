# Gameplay Textures and phone polish

Visuals → Gameplay Textures is the single entry for PacksMC, Modrinth, Installed
packs and Appearance. The master switch applies the selected loadout and restores
your usual packs when switched off. Appearance has its own switch within the module.
Old Texture Packs, Better Textures and Better Looks profiles migrate once, preserving
pack metadata, priorities, favorites, appearance settings and an existing bind.
An existing Gameplay Textures profile always takes precedence over legacy entries.

The compact charcoal overview uses a sidebar, a preview stack of the current
collection, three library/appearance shortcuts and a restrained master switch.
It retains space around the window at every GUI scale, with hover and Tab focus.

PacksMC's first 50 Trending packs retain their previews and creator credits.
Download pack opens the original creator page in the operating system's default
web browser. Browse website opens the PacksMC listing; Creator page opens the
selected pack even when it is already saved. No embedded browser replaces the
game screen. Import the completed ZIP or drag and drop it onto its selected card.
The standard client includes metadata rather than the large pack collection.
Saved cards and the inspector have an Apply action that uses the local ZIP offline.
Maro discovers its named pack ZIPs even if its import-mapping config was removed.
Downloaded previews are cached on disk for offline browsing; refresh drops images
outside the bounded catalog and retries failed previews.
The private library also supplies offline preview bytes. Decoding masks their
corners once, with antialiasing, so images remain inside the rounded cards.
Modrinth retains one-click installation. Installed contains
local packs and shared priority controls. See [PacksMC](packsmc.md) for details.

Phone video navigation keeps one pending swipe while a gesture finishes, so repeated
wheel input and next/previous buttons continue responding. Small trackpad deltas
accumulate into a swipe; full-website mode retains ordinary wheel input.

Spotify retains the outgoing album while new artwork loads. Once the next cover is
ready, the opposite hand and both covers share one 850 ms clock: reach, contact and
slide, then withdraw. Covers slide fully opaque beneath a fixed rounded artwork
viewport, so the outer corners stay round and the moving seam has no corner holes.
The viewport geometry is cached at rest. The hand
uses cached skin geometry with separate index, folded fingers, thumb and shaded
wrist faces in one batch. At most two album textures are retained during a slide;
the outgoing texture closes at completion, and no swipe mesh is submitted at rest.

The Circles Display Name control has been removed. Jump Circle uses its standard
label; old saved rename values are ignored.

Cosmetics includes 66 swords and 37 pickaxes at 64×64, plus the existing 25 shovels
and 25 tridents. Sixteen new swords and twelve new picks use split/forked blades,
hooks, saw teeth, crystal points, drills, jaws, wings, anchors and hammer heads.
Existing names remain stable. Assets are original deterministic pixel art generated
by `tools/cosmeticgen.py`, with native item definitions and held-item models.

## Validation

Run with Java 21 and the optional Meteor companion path configured:

```
./gradlew.bat build compileGametestJava
./gradlew.bat runProductionClientGameTest -PgameplayUpdateTestOnly=true
./gradlew.bat runProductionClientGameTest -PtexturePacksTestOnly=true
./gradlew.bat runProductionClientGameTest -PspotifyPhoneTestOnly=true -PphoneTikTokTestOnly=true
./gradlew.bat runProductionClientGameTest -PjumpCircleTestOnly=true
```

The texture suite exercises legacy migration, appearance hooks, native menus at
three GUI scales, a real browser attachment download followed by installation,
application and staging cleanup, two-pack collection advancement without changing
the loadout, resume, Skip, library discovery without metadata, one-click Apply,
pack priority/restoration, the 50-card catalog
and expanded cosmetic previews. The phone suite uses isolated playback and a
local multi-page video feed to check repeated forward/reverse swipes, cursor
ownership, delayed artwork, synchronized clocks and completed texture cleanup.
The fixtures do not sign into Spotify, PacksMC or TikTok accounts.
