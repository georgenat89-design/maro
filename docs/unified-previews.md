# Unified module previews

Maro uses one preview framework inside the existing ClickGUI and Minecraft
instance. A supported module has **Settings** and **Preview** tabs; Preview opens
an independent middle panel between the module browser and the settings editor.
The settings remain editable beside it. Unsupported modules have no Preview tab.

The written reference layout is implemented: a tall rounded dark card, thin
purple border, purple status dot, **LIVE PREVIEW**, an always-visible **PREVIEW
ONLY** badge, a central subject and bottom controls. The request referred to two
screenshots, but those image files were not present with this text attachment;
pixel-for-pixel matching to those references was therefore unavailable.

## Layout and controls

The standard preview is 210 logical units wide and follows the settings panel's
height. Responsive calculations include all three columns when fitting the menu.
In the flat menu, the middle panel collapses when it would leave less than 130 physical pixels for
the preview, 160 for settings or 230 in height. The Settings tab also hides it.
Settings remain accessible at those sizes, and model images retain their aspect.

Player and pet previews have **FRONT**, **BACK**, **RESET** and **PLAY / PAUSE**.
Dragging within the subject rotates it; scrolling there adjusts zoom between
0.55 and 1.0 of the fitted size. Panel hit regions consume their input. The
preview does not turn the gameplay camera or activate controls beneath it.

The local 1.0.4 Screen GUI change keeps Preview attached to the fixed world panel.
Its reserved three-column canvas does not collapse or resize when Preview opens.
Aim and click to begin a model or slider drag; held relative mouse motion updates
the preview pointer without turning the gameplay camera, and release restores
ordinary look. See [stationary Screen GUI](stationary-screen-gui.md) for placement,
picking and validation status. This supersedes 1.0.3's flat Preview fallback only
for Screen GUI.

While Preview is selected in Floating Window, the menu still temporarily presents
flat with an ordinary unlocked pointer. Its saved preference remains unchanged.
Selecting Settings, closing the module panel or navigating away restores the
configured floating pane and its pointer capture. Switching modules also
clears stale settings drags and field focus, keybind capture and open dropdowns;
the browser's independent search field keeps its own focus behavior.

Pause retains the animation position. Reset restores the default angle, fitted
zoom and animation position; it does not reset module settings. Custom Model's
**SELF / OTHERS** selector chooses which configured model to inspect without
changing either selection. The wardrobe's Preview action preserves its selected
target. Emotes adds a presentation-only emote chooser. Animated non-model
subjects get playback and reset; static subjects do not show player controls.

## Provider coverage

`PreviewProviders` registers module types and the explicit `HudElement`
capability. Each provider declares content, controls, caption, animation support
and whether it uses the actual subject renderer. Module names, categories and
enabled state do not determine support.

| Content | Supported modules | What the preview shows |
| --- | --- | --- |
| Player | Custom Model, Cosmetics, Skin Changer, Player ESP, Emotes, Hats, Acrobatics | Private native player state with the current skin and applicable equipment, or the selected custom model/skin. Uses the actual model, accessory, hat, emote, acrobatic transform or Player ESP silhouette shader. |
| Entity | Pet | A cached detached native companion, its selected type, baby/collar/size settings and representative motion. |
| Shader | Custom Sky, Geeked | The actual shader. Custom Sky includes its selected image mode; Geeked processes an independent neutral scene rather than an already-processed live frame. |
| Item | Item Inspect, Weapon Inspect, View Model, Swing Speed, Custom Totem | Selected/current item geometry, materials, supported transforms or inspection motion; Custom Totem uses the processed artwork. |
| Native HUD | Custom Crosshair, Fake XP, HUD-capable modules | Their own renderer. Fake XP uses configured vanilla bar/level artwork without editing real XP. HUD-capable modules include Armor HUD, Inventory HUD, Compass, Keyboard Strokes, Radar, Staff Watch and Coords Fly. |
| Native card/label with sample data | Chest Preview, Nametags | The actual container-card or label renderer supplied with example contents/identity. No chest contents are requested and no fake entity is inserted. |
| Item comparison | Fake Block | Source and replacement item models, including replacement glint. |
| Illustrative sample/comparison | Jump Circle, Block ESP, Storage ESP, Base ESP, Fullbright, Stretch Res, Custom Fov, Key Zoom, Spawner Notifier, Spotify HUD, Spin Bot, Trajectories | An explicitly labeled sample of the selected visual choices, projection, direction, notification or data display. |

