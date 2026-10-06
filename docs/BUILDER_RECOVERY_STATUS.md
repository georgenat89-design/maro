# Builder recovery work in progress

Updated: 2026-10-06. Branch: `codex/builder-ghost-and-look-recovery`.
Pull request: https://github.com/georgenat89-design/maro/pull/37 (draft).

## Resume here

At 09:05, ceiling-entry-2 PASSED 3m17s: retained staging/thick-wall regressions,
ceiling placement, and owned AIR-cell cleanup from an owned base beneath a low
ceiling. The cleanup case starts with four posts and a five-post limit, reserves
the three-jump budget by reclaiming spares, preserves the committed base,
restores its opening, removes all dirt, retains health20 and checks packets and
unowned walls. Upper63 finished all original fluid/observer jobs but stopped
unfinished702/710 with eight access repairs and127supports. Native full-body
proof found valid ceiling-assisted mining routes that the placement-only guard
excluded. Ceiling entry now also serves actual owned cleanup targets; a real
standing base is tested through the complete future opening before rejecting
its blocked jump volume, and owned base footing is retained through capacity
trips. Pending storage trips keep their ordinary tick priority. Latest Upper64,
Fresh and complete core/AH remain required. No full Upper/Fresh pass yet.

At 08:35, ceiling-entry-1 PASSED 3m1s, including retained ground-level staging
guards and both thick-wall placement/cleanup cases. The new native enclosed
room requires entry through a finished ceiling above an empty column. It proves
actual mining, four acknowledged scaffold jumps, placement, full floor repair,
zero owned/world dirt, health20, no menus, native packet ordering and unchanged
unowned bedrock. Upper62 on5d stopped unfinished704/710 after repeated access
searches; it is not a pass. Its read-only native proof found a safe three-cell
ceiling opening above an unplanned AIR column, a complete jump-body sweep and
an onward walking route to a real water view. Recovery now checks that complete
ascent, reserves its whole support budget before mining, mines from its real
base and keeps the opening registered for final restoration. Every opened cell
must be in mining reach from that base. Upper/Fresh and latest complete core/AH
are pending on this change. Base fetched08:26 unchanged5036776 and already an
ancestor. PR37 remains draft/unmerged; final artifact delivery pending.

At 08:03, full-column-cleanup-3 PASSED 3m16s: selected chest-cover protection,
spare/full-pool cleanup, sealed room and AIR roof-support cleanup all restored
their schematic cells, removed all dirt and retained health20/packet ordering.
Complete floor-plus-owned-column exit proof, a final opening-restoration phase
and real mining views fix the observed cleanup add/mine and virtual-view loops.

At 07:19, water-exit-surface-5 PASSED 1m9s. Upper60 on e42f8e7
advanced beyond the old 475/512/545 stalls to 704/710, then stopped making
progress after placing a water source intersecting its native body. Read-only
fluid-body proof showed valid dry-cell bucket views, but the player's current
cell held water and every route from it failed. Own test PID44948 was verified
and stopped unfinished; no full upper pass. The new walker permits departure
through only water cells already intersecting the actual player body, still
rejects entry into other water cells and all lava, and holds native jump input
while swimming up toward a checked dry ledge. Lava bucket jobs reject body
intersection. The native contained-source/open-trapdoor test now leaves the
source for a dry ledge without mining, preserves the source, rejects dry-side
re-entry and retains full health. Prior doors, stairs, capacity, pit, hopper
crossing and rim checks pass. Latest fullUpper is next; fresh and full core/AH
gates still pending. PR37 remains draft/unmerged; final handoff pending.

