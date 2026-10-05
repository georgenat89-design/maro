# Auto Builder — Minecraft 1.21.11

## Start the build

1. Right-click **Player → Auto Builder** to open the control panel. Click **Choose Schematic**. Use **Open Folder** in the library to put files in the game's `schematics` folder.
2. Choose **Origin: Here** or **Origin: Target** and adjust X/Y/Z with the small nudge buttons. **Options → Placement** contains rotation and mirroring. **Materials** shows total, remaining, owned and missing quantities.
3. Close the panel, look at each double chest you want to use and press **R** to add it. **Shift + R** removes the chest you are looking at. Selections are remembered for this world/dimension; adding an already selected chest refreshes cached contents. Either half of a double chest refers to the same selection. The panel shows how many chests are selected.
4. Set an auction **Budget**, select **Mode: Automatic**, then click **Start Build**. The panel closes and building starts without holding right mouse. Semi Auto is available for holding right mouse. **Pause / Cancel Buy** releases movement/mining input.

Automatic is the default for new configurations. Existing configurations retain their chosen mode; change it with the panel's mode button. Show/Hide Preview controls ghosts independently of building. Protocol words and individual rendering constants are internal defaults, reducing the settings list.

**Restart Build** rescans the same schematic at its current origin and starts again, preserving matching blocks and tracking its temporary supports for cleanup. **Cancel Schematic** stops building/buying, hides the preview and unloads the file, including cancelling an unfinished file load. Blocks already placed remain in the world. **Pause / Cancel Buy** keeps the schematic loaded for resuming.

## Saved builds and reconnecting

**Save Build Progress** is on by default. **Saved Builds** in the panel has a **Last Session** slot plus ten named slots. Choose a slot, enter a name and **Save Current Placement**. **Load Saved Placement** restores the schematic snapshot, origin, rotation/mirror, ignored materials, selected chests, temporary-support tracking and shared build budget. Loading pauses actions; join the saved server/dimension and press **Start / Resume**. A saved placement never silently moves to a different world.

The active placement is checkpointed every ten seconds, when starting/completing and on disconnect. The next launch restores the selected active placement, paused. Resume rescans actual world blocks rather than replaying a stale completion counter. Snapshots live in the game's `maro/builder-placements` folder and remain usable if the original schematic file is moved. Loading another slot first checkpoints the active build. Cancelling unloads the active placement while keeping saved slots available. Choosing a new schematic keeps named saved builds available; **Last Session** can be replaced by the next unsaved build.

## Placement and route recovery

Placements wait for the server's block update before advancing or recording temporary dirt. Rejected/unconfirmed placements retry from a clear standing position. Walking considers safe diagonal paths and follows clear straight sections without a stop at every cell. Blocked standing positions become eligible again after a short timeout, and the builder tries other unfinished targets instead of waiting minutes on one failed route.

Floating blocks can use short dirt columns. When dirt runs out, the builder checks selected chests once for the reserve and then buys missing dirt if **Auto Buy When Missing** and a positive budget are configured. Even with a reserve of zero, an emergency refill requests eight dirt. Only the builder's own temporary dirt obstructing a blocked walking corridor may be mined; footing, useful one-block steps and unrelated blocks are preserved. Completion cleanup works from the highest temporary supports downward.

## Auction purchases

Enter a positive **Budget** and click **Buy Missing**. Zero disables buying. **Options → Materials → Max Price Per Item** sets the unit-price ceiling. **Auto Buy When Missing** defaults on: needed inventory items move to the hotbar first, then the builder checks your selected double chests, and finally buys missing materials and resumes building. **Support Dirt Reserve** is shared by auction buying and restocking.

**Prepare Whole Build** is on by default. Start first deposits held supplies across selected chests, visits every selected chest and counts their combined contents, buys every missing material for the whole schematic, and stores the purchases before placing the first block. Inventory-full deposits continue the same shopping list and spent total. Tools, support dirt and the configured steak reserve are included. A budget/listing/storage stop deposits confirmed purchases and waits for you to resolve it; building does not start with an incomplete preparation. One budget is shared throughout that build, including later automatic shopping. Manual Buy Missing purchases the current work batch. Disable Prepare Whole Build to supply materials as building progresses.

