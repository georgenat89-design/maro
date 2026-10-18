# Current Maro handoff

The default branch now contains the tested GUI and base-counter release from October 18, 2026.
Minecraft 1.21.11, Fabric, Java 21. Start from this branch's current HEAD.

## Current scope and user instructions

- The user canceled schematic/Auto Builder work. Do not resume its implementation or tests unless the user asks again. The released builder code is preserved; unpublished builder experiments are excluded from this snapshot.
- Protection work is frozen. The normal client remains auth-free. Do not change protection or its credentials/signing setup without a new user request.
- Preserve the blue theme, flat module lists, paged settings, separate upper-right preview dock, jump-circle preview repair, right-click module toggle, and main-menu branding from the current GUI release.
- Do not ask routine clarification questions; make reasonable implementation choices. Respect any new user instructions.

## Base counter

Enable Base ESP for the compact top-right counter. Open chat to use + / -; corrections and unique discoveries save independently of config profiles. Nearby bases expands the counter with coordinates and estimated condition. Likely raided sites are hidden by default but still count.

Storage ESP and Block ESP scan results schedule independent verification. Repeated sightings, scanned surrounding chunks, substantial constructed/storage evidence, and local generated-structure rejection precede counting. Spatial identities survive revisits and are scoped by server/world plus dimension. Two copies of the same template at different places both count.

Conditions are likely intact / likely raided / uncertain. Storage loss requires a complete fresh original footprint. Unloaded chunks must never cause a raid downgrade. Unopened chest loot is unavailable, so do not promise an unraided base or loot. Very small/erased bases and server-masked blocks can be missed. The natural filter is conservative and may reject player builds decorated like generated structures.

Relevant code:

- `src/main/java/dev/maro/module/impl/visuals/BaseESP.java`
- `src/main/java/dev/maro/module/impl/visuals/BlockESP.java` (Storage ESP inherits it)
- `src/main/java/dev/maro/render/esp/BaseEvidence.java`
- `src/main/java/dev/maro/render/esp/BaseDiscoveryLog.java`
- `src/gametest/java/dev/maro/gametest/BaseCounterChecks.java`
- `docs/base-counter.md`

## Build and validate

```sh
./gradlew remapJar remapGametestJar
./gradlew runProductionClientGameTest -PbaseCounterTestOnly=true
./gradlew runProductionClientGameTest -PbaseEspTestOnly=true
```

On Windows use `gradlew.bat`. Native tests create disposable local worlds and do not log into Donut SMP. GitHub CI runs the two current ESP suites before publishing a release. The normal build does not enable the optional protected edition.

The native test rejected actual vanilla trial-chamber and ancient-city templates with extra dense storage. It also verified five distinct sites, two raid estimates, both ESP evidence sources, real mouse corrections, duplicate prevention, toggles, storage loss, and chunk unload. An independent clean source build produced all 45 ESP runtime entries byte-for-byte identical to the tested JAR.

See `docs/releases/base-counter-7b562bb4.json` for hashes and validation details. `BUILD-SNAPSHOT.json` is the original tested desktop source manifest; the GitHub workflow and this handoff are the documented publishing-only differences. The desktop JAR SHA-256 is `7b562bb4ceaa74634e94fe6585608a1363a92109fe37399867891121884c384b`. CI may produce a different whole-JAR hash because its version includes the workflow build number.
