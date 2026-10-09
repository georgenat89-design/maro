# Auto Builder — Minecraft 1.21.11

**Builder Homes is enabled by default.** Mark storage with R and press Start.
When setup is needed, the builder walks directly to dry permanent footing beside
the marked chest, or returns through its previously confirmed storage home.
It then sends `/delhome 1`, waits for deletion or an already-empty-slot receipt,
and sends `/sethome`. The server allocates the first free slot; the builder
checks the save receipt for home 1 before resuming the schematic. **Home 1 is
reserved for storage and is replaced automatically.** No homes are deleted
before reaching storage. Permission failures or unconfirmed commands pause setup without
issuing another save. Set Storage Home also explicitly refreshes this slot.

Home commands have at least a one-second gap. A server reply such as “You need to wait another 0.25 seconds to execute a command” schedules a bounded retry of that same command after the requested delay; it does not advance to the next home step. An absent home is accepted only when deleting that slot. Saving and teleporting still require their own server receipts.

Home 2 is a temporary return point for each restock trip. At the current work
area, the builder stops on dry, stable footing, sends `/delhome 2`, confirms it,
then sends `/sethome` and verifies that slot 2 was saved. It goes to `/home 1`,
withdraws the needed supplies, returns through `/home 2`, and deletes home 2
only after actual grounded arrival. It then plans its next needed block from
the returned work area. Existing home 2 is replaced for this trip. Home 3 is
never created, used or deleted. Already being beside storage needs no round trip.
Restocking saves the return point before any storage approach starts. The
confirmed home-1 arrival body stays reserved against temporary dirt, keeping the
builder from blocking its own storage teleport while scaffolding nearby.
After confirmed storage arrival, local walking to a chest view cannot trigger
another home-1 trip. This arrival state survives saved-return reload and clears
when home 2 is removed after returning to work.
Return safety checks the standing body at the exact saved position and records
the block actually supporting it. An off-centre roof-edge pose can therefore
save safely even when the block directly beneath its nominal feet cell is air.
That supporting block, including temporary dirt, stays protected while away.
The supporting coordinate survives save/load; older saved homes still load
using their original floor coordinate. Confirmed
return metadata survives pause and saved-placement reload; a rejected return
keeps home 2 for retry. Travel stays still through warmup and checks actual
arrival and safe footing. Servers without these commands can disable Builder Homes.

With homes enabled, ordinary walking and confirmed home routes are checked first.
Before selecting a new access job, a bounded look-ahead checks dry, immediately
placeable full cubes in the current phase/layer. Committed walks and columns
keep their existing target. Liquid, attachment and native placement checks
remain in the normal placement path.
Missing hopper outlets and attachment backing blocks can pull their placement
prerequisites across a section boundary. This lookup is bounded to96 candidates
and2ms and preserves phase, layer, retry and access-repair restrictions.
A deferred prerequisite also defers its waiting dependants, allowing another
available section immediately. Stock batches stay fixed during restocking.
When those routes and short columns cannot reach unfinished work, a proved dry
passage or floor/ceiling opening can temporarily reopen completed blocks. Mining
requires a registered access job and rechecks attachments, fluids and footing;
unrelated finished blocks stay protected. Checked liquid access opens only above
its source, and retaining floors/walls stay intact.
Completed access jobs restore their openings as soon as the body clears and the
active route releases them. Deeper repairs precede outer faces; contiguous
vertical full-cube repairs restore their lower placement anchor first. The repair list
survives pause and saved-placement reload.

Head Spoofing eases the visible first-person view toward walking and action aim
while normal movement packets publish correctly aimed head rotations.
Walking and action aim share one rotation controller, capped at 12 degrees of
yaw and 8 degrees of pitch per tick, with acceleration and braking through turns.
The visible view uses the same limits and easing, with interpolation between
frames. It visibly looks toward its route and target; placement waits until the
view faces the block. Mouse look still moves the camera independently and gets
a short manual-look hold before following resumes. Camera control stays active
through placements, delays, walking and home travel so it never snaps back after
placement. Menus, pause, Free Look and Free Cam release camera control.
For vertical temporary columns, it removes
the reachable upper blocks, then stays crouched and eases over the ledge to mine
a hidden lower block. The original full-cube ledge must keep supporting part of
the body; its footing is never the peek's mining target. Each actual mining
action rechecks footing and its native ray. Unsuccessful peeks expire quickly;
native crouching also helps a mining view obstructed by a low overhead block.

With Builder Homes disabled, temporary cleanup uses actual reachable mining views. It can reopen a checked
floor to descend its owned dirt column, including for supports in schematic air
or outside the placement bounds; the complete future column and dry exit must
be proved before mining. After older supports are gone, final opening restoration
keeps its new repair scaffold until the wall work finishes. A new cleanup opening
returns to deferred restoration so it cannot close the exit during that cleanup.

Committed stairs survive trips to reclaim temporary block capacity. When the
next piece is out of reach, the builder searches real standing positions and
walks to a checked placement face before resuming the same stair. This staging
uses ordinary movement and adds no temporary blocks of its own.
At a staged position, available stair pieces are placed before climbing an
existing higher step, preventing that climb from undoing the placement trip.
Walking checks the standing player's actual collision volume under partial
blocks. An open trapdoor may permit headroom while its vertical panel blocks
one entry edge; the pathfinder checks that corridor and approaches another side.
Closed low panels remain blocked. Future scaffold queries use the same body
checks against read-only collision masks.
Raised steps also check the body's lift and approach path. A landing behind a
closed door can fit the player while the panel blocks entry; that edge is rejected
so the walker can choose a clear side instead of repeatedly jumping at the door.
Lower landings also prove the horizontal ledge approach and falling body path.
A closed panel along the approach causes another edge to be chosen.
Final cleanup checks whether removing a reachable support would cut the route
to permanent lower footing. It walks down before clearing that return bridge,
then resumes removing its own temporary blocks from a safe position.
For enclosed work, entry checks can prove a short passage through one to three
wall blocks. Only finished safe schematic walls or obsolete owned dirt are
eligible. The passage stays open through remaining work and scaffold cleanup;
restoration fills deeper wall cells before the outer face to preserve access.

