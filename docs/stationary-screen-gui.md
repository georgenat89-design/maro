# Stationary Screen GUI

The local 1.0.4 change fixes Screen GUI's placement and input while preserving
Maro's current design, fonts, categories, settings and unified previews. It does
not introduce a visual redesign. The normal flat GUI and Floating Window retain
their existing presentation and controls. No video file was available with the
request; implementation follows the written behavior.

## Placement and rendering

The earlier world panel changed its facing as the camera moved. Its viewport
layout also influenced the panel geometry, and selecting Preview temporarily
returned Screen GUI to a flat menu. These behaviors prevented the menu from
acting like a stationary object in the world.

`ScreenGuiPose` now captures one upright placement from the opening camera's yaw,
eye position and Screen Distance. Opening FOV and viewport aspect establish the
initial physical dimensions; later changes to the camera, FOV, window size, GUI
scale or menu size do not move, turn or resize that world geometry. Reopening
the menu creates a new placement. Changing Screen Distance affects the next
placement rather than pulling the open panel toward the camera.

Screen GUI reserves a fixed 1080 by 480 logical canvas from the start. The module
browser remains in the left column, the optional preview uses the middle, and
settings use the right. Hiding Preview leaves the middle column available;
opening settings never recenters the browser or resizes the world panel. Unlike
the flat responsive layout, an anchored preview does not collapse because of
window size. The panel can leave the viewport as the player looks or walks away.
Its title cannot be dragged to change its placement.

The entire GUI, including settings, preview subjects, open dropdowns and tooltips,
is rendered into one texture and projected with the same fixed plane geometry.
Raster fitting compensates for window and GUI-scale changes. Screen GUI omits
the flat menu's fullscreen background dim, contours and blur; a custom background
stays clipped inside the browser panel. The existing colors and control shapes
remain intact.

The surface owns Minecraft's standard special GUI renderers, including player
portraits, and routes custom item previews to the renderer consuming that same
canvas. Models and inspection items are therefore inside the plane's texture,
with their own reusable native targets; they are not separate screen overlays.

The plane's world basis stays fixed when approached from either side. Back-side
UVs are folded horizontally so labels remain readable; this does not turn the
plane toward the viewer. World view and projection matrices supply perspective,
including the current FOV and camera effects. A terrain depth snapshot is copied
before the hand pass clears depth. The final composite discards fragments hidden
by that terrain depth, instead of drawing the menu through solid scenery.

## Interaction

Walk and look normally, then aim the crosshair at a control and click or scroll.
Screen GUI uses the inverse of the rendered projection for hover and input. A
pick must intersect the finite canvas in front of the camera and pass a CPU
world raycast from the rendered near ray against block outline shapes, ignoring
fluids. This includes view-bob and hurt-effect projection translations. Occluded, edge-on,
behind-camera and missed picks cannot click or scroll the menu. Transparent
reserved space outside the actual panels does not scroll the module browser.

Holding a model rotation, slider or scrollbar starts a local drag. Relative mouse
motion moves a virtual pointer on the plane and updates that widget; it does not
rotate the real camera. A small pointer marks the held interaction. Walking keys
remain available. Already-started drags can continue beyond the canvas edge so a
slider can reach its bounds; world geometry still blocks the drag ray. Release
restores ordinary mouse look. Raw deltas
and camera smoothing are consumed during the drag so releasing it does not apply
the widget gesture to player look afterward.

The Preview tab remains attached in Screen GUI. Floating Window continues to
use its existing flat unlocked pointer while Preview is selected, preserving its
saved preference and restoring its pane when Preview is left. Typing and keybind
capture keep their existing behavior; movement pauses while a text field is
focused.

## Lifecycle and limits

Module switches, hiding a preview, closing settings, closing the menu, resize,
focus loss and overlays cancel the relevant held interaction. Preview subjects
retain the shared lifecycle: hidden subjects pause and release their owned
resources, and reload rebuilds resources without activating gameplay modules.
Looking away from the anchored panel stops preview subject rendering and clock
advancement while preserving its angle, zoom and playback state. Looking back
resumes from that presentation without adding elapsed offscreen time.
Closing or reloading releases the Screen GUI texture, renderer, depth snapshot
and GPU buffers. World changes establish a new anchor. If depth or projection is
unavailable, the world panel is withheld rather than shown through terrain.

Perspective naturally makes distant or oblique text smaller, and the panel has
no usable area when viewed edge-on. Texture resolution is bounded, so this does
not promise unlimited readability at every distance. Rendered depth and CPU
outline shapes are different sources: transparent blocks, foliage and fluids
can differ between visual occlusion and picking. Nonstandard third-party world
renderers or projection changes require separate compatibility validation.

This change does not extend Streamproof's verified capture scope. OS window or
display capture, OBS, Discord, WGC and DXGI remain unverified; see
[capture limits](streamproof.md). The menu still uses the existing Minecraft
instance and world render.

## Validation

The final 1.0.4 build and three native runs passed on October 25, 2026:

- The stationary suite held world centre, basis vectors and physical dimensions
  invariant during actual W/A/S/D movement, a full yaw rotation, looking up/down,
  front/oblique/far/edge/back views, and window/GUI-scale changes.
- Native pixels matched projected test landmarks. Distance reduced the apparent
  size, oblique sides had different projected heights, and edge-on/away views
  disappeared without changing the anchor. Back-side labels stayed readable.
- Native clicks toggled modules/settings and selected dropdown entries. Sliders,
  tooltips, model drag/zoom and playback stayed attached; model dragging did not
  change the real player's view. Hidden previews stopped work/time and resumed
  their presentation when the player looked back.
- Subject-only red/blue Player ESP pixel changes and native item-scale pixel
  changes confirmed actual model/item rendering, beyond merely queuing states.
- A nearby foreground wall partially and fully occluded the menu and blocked
  clicks. Removing it restored interaction.
- Three window sizes and GUI scales 2/3/4 preserved geometry and picking.
  Resource reload rebuilt current targets while retaining the anchor. Closing
  released resources; reopening created a fresh anchor. Native pixels and picks
  also passed at 20-million-block coordinates and after sub-block movement.
- The same full stationary suite passed with Sodium 0.8.15-beta.1,
  ImmediatelyFast 1.14.3, EntityCulling 1.11.3, Lithium 0.21.4, FerriteCore 8.2.0,
  and Meteor 1.21.11-86.
- The normal flat/unified-preview regression suite passed all 38 supported and
  49 unsupported modules, controls, resource lifecycle and internal Streamproof
  targets. Floating Window's Preview pointer behavior remained intact.

The final stationary runs used synchronous graphics diagnostics and did not
reproduce the earlier invalid-enum messages during resource changes. The flat
regression still logs the previously documented fullscreen-transition blit
warning; this update does not claim to fix graphics-driver transition behavior.

Screenshots use small magenta corner markers inserted by the gametest only.
Those markers and diagnostic probes are absent from the delivered client JAR.

```powershell
.\gradlew.bat runProductionClientGameTest -PstationaryScreenGuiTestOnly=true --offline --no-daemon --console=plain
```
