# Menu rendering

Pause Game has been removed. All Click GUI layouts and Maro's module sub-screens keep
the world running, including when an old config contained Pause Game enabled. Screen
GUI and Floating Window retain their normal controls. Minecraft's own pause menu
retains its pause behavior; the foreground phone remains live.

Stretch Res now defaults to 150% strength. Its Stretch Amount slider runs from 100%
(the original effect) to 250%. New 3:4 and 2:3 presets and Custom Ratio down to .50
allow a wider look. The same effective aspect is used for world rendering, culling,
ESP projection and first-person items; HUD and menu projections stay unchanged.

Background Blur marks the main GUI's background boundary before submitting menu controls. It works for Window, Panels, Screen GUI, and Floating Window. Floating Window is world geometry, so its world blur runs before drawing the pane instead. An enabled Maro toggle uses at least radius 5 while its menu is open in a world, even if Minecraft's Menu Blur is zero. The saved Minecraft option is preserved and applies normally outside Maro. Text, buttons and tooltips remain sharp.

World menu canvases render at up to twice the framebuffer dimensions, bounded by a 4096-pixel long edge and ten million pixels. Text is rasterized at the canvas resolution, scissors scale to that same target, and the projected menu samples with clamped linear filtering. The world itself remains at the original resolution. Closing the menu frees the Screen GUI target and buffers.

Screen GUI maintains a world anchor while facing the viewer. Floating Window also adjusts its facing when the player moves, while preserving drag-bar placement. The floating window uses the configured background opacity, a dark readable surface, and a subdued border. Category titles, category icons, utility headings and section titles share the same light Maro blue across panel headers, sidebar labels and window headers.

Menu text defaults to 90% of its previous size. **Settings > Menu Text Size** adjusts text independently of panel dimensions and HUD text. Measurement, fitting and input hit handling share the same size scope; the glyph renderer still rasterizes at the final pixel size.

The main title menu is 20% smaller, including its logo, labels and native button hitboxes, centered with more space around its content. Loading screens retain their existing size.

The locked Anti-Cheat Off category in Panels opens its existing confirmation in a compact overlay. Confirming reveals the module rows in that panel; Cancel or Escape dismisses the overlay. Entering this section no longer changes to the large Window layout. The confirmation is renewed on each menu opening, as before.

`-PmenuBlurTestOnly=true` verifies actual background pixels against foreground controls with Minecraft blur disabled. `-PaccountsTestOnly=true` additionally exercises both layouts with Screen GUI, clipping/dropdowns, normal mouse look, walking, crosshair clicks, focus changes, close/reopen, resizing, and Floating Window movement and remembered placement.