## Start the build

1. Right-click **Player → Auto Builder** to open the control panel. Click **Choose Schematic**. Use **Open Folder** in the library to put files in the game's `schematics` folder.
2. Choose **Origin: Here** or **Origin: Target** and adjust X/Y/Z with the small nudge buttons. **Options → Placement** contains rotation and mirroring. **Materials** shows total, remaining, owned and missing quantities.
3. Close the panel, look at each double chest you want to use and press **R** to add it. **Shift + R** removes the chest you are looking at. You can also look at either half, open the panel and click **Remove Chest**, or use **Options → Materials → Remove Restock Container**. Removal pauses the builder and removes only that chest's supply selection. **Remove All Chests** in the panel clears every selection, including distant or unloaded chests, without aiming at anything. It clears cached supplies and the old storage-home route, keeps any outstanding home 2 work return, and updates the saved build when progress saving is enabled. The same action is available under **Options → Materials → Clear Restock Marks → Remove All**. Selections are remembered for this world/dimension; adding an already selected chest refreshes cached contents. Either half of a double chest refers to the same selection. The panel shows how many chests are selected.
4. Set an auction **Budget**, select **Mode: Automatic**, and click **Start Build**. With Builder Homes enabled, Start automatically approaches marked storage and replaces **home 1** when setup is needed. **Home 2** is replaced for each restock return and cleared after returning; **home 3** stays untouched. **Set Storage Home** refreshes home 1 explicitly. Semi Auto is available for holding right mouse. **Pause / Cancel Buy** releases movement/mining input.

Automatic is the default for new configurations. Existing configurations retain their chosen mode; change it with the panel's mode button. Show/Hide Preview controls ghosts independently of building. Protocol words and individual rendering constants are internal defaults, reducing the settings list.

The HUD and control panel show a rolling **ETA** based on completed blocks over up to two minutes of active work. Walking and restocking count toward that rate; manual pauses do not. Initial world scans and ignored materials do not count as placed blocks. The display warms up before estimating, says **waiting** after a sustained stall and shows **cleanup** until temporary supports are removed. It estimates placement time, not auction preparation or unmeasured final cleanup time.

New configurations use a two-tick Action Delay with the existing small Timing Variation. Saved delay settings are retained. Aiming eases through large turns and finishes the final sub-degree adjustment promptly; normal movement publication, fresh native rays and server placement confirmation remain required at every speed.

**Restart Build** rescans the same schematic at its current origin and starts again, preserving matching blocks and tracking its temporary supports for cleanup. **Cancel Schematic** stops building/buying, hides the preview and unloads the file, including cancelling an unfinished file load. Blocks already placed remain in the world. **Pause / Cancel Buy** keeps the schematic loaded for resuming.

## Saved builds and reconnecting

**Save Build Progress** is on by default. **Saved Builds** in the panel has a **Last Session** slot plus ten named slots. Choose a slot, enter a name and **Save Current Placement**. **Load Saved Placement** restores the schematic snapshot, origin, rotation/mirror, ignored materials, selected chests, temporary-support tracking and shared build budget. Loading pauses actions; join the saved server/dimension and press **Start / Resume**. A saved placement never silently moves to a different world.

The active placement is checkpointed every ten seconds, when starting/completing and on disconnect. The next launch restores the selected active placement, paused. Resume rescans actual world blocks rather than replaying a stale completion counter. Snapshots live in the game's `maro/builder-placements` folder and remain usable if the original schematic file is moved. Loading another slot first checkpoints the active build. Cancelling unloads the active placement while keeping saved slots available. Choosing a new schematic keeps named saved builds available; **Last Session** can be replaced by the next unsaved build.

## Placement and route recovery

Placements wait for the server's block update before advancing or recording temporary dirt. When the server rejects a ghost placement and restores the original block state, the builder clicks the original hotbar inventory slot to pick up and return its stack, waits for the server's inventory correction if that slot was empty, and retries after two ticks. This also restores a last block that disappeared from the client inventory. With **Auto Move**, it first takes a checked step toward the target when closer dry footing is available, then recomputes the placement ray. This approach does not mine or add temporary blocks and keeps bucket placement above the source. The existing four-second attempt limit still sends persistent refusals through normal repositioning. Missing block acknowledgements retain their separate timeout and reconciliation. Walking considers safe diagonal paths and follows clear straight sections without a stop at every cell. Blocked standing positions become eligible again after a short timeout, and the builder tries other unfinished targets instead of waiting minutes on one failed route.

Floating blocks can use short dirt columns. When dirt runs out, the builder checks selected chests once for the reserve and then buys missing dirt if **Auto Buy When Missing** and a positive budget are configured. Even with a reserve of zero, an emergency refill requests eight dirt. Only the builder's own temporary dirt obstructing a blocked walking corridor may be mined; footing, useful one-block steps and unrelated blocks are preserved. Completion cleanup works from the highest temporary supports downward.

