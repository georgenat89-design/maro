# PacksMC, keyboard arrangement and phone fixes

Visuals → Gameplay Textures → PacksMC replaces the Bauloo collection browser.
It displays exactly the first 50 public packs in PacksMC's Trending order.
Search and favorites filter that bounded set; there is no Load More request.
Refresh obtains the current first 50. A bundled metadata snapshot and disk
catalog cache keep the browser useful when the listing is temporarily unavailable.
Screenshots load asynchronously from PacksMC and retain their aspect ratio in
512×288 preview textures. Right-click a card or click Enlarge for a bigger view.
All creator names remain visible. Pack terms and original page links are retained.
The ordinary Maro JAR contains catalog metadata. Preview images are saved in a bounded disk
cache after their first download, so the saved catalog can show previews offline.
Refresh retries failed images and prunes previews outside the current first 50.

**Download pack** opens the selected pack's original page in your default external
web browser. **Browse website** opens PacksMC's listing. **Creator page** opens the
selected pack's page even if the ZIP is already saved. These actions leave the game
menu in place and do not create an embedded browser. Follow the site's normal
access/version flow, download the Java ZIP, then import it in Minecraft.
The standard client has no embedded pack collection. Previews retain the one-time
antialiased corner mask matching the rounded card.
**Import downloaded ZIP** and drag-and-drop remain available for existing downloads.
Imports reject unsafe paths/executables, bound file sizes, and keep the original
bytes. The pack applies after Minecraft recognizes it. Closing during installation
or disabling Gameplay Textures prevents a late completion from re-enabling it.
**Apply pack** reapplies it later; Installed manages priority and compatibility.
Disabling the module restores the usual pack selection. Disabling during an import
prevents the completion from enabling the module again. Imported full packs replace
a prior PacksMC selection while retaining unrelated local packs.
Packs+ and paid cards stay in their original first-50 positions and use the site's
normal access flow.

## Saved packs and earlier collections

The earlier optional private client with library included 49 archives and 50
previews. The current small client uses external website downloads instead.
Previously installed resourcepacks remain available without redownloading them.
Each saved card gets an **Apply** action, also available in the inspector, which
uses the original local archive even without a network connection. A missing ZIP
is offered for downloading again when the library is rescanned. Maro's named ZIPs
are rediscovered after importing a client/profile without the saved associations.
ZIPs belong in the active Minecraft instance's `resourcepacks` directory, not
`mods`. A local installation collection can carry these named ZIPs, the catalog and
its preview cache alongside the Maro JAR; extract it into the chosen Minecraft
instance. The ordinary build and source archive contain no third-party pack ZIPs;
the earlier separate private library variant embeds the user's downloaded collection.
PacksMC's download gates and paid/access requirements still use its normal pages.

## Individual keyboard positions

Keyboard Strokes → Choose Keys still selects visible keys from the full keyboard.
Arrange Keys opens a canvas where each key can be dragged separately. Snap can
be toggled; Shift uses finer movement. Arrow keys move the selected cap by five
HUD units, or one with Shift. Reset layout / Delete restores physical keyboard
positions. Positions persist in profiles and follow selected game actions when
rebound. Selection and whole-HUD placement remain independent.

## Phone fixes

SpotifyMedia previously supplied the HUD's rounded album image to the phone.
That larger mask cut out the phone artwork's corners before its smaller mask was
applied. The media worker now retains original pixels alongside the HUD mask;
the phone applies only its own antialiased mask.

Video wheel input now reaches videos while mouse look owns the cursor and game
interaction is off. With game interaction enabled, the wheel controls the hotbar;
press I or hold Alt to control the phone. Focused video mode translates wheel
steps into browser touch start/move/end events. A short cooldown and a single
in-flight swipe limit repeated trackpad steps. Previous/Next arrow buttons provide another navigation route.
Full website mode retains ordinary pointer-position wheel scrolling. Feed and
menu overlays consume their own input. TikTok sign-in, availability and feed
content continue to be handled by TikTok in the isolated browser profile.

## Validation

Minecraft 1.21.11 / Java 21 native production tests run with Meteor 1.21.11-85.
Validated against the remapped production JAR:

- PacksMC: exactly 50 live cards and reachable previews; creator credits and
  canonical pages; search/favorites, enlarged previews and final row at GUI
  scales 2/3/4; unchanged ZIP import, application, profile persistence,
  disable/restore and invalid ZIP rejection. Native run: PASS.
- Keyboard: native individual drag, stable neighboring keys, fine nudge, saved
  positions, rebind preservation and physical reset. Existing full-keyboard,
  Panels/Window, Screen GUI and Acrobatics checks: PASS.
- Artwork: actual shared Spotify decoder retains original corner pixels; the
  phone mask restores those pixels and keeps transparent outer corners. Native
  screenshot reviewed with a black cover, where corner/backplate gaps are most
  visible. Playback/hotkeys, hand swipe, game interaction, sharing and local MP4
  regression checks: PASS.
- Browser videos: native H.264 playback, pointer/typing, next swipe, previous
  button, full website wheel, accumulated trackpad steps while mouse look owns
  the cursor, Phone/iPad topic routing, focus/crop restoration and browser cleanup:
  PASS (final production run completed in 51 seconds).
  An anonymous live TikTok page is also captured for visual review; no personal
  account or personalized FYP is part of the fixture.

Commands (set JAVA_HOME to Java 21 first):

```powershell
./gradlew.bat build compileGametestJava
./gradlew.bat runProductionClientGameTest -PtextureStylesTestOnly=true -PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar
./gradlew.bat runProductionClientGameTest -PkeyboardStrokesTestOnly=true -PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar
./gradlew.bat runProductionClientGameTest -PspotifyPhoneTestOnly=true -PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar
./gradlew.bat runProductionClientGameTest -PphoneTikTokTestOnly=true -PliveTikTokTest=true -PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar
```

The historical `textureStylesTestOnly` switch now selects the PacksMC suite.
The combined phone run verified media/sharing/local playback before an earlier
scroll implementation failed. The separate final browser run verifies its replacement.
