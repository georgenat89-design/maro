# Auto Builder — Minecraft 1.21.11

Open **Player → Auto Builder**. This is a new Maro implementation using the supplied screenshots as a feature reference; it does not contain Corz code.

## Start a build

1. Click **Open Folder** and put a schematic into the game's `schematics` folder.
2. Click **Choose Schematic** and select the file. The browser supports filtering and pagination.
3. Set the origin to your feet or the face you are looking at. Nudge X/Y/Z, rotate 0/90/180/270 degrees, or mirror X/Z. Positions and block states transform together. **Apply File Offset** is optional and off by default.
4. Click **Preview** to inspect the blueprint without building. Blue = missing, red = wrong block, amber = wrong state. Textured previews use the game's block models and resource-pack textures.
5. Open **Materials** to see total, remaining, owned and missing quantities. **Copy Missing** copies a shopping list.
6. Choose **Semi Auto** (hold right mouse) or **Automatic**, then click **Build**. **Preview Only** performs no automatic actions. **Pause** releases movement/mining input while leaving the preview available.

Building uses vanilla block interactions and survival reach. It checks the requested block's placement state, supplies materials from your inventory, turns toward the actual visible face, and verifies world states afterward. Double slabs consume two items; door upper halves and bed heads are not counted as extra items. Automatic walking follows loaded, safe ground routes and can reposition around obstructions. It does not teleport, fly automatically, tunnel through obstacles, or construct stair routes. For tall builds, add reachable stairs/platforms or use creative paste with server permission. States that cannot be reproduced by ordinary interactions remain highlighted for manual correction; the module does not invent special server placement packets.

**Replace Wrong Blocks**, **Repair Wrong States**, and **Mine Out Schematic** are off by default. Mine Out clears explicitly stored air cells, not unspecified gaps in sparse structure/litematic regions. Fluids must be drained separately. Container protection is on by default. Temporary dirt can provide reachable side/bottom supports, is capped, and only dirt supports this builder placed are eligible for cleanup.

## File formats

- `.schem`: Sponge v1/v2/v3 palettes and varints.
- `.schematic`: legacy MCEdit numeric blocks + metadata, including AddBlocks; converted using Minecraft's flattening map.
- `.litematic`: multiple regions, signed region sizes and packed block states spanning 64-bit words.
- `.nbt`: vanilla structure palettes and block lists.

Import limits: 64 MB compressed file, 256 MB NBT allocation, 2 million bounding-volume cells, maximum dimension 2048. Unknown/incompatible block identifiers fail with a useful error instead of silently becoming air. Entities, biomes, container inventories and block-entity content such as sign text are not imported or pasted. Capture saves **block states only** as a `.nbt` file, using the configured width/height/length from the placement origin. All captured chunks must be loaded.

## Restock

Look at a chest, barrel, shulker or hopper and press **R** (editable) or **Mark Restock Container**. Marks are saved with the server/local-world scope and dimension. Missing materials are taken from marked containers within **Restock Walk Distance**. **Stockpile In Chests** optionally deposits surplus whole stacks of schematic materials; it does not deposit unrelated items or partial stacks needed by the build. **Restock Temp Dirt** reserves support blocks. Failed/full transfers are not repeatedly clicked forever. The builder owns only the container screen it opens; unrelated screens pause movement.

## Donut auction controls

**Buy Materials**, **Estimate Cost**, a buying keybind, and **Auto Buy When Missing** are provided. The default search command is `/ah` with spaced registry names. Configure the auction title, total-price marker, confirmation labels and page button if the server's menus differ. These controls follow the reference screenshots; they have not been live-verified against Donut's current server menus.

Set **Max Total Spend** to a positive session budget before buying. Zero disables buying. **Max Price Per Item**, stack preference/tolerance, maximum overbuy, page count and click spacing control selection. Prices are read from lore; ambiguous/unreadable prices are rejected. Confirmation must contain the same item, quantity and total price, plus a recognized positive button. A changed or unrecognized menu stops the session. The buyer checks inventory receipt before searching again and does not retry an unconfirmed purchase. Cost estimates are based on listings seen during the scan, not guaranteed final prices or availability. **Buy Temp Dirt** adds support materials. Automatic builds resume only after a completed buying session; failed sessions leave the build paused.

## Preview and stops

Choose Full / Missing & Wrong / Outline Only; All / Single / Below layers; or a Building / Blueprint / Verification preset. Tune ghost opacity, outline width/strength, near-camera fading, surface-only clarity, and faint missing-through-walls outlines. Scanning has a per-tick time budget and previews have a hard cell cap. Rendering does not scan the entire file each frame.

Staff proximity uses the configured staff usernames and **loaded player entities**. It cannot identify hidden/unloaded staff or guarantee detection avoidance. Optional disconnect is off by default; its delay and local reason are configurable. Low health and optional nearby-player checks pause actions. Changing worlds stops active operations.

## Verification and format references

`./gradlew runProductionClientGameTest -PbuilderTestOnly=true` runs import/transform checks and server-verified survival placement, restocking, mining, walking, support cleanup and double-slab fixtures. A test auction server also verifies real menu clicks, inventory receipts when the confirmation closes, and refusal of changed confirmation prices. This validates the buying state machine, not Donut's live menu configuration. Screenshots are saved under `run/screenshots`.

Format references: [Sponge v2](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-2.md), [Sponge v3](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md), [Litematica](https://github.com/maruohon/litematica). The readers are independent Maro code.
