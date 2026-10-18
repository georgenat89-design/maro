# Keyboard chooser, Acrobatics and ESP defaults

This update accompanies Texture Studio on the build 691 merge.

## Keyboard Strokes

The chooser uses an original, code-rendered full keyboard layout inspired by
the supplied reference: 104 ANSI keys plus left/right/middle mouse buttons.
Main keys, function keys, navigation and numpad have distinct colors. Selected
keycaps are filled; click again to remove them. The preview and selected count
update immediately. **Classic keys**, **Clear all**, **Place HUD**, **HUD: On/Off**
and **Done** remain available. Selections save with the normal module profile.

Clicking a key that has a game action selects that action, preserving existing
rebind behavior. Clicking an unused key adds a physical HUD cell without
creating or replacing a Minecraft keybind. **Game actions** retains individual
action selection, including extra mod controls. Physical selections, selected
actions, size and position persist together. Raw input state clears when the
module is disabled, the window loses focus or a menu opens.

The chooser fits the viewport at GUI scales 2/3/4, with inverse-transformed
clicks and clipped action lists. The HUD uses the same physical keyboard geometry
as the chooser. F sits beside D, R stays on the row above, and wide/tall keys
retain their shape. It crops the outer unused space while preserving the gaps
between selected keyboard positions. Mouse buttons form centered rows below.
Actions without an ANSI position remain available in fallback rows.

HUD and preview bounds follow the selected keys. Large layouts automatically
fit the viewport; placement and scrolling use their actual bounds. Rebinding a
selected action updates its position immediately, and multiple selections on
one physical key share a single keycap. Existing selections load into the new
geometry without resetting their selected actions or placement settings.

**Arrange Keys** now adds individual drag placement, snapping and arrow-key
nudges. Positions persist in the profile and follow their action after rebinding.
Reset layout restores this physical arrangement. See [PacksMC and input update](packsmc.md).

## Acrobatics

The original default only animated the player model while **First Person Flip**
was off, making a first-person player see no animation. The native F5 test
confirmed model flips and limb poses worked. **Trick View → Auto Third Person**
now shows the model during a trick and returns to first person when it ends.
**Follow Game** disables the automatic view. A manual F5 change takes priority,
and disabling Acrobatics during a trick restores its temporary camera.

**First Person Flip** uses the original perspective and rotates its view.
Side Flip now rolls the camera quaternion and matching basis vectors; the
previous implementation returned no first-person motion for that move.
Front Flip, Back Flip, Spin and Corkscrew retain their pitch/yaw motion.

The **Trick Key** (G by default) now receives queued press events, including
mouse binds, so a short tap between ticks cannot be missed by polling. Held
keys still trigger once per press. Enabling seeds grounded state immediately;
world changes clear old tricks and removed players cannot leave stale moves.

## Bedrock Void ESP

**Tracers** and **Labels** now default to off. Box highlights remain enabled.
Existing saved profiles retain their explicit values; reset settings to use
the new defaults, or switch those two options off in the current profile.

## Native validation

Run with Java 21 and the production Minecraft 1.21.11 client:

```powershell
./gradlew.bat build runProductionClientGameTest -PkeyboardStrokesTestOnly=true '-PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar'
./gradlew.bat runProductionClientGameTest -PacrobaticsTestOnly=true '-PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar'
```

Acrobatics checks assert native model limb angles, jumping/landing, a real
short G event, temporary view restoration, manual F5 priority, disabling
mid-trick and all five first-person camera moves including the native roll basis.
The keyboard checks cover opening from Panels and Window, scaled clicks on
function/navigation/numpad keys, raw key lighting/release, HUD placement and
profile persistence. Screenshots are reviewed in the native client.

The positioning regression checks add the reported W/A/S/D/F/R selection,
native rebinding, all 107 selected keycaps, nonoverlapping dynamic bounds,
double-height numpad keys, empty/mouse-only selections and viewport fitting.

On 2026-10-12 the expanded keyboard, Screen GUI and Acrobatics suite passed
with Meteor in 55 seconds. The focused Acrobatics suite also passed separately
in 37 seconds, and chooser screenshots at GUI scales 2/3/4 were inspected.
