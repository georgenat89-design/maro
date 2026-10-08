# Builder recovery work in progress

Updated: 2026-10-08. Branch: `codex/builder-ghost-and-look-recovery`.
Pull request: https://github.com/georgenat89-design/maro/pull/37 (draft).

## Resume here

The96a51a5 replay placed the four missing hoppers and another wire, reaching685,
then stalled. Its repair queue proves a depth/owner cycle:636 is owned by the
already complete637, but waits for the deeper opening674, whose owner636 is
still missing. This also suppresses other ready repairs and their wiring.
The correction orders depth only among repairs whose owners are ready;
final opening restoration retains its existing all-opening ordering. A native
fixture requires an owner repair before its deeper dependent opening while
unrelated work remains deferred. The96a production baseline failed this case
in1m19s; the fix repaired the pair in27 ticks. The full native home/access suite
passed in3m16s, including the buried hopper, native water, rooms, crouch mining
and camera continuity. Compilation passed. The exact685 replay is next.
The idle685 capture preserves native supplies and127 posts with no home2:
`350621E25FD2348C8C5E8A6E6F8DF0A9761042F818A86630B597C521069149B1`.
No full710/fresh0 pass has occurred. The beam/side-view probes did not prove a
native piston placement, so no speculative geometry change was made.
Prior9ce CI passed build/general/home/liquid checks but failed its sealed chest
return after120s at81/82, "Settling at build position". Final-head validation
must verify that case; current96a CI remains in progress.

The second668 replay on9ce70d2 advanced to683, then repeated access recovery
without completing another block for12 minutes. It was deliberately stopped;
this is not a completion pass. An idle native683 snapshot preserves the exact
server blocks, inventories, storage home,126 posts and access repairs, without
a transient home2 transaction. Its fixture SHA256 is
`CFFB11AC3AE51DFA79E6135F947BD2BE8E54AB866EDFC4576CC5A88274C45732`.

The next correction extends optional roof access to buried dry components.
It proves the native support hit, requested orientation, attachment and body
clearance using the existing read-only cleared view before registering or
mining an opening. The real placement still revalidates the unmasked native
world. A75-cell sealed hopper fixture requires facing north, all roof blocks
restored, zero raw/owned dirt, full health and bounded look packets. The9ce
production baseline failed this regression in2m52s without opening the roof.
The fixed hopper case completed in96 ticks with all native assertions passing.
The full home/access suite passed in3m11s, including door/dry-basin/native water,
prompt repair, elevated entry, crouch mining and visible eased camera checks.
Compilation passed. Actual683 completion, true fresh0 completion and
final-head CI remain pending.

The dedicated liquid suite on3b08e4a caught a coupled condition: removing the
above-only restriction for dry walls also removed their optional verified
roof-opening fallback. The sealed native basin stalled with its buried
retaining wall and source still missing, without a leak. The correction
separates optional roof access (fluids and basin boundaries) from mandatory
above-only placement (fluids). Door proof excludes viewpoints still requiring
a roof opening. Compilation passed after correcting a non-final lambda capture.
Both native75-block sealed water/lava basins now completed in17s each, restoring
the low wall before pouring and replacing roof/temporary blocks. The complete
native liquid suite passed in2m37s, including all four captured roof sources and
flooded departure at normal/low health. The unchanged668 capture and true fresh
build are next; full710/fresh0 remain pending.

The first668 replay on97fbc09 was deliberately stopped at668, not passed.
Native traces showed that the door planner never scanned: its placement-view
list was empty. Dispenser583 is a planned liquid boundary, so the shared
above-only rule incorrectly discarded all three valid same-height native
views even though the dispenser and basin were still dry. A native precise
opened-door query from the outside floor to the inside view returned true.
The pending correction reserves above-only entry for actual bucket work.
Boundary mining protection and native fluid containment remain unchanged.
The door regression now requires dry basin assembly and a real contained water
source. Its separate roof access becomes available only after the server
confirms the floor was placed through the door; the builder must then fill
from above and restore both door halves, with unchanged basin walls, full
health and zero scaffolds. An earlier ignored-source test exposed an unrelated
repair-counting interaction and was replaced with this complete native case.
The door planner also retains its cursors past the ordinary120-tick view retry
while its exterior-entry search is still progressing. The unchanged97fbc09
production failed the complete regression as expected in2m21s. The fixed full
native home/restock/repair/crouch/camera suite passed in3m3s; the dry basin and
real roof fill case took615 ticks. Dedicated liquid checks are running next,
followed by the unchanged668 capture and a true fresh build. No full710 pass.

