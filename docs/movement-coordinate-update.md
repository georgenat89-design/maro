# Freecam and coordinate update

Free Cam now defaults to 96 blocks per second, with a 0.5–4096 usable speed
range. Sprint still applies its configurable multiplier, and mouse-wheel changes
use the full speed range. The untouched old default pair (16 speed / 0.98
smoothing) upgrades to the new defaults when its old config is loaded. Customized
values stay unchanged.

Movement normalizes the complete horizontal/vertical direction and integrates
the acceleration curve analytically. Diagonal movement cannot increase the
chosen speed, and changing frame rate does not change glide distance. Default
smoothing is 0.88 for a shorter response. Camera frames reuse one small buffer.
The existing input isolation keeps camera movement and look separate from the
real player; disabling restores the normal camera, draw distance and culling.
Freecam still cannot reveal chunks the server has not sent.

No Fall has been removed, including its registration, icon, movement grouping,
packet/tick mixins, Flight dependency, and module-only test hooks. Vanilla fall
damage applies normally.

Coords Fly keeps its destination coordinates and now also shows a **Position**
row for the player and a clearly labeled **Nether** row. In the Overworld, the
Nether row divides X and Z by eight. In the Nether it uses the actual position.
Y stays unchanged. Other dimensions do not display a Nether conversion, including
custom dimensions with an `overworld` path in another namespace. Negative block
coordinates use floor rounding, matching the existing display.

Player coordinates interpolate with the current frame. Long coordinate rows fit
inside the card, and the complete card scales down to remain within the current
viewport. **Guide → Player Coords** toggles both new rows and persists through the
normal config system. It also works before a destination has been entered.

The focused native check is `MovementCoordinateChecks`. It covers speeds, 3D
normalization, sprint and scroll ranges, glide at 20/60/240 frames per second,
config migration, real camera input, player/camera restoration, module removal,
Overworld/Nether/End transitions, signed coordinate calculations, and viewport
fit at GUI scales 2/3/4/Auto. Captured HUD visibility follows the common Stream
Proof rendering path.
