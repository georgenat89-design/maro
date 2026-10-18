# Trident Boost

Enable **Movement → Trident Boost**. Adapted from Anubis 0.9.9.1 by 4ldenz.

- **Boost:** default `2.0`. Multiplies the velocity Riptide adds in every
  direction. Range `0.1–10.0`, in steps of `0.1`.
- **Out Of Water:** default on. Allows local Riptide charging and launching
  without touching water or rain. Turn it off for native water requirements.

The module preserves the normal ten-tick charge. Charge and release manually.
Boost applies to the Riptide impulse, leaving existing momentum intact.
Auto Trident and its automatic charging, pacing and correction hooks have been removed.

These are local client changes; servers still decide whether to accept movement
and can correct a launch. Integrated-server players keep native trident rules.
Disabling the module restores native Riptide strength and water checks.
Trident Util remains removed.

## Verification

```powershell
./gradlew.bat build runProductionClientGameTest -PtridentBoostTestOnly=true
```

Optional Meteor compatibility:
`-PmeteorTestJar=C:/path/to/meteor-client-1.21.11-85.jar`.

The production client checks actual Riptide velocity at 0.1×, 1×, 2×, 5× and
10×, existing momentum, wet/dry use and release, the ten-tick charge boundary,
disable cleanup and unchanged server rules. The checks also verify that
Auto Trident is absent from the module registry.