The third646 replay on6fe7dd0 progressed to668, then was deliberately stopped
after more than20 minutes without placement progress. It did not pass. The
exact idle-home scene is preserved as `stash-fresh-668.json` with127 owned posts
and unchanged captured supplies (SHA256
8FEBB40786BBF2DC42DF5D49C614194475BC8F0A2593CE62E1B81EC883FD8659).
Native geometry checks proved that the closed wooden door blocks the inner
dispenser's valid placement views. The outside door floor is one block lower
than the inside floor and itself requires exterior access. The fix
proves the real opened door hinge collision, reaches an outside approach using
the existing bounded exterior-entry planner, opens the door through normal
interaction and registers both halves for restoration after their work.
Each nested door/approach/view query retains its cursor. A native enclosed-room
regression checks opening, repair ownership, unchanged walls, closed halves,
bounded look packets, health and zero scaffolds. Compilation passed; native
negative2 failed against unchanged6fe7dd0 production as expected in1m57s.
The fixed full native home/restock/repair/crouch/camera suite passed in2m55s;
the door case completed in461 ticks with both halves closed and walls intact.
Earlier test runs exposed fixture floor-placement/cleanup errors, corrected
before that full pass. Door routes now precede fallback scaffold walking and
use actual placement views; disabled supports no longer add scaffold views.
Test-only, unsaved options prevent AFK frame limiting during unattended runs.
The real668 replay is next. No full710/fresh0 pass.

The second646 replay on185bfae restored both861/1095 and reached648, then was
deliberately stopped. Native traces showed the buried dispenser583 exhausting
access planning while its waiting hopper dependants were repeatedly selected
as new sections during its retry cooldown. Other available work was starved.
The pending change carries prerequisite cooldown/access/layer restrictions
through bounded dependency chains when selecting sections, keeping supply
batches fixed while restocking. Its extended native fixture requires another
cube placed while the outlet is deferred, followed by the real outlet chain
after release. The unchanged185bfae JAR failed that regression as expected
in1m19s. The fixed full native home/restock/repair/crouch/camera suite passed
in2m29s, including immediate alternate work and subsequent hopper completion.
The unchanged646 capture is being replayed again. Complete710/fresh0
verification remains pending.

The first actual646 replay on8de83bc was deliberately stopped at646, not
passed. Native reads proved two remaining dependencies: registered upper
waxed copper1095 (depth3) had no anchor because its lower bulb861 (depth2)
was globally deferred behind it, despite their owner1717 already being correct.
The hopper chain638->620->602->601->583 also crossed the eight-cell section
boundary, so its missing terminal dispenser was outside the candidate batch.
The pending fix restores contiguous full-cube repair stacks from below and
expands same-phase/layer placement dependencies with a96-cell/2ms bound.
Existing owner, body, fluid, retry and layer checks remain in force. Native
vertical-repair regression failed against unchanged8de83bc production, as
expected. Both new regressions and the full home/restock/repair/crouch/camera
suite passed in2m30s. An initial positive run placed both repair cubes but
exposed a test assertion earlier than the existing20-tick completion quiet
period; the corrected fixture waits for bounded confirmed completion without
changing its120-tick placement deadline. The unchanged646 replay is next.
No complete710 replay or fresh build has passed yet.

Current: CI441 passed all checks on a7d62c8. True fresh9 started at0 and reached
647, but timed out after one hour at644 with full health. The actual646 scene
was saved, with126 owned posts, unchanged supplies and idle homes. Its chest
still contained two oak signs and player inventory had22 empty slots. Dirt
beside home1 blocked the chest view; walking around it crossed the three-cell
distance test and approachChest repeatedly initiated home1 again. The new
controller retains confirmed storage arrival for the active return trip, saves
that state with home2 metadata and clears it when returning/deleting home2.
The native obstruction regression failed on the unchanged old a7 JAR at the
same edge offsets and repeated home1 state as the real scene. The fixed suite
then collected exact stock and returned with one home1 trip, even after a
mid-storage save/load. This actual646 snapshot is now an available replay.

A read-only native probe also found14 immediately placeable blocks while a new
route started, and three during a later route. A bounded2ms/96-cell look-ahead
now selects dry full cubes before starting a new access job, leaving committed
walks/columns intact. Its native ordering regression failed on the verified old
production JAR, as intended: the old builder moved before placing a reachable
cube. Both fixes passed the full focused suite in2m21s, including roof-edge
returns, exact requested stock, unfinished bucket retention, repaired enclosed/
elevated access, crouched column cleanup and eased visible camera rotation.
Current CI, actual646 replay and a complete fresh0 run remain pending at this
source snapshot. The one-hour fresh deadline is unchanged;
no710 completion or finished release has been established.

Current: native704 replay placed all four source fluids and the observer,
removed all126 original temporary posts, and reached706 during final repairs.
It then timed out waiting to save a restock home. The actual position was
grounded on the neighbouring blackstone roof block, while the nominal feet
cell had air below. Home validation wrongly tested the centre of that cell.
The new fix checks the full standing body at the exact position, records its
actual collision support, protects that support while away and persists it.
The focused native home suite now reproduces the exact roof-edge offsets on
stone and dirt and checks save/load, command order, grounded return and deletion.
Home-edge4 passed the entire focused suite in2m3s, including exact requested-item
pickup, unfinished bucket retention, prompt repairs, enclosed/elevated access,
crouched vertical cleanup and eased visible camera rotation. Home-edge1 aborted
in its launcher on compiler stderr;2 was deliberately stopped before tests to
correct the new fixture's permanent-versus-temporary floor assertion;3 exposed
an overlap with the existing home3 platform, fixed by moving the test fixture.
CI424 passed all general in-game, builder and auction checks on52a60c9. The new
home-footing patch still needs current CI and the final fresh0 run. No710
completion or finished release has been established.