At06:50, full-capacity-surface1 PASSED1m9s: full pool retains native hypothetical
scaffold proof, real queue guard adds no block and does not mine protected posts,
and two remaining ground blocks still finish. Prior hopper crossing, native pit,
rising/falling doors, blind recovery rejection, trapdoors/bucket/stairs/rims pass.
Upper59 oncea stopped475after6mincapacity/viewloop. Native proof found24valid
scaffold views andzero direct desired placements; earlycapacityrejection hidthem.
Latestproduction capacity-independent feasibility plus actual queue capacity
checks is ready to checkpoint/push and run Upper60. No fullupper/freshpass yet.
CI348oncea stillrunning; latestcompletecore/auction afterthisfix required.
PR37draftunmerged, base5036776, no finalartifacthandoff.
At06:41, hopper-surface1 PASSED1m7s on latest lip-jump correction. The native
hopper row was crossed, its build completed, full health/no extra supports;
all rising/falling door, blind-recovery rejection, actual offset-pit escape,
trapdoor/bucket/stair and four hopper arrival checks also passed. Commit latest
checkpoint then run fullUpper directly, followed by fresh and latest core/auction.
Final5 oldd2 stopped unfinished; no seeded-final pass and no fullupper/freshpass.
PR37 draft/unmerged; final artifact handoff pending. Latest base fetched06:23
unchanged5036776. CI342 failure actual native hopper crossing; newcode fixes it.
At06:30, proven-recovery1 PASSED1m3s on latest guard: native blocked upward
recovery leaves phase0/no queued dirt; actual offset-pit escape still confirms
its step, places the requested block and cleans up; rising/falling closed-door
routes, all trapdoor orientations, bucket/stair/native hopper regressions pass.
Final4 old431ca0c was stopped unfinished697/710, not a full pass. Next replay
must use the new guarded production. No merge/final handoff; full gates pending.
At06:19, stash-final3 on bf76677 production removed past the old122-post loop
and reached106owned posts, then stalled at actor(-26217.498,65,-150581.5125).
Native drop-body proof: next lower landing(-26218,64,-150583) fits; horizontal
approach at source height is false, vertical drop true. Closed dark-oak door
at(-26218,65/66,-150582) blocks the approach. Walker checked rising edges but
not falling edges; full landing-column clearance missed the intervening panel.
Own native PID43944 verified/stopped. Working drop-edge body-corridor proof
is UNCOMMITTED; new native descending-closed-door fixture added beside rising
fixture. Surface-drop1 PASSED53s including native stair/partial-block
regressions and test-only bounded feasibility retry. HEAD5e8b705 production
bf76677 remains checkpoint. CI342run37448334038 was in progress at06:16.
Fullupper/fresh/core gates pending; PR37draftunmerged; no final handoff.
At06:09, final2 was stopped after all five final jobs were placed, but cleanup
cycled between122/123owned posts around target(-26215,66,-150583). Native
cleanup-view trace shows the same raised column recommitted repeatedly. Cause:
prepareElevatedEntry proves an opening for an owned support in a schematic AIR
cell, but repositionTarget stage3 skips actual preparePassage when wanted.isAir.
Cleanup-door proof confirms a safe masked route from top(-26219,66,-150583)
through a finished note-block wall to a useful mining view. Neither geometry
nor pose was changed by diagnostics. Working stage3 permits this checked
passage for the current owned cleanup target. Thick-room test now runs both
requested-block entry and cleanup-only entry (owned dirt in schematic air);
both require restored original walls, zero dirt, health20, closed menus and
native packets. Cleanup-entry1 PASSED in 2m37s on these changes: same-level posts stay protected, both requested-build and cleanup-only thick-room cases restored every wall, removed all owned/world dirt, retained full health and closed menus, and passed native packet assertions.
Pose proof at66 showed actualHit=null and projectedHit=null; eyeheight1.62.
Centre-lift proof had no valid cardinal67neighbour. No centering/pose code was
changed from these rejected hypotheses. Current HEAD28dcd0d, PR37 draft.

At05:56, Jump-surface1 PASSED50s. Native raised closed-door arrival took a
clear side, kept the intended closed door state, full health and clear body.
Prior four open/closed trapdoor cases, native source bucket, retained stair/
placement priority, blocked no-fragment proof and hopper rims also passed.
Upward-edge body corridor is ready for a checkpoint. Stash-final2 is next.

At05:55, seeded stash-final1 on7d0579f placed all final sources/observer but
was stopped during cleanup: five opened repair cells remained, owned124, actor
(-26217.49999,64,-150582.30000) repeatedly jumped at stand(-26218,65,-150582).
Read-only jump-body proof: target body fits/canStand=true, vertical lift=true,
horizontal approach=false. A closed south-facing dark-oak door occupies the
raised landing's entry panel; landing-only and same-height corridor checks
missed the blocked upward approach. Working BuilderWalk now proves vertical
lift and horizontal body corridor for upward edges. Jump-surface1 session20816
is running a new native raised closed-door approach from a clear side plus
all prior body/trapdoor/stair/hopper surface cases. UNCOMMITTED; full gates pending.
Diagnostic stateA had all three waters placed; stateB has no missing fluid or
observer, only deferred wall repair cells. Final1 is not a full completion pass.