**Material Supply: Nearby Sections** is the default. The builder finishes compact 8 × 4 × 8 areas, choosing nearby work and building lower supports before upper blocks in that area. Each work batch contains at most 128 cells and 24 stacks of schematic materials, reserving room for tools, food and dirt. Shopping and chest withdrawals use only that batch's missing quantities. The selected batch stays fixed during a chest trip, and stalled sections are temporarily deferred so another area can be attempted. Completed blocks and ignored materials are excluded. Chest withdrawals split stacks to take the exact amount and return the remainder. **Layer by Layer** and **Whole Schematic** remain available; existing settings retain the chosen mode. Set a positive budget for automatic shopping between batches. Saved placements remember their material supply mode.

**Auto Buy Tools** supplies a diamond pickaxe and shovel if no pickaxe/shovel is already in inventory. Required tools are fetched from chests first when clearing; auction purchases still respect the budget and price ceiling. Mining selects the fastest available tool across the inventory and moves it to the hotbar.

Buying compares the lowest price per item across up to three available auction pages, with total price breaking ties. It returns to an earlier page when needed and rechecks the selected listing. Larger stacks have no price premium. Explicit server messages or menus saying an item was already purchased/sold skip that listing and search again; they do not cancel the shopping session. Unconfirmed purchases without a sold notice still stop rather than risk duplicate spending. Repeated unavailable listings are bounded to 24 skips per session.

The buyer searches `/ah` with spaced registry names and checks item, quantity, total price, inventory space and budget before clicking. It supports immediate listing purchases, separate confirmation screens, and confirmations updated in the same screen. Confirmation requires the matching item/count, a matching price on the item or confirmation control, and a recognized positive control. Unknown titles also require a negative control.

Inventory receipts advance the shopping list and start the next search until all requested materials are supplied. Menu and receipt updates may take up to eight seconds. Changed prices, missing receipts and unrecognized confirmations stop the session without retrying an unconfirmed purchase. Estimates reflect observed listings, not guaranteed availability. Donut's live menus have not been directly verified; test-server fixtures cover these patterns.

## Building and cleanup

Placement uses normal interactions and player reach, checks placement states, supplies inventory materials, and verifies world states. Walking follows bounded routes on loaded safe ground, commits to its current target, checks reachable standing positions and cools down blocked candidates. Walking and placement both use head smoothing. Tall builds and states ordinary interactions cannot reproduce may need manual platforms/correction.

**Action Delay** sets the base delay. **Timing Variation** adds zero to the configured number of random ticks. **Head Smoothing** eases visible turns with bounded angular speed/acceleration. These options do not guarantee that a server permits or cannot detect automation.

**Clean Temporary Supports** is enabled by default. After completion, the builder returns to its own temporary dirt, steps off or moves around it if needed, and breaks it. Inaccessible supports remain tracked; completion waits for cleanup. Dirt requested by the schematic and unrelated dirt are preserved. Pause/deactivation retains supports for a later resume.

**Auto Unstuck** can escape a stuck ground route by jumping and placing a temporary dirt step beneath the player, then replanning the route. It requires dirt, solid safe footing and clear headroom. Recovery steps remain available for return routes and are removed by completion cleanup. Crouching is sent ahead of placements against interactive supports such as hoppers/chests. Note blocks are tuned one interaction at a time, waiting for the note update and stopping at the target; instrument/power changes do not trigger endless retuning.

Repeaters are placed and then adjusted to their requested delay; wooden trapdoors/doors are opened or closed with acknowledged interactions. Neighbor-derived connections and redstone power do not force unfinished layers to wait for future wiring. Water/lava sources are supplied with buckets after the structural layers; clearing unwanted fluids still requires draining.

**Auto Eat** pauses movement/building at **Hunger Threshold**, finds steak across the inventory, eats it and restores the previous slot. It checks selected chests first and **Buy Steak** uses the remaining auction budget if food is missing. **Steak Reserve** controls the quantity. Pause/deactivation releases food-use input.

Replace Wrong Blocks and Mine Out Schematic are off by default. Mine Out clears explicit air cells; sparse unspecified gaps are ignored. Fluids must be drained separately. Container/block-entity protection remains enabled internally.

## Restock, preview and other tools

Chest restocking recovers from a rejected pickup with one bounded retry, returns an unexpected held item, and closes completed or stalled chest sessions before checking other selected supplies. Chest routes try alternate visible standing positions and either half of a double chest. An inventory opened by the builder's own block interaction is closed automatically so building can resume; user menus unrelated to a recent builder interaction remain under user control. Persistent rejected transfers stop with the remaining items retained and a clear status.

