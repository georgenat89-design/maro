# Builder recovery work in progress

Updated: 2026-10-06. Branch: `codex/builder-ghost-and-look-recovery`.
Pull request: https://github.com/georgenat89-design/maro/pull/37 (draft).

## Resume here

Upper replay 45 was stopped on unchanged 74659b2 production, at 699/710 after
four minutes with unchanged position and capacity status. A read-only snapshot
found 33 protected posts and mostly hidden remaining candidates. Its repeated
capacity searches expose another lifetime issue:
escape columns stay protected after their work completes. The current untested
working change binds newly protected posts to unfinished work, releases those
markers on completion/ignore, and invalidates the retained recycling snapshot.
Current footing, attachment and active-column guards still apply. Unbound
escape supports retain their protection. Owner bindings persist in saved builds.
The new capacity fixture must retain unfinished-work posts while reclaiming
completed-work posts; the save/resume fixture checks ownership and final cleanup.
The focused support-owner run 1 passed in 4m5s: both capacity variants, protected
active/unbound escape footing, ground-staging cleanup, native movement/reach and
raised-floor entry at heights 3 and 6. The owner variant completed in 19s and
checks actual target state, protected unfinished posts, all dirt cleanup, health
20, closed menus and native packet checks. Full core/auction run 7 passed in
18m16s on unchanged a2a18bd production, including saved-owner resume/cleanup,
rejected and delayed predictions, strict three-support reuse, stored tools,
raised/sealed storage, directional passage restoration, food, sold listings,
cheapest pages, budgets, multiple chests and supply preparation. Upper replay 46
now tests this production; fresh 710 and complete upper gates remain required.
Ignored
run/options.txt inactivityFpsLimit="minimized" was restored before launch; the
previous test's AFK limiter slowed long runs. Do not alter
the user's live profile. Final upper/fresh/core gates remain required.

The user requested a complete builder reliability fix and authorized continued
work, pushing and merging. Do not release this branch as verified yet.

The current checkpoint adds retained, bounded recovery searches; collision-only
route proofs before removing owned posts or opening a finished wall; native
placement acknowledgments and jump landing; and staged exits from enclosed
builds. These are shared geometry fixes, with no schematic coordinate exceptions.

The latest upper-stash replay starts with 411 of 710 blocks built and 128 owned
supports. Replay 40 revealed a same-level staging loop that mined 37 supports
without building. Checkpoint 01c14ae requires a real lower landing for open-stage
descent and adds native underfoot startup for a verified elevated placement view.
Replay 41 reached 416, then stalled at the 128-support limit. Its recycling search
repeated the earliest failed candidates each tick. b21dfb5 retains the post,
view-enumeration and route cursors within a three-millisecond planning pass.

The focused movement/recovery suite passed in 2m41s on b21dfb5 production. This
includes a new ground-staging regression which rejects pointless same-level
descent, builds an elevated target, and checks server completion, all temporary
dirt removed, health 20, closed screens and native packet order. Upper replay 42
now tests unchanged production. No full upper/fresh stash pass is established.

Replay 42 found a usable recycling view but remained at 416: one-slot reclamation
interrupted each new column piece, and the overall access timeout abandoned the
route. The next checkpoint reserves a complete short column budget, prefers
nearby safe tips, returns to existing committed steps and tracks actual access
progress separately from an overall bound. Direct useful exits also precede
general staging; building tries other access alternatives before a staging drop.

The preceding production passed focused movement/ground staging in 2m50s. The
full core/auction run 5 included the latest exit ordering and a new eight-support
capacity regression. It failed earlier, in ground-staging cleanup: the target was
built, but cleanup left the player on an unowned four-block-high ledge after its
intermediate supports were gone. A safe three-block return to a remaining ground
post existed; ordinary walking only searched two-block drops.

The current checkpoint permits actual-footing drops of at most three blocks,
checks the whole falling corridor, and waits for grounded footing before mining
so a landing post cannot be removed mid-fall. Run 6 (`builder-core-reserve-6.log`)
now tests all these latest changes. It passed the ground-staging cleanup case at
22:49:20 and the eight-support capacity case at 22:49:40. The latter requires four
old slots freed before the first native jump and checks no pool overflow, the
final server block, all temporary dirt removed, health 20, closed menus and native
packet order. Run 6 subsequently failed in the enclosed directional shulker case:
the builder stayed outside with "Mining target is obstructed". The three earlier
raised/sealed storage and escape cases passed. Focused passage diagnostics now
reproduce the exact mining intent; this is not yet a completed core pass. The
upper/fresh stash builds also remain required final gates.