At05:48, Thick-entry3 PASSED1m58s: corrected staged-search intent gate,
native staging cleanup, two-deep elevated-room entry, requested interior block,
all179solid server states restored, zero server/owned dirt, full health, closed
menus and native packet checks. Deeper entry and repair order are ready for a
checkpoint. The seeded final-target stash test is next; full gates remain open.

At05:46, Thick-entry2 was stopped at177/179 with supports0 and two inner wall
cells left at(-7,-27/26,12). It had repaired the near outer wall first, blocking
the remaining inner cells. Production now tracks horizontal repair depth for
committed passage openings, keeps them deferred until other work and temporary
cleanup finish, and restores deeper wall cells before the outer face. This also
avoids reopening the same entry between remaining interior targets. Thick-entry3
session30941 is running staging plus the same two-deep room with repair stock.
All deeper-entry/repair-order changes remain uncommitted and need native gates.
Test-only builderStashFinalTestOnly seeds all non-fluid/non-observer cells in the
supplied stash, preserves only owned supports in schematic air cells and holds
the final-job materials. It isolates the final five cells without replacing the
full upper/fresh gates. It has not yet run.

At05:42, Thick-entry1 reached the interior, completed its requested block and
removed all owned dirt, then waited for Stone to restore four opened wall cells
(trace correct175, supports0, Missing Stone). Its one-stone/no-chest/no-AH test
supply omitted repair stock; mined drops can fall below the exterior platform.
Fixture now supplies16stone to test geometry/restoration without a market.
Thick-entry2 is running session24196 on the same uncommitted production.

At05:38, Upper58 was stopped at705/710 after an extended final-target loop.
Read-only fluid-body proof confirms water targets now nativeClear=true,
canStand=true and nativeJob!=null. Fluid-route proof finds many interior65
views with actual routes into them. Entry-door3 proof finds valid two-deep
openings from top(-26213,65,-150585), base(-26213,61,-150585), through walls
(-26213,65/66,-150584) and(-26213,65/66,-150583), to several interior65 views.
All non-air cells are finished safe schematic walls or owned dirt; safeToRecycle
and future pillar+masked route passed. One-depth entry probes failed. No real
geometry was changed by diagnostics. Production now considers depths1..3 with
the existing bounded search cursor, only approved wall/obsolete-own-dirt cells,
target attachment protection and complete safe masked route proof. Actual
passage opening uses the same depths and records finished walls for restoration.
Thick-entry1 is running staging plus a new native two-deep elevated-room test:
all original walls/roof must be restored, zero dirt/full health/closed menus/
native packet checks. These latest changes are UNCOMMITTED/UNVERIFIED.
CI334 on7807d4e is running; production there is f4a57ab, without deeper entries.

At05:29, Upper58 on f4a57ab is running at617/710; the545 and612 pauses
resumed into real placement. Read-only progress traces A/B/C retained.
CI332 run37441653081 built/passed general-game but failed the pre-build
sameLevelStaging assertion: it treated the bounded descent planner's search
yield as a committed route. Test correction allows up to128 slices and asserts
no mining/stand goal or lost owned posts after every slice, then requires search
completion. No production change from f4a57ab. Latest full gates still pending.

At05:13, isolated sameLevelStaging PASSED59s with the cleanup return bridge
guard. The unchanged case previously FAILED2m19s on the body fix alone.
Read-only cleanup trace1 proves the failure: actor at(4.5358,-26,1.5585),
mining owned dirt(5,-27,2), normal route to permanent ground(4,-30,-2)=true;
the same route with only that dirt masked absent=false. Subsequent removal
left it on the unrelated stone obstacle with seven low owned posts remaining.
Working cleanup checks this future deletion, searches bounded permanent lower
footing, and walks down before removing the bridge. No unrelated block or
extra temporary block is used. Passing isolate2 asserts native completion,
zero world/owned dirt, full health, closed menus and packet sequencing.
An explicit unchanged stone-obstacle assertion was added for the next full run.
Cleanup-guard1 PASSED1m2s for owned/unowned chest covers and two scaffold
capacities. Body/bridge production is ready for a checkpoint; upper/fresh/
full-core gates remain pending.

