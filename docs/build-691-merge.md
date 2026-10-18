# Build 691 merge

Merged the actual `build-691` tag, commit
`0f9f97629ab5ed56680323bf8bc2770557be74c5`, from
[georgenat89-design/maro](https://github.com/georgenat89-design/maro/releases/tag/build-691)
into the combined build 690, models and Fake Pay client.
The supplied release asset is 4,816,968 bytes and matches GitHub's SHA-256:

```text
bcd5bde0224e8f9b41d534c29a68674d33237b10f892ee4f07d8b4dcfa04f99d
```

Build 691 makes Floating Window a separate setting for both Panels and Window.
It adds Walk With Menu Open, Remember Spot, feathered glass edges, soft shadows,
a fine top-lit rim and softer grab/close controls. The old Spatial menu style
migrates to Window with Floating Window enabled.

Merge resolution keeps the current sidebar, GUI Size scaling, fonts, old Screen
GUI controls and settings migrations. Window bounds, sidebar buttons and panel
rows use the same canvas transform for accurate crosshair picking. Floating
Window takes priority over Screen GUI when both are enabled. Existing Screen GUI
setups remain selected during the one-time migration. Rendering fallback turns
Floating Window off so a failure cannot repeatedly reopen it.

Movement stops while typing or binding a key, during close, on focus loss, or
while an overlay is present. Closing releases the movement keys. The previous
mouse-ownership and focus fixes remain included.

The combined build retains the 30-model roster, wardrobe clipping fix, replacement
local Fake Pay, phone updates, Auto Replenish, manual Trident Boost, teleport
combat controls, tracers, Staff Watch and Caustics sky from the prior delivery.
The requested Auto Trident and anime-model removals remain in effect.

Native production-client validation uses Minecraft 1.21.11 and Meteor 1.21.11-85:

```powershell
./gradlew.bat build runProductionClientGameTest -PspatialGuiTestOnly=true `
  '-PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar' --console=plain
```

The focused suite checks both floating layouts, real crosshair clicks, fixed
placement, grab/move/close, remembered position, walking and typing/focus guards,
GUI scales 2/3/4, legacy preference migration, opt-out persistence, existing
Screen GUI movement/input, resizing, fonts and resource cleanup.

Validated on October 12, 2026: the native production-client suite passed in
1 minute 35 seconds with Meteor installed. Floating Window and existing Screen
GUI checks passed, including both layouts and native keyboard/mouse input.
The resulting screenshots were inspected for glass edges, layout and clipping.
The production JAR excludes the test harness and companion binaries; full
corresponding source and license notices accompany the delivery.