Latest, 21:47: native704 replay1 placed the previously blocked lava source and
advanced to observer phase after removing its single owned dirt obstruction.
Read-only native masks proved exactly(-26220,66,-150578) was required: its
removal exposes two dry foot66 views with a native source-floor UP hit, and
safeToRecycle is true. It is not an escape support. New bounded recovery scans
one-post masks, routes to a checked mining view, mines normally and rechecks
the bucket target. Every finished passage for this task stays above the source.
The actual704 fixture contains all three water sources and unchanged supplies.
The source also now counts advancing entry queries as section progress, and
lets a checked advancing walk/climb or immediately valid placement finish
before applying its accumulated unproductive-work timeout. The running native
704 replay does not include this last timeout grace; all710/cleanup/health/
fluid/packet/home gates, latest focused home test, full CI and final fresh0
remain pending. Native13904 belongs to checkpoint704 in the isolated worktree.

Latest, 21:38: actual698 replay2 reached702 and then repeated the same entry
search without moving. Read-only traces proved the 360-tick work timeout reset
its view search around candidate807, before the elevated-entry stage finished.
The working fix preserves geometry query cursors across a target switch and
caches each shared column base's walking proof. Deferral is40 ticks. Native
block receipts still invalidate the views. The actual702 server scene is saved
in stash-fresh-702.json with127 posts, three openings, compatible702, idle homes
and empty server home2. Native planner702 replay1 is running this fix; its full
710/cleanup/fluid/health/packet/home checks and a final fresh0 run are pending.
Stockpile retention now includes unfinished max-count1 source buckets. The
native requested-item restock regression also checks that a future water
bucket stays in inventory during an unrelated repair section's home trip.
That focused regression and full CI on the newest code remain pending.

Latest, 21:25: native698 replay immediately passed the buried349 stall and
reached700. Both ordinary and wider radius5 read-only placement probes found
zero views of349; liquidBoundary was false, so no fluid safety rule changed.
A native masked proof identified its north neighbour as a safe removable cube
with a valid target-base hit and walking route. Recovery now checks adjacent
finished dry cubes for a reachable upper view and a safe one-block descent onto
existing footing. It applies only to plain full-cube work without properties,
uses the existing acknowledged descent, and registers the removed wall for
restoration. The actual698 scene with125 posts and unchanged supplies is saved
in stash-fresh-698.json. Native log builder-buried698-recovery-native-1.log is
running; full710, dirt/liquid/health/packet/home checks remain pending, as do
the new requested-item home regression, current full CI and final fresh0 run.
The previous695run3 was deliberately stopped after stable698 capture. Latest
source still includes the requested-material/partial-stack/home-budget fixes.

Latest, 21:14: readonly native restock tracing disproved chest obstruction.
The six-row handler received real contents and collected warped trapdoor/sign
from slots2/3. A later polished-brick section then deposited those same small
unfinished stacks (player slots87/88), repeating693 and home trips. Stockpiling
now retains partial stacks while their material still has unfinished work.
Each restock also preserves at least one of the requested item if refreshing
the section drops it from the batch. Home transitions retain per-target work
budgets; home/menu time was already excluded from those active-work clocks.
Native695 run3 is now testing these changes against the unchanged captured
world/supplies. The home regression now tests an initially empty section's
requested stone through actual first-trip pickup, exact quantity and placement.
These latest edits are uncommitted; primary7d/PR6b/CI416 do not include them.
Runs1/2 were deliberately stopped without a710 pass. Native run3 log:
builder-repair695-recovery-native-3.log. Final fresh9 is still unstarted.

Latest, 21:04: the actual556 replay passed the old559 ceiling and reached695.
It then stalled on a prompt repair outside its current section. The native
snapshot proves storage still had the exact remaining materials: blackstone2,
cracked bricks1 and polished bricks4, with another polished brick held. The
section supply batch excluded registered repairs even though findWork selected
them; the chest was marked checked without collecting the needed repair stock.
Section requirements now include ready registered openings, deduplicated with
ordinary section cells and subject to the same phase/layer/defer rules.
The real695 server world, inventories,126 owned posts and7 openings are saved
in stash-fresh-695.json. Native695 replay is running in the isolated checkout,
log builder-repair695-recovery-native-1.log; the original556 replay was stopped
deliberately after capture. This latest repair-supply fix is uncommitted.
Primary/PR/CI414 remain7d9677b; its full replay/fresh acceptance is incomplete.
Prepared fresh9 must use the final commit after the repair-supply test passes.