If existing views, access stairs and safe scaffold descent cannot reach a missing block, the builder can add a temporary floor beneath a clear placement view. Both the dirt floor and the requested block must have valid native placement rays. A short floor column starts with an actually attachable lower piece when necessary. The floor uses the same support limit, server confirmation and cleanup tracking as other temporary blocks; it never replaces a future solid schematic cell.

Restock trips use the same safe owned-post descent and temporary-block route clearing as build movement. Depositing and restocking re-aim and retry an unanswered double-chest opening after one second, up to three attempts. A failed walk or chest-open attempt does not mark that chest's contents as checked. Auction fallback waits for actual stock checks of the selected accessible chests. Tools stored below a raised work area can be retrieved even when the builder must first break its own dirt support by hand.

With **Auto Unstuck**, a build enclosed by its completed floor can open a temporary access hole above one of its own landing posts. The floor must be a reachable, safe full block belonging to the schematic, without a block entity or dependent neighbouring blocks. The builder descends through that opening, retrieves the fixed material batch and restores the missing floor through ordinary placement. Unrelated floors are excluded.

## Auction purchases

Enter a positive **Budget** and click **Buy Missing**. Zero disables buying. **Options → Materials → Max Price Per Item** sets the unit-price ceiling. **Auto Buy When Missing** defaults on: needed inventory items move to the hotbar first, then the builder checks your selected double chests, and finally buys missing materials and resumes building. **Support Dirt Reserve** is shared by auction buying and restocking.

**Prepare Whole Build** is on by default. Start first deposits held supplies across selected chests, visits every selected chest and counts their combined contents, buys every missing material for the whole schematic, and stores the purchases before placing the first block. Inventory-full deposits continue the same shopping list and spent total. Tools, support dirt and the configured steak reserve are included. A budget/listing/storage stop deposits confirmed purchases and waits for you to resolve it; building does not start with an incomplete preparation. One budget is shared throughout that build, including later automatic shopping. Manual Buy Missing queues all missing schematic materials after subtracting held inventory and continues through every material type, including future sections and layers. It deposits and resumes that queue when inventory fills and automatic deposits are enabled. Disable Prepare Whole Build to supply materials as building progresses.

**Material Supply: Nearby Sections** is the default. The builder finishes compact 8 × 4 × 8 areas, choosing nearby work and building lower supports before upper blocks in that area. Each work batch contains at most 128 cells and 24 stacks of schematic materials, reserving room for tools, food and dirt. Automatic shopping while building and chest withdrawals use only that batch's missing quantities. The selected batch stays fixed during a chest trip, and stalled sections are temporarily deferred so another area can be attempted. Completed blocks and ignored materials are excluded. Chest withdrawals split stacks to take the exact amount and return the remainder. **Layer by Layer** and **Whole Schematic** remain available; existing settings retain the chosen mode. Set a positive budget for automatic shopping between batches. Saved placements remember their material supply mode.

**Auto Buy Tools** supplies a diamond pickaxe and shovel if no pickaxe/shovel is already in inventory. Required tools are fetched from chests first when clearing; auction purchases still respect the budget and price ceiling. Mining selects the fastest available tool across the inventory and moves it to the hotbar.

Buying scans up to three available auction pages, stopping earlier at the server's published last page or a disabled/missing Next control. When at least one full stack is needed, affordable full stacks take priority over smaller listings across all scanned pages; the item-specific stack size is used. The buyer chooses the lowest unit price within that priority and buys smaller quantities for the remainder. The price ceiling, total budget and overbuy limit still apply. It returns to an earlier page when needed and rechecks the selected listing. If the old cheapest price changed, it rescans once and accepts a current listing within the configured limits instead of repeatedly chasing the expired price. Disappeared offers are skipped; disappeared pages use the actual remaining pages. Replanning without an available purchase is bounded to 30 seconds per purchase. Inventory deposits preserve the remaining shopping queue and spent total while resetting the next material's auction scan and retry state. Explicit server messages or menus saying an item was already purchased/sold skip that listing and search again; they do not cancel the shopping session. Unconfirmed purchases without a sold notice still stop rather than risk duplicate spending. Repeated unavailable listings are bounded to 24 skips per session.

The buyer searches `/ah` with spaced registry names and checks item, quantity, total price, inventory space and budget before clicking. It supports immediate listing purchases, separate confirmation screens, and confirmations updated in the same screen. Confirmation requires the matching item/count, a matching price on the item or confirmation control, and a recognized positive control. Unknown titles also require a negative control.

Inventory receipts advance the shopping list and start the next search until all requested materials are supplied. Menu and receipt updates may take up to eight seconds. Changed prices, missing receipts and unrecognized confirmations stop the session without retrying an unconfirmed purchase. Estimates reflect observed listings, not guaranteed availability. Donut's live menus have not been directly verified; test-server fixtures cover these patterns.

## Building and cleanup

Placement uses normal interactions and player reach, checks placement states, supplies inventory materials, and verifies world states. Walking follows bounded routes on loaded safe ground, commits to its current target, checks reachable standing positions and cools down blocked candidates. Walking and placement both use head smoothing. Tall builds and states ordinary interactions cannot reproduce may need manual platforms/correction.

Mining also waits for a server block update or sequence acknowledgement. The original collision stays in place while a locally predicted break is unconfirmed, so the builder cannot walk through or place into a ghost hole. Rejection, timeout or pause restores that original state until server feedback resolves it. A confirmed rejection resets native mining and retries after two ticks, without assuming that the block was removed. Temporary-block cleanup and access-hole repairs use the same confirmation path.

