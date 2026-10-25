# GUI and hand placement

Maro's menu uses a charcoal shell, horizontal navigation built from its real
categories, a two-column module browser, and an independent settings panel.
Module selection, favorite stars, and enabled switches have separate controls.
The profile selector loads saved local configurations; Manage configs opens the
existing configuration page. Supported modules retain the shared preview dock.

Version 1.0.5 introduced Inter Regular/SemiBold and natural casing. Version 1.0.6
uses the requested bold condensed font and connected sidebar; see
`branding-font-sidebar.md`. The smooth renderer caches text at its physical
raster size, measures with the same font, and supplies platform fallback for
unsupported code points; native font providers retain Minecraft fallback.
Theme, font, and size remain configurable. Presentation revision 6 adopts the
new defaults once while preserving module values and keybinds. A prior default
GUI size 75 becomes 97; deliberate custom sizes remain saved.

Numeric settings support both sliders and compact typed value fields. Typed
finite numbers use the setting's existing step and range validation. Incomplete
input remains in the text field until completed or focus is released; the last
valid setting value is retained. Ctrl+A replaces the whole value. Unit suffixes
remain visible. Right-clicking a slider restores its default.

## First-person editor

Open View Model and select **Edit hand placement** below its description.
Click a physical left/right region, drag to move that hand, scroll to resize,
or Shift+drag vertically to change depth. Selected-hand readouts update live.
Linked mode mirrors horizontal deltas and applies matching Y/depth/size deltas;
toggling it does not copy one placement over the other. Reset selected and
Reset both affect only the editor draft.

Save & done commits and persists the placements and returns to View Model
settings. Escape discards the draft. Editing does not toggle View Model on;
its preview override exists only while the editor is active. Empty arms use
the native first-person arm renderer without inventory changes. Perspective
and normal input capture are restored on exit. Positions and size apply once
around the native hand-and-item render call, preserving normal item animations.

Existing Main/Off configuration keys remain compatible. An extra physical-arm
binding records which arm owns the legacy Main settings, so changing Minecraft's
main-hand preference does not exchange saved physical placements.

The menu's fixed 1080×640 logical canvas remains stationary in Screen GUI mode;
navigation, controls, previews, dropdowns, and tooltips use that same canvas.
The redesign does not introduce camera-following panel placement.

Stream Proof integration uses the existing clean/private GUI paths. The hand
editor adds a reusable pre-hand color snapshot and a private native hand pass,
while the clean target retains ordinary first-person rendering. It does not
render the world again. OS capture applications require separate verification.

## Validation

The focused native gate is:

```
gradlew runProductionClientGameTest -PredesignHandPlacementTestOnly=true
```

The restart stages use `-PhandPlacementRestartStage=write` followed by `read`
in separate JVMs, sharing only the disposable test profile. Native screenshots
and a final validation receipt are exported with the versioned build.

Version 1.0.5 passed the focused native suite, a separate restart JVM, the same
full suite with Sodium/ImmediatelyFast/EntityCulling/Lithium/FerriteCore/Meteor,
the stationary-world regression, all 38 unified previews, and the normal build.
Native mouse/keyboard controls, saved profiles, physical handedness, empty/held
hands, input and inventory isolation, normal swing/equip/shield use after exit,
window/GUI scales, FOV 50/70/110, fullscreen, reload and owned-resource cleanup
were exercised. Internal clean/private targets retain ordinary captured hands
and private draft transforms with one world render per frame.

The native screenshots are unedited. Existing Minecraft/driver blit warnings
occur during fullscreen transitions; settled rendering and GL checks pass.
OBS, Discord and other OS capture applications have not been verified. Only
the earlier overall/config reference images were available; the hand editor
follows the supplied written layout.