Latest, 20:46: isolated native sealed-cleanup run5 PASSED in1m47s. Both the
actual four-post outside-column scene and the original room sequence complete
with every wall restored, zero dirt, full health and normal action packets.
The failing CI412 scene was outside the schematic footprint: its owned dirt
target has work index -1, which prevented the checked passage planner from
opening a doorway. Cleanup now permits that checked route for owned supports,
retaining the existing wall/fluid/attachment/footing guards and repair queue.
The first captured-room reproduction used incorrect relative coordinates;
run5 corrects them to start.west(3).north() for the column.

Fresh8 on d3 reached559, then repeated556..559 for over11 minutes; it was
stopped deliberately after capturing the actual compatible556 server scene,
126 owned posts and9 registered openings. A new per-cell360-active-tick work
budget includes helper scaffolding, pauses for native actions/home transactions,
and defers an unproductive route without losing support/opening ownership.
Temporary placement receipts no longer erase deferred-target cooldowns.
Block geometry receipts also clear stale descent/hatch query state. The
actual556 checkpoint replay is now running in Maro-column-cleanup, log
builder-fresh556-recovery-native-1.log. It is not a fresh710 pass. Current
changes are uncommitted; primary/PR remain d3. CI410 on parent d5 passed;
CI412's first d3 attempt failed the now-fixed outside-column cleanup case.
Full current regression, checkpoint710 and a fresh zero-block710 remain
required. No live Minecraft profile was modified.

Latest, 19:18: d5 native549 replay reached579 and was stopped deliberately to
test a confirmed code defect: prepareElevatedEntry proved masked wall removal
but discarded that opening when committing its exterior column. The column now
retains its top, onward destination, work owner and checked blocks. After actual
arrival it rechecks the full route and registers/mines the opening, then repairs
it through the normal prompt queue. Pause/home interruption drops unexecuted
intent. Native home run in Maro-column-cleanup, log
builder-entry-handoff-home-native-1.log, PASSED in1m55s: ground-level enclosed
access187 ticks; the new raised room538 ticks, with its retained exterior-column
opening observed, all83 planned blocks exact-compatible, zero supports, full
health and normal rotation/action packets. Crouch/visible camera/home order also
passed. Main has no native client; the next final-production test is from zero.
No full710/fresh pass yet; CI410 on d5 remains pending (job112547150301).

Latest, 19:10: user specifically requests faster decisions. The d678 native549
replay reached568, but repeatedly reclaimed/climbed capacity and stopped making
schematic progress for minutes; it was stopped deliberately. Homes had disabled
the otherwise checked passage/ceiling/floor planners. Those registered, dry,
attachment-safe routes now remain available after ordinary walking/home/column
checks fail. The native mining guard permits only registered access jobs and
preserves liquid boundaries and above-source liquid entry. Passage and floor
search state is also owned per ViewSearch, avoiding cursor resets between work.
BuilderWalk now caches standing, clearance and footing-point queries within a
single search; no geometry result is reused by a later search.
Focused native home run1 passed in1m15s with the access change, and run2's new
enclosed room completed native opening, missing glass and all wall repairs in
479 ticks with full health/zero supports (that run had no pickaxe). The fixture
now supplies a pickaxe for normal mining speed. Run2's remaining head/crouch
checks and the new real549 replay/final710/fresh/CI checks still need confirmation.

Latest, 18:57: fresh6 reached549/710, then stayed at the same finished roof
footing69 while four targets alternated. The captured server scene has548
exact states and549 compatible states,127 owned posts, safe/clear storage home1,
no saved home2 and no registered openings. Trace showed descent probes sharing
one destination/cursor, and also confirmed some searches exhausted without a
usable roof exit. ViewSearch now retains both useful-only and staged descent
progress. After fully exhausting access planners, a dry safe home1 can return
the actor to storage ground so pillar recovery can approach from another side.
No home2 is saved for this recovery, and home3 remains untouched. Fresh6 was
stopped deliberately after preserving its real scene; it is not a fresh710 pass.
Native549 replay with Meteor, exec10852/PID6064, restored that exact scene and
has progressed to554 after native home1 travel, reclamation and a new climb.
Full710/zero-dirt/liquid/health/packet checks and current CI remain pending.

Latest: CI402 passed five-post column construction in43 ticks and complete native
cleanup, then failed accessCapacity(false): Settling on retained ledge footing
with three old dirt posts left and the body on another solid ground block.
mineTick now abandons an unsafe/never-settled peek after24 ticks, cancels native
mining, releases its owned crouch input and retries a safe view. Build and
game-test compilation PASSED in7 seconds. Current native CI must verify this
guard. It was edited only in Maro-column-cleanup; active fresh6 in primary Maro
continues on376dfc8 without changing its loaded classes or JAR. Fresh6 began
from0 with zero supports; home1 was established beside storage and remains
safe with AIR feet/head. It reached174/710 and completed multiple real home2
round trips. Checkpoint replay recovered423->452 before it was deliberately
stopped to prioritize this full zero-block run; no checkpoint710 pass claimed.