**Action Delay** sets the base delay (two ticks for new configurations; saved values are retained). **Timing Variation** adds zero to the configured number of random ticks. **Head Smoothing** eases visible turns with bounded angular speed/acceleration. Final aiming settles without unnecessary tiny corrections, while each interaction still requires the published view and a fresh reachable ray. These options do not guarantee that a server permits or cannot detect automation.

**Clean Temporary Supports** is enabled by default. After completion, the builder returns to its own temporary dirt, steps off or moves around it if needed, and breaks it. Inaccessible supports remain tracked; completion waits for cleanup. Dirt requested by the schematic and unrelated dirt are preserved. Pause/deactivation retains supports for a later resume.

When the temporary-support pool fills and a new build route needs scaffolding, the builder can recycle an obsolete support. If finished floors hide the available supports, it searches for a reachable mining view before removing one. It preserves the player's footing, fluid containment, gravity blocks and neighbours that still need that face under vanilla support rules. Clearing a route support allows one fresh escape attempt because the geometry has changed. Unstuck steps respect the same support limit. Attached torches, signs, plants and oriented containers wait for a required schematic neighbour before competing for another route. Floating outer edges can use a bounded connected scaffold path with lateral steps, rather than only a straight column.

Placement views favour advancing the scaffold toward its target, with small access steps onto owned posts when necessary. A step uses the highest valid native attachment before trying lower bases, so a side-attached piece does not receive an unnecessary ground column. Lower access pieces remain protected through intermediate arrivals until their committed route finishes or is abandoned. Checked exterior entries include views exposing an intermediate attachment, even when the final schematic block is still outside reach. If held dirt runs out, obsolete supports can be recycled and their drops collected with a bounded pickup route before chest/AH fallback. Cleanup removes upper pieces before lower access stairs, choosing nearby pieces within a height to reduce travel.

Side stairs now require a complete blueprint and a proved walking route through its future collision geometry before any fragment is placed. The query restores its collision mask in `finally` and never changes actual blocks. Only necessary attachment roots are included; native contexts are still checked for each placed piece. Four directions retain their search cursor in bounded passes. Construction retains the plan, reserves its exact remaining support count before climbing and protects every committed piece until the route ends. Existing owned footing along that route and owned attachment neighbours are also protected during capacity reclamation. Pause/restart clears the transient plan and search cursor.

Access construction retains its chosen view until the route is finished or that view fails. Walking to reclaim capacity retains the exact obsolete support being reclaimed. Climbing posts are bound to their unfinished target and stay protected while that work is active. Once the target is completed or ignored, those posts become eligible for safe recycling; current footing, active access and dependent block checks still apply. These bindings are saved with the placement. Placement-view ray searches keep their cursor across ticks, share a two-millisecond planning allowance, and retain at most 24 searches; walking-view candidates are also checked in bounded groups. Native placement and route clearance are rechecked before acting.

If an access column needs capacity and the bounded recycling search finds no safe reachable candidate, its view and target are deferred. The builder clears that recovery request and tries other work or a checked descent, instead of repeatedly requesting a jump that the full support pool cannot supply.

Elevated floor entries consider columns near both the placement view and the player. A nearby climb can therefore lead onto a wide finished roof before ordinary walking crosses to the target. The planner deduplicates at most 3,488 entry/view candidates, retains its cursor in three-millisecond passes, respects failed-view cooldowns, and checks the complete column and onward route before committing.

If the elevated view is inside a sealed room, entry also checks a safe two-block doorway beside the future column. Four horizontal directions retain their cursor across planning passes. The combined collision query leaves actual blocks untouched; native climbing happens first, then ordinary passage recovery rechecks and opens the wall. Block entities, fluids, dependent neighbours and the target's required attachment face are preserved. Opened schematic wall cells are restored after their associated work finishes.

Final route openings stay deferred until their remaining temporary access posts are cleaned, so restoration cannot seal the player away from those posts. Completion waits for the actual opened schematic cells to be restored, even when a large scan still holds an earlier zero-task result.

A checked exterior column and its onward walking route are considered before speculative side stairs or reclaiming capacity for those stairs. A short access column reserves enough free support slots before climbing, so it does not need a separate descent/recycling trip for each new piece. Recycling searches retain their post, view and route cursors in three-millisecond passes and prefer nearby safe column tips. Existing committed steps are used when returning from a capacity trip. The route has a progress timeout and an overall bound; confirmed capacity reclamation and closer grounded positions count as progress.

Each placement view retains its entry, descent, dry-passage and floor-opening
search cursors when nearby work alternates. Within one walking search, standing
clearance and footing positions are resolved once per cell, so neighbouring
nodes reuse collision checks. These caches expire with that search; later server
block updates are always checked against current geometry.
When an exterior column requires a short wall opening for its onward route,
that exact opening travels with the column plan. After reaching the top, the
builder rechecks and mines it before walking inside, then restores its blocks
through the normal access-repair queue. Interrupted climbs drop the unused
opening intent.

Temporary dirt cannot occupy either selected chest lid or the two blocks of standing clearance above it. Planning, queued jobs and future access columns all enforce this reservation. If old saved builder-owned dirt already blocks a lid, ordinary verified mining clears it before opening the chest. Unowned blocks remain untouched and pause the build for the user.

With **Auto Unstuck**, a valid higher view can use a bounded access column with intermediate stairs and ordinary jump placements. It starts with an actually attachable base and advances toward its selected view, up to six blocks above the starting pose. It stops adding access blocks as soon as the intended schematic block can be placed from the current pose. The same support pool, clear headroom, safe footing and server confirmations apply.

