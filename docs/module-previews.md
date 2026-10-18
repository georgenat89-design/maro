# Module previews

Panel and Dropdown choose a subject explicitly instead of assigning every visual module a player. Settings update appearance while the preview is stopped. Animated subjects have Play and Stop; Panel opens a separate preview box at the upper right beside the settings editor. Close or Escape hides the preview while keeping the settings; Show preview reopens it. Controls use pages, so the preview does not push them below the viewport.

| Module | Subject and controls |
| --- | --- |
| Custom Sky | Actual procedural sky shader for all 16 presets, or the imported panorama/skybox/wrapped picture. Independent playback clock; brightness, speed, fit, image turn and mirror affect the preview. |
| Geeked | Actual effect shader on the current world image, with a Live world / Scene sample option and Play/Stop. |
| Emotes | Select an emote using the arrows; Play/Stop poses a native player with the actual emote animation. Uses configured speed without starting a gameplay emote or changing perspective. |
| Item Inspection | Actual knife/glove geometry, materials and InspectionMotion routines; configured knife, size, finish, reflections, duration and alternate routine. |
| Weapon Inspect | Selected knife/held weapon with its configured inspection choreography and Play/Stop. |
| View Model | Native first-person main/off-hand items with configured position, rotation, scale and swing style. The preview uses a fixed showcase camera. |
| Swing Speed | Native item swing at the selected speed. |
| Spin Bot | Preview character rotates at the selected speed and direction. |
| Jump Circle | Visible idle ring at the character’s feet. Play repeats a jump/landing cycle, matching Jump, Land, or Both triggers and the configured radius, lifetime, color, logo, and glow. The floor stays fixed while the character jumps. Stop resets to the idle sample. |
| Trajectories | Clearly labeled example throw, showing the arc, moving pearl, timer and landing marker. It does not throw a real projectile. |
| Fake XP | Native level/bar sprites, selected progress and optional animated fill. No real XP is edited. |
| Fake Block | Source item to replacement item, including replacement glint. The source remains original even when spoofing is active. |
| Fullbright | The same textured deepslate cave before/after illustrative lighting at the selected strength; actual world lighting and brightness options are untouched. |
| Base ESP | Clearly labeled detection example, with storage objects, box color/alpha/style/width and optional chunk marker. It inserts no detection data. |
| Spawner Notifier | Spawner model, configured pillar/outline and example notification label. |
| Spotify HUD | Clearly labeled sample track/card, playback bands or wave/ring, mini/expanded layout and theme. Preview does not start Spotify, change volume or control media playback. |
| Stretch Res | Normal/configured perspective comparison using the same horizontal scale as the world projection, including ratio and stretch amount. |
| Custom Fov / Key Zoom | Normal/configured FOV comparison using the same FOV and half-angle zoom formulas. |
| Pet | All 13 native companion types, baby/collar/size and local animation. Never added to a world or ticked by AI. |
| Player ESP / Custom Model / Cosmetics / Nametags | Correct native player, model, accessories or labels; drag/Front/Back/Reset, zoom and playback. ESP uses its real preview shader. |
| HUD elements | Own HUD rendering fitted to the box, with a larger view for keyboard, Radar, Staff Watch and other overlays. Radar can preview while disabled. |
| Custom Crosshair / Custom Totem | Actual crosshair renderer or processed totem texture/icon. |

Chest Preview has been removed from the module registry. Old config entries cannot turn it on. World utilities without an accurate isolated renderer show their settings without an unrelated portrait; dedicated image/model editors retain their own previews.

Examples are explicitly labeled when they illustrate detection or gameplay data. They do not claim to show a real discovered base, spawner, projectile or current Spotify track. Actual world results still depend on gameplay and server information.

GPU preview passes use independent framebuffers capped at 640 by 360, reuse buffers and release owned resources on close. The client never enables a module or changes real camera, movement, inventory, XP, detection records, media playback or system volume to preview it. Native input/rendering tests cover subjects, all sky presets and imported images, local playback, return navigation, unmodified real state and cleanup. Existing menu, ESP shader, scale/scroll and Panel/Dropdown checks also run.