Latest, 18:05: fresh-reserved-homes-5 was stopped deliberately at 423/710 after
the user reported it frozen. Read-only native sampling proved work cells 828
and 1062 alternated while sharing entry-search cursors, repeatedly restarting
the same unfinished exterior-access search. Each ViewSearch now owns its entry
candidate, passage-probe and retry progress. The production change compiled.
The exact server world, inventory, chest stock, home 1 and 127 owned supports
were captured in stash-fresh-423.json before stopping verified test PID27936.
Exact state equality counted 416 blocks; the normal compatible-state matcher
must confirm 423 when importing that captured scene. This is a checkpoint
regression, not a fresh build pass. Native replay with Meteor is running in
Maro-column-cleanup, exec87128, PID6280. Full completion remains pending.

Latest, 17:40: CI398 on 39f80d1 passed the standalone rotation checks and failed
verticalPillarPacing with only the bottom two owned posts left. The five-post
buildup itself took43 ticks. In the isolated Maro-column-cleanup checkout,
cleanup without homes now proves the complete owned-column descent before
removing a reachable tip that would strand its lower posts. It uses existing
native acknowledged one-step descents, protects dependencies and preserves the
unowned platform. Build plus compileGametestJava PASSED in1m59. Native CI is next.
This new path is explicitly gated to Use Homes=false, so the active fresh replay
with homes enabled continues unchanged in the original checkout. Fresh5 has
reached359/710 with128 supports; it reclaimed capacity and kept building. The
read-only home snapshot confirms safeStorage=true, readyFor=true, air in the
saved arrival body, and no stale home2 after return. Full completion is pending.

Latest, 17:18: fresh-reserved-homes-5 is running unchanged production 9ae4ee7,
exec session33697, owned Java PID27936. It has progressed beyond 58/710.
CI394 failed its standalone raisedTurn walker because the test never advanced
the new shared rotation clock. Standalone native movement tests now explicitly
begin that clock using player.age; production AutoBuilder already begins it
every work tick. No production changes or active-client rebuild were made.
The fresh 710-block pass and current full regression gates remain outstanding.

Latest, 17:12: full fresh-visible-restock-4 exposed a scaffold occupying the
confirmed home-1 feet cell. Native read-only snapshot proved safeStorage=false,
readyFor=false, dirt at the saved feet and end stone below. That correctly
prevented unsafe teleporting, but caused ordinary walking supply trips instead
of the requested home-2 cycle. The replay was stopped at 86/710, 45 supports.

Home 1's exact standing body is now reserved in every temporary-placement and
scaffold search through reservedSupplyAccess. beginRestock starts home-2 setup
immediately, before any storage approach; home transitions also release owned
recovery jump input. The full fresh verifier now requires actual home-2 round
trips, eliminating a zero-trip false pass. builder-home-reserved-visible-native-1
PASSED in 1m34 with Meteor: storage feet/head scaffold rejection, exact restock
order/native supplies, protected saved return, rejected retry, repair14 ticks,
crouched3/3 cleanup and actual visible/rendered turns all passed. Restart fresh
with this protection. The earlier cddc963 test JAR lacks it and is superseded.

Latest, 17:02: the user reported that the visible head did not turn. The retained
independent camera had intentionally stayed fixed, hiding native head motion.
It now visibly eases toward walking/action aim with 12-degree yaw / 8-degree
pitch limits, acceleration/braking and frame interpolation. Action aim waits
for the visible view to face the target. Actual mouse input still controls the
view briefly; zero mouse deltas do not restart the hold and freeze following.

builder-visible-head-native-2 PASSED in 1m27 with Meteor: visible yaw/pitch must
actually move during a target turn and three native placements plus walking;
camera rate bounds, actual rendered view, native packet limits and mouse/native
aim separation all passed. The storage/restock/pause-return suite, protected
dirt footing, rejected return, repair in 14 ticks and crouched 3/3 column cleanup
also passed with the moving camera. Fresh-restock-homes-3 was stopped at 20/710,
21 supports for this user steering; it was not a completion pass. Restart the
full fresh replay with the visible-camera changes. PR stays draft until gates pass.

Latest, 16:54: builder-restock-return-native-7 PASSED in 1m19 with Meteor.
It verifies actual native chest withdrawal (one stone, seven retained in chest)
then placement after exactly delhome 2 -> sethome 2 -> home 1 -> home 2 ->
delhome 2. Deletion follows real grounded native arrival; no home-3 command is
accepted. The transient return survives saved metadata reload and a rejected
teleport, protects its dirt floor while away, and resumes/clears through Start.
Storage replacement/error bounds, access repair in 13 ticks, crouch-only mining,
three-block vertical column edging, three placements with walking and stable
actual camera, and 12/8 native packet turn bounds all passed together. The full
fresh homes-enabled 710-block replay is next; completion is not yet claimed.

Latest, 16:47: the user clarified that home 2 must be a transient restock
return point and home 3 must not be used. BuilderHomes now has only two local
slots: verified storage home 1, and the current confirmed restock return.
Restock stops at the work area, deletes/replaces home 2, travels to storage,
gets native chest supplies, returns to home 2, confirms safe grounded arrival,
then deletes it and plans the next needed job. Home 3 stays untouched. A work
return may use temporary dirt; that floor is protected while away. Confirmed
return metadata survives cancel/pause and saved placement reload.