Walking turns toward raised steps before jumping. Close placement approaches brake with short movement inputs and wait for a grounded, settled pose. Each unfinished schematic target has a 360-active-tick access budget that includes its temporary buildup. An exhausted route settles and finishes any native action before trying another target; its owned supports and registered wall openings remain tracked. Temporary dirt placement does not reset this budget or erase deferred-target cooldowns. Support recycling also works from upper pieces downward; removing a route support clears stale failed-view records so the changed route can be checked again.

Reach prechecks use the nearest face of a block, not its centre. Every actual action still requires an in-range native hit/context. This prevents arriving at a checked corner view and immediately walking away because the centre is farther away. The focused movement test also places two server-confirmed blocks from such a view with walking disabled. Repeated block-state scans reuse the same derived-property set instead of allocating it for every property check.

When a chosen scaffold view can reach only the first support, arrival places that support even if the final schematic block is still farther away. It commits to climbing the new post, building native intermediate stairs for a rise of up to six blocks, before extending more supports. The focused regression builds a target six levels above the initial pose and checks server completion, full temporary-dirt cleanup, unchanged health and native packet ordering.

Recycling preserves the short attachment column serving the active target. A stranded finished ledge can use a reachable owned post up to two blocks lower, followed by safe incremental descent through that post.

The selected post and lower view remain paired while walking to a descent. Arrival waits for a settled pose and rechecks ownership, footing, landing and neighbouring support rules before mining that post.

Owned temporary columns outside the schematic can use a checked doorway through finished walls during cleanup. The route must prove actual walking access and preserve fluid boundaries, attachments and safe footing. Removed wall cells stay registered for restoration. Native sealed-room checks cover both a captured outside-column cleanup scene and a column created while completing the room, requiring repaired walls, zero dirt and full health.

A safe landing alone is insufficient: recovery also checks a route from that landing to its intended view. Tall owned columns may descend through several checked steps, but each actual removal still waits for the native landing. A closed wall cannot be solved by repeatedly removing and rebuilding dirt on the same side.

Descent searches retain their candidate cursor across ticks, keyed to the current position and build/storage destination. They examine up to 128 owned posts and eight lower views within bounded planning passes. An unusable nearby post cannot permanently hide a valid farther exit by consuming every tick's search allowance.

Direct placement/storage exits are checked before intermediate floor hatches or general open staging exits. During building, a general staging descent is considered only after placement views, checked passages and temporary access alternatives have been tried. Its final landing must actually be below the current position; walking onto a ground-level post just to remove it is not accepted as recovery.

Each placement-view search retains its recovery stage as well as its enumeration cursors. An exhausted climb is not restarted before later passage, temporary-view and descent searches finish. Retry timers apply after the complete fallback pass, while geometry receipts invalidate the retained views. Bridge and direct searches keep their own temporary-view cursors. This prevents repeated climb searches from starving a proved descent off an isolated temporary column.

A committed column descent retains its checked exit across successive landings. It first prefers an ordinary walk when one is now reachable. Otherwise it removes the next owned footing only after settling, proving the remaining descent and checking neighbouring block safety again. Mining status identifies temporary scaffolding separately from a checked access passage or an actual schematic mismatch.

Completion cleanup owns a target independently of unfinished schematic cells. A failed walk to a distant support invokes the same mining-view and native exterior-entry proofs used during building. Cleanup retains its target while reserving capacity, climbing and reaching a mining view; it then removes its own access pieces as part of the remaining support pool. It can recover supports outside the schematic bounds. The cleanup regressions verify a finished floor with unreachable high dirt, including a full eight-support budget, no leftover old/new dirt and no damage to the finished structure.

At a standing view, the walker accepts a grounded native footing contact within its horizontal arrival radius when a partial surface supports the player's real body slightly above or below the nominal centre height. It checks actual block collisions and a bounded height difference. Route heights, fall corridors and fresh placement validation still use their normal calculations. Four native hopper-rim cases catch repeated turns while attempting to force the centre height.

For an enclosed placement view, recovery can temporarily reopen two completed full wall blocks only after a collision-only query proves a walkable route. It excludes block entities, fluids, unbreakable blocks and neighbours that would lose required support. Clearing and walking retain one intent; those wall cells are deferred until their associated target finishes, then restored through normal confirmed placement. The native regression places a west-facing shulker inside a sealed room, restores the wall, and checks server states, packet ordering, health and closed menus.

Access stair construction commits to its selected standing view and climbs onto each completed intermediate step before constructing higher pieces. New pieces are protected during construction; older pieces can be reclaimed after that climb while the player's current footing remains protected.

**Auto Unstuck** can escape a stuck ground route by jumping and placing a temporary dirt step beneath the player, then replanning the route. It requires dirt, solid safe footing and clear headroom. Recovery steps remain available for return routes and are removed by completion cleanup. Crouching is sent ahead of placements against interactive supports such as hoppers/chests. Note blocks are tuned one interaction at a time, waiting for the note update and stopping at the target; instrument/power changes do not trigger endless retuning.

Escape jumps first centre and settle on their footing so aiming is ready during the native underfoot-placement window. An actual support receipt advances directly to landing, even though the occupied block no longer has another valid placement context. Removing an escape step does not reset the escape-attempt limit. The focused movement suite also verifies an off-centre two-block pocket escape, its confirmed landing phase, final server placement and cleanup.

For elevated work, the planner first checks the shortest vertical pillar at the player's current column. Every native jump and the onward walking route are proved with all future posts present. A committed column reserves its remaining post budget and preserves its base. Once a post is confirmed and the player has landed, the next jump begins without the ordinary two-second unstuck cooldown. Stair routes remain available where a pillar cannot reach a usable view. Cleanup prefers nearby visible column tips before constructing access to distant posts, keeps lower column pieces and adjacent stair connections until their upper pieces are gone, and retains the checked return-exit rules.