Upper57 was stopped at705/710 after more than six minutes without a placement:
three water sources, one lava source and an observer remained. Read-only fluid
body proof shows all three water positions have native isSpaceEmpty=true and
a valid native bucket job, while walker.canStand=false. Each source has an open
trapdoor in its head cell; whole-cell clear() rejected valid standing bodies.
Working BuilderWalk now checks actual standing-body collision volume against
real or future masked block shapes, with the existing cheap empty/full-floor
case. Same-height edges involving partial shapes also prove body clearance
across the corridor, avoiding entry through an open panel's blocked edge.
No real geometry is changed by feasibility queries. Surface-body1 is running:
four open/closed trapdoor orientations, native walking around panel edges,
server-confirmed water under the panel, plus prior stair/rim regressions.
Surface-body2 PASSED45s: four native open-panel arrivals, closed-panel rejection,
server-confirmed source water, all stair/rim cases, health and native packet
checks. Packet observer now includes normal item-use sequence/look fields as
well as block-use publication, so bucket actions are actually checked. Body1
had passed its world/walking checks but failed the old block-only packet count.
Native isolated staging/CI failure is next on this uncommitted production.
No full upper/fresh/core pass, merge or artifact handoff.

At04:54, Upper57 was running unchanged356621d production (at639/710).
GitHub run330 on356621d built and passed its general in-game test, but full
builder/auction failed in sameLevelStaging during final cleanup: actor stood
on the existing four-high stone obstacle with no inventory and seven owned
low posts remaining. The failure is a real pending gate. No production change
has been made from that snapshot. Test-only builderStagingTestOnly isolates
the unchanged case for native diagnosis after Upper57. Logs were retrieved
from job112183416498. A full core pass remains required before handoff.

Upper56 stopped at512/710. Its read-only trace A shows a retained staircase
arrived at its two-block step at tick3151, staged down to a real ground
placement view at3155, then chose the same higher step again at3191 before
placing the remaining piece. The plan expired at3252. Working correction
tries currently available native stair placements before another climb, so a
successful staging trip is not immediately undone. Surface2 adds a competing
reachable upper step versus an available lower piece and requires the lower
piece's native confirmation before any climb. Surface2 PASSED in36s, including
both staging cases, blocked-route/no-fragment proof, compact step/protection
and four hopper-rim arrivals. Native block/health/packet assertions passed.
Upper57 is next. Full gates still pending.

Upper replay55 was stopped at539/710 after extended plateaus at512 and539.
Read-only access trace B records a complete nine-piece stair at tick18348,
capacity reclamation moving the actor to another footing, and the entire plan
cleared at18524 despite progress at18455. No next placement view was attempted
when the remaining pieces were beyond the current eye/body placement context.
Later the same high dirt at(-26211,67,-150584) was placed and reclaimed again.
The working fix retains the stair and searches bounded real walking positions
for an actual remaining native placement. It can stage horizontally or lower
after a capacity trip; it creates no extra dirt during that movement. Nearby
server geometry changes invalidate that staging search. Native Surface1 now
includes a capacity-trip return regression with a proved complete stair,
outside initial placement reach, actual key movement and server-confirmed dirt.
Surface1 PASSED in33s: native staging return, rejected stair/no fragments,
compact upper piece/protected old footing and four hopper rims; actual blocks,
health and packet assertions passed. Full upper/fresh/core gates remain
required; no merge or release. Upper56 is next on this production.

Upper replay 54 was stopped after another prolonged 512/710 plateau. Native
access snapshot A retained work1239, a side-stair view (-26211,68,-150584),
accessFloor=false and one protected high piece while reclaiming a distant low
post. The heuristic stair branch reserved no complete budget and proved only
individual placements, so smaller pieces/protection alone did not fix the loop.
The working replacement constructs a complete cardinal stair blueprint with
only necessary attachment roots, proves the entire future walking route using
a read-only collision mask, retains the plan and reserves its exact remaining
support count. Four directions share a retained cursor and bounded planning
passes. Native placement/acknowledgement remains unchanged. All access resets
clear the transient plan; pause resets its search cursor too. Existing owned
footing on the proved route and owned attachment neighbours stay protected
during capacity reclamation. Entry/room/cleanup run5 PASSED in4m59s (before the
small cursor-reset and existing-footing guards). Latest Surface run2 PASSED in
29s: blocked destination produces no fragments and restores the mask, compact
upper step leaves lower cells empty and protects its existing owned attachment
and new step through native arrival, four hopper-rim arrivals do not orbit.
Native health/block/packet assertions passed. Upper replay55 is next on this
production; latest full core repeats all focused cases.
No full upper/fresh/core pass or release is claimed.