The first native integrated restock cases reached the exact command order and
resumed placement with the exact quantity withdrawn from a real double chest.
The test packet observer was corrected to recognize Minecraft's native teleport
acknowledgement, as proven by mapped ClientPlayNetworkHandler bytecode. It still
rejects extra builder movement packets outside the native tick/teleport handler.
The raised-dirt test fixture needed its new floor delivered before fixture TP;
the focused suite is being rerun. Full fresh completion is still outstanding.

The second fresh replay was stopped at 30/710 on the user's actual-head rotation
report. Shared walking/aim rotation now caps yaw at 12 and pitch at 8 degrees
per tick, with acceleration/braking of 2 degrees per tick and retained momentum
through changes of action. Native natural-rotation and packet-rate suites both
PASSED before the new home-2 clarification, including three placements/walking,
stable actual camera, repairs and crouched 3/3 column cleanup. These changes
and the new restock semantics still need the fresh 710-block replay together.

Latest, 16:26: the first fresh homes-enabled replay was stopped at 79/710,
47 supports after the user reported snapping onto placed blocks. A read-only
native camera trace caught 161.49, 144.48 and 121.44 degree view changes in a
single tick immediately after placement confirmation. Entity aim was already
smoothed; releasing the independent camera after two idle ticks exposed it.
Camera ownership now persists through placements, delays, walking and home
travel while builder work is active. Menus/pause/Free Look/Free Cam still yield.

Native builder-camera-continuity-1 PASSED in 1m13 with Meteor: the entire storage,
homes, head, repair and crouch suite plus three native placements with walking
between them. The actual rendered view and mouse-independent camera remain
stable after each placement and during walking. Fresh replay is being restarted
with these changes. No full 710-block pass is claimed yet.

Latest, 16:17: storage home setup now replaces home 1 automatically. Start no
longer requires three empty slots or the player already standing at storage.
It walks to a checked dry view of the marked chest, or uses an already verified
storage home before replacement. Only after native arrival/settling does it send
delhome 1, accept deletion/already-empty feedback, then send sethome and verify
the home 1 save. Missing/rejected receipts stop without a save. Existing homes
2/3 are kept; occupied/unreadable optional slots stop retrying and do not pause
the schematic. Home setup also respects pause and the low-health guard.

Native builder-storage-replace-2 PASSED with Meteor: actual approach from twelve
blocks away before deletion, pause/restart with released movement, replacement
with all three slots occupied, resumed Start, preserved homes 2/3, optional-slot
continuation, rejected/missing deletion receipts, inaccessible storage without
deletion, already-empty home 1, three saves in order, verified storage-home return
before replacement, stationary warmup, native arrivals and bounded cooldown.
Prompt repair, crouch-only mining, 3/3 vertical column cleanup with retained ledge
and full health, independent camera/mouse and native packets also passed.

The previous 6c31336 full CI passed build/general UI, homes/head/crouch, liquids
and many cleanup fixtures, then FAILED in the five-block vertical-pillar cleanup
case with two lower supports retained and "Cleanup needs a checked access route".
That failure is not fixed by this storage-setup change. Full CI and full 710-block
completion remain outstanding; PR 37 stays draft.

Earlier, 15:45: mandatory confirmed homes, independent smooth head aim, prompt
access repairs and native crouch peeking are implemented. Homes reserve 1 for
storage, 2 for dry interior and 3 for upper access; occupied slots are preserved.
Home route searches retain cursors under a per-tick budget and run before
scaffold fallback. Finished base/wall mining is rejected with homes enabled;
only registered roof openings above a liquid source are eligible.

Earlier, 15:58: the user's clarified vertical-column sneak action is implemented.
Native builder-column-edge-4 PASSED in 48s: a three-block temporary column loses
its upper blocks, then its hidden lower block is mined after actual sneak edging.
Outbound native mining packets verify crouch, ground and full health at the
action; the retained permanent ledge survives and all three owned posts disappear.
Native builder-homes-crouch-cleanup-1 PASSED in 4m08 on 79c4751: both 82-block
sealed/AIR-roof cases, spare/full capacity and supply lids restore with zero owned
dirt, full health and normal movement packets. The clarified edge extension is
newer than that cleanup run. Teleports now settle before command submission and
discard stale passage/ceiling movement plans while preserving access repairs.

Native builder-homes-crouch-2 PASSED in 43s with Meteor: three normal sethome
receipts, native storage/interior arrivals, stationary teleport warmup, occupied
slot protection, bounded cooldown handling, pause/save/reload repair retention,
repair in 11 ticks before other dirt cleanup, actual crouch-only mining with
obstruction/footing intact, camera/mouse separation and native packet ordering.
Native builder-homes-top-fluid-1 PASSED in 3m05: all four stash sources, sealed
water/lava basins, roof restoration and healthy/low-health flooded departure.
That liquid run preceded the newly requested crouch feature; integrated cleanup
is the next check. Base c391e79 was merged without conflicts.