Recovery does not pillar above its destination. If the builder is stranded while routing down, it can walk onto a reachable owned scaffold and remove its footing block when stationary, with a verified safe landing at most three blocks below. Vanilla attachment checks still preserve neighbouring blocks and fluids. A sealed build or storage trip can temporarily open a checked solid schematic floor above its own landing post. That opening stays deferred until its associated build target is finished, then is restored through normal acknowledged placement. Container floors, unsafe drops and floors supporting attached blocks are excluded. When an anchored short support column has no reachable placement view, a second search can start a connected bridge from another side.

Route-obstruction recovery preserves the stairs committed to the current target and confirmed escape steps. Capacity recycling also keeps escape steps until completion cleanup. Standing on a committed access column continues its ordinary jump placements before adding side stairs. Extra temporary-view checks retain their cursor and process at most four candidates per tick within a shared two-millisecond planning window; floor-opening searches use the same window. These limits reduce repeated searches without changing native reach or placement rules.

After a checked scaffold descent, the builder waits for grounded footing before searching the next walking segment. Landing routes use the landing height for their initial step checks, and a closed room's descent continues to a checked open exit before upward work resumes. A successful walking search clears old failed-route and recovery flags, so a working route does not trigger another underfoot pillar.

Ordinary walking can also use a safe drop of at most three blocks, measured from the actual footing heights, with clearance checked throughout the falling corridor. Mining waits for landing, preventing removal of the post the player is still falling onto. Cleanup can return from a raised finished ledge to its remaining lower supports without constructing another escape column.

Walking turns toward raised waypoints before jumping. Jumping in place does not reset the route-progress timer; a blocked jump is replanned instead of kept alive by vertical bouncing. Placement viewpoints use tighter centering and wait for a grounded, settled stop. Moving within the current walking cell is a valid route. The focused stair regression (`-PbuilderTestOnly=true -PbuilderTurnTestOnly=true`) verifies turning through 180 degrees, climbing, landing and centering within the current cell through native movement.

Repeaters are placed and then adjusted to their requested delay; wooden trapdoors/doors are opened or closed with acknowledged interactions. Neighbor-derived connections and redstone power do not force unfinished layers to wait for future wiring. Water/lava sources are supplied with buckets after the structural layers; observers are placed after structural configuration and fluids so construction updates and note tuning do not pulse unfinished observer circuits. This ordering applies to every material supply mode. Clearing unwanted fluids still requires draining.

**Auto Eat** pauses movement/building at **Hunger Threshold**, finds steak across the inventory, eats it and restores the previous slot. It checks selected chests first and **Buy Steak** uses the remaining auction budget if food is missing. **Steak Reserve** controls the quantity. Pause/deactivation releases food-use input.

Replace Wrong Blocks and Mine Out Schematic are off by default. Mine Out clears explicit air cells; sparse unspecified gaps are ignored. Fluids must be drained separately. Container/block-entity protection remains enabled internally.

## Restock, preview and other tools

Chest restocking recovers from a rejected pickup with one bounded retry, returns an unexpected held item, and closes completed or stalled chest sessions before checking other selected supplies. Chest routes try alternate visible standing positions and either half of a double chest. An inventory opened by the builder's own block interaction is closed automatically so building can resume; user menus unrelated to a recent builder interaction remain under user control. Persistent rejected transfers stop with the remaining items retained and a clear status.

Placements use the current view ray and publish the visible orientation through vanilla movement publication before interacting. Vanilla chooses the required position/look packet and updates its own last-sent bookkeeping, avoiding a manually injected look packet followed by a duplicate vanilla look. Opening selected chests, mining and block tuning use the same readiness check; interaction sequence numbers remain owned by the vanilla interaction manager. Server block updates and vanilla prediction acknowledgements both reconcile placement results; a rejected client prediction is retried rather than counted as completed. An aiming attempt expires after four seconds and replans. Temporary dirt in schematic air cells is deferred to final cleanup so it cannot prevent progress to the next layer.

**Deposit All Items: Inventory Full** is the default. When space is blocked by unrelated items, the builder walks to your selected loaded double chests within 256 blocks, transfers inventory/hotbar items using normal container clicks, then continues the same shopping list and budget. Layer supply uses already-held building materials before depositing another batch. Armor and offhand items remain equipped. The control panel's **Deposit All** button also runs this manually. After Buying / After Build deposit modes are available. When a chest fills, compatible partial stacks are filled and remaining inventory items continue into another selected chest. If every selected chest is full, remaining items stay in inventory and the HUD asks you to add another chest. Initial preparation must inspect every selected chest before buying, so an unavailable selected chest pauses preparation. During building, all available selected chests are checked for missing blocks before AH fallback.

Each material row has **Ignore / Include**. Ignored materials are excluded from buying, estimates and restocking, and their schematic blocks are skipped by building, paste and preview. Including a material again rescans the plan. Ignore choices are saved; Cancel Schematic clears them.

Look at each double chest and press R, or use **Options → Materials → Mark Restock Container**. Selections are scoped to the server/world and dimension. Restocking checks only your selected chests within 256 blocks, in distance order. Missing items are cached per chest until your own deposit or selecting the chest again, so later batches go to AH without repeatedly visiting an empty chest. Stockpile In Chests deposits surplus whole stacks of schematic materials; unrelated items are preserved.

