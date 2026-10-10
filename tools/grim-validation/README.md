# Native builder test with Grim

This fixture runs the production Maro native client against **a separate actual Paper 1.21.11 build132 server with stable Grim2.3.73 enabled**. Integrated-server regression tests do not run Grim. The fixture plugin observes Grim's actual FlagEvent API and records authoritative server blocks, inventory, attempt timestamps and player positions. It never changes Grim checks, thresholds, cancellations, setbacks or bypass permissions.

Prerequisites: Python3, a Java21 JDK on JAVA_HOME/PATH, and Maro's normal Fabric/Gradle native test dependencies. The Minecraft EULA must already be accepted for running a Minecraft server; bootstrap writes the accepted server eula.txt for this requested isolated test. All runtime files live in a new disposable directory. Game and RCON both bind only127.0.0.1; offline authentication is used only on this isolated fixture. connection.json contains an ephemeral local RCON password and must never be committed or printed.

```powershell
$env:JAVA_HOME = 'C:/path/to/jdk21'
python tools/grim-validation/bootstrap.py --runtime 'C:/temp/maro-grim-fixture' --start
# Wait for server-console.log to contain the fixture's "active Grim=2.3.73" and "Done" lines.
./gradlew.bat runProductionClientGameTest -PbuilderGrimTestOnly=true '-PbuilderGrimFixture=C:/temp/maro-grim-fixture' --console=plain
python C:/temp/maro-grim-fixture/rcon.py stop
```

Use only one native Minecraft test client at a time. Do not edit/build the checkout while its owned client is alive. Do not modify the user's live Minecraft profile. The server process and exact runtime directory are recorded in server-process.json. Prefer graceful RCON stop; verify its PID commandline and runtime before any forced termination.

Pinned official artifacts:

- [Paper1.21.11 stable builds API](https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds), build132: SHA256 `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba`.
- [Official Grim2.3.73 metadata](https://api.modrinth.com/v2/version/1FIGlM6Q), SHA512 `bf9be1194eb45afe1dec6bd8e98d078dacf41539025c8aabe24d31337cbea86625774e30842568b47f34ca174472c724c7876cc30793945e7408b03609672655`.
- PaperAPI `1.21.11-R0.1-20260511.115010-91`, downloaded from Paper's official Maven repository and checked against its published SHA512.

bootstrap.py checks all downloaded artifacts and records artifact-manifest.json plus SHA256 of the untouched generated Grim config.yml and punishments.yml. No proxy, ViaVersion, Geyser, permission plugin or operator grant is used. The fixture player is in survival with no grim.exempt, grim.nomodifypacket or grim.nosetback permission. Proof requires an actual tracked Grim user and more than20 enabled checks.

Cases: dirt + native diamond shovel replacement; stone + native diamond pickaxe replacement; exactly3 BlockBreakEvent cancellations followed by real mining/replacement; held native block/sequence receipt followed by safe reconciliation; exactly3 BlockPlaceEvent cancellations followed by placement while moving closer from near maximum reach. The fixture's forced denials are **Paper event denials while Grim is running**, not a claim that Grim itself rejected the client. A pass requires zero actual Grim FlagEvents, correct final server blocks and item counts, empty cursor, bounded native look packets, monotonic native interaction sequence, and closer final placement distance. Per-case proof-*.properties and native client/server logs are the durable evidence. Any failed flag/cancellation must be reported, not suppressed by changing Grim settings.

This proves these local cases on this pinned stock Grim version. It does not prove every public server's custom configuration or the full710block schematic on Grim. Retain the existing separate integration tests for full schematic and auction regressions.

The suite also drives Auto Trident with held mouse input in rain, requires at least three authoritative PlayerRiptideEvents, applies a real server teleport, and checks continued accepted launches with zero Grim FlagEvents. The player remains in survival without bypass permissions. `proof-trident.properties` records launch ticks and the same active-check proof. This is a pinned local compatibility check, not a DonutSMP bypass claim.