The earlier full Upper71 replay was stopped at the user's home-travel steering:
703/710, 51 supports and seven registered openings, full health. It did not pass.
Fresh and Upper full 710-block completion remain unverified with the new homes
behavior. PR stays draft while full CI and long replay gates remain outstanding.

At 14:56, base 4f0de6b (ESP preview and Potato Graphics changes) was integrated
without conflicts. Native top-cleanup-1 failed in the owned supply-lid fixture
because liquid tests had left Restock When Empty disabled in the test config.
The fixture now restores that setting before each independent case. Its debug
"inventory" map actually reports remaining materials, not carried items.
No production supply workaround was added. Native top-cleanup-2 is running
on the integrated tree with that deterministic test reset.

At 14:53, native top-fluid-8 PASSED in 3m09s on the working production.
The captured stash roof replay filled all four actual sources in about 88s,
kept all 20 basin retaining cells intact, preserved dry footing and full health,
opened only cells above their associated source, and passed the escaped-fluid,
unintended-waterlogging and native interaction checks. Closed-roof water and
lava cases also restored their complete roofs and removed all temporary dirt;
healthy and low-health flooded-room departure cases passed. Fluid fallback
floor and ceiling openings now also require a height above the source.
The focused source replay does not establish full stash restoration or cleanup.
Native top-cleanup-1 is checking the updated floor-search cache next; latest
full Upper/Fresh and exact-head core/auction CI remain required before delivery.

At 14:42, Upper70 on ff7deb4 was stopped unfinished704/710 with full health
and128supports during roof departure for the water layer. Read-only native
queries proved a reachable hatch at (-26211,69,-150581), with complete owned
column descents to all seven valid dry fluid views. An earlier rejected hatch
repeated expensive native preflight before each exit search; preflight used
the frame budget, so the escape cursor never advanced to the useful hatch.
The working fix retains candidate preflight/geometry across frames, resets
it when the feet, work or closest view changes or native work geometry updates,
and revalidates actual approach, attachments and full descent before mining.
The native water regression now also starts at the captured roof pose, with
the actual stash and captured posts. It must fill all four level0 sources
(three WATER cells282/339/390 atY64, LAVA cell582 atY65), retain all basin
walls/floors, open no low water entrance and preserve full health/dry footing,
with no escaped fluid/unintended waterlogging and native packet checks.
This is a focused source replay, not a completed stash/cleanup gate. Testing
the working cache fix and roof regression is underway; final full gates remain.

At 14:10 the user requested safe access from above for water and lava.
Upper69 on fd396da was stopped unfinished at 700/710, 128 supports and
full health to implement that steering. No full replay pass is claimed.
The working change restricts bucket placement to dry views above the source,
aims at the basin floor's UP face, checks the connected planned liquid volume
using vanilla flow feasibility, and rechecks containment at the actual bucket
interaction. Basin floors and retaining walls are excluded from access mining;
registered retaining openings get immediate restoration priority. A closed
roof can supply a checked opening above the source, with its intact viewing
ledge preserved and every removed roof block registered for later repair.
Native top-fluid-5 PASSED 1m28s: low leaking access refused; an actual
contained source poured from above without waterlogging the overhead panel;
closed-roof water and lava fixtures each repaired an old low opening before
pouring, opened only roof access, retained every basin wall, contained all
fluid, restored all roof blocks and removed all dirt with full health and
native packet/menu checks. Captured flooded-room healthy/low-health departure
regressions also passed. A buried retaining repair shares the roof strategy.
Native top-surface-1 PASSED 2m23s, including all16 narrow landing variants,
partial panels, contained bucket work, doors, pit recovery, hoppers, native
stair staging and full-capacity guards. Production was unchanged between
the two focused passes.
Latest full Upper/Fresh, exact-head builder/auction CI and merged artifacts
remain required gates.

At 13:35, Upper68 on the integrated ecc8aeb tree failed unfinished698/710
with128supports. The server confirmed drowning during bucket access. Its
saved native world showed a waterlogged overhead trapdoor and falling water
in the requested source/opening. Bucket planning now searches the required
support face and verifies Minecraft's actual FluidFillable target again at
the published look, avoiding unintended waterlogging of an adjacent panel.
Active Auto Move also leaves occupied, connected water through a bounded
native collision route ending on dry footing. It checks an onward dry route
to the pending work, keeps departure committed across isolated dry posts,
and prioritizes breathing after a bounded unsuccessful onward search.
Staff, nearby-player, Semi Auto and explicit pause guards still apply.
Low health pauses ordinary work after departure rather than freezing in water.
Normal water entry and all lava hazards remain rejected; no source is drained.
Native water-source-1 reproduced the overhead-panel mistake before the fix.
The captured491-block Upper68 room reproduced stationary head immersion and
air loss before departure support. Water-source-8 PASSED55s: actual intended
source with a dry overhead panel; captured-room escape and resumed server
STONE placement at full health; a forced6-health escape without further
damage followed by a low-health pause on dry ground; sources preserved,
ordinary wet re-entry rejected, zero temporary placements and native packets.
Water-surface-1 PASSED2m24: final water cases plus all16 narrow landings and
retained surface/partial-panel/door/pit/hopper/capacity checks.
Water-staging-1 PASSED3m31: five-post43tick pillar and full cleanup, nearby
tips, thick walls and ceiling placement/owned cleanup, all restoration,
zero dirt, full health and native packets.
Water-cleanup-1 PASSED4m: registered repair11ticks before the250000-cell
scan, selected lids, full/spare capacity, sealed-build and AIR-roof cleanup,
all restoration, zero dirt, full health and native packet/menu checks.
Full latest Upper/Fresh and exact-head complete builder/auction CI remain gates.

