# Radar and droppers

## Nearby-player Radar

Visuals → Radar is a native Maro HUD for nearby tracked players. Choose Heads, Dots or Arrows;
Heading Up follows the player's facing direction and North Up keeps the map fixed. The range
is the horizontal radius, with a half-range ring. Friends use the Maro friend list's color.
Height chevrons distinguish players above/below; invisible players, spectators and friends
have individual filters. Optional edge arrows show tracked players outside the range.

Movement uses Minecraft's interpolated positions and fractional transforms for both heads
and markers. Turning applies one consistent projection to players and compass points, without
an extra catch-up delay. Entities are selected on game ticks, with at most 128 cached/drawn
players; departed players and previous worlds are cleared. Names appear only where space
allows, and the closest-player footer keeps its selection stable between similarly near players.

Position opens the drag/resize editor. Scale fits the current screen at smaller GUI sizes.
The optional sweep is off by default. Radar is searchable as player radar, Zen Radar or minimap
and supports the per-module public config workflow. Meteor and Zen Radar binaries are unchanged.

Jump Circle already provides Circles → Radius, adjustable from 0.5 to 3 blocks. It scales both
the ring and the Maro logo; the existing setting name and saved values are retained.

## Shulker Dropper

Player → Shulker Dropper provides Buy & Drop, Buy Only and Drop Inventory. The buying modes
open `/shop`, find a shulker-box listing in the container section and wait for inventory progress
after each purchase. An optional category name handles shops with a category step. Delays,
sync waits, timeouts, purchase limits and a listing-price cap are configurable. The default
purchase limit is 64 boxes and the price cap is disabled until set above zero.

Buy & Drop keeps existing boxes by default, including their exact item components. New boxes
are thrown through player-inventory slots in the active handler. Armor, offhand, cursor items
and unrelated items are excluded. Drop Inventory supports colored boxes, keeps filled boxes
unless explicitly enabled, and can retain an additional Keep count of eligible boxes. The shop
listing is never thrown. Rejected purchases stop after the timeout; repeated rejected drops stop.

## Spawner Dropper

Player → Spawner Dropper empties loot through the server's Drop and Confirm controls. Text
matching handles small-capital labels and moved controls; fallback slots 50/53 retain the
supplied reference's layout. Titles, labels and fallback slots can be adjusted in Server Menu.
Personal storage, physical chests, shops and unrelated screens are left alone. A changed menu
handler is accepted during the pending confirmation/page transition. Actions wait for page
updates and stop after the configured timeout or page limit.

Bones Only with menu buttons preserves mixed pages because a server-wide Drop All cannot
select just bones from them. Throw Stacks mode drops individual matching content stacks when
the server supports that interaction. Player inventory and navigation controls are excluded.

Auto Open finds a visible loaded spawner within normal block reach, scanning loaded block
entities once per second while looking. Repeat is optional and defaults off; it reopens the
selected spawner after the configured interval. Auto Close closes only the owned menu. Manual
closure stops the current run. World/connection changes stop both droppers, and enabled states
do not resume automatically from saved configurations. Both modules have a compact status HUD.

## Validation

The production Minecraft 1.21.11 client with Meteor is exercised with tracked fake players and
real integrated-server menu packets. Radar checks cover heading projection, all marker styles,
range/friend/invisibility/altitude/edge filters, three GUI scales, drag placement, an 8-of-80
tracking cap, despawn cleanup and the existing Jump Circle size setting.

Shop/spawner fixtures verify delayed inventory updates without duplicate purchases, retained
old/filled boxes, colored-box Keep counts, unrelated items, purchase/price limits, rejected
purchases, unrelated screens, relocated small-capital controls, confirmation windows, identical
successive pages, mixed bone pages, bone-only stack throws, normal-reach opening and empty completion.
These are local protocol tests, not a claim of live DonutSMP testing. Server menu settings are
editable for different layouts. Run with `-PradarDroppersTestOnly=true`.