Upper replay 53 was stopped after reaching 513/710: it spent roughly three
minutes at 512, repeatedly building/recycling lower access stairs before a
checked column finally completed the work. Read-only access snapshots retain
work 1239 (waxed oxidized copper at -26211,68,-150582), a side-stair view at
(-26208,66,-150583) and repeated low pieces on its route. No full pass is claimed.
Working changes retain lower access pieces through intermediate arrivals,
check exterior entry for scaffold views as well as direct placement views,
and try the highest attachable stair piece before lower bases. A new native
regression offers a valid high side attachment plus an unnecessary reachable
ground base; it requires the high piece, empty lower cells, protection after
native arrival, health and native packet ordering. Entry/room/cleanup run 4
PASSED in 4m53s, including that native compact-step regression, both lid cases,
four entries/isolated descent, raised sealed room/restoration and both cleanup
budgets. The completed-build cases preserved their floors/walls, removed all
owned dirt, kept health and menus correct and passed native packets. Upper
replay 54 is next. Complete upper/fresh/core remain mandatory.

Upper replay 52 failed at 545/710 after a prolonged full-pool side-stair trip
at 542. The saved native server world (New World 285) contains newly placed
dirt at (-26207,62,-150577), directly above the selected chest, plus dirt above
it at y63; neither was in the captured support fixture. Opening that chest
paused the build as blocked. This is a failed gate, not a completed replay.
The working change reserves both selected chest lids and their two-block
standing clearance in temporary placement planning, queued jobs and prospective
columns. Existing owned lid dirt is cleared through normal mining; unrelated
dirt still pauses safely. Checked exterior entries/onward routes now precede
speculative side stairs and speculative capacity recycling. Focused native
entry/room/cleanup run 3 PASSED in 4m58s, including new owned/unowned lid cases,
all four elevated entries, isolated descent, the large raised sealed room and
both cleanup budgets. Native blocks, intact floors/restored walls, zero owned
dirt, health, closed menus and packet checks passed. Run 2's unowned case had
an incorrect packet assertion requiring interactions where none were expected;
that assertion was corrected before run 3. Upper replay 53 is next. Complete
upper/fresh/core remain required before any merge or release.

Upper replay 51 passed the earlier 469/617 stalls, completed fluids and observers,
and reached cleanup at 706/710 with four deferred final wall cells. It then
stalled on a distant highest support at (-26217,68,-150574): walker path empty,
84 failed searches, same ground pose and 126 supports. Out-of-reach cleanup only
called walker.approach; it never used the access planner. Read-only native mining
queries found no currently reachable view but proved eight ordinary exterior
column/base/onward-view routes. The process was stopped, not passed.
The current working change gives cleanup a retained target independent of an
unfinished schematic cell, allows its ordinary checked entry, and shares the
committed capacity/climb/descent logic. Finished walls remain deferred through
cleanup. New native cases start a finished schematic with a high out-of-reach
owned support, check both an ordinary and full eight-support budget, and require
all old/new dirt removed, intact floor, health 20, closed menus and native packets.
Cleanup-access run 1 passed both cases in 57s, with the strict 8/16 limits,
finished 81-cell floors intact, exact existing schematic block, zero raw/owned
dirt, health 20, closed menus and native packet ordering. A subsequent guard
also clears this access intent if Clean Temporary Supports is switched off;
the final focused/full gates must compile that latest guard. The original native
hopper-rim arrival test failed in 20s: north rim position, arrived true but
444.99 degrees of rotation at a single standing view. Arrival now accepts a real
grounded block-collision contact inside the horizontal arrival radius, within
0.5 of the nominal height and 0.025 of the actual feet. Surface run 2 passed all
four rim positions in 25s, without changing route/standingPoint heights or native
placement validation. Latest combined entry/room/cleanup run passed in 4m46s:
height-3/6 entries, wide floor, isolated five-post return with its bounded descent,
524,288-cell raised sealed room and both cleanup budgets. Native directions and
finished floors/walls were preserved, raw/owned dirt was zero, health 20, menus
closed and packet checks passed. Upper replay 52 is next on this production;
complete upper/fresh/core remain required. No release or merge.

