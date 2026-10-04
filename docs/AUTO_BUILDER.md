# Auto Builder — Minecraft 1.21.11

## Start in three steps

1. Right-click **Player → Auto Builder** to open the control panel. Click **Choose Schematic**. Use **Open Folder** in the library to put files in the game's `schematics` folder.
2. Choose **Origin: Here** or **Origin: Target** and adjust X/Y/Z with the small nudge buttons. **Options → Placement** contains rotation and mirroring. **Materials** shows total, remaining, owned and missing quantities.
3. Select **Mode: Automatic**, then click **Start Build**. The panel closes and building starts without holding right mouse. Semi Auto is available for holding right mouse. **Pause / Cancel Buy** releases movement/mining input.

Automatic is the default for new configurations. Existing configurations retain their chosen mode; change it with the panel's mode button. Show/Hide Preview controls ghosts independently of building. Protocol words and individual rendering constants are internal defaults, reducing the settings list.

## Auction purchases

Enter a positive **Budget** and click **Buy Missing**. Zero disables buying. **Options → Materials → Max Price Per Item** sets the unit-price ceiling. **Auto Buy When Missing** buys after marked containers are exhausted and resumes the build afterward. **Support Dirt Reserve** is shared by auction buying and restocking.

The buyer searches `/ah` with spaced registry names and checks item, quantity, total price, inventory space and budget before clicking. It supports immediate listing purchases, separate confirmation screens, and confirmations updated in the same screen. Confirmation requires the matching item/count, a matching price on the item or confirmation control, and a recognized positive control. Unknown titles also require a negative control.

Inventory receipts advance the shopping list and start the next search until all requested materials are supplied. Menu and receipt updates may take up to eight seconds. Changed prices, missing receipts and unrecognized confirmations stop the session without retrying an unconfirmed purchase. Estimates reflect observed listings, not guaranteed availability. Donut's live menus have not been directly verified; test-server fixtures cover these patterns.

## Building and cleanup

Placement uses normal interactions and player reach, checks placement states, supplies inventory materials, and verifies world states. Walking follows bounded routes on loaded safe ground. Tall builds and states ordinary interactions cannot reproduce may need manual platforms/correction.

**Action Delay** sets the base delay. **Timing Variation** adds zero to the configured number of random ticks. **Head Smoothing** eases visible turns with bounded angular speed/acceleration. These options do not guarantee that a server permits or cannot detect automation.

**Clean Temporary Supports** is enabled by default. After completion, the builder returns to its own temporary dirt, steps off or moves around it if needed, and breaks it. Inaccessible supports remain tracked; completion waits for cleanup. Dirt requested by the schematic and unrelated dirt are preserved. Pause/deactivation retains supports for a later resume.

Replace Wrong Blocks and Mine Out Schematic are off by default. Mine Out clears explicit air cells; sparse unspecified gaps are ignored. Fluids must be drained separately. Container/block-entity protection remains enabled internally.

## Restock, preview and other tools

Look at a chest, barrel, shulker or hopper and press R, or use **Options → Materials → Mark Restock Container**. Marks are scoped to the server/world and dimension. Restocking walks to marked containers within 64 blocks. Stockpile In Chests deposits surplus whole stacks of schematic materials; unrelated items are preserved.

Preview presets coordinate textures and outlines. Opacity, range and layers remain adjustable. Scanning has a time budget and previews have a hard cell cap; rendering does not scan the whole file each frame.

Stop On Staff Nearby checks loaded players against configured staff names. Low-health and optional nearby-player checks pause actions. Hidden/unloaded players cannot be identified. Changing worlds stops operations.

Snapshot saves a configured area as block-state-only `.nbt`. Creative material supply and command-based paste appear only in creative mode; paste also requires permission for `/setblock`.

## Formats and verification

Readers support Sponge v1/v2/v3 `.schem`, legacy MCEdit `.schematic` numeric IDs/metadata/AddBlocks, multiregion/signed-size `.litematic`, and vanilla `.nbt` structures. Rotation/mirroring transforms coordinates and block states. Limits: 64 MB file, 256 MB NBT allocation, 2 million cells, dimension 2048. Entities, biomes, inventories and block-entity contents are not imported/pasted.

`./gradlew runProductionClientGameTest -PbuilderTestOnly=true` checks imports/transforms, previews, survival builds, lava-safe walking, restocking, double slabs, air clearing, and cleanup when standing on or far from a support. Auction tests make four consecutive purchases across two materials, with direct purchases, delayed updates, reused handlers and prices on confirmation controls. Changed prices and missing receipts are checked. Screenshots are in `run/screenshots`.

Format references: [Sponge v2](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-2.md), [Sponge v3](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md), [Litematica](https://github.com/maruohon/litematica). This is independent Maro code.