Placements use the current view ray and publish the visible orientation through vanilla movement publication before interacting. Vanilla chooses the required position/look packet and updates its own last-sent bookkeeping, avoiding a manually injected look packet followed by a duplicate vanilla look. Opening selected chests, mining and block tuning use the same readiness check; interaction sequence numbers remain owned by the vanilla interaction manager. Server block updates and vanilla prediction acknowledgements both reconcile placement results; a rejected client prediction is retried rather than counted as completed. An aiming attempt expires after four seconds and replans. Temporary dirt in schematic air cells is deferred to final cleanup so it cannot prevent progress to the next layer.

**Deposit All Items: Inventory Full** is the default. When space is blocked by unrelated items, the builder walks to your selected loaded double chests within 64 blocks, transfers inventory/hotbar items using normal container clicks, then continues the same shopping list and budget. Layer supply uses already-held building materials before depositing another batch. Armor and offhand items remain equipped. The control panel's **Deposit All** button also runs this manually. After Buying / After Build deposit modes are available. When a chest fills, compatible partial stacks are filled and remaining inventory items continue into another selected chest. If every selected chest is full, remaining items stay in inventory and the HUD asks you to add another chest. Initial preparation must inspect every selected chest before buying, so an unavailable selected chest pauses preparation. During building, all available selected chests are checked for missing blocks before AH fallback.

Each material row has **Ignore / Include**. Ignored materials are excluded from buying, estimates and restocking, and their schematic blocks are skipped by building, paste and preview. Including a material again rescans the plan. Ignore choices are saved; Cancel Schematic clears them.

Look at each double chest and press R, or use **Options → Materials → Mark Restock Container**. Selections are scoped to the server/world and dimension. Restocking checks only your selected chests within 64 blocks, in distance order. Missing items are cached per chest until your own deposit or selecting the chest again, so later batches go to AH without repeatedly visiting an empty chest. Stockpile In Chests deposits surplus whole stacks of schematic materials; unrelated items are preserved.

Preview presets coordinate textures and outlines. Opacity, range and layers remain adjustable. Scanning has a time budget and previews have a hard cell cap; rendering does not scan the whole file each frame.

Stop On Staff Nearby checks loaded players against configured staff names. Low-health and optional nearby-player checks pause actions. Hidden/unloaded players cannot be identified. Changing worlds stops operations.

Snapshot saves a configured area as block-state-only `.nbt`. Creative material supply and command-based paste appear only in creative mode; paste also requires permission for `/setblock`.

## Formats and verification

Readers support Sponge v1/v2/v3 `.schem`, legacy MCEdit `.schematic` numeric IDs/metadata/AddBlocks, multiregion/signed-size `.litematic`, and vanilla `.nbt` structures. Rotation/mirroring transforms coordinates and block states. Limits: 64 MB file, 256 MB NBT allocation, 2 million cells, dimension 2048. Entities, biomes, inventories and block-entity contents are not imported/pasted.

Focused navigation/persistence checks: `./gradlew runProductionClientGameTest -PbuilderTestOnly=true -PbuilderNavigationTestOnly=true`. They verify diagonal final-block access, floating layer tails and support cleanup, durable named-placement resume with a completed block already present, and retry after server rejection. The auction suite also verifies empty-chest fallback to dirt buying and automatic build/cleanup resume.

`./gradlew runProductionClientGameTest -PbuilderTestOnly=true` checks imports/transforms, previews, survival builds with materials outside the hotbar, exact chest withdrawals, lava-safe walking, temporary-step recovery, double slabs, air clearing, note tuning, crouching against interactive supports, material ignoring, cancellation/restart, and cleanup when standing on or far from a support. Auction tests cover consecutive purchases, cheapest rates on earlier/later pages, sold chat/menu notices with changing expiry lore, delayed updates, reused handlers, price changes and missing receipts. Full inventory is deposited into the selected double chest, shopping resumes without duplicated purchases, and building retrieves its missing blocks from the chest. Additional fixtures check multiple selections and config restoration, double-chest half deduplication, removal of one selection, partial-stack overflow storage, combined stock accounting and exact withdrawal from another chest, AH fallback after checking each selected chest once, selected farther-chest use, missing-item caching, whole-build preparation and one budget across deposits, auto-eating from inventory/chest, delay/open block configuration, source buckets, the supplied signed-region stash import, layer-sized purchases, full inventory supply batches, automatic shopping on the next layer, and pickaxe/shovel supply and selection. Screenshots are in `run/screenshots`.

Format references: [Sponge v2](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-2.md), [Sponge v3](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md), [Litematica](https://github.com/maruohon/litematica). This is independent Maro code.