Preview presets coordinate textures and outlines. Opacity, range and layers remain adjustable. Scanning has a time budget and previews have a hard cell cap; rendering does not scan the whole file each frame.

Stop On Staff Nearby checks loaded players against configured staff names. Low-health and optional nearby-player checks pause actions. Hidden/unloaded players cannot be identified. Changing worlds stops operations.

Snapshot saves a configured area as block-state-only `.nbt`. Creative material supply and command-based paste appear only in creative mode; paste also requires permission for `/setblock`.

## Formats and verification

Readers support Sponge v1/v2/v3 `.schem`, legacy MCEdit `.schematic` numeric IDs/metadata/AddBlocks, multiregion/signed-size `.litematic`, and vanilla `.nbt` structures. Rotation/mirroring transforms coordinates and block states. Limits: 64 MB file, 256 MB NBT allocation, 2 million cells, dimension 2048. Entities, biomes, inventories and block-entity contents are not imported/pasted.

Focused navigation/persistence checks: `./gradlew runProductionClientGameTest -PbuilderTestOnly=true -PbuilderNavigationTestOnly=true`. They verify diagonal final-block access, floating layer tails and support cleanup, durable named-placement resume with a completed block already present, and retry after server rejection. The auction suite also verifies empty-chest fallback to dirt buying and automatic build/cleanup resume.

Recovery checks also cover retained placement predictions after rejection, timeout and pause/resume, stepping out of an occupied placement target, oriented hopper neighbours, elevated shulker boxes, door hinges, crowded trapdoors approached from the wrong side, thin-block collision checks, access stairs onto a raised floor, walking across hoppers beside lava, descending to a lower placement viewpoint, and avoiding a descent blocked by a low ceiling. These verify actual server blocks and temporary-block cleanup. Chest fixtures include the distant coordinates from the reported issue and check ordinary movement/interaction packet ordering with an optional `-PmeteorTestJar=/path/to/meteor.jar` companion mod.

When a finished ceiling closes an otherwise usable access column, recovery can open up to three correct, full-cube schematic blocks without block entities after proving every native jump and an onward route to a real placement or owned-support mining view. It reserves the complete support budget, protects owned base footing, walks to the checked base and mines only cells reachable from that base. The opening stays registered until remaining work and scaffold cleanup permit restoration. Native enclosed-room regressions verify placement and AIR-cell cleanup, restoration, zero dirt, full health, packet ordering and protection of unrelated walls. The cleanup case also checks capacity reclamation with a five-support limit and a base whose jump clearance depends on the future opening.

Walking uses short centring inputs to clear an upper ledge, brakes above narrow lower landings, and waits for ground contact before advancing to another waypoint. Cleanup waits for landing and horizontal movement to settle before mining its next support. When registered access openings become ready for repair, they are refreshed directly and queued in their original build phase without waiting for a complete schematic scan. A native 250,000-cell regression restores a registered block before its next global scan completes; openings still needed for remaining work or scaffold recovery stay deferred.

Repair depth orders openings whose associated work has finished. An opening still needed by an unfinished owner cannot prevent that owner's own block from being repaired. A native dependency regression keeps unrelated work deferred, repairs the ready owner first, then closes its deeper access opening and completes the remaining work.

When a closed wooden schematic door blocks unfinished dry work, access planning checks the actual opened panels and their hinge collision. It proves an onward route to a placement view before walking or building an exterior column to the door. Normal interaction opens it; both halves stay registered under that work and are restored to their closed schematic state afterward. Doors next to existing fluid or planned basin boundaries are excluded. Door, approach and placement-view checks retain their cursors across bounded planning slices. The native enclosed-room regression verifies the door is never mined, walls stay intact, both halves close, and work finishes with full health, bounded rotation packets and no temporary supports.

Dry basin walls and floors use ordinary placement viewpoints during assembly. The above-only access rule applies to actual bucket placement, so a future water or lava cell cannot prevent its own retaining blocks from being built from the side. Retaining blocks remain protected against temporary access mining. A native two-phase fixture requires the dry floor through a wooden door before roof access becomes available, then checks a real contained water source, both closed door halves, unchanged walls, health and cleanup.

Buried dry blocks also keep a verified roof-opening fallback when no existing side view is available. For oriented components such as hoppers, the future ray must hit a native support face that predicts the requested state, with valid attachment and body clearance in the cleared view. The builder registers removed roof blocks for restoration and rechecks native placement after mining. Optional roof access and mandatory above-only bucket placement are separate checks. A door route must lead to a real placement view, excluding views that still require a roof opening.

Reachable existing placement views and checked doors take priority over new roof openings. When a dry oriented block is hidden by offset beams, a small upper mask proves a native placement, then the actual ray selects at most four obstructing cubes. Joint attachment and fluid safety are rechecked before those exact cubes are registered for restoration. Containers, the viewing footing and the block's required attachment remain intact. The current standing view is eligible for this proof, avoiding a needless walk away and back. Bucket work retains its original above-source proof.

Access mining records nearby item pipes beneath its checked openings. If a hopper collects a required replacement, the builder can recover the exact repair quantity through that pipe's native container menu. Other items stay in the container. These local recovery trips do not use storage homes or stockpiling, and their receiver records survive saved-build reloads and repairs of lower beams.

Material receiver records remain available after an individual opening is repaired, because the same pipe can retain a replacement needed by a later opening. Ready repairs first collect reachable loose drops of their required material, using bounded nearby entity and walking checks. Pickup attempts expire and cool down when a drop or safe route is unavailable.

Nearby-section restocking includes ready registered repairs outside the current section. It counts each work cell once, so a chest trip collects repair materials even when ordinary section work is exhausted.