Upper replay 50 stopped at 617/710 on an isolated owned column after restocking.
Read-only native queries proved a safe five-step descent to the ground and
multiple reachable open exits. Nearby roof columns and a single bridge did not
prove an onward route; there was no overhead obstruction or collision-height
error. Repeated climb/view retries starved the later staged-descent search.
The current change retains a per-view recovery stage, expires views only after
the fallback pass, and gives bridge/direct temporary searches their own cursors.
A new native regression starts on an isolated five-high dirt post beside a
25-wide finished floor. It must descend, find another entry, place the exact
directional block, preserve the floor and remove all dirt without damage or
packet/menu errors. Staged-entry run 1 was stopped before completion to include
the separate temporary-view cursor fix. Run 2 passed in 4m20s, including all
four floor cases and the 524,288-cell raised room. It was compiled before the
latest descent-continuation change: after each acknowledged landing, prefer
native walking, otherwise retain the checked exit and descend the next safe
owned footing after settling. Run 3 adds a 240-tick first-drop-to-ground bound
to the isolated-post case and passed the latest production in 4m11s: all four
floor cases, the bounded descent, exact native direction, unchanged floors,
zero raw/owned dirt, health 20, closed menus and packet checks; the sparse raised
room also finished all 83 blocks and restored its walls. Upper replay 51 is next.
Mining status also
distinguishes scaffolding, checked passages and mismatching schematic blocks.
Latest full upper/fresh/core gates are still required. No release or merge.

Upper replay 49 passed both prior stalls and built the structure to 704/710.
Checked exits and bucket restocking worked, but it then churned small steps
outside the sealed interior at 703/710 (two deferred route openings). A read-only
collision query proved three combined column/door routes to real fluid placement
views. Ordinary nearby-column routes proved none. The working change retains a
bounded four-direction door cursor for each validated column, permits entry only
after safe adjacent noninteractive two-cell doorway/onward-route proof, and uses
ordinary passage recovery after native climbing. It preserves the target's
required attachment face. A raised sealed-room regression requires both masks,
checks they never alter real blocks, and requires exact direction, restored walls,
no dirt, health 20, closed menus and native sequencing. Column-door entry run 1
completed all 83 blocks but closed the exit before removing four outside posts.
Run 2 passed in 2m57s after retaining final openings through support cleanup:
exact target, restored walls, no remaining dirt, health 20, closed menus and
native packet checks. The latest completion gate retains un-restored opening
bindings and prevents a stale zero-task scan from completing early. The raised
case now uses a 524,288-cell sparse schematic with the same physical room.
Run 3 passed latest production in 3m15s: all three floor entries plus the
524,288-cell raised sealed room, exact 83-block server structure and direction,
fully restored walls, raw/owned dirt zero, health 20, closed menu and native
packet checks. The next complete upper replay is run 50;
full upper/fresh/core gates remain required. No release or live-profile edits.

Upper replay 48 passed the old 469 stall and reached 617/710, then stopped
outside the roof after restocking. A read-only nearby-column query proved 14
valid entry/view pairs, including a column beside the actor and ordinary walking
across the finished roof. The target-centred six-block search could not include
that column because the placement views were nine or more blocks away. The
latest working change adds a three-block actor-centred candidate region at the
same two destination heights, deduplicated with the existing region. It retains
the three-millisecond cursor, cooldowns and native column/onward-route proofs.
A new 25-block-wide elevated floor test checks entry far from the placement
target, exact server direction, unchanged floor, dirt cleanup, health and packets.
Nearby-entry run 1 passed all three cases in 1m48s, including the wide-floor
crossing, exact server direction, unchanged finished floor, no remaining dirt,
health 20, closed menus and native packet checks. Upper replay 49 is next;
latest complete upper/fresh/core remain required. No release.

Upper replay 47 stopped at 469 after cycling several targets through the same
capacity-blocked exterior top. The entry planner did not consult the failed-view
cooldowns recorded by the capacity fallback, so it recommitted that top instead
of reaching later candidates or staging descent. The latest entry change respects
the existing per-work top cooldown. Native entry run 5 passed both height-3/6
cases in 1m25s, including exact directional server states, unchanged finished
floors, all dirt removed, health 20, closed menus and native packet checks.
Upper replay 48 now checks latest production. Latest full upper/fresh/core gates
remain required; no release or live-profile modification.

