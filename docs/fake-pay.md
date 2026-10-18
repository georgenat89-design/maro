# Fake Pay

Enable **Misc → Fake Pay**, then type `/pay Steve 1.5m`. While enabled, the command
is consumed on the client and displays `You paid Steve $ 1.5M`, with white text
and a green dollar symbol. The module stays enabled. Disabling it restores normal
server commands. It starts disabled when loading a saved configuration.

**Sound** plays the hit-marker ding. **Action Bar** repeats the line above the
hotbar. **Amount Style** chooses Compact (`1.5M`) or Full (`1,500,000`). Amounts
accept an optional dollar sign, grouped commas, decimals, and k/m/b/t suffixes.
Exact decimal arithmetic supports cents from $0.01 to $1,000,000,000,000,000.
Invalid amounts, extra arguments and malformed names show a local error and never
fall through to a real `/pay`. Namespaced commands such as `/essentials:pay` are
covered; unrelated commands are untouched.

**Adjust Sidebar Balance** optionally subtracts simulated payments from rows
marked with `$` or `Money:`, `Balance:`, `Coins:` or `Cash:`. Original entries,
teams, scores and styles stay intact; only the rendered money substring changes,
before vanilla measures the row width. It clamps at zero and clears on disable,
connection changes, option changes, or **Reset Balance**. Unrecognized formats
stay unchanged. **Preview** shows a sample without changing the balance and works
independently of command interception.

This replaces the earlier one-shot received/sent form. Glazed n-17.1 was used as
a behavior reference; this is original Maro code without Glazed binaries or assets.

Native validation:

```powershell
./gradlew.bat build runProductionClientGameTest -PfakePayTestOnly=true `
  '-PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar' --console=plain
```

The test sends valid and malformed commands through the native network handler
and observes zero outgoing payment-command packets while enabled. It checks
formatting, colors, action-bar toggling, grouped/suffixed cents, unit carry,
preview/reset, unchanged server entries and the actual rendered sidebar. It also
checks that disabled Fake Pay and unrelated commands use the normal packet path
in an integrated test world. Tests are excluded from the production JAR.
