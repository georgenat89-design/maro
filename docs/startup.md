# Maro presentation

The title menu uses a full-screen navy and cyan contour field, a spacious left navigation
column. The home pane now welcomes the player to maro.gg with an animated Maro mark and a clickable discord.gg/maro community card. Worlds, servers, accounts, options,
language and accessibility open inside the right pane on the same home page. Contours are built
once with marching squares and rendered as two cached batches. Slow whole-field motion
avoids per-frame tracing or randomized jitter. Layout and native hitboxes share one
aspect-preserving transform, including smaller windows and different GUI scales.

Connection/configuration, terrain loading and resource loading retain their black/cobalt
geometric layout, native progress and `discord.gg/maro` footer. Unknown durations use a
moving bar; the server-entry M stays lit across stage changes.

Menu/loading text is uppercase Sora Bold. The shared renderer reads bundled
font bytes directly, including before the initial resource load finishes. Every label is rasterized at native framebuffer
resolution and aligned to physical pixels, avoiding atlas resizing and extra
downsampling. Small loading labels have larger type and stronger contrast.

The native title buttons and connection Cancel/Disconnect controls retain their
callbacks, permissions, enabled states, navigation, tooltips and narration. The
splash owns its underlying screen during reloads so GameRenderer cannot paint a
second screen over it. Native SplashOverlay.tick retains completion, callbacks
and error handling. Connection errors and cancellation clear transition state.
A short smooth fade reveals the first playable world, with a bounded terrain
readiness fallback. No synthetic percentages or forced startup delay are added.

See [Typography](typography.md) for the client-wide text audit and verification.
The character renderer is available for the three supplied SpongeBob, LeBron and Chun-Li models. Retired procedural/portrait designs remain excluded.

The persistent home host embeds real native screens, including Create World, Direct Connect,
server editing and nested settings. Mouse, wheel, dragging, keyboard, text input, file drops,
ticks, tooltips and Back/Done controls route through the pane's drawing transform. Native
backgrounds are suppressed only while drawing inside the host. Active-page asynchronous
screen callbacks retain their normal stale-screen guard. Joining a world leaves the host
for native connection and loading screens. Native checks cover GUI scales 2/3/4, ten
server-list wheel events, address typing, nested Back/Escape and actual world entry.

After a world or server is closed, native server/world library screens are hosted in
the Maro pane even when the prior screen was a loading or disconnect screen. Saved
library instances retain their native callbacks; connection Cancel and failure Back
also return to the pane. Back targets pointing to an older home host close the current
pane instead of replacing the page with another home instance.

Loading now frames the Maro mark with slowly rotating cyan arcs and adds a compact
Connect/Create/Play line. Native progress values, cancel controls and callbacks remain
unchanged. The welcome pane is replaced in-place when a native menu section is selected.
