# Base discoveries

Enable **Base ESP**. Its counter starts at the top right. It shows today's corrected number,
the saved all-time count, today's automatic discoveries, and the manual correction.
Open chat to release the cursor, then click **+** or **−**. The same actions are in Base ESP's
Actions settings. Subtracting never makes today's count negative. Corrections are saved
separately so a rescan cannot undo your correction. Use **Place detector HUD** to move/resize it.
Enable **Nearby bases** to expand the compact counter with coordinates and estimated raid states.

**Link storage and block ESP** is enabled by default. When either scanner finds a cluster of
storage or relevant built blocks, Base ESP schedules a scan of that chunk. Ores, ordinary
spawners, and a selected ESP block by themselves never increment the counter. Base ESP
also scans independently, so disabling either visual ESP does not stop discoveries.

Each candidate must have a substantial constructed shell with storage, a compact dense
storage cluster, or a substantial storage-poor shell with strong player evidence. New sites
must remain present across repeated observations for at least one second, and their
surrounding chunks must be loaded and scanned before they count. The full loaded height
is scanned by default; **Include surface bases** can restrict scanning to below Y 0.
This only uses chunk data the server actually sends, inside the configured scan/render range.

Trial spawners, vaults, reinforced deepslate, sculk sensors/shriekers and location-local
trial/ancient-city palette combinations (including dense deepslate tiles with white candles or
soul lighting) reject candidates before recording them. A large
number of storage boxes cannot override that rejection. This conservative filter can also
hide a player base built directly in a generated structure or decorated with its markers.

The estimated states are:

- **Likely intact:** substantial storage remains (at least 16 tracked storage/utility blocks).
- **Likely raided:** a credible constructed remnant has fewer than four such blocks, or a
  previously discovered site's storage falls to one quarter of its recorded maximum.
- **Uncertain:** player evidence exists but the remaining storage cannot justify either estimate.

**Hide likely raided** is on by default. It hides their outlines, listing and discovery alerts;
they still count in today's and all-time totals. A storage-loss estimate needs a fresh scan of
the entire previously recorded footprint. Missing/unloaded chunks cannot prove a raid.
Unopened chest contents are not available to this scanner. An empty chest and a full chest
look alike remotely, so "likely intact" does not promise loot. A completely erased base or an
ambiguous empty stone room cannot reliably be identified. Manual corrections cover misses
and false positives; this is a heuristic, not access to server structure or placement metadata.

Discovery identities use spatial bounds, rather than the best chunk or template name.
Partial overlapping views merge; revisits, reconnects, toggles and later days do not create
another discovery of the same saved site. Separate copies of the same base template count
separately. Extremely close/overlapping builds can still be grouped as one site.

History lives in `<game directory>/maro/base-discoveries/<scope hash>.json`, independently of
configuration profiles. Each server and dimension (or local world and dimension) has its own
history, with a 50,000-site limit. Today's count rolls over at midnight in the computer's local
time zone; prior discoveries remain in all-time. Back up this folder to preserve discoveries.
No coordinates are uploaded. Writes use a single background writer and atomic replacement
where the filesystem supports it. A read/save error is visible in the HUD; an unreadable
original is preserved instead of overwritten. In that case, back it up before repairing/restoring
the affected history file.

Native validation uses the normal production JAR in a disposable integrated-server world.
It covers storage-rich and storage-poor sites, bare storage clusters, surface scans, actual vanilla
trial-chamber/ancient-city templates with additional dense storage, structure-marker and ore rejection,
duplicate visits, shared ESP findings, manual controls, storage removal, and retained counts.
The ledger checks additionally cover independent servers/dimensions, disk reload, negative
coordinates, midnight rollover and preservation of damaged history. These checks do not
claim that every Donut SMP build or server-side masking configuration can be recognized.

## Building and checking the source

Use Java 21 and the included Gradle wrapper. On Windows, from the extracted source folder:

```powershell
.\gradlew.bat remapJar remapGametestJar
.\gradlew.bat runProductionClientGameTest -PbaseCounterTestOnly=true
.\gradlew.bat runProductionClientGameTest -PbaseEspTestOnly=true
```

The client JAR is in `build/libs/` (use the ordinary JAR, not the gametest JAR). Run it with
Fabric for Minecraft 1.21.11 and Fabric API. Native tests launch a disposable local world;
they do not connect to Donut SMP or require a server account. Actual server masking and
previously unseen base designs still need verification during normal play.