“Actual renderer” describes the rendering path, not a promise that the sample
recreates every gameplay condition. The HUD can use current available data;
Nametags and Chest Preview use example data. Pet never runs AI or joins the live
world. Player ESP's portrait represents its silhouette/model shader, not world
occlusion, distance, boxes, tracers or detection results.

The item showcase uses actual geometry/material helpers and inspection
choreography with the selected clip duration and speed. It omits deploy/equip
transitions, camera sway/idle motion and persistent slash-trail, sparkle or glow
history. View Model uses a fixed showcase camera. Acrobatics shares the actual
model transforms and limb poses; it does not flip the real camera or emit world
particle trails/landing puffs. Jump Circle is a projected ring sample: its 2D
logo/glow and music response do not reproduce the complete world effect.

Illustrative detection and trajectory samples do not scan chunks, discover
bases, read unopened remote inventories, throw projectiles or claim to show
actual server data. Spotify's sample does not control media or system volume.
Fullbright's comparison does not change world light or Minecraft brightness.
Modules without a meaningful isolated subject, including temporal world effects
without an appropriate sample renderer, remain settings-only.

## Asset browsers and isolation

Custom Model's wardrobe and Cosmetics' Asset Browser retain selection lists,
search, categories, item icons and sizing controls. Their shared Preview actions
open the selected module's common panel; duplicate character viewports and
playback controls are removed. Opening the common preview does not apply a
pending choice or enable a module. Cosmetics selections are explicit settings
edits, and **Save look** saves configuration without enabling Cosmetics. Choosing
a hat does not automatically enable Hats; the explicit None choice can disable
Hats. Existing explicit enable controls remain deliberate actions.

Previewing a disabled module does not enable it, call gameplay animation
actions, send gameplay packets, move the actual player or modify inventory. A
private player render-state instance carries the subject and its own clock.
Disabled item cosmetics are allowed only during that private extraction scope,
which is restored in `finally`; opening the asset browser no longer applies
disabled cosmetics to the live player or held items. Detached pets are created
once per selected subject and never added to the world. Emote and Acrobatics
samples do not register live moves or change perspective.

The GUI queues only its subject, not another world render or game session.
Native special renderers and shader targets reuse their resources until a size
or subject change requires replacement. Render target, projection and clipping
state are restored around owned passes. Preview model animation caches use a
reserved ID and are cleared independently of real-player animation state.

Hidden, collapsed, unsupported and closed previews stop their clock and perform
no subject rendering. Module changes release the old subject. Closing, resource
reload and client shutdown release owned targets, detached state and disabled
subject assets; re-opening prepares them again. Resource reload preserves
presentation time while rebuilding the resources.

## Streamproof and validation

The panel is part of Maro's local GUI and follows its existing Streamproof
presentation path. Scene effects suspended by Streamproof can still be
inspected through their private renderer without enabling them in the world.
This does not extend Streamproof's verified capture scope: operating-system
window/display capture, OBS, Discord, WGC and DXGI remain unverified until
actual capture-application checks are completed. See [capture limits](streamproof.md).

The 1.0.3 native suite passed on October 25, 2026: **38 supported modules and
49 unsupported modules** were audited. It verified disabled subjects, actual
GUI controls, pause/reset, SELF/OTHERS, current skin/equipment, native ESP color
and paused item-setting pixel changes, player/inventory/entity/packet isolation,
both world-menu input preferences, switching/hiding, three window sizes, GUI
scales 2/3/4, native fullscreen transitions, resource reload and owned item-target
cleanup. The local GPU framebuffer contained the preview; the clean Streamproof
GPU target excluded it. These are internal GPU checks, not OS capture tests.

The same full suite also passed with Sodium 0.8.15-beta.1, ImmediatelyFast
1.14.3, EntityCulling 1.11.3, Lithium 0.21.4, FerriteCore 8.2.0 and Meteor
1.21.11-86. The final normal build passed, and all 12 bundled character assets
are byte-identical to 1.0.2.

Both native runs logged transient OpenGL blit debug messages during fullscreen
resize transitions. The settled preview, restored window layout and final
OpenGL error-state assertions passed. Transition-frame smoothness across graphics
drivers remains a limitation; these logs are retained in the validation receipt.

```powershell
.\gradlew.bat build remapGametestJar --offline --no-daemon --console=plain
.\gradlew.bat runProductionClientGameTest -PunifiedPreviewTestOnly=true --offline --no-daemon --console=plain
```
