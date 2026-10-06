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
descent. Replay 41 tests that checkpoint; its result is pending. It also contains
native underfoot startup for an otherwise verified elevated placement view.

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