Upper replay 46 was stopped at 469/710 after a static committed climb. Its
read-only diagnostic had no escape markers left, confirming lifetime release,
but the 128-support pool was full and no safe recycling view was reachable.
The reservation method returned false for both sufficient capacity and an
exhausted recycling search. The caller then requested the impossible climb
repeatedly. The latest change calculates capacity separately and yields an
exhausted view/target to other work and checked descent, with view/target
cooldowns and stale recovery cleared. Native capacity-yield run 6 passed in
4m9s. Its two reachable ground blocks were confirmed in about two seconds,
within the six-second limit, while all four protected posts stayed intact and
native packet order passed. Both reservation variants, original native jump,
completed-post reclamation/new scaffolding, ground cleanup and height-3/6 entry
also passed. The fixture explicitly asserts decoded positions and native views
(x, then z, then y); earlier new-fixture failures were corrected without lowering
server-state, protected-post, packet, health or cleanup gates. Upper replay 47
now tests this production. Latest complete upper/fresh/core gates remain required;
the core pass below covers the preceding a2a18bd production only.

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

CI338 (28dcd0d) failed before stair-placement staging began: the direct complete-route query exhausted its3ms budget on Linux. Working test-only retry requires the same complete readonly route within60frames before injecting the plan; confirmed placement, protected footing and native packet checks remain unchanged. Checkpointbf76677 pushed; stash-final3 session37225 ownPID43944 running exactproduction. Fullupper/fresh/core gates pending.

At06:28 final4 on431ca0c passedold106doorstall and still progressed, but generic unstuck steps addedowned dirt awayfromcommittedcolumns. Cleanup traceB/C preserved. Working recoveryTick now requires a complete future one-step onward route unless climbing an already checked committed column. Negative blocked-stair native fixture now asserts recoveryPhase0/noqueuedplacement for the blind upward target. UNCOMMITTED and UNTESTED while final4 stillrunning old431ca0c own36152/session37420. Do not treat final4 as verification of thisguard; run latestsurface plus actualoffsetpit/recovery fullsuite and fresh/lateststash tests.

At06:29 final4 old431ca0c stopped with native stateC preserved: unfinished699/710/owned support counts114->117. Generic stepsguard now gets positive actualoffset-pit native escape added to focusedSurface suite, plus negativeblind-recovery assertion. Next surface-proven-recovery1 verifies latestUNCOMMITTEDproduction before lateststash/fullgates.

CI342on5e8b705 failed in retainedPredictions hopper-crossing assertion after prior focused/staging/cleanup/entry cases passed. Native build finished but route avoidedhopper; upward guard onlytestedcentreheight. Working jumpClear fallback proves approach above actualfloor lip within1.25rise then landingbodypath. Hopper nativecrossing factored unchanged into Surface/fullcore. UNCOMMITTED/UNTESTED while final5 runs d2d4226 (own37292/session27588), not thisnewjumpfix. CI344/346ongoing. Need latestsurface beforefullgates.

At06:39 final5 d2d4226 stopped unfinished699/710/owned117 because hopper jump regression from CI342 still affected itsproduction. No seeded-final pass. Surface-hopper1 nexttests UNCOMMITTED lipjump plus all guard/door/pit/surfacecases. Afterfocusedpass runfullUpper directly (coversallfivefinaljobs pluscleanup of same128legacy supports) andfresh; do not repeatoptional seededdiagnostic beforefullUpper, and do notclaimitpassed. Latestcompletecore/auction stillrequired.

At06:49 Upper59cea stopped475/710 after6minstatic. Readonly standingviews directvalid0; scaffoldviews bypassingonlycapacity via independentplacementqueries produced24validactualstanding/scaffoldnative jobs. Cause supportPlacement earlyfullpoolreturn hides feasibility, producingemptyrepositionoptions and loops. Working geometryquerycapacityindependent; actualfindWork rejects/recyles beforequeue and placeTick guards all temporaryqueues atcapacity. Existing exhaustednative fixture now asserts fullpoolscaffold proof survives, nojobqueued/reclamationofprotectedposts, and stillplacesothernativegroundwork; includedSurface. UNCOMMITTED. Surface-full-capacity1 next beforeUpper60, fresh/latestcore stillpending.