At 12:45, work resumed after the user's pause. Upper67 on2c6f3f5 ended
unfinished704/710 with128supports and fullhealth; it had stopped over a lower
waypoint while the body's toe still overlapped the upper ledge. A native
edge-start regression reproduced the stall on2c6 (north, cleanup approach).
Lower grounded waypoints now use short centring inputs until the body clears
the ledge; the landing brake has a.12radius while still grounded and.4radius
when airborne. Grounded waypoint advancement and settled cleanup remain.
Edge-landing-surface-2 PASSED1m53s: all four directions, exact and cleanup
approaches, normal starts plus.218offset edge starts with18mm toe overlap,
all native landings/fullhealth, plus retained complete surface checks.
Staging/cleanup and full latestUpper/Fresh remain pending. CI364 on prior2c6
completedSUCCESS, including fullbuilder/AH45m38s, but does not cover this fix.

At 10:33, the user's observed final cleanup loop has a native movement fix and
a direct access-repair queue. Upper66 on a7da639 was stopped unfinished at
698/710 with 16 supports after cleanup repeatedly overshot narrow lower posts,
took fall damage and rebuilt access. External per-tick evidence records a
checked three-block landing at ticks33945..33959 followed by overshoot and
an unintended six-block fall by33973. It is not a pass.
The walker now brakes horizontally above lower landing centres and only
advances waypoints on ground contact; cleanup waits for landing and movement
to settle before mining. Known openings are refreshed directly, original
work phase/layer restored and repairs queued as soon as their access posts
are cleared, retaining deeper-first repair and final completion guards.
Surface-2 PASSED1m30s, including native narrow drops in four directions using
both exact standing and cleanup approaches. Immediate-repair-cleanup-2
PASSED3m55s: a registered block in a250000-cell schematic restored in11ticks
before its next global scan completed despite a stale observer phase and
empty work queue; retained chest lids, full/spare capacity, sealed-room and
AIR-roof cleanup restored all openings with zero dirt/fullhealth/packets.
First cleanup attempt failed test setup because a fixed internal budget was
looked up as a public setting; the fixture now reads its actual NumberSetting.
No production workaround. Full latest Upper/Fresh/core/AH remain pending.

At09:52, the user's quicker build-up/removal request is implemented using vertical
pillars instead of unnecessary stairs. Upper65 on9975a88 was stopped early
unfinished to implement that request; no pass. Native quick-pillar-2 PASSED
3m22s, including a five-post straight-column climb in43ticks (first confirmed
post to grounded five-block landing), complete target placement/cleanup,
zero dirt/fullhealth/unchanged platform/packets, nearby-tip cleanup before
building distant access, and retained staging/thick-wall/ceiling cases.
Quick-pillar-3 PASSED3m28s with additional adjacent-stair connection protection
and continuous pacing restricted to a fully proved committed column; its
five-post climb also took43ticks and completed all restoration/cleanup checks.
Quick-pillar-surface-1 PASSED1m7s, retaining blocked-climb and full-capacity
guards, native pit recovery, committed stair staging/protection and hopper paths.
Changes:
bounded shortest-current-column planning before exterior/stair search, full
native ascent/onward proof for exterior columns too, exact remaining column
capacity and base protection, no40tick pause between confirmed column jumps,
and nearby visible cleanup tips before distant high targets. Full Upper/Fresh,
latest core/AH CI and final merged artifacts remain pending.

At 09:36, Upper64 on889b114 FAILED its30-minute fixture deadline during
cleanup. All original water/lava/observer targets were placed; the ceiling
placement route worked, and owned-support cleanup selected ceiling entry,
reclaimed posts, opened further registered access and advanced to new targets.
Final progress697/710 with104supports; native health stayed20. It is not a
pass. A capacity trip abandoned one ceiling base, but restoring its five mined
posts in collision-only proof did not recover the route from the current actor;
the original-source route was still valid without them. No speculative routing
patch. The complete survival fixture now allows72000ticks (60minutes) for
placement, selected-storage trips,128legacy posts and final repairs, retaining
all completion/server/zero-dirt/packet/menu assertions and adding final full
health. Production code is unchanged. Full Upper/Fresh/latest CI remain pending.

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