The material that triggered restocking remains in the requested batch if a section refresh changes the queue. Stockpiling retains partial material stacks and nonstackable source buckets that still serve unfinished work, preventing adjacent sections from repeatedly collecting and returning the same small supplies. Home trips pause the active-work timeout without resetting it.

Changing targets after an unproductive route retains the placement search cursor while geometry is unchanged. This lets a large exterior-entry search finish and reach its later recovery methods. Repeated destinations share the same cached walking proof for each column base; native block receipts invalidate those queries. The captured `-PbuilderStashCheckpointStage=702` replay preserves the actual stalled scene with 127 owned posts and three outstanding access repairs.

When an obsolete owned dirt block hides a liquid view, recovery can prove that removing that single block exposes a dry standing position and a native hit on the source's floor. It routes to a checked mining view, removes the post through normal mining and verifies the bucket target again. Temporary escape footing, active supports, attachments and basin walls stay protected; any finished passage opened for this task must be above the source. The actual `-PbuilderStashCheckpointStage=704` replay captures the obstructed lava view with all three water sources already placed.

A surrounded plain full-cube target can have no current placement view. Recovery can open one adjacent finished dry cube after proving a reachable upper view, a safe one-block descent onto existing footing and a native hit on the target's base. The removed neighbour is tracked for restoration. Containers, fluid boundaries, unsafe footing and dependent attachments remain protected. The captured `-PbuilderStashCheckpointStage=698` replay covers this buried-block stall with its original supplies and owned supports.

Full supplied-stash build: `./gradlew runProductionClientGameTest -PbuilderTestOnly=true -PbuilderStashTestOnly=true`. This opt-in longer test builds all 710 blocks of the supplied Litematic in survival at the reported placement coordinates, using a selected double chest, tools, food and layer-based restocking. It compares every final block with the server, checks that no temporary dirt remains, and verifies that the supply screen closed.

The upper-layer replay (`-PbuilderStashUpperTestOnly=true`) starts at the captured 411-block stall, with its 128 owned supports, exhausted escape attempts and the next layer's material batch. It checks recovery and the remaining structure, fluids, observers and cleanup against the server. The smaller bounded-support case also checks that recovery never exceeds a three-support pool.

The captured fresh-build stall replay (`-PbuilderStashHomesTestOnly=true -PbuilderStashCheckpointTestOnly=true`) restores the real 423-block server scene, player and chest inventories, storage home and 127 owned posts. Add `-PbuilderStashCheckpointStage=549` for the captured roof stall, or `-PbuilderStashCheckpointStage=556` for the repeated outside buildup with 126 owned posts and nine recorded access openings. Each import asserts its recorded compatible server progress before starting, then uses normal survival movement, placement and home commands. Final checks cover all 710 blocks, restored openings, contained liquids, zero temporary dirt, full health and rotation packet limits. Each placement target retains its unfinished exterior-entry and descent searches when work switches to a nearby target. After exhausting ordinary and temporary access, the builder can use the confirmed dry storage home to approach a trapped roof job from ground and climb elsewhere. This recovery keeps home 2 exclusive to restocking and leaves home 3 untouched. These replays are distinct from the zero-block fresh test.

The full suite also scans a 250,000-cell schematic through the real incremental scanner and checks that its work queue remains at most 256 cells and its nearby supply batch at most 128 cells / 24 stacks. It checks large-coordinate rotation/mirroring and full material counts. A separate survival case walks to selected storage 80 blocks away and returns to build, exercising bounded route segments across the former 64-block cutoff. This is a scale and batching regression, not evidence that every large schematic can be completed. Loaded chunks, accessible selected storage, available materials and supported vanilla block states are still required.

The captured repair-supply replay uses `-PbuilderStashCheckpointStage=695` with the checkpoint/home options. It preserves the native inventories and the exact remaining chest supplies at the out-of-section repair stall; no replacement materials are added.

The supplied 100-module bone-meal farm is imported at 55 × 39 × 65 (139,425 cells / 49,751 blocks). Regression checks cover its complete material counts, 880 lava and 230 water source buckets, incremental scanning, bounded section withdrawals and deferral of observer/fluid work until assembly. These checks do not represent a completed 49,751-block survival build.

`./gradlew runProductionClientGameTest -PbuilderTestOnly=true` checks imports/transforms, previews, survival builds with materials outside the hotbar, exact chest withdrawals, lava-safe walking, temporary-step recovery, double slabs, air clearing, note tuning, crouching against interactive supports, material ignoring, cancellation/restart, and cleanup when standing on or far from a support. Auction tests cover consecutive purchases, cheapest rates on earlier/later pages, sold chat/menu notices with changing expiry lore, delayed updates, reused handlers, price changes and missing receipts. Full inventory is deposited into the selected double chest, shopping resumes without duplicated purchases, and building retrieves its missing blocks from the chest. Additional fixtures check multiple selections and config restoration, double-chest half deduplication, removal of one selection, partial-stack overflow storage, combined stock accounting and exact withdrawal from another chest, AH fallback after checking each selected chest once, selected farther-chest use, missing-item caching, whole-build preparation and one budget across deposits, auto-eating from inventory/chest, delay/open block configuration, source buckets, the supplied signed-region stash import, layer-sized purchases, full inventory supply batches, automatic shopping on the next layer, and pickaxe/shovel supply and selection. Screenshots are in `run/screenshots`.

Format references: [Sponge v2](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-2.md), [Sponge v3](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md), [Litematica](https://github.com/maruohon/litematica). This is independent Maro code.
