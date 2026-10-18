# Build 690 merge and portrait/Fake Pay update

Merged the actual `build-690` tag, commit
`742cd6a2e93d472c9260dc6ab6a1e5cd1ee140a8`, from
[georgenat89-design/maro](https://github.com/georgenat89-design/maro/releases/tag/build-690).
The supplied Downloads JAR matches GitHub's release asset SHA-256:

```text
9c39d6bb47b975b99ab1127742eca6b4460787e5e55df47b16b88bbd20ef894a
```

Build 690 adds the Spatial menu: a glass pane fixed in the world, crosshair
controls, a grab bar for moving the pane/changing distance, a close dot, and
darkened glass with the world visible around it. It also adds the Caustics sky.

Merge resolution retains Maro's current sidebar navigation, GUI Size scaling,
Panels/Window layouts, Screen GUI movement/input, settings migration and font
handling. Spatial uses its own rendering/input mode. Sidebar click positions and
glass bounds are transformed to canvas coordinates, so picking remains aligned
at different GUI scales. Native cursor capture is released before Spatial takes
ownership, avoiding two simultaneous sources of look movement. Existing Screen
GUI setups are preserved during the one-time Spatial migration.

The combined client retains phone messaging/requests/notifications, Spotify
playlists, TikTok For You/Entertainment/Gaming, teleport combat controls, Auto
Replenish, manual Trident Boost, tracers and centered Staff Watch. Auto Trident
and the five requested anime designs are removed. The roster contains 30 models,
including 19 new portraits and rebuilt human faces/anatomy. The wardrobe's list
clip and partial-card hit areas keep the footer accessible.

[Fake Pay](fake-pay.md) replaces the old one-shot form with local `/pay`
interception, action-bar/sound options, strict decimal validation and optional
render-only sidebar deductions. No Glazed implementation is bundled.

Native production-client validation with Meteor:

```powershell
./gradlew.bat build runProductionClientGameTest -PpresentationUpdateTestOnly=true `
  '-PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar' --console=plain
```

This exercises all 30 character meshes and poses, the wardrobe at GUI scales
2/3/4, Fake Pay packet suppression and real sidebar rendering, Spatial crosshair
clicks/grab/move/close, Panels/Window Screen GUI movement and projective picking,
manual Trident Boost, and the Caustics shader. Test assets and companion binaries
are excluded from the production JAR. Full corresponding source and license
notices accompany the artifact.

Validated on October 12, 2026: the combined production-client suite passed in
1 minute 31 seconds with Meteor Client 1.21.11-85 installed. A focused Spatial/
Screen GUI pass also verified real page clicks at GUI scales 2/3/4, pause settings,
both existing layouts, native walking/mouse look, resize and resource cleanup.