Focused passage diagnostics reproduced a centre ray that only touches a shared
corner: the short visibility ray accepts it, but vanilla's final rotation ray
hits a neighbour. The current fix tries five interior face points and checks the
full ray with Entity.getRotationVector and the same closest equivalent yaw used
by smooth aiming. Vec3d.fromPolar and an unwrapped equivalent yaw have different
float rounding and did not fix this failure. Focused run 11 completed the sealed
directional shulker at 23:07:14 and no-restock escape at 23:07:40; the other cases
and final upper/fresh/core gates still need to finish.

Run 11 passed all four focused cases in 2m34s on 67dd409. Upper replay 43 then
passed the previous 416 and 478 stalls but stopped at 479 on north-facing shulkers.
Read-only geometry queries found valid elevated views and clear column bases,
but none of those bases were reachable from the outside ground below the floor.
The new exterior-entry planner proves a reachable base, a clear native column,
and an onward route from its future top onto an existing placement view. It
retains a bounded 3 ms cursor and the chosen base across capacity trips, and
tries this entry before reopening finished walls. The new native elevated-floor
test passed in 45s before this final priority change: directional server state,
unchanged finished floor, all dirt removed, health 20, closed screens and packet
ordering. Upper replay 44 now checks the latest production; full final gates
remain required.

Upper replay 44 passed 479 and reached 483, then stalled after a storage return
on layer-5 stairs/bulbs. Its native views were at Y=68/69; an exterior column from
ground Y=61 would exceed the six-block bound if forced to end at the exact view
height. The entry query now also proves a top one block below the view and its
ordinary native jump onto the finished floor. Candidate pairs are sorted by
distance from the current feet, rather than accepting a far enumeration corner.
The bounded retained search is at most 2,704 pairs in 3 ms windows. Both native
raised-floor cases passed in 1m14s on the current production: heights 3 and 6,
the latter requires a six-post column then a one-block jump to the Y=7 floor.
Directional server blocks, unchanged finished floors, all raw/owned dirt gone,
health 20, closed screens and native packet checks passed. Upper replay 45 is
next; no complete latest upper/fresh/core pass has been established.

## Required gates on unchanged final production code

- Finish the captured upper-stash build: all 710 server blocks, no owned or raw
  temporary dirt, closed menus, health and native interaction packet checks.
- Finish the fresh 710-block stash from preparation through stocking, building
  and cleanup.
- Pass the full builder core and auction regression suite.
- Fetch the latest base, integrate any changes, verify the affected gates,
  then merge the tested commit and produce the Minecraft 1.21.11 JAR and source.

Previous evidence does not establish these final gates. Four focused native
wall/escape/chest tests passed on a421a55; the full core and auction suite passed
on older b581db4. The 49,751-block farm has import/material/batch coverage only,
not a completed native build. No claim that every schematic works or that server
automation is undetectable is supported.

## Test entry points

Use Java 21 and `runProductionClientGameTest -PbuilderTestOnly=true`, with the
existing Meteor test JAR property. Optional focused properties:

- `-PbuilderStashUpperTestOnly=true`: captured upper-stash replay.
- `-PbuilderStashTestOnly=true`: fresh stash.
- `-PbuilderChestReturnTestOnly=true`: enclosed directional access, three-block
  descent, raised chest return, sealed chest return.
- `-PbuilderTurnTestOnly=true`: movement, reach, scaffold and pocket recovery.

Run one native client at a time. Preserve the external test logs and diagnostic
notes. Do not weaken server-state, temporary-support or interaction assertions
to obtain a pass. Do not install a candidate JAR into the user's live profile.

## Persistent artifacts

Source checkpoints are pushed to the branch above. Detailed chronological notes
and native logs are saved in the task's `outputs` directory, including
`builder-current-status.txt` and `builder-stash-upper-41.log`. Read the newest
entries first; earlier assertions and test sessions may be superseded.
