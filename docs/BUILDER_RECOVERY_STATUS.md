# Builder recovery work in progress

Updated: 2026-10-05. Branch: `codex/builder-ghost-and-look-recovery`.
Pull request: https://github.com/georgenat89-design/maro/pull/37 (draft).

## Resume here

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
packet order. The rest of this full run and the upper/fresh stash builds remain
required final gates. Keep the production code unchanged while they run.

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
